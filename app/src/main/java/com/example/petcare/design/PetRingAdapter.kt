package com.example.petcare.design

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.petcare.R

/**
 * One entry in the pet ring row. Use [petColor] = null for the "All" entry,
 * which uses the brand orange and a paw icon.
 * [icon] is the species icon (pc_ic_dog, pc_ic_cat, pc_ic_paw) shown when there is no photo.
 */
data class PetRingItem(
    val key: String,
    val name: String,
    @DrawableRes val icon: Int,
    val photoUri: String?,
    val petColor: PetColor?,
    val progress: Float,
    val countLabel: String,
    val selected: Boolean,
)

class PetRingAdapter(
    private val loadPhoto: (ImageView, String) -> Unit,
    private val onSelect: (key: String) -> Unit,
    private val onFilter: (key: String) -> Unit,
) : ListAdapter<PetRingItem, PetRingAdapter.Holder>(Diff) {

    private val sweepFromEmpty = mutableSetOf<String>()

    /** [animateFromEmpty] = true for the once-per-launch ring sweep. */
    fun submit(items: List<PetRingItem>, animateFromEmpty: Boolean) {
        if (animateFromEmpty) sweepFromEmpty.addAll(items.map { it.key })
        submitList(items)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.pc_item_pet_ring, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val ring: PetRingView = view.findViewById(R.id.pcRing)
        private val photo: ImageView = view.findViewById(R.id.pcRingPhoto)
        private val icon: ImageView = view.findViewById(R.id.pcRingIcon)
        private val name: TextView = view.findViewById(R.id.pcRingName)
        private val count: TextView = view.findViewById(R.id.pcRingCount)
        private var boundKey: String? = null

        init {
            photo.clipToOutline = true
        }

        fun bind(item: PetRingItem) {
            val ctx = itemView.context
            val main = item.petColor?.main(ctx) ?: ContextCompat.getColor(ctx, R.color.pc_primary)
            val tint = item.petColor?.tint(ctx) ?: ContextCompat.getColor(ctx, R.color.pc_primary_container)

            ring.setRingColor(main)
            when {
                sweepFromEmpty.remove(item.key) -> {
                    ring.setProgress(0f, animate = false)
                    ring.setProgress(item.progress, animate = true)
                }
                boundKey == item.key -> ring.setProgress(item.progress, animate = true)
                else -> ring.setProgress(item.progress, animate = false)
            }
            boundKey = item.key

            if (item.photoUri != null) {
                photo.visibility = View.VISIBLE
                icon.visibility = View.GONE
                loadPhoto(photo, item.photoUri)
            } else {
                photo.visibility = View.GONE
                icon.visibility = View.VISIBLE
                icon.setImageResource(item.icon)
                ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(main))
                ViewCompat.setBackgroundTintList(icon, ColorStateList.valueOf(tint))
            }

            name.text = item.name
            // The selected pet's name is bold and full contrast; the rest stay quiet.
            TextViewCompat.setTextAppearance(
                name,
                if (item.selected) R.style.TextAppearance_PC_LabelBold else R.style.TextAppearance_PC_Label,
            )
            name.setTextColor(
                ContextCompat.getColor(ctx, if (item.selected) R.color.pc_text_primary else R.color.pc_text_secondary),
            )
            count.text = item.countLabel
            itemView.isSelected = item.selected
            itemView.contentDescription = ctx.getString(R.string.pc_a11y_ring, item.name, item.countLabel)
            itemView.setOnClickListener {
                Haptics.tick(it)
                onSelect(item.key)
            }
            itemView.setOnLongClickListener {
                Haptics.tick(it)
                onFilter(item.key)
                true
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<PetRingItem>() {
        override fun areItemsTheSame(oldItem: PetRingItem, newItem: PetRingItem) = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: PetRingItem, newItem: PetRingItem) = oldItem == newItem
        override fun getChangePayload(oldItem: PetRingItem, newItem: PetRingItem): Any = "rebind"
    }
}
