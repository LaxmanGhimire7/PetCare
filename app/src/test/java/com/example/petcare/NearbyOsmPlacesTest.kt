package com.example.petcare

import com.example.petcare.location.NearbyOsmPlaces
import com.example.petcare.location.NearbyCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyOsmPlacesTest {
    @Test fun vetQueryUsesOnlyNodesAndWaysInLocalRadius() {
        val query = NearbyOsmPlaces.query(27.7, 85.3, NearbyCategory.VET)
        assertTrue(query.contains("[\"amenity\"=\"veterinary\"]"))
        assertTrue(query.contains("node[\"amenity\"=\"veterinary\"]"))
        assertTrue(query.contains("way[\"amenity\"=\"veterinary\"]"))
        assertFalse(query.contains("nwr"))
        assertTrue(query.contains("around:5000,27.7,85.3"))
        assertTrue(query.contains("out center"))
    }

    @Test fun groomingAndParkQueriesStaySeparate() {
        val grooming = NearbyOsmPlaces.query(27.7, 85.3, NearbyCategory.GROOMING)
        val parks = NearbyOsmPlaces.query(27.7, 85.3, NearbyCategory.PARK)
        assertTrue(grooming.contains("pet_grooming|dog_grooming"))
        assertFalse(grooming.contains("park|dog_park"))
        assertTrue(parks.contains("park|dog_park"))
        assertFalse(parks.contains("pet_grooming|dog_grooming"))
    }
}
