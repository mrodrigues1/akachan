package com.babytracker.manager

import com.babytracker.di.ApplicationScope
import com.babytracker.domain.model.FeedPrediction
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PredictiveFeedNotificationCoordinator @Inject constructor(
    private val predictNextFeed: PredictNextFeedUseCase,
    private val settingsRepository: SettingsRepository,
    private val scheduler: PredictiveFeedScheduler,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    @OptIn(FlowPreview::class)
    fun start() {
        applicationScope.launch {
            combine(
                predictNextFeed(),
                settingsRepository.getPredictiveEnabled(),
                settingsRepository.getPredictiveLeadMinutes(),
            ) { prediction, enabled, lead -> Triple(prediction, enabled, lead) }
                .debounce(DEBOUNCE_MS)
                .collect { (prediction, enabled, lead) -> reconcile(prediction, enabled, lead) }
        }
    }

    private fun reconcile(prediction: FeedPrediction?, enabled: Boolean, leadMinutes: Int) {
        if (!enabled || prediction == null) {
            scheduler.cancelPredictiveReminder()
            return
        }
        val triggerAt = prediction.predictedAt.minus(Duration.ofMinutes(leadMinutes.toLong()))
        if (triggerAt.isBefore(Instant.now())) {
            scheduler.cancelPredictiveReminder()
            return
        }
        scheduler.schedulePredictiveReminderAt(triggerAt, prediction.predictedAt)
    }

    companion object {
        private const val DEBOUNCE_MS = 200L
    }
}
