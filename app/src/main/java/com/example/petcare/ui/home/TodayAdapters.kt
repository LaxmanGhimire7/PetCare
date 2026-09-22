package com.example.petcare.ui.home

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.petcare.R
import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.data.local.pet.PetColor
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.databinding.ItemPetFilterBinding
import com.example.petcare.databinding.ItemTodayTaskBinding
import com.example.petcare.ui.GestureHaptics
import com.example.petcare.ui.RowMotion
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Horizontal pet identity filter; null represents all pets. */
class PetFilterAdapter(private val onSelect: (Long?) -> Unit) :
    RecyclerView.Adapter<PetFilterAdapter.Holder>() {
    private var pets = emptyList<PetEntity>()
    private var selectedPetId: Long? = null
    private var progress = emptyMap<Long, Pair<Int, Int>>()
    private var allProgress = 0 to 0

    @SuppressLint("NotifyDataSetChanged")
    fun submit(items: List<PetEntity>, selectedId: Long?, values: Map<Long, Pair<Int, Int>>, total: Pair<Int, Int>) {
        pets = items
        selectedPetId = selectedId
        progress = values
        allProgress = total
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = pets.size + 1
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPetFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(if (position == 0) null else pets[position - 1])

    inner class Holder(private val row: ItemPetFilterBinding) : RecyclerView.ViewHolder(row.root) {
        fun bind(pet: PetEntity?) {
            val context = row.root.context
            val selected = pet?.id == selectedPetId
            val value = pet?.let { progress[it.id] } ?: allProgress
            row.filterNameText.text = pet?.name ?: context.getString(R.string.today_all_pets)
            row.filterProgressText.text = context.getString(R.string.pet_progress_count, value.first, value.second)
            row.root.contentDescription = if (pet == null) context.getString(R.string.today_filter_all)
                else context.getString(R.string.pet_avatar_progress, pet.name, value.first, value.second)
            row.filterAvatarView.setPet(
                pet?.name ?: context.getString(R.string.today_all_pets), pet?.colorIndex ?: 0,
                value.first, value.second, selected,
                if (pet == null) ContextCompat.getColor(context, R.color.primary) else null
            )
            row.root.setOnClickListener { onSelect(pet?.id) }
        }
    }
}

/** Chronological task adapter; completion changes styling without changing the item position. */
class TodayTaskAdapter(
    private val onComplete: (CareTaskSummary) -> Unit,
    private val onReopen: (CareTaskSummary) -> Unit,
    private val onEdit: (CareTaskSummary) -> Unit,
    private val onShare: (CareTaskSummary) -> Unit,
    private val onOpen: (CareTaskSummary) -> Unit,
    private val onDrag: (RecyclerView.ViewHolder) -> Unit = {}
) : ListAdapter<CareTaskSummary, TodayTaskAdapter.Holder>(DIFF) {
    var todayEpochDay: Long = 0
    var nowBeforeTaskId: Long? = null
    var restoringTaskId: Long? = null
    private var draggedItems: MutableList<CareTaskSummary>? = null
    private var originalSlots: List<Long> = emptyList()
    var reorderMode = false
        set(value) { field = value; notifyDataSetChanged() }

    fun beginDrag() {
        draggedItems = currentList.toMutableList()
        originalSlots = currentList.map(CareTaskSummary::sortOrder)
    }

    fun moveItem(from: Int, to: Int): Boolean {
        val items = draggedItems ?: return false
        if (from !in items.indices || to !in items.indices || items[from].isCompleted || items[to].isCompleted) return false
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemTodayTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(getItem(position), position == itemCount - 1)

    inner class Holder(private val row: ItemTodayTaskBinding) : RecyclerView.ViewHolder(row.root) {
        private var pendingDrag: Runnable? = null

        @SuppressLint("ClickableViewAccessibility")
        fun bind(task: CareTaskSummary, isLast: Boolean) {
            pendingDrag?.let(row.taskDragHandle::removeCallbacks)
            pendingDrag = null
            val context = row.root.context
            val overdue = !task.isCompleted && task.dueDateEpochDay < todayEpochDay
            val color = PetColor.fromIndex(task.petColorIndex).color(context)

            row.root.visibility = View.VISIBLE
            row.root.layoutParams = row.root.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            row.nowMarkerContainer.visibility = if (nowBeforeTaskId == task.id) View.VISIBLE else View.GONE
            row.nowTimeText.text = formatTime(nowMinutes())
            row.taskConnector.visibility = if (isLast) View.INVISIBLE else View.VISIBLE

            row.taskTimeText.visibility = if (overdue) View.GONE else View.VISIBLE
            row.taskDueDateText.visibility = if (overdue) View.VISIBLE else View.GONE
            row.taskTimeText.text = formatTime(task.reminderMinutesOfDay)
            if (overdue) row.taskDueDateText.text = formatDateBlock(task.dueDateEpochDay)
            row.taskTimeText.setTextColor(ContextCompat.getColor(context,
                when { overdue -> R.color.error; task.isCompleted -> R.color.text_secondary; else -> R.color.text_primary }))

            row.taskTitleText.text = task.title
            row.taskTitleText.paintFlags = if (task.isCompleted)
                row.taskTitleText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else row.taskTitleText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            row.taskTitleText.setTextColor(ContextCompat.getColor(context,
                if (task.isCompleted) R.color.text_secondary else R.color.text_primary))
            row.taskPetText.text = task.petName
            row.taskPetDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            row.taskCategoryIcon.contentDescription = task.category
            row.taskOverdueText.visibility = if (overdue) View.VISIBLE else View.GONE

            row.taskLocationChip.visibility = if (task.latitude != null && task.longitude != null) View.VISIBLE else View.GONE
            row.taskLocationChip.setOnClickListener {
                val lat = task.latitude ?: return@setOnClickListener
                val lon = task.longitude ?: return@setOnClickListener
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon")))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.no_compatible_app, Toast.LENGTH_SHORT).show()
                }
            }

            row.completeTaskButton.strokeColor = ColorStateList.valueOf(color)
            row.completeTaskButton.backgroundTintList = ColorStateList.valueOf(if (task.isCompleted) color else Color.TRANSPARENT)
            row.completeTaskButton.icon = if (task.isCompleted) AppCompatResources.getDrawable(context, R.drawable.ic_check) else null
            row.completeTaskButton.iconTint = ColorStateList.valueOf(Color.BLACK)
            row.completeTaskButton.contentDescription = context.getString(
                if (task.isCompleted) R.string.task_reopen_content_description else R.string.task_done_content_description
            )
            row.completeTaskButton.setOnClickListener {
                GestureHaptics.confirm(it)
                if (task.isCompleted) onReopen(task) else onComplete(task)
            }

            if (restoringTaskId == task.id) {
                restoringTaskId = null
                RowMotion.expand(row.root)
            }
            row.taskDragHandle.visibility = if (!task.isCompleted && reorderMode) View.VISIBLE else View.GONE
            row.taskDragHandle.setOnLongClickListener { GestureHaptics.confirm(it); onDrag(this); true }
            row.taskDragHandle.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> pendingDrag = Runnable {
                        if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                            GestureHaptics.confirm(view)
                            onDrag(this)
                        }
                    }.also { view.postDelayed(it, 350L) }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        pendingDrag?.let(view::removeCallbacks)
                        pendingDrag = null
                    }
                }
                true
            }

            val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onOpen(task); return true }
                override fun onDoubleTap(e: MotionEvent): Boolean { GestureHaptics.confirm(row.root); onEdit(task); return true }
                override fun onLongPress(e: MotionEvent) { GestureHaptics.confirm(row.root); onShare(task) }
            })
            row.root.isClickable = true
            row.root.setOnTouchListener { _, event -> detector.onTouchEvent(event); false }
        }

        private fun formatDateBlock(day: Long): String = SimpleDateFormat("EEE\nd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(day * DAY_MILLIS))

        /** Keeps the fixed 44dp instrument column stable across 12/24-hour device settings. */
        private fun formatTime(minutes: Int): String = String.format(
            Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60
        )
    }

    companion object {
        private const val DAY_MILLIS = 86_400_000L
        private fun nowMinutes(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        private val DIFF = object : DiffUtil.ItemCallback<CareTaskSummary>() {
            override fun areItemsTheSame(a: CareTaskSummary, b: CareTaskSummary) = a.id == b.id
            override fun areContentsTheSame(a: CareTaskSummary, b: CareTaskSummary) = a == b
        }
    }
}
