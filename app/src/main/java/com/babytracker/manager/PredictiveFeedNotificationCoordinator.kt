package com.babytracker.manager

import com.babytracker.di.ApplicationScope
import com.babytracker.domain.model.FeedPrediction
import com.babytracker.domain.repository.FeedSettingsRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCase
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

internal fun isInQuietHours(
    instant: Instant,
    quietStartMinute: Int,
    quietEndMinute: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): Boolean {
    if (quietStartMinute == quietEndMinute) return false
    val localTime = instant.atZone(zone).toLocalTime()
    val minuteOfDay = localTime.hour * 60 + localTime.minute
    return if (quietStartMinute < quietEndMinute) {
        minuteOfDay in quietStartMinute until quietEndMinute
    } else {
        minuteOfDay >= quietStartMinute || minuteOfDay < quietEndMinute
    }
}

@Singleton
class PredictiveFeedNotificationCoordinator @Inject constructor(
    private val predictNextFeed: PredictNextFeedUseCase,
    private val feedSettingsRepository: FeedSettingsRepository,
    // Quiet hours are shared with predictive sleep, so they stay on SettingsRepository.
    private val settingsRepository: SettingsRepository,
    private val scheduler: PredictiveFeedScheduler,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    @OptIn(FlowPreview::class)
    fun start() {
        applicationScope.launch {
            combine(
                predictNextFeed(),
                feedSettingsRepository.getPredictiveEnabled(),
                feedSettingsRepository.getPredictiveLeadMinutes(),
                settingsRepository.getQuietHoursStartMinute(),
                settingsRepository.getQuietHoursEndMinute(),
            ) { prediction, enabled, lead, quietStart, quietEnd ->
                ReconcileParams(prediction, enabled, lead, quietStart, quietEnd)
            }
                .debounce(DEBOUNCE_MS)
                .collect { params ->
                    reconcile(
                        params.prediction,
                        params.enabled,
                        params.leadMinutes,
                        params.quietStartMinute,
                        params.quietEndMinute,
                    )
                }
        }
    }

    private fun reconcile(
        prediction: FeedPrediction?,
        enabled: Boolean,
        leadMinutes: Int,
        quietStartMinute: Int,
        quietEndMinute: Int,
    ) {
        if (!enabled || prediction == null) {
            scheduler.cancelPredictiveReminder()
            return
        }
        val triggerAt = prediction.predictedAt.minus(Duration.ofMinutes(leadMinutes.toLong()))
        if (triggerAt.isBefore(Instant.now())) {
            scheduler.cancelPredictiveReminder()
            return
        }
        if (isInQuietHours(triggerAt, quietStartMinute, quietEndMinute, ZoneId.systemDefault())) {
            scheduler.cancelPredictiveReminder()
            return
        }
        scheduler.schedulePredictiveReminderAt(triggerAt, prediction.predictedAt)
    }

    private data class ReconcileParams(
        val prediction: FeedPrediction?,
        val enabled: Boolean,
        val leadMinutes: Int,
        val quietStartMinute: Int,
        val quietEndMinute: Int,
    )

    companion object {
        private const val DEBOUNCE_MS = 200L
    }
}
