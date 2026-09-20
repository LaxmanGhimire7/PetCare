package com.example.petcare

import com.example.petcare.integration.CarePlanEntry
import com.example.petcare.integration.CarePlanIcsCodec
import com.example.petcare.integration.IcsAppointmentParser
import org.junit.Assert.assertEquals
import org.junit.Test

/** The app must be able to import every event it exports. */
class CarePlanIcsCodecTest {
    @Test fun exportedPlanRoundTripsAllTasks() {
        val entries = listOf(
            CarePlanEntry(1, "Vaccination", 21_000, 9 * 60, "Bring records", "Happy Paws Clinic"),
            CarePlanEntry(2, "Evening walk", 21_001, 17 * 60 + 30, "Leash, water")
        )
        val restored = IcsAppointmentParser.parseAll(CarePlanIcsCodec.encode(entries))
        assertEquals(2, restored.size)
        assertEquals(entries.map { it.title }, restored.map { it.title })
        assertEquals(entries.map { it.dateEpochDay }, restored.map { it.dateEpochDay })
        assertEquals(entries.map { it.minutesOfDay }, restored.map { it.minutesOfDay })
        assertEquals("Happy Paws Clinic", restored.first().location)
    }

    @Test fun longUnicodeDescriptionsStayFoldedAndReadable() {
        val description = "Bring vaccination records for Luna 🐾. ".repeat(5)
        val encoded = CarePlanIcsCodec.encode(listOf(
            CarePlanEntry(3, "Annual visit", 21_000, 10 * 60, description)
        ))
        assert(encoded.split("\r\n").all { it.toByteArray(Charsets.UTF_8).size <= 75 })
        assertEquals(description, IcsAppointmentParser.parseAll(encoded).single().notes)
    }
}
