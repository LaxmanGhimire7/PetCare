package com.example.petcare

import com.example.petcare.data.local.pet.ColorAssignment
import com.example.petcare.data.local.pet.PetColor
import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks stable round-robin pet identity colour assignment. */
class PetColorTest {
    @Test fun newPetsCycleThroughSixColours() {
        assertEquals(1, ColorAssignment.forNewPet(0))
        assertEquals(0, ColorAssignment.forNewPet(5))
        assertEquals(PetColor.VIOLET, PetColor.fromIndex(1))
        assertEquals(PetColor.SILVER, PetColor.fromIndex(-1))
    }
}
