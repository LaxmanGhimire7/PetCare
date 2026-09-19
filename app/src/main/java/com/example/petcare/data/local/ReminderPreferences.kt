package com.example.petcare.data.local

import android.content.Context
import com.example.petcare.data.local.care.DEFAULT_REMINDER_MINUTES_OF_DAY

class ReminderPreferences(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun defaultReminderMinutesOfDay(): Int = preferences.getInt(
        DEFAULT_REMINDER_TIME_KEY,
        DEFAULT_REMINDER_MINUTES_OF_DAY
    )

    fun setDefaultReminderMinutesOfDay(minutesOfDay: Int) {
        preferences.edit().putInt(DEFAULT_REMINDER_TIME_KEY, minutesOfDay).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "reminder_preferences"
        const val DEFAULT_REMINDER_TIME_KEY = "default_reminder_time"
    }
}
