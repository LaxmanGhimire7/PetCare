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
        appendIcsLine("BEGIN:VCALENDAR")
        appendIcsLine("VERSION:2.0")
        appendIcsLine("PRODID:-//PetCare//Care Plan//EN")
        val dateTime = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val stamp = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        entries.forEach { entry ->
            // Floating local times match the date and reminder time shown in PetCare.
            val start = entry.dateEpochDay * DAY + entry.minutesOfDay * MINUTE
            appendIcsLine("BEGIN:VEVENT")
            appendIcsLine("UID:${entry.id}@petcare.local")
            appendIcsLine("DTSTAMP:$stamp")
            appendIcsLine("DTSTART:${dateTime.format(Date(start))}")
            appendIcsLine("DTEND:${dateTime.format(Date(start + HOUR))}")
            appendIcsLine("SUMMARY:${escape(entry.title)}")
            appendIcsLine("DESCRIPTION:${escape(entry.description)}")
            if (entry.location.isNotBlank()) appendIcsLine("LOCATION:${escape(entry.location)}")
            appendIcsLine("END:VEVENT")
        }
        appendIcsLine("END:VCALENDAR")
    }

    /** iCalendar uses CRLF and folds each content line after at most 75 UTF-8 octets. */
    private fun StringBuilder.appendIcsLine(line: String) {
        var bytesOnLine = 0
        var offset = 0
        while (offset < line.length) {
            val codePoint = line.codePointAt(offset)
            val character = String(Character.toChars(codePoint))
            val bytes = character.toByteArray(Charsets.UTF_8).size
            if (bytesOnLine + bytes > 75) {
                append("\r\n ")
                bytesOnLine = 1
            }
            append(character)
            bytesOnLine += bytes
            offset += Character.charCount(codePoint)
        }
        append("\r\n")
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\")
        .replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")

    private const val DAY = 86_400_000L
    private const val MINUTE = 60_000L
    private const val HOUR = 3_600_000L
}
