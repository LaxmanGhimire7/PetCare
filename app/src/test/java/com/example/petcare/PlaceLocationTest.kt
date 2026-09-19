package com.example.petcare

import com.example.petcare.location.PlaceLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the pure distance calculation used for location sorting. */
class PlaceLocationTest {
    @Test fun identicalPointsAreZeroKm() {
        assertEquals(0.0, PlaceLocation.distanceKm(27.7172, 85.3240, 27.7172, 85.3240), 0.00001)
    }

    @Test fun oneDegreeOfLatitudeIsAbout111Km() {
        assertTrue(PlaceLocation.distanceKm(0.0, 0.0, 1.0, 0.0) in 110.0..112.0)
    }
}
