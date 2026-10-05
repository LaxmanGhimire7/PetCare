package com.example.petcare.design

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import com.example.petcare.R
import java.util.TimeZone
import kotlin.math.roundToInt

/** Data for the "At a glance" section on Today. Every field is optional. */
data class GlanceState(
    val streakDays: Int = 0,
    /** Monday to Sunday, 0..1 done; null for days with no tasks or still to come. */
    val weekDays: List<Float?> = emptyList(),
    /** 0 = Monday. */
    val todayIndex: Int = -1,
    /** Share of this week's tasks done so far, 0..100; null when there were none. */
    val weekPercent: Int? = null,
    val health: HealthGlance? = null,
    val place: PlaceGlance? = null,
) {
    companion object {
        val EMPTY = GlanceState()
    }
}

/** The next vaccination, vet visit or medication. [title] e.g. "Rabies booster for Luna". */
data class HealthGlance(
    val taskId: Long,
    val title: String,
    /** e.g. "Sat 26 September, 10:00". */
    val whenLabel: String,
    val daysAway: Int,
    val petColor: PetColor,
    @DrawableRes val icon: Int,
)

/** A saved place, usually the vet. [label] e.g. "Your vet"; [detail] e.g. "1.8 km away" or the address. */
data class PlaceGlance(
    val placeId: Long,
    val label: String,
    val name: String,
    val detail: String?,
    val phone: String?,
    val latitude: Double,
    val longitude: Double,
)

/** One day's task count, keyed by [GlanceMath.epochDay]. */
data class DayTally(val epochDay: Long, val done: Int, val total: Int)

/**
 * Streak and weekly score, computed from per-day task counts. Pure Kotlin with no
 * date library, so it works on minSdk 24 without desugaring.
 */
object GlanceMath {

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val MAX_LOOKBACK_DAYS = 366

    /** Local calendar day number for a timestamp (days since 1 Jan 1970). */
    fun epochDay(millis: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Math.floorDiv(millis + zone.getOffset(millis), DAY_MS)

    /** 0 = Monday ... 6 = Sunday. (1 Jan 1970 was a Thursday.) */
    fun dayOfWeekIndex(epochDay: Long): Int = Math.floorMod(epochDay + 3, 7L).toInt()

    fun weekStart(today: Long): Long = today - dayOfWeekIndex(today)

    /**
     * Consecutive days, counting back, on which every task was done. Days with no tasks
     * don't break a streak. Today counts only once it's finished, so an unfinished today
     * never resets yesterday's streak.
     */
    fun streak(days: List<DayTally>, today: Long): Int {
        val byDay = days.associateBy { it.epochDay }
        val todayTally = byDay[today]
        var day = if (todayTally != null && todayTally.total > 0 && todayTally.done >= todayTally.total) today else today - 1
        var streak = 0
        repeat(MAX_LOOKBACK_DAYS) {
            val tally = byDay[day]
            if (tally != null && tally.total > 0) {
                if (tally.done >= tally.total) streak++ else return streak
            }
            day--
        }
        return streak
    }

    /** Monday to Sunday of the current week. */
    fun weekFractions(days: List<DayTally>, today: Long): List<Float?> {
        val byDay = days.associateBy { it.epochDay }
        val start = weekStart(today)
        return (0 until 7).map { offset ->
            val day = start + offset
            val tally = byDay[day]
            if (day > today || tally == null || tally.total == 0) null else tally.done.toFloat() / tally.total
        }
    }

    /** Percentage of this week's tasks (Monday to today) that are done; null if there were none. */
    fun weekPercent(days: List<DayTally>, today: Long): Int? {
        val start = weekStart(today)
        val week = days.filter { it.epochDay in start..today }
        val total = week.sumOf { it.total }
        if (total == 0) return null
        return (week.sumOf { it.done } * 100f / total).roundToInt()
    }

    /** Builds the streak and week part of [GlanceState] in one call. */
    fun summary(days: List<DayTally>, today: Long): GlanceState = GlanceState(
        streakDays = streak(days, today),
        weekDays = weekFractions(days, today),
        todayIndex = dayOfWeekIndex(today),
        weekPercent = weekPercent(days, today),
    )
}

/**
 * Remembers today's SMS hand-over for the Today card, and adds it to the notification
 * centre. Call [record] from the SMS delegation flow once the message has been sent.
 */
object DelegationLog {

    private const val PREFS = "pc_delegation"

    fun record(context: Context, recipientName: String, now: Long = System.currentTimeMillis()) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", recipientName)
            .putLong("at", now)
            .apply()
        InboxStore.get(context).add(
            type = InboxType.DELEGATION,
            title = context.getString(R.string.pc_delegation_inbox_title, recipientName),
            body = context.getString(R.string.pc_delegation_inbox_body),
            now = now,
        )
    }

    /** Today's hand-over as (recipient, sent-at millis), or null if nothing was sent today. */
    fun today(context: Context, now: Long = System.currentTimeMillis()): Pair<String, Long>? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString("name", null) ?: return null
        val at = prefs.getLong("at", 0L)
        return if (GlanceMath.epochDay(at) == GlanceMath.epochDay(now)) name to at else null
    }
}

/** Opens the dialler or a maps app. No permissions needed. */
object PlaceIntents {

    fun call(context: Context, phone: String) {
        start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))
    }

    /** Any installed maps app; falls back to Google Maps in the browser. */
    fun directions(context: Context, latitude: Double, longitude: Double, name: String) {
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(name)})"))
        if (!start(context, geo)) {
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude")))
        }
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
