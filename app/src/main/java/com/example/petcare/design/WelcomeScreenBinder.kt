package com.example.petcare.design

import android.content.res.ColorStateList
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.button.MaterialButton
import com.example.petcare.R

/**
 * Binds pc_fragment_welcome.xml, the app's front page. Three pet rings sweep in once;
 * nothing else moves. Wire "Continue with Google" with GoogleAuthFlow.attach(binder.googleButton).
 */
class WelcomeScreenBinder(
    root: View,
    @Suppress("UNUSED_PARAMETER") lifecycleOwner: LifecycleOwner,
    onCreateAccount: () -> Unit,
    onLogIn: () -> Unit,
) {
    private val context = root.context

    /** Hidden until GoogleAuthFlow.attach() shows it. */
    val googleButton: MaterialButton = root.findViewById(R.id.pcWelcomeGoogle)

    init {
        root.applySystemBarPaddingWithKeyboard()
        bindRing(root.findViewById(R.id.pcWelcomeRingDog), PetColor.SKY, progress = 0.75f, strokeDp = 5f, delayMs = 150L)
        bindRing(root.findViewById(R.id.pcWelcomeRingCat), PetColor.VIOLET, progress = 1f, strokeDp = 4f, delayMs = 290L)
        bindRing(root.findViewById(R.id.pcWelcomeRingPaw), PetColor.LIME, progress = 0.4f, strokeDp = 3f, delayMs = 430L)
        root.findViewById<View>(R.id.pcWelcomeCreate).setOnClickListener { onCreateAccount() }
        root.findViewById<View>(R.id.pcWelcomeLogIn).setOnClickListener { onLogIn() }
    }

    private fun bindRing(frame: FrameLayout, color: PetColor, progress: Float, strokeDp: Float, delayMs: Long) {
        val ring = frame.getChildAt(0) as PetRingView
        val icon = frame.getChildAt(1) as ImageView
        val main = color.main(context)
        ring.setStrokeWidthDp(strokeDp)
        ring.setRingColor(main)
        ring.setProgress(0f, animate = false)
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(main))
        ViewCompat.setBackgroundTintList(icon, ColorStateList.valueOf(color.tint(context)))
        frame.postDelayed({ ring.setProgress(progress, animate = true) }, delayMs)
    }
}
