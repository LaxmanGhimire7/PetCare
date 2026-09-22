package com.example.petcare.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.care.CareTaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Persists a validated new care task through its account-scoped repository. */
class AddCareTaskViewModel(private val careTaskRepository: CareTaskRepository) : ViewModel() {

    fun addTask(
        petId: Long,
        title: String,
        dueDateEpochDay: Long,
        reminderMinutesOfDay: Int,
        category: String,
        frequency: String,
        requiredSupplies: String,
        notes: String,
        latitude: Double?,
        longitude: Double?,
        placeId: Long?,
        onError: (Throwable) -> Unit,
        onSaved: (CareTaskEntity) -> Unit
    ) {
        viewModelScope.launch {
            val saved = try {
                careTaskRepository.addTask(
                    petId,
                    title,
                    dueDateEpochDay,
                    reminderMinutesOfDay,
                    category,
                    frequency,
                    requiredSupplies,
                    notes,
                    latitude,
                    longitude,
                    placeId
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

/** Supplies the signed-in account's task repository to the add form ViewModel. */
class AddCareTaskViewModelFactory(
    private val careTaskRepository: CareTaskRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddCareTaskViewModel::class.java)) {
            return AddCareTaskViewModel(careTaskRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
