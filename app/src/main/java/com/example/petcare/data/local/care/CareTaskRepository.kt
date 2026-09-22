package com.example.petcare.data.local.care

import kotlinx.coroutines.flow.Flow

/** Single account-scoped entry point for care task reads and mutations. */
class CareTaskRepository(private val careTaskDao: CareTaskDao, private val ownerId: Long) {

    fun observeUpcoming(): Flow<List<CareTaskSummary>> = careTaskDao.observeUpcoming(ownerId)

    fun observeCompleted(): Flow<List<CareTaskSummary>> = careTaskDao.observeCompleted(ownerId)

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
        require(careTaskDao.petBelongsToOwner(petId, ownerId))
        val careTask = CareTaskEntity(
            ownerId = ownerId,
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
            placeId = placeId,
            sortOrder = careTaskDao.nextSortOrder(ownerId)
        )
        return careTask.copy(id = careTaskDao.insert(careTask))
    }

    suspend fun completeTask(careTaskId: Long): CareTaskEntity? {
        val task = careTaskDao.getById(careTaskId, ownerId) ?: return null
        careTaskDao.markCompleted(careTaskId, ownerId)
        // Resetting and completing again must reuse the existing recurring successor.
        careTaskDao.getGeneratedSuccessor(careTaskId, ownerId)?.let { return it }
        val nextDate = CareRecurrence.nextDate(task.dueDateEpochDay, task.frequency)
            ?: return null
        val nextTask = task.copy(
            id = 0,
            dueDateEpochDay = nextDate,
            isCompleted = false,
            sortOrder = careTaskDao.nextSortOrder(ownerId),
            generatedFromId = task.id
        )
        return nextTask.copy(id = careTaskDao.insert(nextTask))
    }

    /** Reopens one completed task for completion Undo without changing its timeline slot. */
    suspend fun reopenTask(careTaskId: Long): CareTaskEntity? {
        val task = careTaskDao.getById(careTaskId, ownerId) ?: return null
        careTaskDao.reopen(careTaskId, ownerId)
        return task.copy(isCompleted = false)
    }

    /** Moves an open task forward while carrying minutes past midnight onto the next day. */
    suspend fun snoozeTask(careTaskId: Long, delayMinutes: Int): CareTaskEntity? {
        val task = careTaskDao.getById(careTaskId, ownerId) ?: return null
        val total = task.reminderMinutesOfDay + delayMinutes
        val moved = task.copy(
            dueDateEpochDay = task.dueDateEpochDay + total.floorDiv(MINUTES_PER_DAY),
            reminderMinutesOfDay = Math.floorMod(total, MINUTES_PER_DAY)
        )
        careTaskDao.moveDueTime(moved.id, moved.dueDateEpochDay, moved.reminderMinutesOfDay, ownerId)
        return moved
    }

    suspend fun deleteTask(careTaskId: Long): CareTaskEntity? {
        val snapshot = careTaskDao.getById(careTaskId, ownerId) ?: return null
        careTaskDao.deleteById(careTaskId, ownerId)
        return snapshot
    }

    suspend fun restoreTask(snapshot: CareTaskEntity) {
        if (snapshot.ownerId == ownerId) careTaskDao.insert(snapshot)
    }

    suspend fun getTask(careTaskId: Long): CareTaskEntity? = careTaskDao.getById(careTaskId, ownerId)

    suspend fun getTaskIdsForPet(petId: Long): List<Long> = careTaskDao.getIdsForPet(petId, ownerId)

    suspend fun updateTask(careTask: CareTaskEntity) {
        if (careTask.ownerId == ownerId && getTask(careTask.id) != null &&
            careTaskDao.petBelongsToOwner(careTask.petId, ownerId)) careTaskDao.update(careTask)
    }

    /** Reopens only tasks completed on the chosen local day; snapshots support Undo. */
    suspend fun resetCompletedForDay(day: Long): List<CareTaskEntity> {
        return careTaskDao.reopenCompletedForDay(day, ownerId)
    }

    suspend fun restoreCompleted(snapshots: List<CareTaskEntity>) {
        careTaskDao.restoreCompletedTasks(snapshots, ownerId)
    }

    /** Assign the original visible slots to the dragged row order. */
    suspend fun saveOrder(idsInOrder: List<Long>, slotsInOrder: List<Long>) {
        careTaskDao.setSortOrders(idsInOrder, slotsInOrder, ownerId)
    }

    private companion object { const val MINUTES_PER_DAY = 24 * 60 }
}
