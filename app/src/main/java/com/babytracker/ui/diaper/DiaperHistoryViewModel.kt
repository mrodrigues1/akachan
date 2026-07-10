package com.babytracker.ui.diaper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.domain.usecase.diaper.DeleteDiaperChangeUseCase
import com.babytracker.util.groupByDateDescending
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Windowed slice of the history grouped by day, newest first. */
data class DiaperHistoryWindow(
    val days: List<Pair<LocalDate, List<DiaperChange>>> = emptyList(),
    val hasMore: Boolean = false,
) {
    val changeCount: Int get() = days.sumOf { it.second.size }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiaperHistoryViewModel @Inject constructor(
    diaperRepository: DiaperRepository,
    private val deleteDiaperChange: DeleteDiaperChangeUseCase,
    private val zone: ZoneId,
) : ViewModel() {

    private val historyLimit = MutableStateFlow(HISTORY_PAGE_SIZE)

    // Bounded window instead of observeAll(): queries one row past the limit so hasMore never
    // needs a separate count query.
    val historyByDateDesc: StateFlow<DiaperHistoryWindow> = historyLimit
        .flatMapLatest { limit ->
            diaperRepository.observeRecent(limit + 1).map { changes ->
                DiaperHistoryWindow(
                    days = changes.take(limit).groupByDateDescending(zone) { it.timestamp },
                    hasMore = changes.size > limit,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DiaperHistoryWindow())

    fun onLoadMoreHistory() {
        val window = historyByDateDesc.value
        // Ignore repeats until the previously requested window has emitted: the load-more
        // sentinel can leave and re-enter composition before Room delivers the bigger page.
        if (!window.hasMore || window.changeCount < historyLimit.value) return
        historyLimit.value += HISTORY_PAGE_SIZE
    }

    // The change awaiting delete confirmation; non-null drives the confirmation dialog.
    private val _pendingDelete = MutableStateFlow<DiaperChange?>(null)
    val pendingDelete: StateFlow<DiaperChange?> = _pendingDelete.asStateFlow()

    // Set when the delete write fails, so the screen can tell the parent the row wasn't removed.
    private val _deleteError = MutableStateFlow(false)
    val deleteError: StateFlow<Boolean> = _deleteError.asStateFlow()

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
            runCatching { deleteDiaperChange(change.id) }.onFailure { _deleteError.value = true }
        }
    }

    /** The screen has shown the delete-failure message; clear the flag. */
    fun onDeleteErrorConsumed() {
        _deleteError.value = false
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val HISTORY_PAGE_SIZE = 50
    }
}
