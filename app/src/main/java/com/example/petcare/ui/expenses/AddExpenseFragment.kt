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
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.expense.ExpenseRepository
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.pet.PetRepository
import com.example.petcare.databinding.FragmentAddExpenseBinding
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

class AddExpenseFragment : Fragment() {
    private var _binding: FragmentAddExpenseBinding? = null
    private val binding get() = _binding!!
    private var pets = emptyList<PetEntity>()
    private var selectedPet: PetEntity? = null
    private var selectedCategory = "Food"
    private var selectedDate = System.currentTimeMillis() / DAY

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) =
        FragmentAddExpenseBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        val categories = resources.getStringArray(R.array.expense_categories).toList()
        binding.categoryInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categories))
        binding.categoryInput.setText(selectedCategory, false)
        binding.categoryInput.setOnItemClickListener { _, _, position, _ -> selectedCategory = categories[position] }
        binding.dateInput.setText(date(selectedDate))
        binding.dateInput.setOnClickListener { showDatePicker() }
        binding.cancelButton.setOnClickListener { findNavController().navigateUp() }
        binding.saveButton.setOnClickListener { save() }
        viewLifecycleOwner.lifecycleScope.launch {
            pets = PetRepository(PetCareDatabase.getInstance(requireContext()).petDao()).observePets().first()
            val labels = pets.map { getString(R.string.pet_selection_label, it.name, it.species) }
            binding.petInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
            binding.petInput.setOnItemClickListener { _, _, position, _ -> selectedPet = pets[position]; binding.petLayout.error = null }
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
        val cents = amount.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        val repo = ExpenseRepository(PetCareDatabase.getInstance(requireContext()).expenseDao())
        viewLifecycleOwner.lifecycleScope.launch {
            repo.add(selectedPet!!.id, selectedCategory, cents, selectedDate, binding.noteInput.text?.toString()?.trim().orEmpty())
            findNavController().navigateUp()
        }
    }
    private fun date(day: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(day * DAY))
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
    private companion object { const val DAY = 86_400_000L }
}
