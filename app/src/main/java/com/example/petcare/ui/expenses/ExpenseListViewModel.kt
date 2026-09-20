package com.example.petcare.ui.expenses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.petcare.R
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.expense.ExpenseEntity
import com.example.petcare.data.local.expense.ExpenseSummary
import com.example.petcare.ui.ScreenState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Supplies live expense rows and routes deletion through the signed-in account. */
class ExpenseListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PetCareRepositories(application).expenses
    val state: StateFlow<ScreenState<List<ExpenseSummary>>> = repository.observeAll()
        .map<List<ExpenseSummary>, ScreenState<List<ExpenseSummary>>> { rows ->
            if (rows.isEmpty()) ScreenState.Empty else ScreenState.Content(rows)
        }
        .catch { emit(ScreenState.Error(application.getString(R.string.expenses_load_failed))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    suspend fun delete(id: Long): ExpenseEntity? = repository.delete(id)
    suspend fun restore(snapshot: ExpenseEntity) = repository.restore(snapshot)
}
