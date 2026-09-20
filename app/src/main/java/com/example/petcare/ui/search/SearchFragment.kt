package com.example.petcare.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.data.local.expense.ExpenseSummary
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.ui.ScreenState
import com.example.petcare.databinding.FragmentSearchBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** One search surface for the signed-in account's pets, care and spending. */
class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SearchViewModel by viewModels()
    private var pets = emptyList<PetEntity>()
    private var tasks = emptyList<CareTaskSummary>()
    private var expenses = emptyList<ExpenseSummary>()
    private var selectedPetId: Long? = null
    private var selectedCategory: String? = null
    private var dateRange: LongRange? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FragmentSearchBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.searchBack.setOnClickListener { findNavController().navigateUp() }
        binding.searchInput.doAfterTextChanged { render() }
        binding.dateRangeButton.setOnClickListener {
            val picker = MaterialDatePicker.Builder.dateRangePicker().build()
            picker.addOnPositiveButtonClickListener { range ->
                dateRange = (range.first / DAY)..(range.second / DAY)
                binding.clearDateButton.visibility = View.VISIBLE
                render()
            }
            picker.show(parentFragmentManager, "search_dates")
        }
        binding.clearDateButton.setOnClickListener {
            dateRange = null
            binding.clearDateButton.visibility = View.GONE
            render()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        ScreenState.Loading -> binding.resultsContainer.removeAllViews()
                        ScreenState.Empty -> showData(SearchData(emptyList(), emptyList(), emptyList()))
                        is ScreenState.Content -> showData(state.data)
                        is ScreenState.Error -> {
                            binding.resultsContainer.removeAllViews()
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun showData(data: SearchData) {
        val petsChanged = pets.map { it.id } != data.pets.map { it.id }
        pets = data.pets; tasks = data.tasks; expenses = data.expenses
        if (petsChanged || binding.petChips.childCount == 0) bindPetChips()
        bindCategoryChips()
        render()
    }

    private fun bindPetChips() {
        val group = binding.petChips
        group.removeAllViews()
        group.addView(chip(getString(R.string.search_all), selectedPetId == null) {
            selectedPetId = null; bindPetChips(); render()
        })
        pets.forEach { pet ->
            group.addView(chip(pet.name, selectedPetId == pet.id) {
                selectedPetId = pet.id; bindPetChips(); render()
            })
        }
    }

    private fun bindCategoryChips() {
        val categories = (tasks.map { it.category } + expenses.map { it.category }).distinct().sorted()
        binding.categoryChips.removeAllViews()
        binding.categoryChips.addView(chip(getString(R.string.search_all), selectedCategory == null) {
            selectedCategory = null; bindCategoryChips(); render()
        })
        categories.forEach { category ->
            binding.categoryChips.addView(chip(category, selectedCategory == category) {
                selectedCategory = category; bindCategoryChips(); render()
            })
        }
    }

    private fun chip(label: String, checked: Boolean, action: () -> Unit) =
        Chip(requireContext()).apply {
            text = label
            isCheckable = true
            isChecked = checked
            setOnClickListener { action() }
        }

    private fun render() {
        if (_binding == null) return
        val query = binding.searchInput.text?.toString()?.trim().orEmpty()
        val petRows = if (selectedCategory == null && dateRange == null) pets.filter {
            (selectedPetId == null || selectedPetId == it.id) &&
                (it.name.contains(query, true) || it.species.contains(query, true))
        } else emptyList()
        val taskRows = tasks.filter {
            (selectedPetId == null || it.petId == selectedPetId) &&
                (selectedCategory == null || it.category == selectedCategory) &&
                (dateRange == null || it.dueDateEpochDay in dateRange!!) &&
                (it.title.contains(query, true) || it.petName.contains(query, true) ||
                    it.category.contains(query, true) || it.notes.contains(query, true))
        }
        val expenseRows = expenses.filter {
            (selectedPetId == null || it.petId == selectedPetId) &&
                (selectedCategory == null || it.category == selectedCategory) &&
                (dateRange == null || it.dateEpochDay in dateRange!!) &&
                (it.petName.contains(query, true) || it.category.contains(query, true) ||
                    it.note.contains(query, true))
        }
        val count = petRows.size + taskRows.size + expenseRows.size
        binding.resultsCount.text = resources.getQuantityString(R.plurals.search_results, count, count)
        binding.resultsContainer.removeAllViews()
        petRows.forEach { pet -> result(getString(R.string.search_pet_result, pet.name, pet.species)) {
            findNavController().navigate(R.id.petDetailFragment,
                Bundle().apply { putLong("petId", pet.id) })
        } }
        taskRows.forEach { task -> result(getString(R.string.search_task_result,
            task.title, task.petName)) {
            findNavController().navigate(R.id.editCareTaskFragment,
                Bundle().apply { putLong("careTaskId", task.id) })
        } }
        expenseRows.forEach { expense -> result(getString(R.string.search_expense_result,
            expense.category, expense.petName,
            java.text.NumberFormat.getCurrencyInstance().format(expense.amountCents / 100.0))) {
            findNavController().navigate(R.id.addExpenseFragment,
                Bundle().apply { putLong("expenseId", expense.id) })
        } }
    }

    private fun result(label: String, action: () -> Unit) {
        binding.resultsContainer.addView(MaterialButton(requireContext()).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        })
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }

    private companion object { const val DAY = 86_400_000L }
}
