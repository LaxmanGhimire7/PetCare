package com.example.petcare.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.petcare.R

/**
 * Uses bundled fonts immediately, then upgrades visible text to provider fonts if Play
 * services supplies them. Provider failure leaves the bundled faces in place offline.
 */
class BrandFonts(private val context: Context) {
    private var manrope: Typeface? = null
    private var fraunces: Typeface? = null
    private var currentRoot: View? = null

    fun load() {
        val handler = Handler(Looper.getMainLooper())
        ResourcesCompat.getFont(context, R.font.manrope_downloadable, object : ResourcesCompat.FontCallback() {
            override fun onFontRetrieved(typeface: Typeface) {
                manrope = typeface
                currentRoot?.let(::applyTo)
            }
            override fun onFontRetrievalFailed(reason: Int) = Unit
        }, handler)
        ResourcesCompat.getFont(context, R.font.fraunces_downloadable, object : ResourcesCompat.FontCallback() {
            override fun onFontRetrieved(typeface: Typeface) {
                fraunces = typeface
                currentRoot?.let(::applyTo)
            }
            override fun onFontRetrievalFailed(reason: Int) = Unit
        }, handler)
    }

    fun applyTo(root: View) {
        currentRoot = root
        visit(root)
    }

    private fun visit(view: View) {
        if (view is TextView) {
            val display = view.id == R.id.auth_display_title ||
                view.id == R.id.pet_form_title ||
                view.id == R.id.care_task_form_title
            val face = if (display) fraunces else manrope
            face?.let { view.typeface = Typeface.create(it, view.typeface?.style ?: Typeface.NORMAL) }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
    }
}
