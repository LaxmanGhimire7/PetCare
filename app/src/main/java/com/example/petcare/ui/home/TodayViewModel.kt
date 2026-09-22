package com.example.petcare.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.petcare.R
import com.example.petcare.data.local.PetCareRepositories
import com.example.petcare.data.local.care.CareTaskEntity
import com.example.petcare.data.local.care.CareTaskSummary
import com.example.petcare.data.local.pet.PetEntity
import com.example.petcare.ui.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn

/** All Room-backed rows needed to draw Today as one consistent state. */
data class TodayData(
    val pets: List<PetEntity>,
    val upcoming: List<CareTaskSummary>,
    val completed: List<CareTaskSummary>
)

/** Retains today's filter, live data, and first-load motion across navigation. */
class TodayViewModel(application: Application) : AndroidViewModel(application) {
    private val repositories = PetCareRepositories(application)
    private val refreshTick = MutableStateFlow(0)
    var selectedPetId: Long? = null
    var selectedEpochDay: Long = java.time.LocalDate.now().toEpochDay()

    val state: StateFlow<ScreenState<TodayData>> = combine(
        repositories.pets.observePets(), repositories.tasks.observeUpcoming(),
        repositories.tasks.observeCompleted(), refreshTick
    ) { pets, upcoming, completed, _ -> TodayData(pets, upcoming, completed) }
        .map<TodayData, ScreenState<TodayData>> { rows ->
            if (rows.pets.isEmpty() && rows.upcoming.isEmpty() && rows.completed.isEmpty())
                ScreenState.Empty else ScreenState.Content(rows)
        }
        .retryWhen { _, _ ->
            emit(ScreenState.Error(application.getString(R.string.today_load_failed)))
            val failedAtTick = refreshTick.value
            refreshTick.first { it > failedAtTick }
            true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    /** Also recalculates local-day and overdue presentation when Room has not changed. */
    fun refresh() { refreshTick.value++ }

    suspend fun completeTask(id: Long): CareTaskEntity? = repositories.tasks.completeTask(id)
    suspend fun reopenTask(id: Long) = repositories.tasks.reopenTask(id)
    suspend fun snoozeTask(id: Long, minutes: Int): CareTaskEntity? =
        repositories.tasks.snoozeTask(id, minutes)
    suspend fun saveOrder(ids: List<Long>, slots: List<Long>) =
        repositories.tasks.saveOrder(ids, slots)
    suspend fun resetCompleted(day: Long): List<CareTaskEntity> =
        repositories.tasks.resetCompletedForDay(day)
    suspend fun restoreCompleted(rows: List<CareTaskEntity>) =
        repositories.tasks.restoreCompleted(rows)
    suspend fun deleteTask(id: Long): CareTaskEntity? = repositories.tasks.deleteTask(id)
    suspend fun restoreTask(row: CareTaskEntity) = repositories.tasks.restoreTask(row)
    suspend fun upcoming(): List<CareTaskSummary> = repositories.tasks.observeUpcoming().first()

    fun consumeFirstEntrance(): Boolean {
        if (playedThisProcess) return false
        playedThisProcess = true
        return true
    }

    private companion object { var playedThisProcess = false }
}
