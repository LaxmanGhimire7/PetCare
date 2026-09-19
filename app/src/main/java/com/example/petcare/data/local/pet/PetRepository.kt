package com.example.petcare.data.local.pet

import kotlinx.coroutines.flow.Flow

class PetRepository(private val petDao: PetDao) {

    fun observePets(): Flow<List<PetEntity>> = petDao.observeAll()

    suspend fun addPet(
        name: String,
        species: String,
        breed: String,
        age: String,
        weight: String,
        dietaryPreferences: String,
        vaccinationHistory: String,
        allergies: String,
        favoriteToys: String,
        medicalRecords: String,
        groomingRoutine: String,
        healthNotes: String,
        photoUris: List<String>,
        selectedColorIndex: Int? = null
    ): PetEntity {
        val pet = PetEntity(
            name = name,
            species = species,
            colorIndex = selectedColorIndex ?: ColorAssignment.forNewPet(petDao.latestId()),
            breed = breed,
            age = age,
            weight = weight,
            dietaryPreferences = dietaryPreferences,
            vaccinationHistory = vaccinationHistory,
            allergies = allergies,
            favoriteToys = favoriteToys,
            medicalRecords = medicalRecords,
            groomingRoutine = groomingRoutine,
            healthNotes = healthNotes,
            photoUri = photoUris.firstOrNull(),
            photoUris = photoUris.joinToString(PetEntity.PHOTO_SEPARATOR)
        )
        return pet.copy(id = petDao.insert(pet))
    }

    suspend fun getPet(petId: Long): PetEntity? = petDao.getById(petId)

    suspend fun updatePet(pet: PetEntity) = petDao.update(pet)

    suspend fun deletePet(petId: Long) {
        petDao.deleteById(petId)
    }
}
