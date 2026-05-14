package com.babytracker.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepSchedule
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.sleep.DeleteSleepEntryUseCase
import com.babytracker.domain.usecase.sleep.GenerateSleepScheduleUseCase
import com.babytracker.domain.usecase.sleep.GetSleepHistoryUseCase
import com.babytracker.domain.usecase.sleep.SaveSleepEntryUseCase
import com.babytracker.domain.usecase.sleep.StartSleepRecordUseCase
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import com.babytracker.domain.usecase.sleep.UpdateSleepEntryUseCase
import com.babytracker.manager.SleepNotificationScheduler
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

enum class SleepTimePickerTarget { WAKE, ENTRY_START, ENTRY_END }

data class SleepUiState(
    val schedule: SleepSchedule? = null,
    val isLoading: Boolean = false,
    val wakeTime: LocalTime? = null,
    val showEntrySheet: Boolean = false,
    val entryType: SleepType = SleepType.NAP,
    val entryStartTime: LocalTime = LocalTime.now(),
    val entryEndTime: LocalTime = LocalTime.now(),
    val entryError: String? = null,
    val entryDurationPreview: Duration? = null,
    val pendingDeleteRecord: SleepRecord? = null,
    val editingRecord: SleepRecord? = null,
    val isRegressionExpanded: Boolean = true,
    val activeTimePicker: SleepTimePickerTarget? = null,
)

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val saveSleepEntry: SaveSleepEntryUseCase,
    private val updateSleepEntry: UpdateSleepEntryUseCase,
    private val deleteSleepEntry: DeleteSleepEntryUseCase,
    private val getSleepHistory: GetSleepHistoryUseCase,
    private val generateSchedule: GenerateSleepScheduleUseCase,
    private val getBabyProfile: GetBabyProfileUseCase,
    private val settingsRepository: SettingsRepository,
    private val startRecord: StartSleepRecordUseCase,
    private val stopRecord: StopSleepRecordUseCase,
    private val sleepNotificationScheduler: SleepNotificationScheduler,
    private val syncToFirestore: SyncToFirestoreUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepUiState())
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<SleepRecord>> = getSleepHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyByDateDesc: StateFlow<List<Pair<LocalDate, List<SleepRecord>>>> = history
        .map { records ->
            val zone = ZoneId.systemDefault()
            records
                .groupBy { it.startTime.atZone(zone).toLocalDate() }
                .entries
                .sortedByDescending { it.key }
                .map { (date, recs) -> date to recs }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSleepSession: StateFlow<SleepRecord?> = history
        .map { records -> records.find { it.isInProgress } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            settingsRepository.getWakeTime().collect { wakeTime ->
                _uiState.value = _uiState.value.copy(wakeTime = wakeTime)
            }
        }
        loadSchedule()
    }

    fun onStartRecord(sleepType: SleepType) {
        viewModelScope.launch {
            val record = startRecord(sleepType)
            sleepNotificationScheduler.show(record.id, record.sleepType, record.startTime)
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
        }
    }

    fun onStopRecord() {
        val session = activeSleepSession.value ?: return
        viewModelScope.launch {
            stopRecord(session.id)
            sleepNotificationScheduler.cancel()
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
        }
    }

    fun onAddEntryClick() {
        val now = LocalTime.now()
        _uiState.value = _uiState.value.copy(
            showEntrySheet = true,
            editingRecord = null,
            entryStartTime = now,
            entryEndTime = now,
            entryError = null,
            entryDurationPreview = null
        )
    }

    fun onEditRecord(record: SleepRecord) {
        val zone = ZoneId.systemDefault()
        val startLocal = record.startTime.atZone(zone).toLocalTime()
        val endLocal = record.endTime?.atZone(zone)?.toLocalTime() ?: LocalTime.now()
        _uiState.value = _uiState.value.copy(
            showEntrySheet = true,
            editingRecord = record,
            entryType = record.sleepType,
            entryStartTime = startLocal,
            entryEndTime = endLocal,
            entryError = null,
            entryDurationPreview = computeDurationPreview(startLocal, endLocal)
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
            deleteSleepEntry(record.id)
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
        }
    }

    fun onDismissSheet() {
        _uiState.value = _uiState.value.copy(showEntrySheet = false, entryError = null, editingRecord = null)
    }

    fun onEntryTypeChanged(type: SleepType) {
        _uiState.value = _uiState.value.copy(entryType = type)
    }

    fun onEntryStartTimeChanged(time: LocalTime) {
        val newState = _uiState.value.copy(entryStartTime = time, entryError = null)
        _uiState.value = newState.copy(entryDurationPreview = computeDurationPreview(time, newState.entryEndTime))
    }

    fun onEntryEndTimeChanged(time: LocalTime) {
        val newState = _uiState.value.copy(entryEndTime = time, entryError = null)
        _uiState.value = newState.copy(entryDurationPreview = computeDurationPreview(newState.entryStartTime, time))
    }

    fun onSaveEntry() {
        val state = _uiState.value
        val zone = ZoneId.systemDefault()
        val referenceDate = state.editingRecord?.startTime?.atZone(zone)?.toLocalDate() ?: LocalDate.now()
        var startInstant = state.entryStartTime.atDate(referenceDate).atZone(zone).toInstant()
        val endInstant = state.entryEndTime.atDate(referenceDate).atZone(zone).toInstant()
        if (startInstant > endInstant) {
            startInstant = state.entryStartTime.atDate(referenceDate.minusDays(1)).atZone(zone).toInstant()
        }
        if (endInstant <= startInstant) {
            _uiState.value = state.copy(entryError = "End time must be after start time")
            return
        }
        viewModelScope.launch {
            if (state.editingRecord != null) {
                updateSleepEntry(state.editingRecord.id, startInstant, endInstant, state.entryType)
            } else {
                saveSleepEntry(startInstant, endInstant, state.entryType)
            }
            _uiState.value = _uiState.value.copy(showEntrySheet = false, entryError = null, editingRecord = null)
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SLEEP_RECORDS) }
        }
    }

    fun onSetWakeTime(time: LocalTime) {
        viewModelScope.launch {
            settingsRepository.setWakeTime(time)
            loadSchedule()
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
            SleepTimePickerTarget.WAKE -> {
                _uiState.value = current.copy(activeTimePicker = null)
                onSetWakeTime(time)
            }
            SleepTimePickerTarget.ENTRY_START -> {
                val base = current.copy(
                    entryStartTime = time,
                    entryError = null,
                    activeTimePicker = null
                )
                _uiState.value = base.copy(
                    entryDurationPreview = computeDurationPreview(time, base.entryEndTime)
                )
            }
            SleepTimePickerTarget.ENTRY_END -> {
                val base = current.copy(
                    entryEndTime = time,
                    entryError = null,
                    activeTimePicker = null
                )
                _uiState.value = base.copy(
                    entryDurationPreview = computeDurationPreview(base.entryStartTime, time)
                )
            }
            null -> Unit
        }
    }

    fun refreshSchedule() {
        loadSchedule()
    }

    fun onToggleRegression() {
        _uiState.value = _uiState.value.copy(isRegressionExpanded = !_uiState.value.isRegressionExpanded)
    }

    private fun computeDurationPreview(start: LocalTime, end: LocalTime): Duration? {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        var startInstant = start.atDate(today).atZone(zone).toInstant()
        val endInstant = end.atDate(today).atZone(zone).toInstant()
        if (startInstant > endInstant) {
            startInstant = start.atDate(today.minusDays(1)).atZone(zone).toInstant()
        }
        val d = Duration.between(startInstant, endInstant)
        return if (d.isNegative || d.isZero) null else d
    }

    private fun loadSchedule() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val baby = getBabyProfile().firstOrNull()
            if (baby != null) {
                val schedule = generateSchedule(baby)
                _uiState.value = _uiState.value.copy(schedule = schedule, isLoading = false)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
