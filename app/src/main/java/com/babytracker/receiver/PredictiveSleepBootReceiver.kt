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
import com.babytracker.manager.SleepNotificationScheduler
import com.babytracker.util.NotificationHelper
import com.babytracker.util.createPredictiveSleepNotificationChannel
import com.babytracker.util.goAsyncWithTimeout
import com.babytracker.util.isInQuietHours
import com.babytracker.util.isPredictionStale
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Restores everything sleep-related that a reboot wipes — the ongoing notification for an
 * in-progress sleep session — and re-arms the predictive-sleep alarm, which a wall-clock change
 * can also invalidate. Both actions are driven off the same
 * BOOT_COMPLETED/TIME_SET/TIMEZONE_CHANGED intents that
 * [com.babytracker.receiver.BreastfeedingBootReceiver] listens to, so this stayed a single
 * receiver rather than splitting into a sibling. Notification restoration is scoped to
 * BOOT_COMPLETED only (see [handle]) — TIME_SET/TIMEZONE_CHANGED do not clear an already-posted
 * notification, so reposting one there would only reset its chronometer against the new wall
 * clock for no reason.
 */
@AndroidEntryPoint
class PredictiveSleepBootReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var sleepSettingsRepository: SleepSettingsRepository
    @Inject lateinit var predictSleepWindow: PredictSleepWindowUseCase
    @Inject lateinit var scheduler: PredictiveSleepScheduler
    @Inject lateinit var sleepRepository: SleepRepository
    @Inject lateinit var sleepRecommendationRepository: SleepRecommendationRepository
    @Inject lateinit var featureToggleRepository: FeatureToggleRepository
    @Inject lateinit var sleepNotificationScheduler: SleepNotificationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldHandle(intent.action)) return
        goAsyncWithTimeout(TAG) { handle(context, intent.action) }
    }

    internal fun shouldHandle(action: String?): Boolean = action in HANDLED_ACTIONS

    internal suspend fun handle(context: Context, action: String? = Intent.ACTION_BOOT_COMPLETED) {
        // The SLEEP feature toggle (not the predictive-sleep setting) gates both actions below:
        // hiding the whole sleep tracker means neither its ongoing-session notification nor its
        // predictive alarm should be restored. Unlike BreastfeedingBootReceiver — which restores
        // the feeding notification unconditionally, with no BREASTFEEDING feature-toggle check —
        // sleep already has a feature-toggle precedent to be consistent with: the predictive-sleep
        // coordinator and receivers (see the SLEEP-toggle fix landed alongside this one) all gate
        // on AppFeature.SLEEP. Restoring a hidden feature's notification here would be the one
        // inconsistent case, so this intentionally does not mirror the feed precedent.
        val sleepFeatureEnabled = AppFeature.SLEEP in featureToggleRepository.getEnabledFeatures().first()
        // Only a real reboot wipes an already-posted notification and kills the process holding
        // it. TIME_SET/TIMEZONE_CHANGED fire without touching existing notifications, so reposting
        // there would just recompute the chronometer off the new wall clock and visibly jump it —
        // a discontinuity the still-running notification's elapsedRealtime-based chronometer never
        // has on its own.
        if (sleepFeatureEnabled && action == Intent.ACTION_BOOT_COMPLETED) {
            restoreActiveSleepNotification(context)
        }

        val predictiveSleepEnabled = sleepSettingsRepository.getPredictiveSleepEnabled().first()
        if (!predictiveSleepEnabled || !sleepFeatureEnabled) return
        restorePredictiveAlarm(context)
    }

    private suspend fun restoreActiveSleepNotification(context: Context) {
        val active = sleepRepository.getActiveRecord() ?: return
        NotificationHelper.createSleepNotificationChannel(context)
        runCatching { sleepNotificationScheduler.show(active.id, active.sleepType, active.startTime) }
        Log.d(TAG, "Restored active sleep notification ${active.id}")
    }

    private suspend fun restorePredictiveAlarm(context: Context) {
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
