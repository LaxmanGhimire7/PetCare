package com.example.petcare.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.example.petcare.R
import com.example.petcare.ui.MotionPrefs
import com.google.android.material.color.MaterialColors
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import kotlin.math.min
import kotlin.math.roundToInt

/** Canvas ring for today's completion ratio; animates only for a meaningful state change. */
class ProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = resources.getDimension(R.dimen.progress_ring_text)
        typeface = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_bold)
    }
    private val arc = RectF()
    private var sweep = 0f
    private var animator: ValueAnimator? = null

    fun setProgress(done: Int, total: Int, animate: Boolean) {
        val target = if (total == 0) 0f else done.toFloat() / total.coerceAtLeast(1)
        animator?.cancel()
        if (!animate || !MotionPrefs.animationsEnabled(context)) {
            sweep = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(sweep, target).apply {
            duration = 450L
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                sweep = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val stroke = resources.getDimension(R.dimen.progress_ring_stroke)
        ringPaint.strokeWidth = stroke
        val left = (width - size) / 2 + stroke / 2
        val top = (height - size) / 2 + stroke / 2
        arc.set(left, top, left + size - stroke, top + size - stroke)
        ringPaint.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
        canvas.drawArc(arc, 0f, 360f, false, ringPaint)
        ringPaint.color = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
        canvas.drawArc(arc, -90f, 360f * sweep, false, ringPaint)
        textPaint.color = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        val baseline = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(
            context.getString(R.string.today_progress_percent, (sweep * 100).roundToInt()),
            width / 2f,
            baseline,
            textPaint
        )
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
