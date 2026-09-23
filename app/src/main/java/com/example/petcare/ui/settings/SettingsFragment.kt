package com.example.petcare.ui.settings

import com.example.petcare.ui.UiSnackbar
import android.os.Bundle
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.design.applySystemBarTopPadding
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.data.local.SettingsPreferences
import com.example.petcare.data.local.backup.CareDataBackup
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.biometric.BiometricManager
import com.example.petcare.data.local.BiometricPreferences
import com.example.petcare.data.local.AccountDataRepository
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.user.AuthRepository
import com.example.petcare.reminders.CareReminderScheduler
import com.example.petcare.widget.CareWidgetProvider
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.petcare.databinding.FragmentSettingsBinding
import com.example.petcare.ui.GestureCoachPrefs
import com.example.petcare.ui.GestureHaptics
import com.example.petcare.debugdata.DemoDataSeeder
import android.content.pm.ApplicationInfo

/** Primary Settings destination, reached from both phone and tablet navigation. */
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val backupDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeBackup(uri)
    }
    private val restoreDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        view.applySystemBarTopPadding()
        val auth = AuthPreferences(requireContext())
        val authRepository = AuthRepository(requireContext())
        val settings = SettingsPreferences(requireContext())
        val accountName = auth.userName().orEmpty()
        binding.accountText.text = accountName
        binding.profileInitials.text = accountName.trim().split(Regex("\\s+"))
            .filter(String::isNotBlank).take(2).joinToString("") { it.take(1) }.uppercase()
        binding.reminderSettingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_reminder_settings)
        }
        binding.searchButton.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_search)
        }
        binding.themeButton.setOnClickListener {
            val modes = intArrayOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)
            val labels = resources.getStringArray(R.array.theme_modes)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(labels, modes.indexOf(settings.themeMode())) { dialog, index ->
                    settings.setThemeMode(modes[index])
                    dialog.dismiss()
                }.setNegativeButton(R.string.cancel, null).show()
        }
        binding.notificationsSwitch.isChecked = settings.notificationsEnabled()
        binding.notificationsSwitch.setOnCheckedChangeListener { _, enabled ->
            settings.setNotificationsEnabled(enabled)
        }
        val biometric = BiometricPreferences(requireContext())
        binding.biometricSwitch.isChecked = biometric.isEnabledFor(auth.ownerId())
        binding.biometricSwitch.setOnCheckedChangeListener { _, enabled ->
            if (!enabled) {
                biometric.setEnabledFor(auth.ownerId(), false)
            } else {
                val available = BiometricManager.from(requireContext()).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                ) == BiometricManager.BIOMETRIC_SUCCESS
                if (available) biometric.setEnabledFor(auth.ownerId(), true)
                else {
                    binding.biometricSwitch.isChecked = false
                    UiSnackbar.make(binding.root, R.string.biometric_unavailable,
                        Snackbar.LENGTH_LONG).show()
                }
            }
        }
        binding.aboutText.setText(R.string.settings_about_geotagging)
        binding.versionText.text = getString(R.string.settings_version,
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName)
        binding.replayGesturesButton.setOnClickListener {
            GestureHaptics.confirm(it)
            GestureCoachPrefs(requireContext()).requestReplay()
            findNavController().navigate(R.id.homeDashboardFragment)
        }
        binding.backupButton.setOnClickListener {
            backupDocument.launch(getString(R.string.backup_filename))
        }
        binding.restoreButton.setOnClickListener {
            restoreDocument.launch(arrayOf("application/json", "text/plain"))
        }
        val debugBuild = requireContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        binding.loadDemoButton.visibility = if (debugBuild) View.VISIBLE else View.GONE
        binding.loadDemoButton.setOnClickListener {
            binding.loadDemoButton.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val loaded = runCatching { DemoDataSeeder(requireContext()).load() }.isSuccess
                binding.loadDemoButton.isEnabled = true
                if (loaded) {
                    UiSnackbar.make(binding.root, R.string.demo_data_loaded, Snackbar.LENGTH_SHORT).show()
                    requireActivity().recreate()
                } else {
                    UiSnackbar.make(binding.root, R.string.demo_data_failed, Snackbar.LENGTH_LONG).show()
                }
            }
        }
        binding.changePasswordButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val email = authRepository.currentUser()?.email.orEmpty()
                findNavController().navigate(
                    R.id.action_settings_to_forgot_password,
                    Bundle().apply { putString("email", email) },
                )
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            binding.accountRecoveryButton.visibility =
                if (authRepository.currentUser()?.securityQuestion.isNullOrBlank()) View.VISIBLE else View.GONE
        }
        binding.accountRecoveryButton.setOnClickListener { showRecoveryDialog(authRepository) }
        binding.clearDataButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_care_data)
                .setMessage(R.string.clear_care_data_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear_care_data) { _, _ ->
                    val app = requireContext().applicationContext
                    val repository = PetCareRepositories(app).accountData
                    val scheduler = CareReminderScheduler(app)
                    viewLifecycleOwner.lifecycleScope.launch {
                        val snapshot = repository.clear()
                        snapshot.tasks.forEach { scheduler.cancel(it.id) }
                        CareWidgetProvider.refresh(app)
                        UiSnackbar.make(requireActivity().findViewById(R.id.main),
                            R.string.care_data_cleared, Snackbar.LENGTH_LONG)
                            .setDuration(10_000)
                            .setAction(R.string.undo) {
                                viewLifecycleOwner.lifecycleScope.launch {
                                    repository.restore(snapshot)
                                    snapshot.tasks.filter { !it.isCompleted }.forEach(scheduler::schedule)
                                    CareWidgetProvider.refresh(app)
                                }
                            }.show()
                    }
                }.show()
        }
        binding.signOutButton.setOnClickListener {
            auth.signOut()
            findNavController().navigate(R.id.action_settings_to_welcome)
        }
    }

    private fun showRecoveryDialog(repository: AuthRepository) {
        val content = layoutInflater.inflate(R.layout.dialog_account_recovery, null)
        val question = content.findViewById<AutoCompleteTextView>(R.id.recovery_question_input)
        val answer = content.findViewById<EditText>(R.id.recovery_answer_input)
        val questions = resources.getStringArray(R.array.pc_security_questions)
        question.setAdapter(ArrayAdapter(requireContext(), R.layout.pc_item_dropdown, questions))
        question.setText(questions.firstOrNull().orEmpty(), false)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_account_recovery)
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save_changes) { _, _ ->
                val selected = question.text?.toString()?.trim().orEmpty()
                val response = answer.text?.toString()?.trim().orEmpty()
                if (selected.isBlank() || response.isBlank()) return@setPositiveButton
                viewLifecycleOwner.lifecycleScope.launch {
                    if (repository.saveRecovery(selected, response)) {
                        binding.accountRecoveryButton.visibility = View.GONE
                        UiSnackbar.make(
                            binding.root,
                            R.string.settings_account_recovery_saved,
                            Snackbar.LENGTH_LONG,
                        ).show()
                    }
                }
            }
            .show()
    }

    private fun writeBackup(uri: Uri) {
        val app = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val success = runCatching {
                val json = CareDataBackup(app).export()
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Document stream unavailable")
                }
            }.isSuccess
            UiSnackbar.make(binding.root, if (success) R.string.backup_saved else
                R.string.backup_failed, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun confirmRestore(uri: Uri) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.restore_data)
            .setMessage(R.string.restore_merge_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.restore_data) { _, _ ->
                val app = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    val count = runCatching {
                        withContext(Dispatchers.IO) {
                            app.contentResolver.openInputStream(uri)?.use {
                                CareDataBackup(app).restore(it)
                            } ?: error("Document stream unavailable")
                        }
                    }.getOrNull()
                    UiSnackbar.make(binding.root, if (count == null) getString(R.string.restore_failed)
                        else resources.getQuantityString(R.plurals.restore_finished, count, count),
                        Snackbar.LENGTH_LONG).show()
                }
            }.show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
