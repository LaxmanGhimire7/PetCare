package com.example.petcare.data.local.expense

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteById(expenseId: Long)

    @Query("SELECT * FROM expenses WHERE petId = :petId")
    suspend fun getForPet(petId: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    suspend fun getById(expenseId: Long): ExpenseEntity?

    @Query(
        """
        SELECT expenses.id, expenses.petId, expenses.category, expenses.amountCents,
               expenses.dateEpochDay, expenses.note, pets.name AS petName,
               pets.colorIndex AS petColorIndex
        FROM expenses
        INNER JOIN pets ON pets.id = expenses.petId
        ORDER BY expenses.dateEpochDay DESC, expenses.id DESC
        """
    )
    fun observeAll(): Flow<List<ExpenseSummary>>
}
