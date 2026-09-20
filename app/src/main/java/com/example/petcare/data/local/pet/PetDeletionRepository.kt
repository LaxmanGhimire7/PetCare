package com.example.petcare.data.local.pet

import androidx.room.withTransaction
import com.example.petcare.data.local.PetCareDatabase
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.expense.ExpenseEntity

/** Full local snapshot needed to undo a pet deletion with its cascading child rows. */
data class DeletedPetSnapshot(
    val pet: PetEntity,
    val tasks: List<CareTaskEntity>,
    val expenses: List<ExpenseEntity>
)

/** Keeps delete and undo atomic so no care or expense records are silently lost. */
class PetDeletionRepository(private val database: PetCareDatabase, private val ownerId: Long) {
    suspend fun delete(petId: Long): DeletedPetSnapshot? = database.withTransaction {
        val pet = database.petDao().getById(petId, ownerId) ?: return@withTransaction null
        val tasks = database.careTaskDao().getForPet(petId, ownerId)
        val expenses = database.expenseDao().getForPet(petId, ownerId)
        database.petDao().deleteById(petId, ownerId)
        DeletedPetSnapshot(pet, tasks, expenses)
    }

    suspend fun restore(snapshot: DeletedPetSnapshot) = database.withTransaction {
        if (snapshot.pet.ownerId != ownerId) return@withTransaction
        database.petDao().insert(snapshot.pet)
        snapshot.tasks.forEach { database.careTaskDao().insert(it) }
        snapshot.expenses.forEach { database.expenseDao().insert(it) }
    }
}
