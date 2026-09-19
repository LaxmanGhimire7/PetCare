package com.example.petcare.ui

import androidx.fragment.app.Fragment
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis

/** Shared navigation motion, skipped when the device disables animator duration. */
object MotionTransitions {
    fun sibling(fragment: Fragment) {
        if (!MotionPrefs.animationsEnabled(fragment.requireContext())) return
        fragment.enterTransition = MaterialFadeThrough().apply { duration = 300L }
        fragment.exitTransition = MaterialFadeThrough().apply { duration = 300L }
        fragment.reenterTransition = MaterialFadeThrough().apply { duration = 300L }
    }

    fun forward(fragment: Fragment) {
        if (!MotionPrefs.animationsEnabled(fragment.requireContext())) return
        fragment.enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true).apply {
            duration = 300L
        }
        fragment.returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false).apply {
            duration = 300L
        }
    }
}
