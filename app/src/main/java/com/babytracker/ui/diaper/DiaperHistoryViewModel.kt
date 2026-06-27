package com.babytracker.ui.diaper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.usecase.diaper.DeleteDiaperChangeUseCase
import com.babytracker.domain.usecase.diaper.ObserveDiaperChangesUseCase
import com.babytracker.util.groupByDateDescending
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val zone: ZoneId,
) : ViewModel() {

    val historyByDateDesc: StateFlow<List<Pair<LocalDate, List<DiaperChange>>>> =
        observeDiaperChanges()
            .map { changes ->
                changes.groupByDateDescending(zone) { it.timestamp }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    // The change awaiting delete confirmation; non-null drives the confirmation dialog.
    private val _pendingDelete = MutableStateFlow<DiaperChange?>(null)
    val pendingDelete: StateFlow<DiaperChange?> = _pendingDelete.asStateFlow()

    fun onDeleteRequest(change: DiaperChange) {
        _pendingDelete.value = change
    }

    fun onCancelDelete() {
        _pendingDelete.value = null
    }

    fun onConfirmDelete() {
        val change = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch {
            runCatching { deleteDiaperChange(change.id) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
