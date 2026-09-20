package com.example.petcare.data.local.pet

import kotlinx.coroutines.flow.Flow

/** Creates and edits only pets that belong to the signed-in account. */
class PetRepository(private val petDao: PetDao, private val ownerId: Long) {

    fun observePets(): Flow<List<PetEntity>> = petDao.observeAll(ownerId)

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
            ownerId = ownerId,
            name = name,
            species = species,
            colorIndex = selectedColorIndex ?: ColorAssignment.forNewPet(petDao.latestId(ownerId)),
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

    suspend fun getPet(petId: Long): PetEntity? = petDao.getById(petId, ownerId)

    suspend fun updatePet(pet: PetEntity) {
        if (getPet(pet.id) == null) return
        petDao.updateFields(pet.id, ownerId, pet.name, pet.species, pet.colorIndex, pet.breed,
            pet.age, pet.weight, pet.dietaryPreferences, pet.vaccinationHistory, pet.allergies,
            pet.favoriteToys, pet.medicalRecords, pet.groomingRoutine, pet.healthNotes,
            pet.photoUri, pet.photoUris)
    }

    suspend fun deletePet(petId: Long) {
        petDao.deleteById(petId, ownerId)
    }
}
