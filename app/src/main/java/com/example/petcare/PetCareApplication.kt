package com.example.petcare

import android.app.Application
import com.example.petcare.data.local.SettingsPreferences
import androidx.appcompat.app.AppCompatDelegate

/** Applies the saved Pit Lane theme without wallpaper-derived dynamic colour. */
class PetCareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(SettingsPreferences(this).themeMode())
    }
}
