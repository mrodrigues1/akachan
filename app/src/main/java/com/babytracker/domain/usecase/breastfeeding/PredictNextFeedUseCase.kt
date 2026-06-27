package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedPrediction
import com.babytracker.domain.model.PredictionTuning
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class PredictNextFeedUseCase @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {

    operator fun invoke(): Flow<FeedPrediction?> {
        // Bounded query instead of getAllSessions().map { take(LIMIT) }: the DESC LIMIT returns the
        // same newest LOOKBACK_LIMIT rows without loading/mapping the entire table on every emission.
        val recentSessionsFlow: Flow<List<BreastfeedingSession>> =
            breastfeedingRepository.getRecentSessionsFlow(PredictionTuning.LOOKBACK_LIMIT)

        return combine(
            recentSessionsFlow,
            settingsRepository.getQuietHoursStartMinute(),
            settingsRepository.getQuietHoursEndMinute(),
        ) { sessions, qhStart, qhEnd ->
            predict(sessions, qhStart, qhEnd)
        }.catch {
            emit(null)
        }
    }

    private fun predict(
        sessions: List<BreastfeedingSession>,
        quietStartMinute: Int,
        quietEndMinute: Int,
    ): FeedPrediction? {
        if (sessions.any { it.endTime == null }) return null
        val now = Instant.now(clock)
        val sortedDesc = sessions.sortedByDescending { it.startTime }
        val mostRecent = sortedDesc.firstOrNull() ?: return null

        if (Duration.between(mostRecent.startTime, now).toHours()
            >= PredictionTuning.FRESHNESS_HORIZON_HOURS
        ) return null

        val taken = validFeedIntervals(sortedDesc, zoneId, quietStartMinute, quietEndMinute)
        if (taken.size < PredictionTuning.SAMPLE_SIZE_MIN) return null

        return buildPrediction(mostRecent.startTime, taken, now)
    }

    private fun buildPrediction(
        mostRecentStart: Instant,
        taken: List<IntervalSample>,
        now: Instant,
    ): FeedPrediction? {
        val avg = taken.map { it.minutes }.average().toInt()
        val predictedAt = mostRecentStart.plus(Duration.ofMinutes(avg.toLong()))
        val isOverdue = now.isAfter(predictedAt)
        val minutesPast = Duration.between(predictedAt, now).toMinutes()
        if (isOverdue && minutesPast > PredictionTuning.OVERDUE_GRACE_MINUTES) return null
        val minutesUntil = Duration.between(now, predictedAt).toMinutes().toInt()
        return FeedPrediction(
            predictedAt = predictedAt,
            averageIntervalMinutes = avg,
            sampleSize = taken.size,
            isOverdue = isOverdue,
            minutesUntil = minutesUntil,
        )
    }
}
