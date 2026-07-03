package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BabyEvent
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.PredictionTuning
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.repository.BabyEventRepository
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.sleep.eval.CircadianBiasFactor
import com.babytracker.domain.sleep.eval.NapBudgetFactor
import com.babytracker.domain.sleep.eval.SleepDebtFactor
import com.babytracker.domain.sleep.eval.SleepWindowPredictor
import com.babytracker.domain.sleep.feature.SleepFeatureExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Provider

class PredictSleepWindowUseCase @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val breastfeedingRepository: BreastfeedingRepository,
    private val babyRepository: BabyRepository,
    private val babyEventRepository: BabyEventRepository,
    private val clock: Clock,
    // Provider so each prediction re-reads the device zone; a captured ZoneId would freeze the
    // zone for the life of the (singleton-held) use case across travel/DST changes.
    private val zoneIdProvider: Provider<ZoneId>,
) {
    operator fun invoke(): Flow<SleepPredictionState> = flow {
        // Query-level cutoffs, frozen at flow start. They only ever trail the real lookback bounds,
        // so the queries over-fetch; the fresh per-evaluation bounds are applied in predictForBaby().
        val flowStart = Instant.now(clock)
        val disruptionCutoff = flowStart.minus(Duration.ofHours(SleepPredictionTuning.DISRUPTION_LOOKBACK_HOURS))
        val recordsCutoff = flowStart.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
        emitAll(
            combine(
                sleepRepository.getRecordsSinceFlow(recordsCutoff),
                // The predictor only reads feeds within the freshness horizon (hours), so the
                // LOOKBACK_LIMIT most recent sessions are more than enough.
                breastfeedingRepository.getRecentSessionsFlow(PredictionTuning.LOOKBACK_LIMIT),
                babyRepository.getBabyProfile(),
                babyEventRepository.getEventsSince(disruptionCutoff),
                // Several states are functions of wall-clock time (Window -> Overdue after
                // windowEnd + grace, freshness horizon, open-feed/open-sleep caps) but the data
                // flows only emit on DB writes. The ticker re-runs the predictor so those
                // transitions surface on an open screen; distinctUntilChanged below keeps
                // downstream collectors (UI, notification coordinator) to real transitions only.
                minuteTicker(),
            ) { sleepRecords, feedSessions, baby, recentEvents, _ ->
                predict(sleepRecords, feedSessions, baby, recentEvents)
            }
        )
    }.distinctUntilChanged().flowOn(Dispatchers.Default).catch { e ->
        emit(SleepPredictionState.Unavailable(e.message ?: "prediction error"))
    }

    private fun minuteTicker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(RECOMPUTE_INTERVAL_MS)
        }
    }

    private fun predict(
        sleepRecords: List<SleepRecord>,
        feedSessions: List<BreastfeedingSession>,
        baby: Baby?,
        recentEvents: List<BabyEvent>,
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
        return predictForBaby(baby, sleepRecords, feedSessions, recentEvents, now)
    }

    private fun predictForBaby(
        baby: Baby,
        sleepRecords: List<SleepRecord>,
        feedSessions: List<BreastfeedingSession>,
        recentEvents: List<BabyEvent>,
        now: Instant,
    ): SleepPredictionState {
        val zoneId = zoneIdProvider.get()
        val today = now.atZone(zoneId).toLocalDate()
        val ageInWeeks = ChronoUnit.WEEKS.between(baby.birthDate, today).toInt()
        if (ageInWeeks < SleepPredictionTuning.CUE_LED_MAX_AGE_WEEKS) return SleepPredictionState.CueLed

        val lookbackStart = now.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
        // Fresh lower bound per evaluation: the flow-start query cutoff never advances, so without
        // this a long-lived collector would treat a days-old disruption as active forever.
        val disruptionCutoff = now.minus(Duration.ofHours(SleepPredictionTuning.DISRUPTION_LOOKBACK_HOURS))
        val hasActiveDisruption = recentEvents.any { event ->
            event.type.isDisruption && event.timestamp <= now && event.timestamp >= disruptionCutoff
        }

        val features = SleepFeatureExtractor(clock, zoneId)
            .extract(sleepRecords.filter { it.startTime >= lookbackStart }, feedSessions)
            .copy(hasActiveDisruption = hasActiveDisruption)

        return SleepWindowPredictor.predict(
            features,
            ageInWeeks,
            now,
            circadianFactorProvider = CircadianBiasFactor::adjustment,
            sleepDebtFactorProvider = SleepDebtFactor::adjustment,
            napBudgetFactorProvider = NapBudgetFactor::adjustment,
        )
    }

    private companion object {
        // ponytail: coarse per-minute recompute instead of scheduling the exact next
        // transition instant; switch to a delay-until-transition flow if the wakeups matter.
        const val RECOMPUTE_INTERVAL_MS = 60_000L
    }
}
