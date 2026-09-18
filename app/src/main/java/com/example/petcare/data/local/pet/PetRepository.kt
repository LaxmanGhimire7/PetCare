package com.example.petcare.data.local.pet

import kotlinx.coroutines.flow.Flow

class PetRepository(private val petDao: PetDao) {

    fun observePets(): Flow<List<PetEntity>> = petDao.observeAll()

    suspend fun addPet(
        name: String,
        species: String,
        healthNotes: String,
        photoUri: String?
    ): PetEntity {
        val pet = PetEntity(name = name, species = species, healthNotes = healthNotes, photoUri = photoUri)
        return pet.copy(id = petDao.insert(pet))
    }

    suspend fun getPet(petId: Long): PetEntity? = petDao.getById(petId)

    suspend fun updatePet(pet: PetEntity) = petDao.update(pet)

    suspend fun deletePet(petId: Long) {
        petDao.deleteById(petId)
    }
}
