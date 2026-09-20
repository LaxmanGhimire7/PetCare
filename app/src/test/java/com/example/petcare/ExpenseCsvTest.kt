package com.example.petcare

import com.example.petcare.data.local.expense.ExpenseCsv
import com.example.petcare.data.local.expense.ExpenseSummary
import org.junit.Assert.assertTrue
import org.junit.Test

/** CSV fields containing quotes, commas and newlines remain valid cells. */
class ExpenseCsvTest {
    @Test fun escapesNotesAndUsesExactDecimalAmounts() {
        val csv = ExpenseCsv.encode(listOf(ExpenseSummary(1, 1, "Vet", 12345, 20_000,
            "Paid \"today\",\nreceipt saved", "Luna", 0)))
        assertTrue(csv.startsWith("Date,Pet,Category,Amount,Note\r\n"))
        assertTrue(csv.contains("Luna,Vet,123.45,\"Paid \"\"today\"\",\nreceipt saved\"\r\n"))
    }
}
