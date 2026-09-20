package com.example.petcare.data.local.expense

import kotlinx.coroutines.flow.Flow

/** Validates ownership before expense changes and exposes live account-scoped rows. */
class ExpenseRepository(private val dao: ExpenseDao, private val ownerId: Long) {
    fun observeAll(): Flow<List<ExpenseSummary>> = dao.observeAll(ownerId)

    suspend fun add(
        petId: Long,
        category: String,
        amountCents: Long,
        dateEpochDay: Long,
        note: String
    ): Long {
        require(dao.petBelongsToOwner(petId, ownerId))
        return dao.insert(ExpenseEntity(
        ownerId = ownerId,
        petId = petId,
        category = category,
        amountCents = amountCents,
        dateEpochDay = dateEpochDay,
        note = note
        ))
    }

    suspend fun delete(expenseId: Long): ExpenseEntity? {
        val snapshot = dao.getById(expenseId, ownerId) ?: return null
        dao.deleteById(expenseId, ownerId)
        return snapshot
    }

    suspend fun restore(snapshot: ExpenseEntity) {
        if (snapshot.ownerId == ownerId) dao.insert(snapshot)
    }

    suspend fun get(expenseId: Long): ExpenseEntity? = dao.getById(expenseId, ownerId)

    suspend fun update(expense: ExpenseEntity) {
        if (expense.ownerId == ownerId && get(expense.id) != null &&
            dao.petBelongsToOwner(expense.petId, ownerId)) dao.update(expense)
    }
}
