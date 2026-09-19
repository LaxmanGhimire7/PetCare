package com.example.petcare.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** Gives the same threshold response across API 24 and newer devices. */
object GestureHaptics {
    fun confirm(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.KEYBOARD_TAP
        )
    }

    fun reject(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.KEYBOARD_TAP
        )
    }
}
