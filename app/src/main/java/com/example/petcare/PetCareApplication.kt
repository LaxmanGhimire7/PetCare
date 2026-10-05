package com.example.petcare

import android.app.Application
import com.example.petcare.design.ThemePrefs
import com.example.petcare.design.PcNotifier

/** Applies the saved Pit Lane theme without wallpaper-derived dynamic colour. */
class PetCareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePrefs.applySaved(this)
        PcNotifier.createChannels(this)
    }
}
