package com.example.petcare.ui.expenses

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.expense.ExpenseRepository
import com.example.petcare.data.local.expense.ExpenseSummary
import com.example.petcare.data.local.pet.PetColor
import com.example.petcare.ui.MotionPrefs
import com.example.petcare.ui.RowMotion
import com.example.petcare.databinding.FragmentFeatureListBinding
import com.example.petcare.databinding.ItemFeatureBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.TimeZone

/** Shows expense totals and entries with pet identity colours. */
class ExpenseListFragment : Fragment() {
    private var _binding: FragmentFeatureListBinding? = null
    private val binding get() = _binding!!
    private val repository by lazy { ExpenseRepository(PetCareDatabase.getInstance(requireContext()).expenseDao()) }
    private var displayedTotal: Long? = null
    private var totalAnimator: ValueAnimator? = null
    private var restoringId: Long? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) =
        FragmentFeatureListBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.titleText.setText(R.string.expense_title)
        binding.subtitleText.setText(R.string.expense_subtitle)
        binding.addButton.setText(R.string.add_expense)
        binding.emptyText.setText(R.string.expense_empty)
        binding.backButton.visibility = View.GONE
        binding.addButton.setOnClickListener { findNavController().navigate(R.id.action_expenses_to_add_expense) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeAll().collect(::render)
            }
        }
    }

    private fun render(items: List<ExpenseSummary>) {
        val total = items.sumOf { it.amountCents }
        val previous = displayedTotal
        totalAnimator?.cancel()
        if (previous != null && previous != total && MotionPrefs.animationsEnabled(requireContext())) {
            totalAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200L
                addUpdateListener { frame ->
                    val shown = previous + ((total - previous) * (frame.animatedValue as Float)).toLong()
                    binding.summaryText.text = getString(R.string.total_spent, money(shown))
                }
                start()
            }
        } else {
            binding.summaryText.text = getString(R.string.total_spent, money(total))
        }
        displayedTotal = total
        binding.secondarySummaryText.text = items.groupBy { it.petName }.entries.joinToString("  ·  ") {
            getString(R.string.pet_spent, it.key, money(it.value.sumOf(ExpenseSummary::amountCents)))
        }
        binding.secondarySummaryText.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.itemContainer.removeAllViews()
        items.forEach { expense ->
            val row = ItemFeatureBinding.inflate(layoutInflater, binding.itemContainer, false)
            row.itemColorRail.setBackgroundColor(PetColor.fromIndex(expense.petColorIndex).primary)
            row.itemTitleText.text = money(expense.amountCents)
            row.itemDetailText.text = getString(R.string.expense_detail, expense.petName, expense.category, date(expense.dateEpochDay))
            row.itemNoteText.text = expense.note
            row.itemNoteText.visibility = if (expense.note.isBlank()) View.GONE else View.VISIBLE
            row.primaryButton.visibility = View.GONE
            row.secondaryButton.visibility = View.GONE
            row.tertiaryButton.visibility = View.GONE
            row.deleteButton.setOnClickListener {
                RowMotion.collapse(row.root) { viewLifecycleOwner.lifecycleScope.launch {
                    val deleted = repository.delete(expense.id) ?: return@launch
                    Snackbar.make(binding.root, R.string.expense_deleted, Snackbar.LENGTH_LONG)
                        .setDuration(6000)
                        .setAction(R.string.undo) {
                            restoringId = deleted.id
                            viewLifecycleOwner.lifecycleScope.launch { repository.restore(deleted) }
                        }.show()
                } }
            }
            binding.itemContainer.addView(row.root)
            if (restoringId == expense.id) {
                restoringId = null
                RowMotion.expand(row.root)
            }
        }
    }

    private fun money(cents: Long) = NumberFormat.getCurrencyInstance().format(cents / 100.0)
    private fun date(day: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(day * 86_400_000L))
    override fun onDestroyView() { totalAnimator?.cancel(); _binding = null; super.onDestroyView() }
}
