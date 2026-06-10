package com.babytracker.ui.breastfeeding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedPrediction
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.breastfeeding.DeleteBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import com.babytracker.domain.usecase.breastfeeding.PauseBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCase
import com.babytracker.domain.usecase.breastfeeding.ResumeBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SaveBreastfeedingEntryUseCase
import com.babytracker.domain.usecase.breastfeeding.StartBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.StopBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
import com.babytracker.domain.usecase.breastfeeding.UpdateBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.foldPause
import com.babytracker.domain.usecase.breastfeeding.validateBreastfeedingEdit
import com.babytracker.manager.BreastfeedingSessionNotificationCoordinator
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.babytracker.util.formatElapsedAgo
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
    val deleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
) {
    val isDirty: Boolean
        get() = editedStart != original.startTime || editedEnd != original.endTime

    val canSave: Boolean
        get() = isDirty && validationError == null && !isSaving && !isDeleting
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
    val showManualEntrySheet: Boolean = false,
    // Placeholder defaults are clock-free on purpose; real values are set in onAddEntryClick.
    // Calling LocalDate.now()/LocalTime.now() here would route through Instant statics and break
    // tests that use mockkStatic(Instant::class).
    val manualEntryDate: LocalDate = LocalDate.EPOCH,
    val manualEntryStartTime: LocalTime = LocalTime.MIN,
    val manualEntryEndTime: LocalTime = LocalTime.MIN,
    // Seeds the sheet's initial side selection; the chip selection itself is local sheet state.
    val manualEntrySide: BreastSide = BreastSide.LEFT,
    val manualEntryError: String? = null,
    val manualEntryDurationPreview: Duration? = null,
)

private const val MANUAL_ENTRY_DEFAULT_MINUTES = 15L

@HiltViewModel
class BreastfeedingViewModel @Inject constructor(
    private val startSession: StartBreastfeedingSessionUseCase,
    private val stopSession: StopBreastfeedingSessionUseCase,
    private val switchSide: SwitchBreastfeedingSideUseCase,
    getHistory: GetBreastfeedingHistoryUseCase,
    private val pauseSession: PauseBreastfeedingSessionUseCase,
    private val resumeSession: ResumeBreastfeedingSessionUseCase,
    private val updateSession: UpdateBreastfeedingSessionUseCase,
    private val deleteSession: DeleteBreastfeedingSessionUseCase,
    private val saveBreastfeedingEntry: SaveBreastfeedingEntryUseCase,
    private val repository: BreastfeedingRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationCoordinator: BreastfeedingSessionNotificationCoordinator,
    private val syncToFirestore: SyncToFirestoreUseCase,
    predictNextFeed: PredictNextFeedUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BreastfeedingUiState())
    val uiState: StateFlow<BreastfeedingUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<BreastfeedingSession>> = getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            combine(
                repository.getActiveSession(),
                settingsRepository.getMaxPerBreastMinutes(),
                settingsRepository.getMaxTotalFeedMinutes()
            ) { session, maxPerBreast, maxTotal ->
                val previous = _uiState.value
                BreastfeedingUiState(
                    activeSession = session,
                    selectedSide = previous.selectedSide,
                    maxPerBreastMinutes = maxPerBreast,
                    maxTotalFeedMinutes = maxTotal,
                    lastFeedingSummary = previous.lastFeedingSummary,
                    nextFeedPrediction = previous.nextFeedPrediction,
                    error = previous.error,
                    currentSide = session?.currentSide(),
                    editSheet = previous.editSheet,
                    showManualEntrySheet = previous.showManualEntrySheet,
                    manualEntryDate = previous.manualEntryDate,
                    manualEntryStartTime = previous.manualEntryStartTime,
                    manualEntryEndTime = previous.manualEntryEndTime,
                    manualEntrySide = previous.manualEntrySide,
                    manualEntryError = previous.manualEntryError,
                    manualEntryDurationPreview = previous.manualEntryDurationPreview,
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }

        viewModelScope.launch {
            // The initial emit runs on the collecting coroutine (Main/testDispatcher) so that
            // StandardTestDispatcher.advanceUntilIdle() sees it and terminates correctly.
            // Subsequent ticks are delayed on Dispatchers.Default to keep the infinite loop
            // off the test scheduler.
            val ticker = flow<Unit> {
                emit(Unit)
                emitAll(
                    flow<Unit> {
                        while (true) {
                            kotlinx.coroutines.delay(60_000L)
                            emit(Unit)
                        }
                    }.flowOn(Dispatchers.Default)
                )
            }
            combine(
                history.map { sessions ->
                    sessions.filter { it.endTime != null }.maxByOrNull { it.endTime!! }
                },
                ticker
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
            val result = runCatching { startSession(side) }
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(error = "Could not start session. Please try again.")
                return@launch
            }
            repository.getActiveSession()
                .first { it != null }
                ?.let { session ->
                    notificationCoordinator.scheduleInitial(session)
                    notificationCoordinator.showRunning(session)
                }
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
        }
    }

    fun onStopSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            val result = runCatching { stopSession(session) }
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(error = "Could not stop session. Please try again.")
                return@launch
            }
            notificationCoordinator.cancelAllSessionNotifications()
            _uiState.value = _uiState.value.copy(selectedSide = null)
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
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
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
        }
    }

    fun onPauseSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            val pausedAt = Instant.now()
            pauseSession(session)
            notificationCoordinator.cancelScheduled()
            notificationCoordinator.showPaused(session, pausedAt)
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
        }
    }

    fun onResumeSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            val resumeInstant = Instant.now()
            resumeSession(session)
            val totalPausedMs = notificationCoordinator.rescheduleAfterResume(session, resumeInstant)
            notificationCoordinator.showRunning(session, pausedDurationMs = totalPausedMs)
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
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

    fun onEditStartChanged(newStart: Instant) {
        val current = _uiState.value.editSheet ?: return
        val (projectedPausedMs, _) = foldPause(current.original, newStart, current.editedEnd)
        val error = validateBreastfeedingEdit(
            startTime = newStart,
            endTime = current.editedEnd,
            pausedDurationMs = projectedPausedMs,
            now = Instant.now(),
        )
        _uiState.value = _uiState.value.copy(
            editSheet = current.copy(editedStart = newStart, validationError = error)
        )
    }

    fun onEditEndChanged(newEnd: Instant?) {
        val current = _uiState.value.editSheet ?: return
        val (projectedPausedMs, _) = foldPause(current.original, current.editedStart, newEnd)
        val error = validateBreastfeedingEdit(
            startTime = current.editedStart,
            endTime = newEnd,
            pausedDurationMs = projectedPausedMs,
            now = Instant.now(),
        )
        _uiState.value = _uiState.value.copy(
            editSheet = current.copy(editedEnd = newEnd, validationError = error)
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
                    error = "Could not save changes. Please try again.",
                )
                return@launch
            }
            if (wasInProgress && nowHasEnd) {
                notificationCoordinator.cancelAllSessionNotifications()
            }
            _uiState.value = _uiState.value.copy(editSheet = null)
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
        }
    }

    fun onDeleteRequested() {
        val current = _uiState.value.editSheet ?: return
        _uiState.value = _uiState.value.copy(editSheet = current.copy(deleteConfirm = true))
    }

    fun onDeleteCancelled() {
        val current = _uiState.value.editSheet ?: return
        _uiState.value = _uiState.value.copy(editSheet = current.copy(deleteConfirm = false))
    }

    fun onDeleteConfirmed() {
        val current = _uiState.value.editSheet ?: return
        val wasInProgress = current.original.endTime == null
        _uiState.value = _uiState.value.copy(editSheet = current.copy(isDeleting = true))
        viewModelScope.launch {
            val result = runCatching { deleteSession(current.original) }
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    editSheet = current.copy(isDeleting = false, deleteConfirm = false),
                    error = "Could not delete session. Please try again.",
                )
                return@launch
            }
            if (wasInProgress) {
                notificationCoordinator.cancelAllSessionNotifications()
            }
            _uiState.value = _uiState.value.copy(editSheet = null)
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
        }
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
            manualEntryDurationPreview = computeDurationPreview(start, end, LocalDate.now()),
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
            manualEntryDurationPreview = computeDurationPreview(startTime, endTime, date),
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
            _uiState.value = state.copy(manualEntryError = "End time must be after start time")
            return
        }
        viewModelScope.launch {
            saveBreastfeedingEntry(startInstant, endInstant, side)
            _uiState.value = _uiState.value.copy(showManualEntrySheet = false, manualEntryError = null)
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
        }
    }

    private fun computeDurationPreview(start: LocalTime, end: LocalTime, date: LocalDate): Duration? {
        val zone = ZoneId.systemDefault()
        var startInstant = start.atDate(date).atZone(zone).toInstant()
        val endInstant = end.atDate(date).atZone(zone).toInstant()
        if (startInstant > endInstant) {
            startInstant = start.atDate(date.minusDays(1)).atZone(zone).toInstant()
        }
        val d = Duration.between(startInstant, endInstant)
        return if (d.isNegative || d.isZero) null else d
    }

    private fun BreastfeedingSession.currentSide(): BreastSide =
        switchTime?.let {
            if (startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT
        } ?: startingSide

    private fun buildLastFeedingSummary(lastSession: BreastfeedingSession?): LastFeedingSummaryState {
        if (lastSession == null) return LastFeedingSummaryState.Empty
        val endTime = lastSession.endTime ?: return LastFeedingSummaryState.Empty

        val elapsed = Duration.between(lastSession.startTime, Instant.now())
        val elapsedLabel = elapsed.formatElapsedAgo()

        val sideDurations = lastSession.sideDurationsUntil(endTime)
        val firstSideDuration = sideDurations.first
        val secondSideDuration = sideDurations.second

        val oppositeSide = if (lastSession.startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT
        val nextRecommendedSide = when {
            // No switch: only the starting side was used — recommend the other side
            secondSideDuration == null -> oppositeSide
            // Second side was used less than first — recommend second side (opposite of starting)
            secondSideDuration < firstSideDuration -> oppositeSide
            // First side was used less (or both equal) — recommend first/starting side
            else -> lastSession.startingSide
        }

        return LastFeedingSummaryState.Populated(
            lastSession = lastSession,
            elapsedLabel = elapsedLabel,
            nextRecommendedSide = nextRecommendedSide,
            firstSideDuration = firstSideDuration,
            secondSideDuration = secondSideDuration
        )
    }
}
