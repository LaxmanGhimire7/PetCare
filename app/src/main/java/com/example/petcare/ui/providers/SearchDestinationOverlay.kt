package com.example.petcare.ui.providers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.example.petcare.R
import com.example.petcare.location.PlaceMarkerIcon

/** Screen-space drawing of a geographic search result, updated as the map camera moves. */
internal class SearchDestinationOverlay(context: Context) : View(context) {
    private val pin = PlaceMarkerIcon.createSearch(context)
    private val density = resources.displayMetrics.density
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.on_primary)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f,
            resources.displayMetrics)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.on_primary)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private var pinX: Float? = null
    private var pinY = 0f
    private var title = ""

    fun showAt(x: Float, y: Float, label: String?) {
        pinX = x
        pinY = y
        title = label.orEmpty().take(30)
        invalidate()
    }

    fun clear() {
        pinX = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = pinX ?: return
        val y = pinY
        if (x < 0f || y < 0f || x > width || y > height) return
        val pinTop = y - pin.height
        canvas.drawBitmap(pin, x - pin.width / 2f, pinTop, null)
        if (title.isBlank()) return

        val horizontalPadding = 11f * density
        val verticalPadding = 7f * density
        val textWidth = labelPaint.measureText(title)
        val badgeBottom = pinTop - 5f * density
        val badgeTop = badgeBottom - (labelPaint.descent() - labelPaint.ascent()) - 2f * verticalPadding
        val badge = RectF(x - textWidth / 2f - horizontalPadding, badgeTop,
            x + textWidth / 2f + horizontalPadding, badgeBottom)
        val radius = 12f * density
        canvas.drawRoundRect(badge, radius, radius, badgePaint)
        canvas.drawRoundRect(badge, radius, radius, outlinePaint)
        canvas.drawText(title, x, badgeTop + verticalPadding - labelPaint.ascent(), labelPaint)
    }
}
