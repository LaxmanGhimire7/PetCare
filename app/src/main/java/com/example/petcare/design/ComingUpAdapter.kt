package com.example.petcare.design

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.petcare.R

/**
 * A notable task in the next 7 days, excluding today and routine feeds/walks.
 * [weekday] e.g. "Fri", [date] e.g. "25".
 */
data class ComingUpItem(
    val id: Long,
    val weekday: String,
    val date: String,
    val title: String,
    val petName: String,
    val petColor: PetColor,
    @DrawableRes val categoryIcon: Int,
)

class ComingUpAdapter(
    private val onOpen: (id: Long) -> Unit,
) : ListAdapter<ComingUpItem, ComingUpAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.pc_item_coming_up, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val weekday: TextView = view.findViewById(R.id.pcComingWeekday)
        private val date: TextView = view.findViewById(R.id.pcComingDate)
        private val title: TextView = view.findViewById(R.id.pcComingTitle)
        private val dot: View = view.findViewById(R.id.pcComingDot)
        private val pet: TextView = view.findViewById(R.id.pcComingPet)
        private val category: ImageView = view.findViewById(R.id.pcComingCategory)

        fun bind(item: ComingUpItem) {
            weekday.text = item.weekday
            date.text = item.date
            title.text = item.title
            pet.text = item.petName
            ViewCompat.setBackgroundTintList(dot, ColorStateList.valueOf(item.petColor.main(itemView.context)))
            if (item.categoryIcon != 0) {
                category.visibility = View.VISIBLE
                category.setImageResource(item.categoryIcon)
            } else {
                category.visibility = View.GONE
            }
            itemView.contentDescription = "${item.title}, ${item.petName}, ${item.weekday} ${item.date}"
            itemView.setOnClickListener { onOpen(item.id) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ComingUpItem>() {
        override fun areItemsTheSame(oldItem: ComingUpItem, newItem: ComingUpItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ComingUpItem, newItem: ComingUpItem) = oldItem == newItem
    }
}

/** Hairline between rows of a grouped list, inset so it starts at the text, not the edge. */
class InsetDividerDecoration(context: Context, private val insetStartPx: Int) : RecyclerView.ItemDecoration() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pc_hairline)
        strokeWidth = context.resources.displayMetrics.density.coerceAtLeast(1f)
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val left = (parent.paddingLeft + insetStartPx).toFloat()
        val right = (parent.width - parent.paddingRight).toFloat()
        for (i in 0 until parent.childCount - 1) {
            val child = parent.getChildAt(i)
            val y = child.bottom + child.translationY
            canvas.drawLine(left, y, right, y, paint)
        }
    }
}
