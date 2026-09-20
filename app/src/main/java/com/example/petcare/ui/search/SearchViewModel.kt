package com.example.petcare.ui.search

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Current account's searchable rows, captured consistently from Room flows. */
data class SearchData(
    val pets: List<PetEntity>,
    val tasks: List<CareTaskSummary>,
    val expenses: List<ExpenseSummary>
)

/** Combines pets, care tasks, and expenses before the search UI applies filters. */
class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repositories = PetCareRepositories(application)
    val state: StateFlow<ScreenState<SearchData>> = combine(
        repositories.pets.observePets(), repositories.tasks.observeUpcoming(),
        repositories.tasks.observeCompleted(), repositories.expenses.observeAll()
    ) { pets, upcoming, completed, expenses ->
        SearchData(pets, upcoming + completed, expenses)
    }.map<SearchData, ScreenState<SearchData>> { rows ->
        if (rows.pets.isEmpty() && rows.tasks.isEmpty() && rows.expenses.isEmpty())
            ScreenState.Empty else ScreenState.Content(rows)
    }.catch { emit(ScreenState.Error(application.getString(R.string.search_load_failed))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)
}
