package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRecommendationRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.manager.PredictiveSleepScheduler
import com.babytracker.util.createPredictiveSleepNotificationChannel
import com.babytracker.util.goAsyncWithTimeout
import com.babytracker.util.isInQuietHours
import com.babytracker.util.isPredictionStale
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class PredictiveSleepBootReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var sleepSettingsRepository: SleepSettingsRepository
    @Inject lateinit var predictSleepWindow: PredictSleepWindowUseCase
    @Inject lateinit var scheduler: PredictiveSleepScheduler
    @Inject lateinit var sleepRepository: SleepRepository
    @Inject lateinit var sleepRecommendationRepository: SleepRecommendationRepository
    @Inject lateinit var featureToggleRepository: FeatureToggleRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldHandle(intent.action)) return
        goAsyncWithTimeout(TAG) { handle(context) }
    }

    internal fun shouldHandle(action: String?): Boolean = action in HANDLED_ACTIONS

    internal suspend fun handle(context: Context) {
        val predictiveSleepEnabled = sleepSettingsRepository.getPredictiveSleepEnabled().first()
        val sleepFeatureEnabled = AppFeature.SLEEP in featureToggleRepository.getEnabledFeatures().first()
        if (!predictiveSleepEnabled || !sleepFeatureEnabled) return
        createPredictiveSleepNotificationChannel(context)
        val leadMinutes = sleepSettingsRepository.getPredictiveSleepLeadMinutes().first()
        val quietStart = settingsRepository.getQuietHoursStartMinute().first()
        val quietEnd = settingsRepository.getQuietHoursEndMinute().first()
        val state = predictSleepWindow().first()
        val window = (state as? SleepPredictionState.Window)?.window
        if (window == null) {
            scheduler.cancelPredictiveReminder()
            return
        }
        val now = Instant.now()
        if (isPredictionStale(now, window.bestEstimate)) {
            scheduler.cancelPredictiveReminder()
            return
        }
        val triggerAt = window.bestEstimate.minus(Duration.ofMinutes(leadMinutes.toLong()))
        val effectiveTrigger = if (triggerAt.isBefore(now)) now.plusSeconds(IMMEDIATE_DELAY_SECONDS) else triggerAt
        if (isInQuietHours(effectiveTrigger, quietStart, quietEnd)) {
            scheduler.cancelPredictiveReminder()
            return
        }
        val currentAnchorId = sleepRepository.getLatestRecord()?.id
        val snapshot = sleepRecommendationRepository.getLatestScheduledRecommendation()
        val recommendationId = if (snapshot != null && snapshot.anchorSleepId == currentAnchorId) snapshot.id else 0L
        scheduler.schedulePredictiveReminderAt(effectiveTrigger, window.bestEstimate, recommendationId)
        Log.d(TAG, "Restored predictive sleep alarm at $triggerAt")
    }

    companion object {
        private const val TAG = "PredictiveSleepBoot"
        private const val IMMEDIATE_DELAY_SECONDS = 5L
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
