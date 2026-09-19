package com.example.petcare.ui

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup

/** Collapses a removed list row and expands it again when Undo restores the record. */
object RowMotion {
    fun collapse(view: View, onEnd: () -> Unit) {
        if (!MotionPrefs.animationsEnabled(view.context) || view.height == 0) {
            onEnd()
            return
        }
        val height = view.height
        ValueAnimator.ofInt(height, 0).apply {
            duration = 200L
            addUpdateListener {
                view.layoutParams = view.layoutParams.apply { this.height = it.animatedValue as Int }
            }
            doOnFinish {
                view.visibility = View.INVISIBLE
                onEnd()
            }
            start()
        }
    }

    fun expand(view: View) {
        if (!MotionPrefs.animationsEnabled(view.context)) return
        view.post {
            val target = view.height
            if (target == 0) return@post
            view.layoutParams = view.layoutParams.apply { height = 0 }
            ValueAnimator.ofInt(0, target).apply {
                duration = 200L
                addUpdateListener {
                    view.layoutParams = view.layoutParams.apply { height = it.animatedValue as Int }
                }
                doOnFinish {
                    view.layoutParams = view.layoutParams.apply {
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                }
                start()
            }
        }
    }

    private fun ValueAnimator.doOnFinish(action: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = action()
        })
    }
}
