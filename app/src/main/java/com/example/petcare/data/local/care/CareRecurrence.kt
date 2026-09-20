package com.example.petcare.data.local.care

import java.util.Calendar
import java.util.TimeZone

/** Advances a care date in the UTC epoch-day convention stored by Room. */
object CareRecurrence {
    fun nextDate(day: Long, frequency: String): Long? {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = day * MILLIS_PER_DAY
        }
        when (frequency) {
            CARE_FREQUENCY_DAILY -> calendar.add(Calendar.DAY_OF_MONTH, 1)
            CARE_FREQUENCY_WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            CARE_FREQUENCY_MONTHLY -> {
                // Calendar.add clamps the day when the next month is shorter (Jan 31 → Feb 28/29).
                calendar.add(Calendar.MONTH, 1)
            }
            else -> return null
        }
        return calendar.timeInMillis / MILLIS_PER_DAY
    }

    private const val MILLIS_PER_DAY = 86_400_000L
}
