package com.example.petcare.ui.pets

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import com.example.petcare.ui.GestureHaptics
import com.example.petcare.ui.MotionPrefs
import kotlin.math.abs

/** Image view with pinch and double-tap zoom, panning, and a downward dismiss gesture. */
class ZoomablePhotoView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    var onDismiss: (() -> Unit)? = null
    private var zoom = 1f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var verticalDrag = false
    private val dismissDistance = 110f * resources.displayMetrics.density
    private val touchSlop = 12f * resources.displayMetrics.density

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                GestureHaptics.confirm(this@ZoomablePhotoView)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = (zoom * detector.scaleFactor).coerceIn(1f, 4f)
                pivotX = detector.focusX
                pivotY = detector.focusY
                scaleX = zoom
                scaleY = zoom
                constrainPan()
                return true
            }
        })
    private val tapDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                GestureHaptics.confirm(this@ZoomablePhotoView)
                zoom = if (zoom > 1.1f) 1f else 2.5f
                pivotX = e.x
                pivotY = e.y
                if (MotionPrefs.animationsEnabled(context)) {
                    animate().scaleX(zoom).scaleY(zoom)
                        .translationX(if (zoom == 1f) 0f else translationX)
                        .translationY(if (zoom == 1f) 0f else translationY)
                        .setDuration(200L).start()
                } else {
                    scaleX = zoom; scaleY = zoom
                    if (zoom == 1f) { translationX = 0f; translationY = 0f }
                }
                return true
            }
        })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        tapDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                lastX = event.x; lastY = event.y
                verticalDrag = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (zoom > 1.05f && !scaleDetector.isInProgress) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    translationX += dx
                    translationY += dy
                    constrainPan()
                } else if (!scaleDetector.isInProgress && event.pointerCount == 1 &&
                    abs(event.y - downY) > touchSlop &&
                    abs(event.y - downY) > abs(event.x - downX) * 1.3f) {
                    verticalDrag = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (zoom <= 1.05f && verticalDrag &&
                    event.y - downY > dismissDistance &&
                    abs(event.y - downY) > abs(event.x - downX) * 1.3f) {
                    GestureHaptics.confirm(this)
                    onDismiss?.invoke()
                } else performClick()
                verticalDrag = false
            }
            MotionEvent.ACTION_CANCEL -> verticalDrag = false
        }
        return true
    }

    /** Limit panning to the extra image area exposed by zooming. */
    private fun constrainPan() {
        val horizontal = width * (zoom - 1f) / 2f
        val vertical = height * (zoom - 1f) / 2f
        translationX = translationX.coerceIn(-horizontal, horizontal)
        translationY = translationY.coerceIn(-vertical, vertical)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
