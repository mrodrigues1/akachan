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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class BreastfeedingUiState(
    val activeSession: BreastfeedingSession? = null,
    val selectedSide: BreastSide? = null,
    val maxPerBreastMinutes: Int = 0,
    val maxTotalFeedMinutes: Int = 0
)

@HiltViewModel
class BreastfeedingViewModel @Inject constructor(
    private val startSession: StartBreastfeedingSessionUseCase,
    private val stopSession: StopBreastfeedingSessionUseCase,
    private val switchSide: SwitchBreastfeedingSideUseCase,
    private val getHistory: GetBreastfeedingHistoryUseCase,
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
                    maxTotalFeedMinutes = maxTotal
                )
            }.collect { newState ->
                _uiState.value = newState
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
            repository.getActiveSession().collect { session ->
                session?.let {
                    scheduleNotifications(it)
                    return@collect
                }
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
        }
    }

    fun onSwitchSide() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            switchSide(session)
        }
    }

    fun onPauseSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            pauseSession(session)
            notificationScheduler.cancelAllScheduledNotifications()
        }
    }

    fun onResumeSession() {
        val session = _uiState.value.activeSession ?: return
        viewModelScope.launch {
            resumeSession(session)
            rescheduleNotificationsAfterResume(session)
        }
    }

    private fun scheduleNotifications(session: BreastfeedingSession) {
        val maxPerBreastMinutes = _uiState.value.maxPerBreastMinutes
        val maxTotalFeedMinutes = _uiState.value.maxTotalFeedMinutes

        if (maxPerBreastMinutes > 0) {
            notificationScheduler.scheduleMaxPerBreastNotification(
                session.startTime,
                maxPerBreastMinutes
            )
        }

        if (maxTotalFeedMinutes > 0) {
            notificationScheduler.scheduleMaxTotalTimeNotification(
                session.startTime,
                maxTotalFeedMinutes
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
                    Instant.now().plus(remaining)
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
                    Instant.now().plus(remaining)
                )
            }
        }
    }
}
