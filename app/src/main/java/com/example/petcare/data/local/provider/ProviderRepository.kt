package com.example.petcare.data.local.provider

import kotlinx.coroutines.flow.Flow

class ProviderRepository(private val dao: ProviderDao) {
    fun observeAll(): Flow<List<ProviderEntity>> = dao.observeAll()
    suspend fun add(provider: ProviderEntity) = dao.insert(provider)
    suspend fun get(providerId: Long): ProviderEntity? = dao.getById(providerId)
    suspend fun update(provider: ProviderEntity) = dao.update(provider)
    suspend fun delete(providerId: Long): ProviderEntity? {
        val snapshot = dao.getById(providerId) ?: return null
        dao.deleteById(providerId)
        return snapshot
    }
    suspend fun restore(snapshot: ProviderEntity) = dao.insert(snapshot)
}
