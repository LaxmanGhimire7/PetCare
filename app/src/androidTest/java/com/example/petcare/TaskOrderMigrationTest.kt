package com.example.petcare

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcare.data.local.PetCareDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that older tasks get stable ordering without changing their care data. */
@RunWith(AndroidJUnit4::class)
class TaskOrderMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), PetCareDatabase::class.java
    )

    @Test fun migrationAssignsRowIdAsInitialOrder() {
        helper.createDatabase("task-order-migration", 10).apply {
            execSQL("""INSERT INTO pets (id, name, species, breed, age, weight,
                dietaryPreferences, vaccinationHistory, allergies, favoriteToys,
                medicalRecords, groomingRoutine, healthNotes, photoUris, colorIndex)
                VALUES (1, 'Luna', 'Cat', '', '', '', '', '', '', '', '', '', '', '', 0)""")
            execSQL("""INSERT INTO care_tasks (id, petId, title, dueDateEpochDay,
                reminderMinutesOfDay, category, frequency, requiredSupplies, notes,
                isCompleted, latitude, longitude, placeId)
                VALUES (7, 1, 'Clinic', 21000, 540, 'Healthcare', 'One time', '', '',
                0, NULL, NULL, NULL)""")
            close()
        }

        helper.runMigrationsAndValidate(
            "task-order-migration", 11, true, PetCareDatabase.MIGRATION_10_11
        ).apply {
            query("SELECT title, sortOrder, generatedFromId FROM care_tasks WHERE id = 7").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Clinic", cursor.getString(0))
                assertEquals(7L, cursor.getLong(1))
                assertEquals(true, cursor.isNull(2))
            }
            close()
        }
    }
}
