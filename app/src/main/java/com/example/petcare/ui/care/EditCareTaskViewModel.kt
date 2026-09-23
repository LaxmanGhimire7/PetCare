package com.example.petcare.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.care.CareTaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Loads and updates an existing account-scoped care task. */
class EditCareTaskViewModel(private val careTaskRepository: CareTaskRepository) : ViewModel() {

    fun updateTask(
        careTask: CareTaskEntity,
        onError: (Throwable) -> Unit,
        onSaved: (CareTaskEntity) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                careTaskRepository.updateTask(careTask)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onError(error)
                return@launch
            }
            onSaved(careTask)
        }
    }
}

/** Supplies the task repository to the edit form ViewModel. */
class EditCareTaskViewModelFactory(
    private val careTaskRepository: CareTaskRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditCareTaskViewModel::class.java)) {
            return EditCareTaskViewModel(careTaskRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
