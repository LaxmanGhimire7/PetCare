package com.example.petcare

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.example.petcare.data.local.SettingsPreferences
import androidx.appcompat.app.AppCompatDelegate

/** Applies dynamic Material chrome where the device supports it. Pet tag colours remain fixed. */
class PetCareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(SettingsPreferences(this).themeMode())
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
