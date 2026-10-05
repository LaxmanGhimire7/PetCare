package com.example.petcare.ui.expenses

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.petcare.R
import com.example.petcare.data.local.expense.ExpenseSummary
import com.example.petcare.databinding.ItemMoneyCategoryBinding
import kotlin.math.roundToInt

/** Shows each category's share of the selected month's actual expenses. */
object ExpenseCategoryBars {
    fun append(
        container: LinearLayout,
        inflater: LayoutInflater,
        expenses: List<ExpenseSummary>,
        formatMoney: (Long) -> String,
    ) {
        val total = expenses.sumOf(ExpenseSummary::amountCents)
        if (expenses.isEmpty() || total <= 0L) return
        expenses.groupBy(ExpenseSummary::category)
            .mapValues { (_, rows) -> rows.sumOf(ExpenseSummary::amountCents) }
            .entries.sortedByDescending { it.value }
            .forEach { (category, amount) ->
                val row = ItemMoneyCategoryBinding.inflate(inflater, container, false)
                val color = ContextCompat.getColor(container.context, colorFor(category))
                val percent = (amount.toDouble() * 100 / total).roundToInt()
                val progress = (amount.toDouble() * 1000 / total).roundToInt()
                    .coerceIn(1, 1000)
                row.categoryName.text = category
                row.categoryAmount.text = formatMoney(amount)
                row.categoryColor.backgroundTintList = ColorStateList.valueOf(color)
                row.categoryProgress.setIndicatorColor(color)
                row.categoryProgress.setProgressCompat(progress, false)
                row.categoryShare.text = container.context.getString(R.string.money_category_share, percent)
                row.root.contentDescription = "$category, ${formatMoney(amount)}, $percent%"
                container.addView(row.root)
            }
    }

    private fun colorFor(category: String) = when (category.lowercase()) {
        "food" -> R.color.expense_food
        "grooming" -> R.color.expense_grooming
        "veterinary" -> R.color.expense_veterinary
        "medication" -> R.color.expense_medication
        "toys" -> R.color.expense_toys
        else -> R.color.expense_other
    }
}
