package com.example.petcare.data.local.expense

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val dao: ExpenseDao) {
    fun observeAll(): Flow<List<ExpenseSummary>> = dao.observeAll()

    suspend fun add(
        petId: Long,
        category: String,
        amountCents: Long,
        dateEpochDay: Long,
        note: String
    ) = dao.insert(ExpenseEntity(
        petId = petId,
        category = category,
        amountCents = amountCents,
        dateEpochDay = dateEpochDay,
        note = note
    ))

    suspend fun delete(expenseId: Long): ExpenseEntity? {
        val snapshot = dao.getById(expenseId) ?: return null
        dao.deleteById(expenseId)
        return snapshot
    }

    suspend fun restore(snapshot: ExpenseEntity) = dao.insert(snapshot)
}
