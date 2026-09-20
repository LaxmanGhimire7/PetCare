package com.example.petcare.ui.routine

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.ReminderPreferences
import com.example.petcare.data.local.care.CARE_FREQUENCY_DAILY
import com.example.petcare.data.local.care.CARE_FREQUENCY_MONTHLY
import com.example.petcare.data.local.care.CARE_FREQUENCY_WEEKLY
import com.example.petcare.data.local.care.CareTaskRepository
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.pet.PetRepository
import com.example.petcare.databinding.FragmentRoutineGeneratorBinding
import com.example.petcare.reminders.CareReminderScheduler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Generates selected care templates for one pet and schedules their reminders. */
class RoutineGeneratorFragment : Fragment() {
    private var _binding: FragmentRoutineGeneratorBinding? = null
    private val binding get() = _binding!!
    private val repositories by lazy { PetCareRepositories(requireContext()) }
    private var pets = emptyList<PetEntity>()
    private var selectedPet: PetEntity? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) = FragmentRoutineGeneratorBinding.inflate(inflater, container, false).also { _binding = it }.root
    override fun onViewCreated(view: View, state: Bundle?) {
        binding.cancelButton.setOnClickListener { findNavController().navigateUp() }
        binding.generateButton.setOnClickListener { generate() }
        viewLifecycleOwner.lifecycleScope.launch {
        pets = repositories.pets.observePets().first()
            val labels = pets.map { getString(R.string.pet_selection_label, it.name, it.species) }
            binding.petInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
            binding.petInput.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.select_pet)
                    .setItems(labels.toTypedArray()) { _, position ->
                        selectedPet = pets[position]
                        binding.petInput.setText(labels[position], false)
                        binding.petLayout.error = null
                    }.show()
            }
            binding.petInput.setOnItemClickListener { _, _, position, _ -> selectedPet = pets[position]; binding.petLayout.error = null }
        }
    }
    private fun generate() {
        val pet = selectedPet
        binding.petLayout.error = if (pet == null) getString(R.string.error_pet_selection_required) else null
        if (pet == null) return
        val categories = resources.getStringArray(R.array.care_categories)
        val plans = buildList {
            if (binding.feedingCheck.isChecked) {
                add(Plan(getString(R.string.routine_morning_feeding_title), categories[0],
                    CARE_FREQUENCY_DAILY, 8 * 60, getString(R.string.routine_feeding_supplies)))
                add(Plan(getString(R.string.routine_evening_feeding_title), categories[0],
                    CARE_FREQUENCY_DAILY, 18 * 60, getString(R.string.routine_feeding_supplies)))
            }
            if (binding.exerciseCheck.isChecked) add(Plan(getString(R.string.routine_exercise_title),
                categories[1], CARE_FREQUENCY_DAILY, 17 * 60,
                getString(R.string.routine_exercise_supplies)))
            if (binding.groomingCheck.isChecked) add(Plan(getString(R.string.routine_grooming_title),
                categories[2], CARE_FREQUENCY_WEEKLY, 10 * 60,
                getString(R.string.routine_grooming_supplies)))
            if (binding.medicationCheck.isChecked) add(Plan(getString(R.string.routine_medication_title),
                categories[3], CARE_FREQUENCY_DAILY,
                ReminderPreferences(requireContext()).defaultReminderMinutesOfDay(),
                getString(R.string.routine_medication_supplies)))
            if (binding.healthcareCheck.isChecked) add(Plan(getString(R.string.routine_healthcare_title),
                categories[4], CARE_FREQUENCY_MONTHLY, 9 * 60,
                getString(R.string.routine_healthcare_supplies)))
        }
        if (plans.isEmpty()) { Toast.makeText(requireContext(), R.string.routine_select_one, Toast.LENGTH_SHORT).show(); return }
        val repository = repositories.tasks; val scheduler = CareReminderScheduler(requireContext())
        val today = System.currentTimeMillis() / 86_400_000L
        viewLifecycleOwner.lifecycleScope.launch {
            plans.forEach { plan -> scheduler.schedule(repository.addTask(pet.id, plan.title, today,
                plan.minutes, plan.category, plan.frequency, plan.supplies,
                getString(R.string.routine_task_notes, pet.name))) }
            Toast.makeText(requireContext(), R.string.routine_generated, Toast.LENGTH_SHORT).show(); findNavController().navigateUp()
        }
    }
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
    /** A template task before a pet and due day are attached. */
    private data class Plan(val title: String, val category: String, val frequency: String,
        val minutes: Int, val supplies: String)
}
