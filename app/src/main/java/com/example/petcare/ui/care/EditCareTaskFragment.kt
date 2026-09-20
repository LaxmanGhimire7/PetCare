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
import com.example.petcare.ui.SelectionDialog
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.care.CareTaskRepository
import com.example.petcare.data.local.care.DEFAULT_REMINDER_MINUTES_OF_DAY
import com.example.petcare.data.local.care.CARE_CATEGORY_GENERAL
import com.example.petcare.data.local.care.CARE_FREQUENCY_ONE_TIME
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.pet.PetRepository
import com.example.petcare.data.local.provider.ProviderEntity
import com.example.petcare.data.local.provider.ProviderRepository
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

/** Presents an existing care task for editing, including its saved place. */
class EditCareTaskFragment : Fragment() {

    private var _binding: FragmentAddCareTaskBinding? = null
    private val binding get() = _binding!!
    private val repositories by lazy { PetCareRepositories(requireContext()) }

    private val careTaskRepository by lazy { repositories.tasks }
    private val petRepository by lazy { repositories.pets }
    private val viewModel: EditCareTaskViewModel by viewModels {
        EditCareTaskViewModelFactory(careTaskRepository)
    }

    private var pets: List<PetEntity> = emptyList()
    private var careTask: CareTaskEntity? = null
    private var selectedPet: PetEntity? = null
    private var selectedDueDateEpochDay: Long? = null
    private var selectedReminderMinutesOfDay = DEFAULT_REMINDER_MINUTES_OF_DAY
    private var selectedCategory = CARE_CATEGORY_GENERAL
    private var selectedFrequency = CARE_FREQUENCY_ONE_TIME
    private var places: List<ProviderEntity> = emptyList()
    private var selectedPlace: ProviderEntity? = null
    private var placeChanged = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

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

        configureForm()
        configureRoutineFields()
        loadCareTask()
        observePets()
        observePlaces()
        binding.dueDateInput.setOnClickListener { showDatePicker() }
        binding.reminderTimeInput.setOnClickListener { showTimePicker() }
        binding.saveCareTaskButton.setOnClickListener {
            if (isValidInput()) {
                updateCareTask()
            }
        }
        binding.cancelButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun configureRoutineFields() {
        val categories = resources.getStringArray(R.array.care_categories).toList()
        val frequencies = resources.getStringArray(R.array.care_frequencies).toList()
        binding.categoryInput.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categories)
        )
        binding.frequencyInput.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, frequencies)
        )
        binding.categoryInput.setOnItemClickListener { _, _, position, _ ->
            selectedCategory = categories[position]
        }
        SelectionDialog.attach(binding.categoryInput, R.string.task_category, categories) {
            selectedCategory = categories[it]
        }
        binding.frequencyInput.setOnItemClickListener { _, _, position, _ ->
            selectedFrequency = frequencies[position]
        }
        SelectionDialog.attach(binding.frequencyInput, R.string.task_frequency, frequencies) {
            selectedFrequency = frequencies[it]
        }
    }

    private fun configureForm() {
        binding.careTaskFormTitle.setText(R.string.edit_care_task_title)
        binding.careTaskFormSubtitle.setText(R.string.edit_care_task_subtitle)
        binding.saveCareTaskButton.setText(R.string.save_changes)
    }

    private fun loadCareTask() {
        val careTaskId = arguments?.getLong(CARE_TASK_ID_ARGUMENT) ?: 0L
        if (careTaskId == 0L) {
            findNavController().navigateUp()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            careTask = careTaskRepository.getTask(careTaskId)
            if (careTask == null) {
                findNavController().navigateUp()
                return@launch
            }
            bindCareTask()
        }
    }

    private fun observePets() {
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
                    SelectionDialog.attach(binding.petInput, R.string.select_pet, labels) {
                        selectedPet = pets[it]
                        binding.petLayout.error = null
                    }
                    bindCareTask()
                }
            }
        }
    }

    private fun observePlaces() {
        val repository = repositories.places
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeAll().collect { saved ->
                    places = saved
                    val labels = listOf(getString(R.string.no_task_place)) + saved.map { it.name }
                    binding.taskPlaceInput.setAdapter(
                        ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
                    )
                    bindPlace()
                    binding.taskPlaceInput.setOnItemClickListener { _, _, position, _ ->
                        selectedPlace = saved.getOrNull(position - 1)
                        placeChanged = true
                    }
                    SelectionDialog.attach(binding.taskPlaceInput, R.string.task_place, labels) {
                        selectedPlace = saved.getOrNull(it - 1)
                        placeChanged = true
                    }
                }
            }
        }
    }

    private fun bindPlace() {
        selectedPlace = places.firstOrNull { it.id == careTask?.placeId }
        binding.taskPlaceInput.setText(selectedPlace?.name ?: getString(R.string.no_task_place), false)
    }

    private fun bindCareTask() {
        val task = careTask ?: return
        binding.careTaskTitleInput.setText(task.title)
        selectedDueDateEpochDay = task.dueDateEpochDay
        binding.dueDateInput.setText(formatDate(task.dueDateEpochDay))
        selectedReminderMinutesOfDay = task.reminderMinutesOfDay
        binding.reminderTimeInput.setText(formatTime(task.reminderMinutesOfDay))
        selectedCategory = task.category
        selectedFrequency = task.frequency
        binding.categoryInput.setText(task.category, false)
        binding.frequencyInput.setText(task.frequency, false)
        binding.requiredSuppliesInput.setText(task.requiredSupplies)
        binding.careNotesInput.setText(task.notes)
        bindPlace()

        pets.firstOrNull { it.id == task.petId }?.let { pet ->
            selectedPet = pet
            binding.petInput.setText(
                getString(R.string.pet_selection_label, pet.name, pet.species),
                false
            )
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

    private fun updateCareTask() {
        val currentTask = requireNotNull(careTask)
        binding.saveCareTaskButton.isEnabled = false
        viewModel.updateTask(
            currentTask.copy(
                petId = requireNotNull(selectedPet).id,
                title = binding.careTaskTitleInput.text?.toString()?.trim().orEmpty(),
                dueDateEpochDay = requireNotNull(selectedDueDateEpochDay),
                reminderMinutesOfDay = selectedReminderMinutesOfDay,
                category = selectedCategory,
                frequency = selectedFrequency,
                requiredSupplies = binding.requiredSuppliesInput.text?.toString()?.trim().orEmpty(),
                notes = binding.careNotesInput.text?.toString()?.trim().orEmpty(),
                latitude = if (placeChanged) selectedPlace?.latitude else currentTask.latitude,
                longitude = if (placeChanged) selectedPlace?.longitude else currentTask.longitude,
                placeId = if (placeChanged) selectedPlace?.id else currentTask.placeId
            )
        ) { updatedCareTask ->
            CareReminderScheduler(requireContext()).schedule(updatedCareTask)
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
        const val CARE_TASK_ID_ARGUMENT = "careTaskId"
        const val MILLIS_PER_DAY = 86_400_000L
        const val MINUTES_PER_HOUR = 60
        const val DATE_PICKER_TAG = "edit_due_date_picker"
        const val TIME_PICKER_TAG = "edit_reminder_time_picker"
    }
}
