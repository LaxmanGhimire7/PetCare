package com.example.petcare.data.local.pet

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.example.petcare.R

/** Fixed identity colours, independent of the device's dynamic chrome palette. */
enum class PetColor(val primary: Int) {
    RUST(0xFFC2410C.toInt()),
    LAKE(0xFF0369A1.toInt()),
    IRIS(0xFF7C3AED.toInt()),
    AMBER(0xFFB45309.toInt()),
    GRASS(0xFF15803D.toInt()),
    PLUM(0xFFBE185D.toInt());

    /** A pale tint keeps the tag readable without making the whole card colourful. */
    fun container(context: Context): Int =
        ColorUtils.blendARGB(context.getColor(R.color.surface_container), primary, 0.12f)

    /** Text and icons use a higher contrast version of the identity colour. */
    fun onContainer(context: Context): Int =
        if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        ) ColorUtils.blendARGB(primary, Color.WHITE, 0.42f) else primary

    companion object {
        fun fromIndex(index: Int): PetColor = entries[Math.floorMod(index, entries.size)]
    }
}

/** Assigns successive persisted row ids to the six identity colours. */
object ColorAssignment {
    fun forNewPet(latestId: Long): Int = ((latestId + 1) % PetColor.entries.size).toInt()
}
