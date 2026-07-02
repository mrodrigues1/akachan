package com.babytracker.ui.doctorvisit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.usecase.doctorvisit.DeleteDoctorVisitUseCase
import com.babytracker.manager.DoctorVisitReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class DoctorVisitHistoryUiState(
    val upcoming: List<DoctorVisit> = emptyList(),
    val past: List<DoctorVisit> = emptyList(),
    val questionCounts: Map<Long, Int> = emptyMap(),
    val lastDeleted: DoctorVisit? = null,
) {
    val isEmpty: Boolean get() = upcoming.isEmpty() && past.isEmpty()
}

@HiltViewModel
class DoctorVisitHistoryViewModel @Inject constructor(
    private val repository: DoctorVisitRepository,
    private val deleteVisit: DeleteDoctorVisitUseCase,
    private val reminderScheduler: DoctorVisitReminderScheduler,
    private val now: () -> Instant,
) : ViewModel() {

    private val local = MutableStateFlow(DoctorVisitHistoryUiState())

    val uiState: StateFlow<DoctorVisitHistoryUiState> =
        combine(
            repository.observeAllVisits(),
            repository.observeAttachedQuestionCounts(),
            local,
        ) { visits, counts, state ->
            val instant = now()
            state.copy(
                upcoming = visits.filter { it.date.isAfter(instant) }.sortedBy { it.date },
                past = visits.filter { !it.date.isAfter(instant) }.sortedByDescending { it.date },
                questionCounts = counts,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DoctorVisitHistoryUiState())

    fun onDelete(visit: DoctorVisit) {
        viewModelScope.launch {
            deleteVisit(visit.id)
            local.update { it.copy(lastDeleted = visit) }
        }
    }

    /**
     * Undo a delete: re-insert the captured visit (preserving its original date / provider /
     * notes / snapshot ref / createdAt — only the row id changes), then re-arm its reminder if
     * still upcoming. Questions were detached to the inbox by the atomic delete and are NOT
     * re-attached (documented v1 trade-off); the user can re-attach from the edit sheet.
     */
    fun onUndoDelete() {
        val deleted = local.value.lastDeleted ?: return
        viewModelScope.launch {
            val newId = repository.insertVisit(deleted.copy(id = 0)) // 0 → Room autogenerates a new id
            reminderScheduler.schedule(deleted.copy(id = newId)) // re-arm under the real new id
            local.update { it.copy(lastDeleted = null) }
        }
    }

    fun onUndoConsumed() = local.update { it.copy(lastDeleted = null) }
}
