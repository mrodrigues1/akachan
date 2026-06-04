package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.sleep.eval.SleepWindowPredictor
import com.babytracker.domain.sleep.feature.SleepFeatureExtractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class PredictSleepWindowUseCase @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val breastfeedingRepository: BreastfeedingRepository,
    private val babyRepository: BabyRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    operator fun invoke(): Flow<SleepPredictionState> = flow {
        emitAll(
            combine(
                sleepRepository.getAllRecords(),
                breastfeedingRepository.getAllSessions(),
                babyRepository.getBabyProfile(),
            ) { sleepRecords, feedSessions, baby ->
                predict(sleepRecords, feedSessions, baby)
            }
        )
    }.catch { e ->
        emit(SleepPredictionState.Unavailable(e.message ?: "prediction error"))
    }

    private fun predict(
        sleepRecords: List<SleepRecord>,
        feedSessions: List<BreastfeedingSession>,
        baby: Baby?,
    ): SleepPredictionState {
        val now = Instant.now(clock)
        val maxOpenSleepAgeMillis = Duration.ofHours(SleepPredictionTuning.MAX_OPEN_SLEEP_AGE_HOURS).toMillis()
        val hasActiveSleep = sleepRecords.any { record ->
            val startMillis = record.startTime.toEpochMilli()
            val nowMillis = now.toEpochMilli()
            record.endTime == null && startMillis <= nowMillis && (nowMillis - startMillis) <= maxOpenSleepAgeMillis
        }
        if (hasActiveSleep) return SleepPredictionState.CurrentlySleeping
        baby ?: return SleepPredictionState.Unavailable("no baby profile")
        return predictForBaby(baby, sleepRecords, feedSessions, now)
    }

    private fun predictForBaby(
        baby: Baby,
        sleepRecords: List<SleepRecord>,
        feedSessions: List<BreastfeedingSession>,
        now: Instant,
    ): SleepPredictionState {
        val today = now.atZone(zoneId).toLocalDate()
        val ageInWeeks = ChronoUnit.WEEKS.between(baby.birthDate, today).toInt()
        if (ageInWeeks < SleepPredictionTuning.CUE_LED_MAX_AGE_WEEKS) return SleepPredictionState.CueLed

        val lookbackStart = now.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
        val features = SleepFeatureExtractor(clock, zoneId)
            .extract(sleepRecords.filter { it.startTime >= lookbackStart }, feedSessions)

        return SleepWindowPredictor.predict(features, ageInWeeks, now)
    }
}
