package com.example.petcare.data.local

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Small device preferences shared by Settings, notifications and startup. */
class SettingsPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "petcare_settings", Context.MODE_PRIVATE
    )

    fun themeMode(): Int = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt("theme_mode", mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun notificationsEnabled(): Boolean = prefs.getBoolean("notifications_enabled", true)
    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }
}
