package com.example.petcare.data.local.pet

import android.content.Context
import android.content.res.Configuration

/** Fixed Pit Lane identity colours with explicit readable tints in both themes. */
enum class PetColor(
    private val darkColor: Int,
    private val lightColor: Int,
    private val darkTint: Int,
    private val lightTint: Int
) {
    SKY(0xFF38BDF8.toInt(), 0xFF0284C7.toInt(), 0xFF0C2A3A.toInt(), 0xFFE0F2FE.toInt()),
    VIOLET(0xFFA78BFA.toInt(), 0xFF7C3AED.toInt(), 0xFF241C3F.toInt(), 0xFFEDE9FE.toInt()),
    LAGOON(0xFF2DD4BF.toInt(), 0xFF0F766E.toInt(), 0xFF0D2E2A.toInt(), 0xFFCCFBF1.toInt()),
    LIME(0xFFA3E635.toInt(), 0xFF4D7C0F.toInt(), 0xFF1F2A0C.toInt(), 0xFFECFCCB.toInt()),
    PINK(0xFFF472B6.toInt(), 0xFFBE185D.toInt(), 0xFF3A1530.toInt(), 0xFFFCE7F3.toInt()),
    SILVER(0xFFD4D4D8.toInt(), 0xFF52525B.toInt(), 0xFF26262A.toInt(), 0xFFF4F4F5.toInt());

    /** Returns the stable identity colour for the current designed theme. */
    fun color(context: Context): Int = if (context.isDarkTheme()) darkColor else lightColor

    /** Compatibility accessor for callers that do not have a Context yet. */
    val primary: Int get() = darkColor

    /** Returns the fixed tint used behind this pet's labels and fallback avatar. */
    fun container(context: Context): Int = if (context.isDarkTheme()) darkTint else lightTint

    /** Text on a pet tint uses the identity colour specified for that theme. */
    fun onContainer(context: Context): Int = color(context)

    companion object {
        fun fromIndex(index: Int): PetColor = entries[Math.floorMod(index, entries.size)]
    }
}

private fun Context.isDarkTheme(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/** Assigns successive persisted row ids to the six identity colours. */
object ColorAssignment {
    fun forNewPet(latestId: Long): Int = ((latestId + 1) % PetColor.entries.size).toInt()
}
