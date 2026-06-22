package com.babytracker.ui.vaccine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.usecase.vaccine.DeleteVaccineRecordUseCase
import com.babytracker.domain.usecase.vaccine.MarkVaccineAdministeredUseCase
import com.babytracker.domain.usecase.vaccine.MarkVaccineScheduledUseCase
import com.babytracker.domain.usecase.vaccine.ObserveVaccineRecordsUseCase
import com.babytracker.domain.usecase.vaccine.RestoreVaccineRecordUseCase
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
    val toSchedule: List<VaccineRecord> = emptyList(),
    val upcoming: List<VaccineRecord> = emptyList(),
    val administeredByDate: List<Pair<LocalDate, List<VaccineRecord>>> = emptyList(),
    val now: Instant = Instant.EPOCH,
) {
    /** Only meaningful once loaded without error. */
    val isEmpty: Boolean get() = toSchedule.isEmpty() && upcoming.isEmpty() && administeredByDate.isEmpty()
}

@HiltViewModel
class VaccineHistoryViewModel @Inject constructor(
    observeRecords: ObserveVaccineRecordsUseCase,
    private val markGivenUseCase: MarkVaccineAdministeredUseCase,
    private val markScheduledUseCase: MarkVaccineScheduledUseCase,
    private val deleteUseCase: DeleteVaccineRecordUseCase,
    private val restoreUseCase: RestoreVaccineRecordUseCase,
    private val zone: ZoneId,
    private val now: () -> Instant,
) : ViewModel() {

    // The record inside the delete undo window. The delete already committed (it commits immediately
    // so it can't be lost when the screen leaves composition before the snackbar resolves); this only
    // holds the original record so the screen can show the undo snackbar and so the list hides the row
    // without a flicker while Room re-emits. Undo re-inserts the record verbatim.
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
                val toSchedule = visible
                    .filter { it.status == VaccineStatus.TO_SCHEDULE && it.scheduledDate != null }
                    .sortedWith(compareBy({ it.scheduledDate }, { it.name }))
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
                    toSchedule = toSchedule,
                    upcoming = upcoming,
                    administeredByDate = administeredByDate,
                    now = now(),
                )
            }.catch {
                emit(VaccineHistoryUiState(isLoading = false, isError = true))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VaccineHistoryUiState())

    fun markGiven(id: Long) = viewModelScope.launch { runCatching { markGivenUseCase(id, now()) } }

    fun markScheduled(id: Long) = viewModelScope.launch { runCatching { markScheduledUseCase(id) } }

    /** Rebuild the data flow after an error state so the screen can recover. */
    fun onRetry() {
        retryTrigger.value++
    }

    /**
     * Delete [record] now and open the undo window. The write commits immediately so it survives the
     * screen leaving composition; [_pendingDelete] only hides the row and holds the record for undo.
     */
    fun requestDelete(record: VaccineRecord) {
        _pendingDelete.value = record
        viewModelScope.launch { runCatching { deleteUseCase(record.id) } }
    }

    /** Snackbar "Undo": revert the committed delete by re-inserting the original record. */
    fun undoDelete() {
        val record = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch { runCatching { restoreUseCase(record) } }
    }

    /** Snackbar dismissed / timed out: the delete already happened, so just close the undo window. */
    fun commitDelete() {
        _pendingDelete.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
