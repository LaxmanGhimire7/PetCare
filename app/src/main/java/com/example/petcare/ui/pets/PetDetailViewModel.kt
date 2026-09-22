package com.example.petcare.ui.pets

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.petcare.R
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.data.local.expense.ExpenseSummary
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.ui.ScreenState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Data required by every pet page and each of its four detail tabs. */
data class PetDetailUi(
    val pets: List<PetEntity>,
    val tasks: List<CareTaskSummary>,
    val expenses: List<ExpenseSummary>
)

/** Keeps Pet Detail live while Room changes tasks, completion, pets, or spending. */
class PetDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repositories = PetCareRepositories(application)

    val state: StateFlow<ScreenState<PetDetailUi>> = combine(
        repositories.pets.observePets(),
        repositories.tasks.observeUpcoming(),
        repositories.tasks.observeCompleted(),
        repositories.expenses.observeAll()
    ) { pets, upcoming, completed, expenses ->
        if (pets.isEmpty()) ScreenState.Empty else ScreenState.Content(
            PetDetailUi(pets, (upcoming + completed).distinctBy { it.id }, expenses)
        )
    }.catch { emit(ScreenState.Error(application.getString(R.string.pet_unavailable))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)
}
