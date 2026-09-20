package com.example.petcare.data.local.expense

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
/** Room queries for a signed-in account's expense records. */
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM pets WHERE id = :petId AND ownerId = :ownerId)")
    suspend fun petBelongsToOwner(petId: Long, ownerId: Long): Boolean

    @Query("DELETE FROM expenses WHERE id = :expenseId AND ownerId = :ownerId")
    suspend fun deleteById(expenseId: Long, ownerId: Long)

    @Query("SELECT * FROM expenses WHERE petId = :petId AND ownerId = :ownerId")
    suspend fun getForPet(petId: Long, ownerId: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :expenseId AND ownerId = :ownerId")
    suspend fun getById(expenseId: Long, ownerId: Long): ExpenseEntity?

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query(
        """
        SELECT expenses.id, expenses.petId, expenses.category, expenses.amountCents,
               expenses.dateEpochDay, expenses.note, pets.name AS petName,
               pets.colorIndex AS petColorIndex
        FROM expenses
        INNER JOIN pets ON pets.id = expenses.petId AND pets.ownerId = expenses.ownerId
        WHERE expenses.ownerId = :ownerId
        ORDER BY expenses.dateEpochDay DESC, expenses.id DESC
        """
    )
    fun observeAll(ownerId: Long): Flow<List<ExpenseSummary>>
}
