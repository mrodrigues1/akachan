package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SleepFeatureExtractor(
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    private val nowMillis: Long
        get() = clock.instant().toEpochMilli()

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
            .let { removeOverlapping(it) }
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
        val today = LocalDate.ofInstant(clock.instant(), zoneId)
        val nightSleeps = completed.filter { it.sleepType == SleepType.NIGHT_SLEEP }
        val typeWakeIntervals = typeAwareWakeIntervals(completed)
        val napIntervals = typeWakeIntervals[SleepType.NAP] ?: emptyList()
        val bedtimeIntervals = typeWakeIntervals[SleepType.NIGHT_SLEEP] ?: emptyList()
        val napQuartiles = quartiles(napIntervals)
        val bedtimeQuartiles = quartiles(bedtimeIntervals)

        return SleepMetrics(
            lastWakeMillis = lastSleep?.endMillis,
            lastSleepType = lastSleep?.sleepType,
            lastSleepDurationMillis = lastSleep?.durationMillis,
            completedWakeIntervals = wakeIntervals,
            medianWakeIntervalMillis = median(wakeIntervals),
            wakeIntervalIqrMillis = iqr(wakeIntervals),
            sleepLast24hMillis = sumOverlap(completed, nowMillis - Duration.ofHours(24).toMillis(), nowMillis),
            daySleepTodayMillis = sumTodayDaySleep(completed, today),
            napCountToday = completed.count {
                it.sleepType == SleepType.NAP && LocalDate.ofInstant(Instant.ofEpochMilli(it.startMillis), zoneId) == today
            },
            medianBedtimeMinuteOfDay = medianMinuteOfDay(nightSleeps.map { it.startMillis }),
            medianMorningWakeMinuteOfDay = medianMinuteOfDay(nightSleeps.mapNotNull { it.endMillis }),
            napWakeIntervalCount = napIntervals.size,
            napWakeP25Millis = napQuartiles?.first,
            napWakeP50Millis = napQuartiles?.second ?: median(napIntervals),
            napWakeP75Millis = napQuartiles?.third,
            bedtimeWakeIntervalCount = bedtimeIntervals.size,
            bedtimeWakeP25Millis = bedtimeQuartiles?.first,
            bedtimeWakeP50Millis = bedtimeQuartiles?.second ?: median(bedtimeIntervals),
            bedtimeWakeP75Millis = bedtimeQuartiles?.third,
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
            .map { LocalDate.ofInstant(Instant.ofEpochMilli(it.startMillis), zoneId) }
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

        return EvidenceQuality(
            lastWakeRecencyMillis = recencyMillis,
            isFresh = isFresh,
            completedIntervalCount = metrics.completedWakeIntervals.size,
            localDayCoverage = localDayCoverage,
            isLocalDayCoverageSufficient = localDayCoverage >= SleepPredictionTuning.MIN_LOCAL_DAYS,
            wakeIntervalIqrMillis = metrics.wakeIntervalIqrMillis,
            invalidRecordRate = invalidRecordRate,
            hasSufficientZoneIndependentEvidence = hasSufficientZoneIndependentEvidence,
        )
    }

    private fun removeOverlapping(sortedCompleted: List<SleepInterval>): List<SleepInterval> {
        val result = mutableListOf<SleepInterval>()
        var cluster = mutableListOf<SleepInterval>()
        var clusterEndMillis: Long? = null

        fun flushCluster() {
            if (cluster.size == 1) result.add(cluster.single())
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

    private fun sumTodayDaySleep(completed: List<SleepInterval>, today: LocalDate): Long =
        completed
            .filter { it.sleepType == SleepType.NAP }
            .sumOf { interval ->
                val startOfDay = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endOfDay = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val overlapStart = maxOf(interval.startMillis, startOfDay)
                val overlapEnd = minOf(interval.endMillis!!, endOfDay)
                (overlapEnd - overlapStart).coerceAtLeast(0L)
            }

    private fun medianMinuteOfDay(timesMillis: List<Long>): Int? =
        circularMedian(timesMillis.map { minuteOfDay(it) })

    private fun minuteOfDay(epochMillis: Long): Int {
        val time = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalTime()
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

    private fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2
        }
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
