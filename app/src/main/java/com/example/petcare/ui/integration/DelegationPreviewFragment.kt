package com.example.petcare.ui.integration

import com.example.petcare.ui.UiSnackbar
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ShareCompat
import androidx.fragment.app.Fragment
import com.example.petcare.R
import com.example.petcare.data.local.CaregiverPreferences
import com.example.petcare.design.DelegationLog
import com.example.petcare.databinding.FragmentDelegationPreviewBinding
import com.google.android.material.snackbar.Snackbar

/** Lets a caregiver checklist be edited before opening SMS or the Android share sheet. */
class DelegationPreviewFragment : Fragment() {
    private var _binding: FragmentDelegationPreviewBinding? = null
    private val binding get() = _binding!!
    private val petIds by lazy {
        requireArguments().getLongArray(PET_IDS)?.toList().orEmpty()
    }
    private val contactPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        // ACTION_PICK on Phone.CONTENT_URI grants access to this chosen phone row only.
        try {
            requireContext().contentResolver.query(uri, arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    binding.caregiverNameInput.setText(cursor.getString(0).orEmpty())
                    binding.caregiverPhoneInput.setText(cursor.getString(1).orEmpty())
                } else showContactError()
            } ?: showContactError()
        } catch (_: SecurityException) { showContactError() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FragmentDelegationPreviewBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.messageInput.setText(requireArguments().getString(MESSAGE).orEmpty())
        petIds.firstOrNull()?.let { id ->
            CaregiverPreferences(requireContext()).get(id)?.let { (name, number) ->
                binding.caregiverNameInput.setText(name)
                binding.caregiverPhoneInput.setText(number)
            }
        }
        binding.pickContactButton.setOnClickListener {
            try {
                contactPicker.launch(Intent(Intent.ACTION_PICK,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
            } catch (_: ActivityNotFoundException) { showContactError() }
        }
        binding.sendSmsButton.setOnClickListener { sendSms() }
        binding.shareButton.setOnClickListener { share() }
    }

    private fun rememberCaregiver() {
        val name = binding.caregiverNameInput.text?.toString()?.trim().orEmpty()
        val number = binding.caregiverPhoneInput.text?.toString()?.trim().orEmpty()
        if (name.isNotBlank() && number.isNotBlank())
            CaregiverPreferences(requireContext()).put(petIds, name, number)
    }

    private fun sendSms() {
        rememberCaregiver()
        val number = binding.caregiverPhoneInput.text?.toString()?.trim().orEmpty()
        val message = binding.messageInput.text?.toString().orEmpty()
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
                .putExtra("sms_body", message))
            val caregiver = binding.caregiverNameInput.text?.toString()?.trim().orEmpty()
                .ifBlank { getString(R.string.pc_someone) }
            DelegationLog.record(requireContext(), caregiver)
        } catch (_: ActivityNotFoundException) { share() }
    }

    private fun share() {
        rememberCaregiver()
        ShareCompat.IntentBuilder(requireActivity())
            .setType("text/plain")
            .setText(binding.messageInput.text?.toString().orEmpty())
            .setChooserTitle(R.string.share_checklist_title)
            .startChooser()
    }

    private fun showContactError() {
        UiSnackbar.make(binding.root, R.string.contact_unavailable, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }

    companion object {
        const val MESSAGE = "checklistMessage"
        const val PET_IDS = "checklistPetIds"
    }
}
