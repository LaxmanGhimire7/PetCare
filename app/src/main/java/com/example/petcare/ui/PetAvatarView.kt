package com.example.petcare.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.petcare.R
import com.example.petcare.data.local.pet.PetColor

/** Draws a pet initial and a colour-owned completion ring with an accessible progress label. */
class PetAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = RectF()
    private var petName = context.getString(R.string.pet_avatar_default_name)
    private var petColor = PetColor.SKY
    private var completed = 0
    private var total = 0
    private var selected = false
    private var accentOverride: Int? = null

    init {
        minimumWidth = resources.getDimensionPixelSize(R.dimen.pet_filter_avatar)
        minimumHeight = resources.getDimensionPixelSize(R.dimen.pet_filter_avatar)
        isFocusable = true
        updateDescription()
    }

    /** Updates identity and progress together so drawing and TalkBack never disagree. */
    fun setPet(name: String, colorIndex: Int, done: Int, taskCount: Int, isSelected: Boolean = false, accent: Int? = null) {
        petName = name.trim().ifBlank { context.getString(R.string.pet_avatar_default_name) }
        petColor = PetColor.fromIndex(colorIndex)
        completed = done.coerceAtLeast(0)
        total = taskCount.coerceAtLeast(0)
        selected = isSelected
        accentOverride = accent
        updateDescription()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val ring = resources.getDimension(R.dimen.pet_avatar_ring_stroke)
        val outer = resources.getDimension(R.dimen.pet_avatar_selected_stroke)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - ring - outer

        paint.style = Paint.Style.FILL
        paint.color = if (accentOverride == null) petColor.container(context)
            else ContextCompat.getColor(context, R.color.primary_container)
        canvas.drawCircle(cx, cy, radius - ring, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = ring
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = ContextCompat.getColor(context, R.color.hairline)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.color = accentOverride ?: petColor.color(context)
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val sweep = if (total == 0) 0f else 360f * completed.coerceAtMost(total) / total
        canvas.drawArc(arc, -90f, sweep, false, paint)

        if (selected) {
            paint.strokeWidth = outer
            paint.color = ContextCompat.getColor(context, R.color.text_primary)
            canvas.drawCircle(cx, cy, radius + ring, paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = if (accentOverride == null) petColor.onContainer(context)
            else ContextCompat.getColor(context, R.color.text_primary)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = resources.getDimension(R.dimen.pet_avatar_initial_text)
        paint.typeface = ResourcesCompat.getFont(context, R.font.barlow_semibold)
        val baseline = cy - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(petName.take(1).uppercase(), cx, baseline, paint)
    }

    private fun updateDescription() {
        contentDescription = context.getString(R.string.pet_avatar_progress, petName, completed, total)
    }
}
