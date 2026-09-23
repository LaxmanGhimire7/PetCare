package com.example.petcare.design

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Stores the user's appearance choice. Dark is the default.
 * Call [applySaved] in Application.onCreate (or before super.onCreate in your launcher activity).
 */
object ThemePrefs {
    private const val PREFS = "pc_appearance"
    private const val KEY_MODE = "night_mode"

    fun applySaved(context: Context) {
        AppCompatDelegate.setDefaultNightMode(saved(context))
    }

    /** One of AppCompatDelegate.MODE_NIGHT_YES / MODE_NIGHT_NO / MODE_NIGHT_FOLLOW_SYSTEM. */
    fun saved(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_MODE, AppCompatDelegate.MODE_NIGHT_YES)

    fun save(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MODE, mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
