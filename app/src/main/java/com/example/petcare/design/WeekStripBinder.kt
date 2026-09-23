package com.example.petcare.design

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.example.petcare.R

/** One day cell. [weekday] e.g. "Tue", [date] e.g. "22". */
data class WeekDay(
    val weekday: String,
    val date: String,
    val hasTasks: Boolean,
    val selected: Boolean,
    val isToday: Boolean,
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

    private fun bindCell(cell: View, day: WeekDay, onClick: () -> Unit) {
        val ctx = cell.context
        val name: TextView = cell.findViewById(R.id.pcDayName)
        val date: TextView = cell.findViewById(R.id.pcDayDate)
        val dot: View = cell.findViewById(R.id.pcDayDot)

        name.text = day.weekday
        date.text = day.date

        if (day.selected) {
            cell.setBackgroundResource(R.drawable.pc_bg_week_selected)
            val onPrimary = ContextCompat.getColor(ctx, R.color.pc_on_primary)
            name.setTextColor(onPrimary)
            date.setTextColor(onPrimary)
            ViewCompat.setBackgroundTintList(dot, ColorStateList.valueOf(onPrimary))
        } else {
            cell.background = null
            name.setTextColor(ContextCompat.getColor(ctx, R.color.pc_text_secondary))
            date.setTextColor(
                ContextCompat.getColor(ctx, if (day.isToday) R.color.pc_primary_text else R.color.pc_text_primary),
            )
            ViewCompat.setBackgroundTintList(
                dot,
                ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.pc_text_secondary)),
            )
        }
        dot.visibility = if (day.hasTasks) View.VISIBLE else View.INVISIBLE
        cell.isSelected = day.selected
        cell.contentDescription = "${day.weekday} ${day.date}"
        cell.setOnClickListener {
            Haptics.tick(it)
            onClick()
        }
    }
}
