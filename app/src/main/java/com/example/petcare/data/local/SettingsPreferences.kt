package com.example.petcare.data.local

import android.content.Context
import com.example.petcare.design.ThemePrefs

/** Small device preferences shared by Settings, notifications and startup. */
class SettingsPreferences(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "petcare_settings", Context.MODE_PRIVATE
    )

    fun themeMode(): Int = ThemePrefs.saved(context)

    fun setThemeMode(mode: Int) {
        ThemePrefs.save(context, mode)
    }

    fun notificationsEnabled(): Boolean = prefs.getBoolean("notifications_enabled", true)
    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }
}
