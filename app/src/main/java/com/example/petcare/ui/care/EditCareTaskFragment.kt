package com.example.petcare.ui.care

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.provider.ProviderEntity
import com.example.petcare.design.TaskDraft
import com.example.petcare.design.TaskEditorActions
import com.example.petcare.design.TaskEditorBinder
import com.example.petcare.reminders.CareReminderScheduler
import com.example.petcare.ui.UiSnackbar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Connects updates of an existing task to the same approved v4 editor used for creation. */
class EditCareTaskFragment : Fragment(R.layout.pc_fragment_task_editor), TaskEditorActions {
    private val repositories by lazy { PetCareRepositories(requireContext()) }
    private val viewModel: EditCareTaskViewModel by viewModels {
        EditCareTaskViewModelFactory(repositories.tasks)
    }
    private var binder: TaskEditorBinder? = null
    private var task: CareTaskEntity? = null
    private var places: List<ProviderEntity> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binder = TaskEditorBinder(view, childFragmentManager, this)
        val taskId = arguments?.getLong(CARE_TASK_ID) ?: 0L
        if (taskId <= 0L) {
            findNavController().navigateUp()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = repositories.tasks.getTask(taskId)
            if (loaded == null) {
                findNavController().navigateUp()
                return@launch
            }
            task = loaded
            val pets = repositories.pets.observePets().first()
            places = repositories.places.observeAll().first()
            binder?.setup(
                pets = pets.map { it.toEditorPet() },
                categories = resources.editorCategories(),
                places = places.map { it.toEditorPlace() },
                initial = loaded.toTaskDraft(),
            )
        }
    }

    override fun onSave(draft: TaskDraft) {
        val current = task ?: return
        binder?.setSaving(true)
        val (day, minutes) = taskDueParts(requireNotNull(draft.dueAtMillis))
        val place = places.firstOrNull { it.id == draft.placeId }
        val updated = current.copy(
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
        )
        viewModel.updateTask(
            updated,
            onError = {
                binder?.setSaving(false)
                view?.let { root ->
                    UiSnackbar.make(root, R.string.task_save_failed, Snackbar.LENGTH_LONG).show()
                }
            },
            onSaved = { saved ->
                CareReminderScheduler(requireContext()).let { scheduler ->
                    scheduler.cancel(saved.id)
                    if (!saved.isCompleted) scheduler.schedule(saved)
                }
                findNavController().navigateUp()
            },
        )
    }

    override fun onClose() {
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        binder = null
        super.onDestroyView()
    }

    private companion object {
        const val CARE_TASK_ID = "careTaskId"
    }
}
