package com.example.petcare.ui.pets

import android.view.View
import android.view.inputmethod.InputMethodManager
import com.example.petcare.R
import com.example.petcare.databinding.FragmentAddPetBinding

/** Keeps the shared add/edit form focused on one group of fields at a time. */
internal object PetFormSections {
    const val ARG_SECTION = "section"
    const val BASICS = "basics"
    const val HEALTH = "health"
    const val CARE = "care"

    fun bind(binding: FragmentAddPetBinding, initial: String) {
        binding.petFormSectionTabs.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked) display(binding, checkedId)
        }
        show(binding, initial)
    }

    fun show(binding: FragmentAddPetBinding, section: String) {
        val id = when (section) {
            HEALTH -> R.id.pet_form_tab_health
            CARE -> R.id.pet_form_tab_care
            else -> R.id.pet_form_tab_basics
        }
        binding.petFormSectionTabs.check(id)
        display(binding, id)
    }

    fun selected(binding: FragmentAddPetBinding): String = when (binding.petFormSectionTabs.checkedButtonId) {
        R.id.pet_form_tab_health -> HEALTH
        R.id.pet_form_tab_care -> CARE
        else -> BASICS
    }

    private fun display(binding: FragmentAddPetBinding, id: Int) {
        binding.root.findFocus()?.clearFocus()
        binding.root.context.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.petFormBasics.visibility = if (id == R.id.pet_form_tab_basics) View.VISIBLE else View.GONE
        binding.petFormHealth.visibility = if (id == R.id.pet_form_tab_health) View.VISIBLE else View.GONE
        binding.petFormCare.visibility = if (id == R.id.pet_form_tab_care) View.VISIBLE else View.GONE
        binding.petFormScroll.scrollTo(0, 0)
    }
}
