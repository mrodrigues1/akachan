package com.babytracker.manager

import android.app.NotificationManager
import android.content.Context
import com.babytracker.di.ApplicationScope
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PredictiveSleepNotificationCoordinator @Inject constructor(
    private val predictSleepWindow: PredictSleepWindowUseCase,
    private val settingsRepository: SettingsRepository,
    private val scheduler: PredictiveSleepScheduler,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    @OptIn(FlowPreview::class)
    fun start() {
        applicationScope.launch {
            combine(
                predictSleepWindow(),
                settingsRepository.getPredictiveSleepEnabled(),
                settingsRepository.getPredictiveSleepLeadMinutes(),
                settingsRepository.getQuietHoursStartMinute(),
                settingsRepository.getQuietHoursEndMinute(),
            ) { state, enabled, lead, quietStart, quietEnd ->
                ReconcileParams(state, enabled, lead, quietStart, quietEnd)
            }
                .debounce(DEBOUNCE_MS)
                .collect { params ->
                    reconcile(
                        params.state,
                        params.enabled,
                        params.leadMinutes,
                        params.quietStartMinute,
                        params.quietEndMinute,
                    )
                }
        }
    }

    private fun reconcile(
        state: SleepPredictionState,
        enabled: Boolean,
        leadMinutes: Int,
        quietStartMinute: Int,
        quietEndMinute: Int,
    ) {
        val window = (state as? SleepPredictionState.Window)?.window
        if (!enabled || window == null) {
            cancelAll()
            return
        }
        val triggerAt = window.bestEstimate.minus(Duration.ofMinutes(leadMinutes.toLong()))
        if (triggerAt.isBefore(Instant.now())) {
            cancelAll()
            return
        }
        if (isInQuietHours(triggerAt, quietStartMinute, quietEndMinute, ZoneId.systemDefault())) {
            cancelAll()
            return
        }
        scheduler.schedulePredictiveReminderAt(triggerAt, window.bestEstimate)
    }

    private fun cancelAll() {
        scheduler.cancelPredictiveReminder()
        context.getSystemService(NotificationManager::class.java)
            .cancel(NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID)
    }

    private data class ReconcileParams(
        val state: SleepPredictionState,
        val enabled: Boolean,
        val leadMinutes: Int,
        val quietStartMinute: Int,
        val quietEndMinute: Int,
    )

    companion object {
        private const val DEBOUNCE_MS = 200L
    }
}
