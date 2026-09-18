package com.example.petcare.data.local

import android.content.Context
import java.security.MessageDigest

class AuthPreferences(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun register(name: String, email: String, password: String): Boolean {
        if (hasRegisteredAccount()) {
            return false
        }

        preferences.edit()
            .putString(KEY_NAME, name)
            .putString(KEY_EMAIL, email.normalizedEmail())
            .putString(KEY_PASSWORD_HASH, password.sha256())
            .putBoolean(KEY_SIGNED_IN, true)
            .apply()
        return true
    }

    fun signIn(email: String, password: String): Boolean {
        val isValidAccount = preferences.getString(KEY_EMAIL, null) == email.normalizedEmail() &&
            preferences.getString(KEY_PASSWORD_HASH, null) == password.sha256()

        if (isValidAccount) {
            preferences.edit().putBoolean(KEY_SIGNED_IN, true).apply()
        }

        return isValidAccount
    }

    fun isSignedIn(): Boolean = preferences.getBoolean(KEY_SIGNED_IN, false)

    fun userName(): String? = preferences.getString(KEY_NAME, null)

    fun signOut() {
        preferences.edit().putBoolean(KEY_SIGNED_IN, false).apply()
    }

    private fun hasRegisteredAccount(): Boolean = preferences.contains(KEY_EMAIL)

    private fun String.normalizedEmail(): String = trim().lowercase()

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val PREFERENCES_NAME = "petcare_auth"
        const val KEY_NAME = "name"
        const val KEY_EMAIL = "email"
        const val KEY_PASSWORD_HASH = "password_hash"
        const val KEY_SIGNED_IN = "signed_in"
    }
}
