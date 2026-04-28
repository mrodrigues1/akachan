package com.babytracker.ui.breastfeeding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import com.babytracker.domain.usecase.breastfeeding.PauseBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.ResumeBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.StartBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.StopBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
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
import javax.inject.Inject

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

data class BreastfeedingUiState(
    val activeSession: BreastfeedingSession? = null,
    val selectedSide: BreastSide? = null,
    val maxPerBreastMinutes: Int = 0,
    val maxTotalFeedMinutes: Int = 0,
    val lastFeedingSummary: LastFeedingSummaryState = LastFeedingSummaryState.Empty
)

@HiltViewModel
class BreastfeedingViewModel @Inject constructor(
    private val startSession: StartBreastfeedingSessionUseCase,
    private val stopSession: StopBreastfeedingSessionUseCase,
    private val switchSide: SwitchBreastfeedingSideUseCase,
    getHistory: GetBreastfeedingHistoryUseCase,
    private val pauseSession: PauseBreastfeedingSessionUseCase,
    private val resumeSession: ResumeBreastfeedingSessionUseCase,
    private val repository: BreastfeedingRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationCoordinator: BreastfeedingSessionNotificationCoordinator,
    private val syncToFirestore: SyncToFirestoreUseCase,
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
                BreastfeedingUiState(
                    activeSession = session,
                    selectedSide = _uiState.value.selectedSide,
                    maxPerBreastMinutes = maxPerBreast,
                    maxTotalFeedMinutes = maxTotal,
                    lastFeedingSummary = _uiState.value.lastFeedingSummary
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
                _uiState.value = _uiState.value.copy(lastFeedingSummary = summary)
            }
        }
    }

    fun onSideSelected(side: BreastSide) {
        _uiState.value = _uiState.value.copy(selectedSide = side)
    }

    fun onStartSession() {
        val side = _uiState.value.selectedSide ?: return
        viewModelScope.launch {
            startSession(side)
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
            stopSession(session)
            notificationCoordinator.cancelAllSessionNotifications()
            runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
        }
    }

    fun onSwitchSide() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            switchSide(session)
            // Update the ongoing notification only when this is the first (and only) side switch.
            // After switching, the current side is the opposite of the original starting side.
            if (session.switchTime == null) {
                val switchedSession = session.copy(
                    switchTime = Instant.now()
                )
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

    private fun buildLastFeedingSummary(lastSession: BreastfeedingSession?): LastFeedingSummaryState {
        if (lastSession == null) return LastFeedingSummaryState.Empty
        val endTime = lastSession.endTime ?: return LastFeedingSummaryState.Empty

        val elapsed = Duration.between(lastSession.startTime, Instant.now())
        val elapsedLabel = elapsed.formatElapsedAgo()

        val firstSideDuration: Duration = lastSession.switchTime
            ?.let { Duration.between(lastSession.startTime, it) }
            ?: Duration.between(lastSession.startTime, endTime)

        val secondSideDuration: Duration? = lastSession.switchTime
            ?.let { Duration.between(it, endTime) }

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
