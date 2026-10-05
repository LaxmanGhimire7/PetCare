package com.example.petcare.ui.expenses

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.design.applySystemBarTopPadding
import com.example.petcare.data.local.expense.ExpenseCsv
import com.example.petcare.data.local.expense.ExpenseInsights
import com.example.petcare.data.local.expense.ExpenseSummary
import com.example.petcare.data.local.pet.PetColor
import com.example.petcare.databinding.FragmentExpenseListBinding
import com.example.petcare.databinding.ItemMoneyExpenseBinding
import com.example.petcare.ui.MotionPrefs
import com.example.petcare.ui.ScreenState
import com.example.petcare.ui.UiSnackbar
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Shows the Pit Lane monthly spending instrument and editable expense history. */
class ExpenseListFragment : Fragment() {
    private var _binding: FragmentExpenseListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExpenseListViewModel by viewModels()
    private var items = emptyList<ExpenseSummary>()
    private var selectedPet: String? = null
    private var displayedTotal: Long? = null
    private var totalAnimator: ValueAnimator? = null

    private val csvDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) writeExport(uri, false) }
    private val pdfDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) writeExport(uri, true) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FragmentExpenseListBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        view.applySystemBarTopPadding()
        binding.monthButton.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        binding.addButton.setOnClickListener {
            findNavController().navigate(R.id.action_expenses_to_add_expense)
        }
        binding.exportCsvButton.setOnClickListener {
            csvDocument.launch(getString(R.string.expense_csv_filename))
        }
        binding.exportPdfButton.setOnClickListener {
            pdfDocument.launch(getString(R.string.expense_pdf_filename))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        ScreenState.Loading -> Unit
                        ScreenState.Empty -> render(emptyList())
                        is ScreenState.Content -> render(state.data)
                        is ScreenState.Error -> {
                            render(emptyList())
                            UiSnackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    /** Rebuilds all summaries after Room or the pet filter changes. */
    private fun render(newItems: List<ExpenseSummary>) {
        items = newItems
        buildPetFilters(newItems)
        val filtered = selectedPet?.let { name -> newItems.filter { it.petName == name } } ?: newItems
        val current = filtered.filter(::isCurrentMonth)
        val total = current.sumOf(ExpenseSummary::amountCents)
        animateTotal(total)

        val insights = ExpenseInsights.calculate(filtered, System.currentTimeMillis() / MILLIS_PER_DAY)
        binding.insightsView.summary = insights
        binding.changeText.text = insights.monthChangePercent?.let {
            getString(R.string.expense_month_change, it)
        } ?: getString(R.string.expense_month_change_unavailable)
        buildSplitBar(current)
        buildCategories(current)
        buildRecent(filtered.take(10))
        binding.emptyText.isVisible = filtered.isEmpty()
        binding.categoryContainer.parent.let { (it as View).isVisible = filtered.isNotEmpty() }
        binding.recentContainer.parent.let { (it as View).isVisible = filtered.isNotEmpty() }
    }

    private fun buildPetFilters(source: List<ExpenseSummary>) {
        val pets = source.distinctBy { it.petName }.map { it.petName to it.petColorIndex }
        val expected = listOf<String?>(null) + pets.map { it.first }
        if (binding.petFilterGroup.childCount == expected.size &&
            (0 until binding.petFilterGroup.childCount).map {
                binding.petFilterGroup.getChildAt(it).tag as? String
            } == expected.map { it.orEmpty() }) return

        binding.petFilterGroup.removeAllViews()
        addFilterChip(null, getString(R.string.money_all_pets), null)
        pets.forEach { (name, colorIndex) -> addFilterChip(name, name, colorIndex) }
    }

    private fun addFilterChip(value: String?, label: String, colorIndex: Int?) {
        val chip = Chip(requireContext()).apply {
            id = View.generateViewId()
            text = label
            tag = value.orEmpty()
            isCheckable = true
            isChecked = value == selectedPet
            minHeight = resources.getDimensionPixelSize(R.dimen.space_40)
            if (colorIndex != null) {
                val petColor = PetColor.fromIndex(colorIndex)
                chipIconTint = ColorStateList.valueOf(petColor.color(context))
                isChipIconVisible = true
                chipIcon = requireContext().getDrawable(R.drawable.bg_now_dot)
            }
            setOnCheckedChangeListener { _, checked ->
                if (checked && selectedPet != value) {
                    selectedPet = value
                    render(items)
                }
            }
        }
        binding.petFilterGroup.addView(chip)
    }

    private fun animateTotal(total: Long) {
        val previous = displayedTotal
        totalAnimator?.cancel()
        if (previous != null && previous != total && MotionPrefs.animationsEnabled(requireContext())) {
            totalAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200L
                addUpdateListener { animation ->
                    val shown = previous + ((total - previous) * animation.animatedFraction).toLong()
                    binding.totalText.text = money(shown)
                }
                start()
            }
        } else binding.totalText.text = money(total)
        displayedTotal = total
    }

    private fun buildSplitBar(current: List<ExpenseSummary>) {
        binding.petSplitBar.removeAllViews()
        val groups = current.groupBy { it.petName }
        val total = current.sumOf(ExpenseSummary::amountCents).coerceAtLeast(1L)
        groups.values.forEach { rows ->
            binding.petSplitBar.addView(View(requireContext()).apply {
                setBackgroundColor(PetColor.fromIndex(rows.first().petColorIndex).color(context))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT,
                rows.sumOf(ExpenseSummary::amountCents).toFloat() / total))
        }
        binding.petSplitBar.isVisible = groups.isNotEmpty()
    }

    private fun buildCategories(current: List<ExpenseSummary>) {
        binding.categoryContainer.removeAllViews()
        if (current.isEmpty()) {
            binding.categoryContainer.addView(TextView(requireContext()).apply {
                setText(R.string.money_no_expenses_month)
                setTextAppearance(R.style.TextAppearance_PetCare_Supporting)
                val padding = resources.getDimensionPixelSize(R.dimen.space_16)
                setPadding(padding, padding, padding, padding)
            })
            return
        }
        ExpenseCategoryBars.append(binding.categoryContainer, layoutInflater, current, ::money)
    }

    private fun buildRecent(recent: List<ExpenseSummary>) {
        binding.recentContainer.removeAllViews()
        recent.forEach { expense ->
            val row = ItemMoneyExpenseBinding.inflate(layoutInflater, binding.recentContainer, false)
            row.petDot.setBackgroundColor(PetColor.fromIndex(expense.petColorIndex).color(requireContext()))
            row.expenseTitle.text = expense.note.ifBlank { expense.category }
            row.expenseDetail.text = getString(R.string.expense_detail, expense.petName,
                expense.category, date(expense.dateEpochDay))
            row.expenseAmount.text = money(expense.amountCents)
            row.root.contentDescription = getString(R.string.money_expense_accessibility,
                row.expenseTitle.text, expense.petName, row.expenseAmount.text)
            row.root.setOnClickListener {
                findNavController().navigate(R.id.action_expenses_to_edit_expense,
                    Bundle().apply { putLong("expenseId", expense.id) })
            }
            row.root.setOnLongClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    val deleted = viewModel.delete(expense.id) ?: return@launch
                    UiSnackbar.make(binding.root, R.string.expense_deleted, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo) {
                            viewLifecycleOwner.lifecycleScope.launch { viewModel.restore(deleted) }
                        }.show()
                }
                true
            }
            binding.recentContainer.addView(row.root)
        }
    }

    private fun isCurrentMonth(expense: ExpenseSummary): Boolean {
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val date = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = expense.dateEpochDay * MILLIS_PER_DAY
        }
        return now.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
            now.get(Calendar.MONTH) == date.get(Calendar.MONTH)
    }

    private fun writeExport(uri: Uri, pdf: Boolean) {
        val snapshot = items.toList()
        viewLifecycleOwner.lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                        if (pdf) ExpensePdf.write(requireContext(), snapshot, output)
                        else output.write(ExpenseCsv.encode(snapshot).toByteArray(Charsets.UTF_8))
                    } ?: error("Document stream unavailable")
                }.isSuccess
            }
            if (success) android.widget.Toast.makeText(requireContext(), R.string.expense_exported,
                android.widget.Toast.LENGTH_SHORT).show()
            else UiSnackbar.make(binding.root, R.string.expense_export_failed,
                Snackbar.LENGTH_LONG).show()
        }
    }

    private fun money(cents: Long) = NumberFormat.getCurrencyInstance().format(cents / 100.0)
    private fun date(day: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(day * MILLIS_PER_DAY))

    override fun onDestroyView() {
        totalAnimator?.cancel()
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
