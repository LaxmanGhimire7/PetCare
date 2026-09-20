package com.example.petcare.ui.providers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.petcare.R
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.provider.ProviderEntity
import com.example.petcare.ui.ScreenState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Keeps saved places live while maps and location remain optional UI concerns. */
class ProviderListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PetCareRepositories(application).places
    val state: StateFlow<ScreenState<List<ProviderEntity>>> = repository.observeAll()
        .map<List<ProviderEntity>, ScreenState<List<ProviderEntity>>> { rows ->
            if (rows.isEmpty()) ScreenState.Empty else ScreenState.Content(rows)
        }
        .catch { emit(ScreenState.Error(application.getString(R.string.places_load_failed))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    suspend fun delete(id: Long): ProviderEntity? = repository.delete(id)
    suspend fun restore(snapshot: ProviderEntity) = repository.restore(snapshot)
}
