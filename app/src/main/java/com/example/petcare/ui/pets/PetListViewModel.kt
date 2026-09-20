package com.example.petcare.ui.pets

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.petcare.R
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.pet.DeletedPetSnapshot
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.ui.ScreenState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Owns pet list state and account-scoped delete/undo operations across rotation. */
class PetListViewModel(application: Application) : AndroidViewModel(application) {
    private val repositories = PetCareRepositories(application)
    val state: StateFlow<ScreenState<List<PetEntity>>> = repositories.pets.observePets()
        .map<List<PetEntity>, ScreenState<List<PetEntity>>> { rows ->
            if (rows.isEmpty()) ScreenState.Empty else ScreenState.Content(rows)
        }
        .catch { emit(ScreenState.Error(application.getString(R.string.pets_load_failed))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    suspend fun delete(id: Long): DeletedPetSnapshot? = repositories.petDeletion.delete(id)
    suspend fun restore(snapshot: DeletedPetSnapshot) = repositories.petDeletion.restore(snapshot)
}
