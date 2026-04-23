package com.babytracker.ui.breastfeeding

import android.content.Context
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
import com.babytracker.manager.BreastfeedingNotificationManager
import com.babytracker.manager.NotificationScheduler
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @param:ApplicationContext private val context: Context,
    private val notificationScheduler: NotificationScheduler = BreastfeedingNotificationManager(context)
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
                    scheduleNotifications(session)
                    val richEnabled = settingsRepository.getRichNotificationsEnabled().first()
                    NotificationHelper.showBreastfeedingActive(
                        context = context,
                        sessionId = session.id,
                        currentSide = session.startingSide.name,
                        sessionStartEpochMs = session.startTime.toEpochMilli(),
                        pausedDurationMs = session.pausedDurationMs,
                        richEnabled = richEnabled
                    )
                }
        }
    }

    fun onStopSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            stopSession(session)
            notificationScheduler.cancelAllScheduledNotifications()
            NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_NOTIFICATION_ID)
            NotificationHelper.cancelNotification(context, NotificationHelper.SWITCH_SIDE_NOTIFICATION_ID)
            NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_ACTIVE_NOTIFICATION_ID)
        }
    }

    fun onSwitchSide() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            switchSide(session)
            // Update the ongoing notification only when this is the first (and only) side switch.
            // After switching, the current side is the opposite of the original starting side.
            if (session.switchTime == null) {
                val newSide = if (session.startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT
                val richEnabled = settingsRepository.getRichNotificationsEnabled().first()
                NotificationHelper.showBreastfeedingActive(
                    context = context,
                    sessionId = session.id,
                    currentSide = newSide.name,
                    sessionStartEpochMs = session.startTime.toEpochMilli(),
                    pausedDurationMs = session.pausedDurationMs,
                    richEnabled = richEnabled
                )
            }
        }
    }

    fun onPauseSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            pauseSession(session)
            notificationScheduler.cancelAllScheduledNotifications()
            NotificationHelper.cancelNotification(context, NotificationHelper.BREASTFEEDING_ACTIVE_NOTIFICATION_ID)
        }
    }

    fun onResumeSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            val resumeInstant = Instant.now()
            resumeSession(session)
            rescheduleNotificationsAfterResume(session)
            val currentPauseDurationMs = session.pausedAt
                ?.let { Duration.between(it, resumeInstant).toMillis() }
                ?: 0L
            val totalPausedMs = session.pausedDurationMs + currentPauseDurationMs
            val currentSide = if (session.switchTime != null) {
                if (session.startingSide == BreastSide.LEFT) BreastSide.RIGHT.name else BreastSide.LEFT.name
            } else {
                session.startingSide.name
            }
            val richEnabled = settingsRepository.getRichNotificationsEnabled().first()
            NotificationHelper.showBreastfeedingActive(
                context = context,
                sessionId = session.id,
                currentSide = currentSide,
                sessionStartEpochMs = session.startTime.toEpochMilli(),
                pausedDurationMs = totalPausedMs,
                richEnabled = richEnabled
            )
        }
    }

    private fun scheduleNotifications(session: BreastfeedingSession) {
        val maxPerBreastMinutes = _uiState.value.maxPerBreastMinutes
        val maxTotalFeedMinutes = _uiState.value.maxTotalFeedMinutes

        if (maxPerBreastMinutes > 0) {
            notificationScheduler.scheduleMaxPerBreastNotification(
                sessionStartTime = session.startTime,
                maxPerBreastMinutes = maxPerBreastMinutes,
                sessionId = session.id,
                currentSide = session.startingSide.name,
                maxTotalMinutes = maxTotalFeedMinutes
            )
        }

        if (maxTotalFeedMinutes > 0) {
            notificationScheduler.scheduleMaxTotalTimeNotification(
                sessionStartTime = session.startTime,
                maxTotalMinutes = maxTotalFeedMinutes,
                sessionId = session.id,
                currentSide = session.startingSide.name,
                maxPerBreastMinutes = maxPerBreastMinutes
            )
        }
    }

    /**
     * After a resume, reschedule any notification that had not yet fired before the pause.
     *
     * The original trigger time for each notification type is:
     *   startTime + maxMinutes + previousPausedDurationMs
     *
     * If that trigger time is still in the future relative to session.pausedAt, the
     * notification hadn't fired yet — reschedule it for (now + remaining).
     *
     * [session] is the state *before* the resume use case cleared pausedAt, so
     * pausedAt is still set and pausedDurationMs reflects only previously accumulated pauses.
     */
    private fun rescheduleNotificationsAfterResume(session: BreastfeedingSession) {
        val pausedAt = session.pausedAt ?: return
        val maxPerBreast = _uiState.value.maxPerBreastMinutes
        val maxTotal = _uiState.value.maxTotalFeedMinutes

        if (maxPerBreast > 0) {
            val adjustedTrigger = session.startTime
                .plusSeconds(maxPerBreast * 60L)
                .plusMillis(session.pausedDurationMs)
            val remaining = Duration.between(pausedAt, adjustedTrigger)
            if (!remaining.isNegative && !remaining.isZero) {
                notificationScheduler.scheduleMaxPerBreastNotificationAt(
                    triggerTime = Instant.now().plus(remaining),
                    sessionId = session.id,
                    maxPerBreastMinutes = maxPerBreast,
                    currentSide = session.startingSide.name,
                    maxTotalMinutes = maxTotal
                )
            }
        }

        if (maxTotal > 0) {
            val adjustedTrigger = session.startTime
                .plusSeconds(maxTotal * 60L)
                .plusMillis(session.pausedDurationMs)
            val remaining = Duration.between(pausedAt, adjustedTrigger)
            if (!remaining.isNegative && !remaining.isZero) {
                notificationScheduler.scheduleMaxTotalTimeNotificationAt(
                    triggerTime = Instant.now().plus(remaining),
                    sessionId = session.id,
                    maxTotalMinutes = maxTotal,
                    currentSide = session.startingSide.name,
                    maxPerBreastMinutes = maxPerBreast
                )
            }
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
