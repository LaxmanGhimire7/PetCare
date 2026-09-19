package com.example.petcare.data.local

import android.content.Context

/** Remembers a caregiver for each pet without requesting the full contacts permission. */
class CaregiverPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(petId: Long): Pair<String, String>? {
        val name = preferences.getString("name_$petId", null) ?: return null
        val number = preferences.getString("number_$petId", null) ?: return null
        return name to number
    }

    fun put(petIds: Collection<Long>, name: String, number: String) {
        preferences.edit().apply {
            petIds.forEach { id ->
                putString("name_$id", name)
                putString("number_$id", number)
            }
        }.apply()
    }

    private companion object { const val PREFS_NAME = "petcare_caregivers" }
}
