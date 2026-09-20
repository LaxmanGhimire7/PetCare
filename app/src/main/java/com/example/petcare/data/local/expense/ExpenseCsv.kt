package com.example.petcare.data.local.expense

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** RFC 4180-style CSV for one account's visible expense records. */
object ExpenseCsv {
    fun encode(items: List<ExpenseSummary>): String = buildString {
        append("Date,Pet,Category,Amount,Note\r\n")
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        items.forEach { item ->
            val columns = listOf(date.format(Date(item.dateEpochDay * 86_400_000L)),
                item.petName, item.category,
                java.math.BigDecimal.valueOf(item.amountCents, 2).toPlainString(), item.note)
            append(columns.joinToString(",") { value ->
                if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' })
                    "\"${value.replace("\"", "\"\"")}\"" else value
            })
            append("\r\n")
        }
    }
}
