package com.example.petcare.data.local.care

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CareTaskDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(careTask: CareTaskEntity): Long

    @Query("UPDATE care_tasks SET isCompleted = 1 WHERE id = :careTaskId")
    suspend fun markCompleted(careTaskId: Long)

    @Query("UPDATE care_tasks SET isCompleted = :completed WHERE id = :careTaskId")
    suspend fun setCompleted(careTaskId: Long, completed: Boolean)

    @Query("SELECT * FROM care_tasks WHERE isCompleted = 1 AND dueDateEpochDay = :day")
    suspend fun getCompletedForDay(day: Long): List<CareTaskEntity>

    @Transaction
    suspend fun reopenCompletedForDay(day: Long): List<CareTaskEntity> =
        getCompletedForDay(day).also { tasks ->
            tasks.forEach { setCompleted(it.id, false) }
        }

    @Transaction
    suspend fun restoreCompletedTasks(tasks: List<CareTaskEntity>) {
        tasks.forEach { setCompleted(it.id, true) }
    }

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM care_tasks")
    suspend fun nextSortOrder(): Long

    @Query("UPDATE care_tasks SET sortOrder = :order WHERE id = :careTaskId")
    suspend fun setSortOrder(careTaskId: Long, order: Long)

    @Transaction
    suspend fun setSortOrders(idsInOrder: List<Long>, slotsInOrder: List<Long>) {
        idsInOrder.zip(slotsInOrder).forEach { (id, order) -> setSortOrder(id, order) }
    }

    @Query("DELETE FROM care_tasks WHERE id = :careTaskId")
    suspend fun deleteById(careTaskId: Long)

    @Query("SELECT * FROM care_tasks WHERE id = :careTaskId")
    suspend fun getById(careTaskId: Long): CareTaskEntity?

    @Query("SELECT * FROM care_tasks WHERE generatedFromId = :careTaskId LIMIT 1")
    suspend fun getGeneratedSuccessor(careTaskId: Long): CareTaskEntity?

    @Query("SELECT id FROM care_tasks WHERE petId = :petId")
    suspend fun getIdsForPet(petId: Long): List<Long>

    @Query("SELECT * FROM care_tasks WHERE petId = :petId")
    suspend fun getForPet(petId: Long): List<CareTaskEntity>

    @Update
    suspend fun update(careTask: CareTaskEntity)

    @Query(
        """
        SELECT care_tasks.id, care_tasks.petId, care_tasks.title, care_tasks.dueDateEpochDay,
               care_tasks.reminderMinutesOfDay, care_tasks.category, care_tasks.frequency,
               care_tasks.requiredSupplies, care_tasks.notes, care_tasks.latitude,
               care_tasks.longitude, care_tasks.placeId, care_tasks.sortOrder,
               pets.name AS petName, pets.colorIndex AS petColorIndex
        FROM care_tasks
        INNER JOIN pets ON pets.id = care_tasks.petId
        WHERE care_tasks.isCompleted = 0
        ORDER BY care_tasks.sortOrder ASC, care_tasks.dueDateEpochDay ASC, care_tasks.id ASC
        """
    )
    fun observeUpcoming(): Flow<List<CareTaskSummary>>

    @Query(
        """
        SELECT care_tasks.id, care_tasks.petId, care_tasks.title, care_tasks.dueDateEpochDay,
               care_tasks.reminderMinutesOfDay, care_tasks.category, care_tasks.frequency,
               care_tasks.requiredSupplies, care_tasks.notes, care_tasks.latitude,
               care_tasks.longitude, care_tasks.placeId, care_tasks.sortOrder,
               pets.name AS petName, pets.colorIndex AS petColorIndex
        FROM care_tasks
        INNER JOIN pets ON pets.id = care_tasks.petId
        WHERE care_tasks.isCompleted = 1
        ORDER BY care_tasks.dueDateEpochDay DESC, care_tasks.id DESC
        """
    )
    fun observeCompleted(): Flow<List<CareTaskSummary>>
}
