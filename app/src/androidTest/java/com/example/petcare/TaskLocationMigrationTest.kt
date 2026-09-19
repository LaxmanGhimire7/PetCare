package com.example.petcare

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcare.data.local.PetCareDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that the location migration leaves older care tasks usable. */
@RunWith(AndroidJUnit4::class)
class TaskLocationMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PetCareDatabase::class.java
    )

    @Test fun migrationKeepsOldTaskAndAddsNullableLocation() {
        helper.createDatabase("task-location-migration", 9).apply {
            execSQL("""INSERT INTO pets (id, name, species, breed, age, weight,
                dietaryPreferences, vaccinationHistory, allergies, favoriteToys,
                medicalRecords, groomingRoutine, healthNotes, photoUris, colorIndex)
                VALUES (1, 'Luna', 'Cat', '', '', '', '', '', '', '', '', '', '', '', 0)""")
            execSQL("""INSERT INTO care_tasks (id, petId, title, dueDateEpochDay,
                reminderMinutesOfDay, category, frequency, requiredSupplies, notes, isCompleted)
                VALUES (2, 1, 'Clinic', 21000, 540, 'Healthcare', 'One time', '', '', 0)""")
            close()
        }

        helper.runMigrationsAndValidate(
            "task-location-migration", 10, true, PetCareDatabase.MIGRATION_9_10
        ).apply {
            query("SELECT title, latitude, longitude, placeId FROM care_tasks WHERE id = 2")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Clinic", cursor.getString(0))
                    assertTrue(cursor.isNull(1))
                    assertTrue(cursor.isNull(2))
                    assertTrue(cursor.isNull(3))
                }
            close()
        }
    }
}
