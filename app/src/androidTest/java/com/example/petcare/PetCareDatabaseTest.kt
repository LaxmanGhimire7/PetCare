package com.example.petcare

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.AccountDataRepository
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.care.CareTaskRepository
import com.example.petcare.data.local.care.CARE_FREQUENCY_DAILY
import com.example.petcare.data.local.expense.ExpenseEntity
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.provider.ProviderEntity
import com.example.petcare.data.local.pet.PetRepository
import com.example.petcare.data.local.expense.ExpenseRepository
import com.example.petcare.data.local.provider.ProviderRepository
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

        val upcoming = database.careTaskDao().observeUpcoming(1).first()
        assertEquals(1, upcoming.size)
        assertEquals("Milo", upcoming.single().petName)
        assertEquals("Vet visit", upcoming.single().title)

        database.careTaskDao().markCompleted(taskId, 1)

        assertTrue(database.careTaskDao().observeUpcoming(1).first().isEmpty())
        assertEquals("Vet visit", database.careTaskDao().observeCompleted(1).first().single().title)
    }

    @Test
    fun completingDailyTaskCreatesNextOccurrence() = runBlocking {
        val petId = database.petDao().insert(PetEntity(name = "Luna", species = "Cat"))
        val repository = CareTaskRepository(database.careTaskDao(), 1)
        val task = repository.addTask(
            petId = petId,
            title = "Clean litter box",
            dueDateEpochDay = 20_000,
            reminderMinutesOfDay = 480,
            frequency = CARE_FREQUENCY_DAILY
        )

        val next = repository.completeTask(task.id)

        assertEquals(20_001L, next?.dueDateEpochDay)
        assertEquals(1, database.careTaskDao().observeUpcoming(1).first().size)
        assertEquals(1, database.careTaskDao().observeCompleted(1).first().size)
    }

    @Test
    fun expenseAndProviderTablesPersistPrototypeData() = runBlocking {
        val petId = database.petDao().insert(PetEntity(name = "Max", species = "Dog"))
        database.expenseDao().insert(ExpenseEntity(petId = petId, category = "Food", amountCents = 2599, dateEpochDay = 20_000))
        database.providerDao().insert(ProviderEntity(name = "Happy Paws", type = "Veterinary clinic", address = "1 Pet Street"))

        assertEquals(2599, database.expenseDao().observeAll(1).first().single().amountCents)
        assertEquals("Happy Paws", database.providerDao().observeAll(1).first().single().name)
    }

    @Test
    fun taskLocationSurvivesSavingAndReopening() = runBlocking {
        val petId = database.petDao().insert(PetEntity(name = "Luna", species = "Cat"))
        val placeId = database.providerDao().insert(ProviderEntity(
            name = "Clinic", type = "Veterinary clinic", address = "Kathmandu",
            latitude = 27.7172, longitude = 85.3240
        ))
        val repository = CareTaskRepository(database.careTaskDao(), 1)
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

    @Test
    fun resetUndoAndRepeatCompletionReuseTheSameSuccessor() = runBlocking {
        val petId = database.petDao().insert(PetEntity(name = "Luna", species = "Cat"))
        val repository = CareTaskRepository(database.careTaskDao(), 1)
        val task = repository.addTask(petId, "Feed Luna", 21_000, 480,
            frequency = CARE_FREQUENCY_DAILY)
        val next = repository.completeTask(task.id)!!

        val snapshots = repository.resetCompletedForDay(21_000)
        assertEquals(listOf(task.id), snapshots.map { it.id })
        assertEquals(2, repository.observeUpcoming().first().size)
        repository.restoreCompleted(snapshots)
        assertEquals(1, repository.observeCompleted().first().size)

        repository.resetCompletedForDay(21_000)
        assertEquals(next.id, repository.completeTask(task.id)?.id)
        assertEquals(1, repository.observeUpcoming().first().size)
    }

    @Test
    fun draggedTaskOrderPersistsInUpcomingQuery() = runBlocking {
        val petId = database.petDao().insert(PetEntity(name = "Max", species = "Dog"))
        val repository = CareTaskRepository(database.careTaskDao(), 1)
        val first = repository.addTask(petId, "Breakfast", 21_000, 480)
        val second = repository.addTask(petId, "Walk", 21_000, 600)
        val third = repository.addTask(petId, "Dinner", 21_000, 1080)
        repository.saveOrder(listOf(third.id, first.id, second.id),
            listOf(first.sortOrder, second.sortOrder, third.sortOrder))

        assertEquals(listOf("Dinner", "Breakfast", "Walk"),
            repository.observeUpcoming().first().map { it.title })
    }

    @Test
    fun accountQueriesAndMutationsDoNotCrossOwners() = runBlocking {
        val firstPet = database.petDao().insert(PetEntity(name = "Luna", species = "Cat", ownerId = 1))
        val secondPet = database.petDao().insert(PetEntity(name = "Max", species = "Dog", ownerId = 2))
        val firstTasks = CareTaskRepository(database.careTaskDao(), 1)
        val secondTasks = CareTaskRepository(database.careTaskDao(), 2)
        val firstTask = firstTasks.addTask(firstPet, "Feed Luna", 21_000, 480)
        val secondTask = secondTasks.addTask(secondPet, "Walk Max", 21_000, 600)
        val firstExpenses = ExpenseRepository(database.expenseDao(), 1)
        val secondExpenses = ExpenseRepository(database.expenseDao(), 2)
        firstExpenses.add(firstPet, "Food", 1000, 21_000, "")
        secondExpenses.add(secondPet, "Food", 2000, 21_000, "")
        val firstPlaces = ProviderRepository(database.providerDao(), 1)
        val secondPlaces = ProviderRepository(database.providerDao(), 2)
        firstPlaces.add(ProviderEntity(name = "Clinic A", type = "Clinic", address = "A"))
        secondPlaces.add(ProviderEntity(name = "Clinic B", type = "Clinic", address = "B"))

        assertEquals(listOf("Luna"), PetRepository(database.petDao(), 1).observePets().first().map { it.name })
        assertEquals(listOf("Max"), PetRepository(database.petDao(), 2).observePets().first().map { it.name })
        assertEquals(listOf("Feed Luna"), firstTasks.observeUpcoming().first().map { it.title })
        assertEquals(listOf("Walk Max"), secondTasks.observeUpcoming().first().map { it.title })
        assertEquals(1000, firstExpenses.observeAll().first().single().amountCents)
        assertEquals(2000, secondExpenses.observeAll().first().single().amountCents)
        assertEquals("Clinic A", firstPlaces.observeAll().first().single().name)
        assertEquals("Clinic B", secondPlaces.observeAll().first().single().name)
        assertEquals(null, firstTasks.getTask(secondTask.id))
        assertEquals(null, firstTasks.deleteTask(secondTask.id))
        assertEquals(null, firstTasks.completeTask(secondTask.id))
        assertEquals(firstTask.id, firstTasks.getTask(firstTask.id)?.id)
        assertTrue(runCatching { firstTasks.addTask(secondPet, "Wrong pet", 21_000, 480) }.isFailure)
        assertTrue(runCatching { firstExpenses.add(secondPet, "Food", 100, 21_000, "") }.isFailure)
    }

    @Test
    fun clearAndUndoRestoreOnlyTheActiveAccountsCare() = runBlocking {
        val firstPet = database.petDao().insert(PetEntity(ownerId = 1, name = "Luna", species = "Cat"))
        database.petDao().insert(PetEntity(ownerId = 2, name = "Max", species = "Dog"))
        database.careTaskDao().insert(CareTaskEntity(ownerId = 1, petId = firstPet,
            title = "Vaccine", dueDateEpochDay = 21_000))
        database.expenseDao().insert(ExpenseEntity(ownerId = 1, petId = firstPet,
            category = "Vet", amountCents = 1000, dateEpochDay = 21_000))
        database.providerDao().insert(ProviderEntity(ownerId = 1,
            name = "Clinic", type = "Vet", address = "A"))
        val repo = AccountDataRepository(database, 1)
        val snapshot = repo.clear()
        assertTrue(database.petDao().getAll(1).isEmpty())
        assertEquals(1, database.petDao().getAll(2).size)
        assertTrue(database.providerDao().getAll(1).isEmpty())
        repo.restore(snapshot)
        assertEquals(1, database.petDao().getAll(1).size)
        assertEquals(1, database.careTaskDao().getForPet(firstPet, 1).size)
        assertEquals(1, database.expenseDao().getForPet(firstPet, 1).size)
        assertEquals(1, database.providerDao().getAll(1).size)
    }
}
