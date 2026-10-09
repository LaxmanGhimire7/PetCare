package com.example.petcare.ui.pets

/** Keeps the cover first while limiting each pet gallery to five distinct photos. */
internal object PetPhotoSelection {
    const val MAX_PHOTOS = 5

    fun <T> append(current: List<T>, picked: List<T>): List<T> {
        val available = (MAX_PHOTOS - current.size).coerceAtLeast(0)
        val newPhotos = picked.distinct().filterNot { it in current }.take(available)
        return current + newPhotos
    }

    fun <T> chooseCover(current: List<T>, cover: T): List<T> = if (cover in current) {
        listOf(cover) + current.filterNot { it == cover }
    } else current
}
