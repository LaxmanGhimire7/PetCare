package com.example.petcare.data.local.care

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
/** Account-scoped Room operations for scheduled and completed care. */
interface CareTaskDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(careTask: CareTaskEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM pets WHERE id = :petId AND ownerId = :ownerId)")
    suspend fun petBelongsToOwner(petId: Long, ownerId: Long): Boolean

    @Query("UPDATE care_tasks SET isCompleted = 1 WHERE id = :careTaskId AND ownerId = :ownerId")
    suspend fun markCompleted(careTaskId: Long, ownerId: Long)

    @Query("UPDATE care_tasks SET isCompleted = :completed WHERE id = :careTaskId AND ownerId = :ownerId")
    suspend fun setCompleted(careTaskId: Long, completed: Boolean, ownerId: Long)

    @Query("SELECT * FROM care_tasks WHERE isCompleted = 1 AND dueDateEpochDay = :day AND ownerId = :ownerId")
    suspend fun getCompletedForDay(day: Long, ownerId: Long): List<CareTaskEntity>

    @Transaction
    suspend fun reopenCompletedForDay(day: Long, ownerId: Long): List<CareTaskEntity> =
        getCompletedForDay(day, ownerId).also { tasks ->
            tasks.forEach { setCompleted(it.id, false, ownerId) }
        }

    @Transaction
    suspend fun restoreCompletedTasks(tasks: List<CareTaskEntity>, ownerId: Long) {
        tasks.forEach { setCompleted(it.id, true, ownerId) }
    }

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM care_tasks WHERE ownerId = :ownerId")
    suspend fun nextSortOrder(ownerId: Long): Long

    @Query("UPDATE care_tasks SET sortOrder = :order WHERE id = :careTaskId AND ownerId = :ownerId")
    suspend fun setSortOrder(careTaskId: Long, order: Long, ownerId: Long)

    @Transaction
    suspend fun setSortOrders(idsInOrder: List<Long>, slotsInOrder: List<Long>, ownerId: Long) {
        idsInOrder.zip(slotsInOrder).forEach { (id, order) -> setSortOrder(id, order, ownerId) }
    }

    @Query("DELETE FROM care_tasks WHERE id = :careTaskId AND ownerId = :ownerId")
    suspend fun deleteById(careTaskId: Long, ownerId: Long)

    @Query("SELECT * FROM care_tasks WHERE id = :careTaskId AND ownerId = :ownerId")
    suspend fun getById(careTaskId: Long, ownerId: Long): CareTaskEntity?

    @Query("SELECT * FROM care_tasks WHERE generatedFromId = :careTaskId AND ownerId = :ownerId LIMIT 1")
    suspend fun getGeneratedSuccessor(careTaskId: Long, ownerId: Long): CareTaskEntity?

    @Query("SELECT id FROM care_tasks WHERE petId = :petId AND ownerId = :ownerId")
    suspend fun getIdsForPet(petId: Long, ownerId: Long): List<Long>

    @Query("SELECT * FROM care_tasks WHERE petId = :petId AND ownerId = :ownerId")
    suspend fun getForPet(petId: Long, ownerId: Long): List<CareTaskEntity>

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
        INNER JOIN pets ON pets.id = care_tasks.petId AND pets.ownerId = care_tasks.ownerId
        WHERE care_tasks.isCompleted = 0 AND care_tasks.ownerId = :ownerId
        ORDER BY care_tasks.sortOrder ASC, care_tasks.dueDateEpochDay ASC, care_tasks.id ASC
        """
    )
    fun observeUpcoming(ownerId: Long): Flow<List<CareTaskSummary>>

    @Query(
        """
        SELECT care_tasks.id, care_tasks.petId, care_tasks.title, care_tasks.dueDateEpochDay,
               care_tasks.reminderMinutesOfDay, care_tasks.category, care_tasks.frequency,
               care_tasks.requiredSupplies, care_tasks.notes, care_tasks.latitude,
               care_tasks.longitude, care_tasks.placeId, care_tasks.sortOrder,
               pets.name AS petName, pets.colorIndex AS petColorIndex
        FROM care_tasks
        INNER JOIN pets ON pets.id = care_tasks.petId AND pets.ownerId = care_tasks.ownerId
        WHERE care_tasks.isCompleted = 1 AND care_tasks.ownerId = :ownerId
        ORDER BY care_tasks.dueDateEpochDay DESC, care_tasks.id DESC
        """
    )
    fun observeCompleted(ownerId: Long): Flow<List<CareTaskSummary>>

    @Query("""SELECT care_tasks.id, care_tasks.title, pets.name AS petName,
        care_tasks.reminderMinutesOfDay FROM care_tasks
        INNER JOIN pets ON pets.id = care_tasks.petId AND pets.ownerId = care_tasks.ownerId
        WHERE care_tasks.ownerId = :ownerId AND care_tasks.isCompleted = 0
        AND care_tasks.dueDateEpochDay <= :today
        ORDER BY care_tasks.dueDateEpochDay, care_tasks.reminderMinutesOfDay LIMIT 3""")
    suspend fun widgetTasks(ownerId: Long, today: Long): List<WidgetTaskRow>
}
