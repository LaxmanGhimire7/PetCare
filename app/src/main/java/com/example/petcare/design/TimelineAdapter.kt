package com.example.petcare.design

import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.petcare.R

/** What the timeline shows. Build the list with [TimelineBuilder]. */
sealed interface TimelineItem {
    val key: String

    /**
     * One task. [timeLabel] should be 24-hour "HH:mm" for today's tasks; for overdue tasks
     * from earlier days pass a short date such as "Mon".
     */
    data class Task(
        val id: Long,
        val dueAtMillis: Long,
        val timeLabel: String,
        val title: String,
        val petName: String,
        val petColor: PetColor,
        @DrawableRes val categoryIcon: Int,
        val done: Boolean,
        val overdue: Boolean,
    ) : TimelineItem {
        override val key: String get() = "task_$id"
    }

    /** The live orange "Now" line. Only when the selected day is today. */
    data class Now(val timeLabel: String) : TimelineItem {
        override val key: String get() = "now"
    }
}

object TimelineBuilder {
    /**
     * Orders tasks strictly by due time, NEVER by status. That is what keeps a ticked task
     * exactly where it was. Inserts the Now marker when [nowMillis] and [nowLabel] are given.
     */
    fun build(tasks: List<TimelineItem.Task>, nowMillis: Long?, nowLabel: String?): List<TimelineItem> {
        val ordered = tasks.sortedBy { it.dueAtMillis }
        if (nowMillis == null || nowLabel == null) return ordered
        val firstFuture = ordered.indexOfFirst { it.dueAtMillis > nowMillis }
        val insertAt = if (firstFuture < 0) ordered.size else firstFuture
        return ArrayList<TimelineItem>(ordered.size + 1).apply {
            addAll(ordered)
            add(insertAt, TimelineItem.Now(nowLabel))
        }
    }
}

/**
 * Timeline adapter. Tapping a node flips it in place with a small pop and calls [onToggle];
 * your ViewModel writes to Room, and the next list keeps the row in the same position.
 */
class TimelineAdapter(
    private val onToggle: (task: TimelineItem.Task, done: Boolean) -> Unit,
    private val onOpen: (task: TimelineItem.Task) -> Unit,
) : ListAdapter<TimelineItem, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is TimelineItem.Task -> TYPE_TASK
        is TimelineItem.Now -> TYPE_NOW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_TASK) {
            TaskHolder(inflater.inflate(R.layout.pc_item_timeline, parent, false))
        } else {
            NowHolder(inflater.inflate(R.layout.pc_item_now_marker, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TimelineItem.Task -> (holder as TaskHolder).bind(item, hasTaskBelow(position))
            is TimelineItem.Now -> (holder as NowHolder).bind(item)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_CONNECTOR } && holder is TaskHolder) {
            holder.bindConnector(hasTaskBelow(position))
            return
        }
        onBindViewHolder(holder, position)
    }

    /** Re-evaluates which rows draw a connector after the list changes (e.g. Now moved). */
    fun refreshConnectors() {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, PAYLOAD_CONNECTOR)
    }

    /** For your ItemTouchHelper: only task rows can be swiped, never the Now marker. */
    fun taskAt(position: Int): TimelineItem.Task? =
        if (position in 0 until itemCount) getItem(position) as? TimelineItem.Task else null

    private fun hasTaskBelow(position: Int): Boolean =
        position + 1 < itemCount && getItem(position + 1) is TimelineItem.Task

    private inner class TaskHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val time: TextView = view.findViewById(R.id.pcTimelineTime)
        private val node: TimelineNodeView = view.findViewById(R.id.pcTimelineNode)
        private val title: TextView = view.findViewById(R.id.pcTimelineTitle)
        private val petDot: View = view.findViewById(R.id.pcTimelinePetDot)
        private val petName: TextView = view.findViewById(R.id.pcTimelinePetName)
        private val category: ImageView = view.findViewById(R.id.pcTimelineCategory)
        private val overdue: TextView = view.findViewById(R.id.pcTimelineOverdue)

        fun bind(item: TimelineItem.Task, connector: Boolean) {
            val ctx = itemView.context
            val petColor = item.petColor.main(ctx)
            val isOverdue = item.overdue && !item.done

            time.text = item.timeLabel
            time.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    when {
                        isOverdue -> R.color.pc_error
                        item.done -> R.color.pc_text_secondary
                        else -> R.color.pc_text_primary
                    },
                ),
            )

            node.bind(petColor, item.done, connector)
            node.contentDescription = ctx.getString(
                if (item.done) R.string.pc_a11y_mark_not_done else R.string.pc_a11y_mark_done,
                item.title,
            )
            ViewCompat.setStateDescription(
                node,
                ctx.getString(if (item.done) R.string.pc_state_done else R.string.pc_state_not_done),
            )
            node.setOnClickListener {
                val nowDone = !item.done
                Haptics.confirm(it)
                node.animateDone(nowDone)
                onToggle(item, nowDone)
            }

            title.text = item.title
            title.paintFlags = if (item.done) {
                title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            title.setTextColor(
                ContextCompat.getColor(ctx, if (item.done) R.color.pc_text_secondary else R.color.pc_text_primary),
            )

            ViewCompat.setBackgroundTintList(petDot, ColorStateList.valueOf(petColor))
            petName.text = item.petName

            if (item.categoryIcon != 0) {
                category.visibility = View.VISIBLE
                category.setImageResource(item.categoryIcon)
            } else {
                category.visibility = View.GONE
            }

            overdue.visibility = if (isOverdue) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onOpen(item) }
            expandNodeTouchTarget()
        }

        fun bindConnector(show: Boolean) = node.setConnector(show)

        /** The node is 20dp to match the design; this makes the tap target 48dp. */
        private fun expandNodeTouchTarget() {
            val parent = node.parent as? View ?: return
            parent.post {
                val target = (48 * itemView.resources.displayMetrics.density).toInt()
                val rect = Rect()
                node.getHitRect(rect)
                val padX = ((target - rect.width()) / 2).coerceAtLeast(0)
                val padY = ((target - rect.height()) / 2).coerceAtLeast(0)
                rect.inset(-padX, -padY)
                parent.touchDelegate = TouchDelegate(rect, node)
            }
        }
    }

    private class NowHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val time: TextView = view.findViewById(R.id.pcNowTime)

        fun bind(item: TimelineItem.Now) {
            time.text = item.timeLabel
            itemView.contentDescription = itemView.context.getString(R.string.pc_a11y_now, item.timeLabel)
        }
    }

    private object Diff : DiffUtil.ItemCallback<TimelineItem>() {
        override fun areItemsTheSame(oldItem: TimelineItem, newItem: TimelineItem) = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: TimelineItem, newItem: TimelineItem) = oldItem == newItem

        // A payload stops RecyclerView cross-fading the row, so the tick "pop" isn't interrupted.
        override fun getChangePayload(oldItem: TimelineItem, newItem: TimelineItem): Any = PAYLOAD_REBIND
    }

    private companion object {
        const val TYPE_TASK = 1
        const val TYPE_NOW = 2
        const val PAYLOAD_REBIND = "rebind"
        const val PAYLOAD_CONNECTOR = "connector"
    }
}
