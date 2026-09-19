package com.example.petcare.ui

import android.content.Context
import android.provider.Settings

/** Central reduced-motion check shared by all non-essential UI animations. */
object MotionPrefs {
    fun animationsEnabled(context: Context): Boolean =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
}
