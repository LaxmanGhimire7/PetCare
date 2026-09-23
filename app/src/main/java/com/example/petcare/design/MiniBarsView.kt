package com.example.petcare.design

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.petcare.R

/**
 * Seven small bars, one per day of the week, height = share of that day's tasks done.
 * Today's bar is orange; days with no tasks (or still to come) show a short track.
 */
class MiniBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val filled = ContextCompat.getColor(context, R.color.pc_text_primary)
    private val track = ContextCompat.getColor(context, R.color.pc_track)
    private val highlight = ContextCompat.getColor(context, R.color.pc_primary)
    private var values: List<Float?> = emptyList()
    private var highlightIndex = -1

    /** [values] 0..1 per day, null for no tasks; [highlightIndex] is today. */
    fun setValues(values: List<Float?>, highlightIndex: Int) {
        this.values = values
        this.highlightIndex = highlightIndex
        contentDescription = values.mapIndexed { index, value ->
            "${index + 1}: ${value?.let { (it * 100).toInt().toString() + "%" } ?: "-"}"
        }.joinToString(", ")
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return
        val gap = 3f * density
        val minHeight = 3f * density
        val radius = 2f * density
        val width = (width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { index, value ->
            val fraction = (value ?: 0f).coerceIn(0f, 1f)
            val barHeight = maxOf(fraction * height, minHeight)
            val left = index * (width + gap)
            rect.set(left, height - barHeight, left + width, height.toFloat())
            paint.color = when {
                index == highlightIndex -> highlight
                fraction > 0f -> filled
                else -> track
            }
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }
}
