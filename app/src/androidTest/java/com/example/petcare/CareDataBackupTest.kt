package com.example.petcare

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.backup.CareDataBackup
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.expense.ExpenseEntity
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.provider.ProviderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File

/** A JSON backup must restore relational links and photo bytes under new row ids. */
@RunWith(AndroidJUnit4::class)
class CareDataBackupTest {
    @Test fun roundTripMergesCareRecordsAndPhotos() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ownerId = 99_999L
        val database = PetCareDatabase.getInstance(context)
        val auth = AuthPreferences(context)
        auth.setSession(ownerId, "Backup test", false)
        val photo = File(context.cacheDir, "backup-test-photo.jpg")
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        photo.writeBytes(bytes)
        try {
            val petId = database.petDao().insert(PetEntity(ownerId = ownerId,
                name = "Luna", species = "Cat", photoUri = Uri.fromFile(photo).toString()))
            val placeId = database.providerDao().insert(ProviderEntity(ownerId = ownerId,
                name = "Clinic", type = "Vet", address = "Kathmandu"))
            database.careTaskDao().insert(CareTaskEntity(ownerId = ownerId, petId = petId,
                title = "Vaccine", dueDateEpochDay = 22_000, placeId = placeId))
            database.expenseDao().insert(ExpenseEntity(ownerId = ownerId, petId = petId,
                category = "Vet", amountCents = 5000, dateEpochDay = 22_000))

            val backup = CareDataBackup(context)
            val json = backup.export()
            assertEquals(4, backup.restore(ByteArrayInputStream(json.toByteArray())))
            val pets = database.petDao().observeAll(ownerId).first()
            val places = database.providerDao().observeAll(ownerId).first()
            assertEquals(2, pets.size)
            assertEquals(2, places.size)
            val importedPet = pets.single { it.id != petId }
            val importedPlace = places.single { it.id != placeId }
            assertArrayEquals(bytes, context.contentResolver.openInputStream(
                Uri.parse(importedPet.photoUri))!!.use { it.readBytes() })
            val importedTask = database.careTaskDao().getForPet(importedPet.id, ownerId).single()
            assertEquals(importedPlace.id, importedTask.placeId)
            assertNotEquals(petId, importedTask.petId)
            assertEquals(5000, database.expenseDao().getForPet(importedPet.id, ownerId)
                .single().amountCents)
        } finally {
            database.openHelper.writableDatabase.execSQL("DELETE FROM pets WHERE ownerId = $ownerId")
            database.openHelper.writableDatabase.execSQL("DELETE FROM providers WHERE ownerId = $ownerId")
            auth.signOut()
            photo.delete()
        }
    }
}
