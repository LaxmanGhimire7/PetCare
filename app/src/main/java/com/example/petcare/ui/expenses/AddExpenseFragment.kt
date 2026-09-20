package com.example.petcare.ui.expenses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.expense.ExpenseRepository
import com.example.petcare.data.local.expense.ExpenseEntity
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.pet.PetRepository
import com.example.petcare.databinding.FragmentAddExpenseBinding
import com.example.petcare.ui.SelectionDialog
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

/** Creates or edits a pet expense with exact decimal-to-cents conversion. */
class AddExpenseFragment : Fragment() {
    private var _binding: FragmentAddExpenseBinding? = null
    private val binding get() = _binding!!
    private val repositories by lazy { PetCareRepositories(requireContext()) }
    private var pets = emptyList<PetEntity>()
    private var selectedPet: PetEntity? = null
    private var selectedCategory = "Food"
    private var selectedDate = System.currentTimeMillis() / DAY
    private var editing: ExpenseEntity? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) =
        FragmentAddExpenseBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        val expenseId = arguments?.getLong("expenseId") ?: 0L
        if (expenseId > 0) {
            binding.formTitle.setText(R.string.edit_expense)
            binding.saveButton.setText(R.string.save_expense)
        }
        val categories = resources.getStringArray(R.array.expense_categories).toList()
        binding.categoryInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categories))
        binding.categoryInput.setText(selectedCategory, false)
        binding.categoryInput.setOnItemClickListener { _, _, position, _ -> selectedCategory = categories[position] }
        SelectionDialog.attach(binding.categoryInput, R.string.expense_category, categories) {
            selectedCategory = categories[it]
        }
        binding.dateInput.setText(date(selectedDate))
        binding.dateInput.setOnClickListener { showDatePicker() }
        binding.cancelButton.setOnClickListener { findNavController().navigateUp() }
        binding.saveButton.setOnClickListener { save() }
        viewLifecycleOwner.lifecycleScope.launch {
            pets = repositories.pets.observePets().first()
            val labels = pets.map { getString(R.string.pet_selection_label, it.name, it.species) }
            binding.petInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
            binding.petInput.setOnItemClickListener { _, _, position, _ -> selectedPet = pets[position]; binding.petLayout.error = null }
            SelectionDialog.attach(binding.petInput, R.string.select_pet, labels) {
                selectedPet = pets[it]
                binding.petLayout.error = null
            }
            if (expenseId > 0) {
                val repo = repositories.expenses
                editing = repo.get(expenseId)
                val expense = editing ?: run { findNavController().navigateUp(); return@launch }
                selectedPet = pets.firstOrNull { it.id == expense.petId }
                selectedPet?.let { pet ->
                    binding.petInput.setText(getString(R.string.pet_selection_label,
                        pet.name, pet.species), false)
                }
                selectedCategory = expense.category
                binding.categoryInput.setText(expense.category, false)
                selectedDate = expense.dateEpochDay
                binding.dateInput.setText(date(selectedDate))
                binding.amountInput.setText(java.math.BigDecimal.valueOf(expense.amountCents, 2)
                    .stripTrailingZeros().toPlainString())
                binding.noteInput.setText(expense.note)
            }
        }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker().setSelection(selectedDate * DAY).build()
        picker.addOnPositiveButtonClickListener { selectedDate = it / DAY; binding.dateInput.setText(date(selectedDate)) }
        picker.show(parentFragmentManager, "expense_date")
    }

    private fun save() {
        val amount = binding.amountInput.text?.toString()?.toBigDecimalOrNull()
        binding.petLayout.error = if (selectedPet == null) getString(R.string.error_pet_selection_required) else null
        binding.amountLayout.error = if (amount == null || amount <= BigDecimal.ZERO) getString(R.string.error_amount_required) else null
        if (selectedPet == null || amount == null || amount <= BigDecimal.ZERO) return
        val cents = runCatching { amount.multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()
        if (cents == null) {
            binding.amountLayout.error = getString(R.string.error_amount_required)
            return
        }
        val repo = repositories.expenses
        viewLifecycleOwner.lifecycleScope.launch {
            val note = binding.noteInput.text?.toString()?.trim().orEmpty()
            val existing = editing
            if (existing == null) repo.add(selectedPet!!.id, selectedCategory, cents, selectedDate, note)
            else repo.update(existing.copy(petId = selectedPet!!.id, category = selectedCategory,
                amountCents = cents, dateEpochDay = selectedDate, note = note))
            findNavController().navigateUp()
        }
    }
    private fun date(day: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(day * DAY))
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
    private companion object { const val DAY = 86_400_000L }
}
