package com.example.petcare.data.local

import android.content.Context

/** Remembers which local account opted into device authentication. */
class BiometricPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "petcare_biometric", Context.MODE_PRIVATE
    )

    fun enabledUserId(): Long = prefs.getLong("enabled_user_id", 0)
    fun isEnabledFor(userId: Long): Boolean = userId > 0 && enabledUserId() == userId
    fun setEnabledFor(userId: Long, enabled: Boolean) {
        prefs.edit().putLong("enabled_user_id", if (enabled) userId else 0).apply()
    }
}
