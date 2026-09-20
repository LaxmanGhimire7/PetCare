package com.example.petcare.integration

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Appointment fields extracted from an external calendar invite. */
data class ImportedAppointment(
    val title: String,
    val dateEpochDay: Long,
    val notes: String,
    val minutesOfDay: Int? = null,
    val location: String? = null
)

/** Reads calendar text while tolerating missing optional ICS fields. */
object IcsAppointmentParser {
    /** Each exported VEVENT can be read independently, preserving full care plans. */
    fun parseAll(content: String): List<ImportedAppointment> {
        val events = Regex("BEGIN:VEVENT[\\s\\S]*?END:VEVENT").findAll(content).toList()
        return if (events.isEmpty()) listOfNotNull(parse(content))
            else events.mapNotNull { parse(it.value) }
    }

    fun parse(content: String): ImportedAppointment? {
        val lines = unfold(content)
        val title = value(lines, "SUMMARY")?.decodeIcs()?.ifBlank { null } ?: return null
        val rawDate = lines.firstOrNull { it.substringBefore(':').startsWith("DTSTART") }?.substringAfter(':') ?: return null
        val date = parseDate(rawDate) ?: return null
        val description = value(lines, "DESCRIPTION")?.decodeIcs().orEmpty()
        val location = value(lines, "LOCATION")?.decodeIcs().orEmpty()
        val notes = description
        val clock = rawDate.substringAfter('T', "").take(4)
        val minutes = if (clock.length == 4 && clock.all(Char::isDigit))
            clock.take(2).toInt() * 60 + clock.takeLast(2).toInt() else null
        return ImportedAppointment(title, date / DAY, notes, minutes, location.ifBlank { null })
    }

    private fun unfold(content: String): List<String> = content.replace("\r\n", "\n").lines().fold(mutableListOf()) { result, line ->
        if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) result[result.lastIndex] += line.drop(1) else result += line
        result
    }
    private fun value(lines: List<String>, name: String) = lines.firstOrNull { it.substringBefore(':').substringBefore(';') == name }?.substringAfter(':')
    private fun String.decodeIcs() = replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";")
    private fun parseDate(value: String): Long? {
        val patterns = when {
            value.length == 8 -> listOf("yyyyMMdd")
            value.endsWith("Z") -> listOf("yyyyMMdd'T'HHmmss'Z'", "yyyyMMdd'T'HHmm'Z'")
            else -> listOf("yyyyMMdd'T'HHmmss", "yyyyMMdd'T'HHmm")
        }
        return patterns.firstNotNullOfOrNull { pattern -> runCatching { SimpleDateFormat(pattern, Locale.US).apply { isLenient = false; timeZone = TimeZone.getTimeZone("UTC") }.parse(value)?.time }.getOrNull() }
    }
    private const val DAY = 86_400_000L
}
