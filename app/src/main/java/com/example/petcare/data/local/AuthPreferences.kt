package com.example.petcare.data.local

import android.content.Context

/** Persists only a session marker and retains old credentials until they are migrated. */
class AuthPreferences(context: Context) {
    private val biometric = BiometricPreferences(context)
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME, Context.MODE_PRIVATE
    )

    fun ownerId(): Long = transientOwnerId.takeIf { it > 0 }
        ?: preferences.getLong(KEY_OWNER_ID, 0)

    fun isSignedIn(): Boolean {
        val id = ownerId()
        if (id > 0) return !biometric.isEnabledFor(id) || unlockedThisProcess
        return preferences.getBoolean(KEY_LEGACY_SIGNED_IN, false) && legacyEmail() != null
    }

    fun userName(): String? = transientName ?: preferences.getString(KEY_NAME, null)

    fun setSession(ownerId: Long, name: String, staySignedIn: Boolean) {
        transientOwnerId = ownerId
        transientName = name
        unlockedThisProcess = true
        preferences.edit()
            .putLong(KEY_OWNER_ID, if (staySignedIn) ownerId else 0)
            .putString(KEY_NAME, if (staySignedIn) name else null)
            .putBoolean(KEY_STAY_SIGNED_IN, staySignedIn)
            .apply()
    }

    fun staySignedIn(): Boolean = preferences.getBoolean(KEY_STAY_SIGNED_IN, true)

    fun signOut() {
        transientOwnerId = 0
        transientName = null
        unlockedThisProcess = false
        preferences.edit().remove(KEY_OWNER_ID).remove(KEY_NAME)
            .putBoolean(KEY_LEGACY_SIGNED_IN, false).apply()
    }

    fun legacyEmail(): String? = preferences.getString(KEY_LEGACY_EMAIL, null)
    fun legacyName(): String? = preferences.getString(KEY_LEGACY_NAME, null)
    fun legacyHash(): String? = preferences.getString(KEY_LEGACY_HASH, null)
    fun legacyWasSignedIn(): Boolean = preferences.getBoolean(KEY_LEGACY_SIGNED_IN, false)

    fun clearLegacyCredentials() {
        preferences.edit().remove(KEY_LEGACY_EMAIL).remove(KEY_LEGACY_HASH)
            .remove(KEY_LEGACY_SIGNED_IN).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "petcare_auth"
        const val KEY_OWNER_ID = "owner_id"
        const val KEY_NAME = "name"
        const val KEY_STAY_SIGNED_IN = "stay_signed_in"
        const val KEY_LEGACY_NAME = "name"
        const val KEY_LEGACY_EMAIL = "email"
        const val KEY_LEGACY_HASH = "password_hash"
        const val KEY_LEGACY_SIGNED_IN = "signed_in"
        @Volatile var transientOwnerId: Long = 0
        @Volatile var transientName: String? = null
        @Volatile var unlockedThisProcess: Boolean = false
    }
}
