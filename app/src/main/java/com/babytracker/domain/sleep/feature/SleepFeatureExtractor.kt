package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.sleep.median
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class SleepFeatureExtractor(
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    // Snapshot the clock once per extractor instead of re-reading it for every list element in each
    // isPossibleAt(...) filter. Previously this getter ran clock.instant() hundreds of times per
    // extraction; reads are now a single field access. The pipeline builds a fresh extractor per
    // emission, and tests use a fixed Clock, so the snapshot is value-identical to the old getter.
    private val nowMillis: Long = clock.instant().toEpochMilli()

    fun extract(
        sleepRecords: List<SleepRecord>,
        feedSessions: List<BreastfeedingSession>,
    ): SleepFeatures {
        val validIntervals = buildSleepIntervals(sleepRecords)
        val feedIntervals = buildBreastfeedIntervals(feedSessions)
        val metrics = computeMetrics(validIntervals)
        val quality = computeQuality(validIntervals, sleepRecords.size, metrics)
        val localTime = clock.instant().atZone(zoneId).toLocalTime()
        val currentMinuteOfDay = localTime.hour * 60 + localTime.minute
        return SleepFeatures(validIntervals, feedIntervals, metrics, quality, currentMinuteOfDay)
    }

    fun buildSleepIntervals(records: List<SleepRecord>): List<SleepInterval> {
        val perRecordValid = records
            .mapNotNull { SleepInterval.from(it) }
            .filter { it.isPossibleAt(nowMillis) }
        val completed = perRecordValid
            .filter { it.isCompleted }
            .sortedBy { it.startMillis }
            .let { mergeOverlapping(it) }
        val latestOpen = perRecordValid
            .filter { !it.isCompleted }
            .maxByOrNull { it.startMillis }
        val nonOverlappingOpen = listOfNotNull(latestOpen)
            .filter { openInterval ->
                completed.none { completedInterval -> completedInterval.endMillis!! > openInterval.startMillis }
            }
        return (completed + nonOverlappingOpen).sortedBy { it.startMillis }
    }

    fun buildBreastfeedIntervals(sessions: List<BreastfeedingSession>): List<BreastfeedInterval> =
        sessions
            .mapNotNull { BreastfeedInterval.from(it) }
            .filter { it.isPossibleAt(nowMillis) }
            .sortedBy { it.startMillis }

    fun computeMetrics(intervals: List<SleepInterval>): SleepMetrics {
        val completed = intervals
            .filter { it.isCompleted && it.isPossibleAt(nowMillis) }
            .sortedBy { it.startMillis }
        val lastSleep = completed.maxByOrNull { it.endMillis!! }
        val wakeIntervals = completedWakeIntervals(completed)
        val today = clock.instant().atZone(zoneId).toLocalDate()
        val nightSleeps = completed.filter { it.sleepType == SleepType.NIGHT_SLEEP }
        val typeWakeIntervals = typeAwareWakeIntervals(completed)
        val napIntervals = typeWakeIntervals[SleepType.NAP] ?: emptyList()
        val bedtimeIntervals = typeWakeIntervals[SleepType.NIGHT_SLEEP] ?: emptyList()
        val napQuartiles = quartiles(napIntervals)
        val bedtimeQuartiles = quartiles(bedtimeIntervals)

        return SleepMetrics(
            lastWakeMillis = lastSleep?.endMillis,
            lastSleepType = lastSleep?.sleepType,
            completedWakeIntervals = wakeIntervals,
            medianWakeIntervalMillis = median(wakeIntervals),
            wakeIntervalIqrMillis = iqr(wakeIntervals),
            sleepLast24hMillis = sumOverlap(completed, nowMillis - Duration.ofHours(24).toMillis(), nowMillis),
            napCountToday = completed.count {
                it.sleepType == SleepType.NAP && Instant.ofEpochMilli(it.startMillis).atZone(it.zone()).toLocalDate() == today
            },
            medianBedtimeMinuteOfDay = circularMedian(nightSleeps.map { startMinuteOfDay(it) }),
            napWakeIntervalCount = napIntervals.size,
            napWakeP25Millis = napQuartiles?.first,
            napWakeP50Millis = napQuartiles?.second ?: median(napIntervals),
            napWakeP75Millis = napQuartiles?.third,
            bedtimeWakeIntervalCount = bedtimeIntervals.size,
            bedtimeWakeP25Millis = bedtimeQuartiles?.first,
            bedtimeWakeP50Millis = bedtimeQuartiles?.second ?: median(bedtimeIntervals),
            bedtimeWakeP75Millis = bedtimeQuartiles?.third,
            avgDailySleepMillis = avgDailySleepMillis(completed),
        )
    }

    fun computeQuality(
        validIntervals: List<SleepInterval>,
        rawRecordCount: Int,
        metrics: SleepMetrics,
    ): EvidenceQuality {
        val possibleIntervals = validIntervals.filter { it.isPossibleAt(nowMillis) }
        val recencyMillis = metrics.lastWakeMillis?.let { nowMillis - it }
        val freshnessHorizonMillis = Duration.ofHours(SleepPredictionTuning.FRESHNESS_HORIZON_HOURS).toMillis()
        val isFresh = recencyMillis != null && recencyMillis < freshnessHorizonMillis
        val lookbackStartMillis = nowMillis - Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS).toMillis()
        val localDayCoverage = possibleIntervals
            .filter { it.isCompleted && it.endMillis!! >= lookbackStartMillis }
            .map { Instant.ofEpochMilli(it.startMillis).atZone(it.zone()).toLocalDate() }
            .toSet()
            .size
        val invalidRecordRate = if (rawRecordCount > 0) {
            (rawRecordCount - possibleIntervals.size).coerceAtLeast(0).toFloat() / rawRecordCount
        } else {
            0f
        }
        val instabilityCeilingMillis = Duration.ofMinutes(SleepPredictionTuning.INSTABILITY_CEILING_MINUTES).toMillis()
        val isStable = (metrics.wakeIntervalIqrMillis == null || metrics.wakeIntervalIqrMillis <= instabilityCeilingMillis) ||
            isTypeAwareStable(metrics, instabilityCeilingMillis)
        val hasSufficientZoneIndependentEvidence = isFresh &&
            metrics.completedWakeIntervals.size >= SleepPredictionTuning.MIN_COMPLETED_INTERVALS &&
            isStable &&
            invalidRecordRate < SleepPredictionTuning.MAX_INVALID_RATE

        val recentCompleted = possibleIntervals.filter {
            it.isCompleted && it.endMillis!! >= lookbackStartMillis
        }
        val qualifiedTzCount = recentCompleted.count { it.timezoneId != null }
        val hasQualifiedTimezoneProvenance = recentCompleted.size >= SleepPredictionTuning.MIN_COMPLETED_INTERVALS &&
            qualifiedTzCount.toFloat() / recentCompleted.size >= SleepPredictionTuning.MIN_QUALIFIED_TZ_PROVENANCE_RATE

        return EvidenceQuality(
            isFresh = isFresh,
            completedIntervalCount = metrics.completedWakeIntervals.size,
            localDayCoverage = localDayCoverage,
            isLocalDayCoverageSufficient = localDayCoverage >= SleepPredictionTuning.MIN_LOCAL_DAYS,
            hasSufficientZoneIndependentEvidence = hasSufficientZoneIndependentEvidence,
            hasQualifiedTimezoneProvenance = hasQualifiedTimezoneProvenance,
        )
    }

    // Merge each overlap cluster into its envelope instead of discarding it: near-duplicate
    // records (partner sync, manual edits) must not delete real sleep evidence (AKACHAN-308).
    private fun mergeOverlapping(sortedCompleted: List<SleepInterval>): List<SleepInterval> {
        val result = mutableListOf<SleepInterval>()
        var cluster = mutableListOf<SleepInterval>()
        var clusterEndMillis: Long? = null

        fun flushCluster() {
            when {
                cluster.size == 1 -> result.add(cluster.single())
                cluster.size > 1 -> result.add(mergeCluster(cluster))
            }
            cluster = mutableListOf()
            clusterEndMillis = null
        }

        for (interval in sortedCompleted) {
            val currentClusterEnd = clusterEndMillis
            if (currentClusterEnd == null) {
                cluster.add(interval)
                clusterEndMillis = interval.endMillis!!
            } else if (interval.startMillis < currentClusterEnd) {
                cluster.add(interval)
                clusterEndMillis = maxOf(currentClusterEnd, interval.endMillis!!)
            } else {
                flushCluster()
                cluster.add(interval)
                clusterEndMillis = interval.endMillis!!
            }
        }

        flushCluster()
        return result
    }

    // Envelope of the cluster: min start, max end, type with the most summed duration. If the
    // envelope fails plausibility validation (e.g. two distant naps chained by a bridging record),
    // keep the longest member instead so the cluster still contributes evidence.
    private fun mergeCluster(cluster: List<SleepInterval>): SleepInterval {
        val dominantType = cluster
            .groupBy { it.sleepType }
            .maxBy { (_, members) -> members.sumOf { it.endMillis!! - it.startMillis } }
            .key
        return SleepInterval.from(
            startMillis = cluster.minOf { it.startMillis },
            endMillis = cluster.maxOf { it.endMillis!! },
            sleepType = dominantType,
            timezoneId = cluster.minBy { it.startMillis }.timezoneId,
        ) ?: cluster.maxBy { it.endMillis!! - it.startMillis }
    }

    private fun SleepInterval.isPossibleAt(referenceMillis: Long): Boolean =
        startMillis <= referenceMillis &&
            endMillis?.let { it <= referenceMillis }
                ?: (referenceMillis - startMillis <= maxOpenSleepAgeMillis)

    private fun BreastfeedInterval.isPossibleAt(referenceMillis: Long): Boolean =
        startMillis <= referenceMillis &&
            endMillis?.let { it <= referenceMillis }
                ?: (referenceMillis - startMillis <= maxOpenFeedAgeMillis)

    private fun completedWakeIntervals(completed: List<SleepInterval>): List<Long> {
        val lookbackStartMillis = nowMillis - Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS).toMillis()
        val minMillis = Duration.ofMinutes(SleepPredictionTuning.MIN_PLAUSIBLE_WAKE_INTERVAL_MINUTES).toMillis()
        val maxMillis = Duration.ofHours(SleepPredictionTuning.MAX_PLAUSIBLE_WAKE_INTERVAL_HOURS).toMillis()
        return completed
            .filter { it.endMillis!! >= lookbackStartMillis }
            .zipWithNext()
            .map { (previous, next) -> next.startMillis - previous.endMillis!! }
            .filter { it in minMillis..maxMillis }
    }

    private fun sumOverlap(completed: List<SleepInterval>, windowStartMillis: Long, windowEndMillis: Long): Long =
        completed.sumOf {
            val overlapStart = maxOf(it.startMillis, windowStartMillis)
            val overlapEnd = minOf(it.endMillis!!, windowEndMillis)
            (overlapEnd - overlapStart).coerceAtLeast(0L)
        }

    // Bucket each record by the zone it was logged in (falling back to the current zone) so
    // records from another timezone don't skew local-date/minute-of-day stats (AKACHAN-306).
    private fun SleepInterval.zone(): ZoneId =
        timezoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zoneId

    private fun startMinuteOfDay(interval: SleepInterval): Int {
        val time = Instant.ofEpochMilli(interval.startMillis).atZone(interval.zone()).toLocalTime()
        return time.hour * 60 + time.minute
    }

    private fun circularMedian(minutes: List<Int>): Int? {
        if (minutes.isEmpty()) return null
        if (minutes.size == 1) return minutes.single()

        val sorted = minutes.sorted()
        val gapStartIndex = sorted.indices.maxBy { index ->
            val current = sorted[index]
            val next = sorted[(index + 1) % sorted.size]
            if (index == sorted.lastIndex) next + MINUTES_PER_DAY - current else next - current
        }
        val unwrapStart = sorted[(gapStartIndex + 1) % sorted.size]
        val unwrapped = sorted.map { minute ->
            if (minute < unwrapStart) minute + MINUTES_PER_DAY else minute
        }.sorted()

        return (median(unwrapped.map { it.toLong() })!!.toInt() % MINUTES_PER_DAY)
    }

    private fun typeAwareWakeIntervals(completed: List<SleepInterval>): Map<SleepType, List<Long>> {
        val lookbackStartMillis = nowMillis - Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS).toMillis()
        val minMillis = Duration.ofMinutes(SleepPredictionTuning.MIN_PLAUSIBLE_WAKE_INTERVAL_MINUTES).toMillis()
        val maxMillis = Duration.ofHours(SleepPredictionTuning.MAX_PLAUSIBLE_WAKE_INTERVAL_HOURS).toMillis()
        return completed
            .filter { it.endMillis!! >= lookbackStartMillis }
            .zipWithNext()
            .mapNotNull { (previous, next) ->
                val gap = next.startMillis - previous.endMillis!!
                if (gap in minMillis..maxMillis) next.sleepType to gap else null
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun quartiles(values: List<Long>): Triple<Long, Long, Long>? {
        if (values.size < 4) return null
        val sorted = values.sorted()
        val p50 = median(sorted) ?: return null
        val lower = sorted.take(sorted.size / 2)
        val upper = sorted.takeLast(sorted.size / 2)
        val p25 = median(lower) ?: return null
        val p75 = median(upper) ?: return null
        return Triple(p25, p50, p75)
    }

    private fun iqr(values: List<Long>): Long? =
        quartiles(values)?.let { (p25, _, p75) -> p75 - p25 }

    private fun avgDailySleepMillis(completed: List<SleepInterval>): Long? {
        val lookbackStartMillis = nowMillis - Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS).toMillis()
        val inLookback = completed.filter { it.endMillis!! >= lookbackStartMillis }
        if (inLookback.size < SleepPredictionTuning.MIN_COMPLETED_INTERVALS) return null
        val totalSleepMillis = inLookback.sumOf { interval ->
            val overlapStart = maxOf(interval.startMillis, lookbackStartMillis)
            (interval.endMillis!! - overlapStart).coerceAtLeast(0L)
        }
        val observedDays = inLookback
            .flatMap { interval ->
                // Clamp to the lookback start like the numerator: days before it contribute no sleep
                // time, so counting them would deflate the average.
                val clampedStartMillis = maxOf(interval.startMillis, lookbackStartMillis)
                val start = Instant.ofEpochMilli(clampedStartMillis).atZone(interval.zone()).toLocalDate()
                val end = Instant.ofEpochMilli(interval.endMillis!!).atZone(interval.zone()).toLocalDate()
                generateSequence(start) { if (it < end) it.plusDays(1) else null }.toList()
            }
            .toSet()
            .size
            .toLong()
            .coerceAtLeast(1L)
        return totalSleepMillis / observedDays
    }

    private fun isTypeAwareStable(metrics: SleepMetrics, ceilingMillis: Long): Boolean {
        val napIqr = if (metrics.napWakeP25Millis != null && metrics.napWakeP75Millis != null) {
            metrics.napWakeP75Millis - metrics.napWakeP25Millis
        } else {
            null
        }
        val bedtimeIqr = if (metrics.bedtimeWakeP25Millis != null && metrics.bedtimeWakeP75Millis != null) {
            metrics.bedtimeWakeP75Millis - metrics.bedtimeWakeP25Millis
        } else {
            null
        }
        return napIqr != null && bedtimeIqr != null && napIqr <= ceilingMillis && bedtimeIqr <= ceilingMillis
    }

    private companion object {
        const val MINUTES_PER_DAY = 1_440
        val maxOpenSleepAgeMillis: Long =
            Duration.ofHours(SleepPredictionTuning.MAX_OPEN_SLEEP_AGE_HOURS).toMillis()
        val maxOpenFeedAgeMillis: Long =
            Duration.ofHours(SleepPredictionTuning.MAX_OPEN_FEED_AGE_HOURS).toMillis()
    }
}
