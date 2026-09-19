package com.example.petcare

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcare.data.local.PetCareDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Validates both the 8→9 schema and the per-row colour backfill. */
@RunWith(AndroidJUnit4::class)
class PetColorMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PetCareDatabase::class.java
    )

    @Test fun migrationPreservesPetsAndAssignsColours() {
        helper.createDatabase("pet-color-migration", 8).apply {
            execSQL(
                """INSERT INTO pets (id, name, species, breed, age, weight,
                    dietaryPreferences, vaccinationHistory, allergies, favoriteToys,
                    medicalRecords, groomingRoutine, healthNotes, photoUris)
                    VALUES (7, 'Milo', 'Dog', '', '', '', '', '', '', '', '', '', '', '')"""
            )
            close()
        }

        helper.runMigrationsAndValidate(
            "pet-color-migration",
            9,
            true,
            PetCareDatabase.MIGRATION_8_9
        ).apply {
            query("SELECT name, colorIndex FROM pets WHERE id = 7").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Milo", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
            close()
        }
    }
}
