package com.example.petcare.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.care.CARE_FREQUENCY_DAILY
import com.example.petcare.data.local.care.CARE_FREQUENCY_MONTHLY
import com.example.petcare.data.local.care.CARE_FREQUENCY_WEEKLY
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.design.InboxStore
import com.example.petcare.design.PetColor
import com.example.petcare.design.TaskDetail
import com.example.petcare.design.TaskDetailActions
import com.example.petcare.design.TaskDetailSheetBinder
import com.example.petcare.reminders.CareReminderScheduler
import com.example.petcare.ui.categoryIcon
import com.example.petcare.ui.integration.DelegationPreviewFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Repository-backed task-detail sheet shared by the v4 Today and checklist flows. */
class TaskDetailSheet : BottomSheetDialogFragment(R.layout.pc_sheet_task_detail), TaskDetailActions {
    private val repositories by lazy { PetCareRepositories(requireContext()) }
    private val scheduler by lazy { CareReminderScheduler(requireContext()) }
    private var binder: TaskDetailSheetBinder? = null
    private var current: LoadedTask? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binder = TaskDetailSheetBinder(view, this)
        load()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet,
        )?.setBackgroundResource(android.R.color.transparent)
    }

    private fun load() {
        val taskId = requireArguments().getLong(ARG_TASK_ID)
        lifecycleScope.launch {
            val task = repositories.tasks.getTask(taskId) ?: run { dismissAllowingStateLoss(); return@launch }
            val pet = repositories.pets.getPet(task.petId) ?: run { dismissAllowingStateLoss(); return@launch }
            val place = task.placeId?.let { repositories.places.get(it) }
            current = LoadedTask(task, pet.name, pet.colorIndex, place?.name)
            binder?.render(task.toDetail(pet.name, pet.colorIndex, place?.name))
        }
    }

    override fun onToggleDone(taskId: Long, done: Boolean) {
        lifecycleScope.launch {
            if (done) {
                repositories.tasks.completeTask(taskId)?.let(scheduler::schedule)
                scheduler.cancel(taskId)
                current?.task?.title?.let { title ->
                    Toast.makeText(requireContext(), getString(R.string.task_done_message, title),
                        Toast.LENGTH_SHORT).show()
                }
            } else {
                repositories.tasks.reopenTask(taskId)?.let {
                    scheduler.schedule(it)
                    Toast.makeText(requireContext(), getString(R.string.task_reopened_message, it.title),
                        Toast.LENGTH_SHORT).show()
                }
            }
            load()
        }
    }

    override fun onEdit(taskId: Long) {
        dismiss()
        findNavController().navigate(R.id.editCareTaskFragment, bundleOf("careTaskId" to taskId))
    }

    override fun onShare(taskId: Long) {
        val loaded = current ?: return
        val detail = loaded.task.toDetail(loaded.petName, loaded.petColorIndex, loaded.placeName)
        val message = getString(
            R.string.task_share_body,
            loaded.petName,
            loaded.task.title,
            detail.whenLabel.substringBefore(" at "),
            detail.whenLabel.substringAfter(" at "),
            loaded.task.notes,
        )
        dismiss()
        findNavController().navigate(
            R.id.delegationPreviewFragment,
            bundleOf(
                DelegationPreviewFragment.MESSAGE to message,
                DelegationPreviewFragment.PET_IDS to longArrayOf(loaded.task.petId),
            ),
        )
    }

    override fun onSnooze(taskId: Long) {
        lifecycleScope.launch {
            repositories.tasks.snoozeTask(taskId, 30)?.let { moved ->
                scheduler.cancel(taskId)
                scheduler.schedule(moved)
                Toast.makeText(requireContext(), R.string.snoozed_thirty_minutes, Toast.LENGTH_SHORT).show()
            }
            load()
        }
    }

    override fun onDelete(taskId: Long) {
        lifecycleScope.launch {
            repositories.tasks.deleteTask(taskId) ?: return@launch
            scheduler.cancel(taskId)
            InboxStore.get(requireContext()).removeForTask(taskId)
            Toast.makeText(requireContext(), R.string.task_deleted, Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    override fun onOpenPlace(taskId: Long) {
        val task = current?.task ?: return
        lifecycleScope.launch {
            val place = task.placeId?.let { repositories.places.get(it) }
            val target = when {
                task.latitude != null && task.longitude != null ->
                    "geo:${task.latitude},${task.longitude}?q=${task.latitude},${task.longitude}"
                place != null -> "geo:0,0?q=${Uri.encode(place.address.ifBlank { place.name })}"
                else -> return@launch
            }
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.no_compatible_app, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        binder = null
        super.onDestroyView()
    }

    private fun CareTaskEntity.toDetail(petName: String, colorIndex: Int, placeName: String?) = TaskDetail(
        id = id,
        title = title,
        petName = petName,
        petColor = PetColor.fromIndex(colorIndex),
        categoryIcon = categoryIcon(category, title),
        whenLabel = SimpleDateFormat("EEE d MMMM 'at' HH:mm", Locale.getDefault()).format(
            Date(LocalDayClock.dueMillis(dueDateEpochDay, reminderMinutesOfDay)),
        ),
        repeatLabel = repeatLabel(),
        done = isCompleted,
        overdue = !isCompleted && LocalDayClock.dueMillis(dueDateEpochDay, reminderMinutesOfDay) < System.currentTimeMillis(),
        supplies = requiredSupplies.ifBlank { null },
        notes = notes.ifBlank { null },
        placeName = placeName,
    )

    private fun CareTaskEntity.repeatLabel(): String? = when (frequency) {
        CARE_FREQUENCY_DAILY -> "Every day"
        CARE_FREQUENCY_WEEKLY -> "Every week on ${SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(LocalDayClock.dueMillis(dueDateEpochDay, reminderMinutesOfDay)))}"
        CARE_FREQUENCY_MONTHLY -> "Every month on the ${ordinal(LocalDayClock.utcCalendar(dueDateEpochDay).get(Calendar.DAY_OF_MONTH))}"
        else -> null
    }

    private fun ordinal(day: Int): String = when {
        day in 11..13 -> "${day}th"
        day % 10 == 1 -> "${day}st"
        day % 10 == 2 -> "${day}nd"
        day % 10 == 3 -> "${day}rd"
        else -> "${day}th"
    }

    private data class LoadedTask(
        val task: CareTaskEntity,
        val petName: String,
        val petColorIndex: Int,
        val placeName: String?,
    )

    companion object {
        private const val ARG_TASK_ID = "taskId"

        fun newInstance(taskId: Long) = TaskDetailSheet().apply {
            arguments = bundleOf(ARG_TASK_ID to taskId)
        }

        fun newInstance(task: CareTaskSummary) = newInstance(task.id)
    }
}
