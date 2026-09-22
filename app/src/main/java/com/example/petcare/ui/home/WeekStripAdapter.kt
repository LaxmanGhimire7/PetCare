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
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Seven-day selector for Today; selection is retained by the screen ViewModel. */
class WeekStripAdapter(private val onSelect: (Long) -> Unit) : RecyclerView.Adapter<WeekStripAdapter.Holder>() {
    private var days: List<Long> = emptyList()
    private var selected = LocalDayClock.todayEpochDay()
    private var taskDays: Set<Long> = emptySet()

    fun submit(selectedDay: Long, daysWithTasks: Set<Long>) {
        selected = selectedDay
        taskDays = daysWithTasks
        val start = LocalDayClock.weekStart(selectedDay)
        days = (0L..6L).map { start + it }
        notifyDataSetChanged()
    }

    override fun getItemCount() = days.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemWeekDayBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(days[position])

    inner class Holder(private val row: ItemWeekDayBinding) : RecyclerView.ViewHolder(row.root) {
        fun bind(day: Long) {
            val context = row.root.context
            val isSelected = day == selected
            val date = Date(day * DAY_MILLIS)
            row.weekDayName.text = weekdayFormat.format(date)
            row.weekDayNumber.text = dayFormat.format(date)
            val foreground = ContextCompat.getColor(context,
                if (isSelected) R.color.on_primary else R.color.text_primary)
            row.weekDayName.setTextColor(foreground)
            row.weekDayNumber.setTextColor(foreground)
            row.root.background = GradientDrawable().apply {
                cornerRadius = context.resources.getDimension(R.dimen.radius_input)
                setColor(if (isSelected) ContextCompat.getColor(context, R.color.primary) else Color.TRANSPARENT)
            }
            row.weekTaskDot.visibility = if (day in taskDays) View.VISIBLE else View.INVISIBLE
            row.weekTaskDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context,
                    if (isSelected) R.color.on_primary else R.color.text_secondary))
            }
            row.root.contentDescription = DateFormat.getDateInstance(DateFormat.FULL).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(date)
            row.root.setOnClickListener { onSelect(day) }
        }
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        val weekdayFormat = SimpleDateFormat("EEE", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dayFormat = SimpleDateFormat("d", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
