package com.example.petcare.design

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.petcare.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Binds pc_fragment_welcome.xml, the app's entry point (the brief's "home screen with
 * login or signup"). The Next-up card is a live, ticking preview of the app.
 */
class WelcomeScreenBinder(
    root: View,
    private val lifecycleOwner: LifecycleOwner,
    onCreateAccount: () -> Unit,
    onLogIn: () -> Unit,
) {
    private val context = root.context
    private val countdown: TextView = root.findViewById(R.id.pcWelcomeCountdown)
    private var target = System.currentTimeMillis() + PREVIEW_MS

    init {
        root.applySystemBarPaddingWithKeyboard()

        val pet: TextView = root.findViewById(R.id.pcWelcomePet)
        pet.setTextColor(PetColor.SKY.main(context))
        ViewCompat.setBackgroundTintList(pet, ColorStateList.valueOf(PetColor.SKY.tint(context)))

        root.findViewById<SegmentedBarView>(R.id.pcWelcomeBar).setEqualSegments(
            listOf(PetColor.SKY.main(context), PetColor.VIOLET.main(context), PetColor.SKY.main(context), null, null, null),
            animate = true,
        )

        root.findViewById<View>(R.id.pcWelcomeCreate).setOnClickListener { onCreateAccount() }
        root.findViewById<View>(R.id.pcWelcomeLogIn).setOnClickListener { onLogIn() }
        startTicker()
    }

    private fun startTicker() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    val now = System.currentTimeMillis()
                    if (target <= now) target = now + PREVIEW_MS
                    countdown.text = Countdown.format(context, target - now)
                    delay(1_000L - now % 1_000L)
                }
            }
        }
    }

    private companion object {
        /** 00:57:48, the same moment as the approved mockup. */
        const val PREVIEW_MS = (57 * 60 + 48) * 1_000L
    }
}
