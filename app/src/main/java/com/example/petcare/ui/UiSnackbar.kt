package com.example.petcare.ui

import android.view.View
import androidx.annotation.StringRes
import com.example.petcare.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar

/** Creates consistently anchored snackbars above the visible app navigation or task FAB. */
object UiSnackbar {
    fun make(view: View, text: CharSequence, duration: Int): Snackbar =
        anchor(Snackbar.make(view, text, duration), view)

    fun make(view: View, @StringRes text: Int, duration: Int): Snackbar =
        anchor(Snackbar.make(view, text, duration), view)

    private fun anchor(snackbar: Snackbar, source: View): Snackbar {
        val root = source.rootView
        val fab = root.findViewById<View?>(R.id.pcTodayFab)
        val navigation = root.findViewById<BottomNavigationView?>(R.id.bottom_navigation)
        val target = fab?.takeIf { it.isShown && it.visibility == View.VISIBLE } ?: navigation?.takeIf { it.isShown }
        if (target != null) snackbar.setAnchorView(target)
        snackbar.setBackgroundTint(source.context.getColor(R.color.surface_raised))
        snackbar.setTextColor(source.context.getColor(R.color.text_primary))
        snackbar.setActionTextColor(source.context.getColor(R.color.primary))
        return snackbar
    }
}
