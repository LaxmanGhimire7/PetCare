package com.example.petcare.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.petcare.R
import com.example.petcare.ui.GestureHaptics
import com.example.petcare.ui.MotionPrefs
import kotlin.math.abs
import kotlin.math.min

/** Draws a proportional complete/delete action behind a dragged task row. */
class TaskSwipeCallback(
    private val onAction: (RecyclerView.ViewHolder, Int) -> Unit,
    private val onDragStart: () -> Unit,
    private val onDragMove: (Int, Int) -> Boolean,
    private val onDragFinish: () -> Unit,
    private val completeColor: (RecyclerView.ViewHolder) -> Int,
    private val canSwipe: (RecyclerView.ViewHolder) -> Boolean = { true }
) : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var thresholdHolder: RecyclerView.ViewHolder? = null
    private var dragging = false

    override fun isLongPressDragEnabled() = false

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
        if (canSwipe(viewHolder)) super.getSwipeDirs(recyclerView, viewHolder) else 0

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = onDragMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
            dragging = true
            onDragStart()
            val row = viewHolder.itemView
            row.translationZ = row.resources.getDimension(R.dimen.space_12)
            if (MotionPrefs.animationsEnabled(row.context)) row.animate()
                .scaleX(1.02f).scaleY(1.02f).setDuration(200L).start()
            else { row.scaleX = 1.02f; row.scaleY = 1.02f }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (dragging) {
            dragging = false
            val row = viewHolder.itemView
            row.translationZ = 0f
            if (MotionPrefs.animationsEnabled(row.context)) row.animate()
                .scaleX(1f).scaleY(1f).setDuration(200L).start()
            else { row.scaleX = 1f; row.scaleY = 1f }
            GestureHaptics.confirm(row)
            onDragFinish()
        }
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.3f

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        thresholdHolder = null
        onAction(viewHolder, direction)
    }

    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
            val view = viewHolder.itemView
            val fraction = min(1f, abs(dX) / (view.width * 0.3f))
            val complete = dX > 0f
            paint.color = if (complete) completeColor(viewHolder)
                else ContextCompat.getColor(view.context, R.color.error)
            val radius = view.resources.getDimension(R.dimen.radius_card)
            val left = if (complete) view.left.toFloat() else view.right + dX
            val right = if (complete) view.left + dX else view.right.toFloat()
            canvas.drawRoundRect(left, view.top.toFloat(), right, view.bottom.toFloat(), radius, radius, paint)
            val icon = AppCompatResources.getDrawable(
                view.context, if (complete) R.drawable.ic_check else R.drawable.ic_delete
            )
            if (icon != null) {
                icon.setTint(ContextCompat.getColor(view.context,
                    if (complete) R.color.on_primary else R.color.on_error))
                val size = view.resources.getDimensionPixelSize(R.dimen.space_24)
                val inset = view.resources.getDimensionPixelSize(R.dimen.space_16)
                val x = if (complete) view.left + inset + size / 2 else view.right - inset - size / 2
                val y = (view.top + view.bottom) / 2
                // Icon grows with reveal distance and pops to 1.1× at the 30% action threshold.
                val scale = if (MotionPrefs.animationsEnabled(view.context)) 0.7f + 0.4f * fraction
                    else 1f
                canvas.save()
                canvas.scale(scale, scale, x.toFloat(), y.toFloat())
                icon.setBounds(x - size / 2, y - size / 2, x + size / 2, y + size / 2)
                icon.draw(canvas)
                canvas.restore()
            }
            if (isCurrentlyActive && fraction >= 1f && thresholdHolder !== viewHolder) {
                if (complete) GestureHaptics.confirm(view) else GestureHaptics.reject(view)
                thresholdHolder = viewHolder
            } else if (fraction < 1f && thresholdHolder === viewHolder) {
                thresholdHolder = null
            }
        }
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
