package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.PredictionTuning
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    ): Int = validFeedIntervals(
        sessions = sessions.filter { it.endTime != null },
        zoneId = zoneId,
        quietStartMinute = qhStart,
        quietEndMinute = qhEnd,
    ).size
}
