package com.babytracker.manager

import com.babytracker.di.ApplicationScope
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.model.RecommendationOutcome
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.domain.usecase.sleep.SharedSleepPredictionStream
import com.babytracker.domain.usecase.sleep.SleepRecommendationUseCases
import com.babytracker.util.ScheduleDecision
import com.babytracker.util.decideSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PredictiveSleepNotificationCoordinator @Inject constructor(
    private val sharedSleepPrediction: SharedSleepPredictionStream,
    private val settingsRepository: SettingsRepository,
    private val sleepSettingsRepository: SleepSettingsRepository,
    private val scheduler: PredictiveSleepScheduler,
    private val sleepRepository: SleepRepository,
    private val recommendation: SleepRecommendationUseCases,
    private val featureToggleRepository: FeatureToggleRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    private var activeRecommendationId: Long? = null
    private var activeAnchorId: Long? = null
    // Only set when alarm is actually scheduled — guards ACTED_* feedback from past-trigger/quiet-hours paths.
    private var scheduledWindowStart: Instant? = null
    private var scheduledWindowEnd: Instant? = null
    private var scheduledBestEstimate: Instant? = null
    private var quietHoursFeedbackCreated: Boolean = false
    private var lastSeenCompletedSleepId: Long? = null

    @OptIn(FlowPreview::class)
    fun start() {
        applicationScope.launch {
            combine(
                combine(
                    sharedSleepPrediction.observe(),
                    sleepSettingsRepository.getPredictiveSleepEnabled(),
                    sleepSettingsRepository.getPredictiveSleepLeadMinutes(),
                    settingsRepository.getQuietHoursStartMinute(),
                    settingsRepository.getQuietHoursEndMinute(),
                ) { state, enabled, lead, quietStart, quietEnd ->
                    ReconcileParams(state, enabled, lead, quietStart, quietEnd)
                },
                featureToggleRepository.getEnabledFeatures(),
            ) { params, features ->
                params.copy(enabled = params.enabled && AppFeature.SLEEP in features)
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

        applicationScope.launch {
            sleepRepository.observeLatestRecord().collect { record ->
                val completed = record?.takeIf { !it.isInProgress } ?: return@collect
                if (completed.id == lastSeenCompletedSleepId) return@collect
                lastSeenCompletedSleepId = completed.id

                val recId = activeRecommendationId ?: return@collect
                val winStart = scheduledWindowStart ?: return@collect
                val winEnd = scheduledWindowEnd ?: return@collect
                val bestEst = scheduledBestEstimate ?: return@collect

                val outcome = if (completed.startTime in winStart..winEnd) {
                    RecommendationOutcome.ACTED_IN_WINDOW
                } else {
                    RecommendationOutcome.ACTED_OUTSIDE
                }
                recommendation.createFeedback(
                    recommendationId = recId,
                    outcome = outcome,
                    actualSleepRecordId = completed.id,
                    sleepStartTime = completed.startTime,
                    windowBestEstimate = bestEst,
                )
            }
        }
    }

    private suspend fun reconcile(
        state: SleepPredictionState,
        enabled: Boolean,
        leadMinutes: Int,
        quietStartMinute: Int,
        quietEndMinute: Int,
    ) {
        val window = (state as? SleepPredictionState.Window)?.window
        if (!enabled || window == null) {
            supersedeCurrent()
            scheduler.cancelPredictiveReminder()
            return
        }

        val anchorId = sleepRepository.getLatestRecord()?.id ?: run {
            supersedeCurrent()
            scheduler.cancelPredictiveReminder()
            return
        }

        if (anchorId != activeAnchorId) {
            supersedeCurrent()
            quietHoursFeedbackCreated = false
        }

        val recId = recommendation.persist(anchorId, window)

        activeRecommendationId = recId
        activeAnchorId = anchorId

        val decision = decideSchedule(
            now = Instant.now(),
            bestEstimate = window.bestEstimate,
            leadMinutes = leadMinutes,
            quietStartMinute = quietStartMinute,
            quietEndMinute = quietEndMinute,
        )
        when (decision) {
            ScheduleDecision.PastTrigger -> {
                clearScheduledWindowState()
                scheduler.cancelPredictiveReminder()
            }
            ScheduleDecision.QuietHours -> {
                if (!quietHoursFeedbackCreated) {
                    recommendation.createFeedback(recId, RecommendationOutcome.QUIET_HOURS_SUPPRESSED)
                    quietHoursFeedbackCreated = true
                }
                clearScheduledWindowState()
                scheduler.cancelPredictiveReminder()
            }
            is ScheduleDecision.Schedule -> {
                scheduledWindowStart = window.windowStart
                scheduledWindowEnd = window.windowEnd
                scheduledBestEstimate = window.bestEstimate
                quietHoursFeedbackCreated = false

                scheduler.schedulePredictiveReminderAt(decision.triggerAt, window.bestEstimate, recId)
                recommendation.repository.updateLifecycle(recId, RecommendationLifecycle.SCHEDULED)
            }
        }
    }

    private suspend fun supersedeCurrent() {
        val id = activeRecommendationId ?: return
        recommendation.repository.updateLifecycle(id, RecommendationLifecycle.SUPERSEDED)
        recommendation.createFeedback(id, RecommendationOutcome.SUPERSEDED)
        activeRecommendationId = null
        activeAnchorId = null
        clearScheduledWindowState()
        quietHoursFeedbackCreated = false
    }

    private fun clearScheduledWindowState() {
        scheduledWindowStart = null
        scheduledWindowEnd = null
        scheduledBestEstimate = null
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
