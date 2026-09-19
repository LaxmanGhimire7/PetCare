package com.example.petcare

import com.example.petcare.integration.SharedAppointmentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Realistic clinic-message formats must be recognized without silently inventing fields. */
class SharedAppointmentParserTest {
    @Test fun parsesDayFirstDateTimeAndClinicFromEmail() {
        val draft = SharedAppointmentParser.parse(
            "Luna's vaccination is booked for 12/03/2026 at 5:30pm at Happy Paws Clinic.",
            "text/plain"
        )
        assertEquals("Vaccination", draft.title)
        assertEquals(day("12/03/2026", "dd/MM/yyyy"), draft.dateEpochDay)
        assertEquals(17 * 60 + 30, draft.minutesOfDay)
        assertEquals("Happy Paws Clinic", draft.clinic)
    }

    @Test fun parsesWrittenMonthAnd24HourTime() {
        val draft = SharedAppointmentParser.parse(
            "Check-up at 09:45 on 12 March 2026 at Riverside Veterinary Clinic",
            "text/plain"
        )
        assertEquals(day("12 March 2026", "dd MMMM yyyy"), draft.dateEpochDay)
        assertEquals(9 * 60 + 45, draft.minutesOfDay)
    }

    @Test fun leavesMissingFieldsBlank() {
        val draft = SharedAppointmentParser.parse("Please call us about Luna.", "text/plain")
        assertNull(draft.dateEpochDay)
        assertNull(draft.minutesOfDay)
        assertNull(draft.clinic)
    }

    private fun day(value: String, pattern: String): Long =
        SimpleDateFormat(pattern, Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(value)!!.time / 86_400_000L
}
