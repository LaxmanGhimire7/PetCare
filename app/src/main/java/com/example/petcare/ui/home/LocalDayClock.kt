package com.example.petcare.ui.home

import java.util.Calendar
import java.util.TimeZone

/** Calendar helpers that preserve the app's epoch-day convention on every supported API. */
object LocalDayClock {
    private const val DAY_MILLIS = 86_400_000L

    fun todayEpochDay(): Long {
        val local = Calendar.getInstance()
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis / DAY_MILLIS
    }

    /** Returns the Monday that begins the week containing the stored epoch day. */
    fun weekStart(day: Long): Long {
        val utc = utcCalendar(day)
        val daysSinceMonday = (utc.get(Calendar.DAY_OF_WEEK) + 5) % 7
        return day - daysSinceMonday
    }

    /** Converts a stored local date and minute into an instant in the device time zone. */
    fun dueMillis(day: Long, minutes: Int): Long {
        val utc = utcCalendar(day)
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH),
                minutes / 60, minutes % 60, 0)
        }.timeInMillis
    }

    fun utcCalendar(day: Long): Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = day * DAY_MILLIS
    }
}
