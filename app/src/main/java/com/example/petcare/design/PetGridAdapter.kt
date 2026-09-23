package com.example.petcare.design

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.petcare.R

/**
 * One pet on the Pets screen. [todayLabel] e.g. "2 of 3 today".
 * [icon] is the species icon (pc_ic_dog, pc_ic_cat, pc_ic_paw) shown when there is no photo.
 */
data class PetCardItem(
    val id: Long,
    val name: String,
    val breed: String,
    @DrawableRes val icon: Int,
    val photoUri: String?,
    val petColor: PetColor,
    val progress: Float,
    val todayLabel: String,
)

sealed interface PetGridItem {
    val key: String

    data class Pet(val card: PetCardItem, val wide: Boolean) : PetGridItem {
        override val key: String get() = "pet_${card.id}"
    }

    object AddCell : PetGridItem {
        override val key: String get() = "add_cell"
    }

    object AddFull : PetGridItem {
        override val key: String get() = "add_full"
    }
}

object PetGridBuilder {
    /**
     * One pet: a full-width card. Odd count: the Add tile fills the empty grid slot.
     * Even count: a full-width Add row underneath. The grid never has a hole in it.
     */
    fun build(pets: List<PetCardItem>): List<PetGridItem> = when {
        pets.isEmpty() -> listOf(PetGridItem.AddFull)
        pets.size == 1 -> listOf(PetGridItem.Pet(pets[0], wide = true), PetGridItem.AddFull)
        pets.size % 2 == 1 -> pets.map { PetGridItem.Pet(it, wide = false) } + PetGridItem.AddCell
        else -> pets.map { PetGridItem.Pet(it, wide = false) } + PetGridItem.AddFull
    }
}

class PetGridAdapter(
    private val loadPhoto: (ImageView, String) -> Unit,
    private val onOpen: (pet: PetCardItem, sharedView: View) -> Unit,
    private val onMenu: (pet: PetCardItem, anchor: View) -> Unit,
    private val onAdd: () -> Unit,
) : ListAdapter<PetGridItem, RecyclerView.ViewHolder>(Diff) {

    /** Set this on a GridLayoutManager with spanCount = 2. */
    val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int = when (val item = getItem(position)) {
            is PetGridItem.Pet -> if (item.wide) 2 else 1
            PetGridItem.AddCell -> 1
            PetGridItem.AddFull -> 2
        }
    }

    override fun getItemViewType(position: Int): Int = when (val item = getItem(position)) {
        is PetGridItem.Pet -> if (item.wide) TYPE_WIDE else TYPE_CARD
        PetGridItem.AddCell -> TYPE_ADD_CELL
        PetGridItem.AddFull -> TYPE_ADD_FULL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_WIDE -> PetHolder(inflater.inflate(R.layout.pc_item_pet_card_wide, parent, false))
            TYPE_CARD -> PetHolder(inflater.inflate(R.layout.pc_item_pet_card, parent, false))
            TYPE_ADD_CELL -> AddHolder(inflater.inflate(R.layout.pc_item_add_pet_cell, parent, false))
            else -> AddHolder(inflater.inflate(R.layout.pc_item_add_pet_full, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is PetGridItem.Pet -> (holder as PetHolder).bind(item.card)
            else -> (holder as AddHolder).bind()
        }
    }

    private inner class PetHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val frame: View = view.findViewById(R.id.pcPetPhotoFrame)
        private val photo: ImageView = view.findViewById(R.id.pcPetPhoto)
        private val icon: ImageView = view.findViewById(R.id.pcPetIcon)
        private val ring: PetRingView = view.findViewById(R.id.pcPetRing)
        private val name: TextView = view.findViewById(R.id.pcPetName)
        private val breed: TextView = view.findViewById(R.id.pcPetBreed)
        private val today: TextView = view.findViewById(R.id.pcPetToday)

        init {
            frame.clipToOutline = true
        }

        fun bind(pet: PetCardItem) {
            val ctx = itemView.context
            val main = pet.petColor.main(ctx)
            ViewCompat.setBackgroundTintList(frame, ColorStateList.valueOf(pet.petColor.tint(ctx)))

            if (pet.photoUri != null) {
                photo.visibility = View.VISIBLE
                icon.visibility = View.GONE
                loadPhoto(photo, pet.photoUri)
            } else {
                photo.visibility = View.GONE
                photo.setImageDrawable(null)
                icon.visibility = View.VISIBLE
                icon.setImageResource(pet.icon)
                ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(main))
            }

            ring.setRingColor(main)
            ring.setProgress(pet.progress, animate = false)
            name.text = pet.name
            breed.text = pet.breed
            breed.visibility = if (pet.breed.isBlank()) View.GONE else View.VISIBLE
            today.text = pet.todayLabel

            ViewCompat.setTransitionName(itemView, "pet_${pet.id}")
            itemView.contentDescription = ctx.getString(R.string.pc_a11y_pet_card, pet.name, pet.todayLabel)
            itemView.setOnClickListener { onOpen(pet, itemView) }
            itemView.setOnLongClickListener {
                Haptics.tick(it)
                onMenu(pet, itemView)
                true
            }
        }
    }

    private inner class AddHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            itemView.contentDescription = itemView.context.getString(R.string.pc_add_pet)
            itemView.setOnClickListener { onAdd() }
        }
    }

    private object Diff : DiffUtil.ItemCallback<PetGridItem>() {
        override fun areItemsTheSame(oldItem: PetGridItem, newItem: PetGridItem) =
            oldItem.key == newItem.key && oldItem.javaClass == newItem.javaClass &&
                (oldItem as? PetGridItem.Pet)?.wide == (newItem as? PetGridItem.Pet)?.wide

        override fun areContentsTheSame(oldItem: PetGridItem, newItem: PetGridItem) = oldItem == newItem
    }

    private companion object {
        const val TYPE_WIDE = 1
        const val TYPE_CARD = 2
        const val TYPE_ADD_CELL = 3
        const val TYPE_ADD_FULL = 4
    }
}
