package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRecommendationRepository
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.manager.PredictiveSleepScheduler
import com.babytracker.util.createPredictiveSleepNotificationChannel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class PredictiveSleepBootReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var predictSleepWindow: PredictSleepWindowUseCase
    @Inject lateinit var scheduler: PredictiveSleepScheduler
    @Inject lateinit var sleepRecommendationRepository: SleepRecommendationRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldHandle(intent.action)) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val failure = runCatching {
                    withTimeout(TIMEOUT_MS) { handle(context) }
                }.exceptionOrNull()
                when (failure) {
                    null -> Unit
                    is TimeoutCancellationException -> Log.e(TAG, "onReceive timed out", failure)
                    is CancellationException -> throw failure
                    else -> Log.e(TAG, "onReceive failed", failure)
                }
            } finally {
                result.finish()
            }
        }
    }

    internal fun shouldHandle(action: String?): Boolean = action in HANDLED_ACTIONS

    internal suspend fun handle(context: Context) {
        if (!settingsRepository.getPredictiveSleepEnabled().first()) return
        createPredictiveSleepNotificationChannel(context)
        val leadMinutes = settingsRepository.getPredictiveSleepLeadMinutes().first()
        val quietStart = settingsRepository.getQuietHoursStartMinute().first()
        val quietEnd = settingsRepository.getQuietHoursEndMinute().first()
        val state = predictSleepWindow().first()
        val window = (state as? SleepPredictionState.Window)?.window
        if (window == null) {
            scheduler.cancelPredictiveReminder()
            return
        }
        val now = Instant.now()
        val staleAfter = window.bestEstimate.plusSeconds(PredictiveSleepReceiver.MAX_STALE_MINUTES * 60)
        if (now.isAfter(staleAfter)) {
            scheduler.cancelPredictiveReminder()
            return
        }
        val triggerAt = window.bestEstimate.minus(Duration.ofMinutes(leadMinutes.toLong()))
        val effectiveTrigger = if (triggerAt.isBefore(now)) now.plusSeconds(IMMEDIATE_DELAY_SECONDS) else triggerAt
        if (PredictiveSleepReceiver.isInsideQuietHours(effectiveTrigger.toEpochMilli(), quietStart, quietEnd)) {
            scheduler.cancelPredictiveReminder()
            return
        }
        val recommendationId = sleepRecommendationRepository.getLatestScheduledRecommendation()?.id ?: 0L
        scheduler.schedulePredictiveReminderAt(effectiveTrigger, window.bestEstimate, recommendationId)
        Log.d(TAG, "Restored predictive sleep alarm at $triggerAt")
    }

    companion object {
        private const val TAG = "PredictiveSleepBoot"
        private const val TIMEOUT_MS = 10_000L
        private const val IMMEDIATE_DELAY_SECONDS = 5L
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
        )
    }
}
