package com.babytracker.ui.breastfeeding

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.R
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedPrediction
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.FeedSettingsRepository
import com.babytracker.domain.usecase.breastfeeding.PauseBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCase
import com.babytracker.domain.usecase.breastfeeding.ResumeBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.BreastfeedingEditError
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
import com.babytracker.domain.usecase.breastfeeding.UpdateBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.foldPause
import com.babytracker.domain.usecase.breastfeeding.validateBreastfeedingEdit
import com.babytracker.manager.BreastfeedingSessionNotificationCoordinator
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.babytracker.util.durationBetween
import com.babytracker.util.formatElapsedAgo
import com.babytracker.util.tickerFlow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

enum class FeedTimePickerTarget { ENTRY_START, ENTRY_END }

sealed class LastFeedingSummaryState {
    object Empty : LastFeedingSummaryState()
    data class Populated(
        val lastSession: BreastfeedingSession,
        val elapsedLabel: String,
        val nextRecommendedSide: BreastSide,
        val firstSideDuration: Duration,
        val secondSideDuration: Duration?
    ) : LastFeedingSummaryState()
}

data class EditSheetState(
    val original: BreastfeedingSession,
    val editedStart: Instant,
    val editedEnd: Instant?,
    val validationError: String? = null,
    val isSaving: Boolean = false,
) {
    val isDirty: Boolean
        get() = editedStart != original.startTime || editedEnd != original.endTime

    val canSave: Boolean
        get() = isDirty && validationError == null && !isSaving
}

data class BreastfeedingUiState(
    val activeSession: BreastfeedingSession? = null,
    val selectedSide: BreastSide? = null,
    val maxPerBreastMinutes: Int = 0,
    val maxTotalFeedMinutes: Int = 0,
    val lastFeedingSummary: LastFeedingSummaryState = LastFeedingSummaryState.Empty,
    val nextFeedPrediction: FeedPrediction? = null,
    val error: String? = null,
    val currentSide: BreastSide? = null,
    val editSheet: EditSheetState? = null,
    val pendingDeleteSession: BreastfeedingSession? = null,
    val showManualEntrySheet: Boolean = false,
    // Placeholder defaults are clock-free on purpose; real values are set in onAddEntryClick.
    // Calling LocalDate.now()/LocalTime.now() here would route through Instant statics and break
    // tests that use mockkStatic(Instant::class).
    val manualEntryDate: LocalDate = LocalDate.ofEpochDay(0),
    val manualEntryStartTime: LocalTime = LocalTime.MIN,
    val manualEntryEndTime: LocalTime = LocalTime.MIN,
    // Seeds the sheet's initial side selection; the chip selection itself is local sheet state.
    val manualEntrySide: BreastSide = BreastSide.LEFT,
    val manualEntryError: String? = null,
    val manualEntryDurationPreview: Duration? = null,
)

private const val MANUAL_ENTRY_DEFAULT_MINUTES = 15L
private const val SUMMARY_TICK_MS = 60_000L

@HiltViewModel
class BreastfeedingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val switchSide: SwitchBreastfeedingSideUseCase,
    private val pauseSession: PauseBreastfeedingSessionUseCase,
    private val resumeSession: ResumeBreastfeedingSessionUseCase,
    private val updateSession: UpdateBreastfeedingSessionUseCase,
    private val repository: BreastfeedingRepository,
    private val feedSettingsRepository: FeedSettingsRepository,
    private val notificationCoordinator: BreastfeedingSessionNotificationCoordinator,
    private val syncedWrite: SyncedWrite,
    predictNextFeed: PredictNextFeedUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreastfeedingUiState())
    val uiState: StateFlow<BreastfeedingUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<BreastfeedingSession>> = repository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            combine(
                repository.getActiveSession(),
                feedSettingsRepository.getMaxPerBreastMinutes(),
                feedSettingsRepository.getMaxTotalFeedMinutes()
            ) { session, maxPerBreast, maxTotal ->
                _uiState.value.copy(
                    activeSession = session,
                    maxPerBreastMinutes = maxPerBreast,
                    maxTotalFeedMinutes = maxTotal,
                    currentSide = session?.currentSide(),
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }

        viewModelScope.launch {
            combine(
                history.map { sessions ->
                    // Single pass, no intermediate filtered list: pick the latest non-null endTime
                    // (sessions with a null endTime sort to Instant.MIN and are dropped by takeIf).
                    sessions.maxByOrNull { it.endTime ?: Instant.MIN }?.takeIf { it.endTime != null }
                }.distinctUntilChanged(),
                tickerFlow(SUMMARY_TICK_MS)
            ) { lastSession, _ ->
                buildLastFeedingSummary(lastSession)
            }.collect { summary ->
                val autoSide = if (summary is LastFeedingSummaryState.Populated && _uiState.value.selectedSide == null) {
                    summary.nextRecommendedSide
                } else {
                    _uiState.value.selectedSide
                }
                _uiState.value = _uiState.value.copy(lastFeedingSummary = summary, selectedSide = autoSide)
            }
        }

        viewModelScope.launch {
            predictNextFeed().collect { prediction ->
                _uiState.value = _uiState.value.copy(nextFeedPrediction = prediction)
            }
        }

    }

    fun onSideSelected(side: BreastSide) {
        _uiState.value = _uiState.value.copy(selectedSide = side)
    }

    fun onStartSession() {
        val side = _uiState.value.selectedSide ?: return
        viewModelScope.launch {
            val result = runCatching {
                repository.insertSession(BreastfeedingSession(startTime = Instant.now(), startingSide = side))
            }
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(error = appContext.getString(R.string.error_bf_start))
                return@launch
            }
            repository.getActiveSession()
                .first { it != null }
                ?.let { session ->
                    notificationCoordinator.scheduleInitial(session)
                    notificationCoordinator.showRunning(session)
                }
            syncedWrite.sync(SyncType.SESSIONS)
        }
    }

    fun onStopSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            val result = runCatching { repository.updateSession(session.copy(endTime = Instant.now())) }
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(error = appContext.getString(R.string.error_bf_stop))
                return@launch
            }
            notificationCoordinator.cancelAllSessionNotifications()
            _uiState.value = _uiState.value.copy(selectedSide = null)
            syncedWrite.sync(SyncType.SESSIONS)
        }
    }

    fun onErrorDismissed() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun onSwitchSide() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            switchSide(session)
            if (session.switchTime == null) {
                notificationCoordinator.cancelPerBreastScheduled()
                val switchedSession = session.copy(switchTime = Instant.now())
                notificationCoordinator.showRunning(switchedSession)
            }
            syncedWrite.sync(SyncType.SESSIONS)
        }
    }

    fun onPauseSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            val pausedAt = Instant.now()
            pauseSession(session)
            notificationCoordinator.cancelScheduled()
            notificationCoordinator.showPaused(session, pausedAt)
            syncedWrite.sync(SyncType.SESSIONS)
        }
    }

    fun onResumeSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            val resumeInstant = Instant.now()
            resumeSession(session)
            val totalPausedMs = notificationCoordinator.rescheduleAfterResume(session, resumeInstant)
            notificationCoordinator.showRunning(session, pausedDurationMs = totalPausedMs)
            syncedWrite.sync(SyncType.SESSIONS)
        }
    }

    fun onEditSessionClick(session: BreastfeedingSession) {
        val state = EditSheetState(
            original = session,
            editedStart = session.startTime,
            editedEnd = session.endTime,
            validationError = null,
        )
        _uiState.value = _uiState.value.copy(editSheet = state)
    }

    fun onEditTimeChanged(newStart: Instant, newEnd: Instant?) {
        val current = _uiState.value.editSheet ?: return
        val (projectedPausedMs, _) = foldPause(current.original, newStart, newEnd)
        val error = validateBreastfeedingEdit(
            startTime = newStart,
            endTime = newEnd,
            pausedDurationMs = projectedPausedMs,
            now = Instant.now(),
        )
        _uiState.value = _uiState.value.copy(
            editSheet = current.copy(
                editedStart = newStart,
                editedEnd = newEnd,
                validationError = error?.let { appContext.getString(it.messageRes()) },
            )
        )
    }

    fun onEditDismiss() {
        _uiState.value = _uiState.value.copy(editSheet = null)
    }

    fun onEditSave() {
        val current = _uiState.value.editSheet ?: return
        if (!current.canSave) return
        val wasInProgress = current.original.endTime == null
        val nowHasEnd = current.editedEnd != null
        _uiState.value = _uiState.value.copy(editSheet = current.copy(isSaving = true))
        viewModelScope.launch {
            val result = runCatching {
                updateSession(current.original, current.editedStart, current.editedEnd)
            }
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    editSheet = current.copy(isSaving = false),
                    error = appContext.getString(R.string.error_bf_save),
                )
                return@launch
            }
            if (wasInProgress && nowHasEnd) {
                notificationCoordinator.cancelAllSessionNotifications()
            }
            _uiState.value = _uiState.value.copy(editSheet = null)
            syncedWrite.sync(SyncType.SESSIONS)
        }
    }

    /** Sets the session pending card-level delete confirmation, or clears it when null. */
    fun onPendingDeleteSessionChanged(session: BreastfeedingSession?) {
        _uiState.value = _uiState.value.copy(pendingDeleteSession = session)
    }

    fun onConfirmDeleteSession() {
        val session = _uiState.value.pendingDeleteSession ?: return
        _uiState.value = _uiState.value.copy(pendingDeleteSession = null)
        viewModelScope.launch {
            if (!deleteSessionInternal(session)) {
                _uiState.value = _uiState.value.copy(
                    error = appContext.getString(R.string.error_bf_delete),
                )
            }
        }
    }

    /**
     * Shared delete body behind the card overflow-menu flow ([onConfirmDeleteSession]).
     * Deletes the session, cancels its notifications if it was still in progress, and syncs
     * to Firestore. Returns true on success so callers can update their own UI state.
     */
    private suspend fun deleteSessionInternal(session: BreastfeedingSession): Boolean {
        val result = runCatching { repository.deleteSession(session) }
        if (result.isFailure) return false
        if (session.endTime == null) {
            notificationCoordinator.cancelAllSessionNotifications()
        }
        syncedWrite.sync(SyncType.SESSIONS)
        return true
    }

    fun onAddEntryClick() {
        val end = LocalTime.now()
        val start = end.minusMinutes(MANUAL_ENTRY_DEFAULT_MINUTES)
        val summary = _uiState.value.lastFeedingSummary
        val recommendedSide = (summary as? LastFeedingSummaryState.Populated)?.nextRecommendedSide ?: BreastSide.LEFT
        _uiState.value = _uiState.value.copy(
            showManualEntrySheet = true,
            manualEntryDate = LocalDate.now(),
            manualEntryStartTime = start,
            manualEntryEndTime = end,
            manualEntrySide = recommendedSide,
            manualEntryError = null,
            manualEntryDurationPreview = durationBetween(start, end, LocalDate.now()),
        )
    }

    fun onDismissManualEntry() {
        _uiState.value = _uiState.value.copy(showManualEntrySheet = false, manualEntryError = null)
    }

    /**
     * Patches the editable manual-entry fields. Each argument defaults to its current value so
     * callers can change a single field (date, start, or end) without touching the others. The
     * duration preview is recomputed from the resulting date + times on every change.
     */
    fun onManualEntryChanged(
        date: LocalDate = _uiState.value.manualEntryDate,
        startTime: LocalTime = _uiState.value.manualEntryStartTime,
        endTime: LocalTime = _uiState.value.manualEntryEndTime,
    ) {
        _uiState.value = _uiState.value.copy(
            manualEntryDate = date,
            manualEntryStartTime = startTime,
            manualEntryEndTime = endTime,
            manualEntryError = null,
            manualEntryDurationPreview = durationBetween(startTime, endTime, date),
        )
    }

    fun onSaveManualEntry(side: BreastSide) {
        val state = _uiState.value
        val zone = ZoneId.systemDefault()
        var startInstant = state.manualEntryStartTime.atDate(state.manualEntryDate).atZone(zone).toInstant()
        val endInstant = state.manualEntryEndTime.atDate(state.manualEntryDate).atZone(zone).toInstant()
        if (startInstant > endInstant) {
            startInstant = state.manualEntryStartTime.atDate(state.manualEntryDate.minusDays(1)).atZone(zone).toInstant()
        }
        if (endInstant <= startInstant) {
            _uiState.value = state.copy(manualEntryError = appContext.getString(R.string.error_bf_end_after_start))
            return
        }
        viewModelScope.launch {
            repository.insertSession(
                BreastfeedingSession(startTime = startInstant, endTime = endInstant, startingSide = side),
            )
            _uiState.value = _uiState.value.copy(showManualEntrySheet = false, manualEntryError = null)
            syncedWrite.sync(SyncType.SESSIONS)
        }
    }

    private fun BreastfeedingSession.currentSide(): BreastSide =
        switchTime?.let {
            if (startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT
        } ?: startingSide

    private fun buildLastFeedingSummary(lastSession: BreastfeedingSession?): LastFeedingSummaryState {
        if (lastSession == null) return LastFeedingSummaryState.Empty
        val endTime = lastSession.endTime ?: return LastFeedingSummaryState.Empty

        val elapsed = Duration.between(lastSession.startTime, Instant.now())
        val elapsedLabel = elapsed.formatElapsedAgo(appContext)

        val sideDurations = lastSession.sideDurationsUntil(endTime)
        val firstSideDuration = sideDurations.first
        val secondSideDuration = sideDurations.second

        // Non-null: endTime was checked above, and a completed session always has a recommendation.
        val nextRecommendedSide = lastSession.recommendedNextSide()
            ?: return LastFeedingSummaryState.Empty

        return LastFeedingSummaryState.Populated(
            lastSession = lastSession,
            elapsedLabel = elapsedLabel,
            nextRecommendedSide = nextRecommendedSide,
            firstSideDuration = firstSideDuration,
            secondSideDuration = secondSideDuration
        )
    }
}

@StringRes
internal fun BreastfeedingEditError.messageRes(): Int = when (this) {
    BreastfeedingEditError.START_IN_FUTURE -> R.string.error_bf_start_future
    BreastfeedingEditError.END_IN_FUTURE -> R.string.error_bf_end_future
    BreastfeedingEditError.END_BEFORE_START -> R.string.error_bf_end_after_start
    BreastfeedingEditError.SESSION_SHORTER_THAN_PAUSES -> R.string.error_bf_session_shorter_pauses
}
