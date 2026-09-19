package com.example.petcare.data.local.care

import kotlinx.coroutines.flow.Flow

class CareTaskRepository(private val careTaskDao: CareTaskDao) {

    fun observeUpcoming(): Flow<List<CareTaskSummary>> = careTaskDao.observeUpcoming()

    fun observeCompleted(): Flow<List<CareTaskSummary>> = careTaskDao.observeCompleted()

    suspend fun addTask(
        petId: Long,
        title: String,
        dueDateEpochDay: Long,
        reminderMinutesOfDay: Int,
        category: String = CARE_CATEGORY_GENERAL,
        frequency: String = CARE_FREQUENCY_ONE_TIME,
        requiredSupplies: String = "",
        notes: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
        placeId: Long? = null
    ): CareTaskEntity {
        val careTask = CareTaskEntity(
            petId = petId,
            title = title,
            dueDateEpochDay = dueDateEpochDay,
            reminderMinutesOfDay = reminderMinutesOfDay,
            category = category,
            frequency = frequency,
            requiredSupplies = requiredSupplies,
            notes = notes,
            latitude = latitude,
            longitude = longitude,
            placeId = placeId
        )
        return careTask.copy(id = careTaskDao.insert(careTask))
    }

    suspend fun completeTask(careTaskId: Long): CareTaskEntity? {
        val task = careTaskDao.getById(careTaskId) ?: return null
        careTaskDao.markCompleted(careTaskId)
        val daysToAdd = when (task.frequency) {
            CARE_FREQUENCY_DAILY -> 1L
            CARE_FREQUENCY_WEEKLY -> 7L
            CARE_FREQUENCY_MONTHLY -> 30L
            else -> return null
        }
        val nextTask = task.copy(
            id = 0,
            dueDateEpochDay = task.dueDateEpochDay + daysToAdd,
            isCompleted = false
        )
        return nextTask.copy(id = careTaskDao.insert(nextTask))
    }

    suspend fun deleteTask(careTaskId: Long): CareTaskEntity? {
        val snapshot = careTaskDao.getById(careTaskId) ?: return null
        careTaskDao.deleteById(careTaskId)
        return snapshot
    }

    suspend fun restoreTask(snapshot: CareTaskEntity) = careTaskDao.insert(snapshot)

    suspend fun getTask(careTaskId: Long): CareTaskEntity? = careTaskDao.getById(careTaskId)

    suspend fun getTaskIdsForPet(petId: Long): List<Long> = careTaskDao.getIdsForPet(petId)

    suspend fun updateTask(careTask: CareTaskEntity) {
        careTaskDao.update(careTask)
    }
}
