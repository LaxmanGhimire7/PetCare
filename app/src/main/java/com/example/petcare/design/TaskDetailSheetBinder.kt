package com.example.petcare.design

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.petcare.R

/**
 * Everything the task sheet shows. [whenLabel] is a full sentence such as
 * "Fri 25 September at 17:00"; [repeatLabel] e.g. "Every day", or null for one-off tasks.
 */
data class TaskDetail(
    val id: Long,
    val title: String,
    val petName: String,
    val petColor: PetColor,
    @DrawableRes val categoryIcon: Int,
    val whenLabel: String,
    val repeatLabel: String?,
    val done: Boolean,
    val overdue: Boolean,
    val supplies: String?,
    val notes: String?,
    val placeName: String?,
)

interface TaskDetailActions {
    fun onToggleDone(taskId: Long, done: Boolean)
    fun onEdit(taskId: Long)

    /** Opens the existing SMS delegation flow for this one task. */
    fun onShare(taskId: Long)
    fun onSnooze(taskId: Long)

    /** Called only after the user confirms in the dialog. */
    fun onDelete(taskId: Long)
    fun onOpenPlace(taskId: Long) = Unit
}

/**
 * Binds pc_sheet_task_detail.xml inside a BottomSheetDialogFragment. One place for the
 * brief's edit, delete, mark-complete and delegate actions on a single task.
 */
class TaskDetailSheetBinder(
    root: View,
    private val actions: TaskDetailActions,
) {
    private val context = root.context
    private val icon: ImageView = root.findViewById(R.id.pcSheetIcon)
    private val title: TextView = root.findViewById(R.id.pcSheetTitle)
    private val pet: TextView = root.findViewById(R.id.pcSheetPet)
    private val status: TextView = root.findViewById(R.id.pcSheetStatus)
    private val whenText: TextView = root.findViewById(R.id.pcSheetWhen)
    private val repeatRow: View = root.findViewById(R.id.pcSheetRepeatRow)
    private val repeat: TextView = root.findViewById(R.id.pcSheetRepeat)
    private val suppliesRow: View = root.findViewById(R.id.pcSheetSuppliesRow)
    private val supplies: TextView = root.findViewById(R.id.pcSheetSupplies)
    private val notesRow: View = root.findViewById(R.id.pcSheetNotesRow)
    private val notes: TextView = root.findViewById(R.id.pcSheetNotes)
    private val placeRow: View = root.findViewById(R.id.pcSheetPlaceRow)
    private val place: TextView = root.findViewById(R.id.pcSheetPlace)
    private val done: MaterialButton = root.findViewById(R.id.pcSheetDone)
    private val snooze: View = root.findViewById(R.id.pcSheetSnooze)
    private val edit: View = root.findViewById(R.id.pcSheetEdit)
    private val share: View = root.findViewById(R.id.pcSheetShare)
    private val delete: View = root.findViewById(R.id.pcSheetDelete)

    fun render(detail: TaskDetail) {
        val main = detail.petColor.main(context)
        val tint = detail.petColor.tint(context)

        icon.setImageResource(detail.categoryIcon)
        ViewCompat.setBackgroundTintList(icon, ColorStateList.valueOf(tint))
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(main))

        title.text = detail.title
        pet.text = detail.petName
        pet.setTextColor(main)
        ViewCompat.setBackgroundTintList(pet, ColorStateList.valueOf(tint))
        bindStatus(detail)

        whenText.text = detail.whenLabel
        bindOptional(repeatRow, repeat, detail.repeatLabel)
        bindOptional(suppliesRow, supplies, detail.supplies)
        bindOptional(notesRow, notes, detail.notes)
        bindOptional(placeRow, place, detail.placeName)
        placeRow.setOnClickListener { actions.onOpenPlace(detail.id) }

        bindDoneButton(detail.done)
        done.setOnClickListener {
            Haptics.confirm(it)
            actions.onToggleDone(detail.id, !detail.done)
        }
        snooze.visibility = if (detail.done) View.GONE else View.VISIBLE
        snooze.setOnClickListener { actions.onSnooze(detail.id) }
        edit.setOnClickListener { actions.onEdit(detail.id) }
        share.setOnClickListener { actions.onShare(detail.id) }
        delete.setOnClickListener { confirmDelete(detail) }
    }

    private fun bindStatus(detail: TaskDetail) {
        val (label, background, foreground) = when {
            detail.done -> Triple(R.string.pc_status_done, R.color.pc_success_container, R.color.pc_success)
            detail.overdue -> Triple(R.string.pc_status_overdue, R.color.pc_error_container, R.color.pc_on_error_container)
            else -> Triple(R.string.pc_status_upcoming, R.color.pc_surface_raised, R.color.pc_text_secondary)
        }
        status.setText(label)
        status.setTextColor(ContextCompat.getColor(context, foreground))
        ViewCompat.setBackgroundTintList(status, ColorStateList.valueOf(ContextCompat.getColor(context, background)))
    }

    /** Orange "Mark done" while open; a quiet "Mark not done" once it's done. */
    private fun bindDoneButton(isDone: Boolean) {
        val background = ContextCompat.getColor(context, if (isDone) R.color.pc_surface_raised else R.color.pc_primary)
        val foreground = ContextCompat.getColor(context, if (isDone) R.color.pc_text_primary else R.color.pc_on_primary)
        done.setText(if (isDone) R.string.pc_mark_not_done else R.string.pc_mark_done)
        done.backgroundTintList = ColorStateList.valueOf(background)
        done.setTextColor(foreground)
        done.iconTint = ColorStateList.valueOf(foreground)
    }

    private fun bindOptional(row: View, text: TextView, value: String?) {
        if (value.isNullOrBlank()) {
            row.visibility = View.GONE
        } else {
            row.visibility = View.VISIBLE
            text.text = value
        }
    }

    private fun confirmDelete(detail: TaskDetail) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pc_delete_title)
            .setMessage(context.getString(R.string.pc_delete_body, detail.title, detail.petName))
            .setNegativeButton(R.string.pc_cancel, null)
            .setPositiveButton(R.string.pc_delete) { _, _ -> actions.onDelete(detail.id) }
            .show()
    }
}
