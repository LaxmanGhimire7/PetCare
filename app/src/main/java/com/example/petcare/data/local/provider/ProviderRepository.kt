package com.example.petcare.data.local.provider

import kotlinx.coroutines.flow.Flow

/** Account-scoped access to saved care places and their undo snapshots. */
class ProviderRepository(private val dao: ProviderDao, private val ownerId: Long) {
    fun observeAll(): Flow<List<ProviderEntity>> = dao.observeAll(ownerId)
    suspend fun add(provider: ProviderEntity) = dao.insert(provider.copy(ownerId = ownerId))
    suspend fun get(providerId: Long): ProviderEntity? = dao.getById(providerId, ownerId)
    suspend fun update(provider: ProviderEntity) {
        if (provider.ownerId == ownerId && get(provider.id) != null) dao.update(provider)
    }
    suspend fun delete(providerId: Long): ProviderEntity? {
        val snapshot = dao.getById(providerId, ownerId) ?: return null
        dao.deleteById(providerId, ownerId)
        return snapshot
    }
    suspend fun restore(snapshot: ProviderEntity) {
        if (snapshot.ownerId == ownerId) dao.insert(snapshot)
    }
}
