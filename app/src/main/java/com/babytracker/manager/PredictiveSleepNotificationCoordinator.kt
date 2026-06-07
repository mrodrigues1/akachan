package com.babytracker.manager

import com.babytracker.di.ApplicationScope
import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.model.RecommendationOutcome
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.domain.usecase.sleep.SleepRecommendationUseCases
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
    private val sleepRepository: SleepRepository,
    private val recommendation: SleepRecommendationUseCases,
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

        val recommendationType = deriveRecommendationType(window.bestEstimate)
        val recId = recommendation.persist(anchorId, window, recommendationType)

        activeRecommendationId = recId
        activeAnchorId = anchorId

        val triggerAt = window.bestEstimate.minus(Duration.ofMinutes(leadMinutes.toLong()))
        if (triggerAt.isBefore(Instant.now())) {
            clearScheduledWindowState()
            scheduler.cancelPredictiveReminder()
            return
        }
        if (isInQuietHours(triggerAt, quietStartMinute, quietEndMinute, ZoneId.systemDefault())) {
            if (!quietHoursFeedbackCreated) {
                recommendation.createFeedback(recId, RecommendationOutcome.QUIET_HOURS_SUPPRESSED)
                quietHoursFeedbackCreated = true
            }
            clearScheduledWindowState()
            scheduler.cancelPredictiveReminder()
            return
        }

        scheduledWindowStart = window.windowStart
        scheduledWindowEnd = window.windowEnd
        scheduledBestEstimate = window.bestEstimate
        quietHoursFeedbackCreated = false

        scheduler.schedulePredictiveReminderAt(triggerAt, window.bestEstimate)
        recommendation.updateLifecycle(recId, RecommendationLifecycle.SCHEDULED)
    }

    private suspend fun supersedeCurrent() {
        val id = activeRecommendationId ?: return
        recommendation.updateLifecycle(id, RecommendationLifecycle.SUPERSEDED)
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

    private fun deriveRecommendationType(bestEstimate: Instant): String =
        if (bestEstimate.atZone(ZoneId.systemDefault()).hour < 18) "NAP" else "NIGHT_SLEEP"

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
