package com.example.petcare.ui.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.petcare.R
import com.example.petcare.databinding.ItemWeekDayBinding
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Seven-day selector for Today; selection is retained by the screen ViewModel. */
class WeekStripAdapter(private val onSelect: (Long) -> Unit) : RecyclerView.Adapter<WeekStripAdapter.Holder>() {
    private var days: List<LocalDate> = emptyList()
    private var selected = LocalDate.now().toEpochDay()
    private var taskDays: Set<Long> = emptySet()

    fun submit(selectedDay: Long, daysWithTasks: Set<Long>) {
        selected = selectedDay
        taskDays = daysWithTasks
        val date = LocalDate.ofEpochDay(selectedDay)
        val start = date.minusDays((date.dayOfWeek.value - 1).toLong())
        days = (0L..6L).map(start::plusDays)
        notifyDataSetChanged()
    }

    override fun getItemCount() = days.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemWeekDayBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(days[position])

    inner class Holder(private val row: ItemWeekDayBinding) : RecyclerView.ViewHolder(row.root) {
        fun bind(day: LocalDate) {
            val context = row.root.context
            val isSelected = day.toEpochDay() == selected
            row.weekDayName.text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            row.weekDayNumber.text = day.dayOfMonth.toString()
            val foreground = ContextCompat.getColor(context, if (isSelected) R.color.on_primary else R.color.text_primary)
            row.weekDayName.setTextColor(foreground)
            row.weekDayNumber.setTextColor(foreground)
            row.root.background = GradientDrawable().apply {
                cornerRadius = context.resources.getDimension(R.dimen.radius_input)
                setColor(if (isSelected) ContextCompat.getColor(context, R.color.primary) else Color.TRANSPARENT)
            }
            row.weekTaskDot.visibility = if (day.toEpochDay() in taskDays) View.VISIBLE else View.INVISIBLE
            row.weekTaskDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, if (isSelected) R.color.on_primary else R.color.text_secondary))
            }
            row.root.contentDescription = day.toString()
            row.root.setOnClickListener { onSelect(day.toEpochDay()) }
        }
    }
}
