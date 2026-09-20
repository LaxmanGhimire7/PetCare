package com.example.petcare.data.local.expense

import java.util.Calendar
import java.util.TimeZone

/** Aggregates expense rows for the chart and month comparison without UI dependencies. */
object ExpenseInsights {
    /** One calendar month of spending, including an empty month when no rows exist. */
    data class Month(val year: Int, val month: Int, val totalCents: Long)
    /** Category and six-month aggregates consumed by the custom chart. */
    data class Summary(
        val categoryTotals: Map<String, Long>,
        val months: List<Month>,
        val monthChangePercent: Int?
    )

    fun calculate(items: List<ExpenseSummary>, todayEpochDay: Long): Summary {
        val utc = TimeZone.getTimeZone("UTC")
        val current = Calendar.getInstance(utc).apply { timeInMillis = todayEpochDay * 86_400_000L }
        val keys = (5 downTo 0).map { offset ->
            (current.clone() as Calendar).apply { add(Calendar.MONTH, -offset) }
        }
        val sums = items.groupBy { item ->
            Calendar.getInstance(utc).apply { timeInMillis = item.dateEpochDay * 86_400_000L }
                .let { it.get(Calendar.YEAR) to it.get(Calendar.MONTH) }
        }.mapValues { (_, rows) -> rows.sumOf(ExpenseSummary::amountCents) }
        val months = keys.map { Month(it.get(Calendar.YEAR), it.get(Calendar.MONTH),
            sums[it.get(Calendar.YEAR) to it.get(Calendar.MONTH)] ?: 0L) }
        val previous = months[4].totalCents
        val change = if (previous == 0L) null else
            (((months[5].totalCents - previous).toDouble() / previous) * 100).toInt()
        return Summary(items.groupBy { it.category }.mapValues { (_, rows) ->
            rows.sumOf(ExpenseSummary::amountCents)
        }.toList().sortedByDescending { it.second }.toMap(), months, change)
    }
}
