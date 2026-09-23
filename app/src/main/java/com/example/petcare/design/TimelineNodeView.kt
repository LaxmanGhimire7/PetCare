package com.example.petcare.design

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.petcare.R

/**
 * The timeline node, which is also the task's checkbox.
 * Open: a ring in the pet's colour. Done: a filled circle with the tick drawn INSIDE it.
 * Draws the connector line down to the next task. The row never moves when toggled.
 */
class TimelineNodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val radius = NODE_RADIUS_DP * density
    private val topOffset = TOP_OFFSET_DP * density

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f * density
        color = ContextCompat.getColor(context, R.color.pc_hairline)
    }
    private val checkPath = Path()

    @ColorInt private var nodeColor = ContextCompat.getColor(context, R.color.pc_primary)
    private var done = false
    private var showConnector = true
    private var popScale = 1f
    private var popAnimator: ValueAnimator? = null

    /** Binds state from the list. Leaves a running "pop" alone if the state already matches. */
    fun bind(@ColorInt color: Int, done: Boolean, showConnector: Boolean) {
        val animating = popAnimator?.isRunning == true
        if (!(animating && this.done == done)) {
            popAnimator?.cancel()
            popScale = 1f
        }
        nodeColor = color
        this.done = done
        this.showConnector = showConnector
        invalidate()
    }

    fun setConnector(show: Boolean) {
        if (showConnector != show) {
            showConnector = show
            invalidate()
        }
    }

    /** Called on tap: flips immediately with a small overshoot. */
    fun animateDone(done: Boolean) {
        this.done = done
        popAnimator?.cancel()
        if (!MotionPrefs.animationsEnabled(context)) {
            popScale = 1f
            invalidate()
            return
        }
        popAnimator = ValueAnimator.ofFloat(POP_FROM, 1f).apply {
            duration = POP_MS
            interpolator = OvershootInterpolator(POP_TENSION)
            addUpdateListener {
                popScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = topOffset + radius

        if (showConnector) {
            canvas.drawLine(cx, cy + radius + CONNECTOR_GAP_DP * density, cx, height.toFloat(), connectorPaint)
        }

        canvas.save()
        canvas.scale(popScale, popScale, cx, cy)
        if (done) {
            fillPaint.color = nodeColor
            canvas.drawCircle(cx, cy, radius, fillPaint)
            // Black tick on light pet colours, white on dark ones: always readable.
            checkPaint.color = if (ColorUtils.calculateLuminance(nodeColor) > 0.4) Color.BLACK else Color.WHITE
            checkPath.reset()
            checkPath.moveTo(cx - radius * 0.42f, cy + radius * 0.02f)
            checkPath.lineTo(cx - radius * 0.10f, cy + radius * 0.34f)
            checkPath.lineTo(cx + radius * 0.44f, cy - radius * 0.30f)
            canvas.drawPath(checkPath, checkPaint)
        } else {
            ringPaint.color = nodeColor
            canvas.drawCircle(cx, cy, radius - ringPaint.strokeWidth / 2f, ringPaint)
        }
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        popAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val NODE_RADIUS_DP = 10f
        const val TOP_OFFSET_DP = 0f
        const val CONNECTOR_GAP_DP = 4f
        const val POP_FROM = 0.7f
        const val POP_MS = 260L
        const val POP_TENSION = 3f
    }
}
