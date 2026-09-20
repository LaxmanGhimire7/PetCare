package com.example.petcare.data.local

import androidx.room.withTransaction
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.expense.ExpenseEntity
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.provider.ProviderEntity

/** One-account care snapshot used to undo a Settings clear operation. */
data class AccountDataSnapshot(val pets: List<PetEntity>, val tasks: List<CareTaskEntity>,
    val expenses: List<ExpenseEntity>, val places: List<ProviderEntity>)

/** Clears care data atomically while leaving the account and credentials intact. */
class AccountDataRepository(private val database: PetCareDatabase, private val ownerId: Long) {
    suspend fun clear(): AccountDataSnapshot = database.withTransaction {
        val pets = database.petDao().getAll(ownerId)
        val tasks = pets.flatMap { database.careTaskDao().getForPet(it.id, ownerId) }
        val expenses = pets.flatMap { database.expenseDao().getForPet(it.id, ownerId) }
        val places = database.providerDao().getAll(ownerId)
        database.petDao().deleteAllForOwner(ownerId)
        database.providerDao().deleteAllForOwner(ownerId)
        AccountDataSnapshot(pets, tasks, expenses, places)
    }

    suspend fun restore(snapshot: AccountDataSnapshot) = database.withTransaction {
        require(snapshot.pets.all { it.ownerId == ownerId } &&
            snapshot.tasks.all { it.ownerId == ownerId } &&
            snapshot.expenses.all { it.ownerId == ownerId } &&
            snapshot.places.all { it.ownerId == ownerId })
        snapshot.pets.forEach { database.petDao().insert(it) }
        snapshot.places.forEach { database.providerDao().insert(it) }
        snapshot.tasks.forEach { database.careTaskDao().insert(it) }
        snapshot.expenses.forEach { database.expenseDao().insert(it) }
    }
}
