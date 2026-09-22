package com.example.petcare.ui.home

import com.example.petcare.data.local.care.CareTaskSummary

/** Pure selection and ordering rules for the Today timeline. */
object TodayTimeline {
    /**
     * Returns tasks for the selected date. Earlier open tasks are included only when today is
     * selected. Status never participates in sorting, which keeps a completed row in its slot.
     */
    fun select(
        upcoming: List<CareTaskSummary>,
        completed: List<CareTaskSummary>,
        selectedDay: Long,
        today: Long,
        petId: Long?,
        hideCompleted: Boolean
    ): List<CareTaskSummary> {
        val all = (upcoming + completed).distinctBy(CareTaskSummary::id)
        val selected = all.filter { task ->
            task.dueDateEpochDay == selectedDay ||
                (selectedDay == today && !task.isCompleted && task.dueDateEpochDay < today)
        }.filter { petId == null || it.petId == petId }
            .filter { !hideCompleted || !it.isCompleted }
        return selected.sortedWith(compareBy(CareTaskSummary::dueDateEpochDay)
            .thenBy(CareTaskSummary::reminderMinutesOfDay)
            .thenBy(CareTaskSummary::sortOrder)
            .thenBy(CareTaskSummary::id))
    }
}
