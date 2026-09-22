package com.example.petcare

import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.ui.home.TodayTimeline
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayTimelineTest {
    @Test fun `selected day excludes future recurring successor`() {
        val original = task(1, 100, 480, completed = true)
        val successor = task(2, 101, 480)
        assertEquals(listOf(1L), TodayTimeline.select(
            listOf(successor), listOf(original), 100, 100, null, false
        ).map(CareTaskSummary::id))
    }

    @Test fun `today includes overdue but another day does not`() {
        val overdue = task(1, 99, 600)
        val todayTask = task(2, 100, 700)
        val tomorrow = task(3, 101, 800)
        assertEquals(listOf(1L, 2L), TodayTimeline.select(
            listOf(overdue, todayTask, tomorrow), emptyList(), 100, 100, null, false
        ).map(CareTaskSummary::id))
        assertEquals(listOf(3L), TodayTimeline.select(
            listOf(overdue, todayTask, tomorrow), emptyList(), 101, 100, null, false
        ).map(CareTaskSummary::id))
    }

    @Test fun `completion status cannot change chronological position`() {
        val eight = task(1, 100, 480, completed = true)
        val nine = task(2, 100, 540)
        assertEquals(listOf(1L, 2L), TodayTimeline.select(
            listOf(nine), listOf(eight), 100, 100, null, false
        ).map(CareTaskSummary::id))
    }

    private fun task(id: Long, day: Long, minutes: Int, completed: Boolean = false) =
        CareTaskSummary(id, 1, "Task $id", day, minutes, "Max", 0, "General", "Once",
            "", "", null, null, null, id, completed)
}
