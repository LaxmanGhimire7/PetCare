package com.example.petcare.ui.expenses

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.expense.ExpenseSummary
import com.example.petcare.data.local.expense.ExpenseInsights
import com.example.petcare.data.local.expense.ExpenseCsv
import com.example.petcare.data.local.pet.PetColor
import com.example.petcare.ui.MotionPrefs
import com.example.petcare.ui.RowMotion
import com.example.petcare.ui.ScreenState
import com.example.petcare.databinding.FragmentFeatureListBinding
import com.example.petcare.databinding.ItemFeatureBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.TimeZone

/** Shows expense totals and entries with pet identity colours. */
class ExpenseListFragment : Fragment() {
    private var _binding: FragmentFeatureListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExpenseListViewModel by viewModels()
    private var displayedTotal: Long? = null
    private var totalAnimator: ValueAnimator? = null
    private var restoringId: Long? = null
    private var currentItems = emptyList<ExpenseSummary>()
    private val csvDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        uri -> if (uri != null) writeExport(uri, false)
    }
    private val pdfDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        uri -> if (uri != null) writeExport(uri, true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) =
        FragmentFeatureListBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.titleText.setText(R.string.expense_title)
        binding.summaryText.setTextAppearance(R.style.TextAppearance_PetCare_Numeric)
        binding.subtitleText.setText(R.string.expense_subtitle)
        binding.addButton.setText(R.string.add_expense)
        binding.emptyText.setText(R.string.expense_empty)
        binding.backButton.visibility = View.GONE
        binding.addButton.setOnClickListener { findNavController().navigate(R.id.action_expenses_to_add_expense) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        ScreenState.Loading -> binding.emptyText.visibility = View.GONE
                        ScreenState.Empty -> render(emptyList())
                        is ScreenState.Content -> render(state.data)
                        is ScreenState.Error -> {
                            render(emptyList())
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun render(items: List<ExpenseSummary>) {
        currentItems = items
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
        val petBreakdown = items.groupBy { it.petName }.entries.joinToString("  ·  ") {
            getString(R.string.pet_spent, it.key, money(it.value.sumOf(ExpenseSummary::amountCents)))
        }
        val insightSummary = ExpenseInsights.calculate(items, System.currentTimeMillis() / 86_400_000L)
        val change = insightSummary.monthChangePercent?.let {
            getString(R.string.expense_month_change, it)
        } ?: getString(R.string.expense_month_change_unavailable)
        binding.secondarySummaryText.text = getString(R.string.expense_breakdown_with_change,
            petBreakdown, change)
        binding.secondarySummaryText.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.itemContainer.removeAllViews()
        if (items.isNotEmpty()) {
            val tools = layoutInflater.inflate(R.layout.item_expense_tools,
                binding.itemContainer, false)
            tools.findViewById<com.google.android.material.button.MaterialButton>(R.id.export_csv_button)
                .setOnClickListener { csvDocument.launch(getString(R.string.expense_csv_filename)) }
            tools.findViewById<com.google.android.material.button.MaterialButton>(R.id.export_pdf_button)
                .setOnClickListener { pdfDocument.launch(getString(R.string.expense_pdf_filename)) }
            binding.itemContainer.addView(tools)
            val insights = insightSummary
            binding.itemContainer.addView(ExpenseInsightsView(requireContext()).apply {
                summary = insights
                val categories = insights.categoryTotals.entries.joinToString(", ") {
                    getString(R.string.expense_legend, it.key, money(it.value))
                }
                val months = insights.months.joinToString(", ") {
                    "${java.text.DateFormatSymbols().months[it.month]} ${it.year}: ${money(it.totalCents)}"
                }
                contentDescription = getString(R.string.expense_insights_accessibility,
                    categories, months, change)
            })
        }
        items.forEach { expense ->
            val row = ItemFeatureBinding.inflate(layoutInflater, binding.itemContainer, false)
            row.itemColorRail.setBackgroundColor(PetColor.fromIndex(expense.petColorIndex).primary)
            row.itemTitleText.text = money(expense.amountCents)
            row.itemDetailText.text = getString(R.string.expense_detail, expense.petName, expense.category, date(expense.dateEpochDay))
            row.itemNoteText.text = expense.note
            row.itemNoteText.visibility = if (expense.note.isBlank()) View.GONE else View.VISIBLE
            row.primaryButton.visibility = View.VISIBLE
            row.primaryButton.setText(R.string.edit_expense)
            row.primaryButton.setOnClickListener {
                findNavController().navigate(R.id.action_expenses_to_edit_expense,
                    Bundle().apply { putLong("expenseId", expense.id) })
            }
            row.secondaryButton.visibility = View.GONE
            row.tertiaryButton.visibility = View.GONE
            row.deleteButton.setOnClickListener {
                RowMotion.collapse(row.root) { viewLifecycleOwner.lifecycleScope.launch {
                    val deleted = viewModel.delete(expense.id) ?: return@launch
                    Snackbar.make(binding.root, R.string.expense_deleted, Snackbar.LENGTH_LONG)
                        .setDuration(6000)
                        .setAction(R.string.undo) {
                            restoringId = deleted.id
                            viewLifecycleOwner.lifecycleScope.launch { viewModel.restore(deleted) }
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

    private fun writeExport(uri: Uri, pdf: Boolean) {
        val snapshot = currentItems.toList()
        viewLifecycleOwner.lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                        if (pdf) ExpensePdf.write(requireContext(), snapshot, output)
                        else output.write(ExpenseCsv.encode(snapshot).toByteArray(Charsets.UTF_8))
                    } ?: error("Document stream unavailable")
                }.isSuccess
            }
            Snackbar.make(binding.root, if (success) R.string.expense_exported else
                R.string.expense_export_failed, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun money(cents: Long) = NumberFormat.getCurrencyInstance().format(cents / 100.0)
    private fun date(day: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(day * 86_400_000L))
    override fun onDestroyView() { totalAnimator?.cancel(); _binding = null; super.onDestroyView() }
}
