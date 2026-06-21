package com.babytracker.ui.vaccine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.usecase.vaccine.DeleteVaccineRecordUseCase
import com.babytracker.domain.usecase.vaccine.MarkVaccineAdministeredUseCase
import com.babytracker.domain.usecase.vaccine.ObserveVaccineRecordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class VaccineHistoryUiState(
    val isLoading: Boolean = true,
    /** Set when an upstream flow throws, so the screen can offer a retry instead of a blank list. */
    val isError: Boolean = false,
    val upcoming: List<VaccineRecord> = emptyList(),
    val administeredByDate: List<Pair<LocalDate, List<VaccineRecord>>> = emptyList(),
    val now: Instant = Instant.EPOCH,
) {
    /** Only meaningful once loaded without error. */
    val isEmpty: Boolean get() = upcoming.isEmpty() && administeredByDate.isEmpty()
}

@HiltViewModel
class VaccineHistoryViewModel @Inject constructor(
    observeRecords: ObserveVaccineRecordsUseCase,
    private val markGivenUseCase: MarkVaccineAdministeredUseCase,
    private val deleteUseCase: DeleteVaccineRecordUseCase,
    private val zone: ZoneId,
    private val now: () -> Instant,
) : ViewModel() {

    // The record inside the undo window: optimistically hidden from the list, deleted only once
    // the snackbar is dismissed. This "deferred delete" gives a real undo without re-creating the
    // row (re-adding via AddVaccineRecordUseCase would reject an overdue scheduled date).
    private val _pendingDelete = MutableStateFlow<VaccineRecord?>(null)
    val pendingDelete: StateFlow<VaccineRecord?> = _pendingDelete.asStateFlow()

    // Bumped by onRetry so flatMapLatest rebuilds the combined flow after an upstream failure;
    // a plain .catch would emit the error once and leave the flow terminated with no way back.
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<VaccineHistoryUiState> =
        retryTrigger.flatMapLatest {
            combine(observeRecords(), _pendingDelete) { records, pending ->
                val visible = records.filterNot { it.id == pending?.id }
                // Upcoming sorted ascending: overdue (earliest dates) naturally float to the top.
                val upcoming = visible
                    .filter { it.status == VaccineStatus.SCHEDULED }
                    .sortedBy { it.scheduledDate ?: it.createdAt }
                val administeredByDate = visible
                    .filter { it.status == VaccineStatus.ADMINISTERED }
                    .groupBy { (it.administeredDate ?: it.createdAt).atZone(zone).toLocalDate() }
                    .toSortedMap(reverseOrder())
                    .map { (date, list) ->
                        date to list.sortedByDescending { it.administeredDate ?: it.createdAt }
                    }
                VaccineHistoryUiState(
                    isLoading = false,
                    upcoming = upcoming,
                    administeredByDate = administeredByDate,
                    now = now(),
                )
            }.catch {
                emit(VaccineHistoryUiState(isLoading = false, isError = true))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VaccineHistoryUiState())

    fun markGiven(id: Long) = viewModelScope.launch { runCatching { markGivenUseCase(id, now()) } }

    /** Rebuild the data flow after an error state so the screen can recover. */
    fun onRetry() {
        retryTrigger.value++
    }

    /** Start the undo window: [record] is hidden but not yet deleted. Finalizes any prior pending delete. */
    fun requestDelete(record: VaccineRecord) {
        flushPending()
        _pendingDelete.value = record
    }

    /** Snackbar "Undo": the record was never actually deleted, so just reveal it again. */
    fun undoDelete() {
        _pendingDelete.value = null
    }

    /** Snackbar dismissed / timed out: finalize the deletion. */
    fun commitDelete() = flushPending()

    private fun flushPending() {
        val record = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch { runCatching { deleteUseCase(record.id) } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
