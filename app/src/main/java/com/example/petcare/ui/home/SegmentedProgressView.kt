package com.example.petcare.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.petcare.R

/** Draws one timeline progress segment per task, coloured by its pet when complete. */
class SegmentedProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var segments: List<Pair<Int, Boolean>> = emptyList()

    /** Replaces the full set atomically so count and colour cannot render out of sync. */
    fun setSegments(values: List<Pair<Int, Boolean>>) {
        segments = values
        contentDescription = context.getString(R.string.today_done_progress,
            values.count { it.second }, values.size)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (segments.isEmpty()) return
        val gap = resources.getDimension(R.dimen.space_4)
        val widthPerSegment = ((width - gap * (segments.size - 1)) / segments.size).coerceAtLeast(1f)
        val radius = height / 2f
        segments.forEachIndexed { index, (color, done) ->
            val left = index * (widthPerSegment + gap)
            rect.set(left, 0f, left + widthPerSegment, height.toFloat())
            paint.color = if (done) color else ContextCompat.getColor(context, R.color.track)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }
}
