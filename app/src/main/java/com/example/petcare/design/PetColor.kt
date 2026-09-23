package com.example.petcare.design

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt

/**
 * The six pet identity colours. A pet's colour appears wherever that pet does:
 * ring, timeline node, detail dot, progress segment, spending bar.
 *
 * None of them are orange or red, so they never collide with actions (orange)
 * or overdue (red). Map your stored colour index with [fromIndex].
 */
enum class PetColor(
    @ColorInt private val darkMain: Int,
    @ColorInt private val darkTint: Int,
    @ColorInt private val lightMain: Int,
    @ColorInt private val lightTint: Int,
) {
    SKY(0xFF38BDF8.toInt(), 0xFF0C2A3A.toInt(), 0xFF0369A1.toInt(), 0xFFE0F2FE.toInt()),
    VIOLET(0xFFA78BFA.toInt(), 0xFF241C3F.toInt(), 0xFF7C3AED.toInt(), 0xFFEDE9FE.toInt()),
    LAGOON(0xFF2DD4BF.toInt(), 0xFF0D2E2A.toInt(), 0xFF0F766E.toInt(), 0xFFCCFBF1.toInt()),
    LIME(0xFFA3E635.toInt(), 0xFF1F2A0C.toInt(), 0xFF4D7C0F.toInt(), 0xFFECFCCB.toInt()),
    PINK(0xFFF472B6.toInt(), 0xFF3A1530.toInt(), 0xFFBE185D.toInt(), 0xFFFCE7F3.toInt()),
    SILVER(0xFFD4D4D8.toInt(), 0xFF26262A.toInt(), 0xFF52525B.toInt(), 0xFFF4F4F5.toInt());

    /** Strong colour: rings, nodes, dots, and text placed on [tint]. */
    @ColorInt
    fun main(context: Context): Int = if (context.isNightTheme()) darkMain else lightMain

    /** Soft background behind initials and pills. */
    @ColorInt
    fun tint(context: Context): Int = if (context.isNightTheme()) darkTint else lightTint

    companion object {
        /** Maps a stored colour index (any integer) onto the palette. */
        fun fromIndex(index: Int): PetColor = entries[Math.floorMod(index, entries.size)]
    }
}

/** True while the app renders its dark theme. */
fun Context.isNightTheme(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
