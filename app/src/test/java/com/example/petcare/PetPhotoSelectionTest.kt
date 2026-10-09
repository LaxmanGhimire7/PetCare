package com.example.petcare

import com.example.petcare.ui.pets.PetPhotoSelection
import org.junit.Assert.assertEquals
import org.junit.Test

class PetPhotoSelectionTest {
    @Test fun editAddsPhotosWithoutReplacingTheSavedCover() {
        val saved = listOf("cover", "second")
        assertEquals(listOf("cover", "second", "third", "fourth"),
            PetPhotoSelection.append(saved, listOf("second", "third", "fourth")))
    }

    @Test fun selectionKeepsAtMostFiveUniquePhotos() {
        assertEquals(listOf("a", "b", "c", "d", "e"),
            PetPhotoSelection.append(listOf("a", "b", "c"), listOf("d", "d", "e", "f")))
    }

    @Test fun choosingCoverChangesOnlyTheOrder() {
        assertEquals(listOf("third", "cover", "second"),
            PetPhotoSelection.chooseCover(listOf("cover", "second", "third"), "third"))
    }
}
