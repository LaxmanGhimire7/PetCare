package com.example.petcare

import com.example.petcare.location.NominatimPlaceSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NominatimPlaceSearchTest {
    @Test fun explicitSearchEncodesQueryAndBiasesTowardVisibleMap() {
        val url = NominatimPlaceSearch.searchUrl("ISMT College", 27.68 to 85.35)

        assertEquals("https", url.scheme)
        assertEquals("nominatim.openstreetmap.org", url.host)
        assertEquals("ISMT College", url.queryParameter("q"))
        assertEquals("jsonv2", url.queryParameter("format"))
        assertEquals("1", url.queryParameter("limit"))
        assertEquals("84.65,28.45,86.15,26.95", url.queryParameter("viewbox"))
    }

    @Test fun invalidLocationDoesNotRestrictWorldwideSearch() {
        val url = NominatimPlaceSearch.searchUrl("Kathmandu", 100.0 to 85.0)

        assertNull(url.queryParameter("viewbox"))
    }
}
