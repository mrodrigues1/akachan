package com.babytracker.ui.sleep

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.SaveSleepEntryUseCase
import com.babytracker.domain.usecase.sleep.SharedSleepPredictionStream
import com.babytracker.domain.usecase.sleep.SleepEntryError
import com.babytracker.domain.usecase.sleep.UpdateSleepEntryUseCase
import com.babytracker.domain.usecase.sleep.shouldUpdateWakeTimeFor
import com.babytracker.domain.usecase.sleep.validateSleepEntry
import com.babytracker.manager.SleepSessionController
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import com.babytracker.domain.model.BabyEventType
import com.babytracker.domain.usecase.baby.LogBabyEventUseCase
import com.babytracker.util.durationBetween
import com.babytracker.util.formatElapsedShort
import com.babytracker.util.formatTime12h
import com.babytracker.util.sumMergingOverlaps
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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

/** This record's (start, end) span, or `null` while it is still in progress. */
internal fun SleepRecord.toInterval(): Pair<Instant, Instant>? = endTime?.let { startTime to it }

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
        totalSleep = sumMergingOverlaps(entries.mapNotNull { it.toInterval() }),
        napCount = entries.count { it.sleepType == SleepType.NAP },
        nightSleep = sumMergingOverlaps(
            records
                .filter { it.sleepType == SleepType.NIGHT_SLEEP && it.endTime?.atZone(zone)?.toLocalDate() == today }
                .mapNotNull { it.toInterval() }
        ),
    )
}

/**
 * Tracking-screen state. Only the imperative sheet/picker/delete fields live here as a
 * [MutableStateFlow]; the reactive projections ([wakeTime], [lastSleepSummary], [sleepPrediction],
 * [activeSleepSession], [todayStats]) are exposed as their own subscription-scoped [StateFlow]s so
 * their upstream Room/prediction pipelines only run while the screen is on-screen.
 */
data class SleepUiState(
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
class SleepViewModel @Inject constructor(
    private val saveSleepEntry: SaveSleepEntryUseCase,
    private val updateSleepEntry: UpdateSleepEntryUseCase,
    private val sleepRepository: SleepRepository,
    private val settingsRepository: SettingsRepository,
    private val sleepSessionController: SleepSessionController,
    private val syncedWrite: SyncedWrite,
    sharedSleepPrediction: SharedSleepPredictionStream,
    private val logBabyEvent: LogBabyEventUseCase,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepUiState())
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

    // One shared, subscription-scoped observer: the derived flows below all fan out from this, so a
    // single Room observer runs while any of them is collected and detaches 5s after the screen stops.
    // Bounded to the window from the start of yesterday to now instead of the unbounded getAllRecords():
    // the tracking screen only reads today's completed sleeps, last night's night sleep (which starts
    // yesterday), the active session, and the latest completed record — all within this window — so a
    // sleep write no longer re-maps and re-emits the whole ~2-3k-row history. getRecentOrActiveRecordsFlow
    // also keeps any still-open record regardless of age (so a stuck/forgotten active sleep stays
    // visible and stoppable, and lastSleepSummary stays Empty mid-session) and any record that ended in
    // the window even if it started earlier (so the completed record left behind when such a sleep is
    // stopped keeps its summary). `since` is recomputed on each (re)subscription (the flow{} defers it
    // to collection time), and WhileSubscribed(5s) re-anchors whenever the screen is left and reopened;
    // the today/yesterday filters in sleepTodayStats and buildLastSleepSummary are re-derived per emission.
    // onSaveEntry deliberately still validates against a full getAllRecords().first() read — overlap
    // must be checked against all history.
    // ponytail: a tracking screen held continuously subscribed across midnight keeps yesterday's lower
    // bound until the next re-subscription, widening the window by the extra day's rows. Accepted for the
    // same reason as HomeViewModel — re-anchoring per rollover needs a midnight ticker we don't build.
    private val history: StateFlow<List<SleepRecord>> =
        flow { emitAll(sleepRepository.getRecentOrActiveRecordsFlow(startOfYesterdayInstant())) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())

    val activeSleepSession: StateFlow<SleepRecord?> = history
        .map { records -> records.find { it.isInProgress } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), null)

    val todayStats: StateFlow<SleepTodayStats> = history
        .map { records -> sleepTodayStats(records, LocalDate.now(), ZoneId.systemDefault()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), SleepTodayStats())

    val wakeTime: StateFlow<LocalTime?> = settingsRepository.getWakeTime()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), null)

    val lastSleepSummary: StateFlow<LastSleepSummaryState> = combine(
        // Select the latest completed record only when history changes; the per-minute tick then
        // just reformats its elapsed-awake label instead of rescanning the whole history list.
        history
            .map { records ->
                if (records.any { it.isInProgress }) {
                    null
                } else {
                    // Single pass, no intermediate filtered list: latest non-null endTime.
                    records.maxByOrNull { it.endTime ?: Instant.MIN }?.takeIf { it.endTime != null }
                }
            }
            .distinctUntilChanged(),
        tickerFlow(LAST_SLEEP_TICK_MS),
    ) { record, _ -> buildLastSleepSummary(record) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), LastSleepSummaryState.Empty)

    // The one hot prediction pipeline is shared app-wide; here it is only collected while the tracking
    // screen is subscribed.
    val sleepPrediction: StateFlow<SleepPredictionState> = sharedSleepPrediction.observe()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            SleepPredictionState.Unavailable("loading"),
        )

    fun onStartRecord(sleepType: SleepType) {
        viewModelScope.launch {
            sleepSessionController.start(sleepType)
        }
    }

    fun onStopRecord() {
        val session = activeSleepSession.value ?: return
        viewModelScope.launch {
            sleepSessionController.stop(session.id)
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
        viewModelScope.launch {
            val state = _uiState.value
            val editingRecord = state.editingRecord
            val zone = editingRecord?.let { zoneForEditing(it) } ?: ZoneId.systemDefault()
            val startDate = state.entryDate
            val endDate = if (state.entryEndTime.isBefore(state.entryStartTime)) startDate.plusDays(1) else startDate
            val startInstant = state.entryStartTime.atDate(startDate).atZone(zone).toInstant()
            val endInstant = state.entryEndTime.atDate(endDate).atZone(zone).toInstant()
            // Validate against a fresh snapshot; the history observer is subscription-scoped, so read
            // the current records directly instead of relying on a hot cache.
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
                // The use case re-validates against a fresh read as a safety net (defense-in-depth
                // against a race with a concurrent write); this VM-level check already passed
                // against a slightly older snapshot, so a fresh failure here is rare but must not
                // crash the screen — surface it the same way a pre-save validation failure would.
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

    fun onSetWakeTime(time: LocalTime) {
        viewModelScope.launch {
            settingsRepository.setWakeTime(time)
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

    fun onCueTapped(type: BabyEventType) {
        // Fire-and-forget by design (no UI state depends on it) — still log so a failure isn't invisible.
        viewModelScope.launch {
            runCatching { logBabyEvent(type) }
                .onFailure { Log.w(TAG, "onCueTapped failed to log $type", it) }
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

    /** Start of the local calendar day before today — the lower bound of the bounded history window. */
    private fun startOfYesterdayInstant(zone: ZoneId = ZoneId.systemDefault()): Instant =
        LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant()

    private companion object {
        const val TAG = "SleepViewModel"
        const val LAST_SLEEP_TICK_MS = 60_000L
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}

@StringRes
internal fun SleepEntryError.messageRes(): Int = when (this) {
    SleepEntryError.END_BEFORE_START -> R.string.error_sleep_end_after_start
    SleepEntryError.DURATION_TOO_LONG -> R.string.error_sleep_duration_too_long
    SleepEntryError.OVERLAP -> R.string.error_sleep_overlap
}
