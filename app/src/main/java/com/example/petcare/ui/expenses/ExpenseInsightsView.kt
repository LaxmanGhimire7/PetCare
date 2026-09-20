package com.example.petcare.ui.expenses

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.example.petcare.R
import com.example.petcare.data.local.expense.ExpenseInsights
import com.example.petcare.data.local.pet.PetColor
import com.google.android.material.color.MaterialColors
import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Draws an accessible expense donut and six-month trend without a chart dependency. */
class ExpenseInsightsView(context: Context) : View(context) {
    var summary: ExpenseInsights.Summary? = null
        set(value) { field = value; requestLayout(); invalidate() }

    private val density = resources.displayMetrics.density
    private val fontScale = resources.configuration.fontScale
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = RectF()
    private val currency = NumberFormat.getCurrencyInstance()
    private val monthNames = DateFormatSymbols.getInstance(Locale.getDefault()).shortMonths
    private val categories = resources.getStringArray(R.array.expense_categories).toList()
    private val categoryCount get() = min(summary?.categoryTotals?.size ?: 0, 6)
    private val headingSize get() = 18f * density * fontScale
    private val labelSize get() = 12f * density * fontScale
    private val monthSize get() = 11f * density * fontScale
    private val rowHeight get() = max(26f * density, labelSize * 1.55f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Legend rows grow with font scale; bars always begin below them.
        val height = 22f * density + headingSize * 1.4f + 160f * density +
            categoryCount * rowHeight + 32f * density + 88f * density +
            monthSize * 1.7f + 16f * density
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val data = summary ?: return
        val left = 20f * density
        val right = width - left
        val textColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        val mutedColor = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSurfaceVariant)

        paint.style = Paint.Style.FILL
        paint.color = textColor
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = headingSize
        val headingBaseline = 20f * density + headingSize
        canvas.drawText(context.getString(R.string.expense_insights_title), left,
            headingBaseline, paint)

        val radius = min((right - left) * 0.25f, 62f * density)
        val centerX = width / 2f
        val centerY = headingBaseline + 16f * density + radius
        arc.set(centerX - radius, centerY - radius, centerX + radius,
            centerY + radius)
        val grandTotal = data.categoryTotals.values.sum().coerceAtLeast(1L)
        var start = -90f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 18f * density
        data.categoryTotals.entries.take(6).forEach { (category, amount) ->
            paint.color = categoryColor(category)
            val sweep = amount.toFloat() / grandTotal * 360f
            canvas.drawArc(arc, start, sweep, false, paint)
            start += sweep
        }

        val legendTop = centerY + radius + 28f * density
        paint.style = Paint.Style.FILL
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textSize = labelSize
        data.categoryTotals.entries.take(6).forEachIndexed { index, (category, amount) ->
            val baseline = legendTop + index * rowHeight
            paint.color = categoryColor(category)
            canvas.drawCircle(left + 5f * density, baseline - 4f * density,
                5f * density, paint)
            paint.color = textColor
            val label = context.getString(R.string.expense_legend, category,
                currency.format(amount / 100.0))
            canvas.drawText(fit(label, right - left - 22f * density),
                left + 20f * density, baseline, paint)
        }

        val barTop = legendTop + categoryCount * rowHeight + 20f * density
        val barBottom = barTop + 88f * density
        val maximum = data.months.maxOfOrNull { it.totalCents }?.coerceAtLeast(1L) ?: 1L
        val gap = (right - left) / 6f
        data.months.forEachIndexed { index, month ->
            val x = left + gap * index + gap * 0.2f
            val barHeight = month.totalCents.toFloat() / maximum * (barBottom - barTop)
            // Time is encoded by x-position, so one chrome colour is used for all bars.
            paint.color = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
            canvas.drawRoundRect(x, barBottom - barHeight, x + gap * 0.6f,
                barBottom, 5f * density, 5f * density, paint)
            paint.color = mutedColor
            paint.textSize = monthSize
            val monthLabel = monthNames[month.month]
            canvas.drawText(fit(monthLabel, gap * 0.8f), x,
                barBottom + monthSize * 1.3f, paint)
        }
    }

    /** Keeps labels inside the chart when accessibility font size or locale expands them. */
    private fun fit(value: String, availableWidth: Float): String {
        if (paint.measureText(value) <= availableWidth) return value
        val ellipsis = "…"
        var length = value.length
        while (length > 0 && paint.measureText(value.take(length) + ellipsis) > availableWidth) {
            length--
        }
        return value.take(length) + ellipsis
    }

    private fun categoryColor(category: String): Int {
        val index = categories.indexOf(category).takeIf { it >= 0 }
            ?: (category.hashCode() and Int.MAX_VALUE) % PetColor.entries.size
        return PetColor.fromIndex(index).primary
    }
}
