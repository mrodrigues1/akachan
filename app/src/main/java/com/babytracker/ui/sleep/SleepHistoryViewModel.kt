package com.babytracker.ui.sleep

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.SaveSleepEntryUseCase
import com.babytracker.domain.usecase.sleep.SleepEntryError
import com.babytracker.domain.usecase.sleep.UpdateSleepEntryUseCase
import com.babytracker.domain.usecase.sleep.shouldUpdateWakeTimeFor
import com.babytracker.domain.usecase.sleep.validateSleepEntry
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import com.babytracker.util.durationBetween
import com.babytracker.util.groupByDateDescending
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/** History-screen state: the imperative edit-sheet, picker, and delete fields. */
data class SleepHistoryUiState(
    val showEntrySheet: Boolean = false,
    val entryType: SleepType = SleepType.NAP,
    val entryDate: LocalDate = LocalDate.now(),
    val entryStartTime: LocalTime = LocalTime.now(),
    val entryEndTime: LocalTime = LocalTime.now(),
    val entryError: String? = null,
    val entryDurationPreview: Duration? = null,
    val pendingDeleteRecord: SleepRecord? = null,
    val editingRecord: SleepRecord? = null,
    val activeTimePicker: SleepTimePickerTarget? = null,
)

@HiltViewModel
class SleepHistoryViewModel @Inject constructor(
    private val saveSleepEntry: SaveSleepEntryUseCase,
    private val updateSleepEntry: UpdateSleepEntryUseCase,
    private val sleepRepository: SleepRepository,
    private val settingsRepository: SettingsRepository,
    private val syncedWrite: SyncedWrite,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepHistoryUiState())
    val uiState: StateFlow<SleepHistoryUiState> = _uiState.asStateFlow()

    // The grouped list is the only retained representation of history; it fans out directly from the
    // Room flow (no second flat StateFlow) and is subscription-scoped so the observer detaches when the
    // history screen leaves the foreground.
    val historyByDateDesc: StateFlow<List<Pair<LocalDate, List<SleepRecord>>>> =
        sleepRepository.getAllRecords()
            .map { records -> records.groupByDateDescending { it.startTime } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())

    fun onEditRecord(record: SleepRecord) {
        val zone = zoneForEditing(record)
        val startLocal = record.startTime.atZone(zone).toLocalTime()
        val endLocal = record.endTime?.atZone(zone)?.toLocalTime() ?: LocalTime.now()
        val dateLocal = record.startTime.atZone(zone).toLocalDate()
        _uiState.value = _uiState.value.copy(
            showEntrySheet = true,
            editingRecord = record,
            entryType = record.sleepType,
            entryDate = dateLocal,
            entryStartTime = startLocal,
            entryEndTime = endLocal,
            entryError = null,
            entryDurationPreview = durationBetween(startLocal, endLocal, LocalDate.now())
        )
    }

    fun onDeleteRequest(record: SleepRecord) {
        _uiState.value = _uiState.value.copy(pendingDeleteRecord = record)
    }

    fun onDismissDelete() {
        _uiState.value = _uiState.value.copy(pendingDeleteRecord = null)
    }

    fun onConfirmDelete() {
        val record = _uiState.value.pendingDeleteRecord ?: return
        _uiState.value = _uiState.value.copy(pendingDeleteRecord = null)
        viewModelScope.launch {
            sleepRepository.deleteRecord(record.id)
            syncedWrite.sync(SyncType.SLEEP_RECORDS)
        }
    }

    fun onDismissSheet() {
        _uiState.value = _uiState.value.copy(showEntrySheet = false, entryError = null, editingRecord = null)
    }

    fun onEntryTypeChanged(type: SleepType) {
        _uiState.value = _uiState.value.copy(entryType = type)
    }

    fun onEntryDateChanged(date: LocalDate) {
        _uiState.value = _uiState.value.copy(entryDate = date, entryError = null)
    }

    fun onSaveEntry() {
        viewModelScope.launch {
            val state = _uiState.value
            val editingRecord = state.editingRecord
            val zone = editingRecord?.let { zoneForEditing(it) } ?: ZoneId.systemDefault()
            val startDate = state.entryDate
            val endDate = if (state.entryEndTime.isBefore(state.entryStartTime)) startDate.plusDays(1) else startDate
            val startInstant = state.entryStartTime.atDate(startDate).atZone(zone).toInstant()
            val endInstant = state.entryEndTime.atDate(endDate).atZone(zone).toInstant()
            val existingRecords = sleepRepository.getAllRecords().first()
            val error = validateSleepEntry(
                startTime = startInstant,
                endTime = endInstant,
                type = state.entryType,
                existingRecords = existingRecords,
                now = Instant.now(),
                excludingId = editingRecord?.id,
            )
            if (error != null) {
                _uiState.value = _uiState.value.copy(entryError = appContext.getString(error.messageRes()))
                return@launch
            }
            try {
                if (editingRecord != null) {
                    updateSleepEntry(
                        id = editingRecord.id,
                        startTime = startInstant,
                        endTime = endInstant,
                        type = state.entryType,
                        timezoneId = zone.id,
                    )
                } else {
                    saveSleepEntry(startInstant, endInstant, state.entryType)
                }
            } catch (e: IllegalArgumentException) {
                val mappedError = SleepEntryError.entries.find { it.name == e.message }
                _uiState.value = _uiState.value.copy(
                    entryError = mappedError?.let { appContext.getString(it.messageRes()) }
                        ?: appContext.getString(R.string.sleep_entry_pick_times),
                )
                return@launch
            }
            val sysZone = ZoneId.systemDefault()
            if (shouldUpdateWakeTimeFor(
                    endTime = endInstant,
                    sleepType = state.entryType,
                    existingRecords = existingRecords,
                    zone = sysZone,
                    today = LocalDate.now(sysZone),
                    excludingId = editingRecord?.id,
                )
            ) {
                settingsRepository.setWakeTime(endInstant.atZone(sysZone).toLocalTime())
            }
            _uiState.value = _uiState.value.copy(showEntrySheet = false, entryError = null, editingRecord = null)
            syncedWrite.sync(SyncType.SLEEP_RECORDS)
        }
    }

    fun onShowTimePicker(target: SleepTimePickerTarget) {
        _uiState.value = _uiState.value.copy(activeTimePicker = target)
    }

    fun onDismissTimePicker() {
        _uiState.value = _uiState.value.copy(activeTimePicker = null)
    }

    fun onConfirmTimePicker(time: LocalTime) {
        val current = _uiState.value
        when (current.activeTimePicker) {
            SleepTimePickerTarget.ENTRY_START -> {
                val base = current.copy(
                    entryStartTime = time,
                    entryError = null,
                    activeTimePicker = null
                )
                _uiState.value = base.copy(
                    entryDurationPreview = durationBetween(time, base.entryEndTime, LocalDate.now())
                )
            }
            SleepTimePickerTarget.ENTRY_END -> {
                val base = current.copy(
                    entryEndTime = time,
                    entryError = null,
                    activeTimePicker = null
                )
                _uiState.value = base.copy(
                    entryDurationPreview = durationBetween(base.entryStartTime, time, LocalDate.now())
                )
            }
            // The history screen never opens the wake-time picker.
            SleepTimePickerTarget.WAKE, null -> Unit
        }
    }

    private fun zoneForEditing(record: SleepRecord): ZoneId =
        record.timezoneId
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
