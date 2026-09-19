package com.example.petcare

import android.app.Application
import com.google.android.material.color.DynamicColors

/** Applies dynamic Material chrome where the device supports it. Pet tag colours remain fixed. */
class PetCareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
