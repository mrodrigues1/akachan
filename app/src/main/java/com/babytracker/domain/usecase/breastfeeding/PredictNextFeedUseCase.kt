package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedPrediction
import com.babytracker.domain.model.PredictionTuning
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
        val recentSessionsFlow: Flow<List<BreastfeedingSession>> =
            breastfeedingRepository.getAllSessions()
                .map { it.take(PredictionTuning.LOOKBACK_LIMIT) }

        return combine(
            recentSessionsFlow,
            settingsRepository.getQuietHoursStartMinute(),
            settingsRepository.getQuietHoursEndMinute(),
        ) { sessions, qhStart, qhEnd ->
            predict(sessions, qhStart, qhEnd)
        }.catch { e ->
            android.util.Log.e("PredictNextFeed", "Prediction failed", e)
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

        val rawIntervals = sortedDesc.zipWithNext { newer, older ->
            IntervalSample(
                endpointA = older.startTime,
                endpointB = newer.startTime,
                minutes = Duration.between(older.startTime, newer.startTime).toMinutes().toInt(),
            )
        }

        val filtered = rawIntervals
            .filter { it.minutes <= PredictionTuning.INTERVAL_MAX_MINUTES }
            .filter { !endpointInQuietHours(it.endpointA, quietStartMinute, quietEndMinute) }
            .filter { !endpointInQuietHours(it.endpointB, quietStartMinute, quietEndMinute) }

        val taken = filtered.take(PredictionTuning.SAMPLE_SIZE_TARGET)
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

    private fun endpointInQuietHours(endpoint: Instant, startMinute: Int, endMinute: Int): Boolean {
        if (startMinute == endMinute) return false
        val localMinute = endpoint.atZone(zoneId).toLocalTime().toSecondOfDay() / 60
        return if (startMinute < endMinute) {
            localMinute in startMinute until endMinute
        } else {
            localMinute >= startMinute || localMinute <= endMinute
        }
    }

    private data class IntervalSample(
        val endpointA: Instant,
        val endpointB: Instant,
        val minutes: Int,
    )
}
