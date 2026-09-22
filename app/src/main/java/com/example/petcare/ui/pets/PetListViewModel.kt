package com.example.petcare.ui.pets

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.petcare.R
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.data.local.expense.ExpenseSummary
import com.example.petcare.data.local.pet.DeletedPetSnapshot
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.ui.ScreenState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Complete Pets destination data used for cards, coming-up care, and monthly spend. */
data class PetListUi(
    val pets: List<PetEntity>,
    val tasks: List<CareTaskSummary>,
    val expenses: List<ExpenseSummary>
)

/** Owns the account-scoped Pets destination and delete/undo operations across rotation. */
class PetListViewModel(application: Application) : AndroidViewModel(application) {
    private val repositories = PetCareRepositories(application)
    val state: StateFlow<ScreenState<PetListUi>> = combine(
        repositories.pets.observePets(),
        repositories.tasks.observeUpcoming(),
        repositories.tasks.observeCompleted(),
        repositories.expenses.observeAll()
    ) { pets, upcoming, completed, expenses ->
        if (pets.isEmpty()) ScreenState.Empty else ScreenState.Content(
            PetListUi(pets, (upcoming + completed).distinctBy { it.id }, expenses)
        )
    }.catch { emit(ScreenState.Error(application.getString(R.string.pets_load_failed))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    suspend fun delete(id: Long): DeletedPetSnapshot? = repositories.petDeletion.delete(id)
    suspend fun restore(snapshot: DeletedPetSnapshot) = repositories.petDeletion.restore(snapshot)
}
