package com.example.petcare.data.local.care

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CareTaskDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(careTask: CareTaskEntity): Long

    @Query("UPDATE care_tasks SET isCompleted = 1 WHERE id = :careTaskId")
    suspend fun markCompleted(careTaskId: Long)

    @Query("DELETE FROM care_tasks WHERE id = :careTaskId")
    suspend fun deleteById(careTaskId: Long)

    @Query("SELECT * FROM care_tasks WHERE id = :careTaskId")
    suspend fun getById(careTaskId: Long): CareTaskEntity?

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
               care_tasks.longitude, care_tasks.placeId,
               pets.name AS petName, pets.colorIndex AS petColorIndex
        FROM care_tasks
        INNER JOIN pets ON pets.id = care_tasks.petId
        WHERE care_tasks.isCompleted = 0
        ORDER BY care_tasks.dueDateEpochDay ASC, care_tasks.id ASC
        """
    )
    fun observeUpcoming(): Flow<List<CareTaskSummary>>

    @Query(
        """
        SELECT care_tasks.id, care_tasks.petId, care_tasks.title, care_tasks.dueDateEpochDay,
               care_tasks.reminderMinutesOfDay, care_tasks.category, care_tasks.frequency,
               care_tasks.requiredSupplies, care_tasks.notes, care_tasks.latitude,
               care_tasks.longitude, care_tasks.placeId,
               pets.name AS petName, pets.colorIndex AS petColorIndex
        FROM care_tasks
        INNER JOIN pets ON pets.id = care_tasks.petId
        WHERE care_tasks.isCompleted = 1
        ORDER BY care_tasks.dueDateEpochDay DESC, care_tasks.id DESC
        """
    )
    fun observeCompleted(): Flow<List<CareTaskSummary>>
}
