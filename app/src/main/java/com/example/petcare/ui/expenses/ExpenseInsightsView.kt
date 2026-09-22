package com.example.petcare.ui.expenses

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.example.petcare.R
import com.example.petcare.data.local.expense.ExpenseInsights
import com.google.android.material.color.MaterialColors
import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

/** Draws the six-month Pit Lane spending bars without a chart dependency. */
class ExpenseInsightsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var summary: ExpenseInsights.Summary? = null
        set(value) {
            field = value
            contentDescription = value?.months?.joinToString(", ") {
                context.getString(R.string.money_chart_accessibility, monthNames[it.month],
                    it.year, currency.format(it.totalCents / 100.0))
            }
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val monthNames = DateFormatSymbols.getInstance(Locale.getDefault()).shortMonths
    private val currency = NumberFormat.getCurrencyInstance()
    private val horizontalPadding = resources.getDimension(R.dimen.space_4)
    private val chartHeight = resources.getDimension(R.dimen.money_chart_bar_height)
    private val corner = resources.getDimension(R.dimen.space_4)
    private val labelSize = resources.getDimension(R.dimen.money_chart_label_text)
    private val titleSize = resources.getDimension(R.dimen.money_chart_title_text)
    private val titleTypeface = ResourcesCompat.getFont(context, R.font.barlow_bold)
    private val figureTypeface = ResourcesCompat.getFont(context, R.font.barlow_condensed_semibold)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            resources.getDimensionPixelSize(R.dimen.money_chart_height))
    }

    override fun onDraw(canvas: Canvas) {
        val data = summary ?: return
        val text = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        val muted = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSurfaceVariant)
        val primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
        val track = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOutlineVariant)

        paint.style = Paint.Style.FILL
        paint.color = text
        paint.typeface = titleTypeface
        paint.textSize = titleSize
        canvas.drawText(context.getString(R.string.money_six_month_trend), horizontalPadding,
            titleSize, paint)

        val top = titleSize + resources.getDimension(R.dimen.space_20)
        val bottom = top + chartHeight
        val available = width - horizontalPadding * 2
        val slot = available / data.months.size.coerceAtLeast(1)
        val maximum = data.months.maxOfOrNull { it.totalCents }?.coerceAtLeast(1L) ?: 1L
        data.months.forEachIndexed { index, month ->
            val left = horizontalPadding + slot * index + slot * 0.2f
            val right = left + slot * 0.6f
            val ratio = month.totalCents.toFloat() / maximum
            val barHeight = max(resources.getDimension(R.dimen.space_2), chartHeight * ratio)
            paint.color = if (index == data.months.lastIndex) primary else track
            canvas.drawRoundRect(left, bottom - barHeight, right, bottom, corner, corner, paint)

            paint.color = muted
            paint.typeface = figureTypeface
            paint.textSize = labelSize
            val label = monthNames[month.month]
            canvas.drawText(label, left, bottom + labelSize * 1.5f, paint)
        }
    }
}
