package com.example.petcare.ui.pets

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import coil.load
import com.example.petcare.R
import com.example.petcare.databinding.FragmentAddPetBinding
import com.example.petcare.databinding.ItemPetPhotoChoiceBinding

/** Displays every selected photo and lets the owner choose the first, profile-facing photo. */
internal object PetPhotoStrip {
    fun render(
        binding: FragmentAddPetBinding,
        photos: List<Uri>,
        onCoverSelected: (Uri) -> Unit,
        onPhotoRemoved: (Uri) -> Unit,
    ) {
        val context = binding.root.context
        val hasPhotos = photos.isNotEmpty()
        binding.photoSelectionHint.visibility = if (hasPhotos) View.VISIBLE else View.GONE
        binding.photoStripScroll.visibility = if (hasPhotos) View.VISIBLE else View.GONE
        binding.clearPhotosButton.visibility = if (hasPhotos) View.VISIBLE else View.GONE
        binding.photoCountText.text = if (hasPhotos) {
            context.getString(R.string.pet_photo_count, photos.size, PetPhotoSelection.MAX_PHOTOS)
        } else context.getString(R.string.profile_photo_title)

        // The saved list order determines the photo used by cards, tasks, and the profile hero.
        photos.firstOrNull()?.let { binding.petPhotoPreview.load(it) }
            ?: binding.petPhotoPreview.load(R.drawable.ic_pets)
        val previewPadding = if (hasPhotos) 0 else context.resources.getDimensionPixelSize(R.dimen.layout_22_dp)
        binding.petPhotoPreview.setPadding(previewPadding, previewPadding, previewPadding, previewPadding)

        binding.photoThumbnailRow.removeAllViews()
        photos.forEachIndexed { index, uri ->
            val item = ItemPetPhotoChoiceBinding.inflate(
                LayoutInflater.from(context), binding.photoThumbnailRow, false
            )
            item.photoChoiceImage.load(uri)
            val cover = index == 0
            item.photoChoiceCard.strokeColor = context.getColor(if (cover) R.color.primary else R.color.hairline)
            item.photoChoiceLabel.text = if (cover) context.getString(R.string.pet_photo_cover)
                else context.getString(R.string.pet_photo_number, index + 1)
            item.photoChoiceCard.contentDescription = if (cover) context.getString(R.string.pet_photo_cover)
                else context.getString(R.string.pet_photo_make_cover, index + 1)
            item.photoChoiceCard.setOnClickListener { onCoverSelected(uri) }
            item.removePhotoButton.contentDescription = context.getString(R.string.remove_pet_photo, index + 1)
            item.removePhotoButton.setOnClickListener { onPhotoRemoved(uri) }
            binding.photoThumbnailRow.addView(item.root)
        }
    }
}
