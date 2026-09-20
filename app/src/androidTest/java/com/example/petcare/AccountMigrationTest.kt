package com.example.petcare

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcare.data.local.PetCareDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Legacy records must remain attached to the first local account after migration. */
@RunWith(AndroidJUnit4::class)
class AccountMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), PetCareDatabase::class.java
    )

    @Test fun migrationPreservesLegacyDataAndCreatesAccountSchema() {
        helper.createDatabase("account-migration", 11).apply {
            execSQL("""INSERT INTO pets (id, name, species, breed, age, weight,
                dietaryPreferences, vaccinationHistory, allergies, favoriteToys,
                medicalRecords, groomingRoutine, healthNotes, photoUris, colorIndex)
                VALUES (1, 'Luna', 'Cat', '', '', '', '', '', '', '', '', '', '', '', 0)""")
            execSQL("""INSERT INTO care_tasks (id, petId, title, dueDateEpochDay,
                reminderMinutesOfDay, category, frequency, requiredSupplies, notes,
                isCompleted, latitude, longitude, placeId, sortOrder, generatedFromId)
                VALUES (3, 1, 'Clinic', 21000, 540, 'Healthcare', 'One time', '', '',
                0, NULL, NULL, NULL, 3, NULL)""")
            close()
        }
        helper.runMigrationsAndValidate(
            "account-migration", 12, true, PetCareDatabase.MIGRATION_11_12
        ).apply {
            query("SELECT ownerId FROM pets WHERE id = 1").use {
                it.moveToFirst(); assertEquals(1L, it.getLong(0))
            }
            query("SELECT ownerId FROM care_tasks WHERE id = 3").use {
                it.moveToFirst(); assertEquals(1L, it.getLong(0))
            }
            close()
        }
    }
}
