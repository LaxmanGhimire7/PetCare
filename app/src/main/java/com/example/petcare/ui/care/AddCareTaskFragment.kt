package com.example.petcare.ui.care

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.care.CareTaskRepository
import com.example.petcare.data.local.care.DEFAULT_REMINDER_MINUTES_OF_DAY
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.pet.PetRepository
import com.example.petcare.databinding.FragmentAddCareTaskBinding
import com.example.petcare.reminders.CareReminderScheduler
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class AddCareTaskFragment : Fragment() {

    private var _binding: FragmentAddCareTaskBinding? = null
    private val binding get() = _binding!!

    private var pets: List<PetEntity> = emptyList()
    private var selectedPet: PetEntity? = null
    private var selectedDueDateEpochDay: Long? = null
    private var selectedReminderMinutesOfDay = DEFAULT_REMINDER_MINUTES_OF_DAY

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val viewModel: AddCareTaskViewModel by viewModels {
        AddCareTaskViewModelFactory(
            CareTaskRepository(PetCareDatabase.getInstance(requireContext()).careTaskDao())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddCareTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observePets()
        binding.dueDateInput.setOnClickListener { showDatePicker() }
        binding.reminderTimeInput.setOnClickListener { showTimePicker() }
        binding.reminderTimeInput.setText(formatTime(selectedReminderMinutesOfDay))
        binding.saveCareTaskButton.setOnClickListener {
            if (isValidInput()) {
                saveCareTask()
            }
        }
        binding.cancelButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun observePets() {
        val petRepository = PetRepository(PetCareDatabase.getInstance(requireContext()).petDao())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                petRepository.observePets().collect { savedPets ->
                    pets = savedPets
                    val labels = savedPets.map { pet ->
                        getString(R.string.pet_selection_label, pet.name, pet.species)
                    }
                    binding.petInput.setAdapter(
                        ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
                    )
                    binding.petInput.setOnItemClickListener { _, _, position, _ ->
                        selectedPet = pets[position]
                        binding.petLayout.error = null
                    }
                }
            }
        }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.select_due_date)
            .apply {
                selectedDueDateEpochDay?.let { setSelection(it * MILLIS_PER_DAY) }
            }
            .build()

        picker.addOnPositiveButtonClickListener { selectedDateMillis ->
            selectedDueDateEpochDay = selectedDateMillis / MILLIS_PER_DAY
            binding.dueDateInput.setText(formatDate(selectedDueDateEpochDay!!))
            binding.dueDateLayout.error = null
        }
        picker.show(parentFragmentManager, DATE_PICKER_TAG)
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(
                if (android.text.format.DateFormat.is24HourFormat(requireContext())) {
                    TimeFormat.CLOCK_24H
                } else {
                    TimeFormat.CLOCK_12H
                }
            )
            .setHour(selectedReminderMinutesOfDay / MINUTES_PER_HOUR)
            .setMinute(selectedReminderMinutesOfDay % MINUTES_PER_HOUR)
            .setTitleText(R.string.select_reminder_time)
            .build()

        picker.addOnPositiveButtonClickListener {
            selectedReminderMinutesOfDay = picker.hour * MINUTES_PER_HOUR + picker.minute
            binding.reminderTimeInput.setText(formatTime(selectedReminderMinutesOfDay))
        }
        picker.show(parentFragmentManager, TIME_PICKER_TAG)
    }

    private fun isValidInput(): Boolean {
        val title = binding.careTaskTitleInput.text?.toString()?.trim().orEmpty()

        binding.careTaskTitleLayout.error = if (title.isEmpty()) {
            getString(R.string.error_care_task_title_required)
        } else {
            null
        }
        binding.petLayout.error = if (selectedPet == null) {
            getString(R.string.error_pet_selection_required)
        } else {
            null
        }
        binding.dueDateLayout.error = if (selectedDueDateEpochDay == null) {
            getString(R.string.error_due_date_required)
        } else {
            null
        }

        return binding.careTaskTitleLayout.error == null &&
            binding.petLayout.error == null &&
            binding.dueDateLayout.error == null
    }

    private fun saveCareTask() {
        binding.saveCareTaskButton.isEnabled = false
        viewModel.addTask(
            petId = requireNotNull(selectedPet).id,
            title = binding.careTaskTitleInput.text?.toString()?.trim().orEmpty(),
            dueDateEpochDay = requireNotNull(selectedDueDateEpochDay),
            reminderMinutesOfDay = selectedReminderMinutesOfDay
        ) { savedCareTask ->
            CareReminderScheduler(requireContext()).schedule(savedCareTask)
            requestNotificationPermission()
            findNavController().navigateUp()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun formatDate(epochDay: Long): String {
        return DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochDay * MILLIS_PER_DAY))
    }

    private fun formatTime(minutesOfDay: Int): String {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minutesOfDay / MINUTES_PER_HOUR)
                set(Calendar.MINUTE, minutesOfDay % MINUTES_PER_HOUR)
            }.time
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        const val MINUTES_PER_HOUR = 60
        const val DATE_PICKER_TAG = "due_date_picker"
        const val TIME_PICKER_TAG = "reminder_time_picker"
    }
}
