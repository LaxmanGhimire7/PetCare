package com.example.petcare.data.local.provider

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Insert
    suspend fun insert(provider: ProviderEntity): Long

    @Query("DELETE FROM providers WHERE id = :providerId")
    suspend fun deleteById(providerId: Long)

    @Query("SELECT * FROM providers WHERE id = :providerId")
    suspend fun getById(providerId: Long): ProviderEntity?

    @Update
    suspend fun update(provider: ProviderEntity)

    @Query("SELECT * FROM providers ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ProviderEntity>>
}
