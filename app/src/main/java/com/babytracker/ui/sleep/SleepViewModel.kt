package com.babytracker.ui.sleep

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepSchedule
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.domain.usecase.sleep.GenerateSleepScheduleUseCase
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.domain.usecase.sleep.SaveSleepEntryUseCase
import com.babytracker.domain.usecase.sleep.StartSleepRecordUseCase
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import com.babytracker.domain.usecase.sleep.UpdateSleepEntryUseCase
import com.babytracker.manager.NapReminderScheduler
import com.babytracker.manager.SleepNotificationScheduler
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import com.babytracker.domain.model.BabyEventType
import com.babytracker.domain.usecase.baby.LogBabyEventUseCase
import com.babytracker.util.durationBetween
import com.babytracker.util.formatElapsedShort
import com.babytracker.util.formatTime12h
import com.babytracker.util.groupByDateDescending
import com.babytracker.util.tickerFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
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

enum class SleepTimePickerTarget { WAKE, ENTRY_START, ENTRY_END }

sealed class LastSleepSummaryState {
    object Empty : LastSleepSummaryState()
    data class Populated(
        val record: SleepRecord,
        val awakeForLabel: String,
        val endedAtLabel: String,
    ) : LastSleepSummaryState()
}

/** Today's completed-sleep rollup for the tracking screen: list, totals, and nap count. */
data class SleepTodayStats(
    val entries: List<SleepRecord> = emptyList(),
    val totalSleep: Duration = Duration.ZERO,
    val napCount: Int = 0,
    val nightSleep: Duration = Duration.ZERO,
)

/**
 * [entries]/[totalSleep]/[napCount] cover completed records that started on [today];
 * [nightSleep] sums night sleeps that ended on [today], so last night's sleep that started
 * yesterday still counts.
 */
internal fun sleepTodayStats(records: List<SleepRecord>, today: LocalDate, zone: ZoneId): SleepTodayStats {
    val entries = records
        .filter { it.startTime.atZone(zone).toLocalDate() == today && it.endTime != null }
        .sortedByDescending { it.startTime }
    return SleepTodayStats(
        entries = entries,
        totalSleep = entries
            .mapNotNull { record -> record.endTime?.let { Duration.between(record.startTime, it) } }
            .fold(Duration.ZERO) { acc, d -> acc + d },
        napCount = entries.count { it.sleepType == SleepType.NAP },
        nightSleep = records
            .filter { it.sleepType == SleepType.NIGHT_SLEEP && it.endTime?.atZone(zone)?.toLocalDate() == today }
            .mapNotNull { record -> record.endTime?.let { Duration.between(record.startTime, it) } }
            .fold(Duration.ZERO) { acc, d -> acc + d },
    )
}

data class SleepUiState(
    val schedule: SleepSchedule? = null,
    val isLoading: Boolean = false,
    val wakeTime: LocalTime? = null,
    val lastSleepSummary: LastSleepSummaryState = LastSleepSummaryState.Empty,
    val showEntrySheet: Boolean = false,
    val entryType: SleepType = SleepType.NAP,
    val entryDate: LocalDate = LocalDate.now(),
    val entryStartTime: LocalTime = LocalTime.now(),
    val entryEndTime: LocalTime = LocalTime.now(),
    val entryError: String? = null,
    val entryDurationPreview: Duration? = null,
    val pendingDeleteRecord: SleepRecord? = null,
    val editingRecord: SleepRecord? = null,
    val isRegressionExpanded: Boolean = true,
    val activeTimePicker: SleepTimePickerTarget? = null,
    val sleepPrediction: SleepPredictionState = SleepPredictionState.Unavailable("loading"),
)

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val saveSleepEntry: SaveSleepEntryUseCase,
    private val updateSleepEntry: UpdateSleepEntryUseCase,
    private val sleepRepository: SleepRepository,
    private val generateSchedule: GenerateSleepScheduleUseCase,
    private val babyRepository: BabyRepository,
    private val settingsRepository: SettingsRepository,
    private val sleepSettingsRepository: SleepSettingsRepository,
    private val startRecord: StartSleepRecordUseCase,
    private val stopRecord: StopSleepRecordUseCase,
    private val sleepNotificationScheduler: SleepNotificationScheduler,
    private val napReminderScheduler: NapReminderScheduler,
    private val syncedWrite: SyncedWrite,
    private val predictSleepWindow: PredictSleepWindowUseCase,
    private val logBabyEvent: LogBabyEventUseCase,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepUiState())
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<SleepRecord>> = sleepRepository.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyByDateDesc: StateFlow<List<Pair<LocalDate, List<SleepRecord>>>> = history
        .map { records -> records.groupByDateDescending { it.startTime } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSleepSession: StateFlow<SleepRecord?> = history
        .map { records -> records.find { it.isInProgress } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayStats: StateFlow<SleepTodayStats> = history
        .map { records -> sleepTodayStats(records, LocalDate.now(), ZoneId.systemDefault()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SleepTodayStats())

    init {
        viewModelScope.launch {
            predictSleepWindow().collect { prediction ->
                _uiState.value = _uiState.value.copy(sleepPrediction = prediction)
            }
        }
        viewModelScope.launch {
            settingsRepository.getWakeTime().collect { wakeTime ->
                _uiState.value = _uiState.value.copy(wakeTime = wakeTime)
            }
        }
        viewModelScope.launch {
            // Select the latest completed record only when history changes; the per-minute tick then
            // just reformats its elapsed-awake label instead of rescanning the whole history list.
            val latestCompleted = history
                .map { records ->
                    if (records.any { it.isInProgress }) {
                        null
                    } else {
                        // Single pass, no intermediate filtered list: latest non-null endTime.
                        records.maxByOrNull { it.endTime ?: Instant.MIN }?.takeIf { it.endTime != null }
                    }
                }
                .distinctUntilChanged()
            combine(latestCompleted, tickerFlow(LAST_SLEEP_TICK_MS)) { record, _ ->
                buildLastSleepSummary(record)
            }.collect { summary ->
                _uiState.value = _uiState.value.copy(lastSleepSummary = summary)
            }
        }
        loadSchedule()
    }

    fun onStartRecord(sleepType: SleepType) {
        viewModelScope.launch {
            napReminderScheduler.cancel()
            val record = startRecord(sleepType)
            sleepNotificationScheduler.show(record.id, record.sleepType, record.startTime)
            syncedWrite.sync(SyncType.SLEEP_RECORDS)
        }
    }

    fun onStopRecord() {
        val session = activeSleepSession.value ?: return
        viewModelScope.launch {
            val stoppedRecord = stopRecord(session.id)
            sleepNotificationScheduler.cancel()
            if (stoppedRecord != null) {
                when (stoppedRecord.sleepType) {
                    SleepType.NIGHT_SLEEP -> {
                        val endTime = stoppedRecord.endTime
                        if (endTime != null) {
                            val zone = ZoneId.systemDefault()
                            if (endTime.atZone(zone).toLocalDate() == LocalDate.now()) {
                                settingsRepository.setWakeTime(endTime.atZone(zone).toLocalTime())
                            }
                        }
                    }
                    SleepType.NAP -> {
                        val enabled = sleepSettingsRepository.getNapReminderEnabled().first()
                        if (enabled) {
                            val delayMinutes = sleepSettingsRepository.getNapReminderDelayMinutes().first()
                            napReminderScheduler.schedule(Instant.now(), delayMinutes)
                        }
                    }
                }
            }
            syncedWrite.sync(SyncType.SLEEP_RECORDS)
        }
    }

    fun onAddEntryClick() {
        val end = LocalTime.now()
        val start = end.minusHours(1)
        _uiState.value = _uiState.value.copy(
            showEntrySheet = true,
            editingRecord = null,
            entryDate = LocalDate.now(),
            entryStartTime = start,
            entryEndTime = end,
            entryError = null,
            entryDurationPreview = durationBetween(start, end, LocalDate.now())
        )
    }

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
        val state = _uiState.value
        val editingRecord = state.editingRecord
        val zone = editingRecord?.let { zoneForEditing(it) } ?: ZoneId.systemDefault()
        val startDate = state.entryDate
        val endDate = if (state.entryEndTime.isBefore(state.entryStartTime)) startDate.plusDays(1) else startDate
        val startInstant = state.entryStartTime.atDate(startDate).atZone(zone).toInstant()
        val endInstant = state.entryEndTime.atDate(endDate).atZone(zone).toInstant()
        if (endInstant <= startInstant) {
            _uiState.value = state.copy(
                entryError = "End time needs to be after start time. Adjust one time to save this sleep."
            )
            return
        }
        val duration = Duration.between(startInstant, endInstant)
        if (duration > maxDurationFor(state.entryType)) {
            _uiState.value = state.copy(
                entryError = "Sleep duration is too long for this sleep type. Adjust one time to save this sleep."
            )
            return
        }
        if (state.entryType == SleepType.NIGHT_SLEEP) {
            val now = Instant.now()
            val hasOverlap = history.value.any { existing ->
                if (existing.sleepType != SleepType.NIGHT_SLEEP) return@any false
                if (editingRecord != null && existing.id == editingRecord.id) return@any false
                val existingEnd = existing.endTime ?: now
                startInstant < existingEnd && endInstant > existing.startTime
            }
            if (hasOverlap) {
                _uiState.value = state.copy(
                    entryError = "Night sleep overlaps with an existing record. Adjust the times to save this sleep."
                )
                return
            }
        }
        viewModelScope.launch {
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
            if (state.entryType == SleepType.NIGHT_SLEEP) {
                val sysZone = ZoneId.systemDefault()
                if (endInstant.atZone(sysZone).toLocalDate() == LocalDate.now()) {
                    val latestOtherEnd = history.value
                        .filter { r ->
                            r.sleepType == SleepType.NIGHT_SLEEP &&
                                r.endTime?.atZone(sysZone)?.toLocalDate() == LocalDate.now() &&
                                (editingRecord == null || r.id != editingRecord.id)
                        }
                        .mapNotNull { it.endTime }
                        .maxOrNull()
                    if (latestOtherEnd == null || endInstant >= latestOtherEnd) {
                        settingsRepository.setWakeTime(endInstant.atZone(sysZone).toLocalTime())
                    }
                }
            }
            _uiState.value = _uiState.value.copy(showEntrySheet = false, entryError = null, editingRecord = null)
            syncedWrite.sync(SyncType.SLEEP_RECORDS)
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
            null -> Unit
        }
    }

    fun refreshSchedule() {
        loadSchedule()
    }

    fun onCueTapped(type: BabyEventType) {
        viewModelScope.launch { runCatching { logBabyEvent(type) } }
    }

    fun onToggleRegression() {
        _uiState.value = _uiState.value.copy(isRegressionExpanded = !_uiState.value.isRegressionExpanded)
    }

    private fun loadSchedule() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val baby = babyRepository.getBabyProfile().firstOrNull()
            if (baby != null) {
                val schedule = generateSchedule(baby)
                _uiState.value = _uiState.value.copy(schedule = schedule, isLoading = false)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun buildLastSleepSummary(latestCompleted: SleepRecord?): LastSleepSummaryState {
        val record = latestCompleted ?: return LastSleepSummaryState.Empty
        val endTime = record.endTime ?: return LastSleepSummaryState.Empty
        val awakeDuration = Duration.between(endTime, Instant.now()).coerceAtLeast(Duration.ZERO)

        return LastSleepSummaryState.Populated(
            record = record,
            awakeForLabel = appContext.getString(R.string.sleep_awake_for, awakeDuration.formatElapsedShort()),
            endedAtLabel = appContext.getString(R.string.sleep_ended_at, endTime.formatTime12h()),
        )
    }

    private fun zoneForEditing(record: SleepRecord): ZoneId =
        record.timezoneId
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()

    private fun maxDurationFor(type: SleepType): Duration = when (type) {
        SleepType.NAP -> Duration.ofHours(SleepPredictionTuning.MAX_NAP_DURATION_HOURS)
        SleepType.NIGHT_SLEEP -> Duration.ofHours(SleepPredictionTuning.MAX_NIGHT_SLEEP_DURATION_HOURS)
    }

    private companion object {
        const val LAST_SLEEP_TICK_MS = 60_000L
    }
}
