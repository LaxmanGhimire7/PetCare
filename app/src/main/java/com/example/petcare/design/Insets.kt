package com.example.petcare.design

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Pads a screen's root by the status bar (and display cutout) height, so content never
 * draws under the status bar. Apply once per screen root. Do NOT use this on the
 * BottomNavigationView: it handles its own bottom inset.
 */
fun View.applySystemBarTopPadding() {
    val basePaddingTop = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val top = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        ).top
        view.updatePadding(top = basePaddingTop + top)
        insets
    }
    if (isAttachedToWindow) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                ViewCompat.requestApplyInsets(v)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}

/**
 * For full-screen pages without the bottom navigation (welcome, log in, sign up, reset):
 * pads the top by the status bar and the bottom by the navigation bar, or by the
 * keyboard while it's open, so buttons and fields are never hidden behind either.
 */
fun View.applySystemBarPaddingWithKeyboard() {
    val baseTop = paddingTop
    val baseBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        view.updatePadding(top = baseTop + bars.top, bottom = baseBottom + maxOf(bars.bottom, ime.bottom))
        insets
    }
    if (isAttachedToWindow) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                ViewCompat.requestApplyInsets(v)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}
