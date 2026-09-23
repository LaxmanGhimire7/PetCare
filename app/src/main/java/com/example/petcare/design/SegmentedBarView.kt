package com.example.petcare.design

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.example.petcare.R

/**
 * A row of rounded segments with small gaps, like lap splits.
 * Next-up card: one equal segment per task today, done ones in their pet's colour.
 * Money: one weighted segment per pet, sized by spend.
 */
class SegmentedBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** [color] null means "not done yet" and draws in the track colour. */
    data class Segment(val weight: Float, @ColorInt val color: Int?)

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    @ColorInt private val trackColor = ContextCompat.getColor(context, R.color.pc_track)
    private val rect = RectF()
    private var segments: List<Segment> = emptyList()
    private var reveal = 1f
    private var animator: ValueAnimator? = null

    /** Gap between segments. */
    var gapPx: Float = 3f * density

    /** Sets segments. With [animate], filled segments light up left to right, 40ms each. */
    fun setSegments(list: List<Segment>, animate: Boolean) {
        segments = list
        animator?.cancel()
        if (!animate || list.isEmpty() || !MotionPrefs.animationsEnabled(context)) {
            reveal = 1f
            invalidate()
            return
        }
        reveal = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (PER_SEGMENT_MS * list.size).coerceAtLeast(MIN_MS)
            interpolator = LinearInterpolator()
            addUpdateListener {
                reveal = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** Convenience for equal-width segments. */
    fun setEqualSegments(colors: List<Int?>, animate: Boolean) {
        setSegments(colors.map { Segment(1f, it) }, animate)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (segments.isEmpty()) return
        val totalWeight = segments.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(0.0001f)
        val available = width - gapPx * (segments.size - 1)
        val radius = height / 2f
        val revealedCount = reveal * segments.size
        var x = 0f
        segments.forEachIndexed { index, segment ->
            val w = available * segment.weight / totalWeight
            rect.set(x, 0f, x + w, height.toFloat())
            val filled = segment.color != null && index < revealedCount
            paint.color = if (filled) segment.color!! else trackColor
            canvas.drawRoundRect(rect, radius, radius, paint)
            x += w + gapPx
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val PER_SEGMENT_MS = 40L
        const val MIN_MS = 200L
    }
}
