package com.example.petcare.ui.expenses

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.petcare.R
import com.example.petcare.data.local.expense.ExpenseSummary
import java.io.OutputStream
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.TimeZone

/** Writes a readable, paginated expense statement with totals. */
object ExpensePdf {
    fun write(context: Context, items: List<ExpenseSummary>, output: OutputStream) {
        val document = PdfDocument()
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val money = NumberFormat.getCurrencyInstance()
            var pageNumber = 0
            var page: PdfDocument.Page? = null
            var y = 0f
            fun startPage() {
                page?.let(document::finishPage)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                paint.textSize = 22f; paint.isFakeBoldText = true
                page!!.canvas.drawText(context.getString(R.string.expense_pdf_title), 36f, 48f, paint)
                paint.textSize = 12f; paint.isFakeBoldText = false
                page!!.canvas.drawText(context.getString(R.string.expense_pdf_page, pageNumber), 510f, 48f, paint)
                paint.isFakeBoldText = true
                page!!.canvas.drawText(context.getString(R.string.expense_pdf_columns), 36f, 82f, paint)
                paint.isFakeBoldText = false
                y = 108f
            }
            startPage()
            items.forEach { item ->
                if (y > 770f) startPage()
                val canvas = page!!.canvas
                paint.textSize = 11f
                canvas.drawText(date.format(Date(item.dateEpochDay * 86_400_000L)), 36f, y, paint)
                canvas.drawText(item.petName.take(20), 135f, y, paint)
                canvas.drawText(item.category.take(20), 260f, y, paint)
                canvas.drawText(money.format(item.amountCents / 100.0), 470f, y, paint)
                if (item.note.isNotBlank()) {
                    y += 17f
                    paint.color = android.graphics.Color.DKGRAY
                    canvas.drawText(item.note.take(75), 135f, y, paint)
                    paint.color = android.graphics.Color.BLACK
                }
                y += 27f
            }
            if (y > 770f) startPage()
            paint.textSize = 14f; paint.isFakeBoldText = true
            page!!.canvas.drawText(context.getString(R.string.total_spent,
                money.format(items.sumOf { it.amountCents } / 100.0)), 36f, y + 16f, paint)
            page?.let(document::finishPage)
            document.writeTo(output)
        } finally { document.close() }
    }
}
