package com.example.petcare.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.care.CareTaskRepository
import kotlinx.coroutines.launch

class EditCareTaskViewModel(private val careTaskRepository: CareTaskRepository) : ViewModel() {

    fun updateTask(careTask: CareTaskEntity, onSaved: (CareTaskEntity) -> Unit) {
        viewModelScope.launch {
            careTaskRepository.updateTask(careTask)
            onSaved(careTask)
        }
    }
}

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
