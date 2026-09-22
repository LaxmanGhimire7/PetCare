package com.example.petcare.data.local.pet

import android.content.Context
import android.content.res.Configuration
/** Fixed identity colours with explicit readable tints for both app themes. */
enum class PetColor(
    val primary: Int,
    private val lightContainer: Int,
    private val darkContainer: Int,
    private val lightOnContainer: Int,
    private val darkOnContainer: Int
) {
    SKY(0xFF2F7DF6.toInt(), 0xFFE6EFFE.toInt(), 0xFF15233D.toInt(), 0xFF1E4FB3.toInt(), 0xFF9FC0FF.toInt()),
    IRIS(0xFF7A5AF8.toInt(), 0xFFEFEBFE.toInt(), 0xFF221B3F.toInt(), 0xFF4F35C2.toInt(), 0xFFB9A9FF.toInt()),
    ROSE(0xFFE5487D.toInt(), 0xFFFCE8EF.toInt(), 0xFF3A1826.toInt(), 0xFFA32458.toInt(), 0xFFFF9BBB.toInt()),
    LAGOON(0xFF12A594.toInt(), 0xFFE3F6F3.toInt(), 0xFF0F2E2A.toInt(), 0xFF0B6B60.toInt(), 0xFF75D6C9.toInt()),
    EMBER(0xFFF2701F.toInt(), 0xFFFEEEE3.toInt(), 0xFF3A2212.toInt(), 0xFFA8470C.toInt(), 0xFFFFAB73.toInt()),
    STONE(0xFF6B7280.toInt(), 0xFFEEF0F2.toInt(), 0xFF23262C.toInt(), 0xFF3F4450.toInt(), 0xFFB5BBC5.toInt());

    /** Returns the designed pet tint rather than blending against dynamic chrome. */
    fun container(context: Context): Int = if (context.isDarkTheme()) darkContainer else lightContainer

    /** Returns a contrast-safe label colour for the current tint. */
    fun onContainer(context: Context): Int = if (context.isDarkTheme()) darkOnContainer else lightOnContainer

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
