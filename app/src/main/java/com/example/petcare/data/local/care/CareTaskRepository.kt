package com.example.petcare.data.local.care

import kotlinx.coroutines.flow.Flow

class CareTaskRepository(private val careTaskDao: CareTaskDao) {

    fun observeUpcoming(): Flow<List<CareTaskSummary>> = careTaskDao.observeUpcoming()

    fun observeCompleted(): Flow<List<CareTaskSummary>> = careTaskDao.observeCompleted()

    suspend fun addTask(
        petId: Long,
        title: String,
        dueDateEpochDay: Long,
        reminderMinutesOfDay: Int
    ): CareTaskEntity {
        val careTask = CareTaskEntity(
            petId = petId,
            title = title,
            dueDateEpochDay = dueDateEpochDay,
            reminderMinutesOfDay = reminderMinutesOfDay
        )
        return careTask.copy(id = careTaskDao.insert(careTask))
    }

    suspend fun markCompleted(careTaskId: Long) {
        careTaskDao.markCompleted(careTaskId)
    }

    suspend fun deleteTask(careTaskId: Long) {
        careTaskDao.deleteById(careTaskId)
    }

    suspend fun getTask(careTaskId: Long): CareTaskEntity? = careTaskDao.getById(careTaskId)

    suspend fun getTaskIdsForPet(petId: Long): List<Long> = careTaskDao.getIdsForPet(petId)

    suspend fun updateTask(careTask: CareTaskEntity) {
        careTaskDao.update(careTask)
    }
}
