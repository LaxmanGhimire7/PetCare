package com.example.petcare.data.local.pet

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PetDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(pet: PetEntity): Long

    @Query("SELECT * FROM pets WHERE id = :petId")
    suspend fun getById(petId: Long): PetEntity?

    @Update
    suspend fun update(pet: PetEntity)

    @Query("DELETE FROM pets WHERE id = :petId")
    suspend fun deleteById(petId: Long)

    @Query("SELECT * FROM pets ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PetEntity>>
}
