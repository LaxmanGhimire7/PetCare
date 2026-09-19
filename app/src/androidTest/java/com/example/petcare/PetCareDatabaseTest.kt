package com.example.petcare

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.care.CareTaskRepository
import com.example.petcare.data.local.care.CARE_FREQUENCY_DAILY
import com.example.petcare.data.local.expense.ExpenseEntity
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.provider.ProviderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PetCareDatabaseTest {

    private lateinit var database: PetCareDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PetCareDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun careTaskAppearsForItsPetAndMovesToCompletedHistory() = runBlocking {
        val petId = database.petDao().insert(
            PetEntity(name = "Milo", species = "Dog", healthNotes = "Allergy to chicken")
        )
        val taskId = database.careTaskDao().insert(
            CareTaskEntity(
                petId = petId,
                title = "Vet visit",
                dueDateEpochDay = 20_000,
                reminderMinutesOfDay = 600
            )
        )

        val upcoming = database.careTaskDao().observeUpcoming().first()
        assertEquals(1, upcoming.size)
        assertEquals("Milo", upcoming.single().petName)
        assertEquals("Vet visit", upcoming.single().title)

        database.careTaskDao().markCompleted(taskId)

        assertTrue(database.careTaskDao().observeUpcoming().first().isEmpty())
        assertEquals("Vet visit", database.careTaskDao().observeCompleted().first().single().title)
    }

    @Test
    fun completingDailyTaskCreatesNextOccurrence() = runBlocking {
        val petId = database.petDao().insert(PetEntity(name = "Luna", species = "Cat"))
        val repository = CareTaskRepository(database.careTaskDao())
        val task = repository.addTask(
            petId = petId,
            title = "Clean litter box",
            dueDateEpochDay = 20_000,
            reminderMinutesOfDay = 480,
            frequency = CARE_FREQUENCY_DAILY
        )

        val next = repository.completeTask(task.id)

        assertEquals(20_001L, next?.dueDateEpochDay)
        assertEquals(1, database.careTaskDao().observeUpcoming().first().size)
        assertEquals(1, database.careTaskDao().observeCompleted().first().size)
    }

    @Test
    fun expenseAndProviderTablesPersistPrototypeData() = runBlocking {
        val petId = database.petDao().insert(PetEntity(name = "Max", species = "Dog"))
        database.expenseDao().insert(ExpenseEntity(petId = petId, category = "Food", amountCents = 2599, dateEpochDay = 20_000))
        database.providerDao().insert(ProviderEntity(name = "Happy Paws", type = "Veterinary clinic", address = "1 Pet Street"))

        assertEquals(2599, database.expenseDao().observeAll().first().single().amountCents)
        assertEquals("Happy Paws", database.providerDao().observeAll().first().single().name)
    }

    @Test
    fun taskLocationSurvivesSavingAndReopening() = runBlocking {
        val petId = database.petDao().insert(PetEntity(name = "Luna", species = "Cat"))
        val placeId = database.providerDao().insert(ProviderEntity(
            name = "Clinic", type = "Veterinary clinic", address = "Kathmandu",
            latitude = 27.7172, longitude = 85.3240
        ))
        val repository = CareTaskRepository(database.careTaskDao())
        val task = repository.addTask(
            petId = petId, title = "Vaccination", dueDateEpochDay = 21_000,
            reminderMinutesOfDay = 540, latitude = 27.7172, longitude = 85.3240,
            placeId = placeId
        )

        val reopened = repository.getTask(task.id)
        assertEquals(placeId, reopened?.placeId)
        assertEquals(27.7172, reopened?.latitude ?: 0.0, 0.00001)
        assertEquals(85.3240, reopened?.longitude ?: 0.0, 0.00001)
        assertEquals(placeId, repository.observeUpcoming().first().single().placeId)
    }
}
