package com.example.petcare.ui.care

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.ReminderPreferences
import com.example.petcare.data.local.provider.ProviderEntity
import com.example.petcare.design.Repeat
import com.example.petcare.design.TaskDraft
import com.example.petcare.design.TaskEditorActions
import com.example.petcare.design.TaskEditorBinder
import com.example.petcare.reminders.CareReminderScheduler
import com.example.petcare.ui.UiSnackbar
import com.example.petcare.ui.home.LocalDayClock
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

internal const val SAVED_TASK_RESULT_KEY = "saved_task_result_id"

/** Connects task creation and imported appointments to the approved v4 editor. */
class AddCareTaskFragment : Fragment(R.layout.pc_fragment_task_editor), TaskEditorActions {
    private val repositories by lazy { PetCareRepositories(requireContext()) }
    private val viewModel: AddCareTaskViewModel by viewModels {
        AddCareTaskViewModelFactory(repositories.tasks)
    }
    private var binder: TaskEditorBinder? = null
    private var places: List<ProviderEntity> = emptyList()
    private var setupComplete = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binder = TaskEditorBinder(view, childFragmentManager, this)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repositories.pets.observePets(),
                    repositories.places.observeAll(),
                ) { pets, savedPlaces -> pets to savedPlaces }.collect { (pets, savedPlaces) ->
                    places = savedPlaces
                    if (!setupComplete) {
                        setupComplete = true
                        binder?.setup(
                            pets = pets.map { it.toEditorPet() },
                            categories = resources.editorCategories(),
                            places = savedPlaces.map { it.toEditorPlace() },
                            initial = importedDraft(savedPlaces),
                            preselectPetId = arguments?.getLong(PRESELECT_PET_ID)
                                ?.takeIf { it > 0L },
                        )
                    }
                }
            }
        }
    }

    private fun importedDraft(savedPlaces: List<ProviderEntity>): TaskDraft? {
        val title = arguments?.getString(IMPORTED_TITLE).orEmpty()
        val date = arguments?.getLong(IMPORTED_DATE) ?: 0L
        val notes = arguments?.getString(IMPORTED_NOTES).orEmpty()
        val minutes = arguments?.getInt(IMPORTED_TIME, -1) ?: -1
        val clinic = arguments?.getString(IMPORTED_CLINIC).orEmpty()
        val imported = title.isNotBlank() || date > 0L || notes.isNotBlank() || clinic.isNotBlank()
        if (!imported) return null
        val time = minutes.takeIf { it in 0..1439 }
            ?: ReminderPreferences(requireContext()).defaultReminderMinutesOfDay()
        return TaskDraft(
            id = null,
            title = title,
            petId = null,
            categoryKey = getString(R.string.category_healthcare),
            dueAtMillis = date.takeIf { it > 0L }?.let { LocalDayClock.dueMillis(it, time) },
            repeat = Repeat.ONCE,
            placeId = savedPlaces.firstOrNull { it.name.equals(clinic, ignoreCase = true) }?.id,
            supplies = null,
            notes = notes.ifBlank { null },
        )
    }

    override fun onSave(draft: TaskDraft) {
        binder?.setSaving(true)
        val (day, minutes) = taskDueParts(requireNotNull(draft.dueAtMillis))
        val place = places.firstOrNull { it.id == draft.placeId }
        viewModel.addTask(
            petId = requireNotNull(draft.petId),
            title = draft.title,
            dueDateEpochDay = day,
            reminderMinutesOfDay = minutes,
            category = requireNotNull(draft.categoryKey),
            frequency = draft.repeat.toFrequency(),
            requiredSupplies = draft.supplies.orEmpty(),
            notes = draft.notes.orEmpty(),
            latitude = place?.latitude,
            longitude = place?.longitude,
            placeId = place?.id,
            onError = {
                binder?.setSaving(false)
                view?.let { root ->
                    UiSnackbar.make(root, R.string.task_save_failed, Snackbar.LENGTH_LONG).show()
                }
            },
            onSaved = { saved ->
                CareReminderScheduler(requireContext()).schedule(saved)
                runCatching {
                    findNavController().getBackStackEntry(R.id.homeDashboardFragment)
                        .savedStateHandle[SAVED_TASK_RESULT_KEY] = saved.id
                }
                val host = requireActivity().findViewById<View>(android.R.id.content)
                findNavController().navigateUp()
                UiSnackbar.make(
                    host,
                    getString(R.string.task_saved_for_date, formatDate(saved.dueDateEpochDay)),
                    Snackbar.LENGTH_LONG,
                ).show()
            },
        )
    }

    override fun onClose() {
        findNavController().navigateUp()
    }

    private fun formatDate(epochDay: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochDay * 86_400_000L))

    override fun onDestroyView() {
        binder = null
        setupComplete = false
        super.onDestroyView()
    }

    private companion object {
        const val PRESELECT_PET_ID = "preselectPetId"
        const val IMPORTED_TITLE = "importedTitle"
        const val IMPORTED_DATE = "importedDateEpochDay"
        const val IMPORTED_NOTES = "importedNotes"
        const val IMPORTED_TIME = "importedTimeMinutes"
        const val IMPORTED_CLINIC = "importedClinic"
    }
}
