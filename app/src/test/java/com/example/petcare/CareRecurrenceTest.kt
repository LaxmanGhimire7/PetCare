package com.example.petcare

import com.example.petcare.data.local.care.CARE_FREQUENCY_DAILY
import com.example.petcare.data.local.care.CARE_FREQUENCY_MONTHLY
import com.example.petcare.data.local.care.CARE_FREQUENCY_WEEKLY
import com.example.petcare.data.local.care.CareRecurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Month-end dates must follow calendar months rather than fixed 30-day jumps. */
class CareRecurrenceTest {
    private fun day(date: String): Long = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }.parse(date)!!.time / 86_400_000L

    @Test fun dailyAndWeeklyAdvanceByCalendarDayAndWeek() {
        assertEquals(day("2026-03-01"), CareRecurrence.nextDate(day("2026-02-28"), CARE_FREQUENCY_DAILY))
        assertEquals(day("2026-03-07"), CareRecurrence.nextDate(day("2026-02-28"), CARE_FREQUENCY_WEEKLY))
    }

    @Test fun monthEndClampsForLeapAndCommonYears() {
        assertEquals(day("2025-02-28"), CareRecurrence.nextDate(day("2025-01-31"), CARE_FREQUENCY_MONTHLY))
        assertEquals(day("2024-02-29"), CareRecurrence.nextDate(day("2024-01-31"), CARE_FREQUENCY_MONTHLY))
        assertEquals(day("2026-05-30"), CareRecurrence.nextDate(day("2026-04-30"), CARE_FREQUENCY_MONTHLY))
    }

    @Test fun oneTimeHasNoSuccessor() {
        assertNull(CareRecurrence.nextDate(day("2026-01-01"), "One time"))
    }
}
