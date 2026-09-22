package com.example.petcare

import android.app.Application
import com.example.petcare.data.local.SettingsPreferences
import androidx.appcompat.app.AppCompatDelegate

/** Applies the saved theme; wallpaper colours remain opt-in in the redesigned interface. */
class PetCareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(SettingsPreferences(this).themeMode())
    }
}
