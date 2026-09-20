package com.example.petcare.ui

import android.content.Context

/** Records whether the short gesture guide has been seen and allows Settings to replay it. */
class GestureCoachPrefs(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun shouldShow(): Boolean = !preferences.getBoolean(SEEN, false) ||
        preferences.getBoolean(REPLAY, false)

    fun requestReplay() {
        preferences.edit().putBoolean(REPLAY, true).apply()
    }

    fun markSeen() {
        preferences.edit().putBoolean(SEEN, true).putBoolean(REPLAY, false).apply()
    }

    private companion object {
        const val PREFS = "gesture_coach"
        const val SEEN = "seen"
        const val REPLAY = "replay"
    }
}
