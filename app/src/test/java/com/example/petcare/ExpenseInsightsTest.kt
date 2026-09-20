package com.example.petcare

import com.example.petcare.data.local.expense.ExpenseInsights
import com.example.petcare.data.local.expense.ExpenseSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Expense trends count only the correct calendar month and preserve category totals. */
class ExpenseInsightsTest {
    private fun day(value: String): Long = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse(value)!!.time / 86_400_000L

    private fun expense(id: Long, amount: Long, date: String, category: String) =
        ExpenseSummary(id, 1, category, amount, day(date), "", "Luna", 1)

    @Test fun sixMonthTrendAndCategoryTotals() {
        val items = listOf(expense(1, 1000, "2026-08-31", "Food"),
            expense(2, 1200, "2026-09-01", "Food"),
            expense(3, 800, "2026-09-20", "Vet"),
            expense(4, 900, "2026-02-10", "Vet"))
        val result = ExpenseInsights.calculate(items, day("2026-09-20"))
        assertEquals(1000, result.months[4].totalCents)
        assertEquals(2000, result.months[5].totalCents)
        assertEquals(100, result.monthChangePercent)
        assertEquals(2200L, result.categoryTotals["Food"])
    }

    @Test fun noPreviousSpendHasNoPercentage() {
        val result = ExpenseInsights.calculate(
            listOf(expense(1, 500, "2026-09-20", "Food")), day("2026-09-20"))
        assertNull(result.monthChangePercent)
    }
}
