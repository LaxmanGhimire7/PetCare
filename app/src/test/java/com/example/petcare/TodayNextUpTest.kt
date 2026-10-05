package com.example.petcare

import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.design.NextUpState
import com.example.petcare.ui.home.LocalDayClock
import com.example.petcare.ui.home.TodayData
import com.example.petcare.ui.toTodayUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TodayNextUpTest {
    private val day = 21_000L
    private val now = LocalDayClock.dueMillis(day, 10 * 60)

    @Test fun futureTaskWinsOverOverdueTask() {
        val state = TodayData(emptyList(), listOf(
            task(1, "Overdue", day - 1, 9 * 60),
            task(2, "Future", day, 14 * 60),
        ), emptyList(), emptyList()).toTodayUiState("Alex", day, null, nowMillis = now, today = day)

        val next = state.nextUp as NextUpState.Upcoming
        assertEquals("Future", next.title)
        assertEquals(null, next.dueFullLabel)
    }

    @Test fun mostRecentOverdueTaskHasFullDateAndTime() {
        val state = TodayData(emptyList(), listOf(
            task(1, "Older", day - 1, 9 * 60),
            task(2, "Recent", day, 8 * 60),
        ), emptyList(), emptyList()).toTodayUiState("Alex", day, null, nowMillis = now, today = day)

        val next = state.nextUp as NextUpState.Upcoming
        assertEquals("Recent", next.title)
        assertNotNull(next.dueFullLabel)
        assertEquals(true, next.dueFullLabel!!.endsWith("08:00"))
    }

    private fun task(id: Long, title: String, dueDay: Long, minutes: Int) = CareTaskSummary(
        id = id, petId = 1, title = title, dueDateEpochDay = dueDay,
        reminderMinutesOfDay = minutes, petName = "Luna", petColorIndex = 0,
        category = "General", frequency = "One time", requiredSupplies = "", notes = "",
        latitude = null, longitude = null, placeId = null, sortOrder = id, isCompleted = false,
    )
}
