package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.PredictionTuning
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class CountRecentValidIntervalsUseCase @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val settingsRepository: SettingsRepository,
    private val zoneId: ZoneId,
) {
    operator fun invoke(): Flow<Int> = combine(
        breastfeedingRepository.getAllSessions()
            .map { it.take(PredictionTuning.LOOKBACK_LIMIT) },
        settingsRepository.getQuietHoursStartMinute(),
        settingsRepository.getQuietHoursEndMinute(),
    ) { sessions, qhStart, qhEnd ->
        countValidIntervals(sessions, qhStart, qhEnd)
    }.catch { emit(0) }

    private fun countValidIntervals(
        sessions: List<BreastfeedingSession>,
        qhStart: Int,
        qhEnd: Int,
    ): Int {
        val sortedDesc = sessions.filter { it.endTime != null }.sortedByDescending { it.startTime }

        val filtered = sortedDesc
            .zipWithNext { newer, older ->
                val minutes = Duration.between(older.startTime, newer.startTime).toMinutes().toInt()
                Triple(older.startTime, newer.startTime, minutes)
            }
            .filter { (_, _, minutes) -> minutes <= PredictionTuning.INTERVAL_MAX_MINUTES }
            .filter { (endpointA, endpointB, _) ->
                !endpointInQuietHours(endpointA, qhStart, qhEnd) &&
                    !endpointInQuietHours(endpointB, qhStart, qhEnd)
            }

        return filtered.take(PredictionTuning.SAMPLE_SIZE_TARGET).size
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
}
