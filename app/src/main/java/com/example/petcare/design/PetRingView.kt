package com.example.petcare.design

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.example.petcare.R

/**
 * Progress ring drawn around a pet's avatar. The filled arc is the pet's identity colour
 * and runs clockwise from twelve o'clock; the remainder is drawn in the hairline colour.
 */
class PetRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = DEFAULT_STROKE_DP * density
        color = ContextCompat.getColor(context, R.color.pc_track)
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = DEFAULT_STROKE_DP * density
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.pc_primary)
    }

    private val bounds = RectF()
    private var animator: ValueAnimator? = null

    /** Current drawn progress, 0..1. */
    var progress: Float = 0f
        private set

    fun setStrokeWidthDp(dp: Float) {
        trackPaint.strokeWidth = dp * density
        ringPaint.strokeWidth = dp * density
        invalidate()
    }

    fun setRingColor(@ColorInt color: Int) {
        ringPaint.color = color
        invalidate()
    }

    /**
     * Sets progress. With [animate], sweeps from the current value to the new one;
     * call with 0 first (not animated) to sweep up from empty.
     */
    fun setProgress(value: Float, animate: Boolean) {
        val target = value.coerceIn(0f, 1f)
        animator?.cancel()
        if (!animate || !MotionPrefs.animationsEnabled(context)) {
            progress = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = SWEEP_MS
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Inset by a full stroke width so the ring sits inside its box, as in the mockup.
        val inset = ringPaint.strokeWidth
        bounds.set(inset, inset, width - inset, height - inset)
        canvas.drawOval(bounds, trackPaint)
        if (progress > 0f) {
            canvas.drawArc(bounds, START_ANGLE, SWEEP_FULL * progress, false, ringPaint)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val DEFAULT_STROKE_DP = 3f
        const val START_ANGLE = -90f
        const val SWEEP_FULL = 360f
        const val SWEEP_MS = 600L
    }
}
