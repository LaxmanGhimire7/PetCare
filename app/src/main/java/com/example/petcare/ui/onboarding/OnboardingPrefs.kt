package com.example.petcare.ui.onboarding

import android.content.Context
import com.example.petcare.data.local.AuthPreferences

/** Tracks onboarding per local account, including users who add a second account later. */
class OnboardingPrefs(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("petcare_onboarding", Context.MODE_PRIVATE)
    fun isComplete(ownerId: Long = AuthPreferences(app).ownerId()): Boolean =
        ownerId > 0 && prefs.getBoolean("complete_$ownerId", false)
    fun markComplete(ownerId: Long = AuthPreferences(app).ownerId()) {
        if (ownerId > 0) prefs.edit().putBoolean("complete_$ownerId", true).apply()
    }
}
