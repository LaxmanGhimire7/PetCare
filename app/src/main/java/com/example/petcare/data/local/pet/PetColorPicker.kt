package com.example.petcare.data.local.pet

import android.content.Context
import android.content.res.ColorStateList
import com.example.petcare.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/** Populates the profile form with the six fixed pet identity colours. */
object PetColorPicker {
    fun bind(group: ChipGroup, context: Context, selectedIndex: Int?, onSelected: (Int) -> Unit) {
        group.removeAllViews()
        val names = context.resources.getStringArray(R.array.pet_color_names)
        PetColor.entries.forEachIndexed { index, petColor ->
            val chip = Chip(context).apply {
                id = android.view.View.generateViewId()
                text = names[index]
                isCheckable = true
                chipBackgroundColor = ColorStateList.valueOf(petColor.container(context))
                setTextColor(petColor.onContainer(context))
                isChecked = selectedIndex == index
                setOnClickListener { onSelected(index) }
            }
            group.addView(chip)
        }
    }
}
