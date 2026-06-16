package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Deterministic synthetic sleep cohorts for the Phase-6 "personalization rebalance" eval gate
 * (AKA-153). Design: `docs/superpowers/specs/2026-06-16-sleep-prediction-personalization-design.md`.
 *
 * Three babies, each with stable, well-logged sleep that passes the quality gate and personalizes
 * (type-specific wake intervals → high qualityC). They differ only in how their real wake windows
 * sit relative to the age-prior midpoints (nap ≈ 120 min, pre-bedtime ≈ 150 min for the [16,24)w band):
 *
 *  - **short-wake** — wake windows well *below* the prior (nap 75 / bedtime 105 min). Reproduces the
 *    user complaint: the prior + heuristic factors drag the predicted window *later* than the baby
 *    actually sleeps.
 *  - **typical** — wake windows *on* the prior (nap 120 / bedtime 150 min). Regression guard.
 *  - **long-wake** — wake windows *above* the prior (nap 165 / bedtime 195 min). Opposite-direction
 *    regression guard (blend cap pulls the prediction earlier than the baby actually sleeps).
 *
 * ### Calendar-aligned construction
 *
 * Records are anchored to real UTC calendar days so `napCountToday` and bedtime routing stay correct
 * (avoids the non-24h-cycle nap-miscount seen in [PersonalizedWakeEvalComparisonTest]). Each day:
 *
 * ```
 * night (ends 05:00) → [7h morning gap, FILTERED >6h] → nap1 (12:00) → napWake → nap2 → bedtimeWake → night
 * ```
 *
 * The morning gap is intentionally > [SleepPredictionTuning.MAX_PLAUSIBLE_WAKE_INTERVAL_HOURS] for
 * every cohort, so it is dropped from wake-interval stats and excluded from scoring via
 * [Cohort.actualOnsetByWake]. The two controlled intervals — `nap1→nap2` (NAP) and `nap2→night`
 * (NIGHT) — are the clean prediction targets, one of each per day.
 *
 * The whole 49-day span stays inside the [16,24)w age band (constant priors, constant circadian ramp),
 * so every scored anchor shares one segment per type.
 */
object SleepPredictionCohorts {

    val ZONE: ZoneId = ZoneOffset.UTC

    /** Baby age on the first fixture day; span keeps every anchor inside the [16,24)w band. */
    const val AGE_WEEKS_AT_START = 16
    const val DAYS = 49

    /** Warm-up days whose anchors lack a full 14-day lookback; excluded from scoring. */
    val WARMUP_DAYS: Int = SleepPredictionTuning.LOOKBACK_DAYS.toInt()

    private const val NAP_SLEEP_MIN = 75L
    private const val MORNING_WAKE_MIN = 5 * 60L // 05:00 — night end
    private const val NAP1_START_MIN = 12 * 60L  // 12:00 — fixes the morning gap at 7h (> 6h) for all cohorts
    private const val MAX_LEGIT_WAKE_MILLIS =
        SleepPredictionTuning.MAX_PLAUSIBLE_WAKE_INTERVAL_HOURS * 60L * 60L * 1000L

    /** Age-band [16,24): nap-wake prior midpoint = 120 min, pre-bedtime prior = 150 min. */
    val SHORT_WAKE = CohortParams("short-wake", napWakeMin = 75L, bedtimeWakeMin = 105L)
    val TYPICAL = CohortParams("typical", napWakeMin = 120L, bedtimeWakeMin = 150L)
    val LONG_WAKE = CohortParams("long-wake", napWakeMin = 165L, bedtimeWakeMin = 195L)

    data class CohortParams(
        val label: String,
        val napWakeMin: Long,
        val bedtimeWakeMin: Long,
    )

    data class Cohort(
        val params: CohortParams,
        val baby: Baby,
        val records: List<SleepRecord>,
        val zone: ZoneId,
        val evaluatedAt: Instant,
        val scoredRangeStart: Instant,
    ) {
        /**
         * Actual sleep onset following each wake instant, restricted to legitimate wake windows
         * (gap ≤ 6h). Anchors whose wake instant is absent here are the filtered morning gaps and
         * must be excluded from scoring.
         */
        val actualOnsetByWake: Map<Instant, Instant> = buildOnsetMap(records)

        /** True when [wake] is a scored anchor: past warm-up and a legitimate (≤6h) wake window. */
        fun isScoredWake(wake: Instant): Boolean =
            wake >= scoredRangeStart && actualOnsetByWake.containsKey(wake)
    }

    fun all(): List<Cohort> = listOf(cohort(SHORT_WAKE), cohort(TYPICAL), cohort(LONG_WAKE))

    fun cohort(params: CohortParams): Cohort {
        val startDate = LocalDate.of(2024, 1, 1)
        val birthDate = startDate.minusWeeks(AGE_WEEKS_AT_START.toLong())
        val evaluatedAt = at(startDate.plusDays(DAYS.toLong()), MORNING_WAKE_MIN + 60L)

        var id = 1L
        val records = mutableListOf<SleepRecord>()
        for (i in 0 until DAYS) {
            val date = startDate.plusDays(i.toLong())

            val nap1Start = at(date, NAP1_START_MIN)
            val nap1End = nap1Start.plus(Duration.ofMinutes(NAP_SLEEP_MIN))
            records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)

            val nap2Start = nap1End.plus(Duration.ofMinutes(params.napWakeMin))
            val nap2End = nap2Start.plus(Duration.ofMinutes(NAP_SLEEP_MIN))
            records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)

            val nightStart = nap2End.plus(Duration.ofMinutes(params.bedtimeWakeMin))
            val nightEnd = at(startDate.plusDays((i + 1).toLong()), MORNING_WAKE_MIN)
            records += SleepRecord(id++, nightStart, nightEnd, SleepType.NIGHT_SLEEP)
        }

        val sorted = records
            .filter { it.endTime != null && it.startTime.isBefore(evaluatedAt) }
            .sortedBy { it.startTime }
        val scoredRangeStart = sorted.first().startTime.plus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))

        return Cohort(
            params = params,
            baby = Baby(name = "Cohort-${params.label}", birthDate = birthDate),
            records = sorted,
            zone = ZONE,
            evaluatedAt = evaluatedAt,
            scoredRangeStart = scoredRangeStart,
        )
    }

    private fun buildOnsetMap(sorted: List<SleepRecord>): Map<Instant, Instant> {
        val map = LinkedHashMap<Instant, Instant>()
        for (k in sorted.indices) {
            val wake = sorted[k].endTime ?: continue
            val next = sorted.getOrNull(k + 1) ?: break
            val gapMillis = next.startTime.toEpochMilli() - wake.toEpochMilli()
            if (gapMillis in 0..MAX_LEGIT_WAKE_MILLIS) {
                map[wake] = next.startTime
            }
        }
        return map
    }

    private fun at(date: LocalDate, minutesFromMidnight: Long): Instant =
        date.atStartOfDay(ZONE).toInstant().plus(Duration.ofMinutes(minutesFromMidnight))
}
