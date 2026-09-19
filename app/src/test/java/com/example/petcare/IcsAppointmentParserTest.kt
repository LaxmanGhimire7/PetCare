package com.example.petcare

import com.example.petcare.integration.IcsAppointmentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsAppointmentParserTest {
    @Test
    fun parsesAppointmentAndLocation() {
        val appointment = IcsAppointmentParser.parse(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            DTSTART:20261012T093000Z
            SUMMARY:Luna annual vaccination
            DESCRIPTION:Bring vaccination record
            LOCATION:Happy Paws Clinic
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        )!!

        assertEquals("Luna annual vaccination", appointment.title)
        assertEquals("Happy Paws Clinic", appointment.location)
        assertTrue(appointment.dateEpochDay > 0)
    }
}
