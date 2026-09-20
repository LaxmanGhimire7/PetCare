package com.example.petcare.data.local.pet

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
/** Room queries for pet profiles owned by the current local account. */
interface PetDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(pet: PetEntity): Long

    @Query("SELECT COALESCE(MAX(id), 0) FROM pets WHERE ownerId = :ownerId")
    suspend fun latestId(ownerId: Long): Long

    @Query("SELECT * FROM pets WHERE id = :petId AND ownerId = :ownerId")
    suspend fun getById(petId: Long, ownerId: Long): PetEntity?

    @Query("UPDATE pets SET name = :name, species = :species, colorIndex = :colorIndex, breed = :breed, age = :age, weight = :weight, dietaryPreferences = :dietaryPreferences, vaccinationHistory = :vaccinationHistory, allergies = :allergies, favoriteToys = :favoriteToys, medicalRecords = :medicalRecords, groomingRoutine = :groomingRoutine, healthNotes = :healthNotes, photoUri = :photoUri, photoUris = :photoUris WHERE id = :id AND ownerId = :ownerId")
    suspend fun updateFields(id: Long, ownerId: Long, name: String, species: String, colorIndex: Int, breed: String, age: String, weight: String, dietaryPreferences: String, vaccinationHistory: String, allergies: String, favoriteToys: String, medicalRecords: String, groomingRoutine: String, healthNotes: String, photoUri: String?, photoUris: String)

    @Query("DELETE FROM pets WHERE id = :petId AND ownerId = :ownerId")
    suspend fun deleteById(petId: Long, ownerId: Long)

    @Query("DELETE FROM pets WHERE ownerId = :ownerId")
    suspend fun deleteAllForOwner(ownerId: Long)

    @Query("SELECT * FROM pets WHERE ownerId = :ownerId ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(ownerId: Long): Flow<List<PetEntity>>

    @Query("SELECT * FROM pets WHERE ownerId = :ownerId ORDER BY id")
    suspend fun getAll(ownerId: Long): List<PetEntity>
}
