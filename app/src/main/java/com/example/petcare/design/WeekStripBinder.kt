package com.example.petcare.design

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.petcare.R

/**
 * One day cell. [weekday] e.g. "Tue", [date] e.g. "22".
 * [progress] = share of that day's tasks done (0..1); the bar hides when [hasTasks] is false.
 */
data class WeekDay(
    val weekday: String,
    val date: String,
    val hasTasks: Boolean,
    val selected: Boolean,
    val isToday: Boolean,
    val progress: Float = 0f,
)

/** Fills the week strip container with seven day cells and applies the selected state. */
object WeekStripBinder {

    fun bind(container: LinearLayout, days: List<WeekDay>, onSelect: (index: Int) -> Unit) {
        val inflater = LayoutInflater.from(container.context)
        while (container.childCount < days.size) {
            container.addView(inflater.inflate(R.layout.pc_item_week_day, container, false))
        }
        while (container.childCount > days.size) {
            container.removeViewAt(container.childCount - 1)
        }
        days.forEachIndexed { index, day ->
            bindCell(container.getChildAt(index), day) { onSelect(index) }
        }
    }

    private fun bindBar(bar: SegmentedBarView, day: WeekDay, fill: Int) {
        val done = day.progress.coerceIn(0f, 1f)
        bar.setSegments(
            listOf(SegmentedBarView.Segment(done, fill), SegmentedBarView.Segment(1f - done, null)),
            animate = false,
        )
    }

    private const val SELECTED_TRACK_ALPHA = 64

    private fun bindCell(cell: View, day: WeekDay, onClick: () -> Unit) {
        val ctx = cell.context
        val name: TextView = cell.findViewById(R.id.pcDayName)
        val date: TextView = cell.findViewById(R.id.pcDayDate)
        val bar: SegmentedBarView = cell.findViewById(R.id.pcDayBar)
        bar.gapPx = 0f

        name.text = day.weekday
        date.text = day.date

        if (day.selected) {
            cell.setBackgroundResource(R.drawable.pc_bg_week_selected)
            val onPrimary = ContextCompat.getColor(ctx, R.color.pc_on_primary)
            name.setTextColor(onPrimary)
            date.setTextColor(onPrimary)
            bar.setTrackColor(ColorUtils.setAlphaComponent(onPrimary, SELECTED_TRACK_ALPHA))
            bindBar(bar, day, onPrimary)
        } else {
            cell.background = null
            name.setTextColor(ContextCompat.getColor(ctx, R.color.pc_text_secondary))
            date.setTextColor(
                ContextCompat.getColor(ctx, if (day.isToday) R.color.pc_primary_text else R.color.pc_text_primary),
            )
            bar.setTrackColor(ContextCompat.getColor(ctx, R.color.pc_track))
            bindBar(bar, day, ContextCompat.getColor(ctx, R.color.pc_text_primary))
        }
        bar.visibility = if (day.hasTasks) View.VISIBLE else View.INVISIBLE
        cell.isSelected = day.selected
        cell.contentDescription = "${day.weekday} ${day.date}"
        cell.setOnClickListener {
            Haptics.tick(it)
            onClick()
        }
    }
}
