package com.example.petcare.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.petcare.R
import com.example.petcare.ui.MotionPrefs

/** Two placeholder rows with a moving highlight while Room loads the checklist. */
class ShimmerSkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val transform = Matrix()
    private val base = ContextCompat.getColor(context, R.color.surface_container_high)
    private val highlight = ContextCompat.getColor(context, R.color.outline_variant)
    private var shimmer: LinearGradient? = null
    private var animator: ValueAnimator? = null
    private var progress = 0f

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0) return
        shimmer = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            intArrayOf(base, highlight, base),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (MotionPrefs.animationsEnabled(context)) {
            animator = ValueAnimator.ofFloat(-1f, 1f).apply {
                duration = 900L
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val row = resources.getDimension(R.dimen.skeleton_row_height)
        val gap = resources.getDimension(R.dimen.space_8)
        val radius = resources.getDimension(R.dimen.radius_card)
        val gradient = shimmer
        if (gradient == null || !MotionPrefs.animationsEnabled(context)) {
            paint.shader = null
            paint.color = base
        } else {
            // Translate the light band across both rows so the loading state reads as shimmer.
            transform.setTranslate(progress * width, 0f)
            gradient.setLocalMatrix(transform)
            paint.shader = gradient
        }
        canvas.drawRoundRect(0f, gap, width.toFloat(), gap + row, radius, radius, paint)
        canvas.drawRoundRect(0f, gap * 2 + row, width.toFloat(), gap * 2 + row * 2, radius, radius, paint)
    }

    /** Stop drawing once data arrives; a detached view must not keep an animator alive. */
    fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }
}
