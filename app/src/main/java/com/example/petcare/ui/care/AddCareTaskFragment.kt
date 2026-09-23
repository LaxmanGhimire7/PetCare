package com.example.petcare.ui.care

import com.example.petcare.ui.UiSnackbar
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
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
import com.example.petcare.data.local.ReminderPreferences
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
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.chip.Chip
import com.example.petcare.design.PetColor as DesignPetColor
import com.example.petcare.design.PetRingView
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

internal const val SAVED_TASK_RESULT_KEY = "saved_task_result_id"

/** Edits a new care task and reviews imported appointment fields before saving. */
class AddCareTaskFragment : Fragment() {

    private var _binding: FragmentAddCareTaskBinding? = null
    private val binding get() = _binding!!
    private val repositories by lazy { PetCareRepositories(requireContext()) }

    private var pets: List<PetEntity> = emptyList()
    private var selectedPet: PetEntity? = null
    private var selectedDueDateEpochDay: Long? = null
    private var selectedReminderMinutesOfDay = DEFAULT_REMINDER_MINUTES_OF_DAY
    private var selectedCategory = CARE_CATEGORY_GENERAL
    private var selectedFrequency = CARE_FREQUENCY_ONE_TIME
    private var selectedPlace: ProviderEntity? = null

    private val viewModel: AddCareTaskViewModel by viewModels {
        AddCareTaskViewModelFactory(
        repositories.tasks
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

        selectedReminderMinutesOfDay = ReminderPreferences(requireContext())
            .defaultReminderMinutesOfDay()
        configureRoutineFields()
        applyImportedAppointment()
        observePets()
        observePlaces()
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

    private fun configureRoutineFields() {
        val categories = resources.getStringArray(R.array.care_categories).toList()
        val frequencies = resources.getStringArray(R.array.care_frequencies).toList()
        categories.forEach { category ->
            binding.categoryChipGroup.addView(Chip(requireContext()).apply {
                text = category
                isCheckable = true
                isChecked = category == selectedCategory
                setOnClickListener { selectedCategory = category }
            })
        }
        binding.frequencyToggle.addOnButtonCheckedListener { _, checkedId, checked ->
            if (!checked) return@addOnButtonCheckedListener
            selectedFrequency = when (checkedId) {
                R.id.repeat_daily -> frequencies[1]
                R.id.repeat_weekly -> frequencies[2]
                R.id.repeat_monthly -> frequencies[3]
                else -> frequencies[0]
            }
        }
    }

    private fun applyImportedAppointment() {
        arguments?.getString(IMPORTED_TITLE_ARGUMENT)?.takeIf(String::isNotBlank)?.let {
            binding.careTaskTitleInput.setText(it)
        }
        arguments?.getLong(IMPORTED_DATE_ARGUMENT)?.takeIf { it > 0 }?.let {
            selectedDueDateEpochDay = it
            binding.dueDateInput.setText(formatDate(it))
        }
        arguments?.getString(IMPORTED_NOTES_ARGUMENT)?.takeIf(String::isNotBlank)?.let {
            binding.careNotesInput.setText(it)
        }
        arguments?.getInt(IMPORTED_TIME_ARGUMENT, -1)?.takeIf { it in 0..1439 }?.let {
            selectedReminderMinutesOfDay = it
            binding.reminderTimeInput.setText(formatTime(it))
        }
        if (arguments?.containsKey(IMPORTED_TITLE_ARGUMENT) == true) {
            selectedCategory = getString(R.string.category_healthcare)
            for (index in 0 until binding.categoryChipGroup.childCount) {
                (binding.categoryChipGroup.getChildAt(index) as? Chip)?.let { chip ->
                    chip.isChecked = chip.text.toString() == selectedCategory
                }
            }
        }
    }

    private fun observePets() {
        val petRepository = repositories.pets
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                petRepository.observePets().collect { savedPets ->
                    pets = savedPets
                    renderPetChoices()
                }
            }
        }
    }

    /** Builds the visual pet picker from the live account-scoped pet list. */
    private fun renderPetChoices() {
        binding.petAvatarContainer.removeAllViews()
        pets.forEach { pet ->
            val column = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.pet_filter_width),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                isClickable = true
                isFocusable = true
                contentDescription = getString(R.string.select_pet_named, pet.name)
            }
            val ringItem = layoutInflater.inflate(R.layout.pc_item_pet_ring, column, false)
            val color = DesignPetColor.fromIndex(pet.colorIndex)
            val main = color.main(requireContext())
            ringItem.findViewById<PetRingView>(R.id.pcRing).apply {
                setRingColor(main)
                setProgress(if (selectedPet?.id == pet.id) 1f else 0f, animate = true)
            }
            ringItem.findViewById<ImageView>(R.id.pcRingPhoto).visibility = View.GONE
            ringItem.findViewById<ImageView>(R.id.pcRingIcon).apply {
                setImageResource(
                    when (pet.species.lowercase()) {
                        "dog" -> R.drawable.pc_ic_dog
                        "cat" -> R.drawable.pc_ic_cat
                        else -> R.drawable.pc_ic_paw
                    },
                )
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(main))
                ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(color.tint(requireContext())))
            }
            ringItem.findViewById<TextView>(R.id.pcRingName).apply {
                text = pet.name
                setTextAppearance(
                    if (selectedPet?.id == pet.id) R.style.TextAppearance_PC_LabelBold
                    else R.style.TextAppearance_PC_Label,
                )
            }
            ringItem.findViewById<TextView>(R.id.pcRingCount).visibility = View.GONE
            ringItem.isClickable = false
            ringItem.isFocusable = false
            column.addView(ringItem)
            column.setOnClickListener {
                selectedPet = pet
                binding.petErrorText.visibility = View.GONE
                renderPetChoices()
            }
            binding.petAvatarContainer.addView(column)
        }
    }

    private fun observePlaces() {
        val repository = repositories.places
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeAll().collect { places ->
                    if (selectedPlace == null) {
                        val importedClinic = arguments?.getString(IMPORTED_CLINIC_ARGUMENT).orEmpty()
                        selectedPlace = places.firstOrNull { it.name.equals(importedClinic, true) }
                    }
                    val labels = listOf(getString(R.string.no_task_place)) + places.map { it.name }
                    binding.taskPlaceInput.setAdapter(
                        ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
                    )
                    binding.taskPlaceInput.setText(
                        selectedPlace?.name ?: getString(R.string.no_task_place), false
                    )
                    binding.taskPlaceInput.setOnItemClickListener { _, _, position, _ ->
                        selectedPlace = places.getOrNull(position - 1)
                    }
                    SelectionDialog.attach(binding.taskPlaceInput, R.string.task_place, labels) {
                        selectedPlace = places.getOrNull(it - 1)
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
        binding.petErrorText.visibility = if (selectedPet == null) View.VISIBLE else View.GONE
        binding.petErrorText.setText(R.string.error_pet_selection_required)
        binding.dueDateLayout.error = if (selectedDueDateEpochDay == null) {
            getString(R.string.error_due_date_required)
        } else {
            null
        }

        return binding.careTaskTitleLayout.error == null &&
            selectedPet != null &&
            binding.dueDateLayout.error == null
    }

    private fun saveCareTask() {
        binding.saveCareTaskButton.isEnabled = false
        viewModel.addTask(
            petId = requireNotNull(selectedPet).id,
            title = binding.careTaskTitleInput.text?.toString()?.trim().orEmpty(),
            dueDateEpochDay = requireNotNull(selectedDueDateEpochDay),
            reminderMinutesOfDay = selectedReminderMinutesOfDay,
            category = selectedCategory,
            frequency = selectedFrequency,
            requiredSupplies = binding.requiredSuppliesInput.text?.toString()?.trim().orEmpty(),
            notes = binding.careNotesInput.text?.toString()?.trim().orEmpty(),
            latitude = selectedPlace?.latitude,
            longitude = selectedPlace?.longitude,
            placeId = selectedPlace?.id,
            onError = {
                binding.saveCareTaskButton.isEnabled = true
                UiSnackbar.make(binding.root, R.string.task_save_failed, Snackbar.LENGTH_LONG).show()
            },
            onSaved = { savedCareTask ->
                CareReminderScheduler(requireContext()).schedule(savedCareTask)
                runCatching {
                    findNavController().getBackStackEntry(R.id.homeDashboardFragment)
                        .savedStateHandle[SAVED_TASK_RESULT_KEY] = savedCareTask.id
                }
                findNavController().navigateUp()
                UiSnackbar.make(requireActivity().findViewById(android.R.id.content),
                    getString(R.string.task_saved_for_date,
                        formatDate(savedCareTask.dueDateEpochDay)), Snackbar.LENGTH_LONG).show()
            }
        )
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
        const val IMPORTED_TITLE_ARGUMENT = "importedTitle"
        const val IMPORTED_DATE_ARGUMENT = "importedDateEpochDay"
        const val IMPORTED_NOTES_ARGUMENT = "importedNotes"
        const val IMPORTED_TIME_ARGUMENT = "importedTimeMinutes"
        const val IMPORTED_CLINIC_ARGUMENT = "importedClinic"
    }
}
