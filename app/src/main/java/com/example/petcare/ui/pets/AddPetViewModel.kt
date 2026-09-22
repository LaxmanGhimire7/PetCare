package com.example.petcare.ui.pets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.pet.PetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Saves a new profile through the current account's pet repository. */
class AddPetViewModel(private val petRepository: PetRepository) : ViewModel() {

    fun addPet(
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
        selectedColorIndex: Int?,
        onError: (Throwable) -> Unit,
        onSaved: (PetEntity) -> Unit
    ) {
        viewModelScope.launch {
            val saved = try {
                petRepository.addPet(
                    name, species, breed, age, weight, dietaryPreferences,
                    vaccinationHistory, allergies, favoriteToys, medicalRecords,
                    groomingRoutine, healthNotes, photoUris, selectedColorIndex
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onError(error)
                return@launch
            }
            onSaved(saved)
        }
    }
}

/** Supplies the account-scoped pet repository to the add form ViewModel. */
class AddPetViewModelFactory(
    private val petRepository: PetRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddPetViewModel::class.java)) {
            return AddPetViewModel(petRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
