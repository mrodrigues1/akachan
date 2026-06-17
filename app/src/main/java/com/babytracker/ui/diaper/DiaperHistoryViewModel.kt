package com.babytracker.ui.diaper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.usecase.diaper.DeleteDiaperChangeUseCase
import com.babytracker.domain.usecase.diaper.LogDiaperChangeUseCase
import com.babytracker.domain.usecase.diaper.ObserveDiaperChangesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DiaperHistoryViewModel @Inject constructor(
    observeDiaperChanges: ObserveDiaperChangesUseCase,
    private val deleteDiaperChange: DeleteDiaperChangeUseCase,
    private val logDiaperChange: LogDiaperChangeUseCase,
    private val zone: ZoneId,
) : ViewModel() {

    val historyByDateDesc: StateFlow<List<Pair<LocalDate, List<DiaperChange>>>> =
        observeDiaperChanges()
            .map { changes ->
                changes
                    .groupBy { it.timestamp.atZone(zone).toLocalDate() }
                    .toSortedMap(reverseOrder())
                    .map { (date, list) -> date to list.sortedByDescending { it.timestamp } }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _deletions = MutableSharedFlow<DiaperChange>(extraBufferCapacity = 1)
    val deletions: SharedFlow<DiaperChange> = _deletions.asSharedFlow()

    fun onDelete(change: DiaperChange) {
        viewModelScope.launch {
            runCatching { deleteDiaperChange(change.id) }
                .onSuccess { _deletions.emit(change) }
        }
    }

    fun onUndoDelete(change: DiaperChange) {
        viewModelScope.launch {
            runCatching { logDiaperChange(change.type, change.timestamp, change.notes) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
