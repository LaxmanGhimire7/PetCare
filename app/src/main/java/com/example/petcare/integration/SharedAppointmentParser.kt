package com.example.petcare.integration

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Editable fields extracted from an email, message, or calendar attachment. */
data class AppointmentDraft(
    val title: String,
    val dateEpochDay: Long?,
    val minutesOfDay: Int?,
    val clinic: String?,
    val notes: String
)

/** Extracts common clinic dates and times while leaving uncertain fields blank for review. */
object SharedAppointmentParser {
    fun parse(content: String, mimeType: String?, todayEpochDay: Long = System.currentTimeMillis() / DAY): AppointmentDraft {
        if (mimeType == "text/calendar" || content.contains("BEGIN:VEVENT")) {
            val ics = IcsAppointmentParser.parse(content)
            if (ics != null) return AppointmentDraft(
                title = ics.title,
                dateEpochDay = ics.dateEpochDay,
                minutesOfDay = ics.minutesOfDay,
                clinic = ics.location,
                notes = ics.notes
            )
        }
        val normalized = content.replace(Regex("\\s+"), " ").trim()
        val date = parseDate(normalized, todayEpochDay)
        val time = parseTime(normalized)
        val clinic = Regex("(?i)(?:at|from)\\s+([A-Za-z0-9&' -]+?(?:clinic|hospital|vets))\\b")
            .find(normalized)?.groupValues?.get(1)?.trim()
        val title = (Regex("(?i)\\bvaccination\\b|\\bcheck.?up\\b")
            .find(normalized)?.value ?: normalized.lineSequence().firstOrNull()?.take(80).orEmpty())
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        return AppointmentDraft(title, date, time, clinic, content.trim())
    }

    /** Day-first numeric dates, full month names, and abbreviated month/day are supported. */
    private fun parseDate(value: String, todayEpochDay: Long): Long? {
        val numeric = Regex("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b").find(value)
        if (numeric != null) return epochDay(numeric.value, "d/M/yyyy")
        val full = Regex("\\b\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4}\\b").find(value)
        if (full != null) return epochDay(full.value, "d MMMM yyyy")
        val short = Regex("\\b[A-Za-z]{3}\\s+\\d{1,2}(?:,?\\s+\\d{4})?\\b").find(value)
        if (short != null) {
            val raw = short.value.replace(",", "")
            val hasYear = Regex("\\d{4}").containsMatchIn(raw)
            val currentYear = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = todayEpochDay * DAY
            }.get(Calendar.YEAR)
            val candidate = epochDay(if (hasYear) raw else "$raw $currentYear", "MMM d yyyy")
            if (candidate != null && !hasYear && candidate < todayEpochDay)
                return epochDay("$raw ${currentYear + 1}", "MMM d yyyy")
            return candidate
        }
        return null
    }

    private fun epochDay(value: String, pattern: String): Long? = runCatching {
        SimpleDateFormat(pattern, Locale.ENGLISH).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(value)?.time?.div(DAY)
    }.getOrNull()

    /** A 12-hour suffix or a 24-hour clock is converted to minutes after midnight. */
    private fun parseTime(value: String): Int? {
        val match = Regex("(?i)\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b|\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b")
            .find(value) ?: return null
        if (match.groupValues[3].isNotBlank()) {
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            if (hour !in 1..12) return null
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            if (minute !in 0..59) return null
            return (hour % 12 + if (match.groupValues[3].equals("pm", true)) 12 else 0) * 60 + minute
        }
        val hour = match.groupValues[4].toIntOrNull() ?: return null
        val minute = match.groupValues[5].toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    private const val DAY = 86_400_000L
}
