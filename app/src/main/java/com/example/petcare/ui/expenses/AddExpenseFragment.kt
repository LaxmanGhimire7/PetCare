package com.example.petcare.ui.expenses

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.petcare.R
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.expense.ExpenseEntity
import com.example.petcare.data.local.pet.PetColor
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.databinding.FragmentAddExpenseBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.TimeZone

/** Creates or edits an expense in the Money destination's focused bottom sheet. */
class AddExpenseFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentAddExpenseBinding? = null
    private val binding get() = _binding!!
    private val repositories by lazy { PetCareRepositories(requireContext()) }
    private var pets = emptyList<PetEntity>()
    private var selectedPet: PetEntity? = null
    private var selectedCategory = ""
    private var selectedDate = System.currentTimeMillis() / DAY
    private var editing: ExpenseEntity? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) =
        FragmentAddExpenseBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        val expenseId = arguments?.getLong("expenseId") ?: 0L
        if (expenseId > 0) {
            binding.formTitle.setText(R.string.edit_expense)
            binding.saveButton.setText(R.string.save_changes)
        }
        binding.amountLayout.prefixText = NumberFormat.getCurrencyInstance().currency?.symbol
        selectedCategory = resources.getStringArray(R.array.expense_categories).first()
        buildCategoryChips()
        binding.dateButton.text = date(selectedDate)
        binding.dateButton.setOnClickListener { showDatePicker() }
        binding.cancelButton.setOnClickListener { dismiss() }
        binding.saveButton.setOnClickListener { save() }

        viewLifecycleOwner.lifecycleScope.launch {
            pets = repositories.pets.observePets().first()
            val preselectedPetId = arguments?.getLong("petId") ?: 0L
            selectedPet = pets.firstOrNull { it.id == preselectedPetId }
            if (expenseId > 0) {
                editing = repositories.expenses.get(expenseId)
                val expense = editing ?: run { dismiss(); return@launch }
                selectedPet = pets.firstOrNull { it.id == expense.petId }
                selectedCategory = expense.category
                selectedDate = expense.dateEpochDay
                binding.dateButton.text = date(selectedDate)
                binding.amountInput.setText(BigDecimal.valueOf(expense.amountCents, 2)
                    .stripTrailingZeros().toPlainString())
                binding.noteInput.setText(expense.note)
                buildCategoryChips()
            }
            buildPetChips()
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    private fun buildCategoryChips() {
        binding.categoryGroup.removeAllViews()
        resources.getStringArray(R.array.expense_categories).forEach { category ->
            binding.categoryGroup.addView(Chip(requireContext()).apply {
                id = View.generateViewId()
                text = category
                isCheckable = true
                isChecked = category == selectedCategory
                minHeight = resources.getDimensionPixelSize(R.dimen.touch_target)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedCategory = category
                }
            })
        }
    }

    /** Rebuilds account-scoped pet choices and carries their identity colour into the sheet. */
    private fun buildPetChips() {
        binding.petGroup.removeAllViews()
        pets.forEach { pet ->
            val identity = PetColor.fromIndex(pet.colorIndex)
            binding.petGroup.addView(Chip(requireContext()).apply {
                id = View.generateViewId()
                text = pet.name
                isCheckable = true
                isChecked = pet.id == selectedPet?.id
                minHeight = resources.getDimensionPixelSize(R.dimen.touch_target)
                chipIcon = ContextCompat.getDrawable(context, R.drawable.bg_now_dot)
                chipIconTint = ColorStateList.valueOf(identity.color(context))
                isChipIconVisible = true
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedPet = pet
                        binding.selectionError.isVisible = false
                    }
                }
            })
        }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(selectedDate * DAY)
            .build()
        picker.addOnPositiveButtonClickListener {
            selectedDate = it / DAY
            binding.dateButton.text = date(selectedDate)
        }
        picker.show(parentFragmentManager, DATE_PICKER_TAG)
    }

    private fun save() {
        val amount = binding.amountInput.text?.toString()?.toBigDecimalOrNull()
        binding.selectionError.isVisible = selectedPet == null
        binding.amountLayout.error = if (amount == null || amount <= BigDecimal.ZERO) {
            getString(R.string.error_amount_required)
        } else null
        if (selectedPet == null || amount == null || amount <= BigDecimal.ZERO) return
        val cents = runCatching {
            amount.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        }.getOrNull()
        if (cents == null) {
            binding.amountLayout.error = getString(R.string.error_amount_required)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val note = binding.noteInput.text?.toString()?.trim().orEmpty()
            val existing = editing
            if (existing == null) {
                repositories.expenses.add(selectedPet!!.id, selectedCategory, cents, selectedDate, note)
            } else {
                repositories.expenses.update(existing.copy(petId = selectedPet!!.id,
                    category = selectedCategory, amountCents = cents,
                    dateEpochDay = selectedDate, note = note))
            }
            Toast.makeText(requireContext(), if (existing == null) R.string.expense_added else
                R.string.expense_updated, Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun date(day: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(day * DAY))

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val DAY = 86_400_000L
        const val DATE_PICKER_TAG = "expense_date"
    }
}
