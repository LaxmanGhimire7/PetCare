package com.example.petcare

import com.example.petcare.data.local.care.CareTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CareTaskEntityTest {

    @Test
    fun newTaskUsesTheDefaultReminderAndIsIncomplete() {
        val task = CareTaskEntity(
            petId = 7,
            title = "Annual check-up",
            dueDateEpochDay = 20_000
        )

        assertEquals(540, task.reminderMinutesOfDay)
        assertFalse(task.isCompleted)
    }
}
