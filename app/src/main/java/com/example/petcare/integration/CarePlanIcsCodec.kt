package com.example.petcare.integration

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A single task in a portable iCalendar care plan. */
data class CarePlanEntry(
    val id: Long,
    val title: String,
    val dateEpochDay: Long,
    val minutesOfDay: Int,
    val description: String,
    val location: String = ""
)

/** Writes standards-shaped VEVENT records that the app's own parser can read back. */
object CarePlanIcsCodec {
    fun encode(entries: List<CarePlanEntry>): String = buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//PetCare//Care Plan//EN")
        val dateTime = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val stamp = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        entries.forEach { entry ->
            // Floating local times match the date and reminder time shown in PetCare.
            val start = entry.dateEpochDay * DAY + entry.minutesOfDay * MINUTE
            appendLine("BEGIN:VEVENT")
            appendLine("UID:${entry.id}@petcare.local")
            appendLine("DTSTAMP:$stamp")
            appendLine("DTSTART:${dateTime.format(Date(start))}")
            appendLine("DTEND:${dateTime.format(Date(start + HOUR))}")
            appendLine("SUMMARY:${escape(entry.title)}")
            appendLine("DESCRIPTION:${escape(entry.description)}")
            if (entry.location.isNotBlank()) appendLine("LOCATION:${escape(entry.location)}")
            appendLine("END:VEVENT")
        }
        appendLine("END:VCALENDAR")
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\")
        .replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")

    private const val DAY = 86_400_000L
    private const val MINUTE = 60_000L
    private const val HOUR = 3_600_000L
}
