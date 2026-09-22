package com.example.petcare.ui.home

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.Animatable
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.content.res.AppCompatResources
import coil.load
import com.example.petcare.R
import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.data.local.pet.PetColor
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.ui.MotionPrefs
import com.example.petcare.ui.GestureHaptics
import com.example.petcare.ui.RowMotion
import com.example.petcare.databinding.ItemPetFilterBinding
import com.example.petcare.databinding.ItemTodayTaskBinding
import com.google.android.material.color.MaterialColors
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** Horizontal pet identity filter; null represents all pets. */
class PetFilterAdapter(private val onSelect: (Long?) -> Unit) :
    RecyclerView.Adapter<PetFilterAdapter.Holder>() {
    private var pets = emptyList<PetEntity>()
    private var selectedPetId: Long? = null

    @SuppressLint("NotifyDataSetChanged")
    fun submit(items: List<PetEntity>, selectedId: Long?) {
        pets = items
        selectedPetId = selectedId
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = pets.size + 1
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemPetFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(if (position == 0) null else pets[position - 1])
    }

    inner class Holder(private val row: ItemPetFilterBinding) : RecyclerView.ViewHolder(row.root) {
        fun bind(pet: PetEntity?) {
            val context = row.root.context
            val selected = pet?.id == selectedPetId
            val color = pet?.let { PetColor.fromIndex(it.colorIndex).primary }
                ?: MaterialColors.getColor(row.root, androidx.appcompat.R.attr.colorPrimary)
            row.filterAvatarCard.strokeColor = color
            row.filterAvatarCard.strokeWidth = context.resources.getDimensionPixelSize(
                if (selected) R.dimen.space_4 else R.dimen.space_2
            )
            row.filterNameText.text = pet?.name ?: context.getString(R.string.today_all_pets)
            row.root.contentDescription = if (pet == null) context.getString(R.string.today_filter_all)
                else context.getString(R.string.today_filter_pet, pet.name)
            val photo = pet?.photos()?.firstOrNull()
            if (photo == null) {
                row.filterAvatarImage.setImageResource(R.drawable.ic_pets)
                row.filterAvatarImage.setColorFilter(color)
                row.filterAvatarImage.setPadding(
                    context.resources.getDimensionPixelSize(R.dimen.space_12),
                    context.resources.getDimensionPixelSize(R.dimen.space_12),
                    context.resources.getDimensionPixelSize(R.dimen.space_12),
                    context.resources.getDimensionPixelSize(R.dimen.space_12)
                )
            } else {
                row.filterAvatarImage.clearColorFilter()
                row.filterAvatarImage.setPadding(0, 0, 0, 0)
                row.filterAvatarImage.load(photo)
            }
            row.root.setOnClickListener { onSelect(pet?.id) }
        }
    }
}

/** Task rows use stable ids and DiffUtil so filter changes avoid rebuilding the whole list. */
class TodayTaskAdapter(
    private val completed: Boolean,
    private val onComplete: (CareTaskSummary) -> Unit,
    private val onEdit: (CareTaskSummary) -> Unit,
    private val onShare: (CareTaskSummary) -> Unit,
    private val onOpen: (CareTaskSummary) -> Unit,
    private val onDrag: (RecyclerView.ViewHolder) -> Unit = {}
) : ListAdapter<CareTaskSummary, TodayTaskAdapter.Holder>(DIFF) {
    var todayEpochDay: Long = 0
    var restoringTaskId: Long? = null
    private var draggedItems: MutableList<CareTaskSummary>? = null
    private var originalSlots: List<Long> = emptyList()

    fun beginDrag() {
        draggedItems = currentList.toMutableList()
        originalSlots = currentList.map(CareTaskSummary::sortOrder)
    }

    /** Keep the dragged row in the displayed order until Room confirms the move. */
    fun moveItem(from: Int, to: Int): Boolean {
        val items = draggedItems ?: return false
        if (from !in items.indices || to !in items.indices) return false
        items.add(to, items.removeAt(from))
        submitList(items.toList())
        return true
    }

    fun finishDrag(): Pair<List<Long>, List<Long>>? {
        val result = draggedItems?.map(CareTaskSummary::id)?.let { it to originalSlots }
        draggedItems = null
        originalSlots = emptyList()
        return result
    }

    fun taskAt(position: Int): CareTaskSummary = getItem(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemTodayTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val row: ItemTodayTaskBinding) : RecyclerView.ViewHolder(row.root) {
        private var pendingDrag: Runnable? = null
        @SuppressLint("ClickableViewAccessibility")
        fun bind(task: CareTaskSummary) {
            pendingDrag?.let(row.taskDragHandle::removeCallbacks)
            pendingDrag = null
            val context = row.root.context
            row.root.visibility = View.VISIBLE
            row.root.layoutParams = row.root.layoutParams.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            val overdue = !completed && task.dueDateEpochDay < todayEpochDay
            row.taskColorRail.setBackgroundColor(
                if (overdue) androidx.core.content.ContextCompat.getColor(context, R.color.error)
                else PetColor.fromIndex(task.petColorIndex).primary
            )
            row.taskTimeText.text = formatTime(task.reminderMinutesOfDay)
            row.taskTitleText.text = task.title
            row.taskPetText.text = task.petName
            row.taskDueDateText.visibility = if (task.dueDateEpochDay == todayEpochDay)
                View.GONE else View.VISIBLE
            if (task.dueDateEpochDay != todayEpochDay) {
                val date = DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(task.dueDateEpochDay * 86_400_000L))
                row.taskDueDateText.text = context.getString(R.string.task_due_date, date)
            }
            row.taskCategoryChip.text = task.category
            row.taskCategoryChip.chipBackgroundColor = ColorStateList.valueOf(
                MaterialColors.getColor(row.root, com.google.android.material.R.attr.colorSurfaceContainerHigh)
            )
            row.taskLocationChip.visibility = if (task.latitude != null && task.longitude != null)
                View.VISIBLE else View.GONE
            row.taskLocationChip.setOnClickListener {
                val lat = task.latitude ?: return@setOnClickListener
                val lon = task.longitude ?: return@setOnClickListener
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon")))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.no_compatible_app, Toast.LENGTH_SHORT).show()
                }
            }
            row.taskOverdueText.visibility = if (overdue) View.VISIBLE else View.GONE
            if (restoringTaskId == task.id) {
                restoringTaskId = null
                RowMotion.expand(row.root)
            }
            row.root.animate().cancel()
            row.root.scaleX = 1f
            row.root.scaleY = 1f
            row.completeTaskButton.visibility = if (completed) View.GONE else View.VISIBLE
            row.completeTaskButton.isEnabled = !completed
            row.completeTaskButton.setOnClickListener {
                row.completeTaskButton.isEnabled = false
                GestureHaptics.confirm(row.root)
                if (MotionPrefs.animationsEnabled(context)) {
                    row.completeTaskButton.icon = AppCompatResources.getDrawable(
                        context, R.drawable.avd_task_check
                    ).also { (it as? Animatable)?.start() }
                    // The database action must not depend on an animation end callback:
                    // RecyclerView can cancel that callback when a large-text row reflows.
                    row.root.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100L)
                        .withEndAction {
                            row.root.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                        }.start()
                }
                onComplete(task)
            }
            row.taskDragHandle.visibility = if (completed) View.GONE else View.VISIBLE
            row.taskDragHandle.setOnLongClickListener {
                GestureHaptics.confirm(row.taskDragHandle)
                onDrag(this@Holder)
                true
            }
            row.taskDragHandle.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // 350 ms feels deliberate and lifts before a scrolling parent takes over.
                        pendingDrag = Runnable {
                            if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                                GestureHaptics.confirm(view)
                                onDrag(this@Holder)
                            }
                        }.also { view.postDelayed(it, 350L) }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        pendingDrag?.let(view::removeCallbacks)
                        pendingDrag = null
                    }
                }
                true
            }
            val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onOpen(task)
                    return true
                }
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    GestureHaptics.confirm(row.root)
                    onEdit(task)
                    return true
                }
                override fun onLongPress(e: MotionEvent) {
                    GestureHaptics.confirm(row.root)
                    onShare(task)
                }
            })
            row.root.isClickable = true
            row.root.setOnTouchListener { _, event ->
                detector.onTouchEvent(event)
                false
            }
        }

        private fun formatTime(minutes: Int): String {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minutes / 60)
                set(Calendar.MINUTE, minutes % 60)
            }
            return DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.time)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CareTaskSummary>() {
            override fun areItemsTheSame(a: CareTaskSummary, b: CareTaskSummary) = a.id == b.id
            override fun areContentsTheSame(a: CareTaskSummary, b: CareTaskSummary) = a == b
        }
    }
}
