package com.example.petcare.ui.pets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.data.local.pet.PetRepository
import kotlinx.coroutines.launch

class AddPetViewModel(private val petRepository: PetRepository) : ViewModel() {

    fun addPet(
        name: String,
        species: String,
        healthNotes: String,
        photoUri: String?,
        onSaved: (PetEntity) -> Unit
    ) {
        viewModelScope.launch {
            onSaved(petRepository.addPet(name, species, healthNotes, photoUri))
        }
    }
}

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
