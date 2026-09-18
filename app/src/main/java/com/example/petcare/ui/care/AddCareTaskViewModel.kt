package com.example.petcare.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.care.CareTaskRepository
import kotlinx.coroutines.launch

class AddCareTaskViewModel(private val careTaskRepository: CareTaskRepository) : ViewModel() {

    fun addTask(
        petId: Long,
        title: String,
        dueDateEpochDay: Long,
        reminderMinutesOfDay: Int,
        onSaved: (CareTaskEntity) -> Unit
    ) {
        viewModelScope.launch {
            onSaved(
                careTaskRepository.addTask(
                    petId,
                    title,
                    dueDateEpochDay,
                    reminderMinutesOfDay
                )
            )
        }
    }
}

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
