package com.example.petcare.data.local.provider

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
/** Room queries for saved clinics, parks, shops, and other care places. */
interface ProviderDao {
    @Insert
    suspend fun insert(provider: ProviderEntity): Long

    @Query("DELETE FROM providers WHERE id = :providerId AND ownerId = :ownerId")
    suspend fun deleteById(providerId: Long, ownerId: Long)

    @Query("DELETE FROM providers WHERE ownerId = :ownerId")
    suspend fun deleteAllForOwner(ownerId: Long)

    @Query("SELECT * FROM providers WHERE id = :providerId AND ownerId = :ownerId")
    suspend fun getById(providerId: Long, ownerId: Long): ProviderEntity?

    @Update
    suspend fun update(provider: ProviderEntity)

    @Query("SELECT * FROM providers WHERE ownerId = :ownerId ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(ownerId: Long): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE ownerId = :ownerId ORDER BY id")
    suspend fun getAll(ownerId: Long): List<ProviderEntity>
}
