package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class SleepFeatureExtractorTest {
    private val nowInstant = Instant.parse("2024-06-15T14:00:00Z")
    private val clock = Clock.fixed(nowInstant, ZoneOffset.UTC)
    private val utcZone: ZoneId = ZoneId.of("UTC")
    private lateinit var extractor: SleepFeatureExtractor

    @BeforeEach
    fun setup() {
        extractor = SleepFeatureExtractor(clock, utcZone)
    }

    @Test
    fun `SleepPredictionTuning constants are positive`() {
        assertTrue(SleepPredictionTuning.FRESHNESS_HORIZON_HOURS > 0)
        assertTrue(SleepPredictionTuning.MIN_COMPLETED_INTERVALS > 0)
        assertTrue(SleepPredictionTuning.LOOKBACK_DAYS > 0)
        assertTrue(SleepPredictionTuning.MAX_NAP_DURATION_HOURS > 0)
        assertTrue(SleepPredictionTuning.HALF_WINDOW_MINUTES > 0)
        assertEquals("sleep-pred-baseline-1", SleepPredictionTuning.ALGORITHM_VERSION)
    }

    @Test
    fun `buildSleepIntervals removes invalid and overlapping records`() {
        val records = listOf(
            sleepRecord(6.0, 5.0),
            sleepRecord(4.5, 3.5),
            sleepRecord(4.0, 3.0),
            sleepRecord(2.0, 2.0),
        )

        val intervals = extractor.buildSleepIntervals(records)

        assertEquals(1, intervals.size)
        assertEquals(nowInstant.minusMillis(hoursMs(6.0)).toEpochMilli(), intervals[0].startMillis)
    }

    @Test
    fun `buildSleepIntervals keeps only latest non-overlapping open sleep`() {
        val records = listOf(
            sleepRecord(3.0, null),
            sleepRecord(2.0, null),
            sleepRecord(1.0, null),
        )

        val intervals = extractor.buildSleepIntervals(records)

        assertEquals(1, intervals.size)
        assertEquals(nowInstant.minusMillis(hoursMs(1.0)).toEpochMilli(), intervals.single().startMillis)
    }

    @Test
    fun `buildSleepIntervals rejects future completed and active sleeps`() {
        val records = listOf(
            sleepRecord(4.0, 3.0),
            SleepRecord(
                startTime = nowInstant.plusMillis(hoursMs(1.0)),
                endTime = nowInstant.plusMillis(hoursMs(2.0)),
                sleepType = SleepType.NAP,
            ),
            SleepRecord(
                startTime = nowInstant.plusMillis(hoursMs(1.0)),
                endTime = null,
                sleepType = SleepType.NAP,
            ),
        )

        val intervals = extractor.buildSleepIntervals(records)

        assertEquals(1, intervals.size)
        assertEquals(nowInstant.minusMillis(hoursMs(4.0)).toEpochMilli(), intervals.single().startMillis)
    }

    @Test
    fun `buildSleepIntervals rejects overlong night sleep without suppressing later records`() {
        val records = listOf(
            SleepRecord(
                startTime = nowInstant.minusMillis(hoursMs(30.0)),
                endTime = nowInstant.minusMillis(hoursMs(1.0)),
                sleepType = SleepType.NIGHT_SLEEP,
            ),
            sleepRecord(4.0, 3.0),
            sleepRecord(2.0, 1.0),
        )

        val intervals = extractor.buildSleepIntervals(records)

        assertEquals(2, intervals.size)
        assertTrue(intervals.all { it.sleepType == SleepType.NAP })
    }

    @Test
    fun `buildSleepIntervals rejects all records in an overlapping completed cluster`() {
        val records = listOf(
            SleepRecord(
                startTime = nowInstant.minusMillis(hoursMs(10.0)),
                endTime = nowInstant.minusMillis(hoursMs(1.0)),
                sleepType = SleepType.NIGHT_SLEEP,
            ),
            sleepRecord(4.0, 3.0),
            sleepRecord(2.5, 2.0),
            sleepRecord(0.5, 0.25),
        )

        val intervals = extractor.buildSleepIntervals(records)

        assertEquals(1, intervals.size)
        assertEquals(nowInstant.minusMillis(hoursMs(0.5)).toEpochMilli(), intervals.single().startMillis)
    }

    @Test
    fun `buildSleepIntervals rejects stale open sleep`() {
        val records = listOf(
            SleepRecord(
                startTime = nowInstant.minusMillis(hoursMs(19.0)),
                endTime = null,
                sleepType = SleepType.NIGHT_SLEEP,
            ),
            sleepRecord(4.0, 3.0),
        )

        val intervals = extractor.buildSleepIntervals(records)

        assertEquals(1, intervals.size)
        assertTrue(intervals.single().isCompleted)
    }

    @Test
    fun `buildBreastfeedIntervals keeps active feed signal`() {
        val intervals = extractor.buildBreastfeedIntervals(
            listOf(
                feed(3.0, 2.5),
                feed(1.0, null),
            )
        )

        assertEquals(2, intervals.size)
        assertTrue(intervals.last().isActive)
    }

    @Test
    fun `buildBreastfeedIntervals rejects future feeds`() {
        val intervals = extractor.buildBreastfeedIntervals(
            listOf(
                feed(3.0, 2.5),
                BreastfeedingSession(
                    startTime = nowInstant.plusMillis(hoursMs(1.0)),
                    endTime = null,
                    startingSide = BreastSide.LEFT,
                ),
            )
        )

        assertEquals(1, intervals.size)
    }

    @Test
    fun `buildBreastfeedIntervals rejects stale active feeds`() {
        val intervals = extractor.buildBreastfeedIntervals(
            listOf(
                feed(3.0, 2.5),
                feed(5.0, null),
            )
        )

        assertEquals(1, intervals.size)
        assertFalse(intervals.single().isActive)
    }

    @Test
    fun `computeMetrics with no records returns empty metrics`() {
        val metrics = extractor.computeMetrics(emptyList())

        assertNull(metrics.lastWakeMillis)
        assertNull(metrics.lastSleepType)
        assertNull(metrics.lastSleepDurationMillis)
        assertTrue(metrics.completedWakeIntervals.isEmpty())
        assertNull(metrics.medianWakeIntervalMillis)
        assertNull(metrics.wakeIntervalIqrMillis)
        assertEquals(0L, metrics.sleepLast24hMillis)
        assertEquals(0L, metrics.daySleepTodayMillis)
        assertEquals(0, metrics.napCountToday)
        assertNull(metrics.medianBedtimeMinuteOfDay)
        assertNull(metrics.medianMorningWakeMinuteOfDay)
    }

    @Test
    fun `computeMetrics reports lastWake as end of most recent completed sleep`() {
        val metrics = extractor.computeMetrics(
            listOf(
                interval(4.0, 3.0),
                interval(2.0, 1.0),
            )
        )

        assertEquals(nowInstant.minusMillis(hoursMs(1.0)).toEpochMilli(), metrics.lastWakeMillis)
        assertEquals(SleepType.NAP, metrics.lastSleepType)
        assertEquals(hoursMs(1.0), metrics.lastSleepDurationMillis)
    }

    @Test
    fun `computeMetrics ignores future intervals to avoid negative recency`() {
        val metrics = extractor.computeMetrics(
            listOf(
                interval(4.0, 3.0),
                SleepInterval.from(
                    nowInstant.plusMillis(hoursMs(1.0)).toEpochMilli(),
                    nowInstant.plusMillis(hoursMs(2.0)).toEpochMilli(),
                    SleepType.NAP,
                )!!,
            )
        )

        assertEquals(nowInstant.minusMillis(hoursMs(3.0)).toEpochMilli(), metrics.lastWakeMillis)
    }

    @Test
    fun `computeMetrics filters wake intervals outside plausible range`() {
        val metrics = extractor.computeMetrics(
            listOf(
                interval(10.0, 9.0),
                interval(2.0, 1.0),
            )
        )

        assertTrue(metrics.completedWakeIntervals.isEmpty())
        assertNull(metrics.medianWakeIntervalMillis)
    }

    @Test
    fun `computeMetrics computes rolling median and IQR`() {
        val metrics = extractor.computeMetrics(
            buildConsecutiveSleeps(
                wakeIntervalsMinutes = listOf(90L, 100L, 110L, 120L, 130L),
                sleepDurationMinutes = 60L,
                endHoursAgo = 0.5,
            )
        )

        assertEquals(5, metrics.completedWakeIntervals.size)
        assertEquals(110 * 60_000L, metrics.medianWakeIntervalMillis)
        assertEquals(30 * 60_000L, metrics.wakeIntervalIqrMillis)
    }

    @Test
    fun `computeMetrics sleepLast24h sums interval overlap across boundary`() {
        val metrics = extractor.computeMetrics(
            listOf(
                interval(3.0, 2.0),
                interval(25.0, 23.0),
            )
        )

        assertEquals(hoursMs(2.0), metrics.sleepLast24hMillis)
    }

    @Test
    fun `computeMetrics computes today nap count and day sleep from injected zone`() {
        val londonExtractor = SleepFeatureExtractor(
            Clock.fixed(Instant.parse("2024-06-15T12:00:00Z"), ZoneOffset.UTC),
            ZoneId.of("Europe/London"),
        )
        val interval = SleepInterval.from(
            Instant.parse("2024-06-15T07:00:00Z").toEpochMilli(),
            Instant.parse("2024-06-15T08:00:00Z").toEpochMilli(),
            SleepType.NAP,
        )!!

        val metrics = londonExtractor.computeMetrics(listOf(interval))

        assertEquals(1, metrics.napCountToday)
        assertEquals(3_600_000L, metrics.daySleepTodayMillis)
    }

    @Test
    fun `computeMetrics produces night bedtime and morning wake medians`() {
        val metrics = extractor.computeMetrics(
            listOf(
                fixedInterval("2024-06-13T20:00:00Z", "2024-06-14T06:00:00Z", SleepType.NIGHT_SLEEP),
                fixedInterval("2024-06-14T20:30:00Z", "2024-06-15T06:30:00Z", SleepType.NIGHT_SLEEP),
            )
        )

        assertEquals(20 * 60 + 15, metrics.medianBedtimeMinuteOfDay)
        assertEquals(6 * 60 + 15, metrics.medianMorningWakeMinuteOfDay)
    }

    @Test
    fun `computeMetrics uses circular medians around midnight`() {
        val metrics = extractor.computeMetrics(
            listOf(
                fixedInterval("2024-06-13T23:50:00Z", "2024-06-14T00:20:00Z", SleepType.NIGHT_SLEEP),
                fixedInterval("2024-06-15T00:10:00Z", "2024-06-15T00:40:00Z", SleepType.NIGHT_SLEEP),
            )
        )

        assertEquals(0, metrics.medianBedtimeMinuteOfDay)
        assertEquals(30, metrics.medianMorningWakeMinuteOfDay)
    }

    @Test
    fun `naps only leaves bedtime and morning wake medians null`() {
        val features = extractor.extract(
            listOf(
                sleepRecord(4.0, 3.0),
                sleepRecord(2.0, 1.0),
            ),
            emptyList(),
        )

        assertNull(features.metrics.medianBedtimeMinuteOfDay)
        assertNull(features.metrics.medianMorningWakeMinuteOfDay)
    }

    @Test
    fun `one sleep per day has no plausible wake intervals and does not crash`() {
        val records = (1..3).map { day ->
            SleepRecord(
                startTime = nowInstant.minusMillis(hoursMs((day * 24 + 2).toDouble())),
                endTime = nowInstant.minusMillis(hoursMs((day * 24 + 1).toDouble())),
                sleepType = SleepType.NAP,
            )
        }

        val features = extractor.extract(records, emptyList())

        assertTrue(features.metrics.completedWakeIntervals.isEmpty())
        assertFalse(features.quality.hasSufficientZoneIndependentEvidence)
    }

    @Test
    fun `stale last wake fails freshness check`() {
        val features = extractor.extract(listOf(sleepRecord(14.0, 13.0)), emptyList())

        assertFalse(features.quality.isFresh)
        assertFalse(features.quality.hasSufficientZoneIndependentEvidence)
    }

    @Test
    fun `quality passes gating bar with fresh stable intervals`() {
        val intervals = buildConsecutiveSleeps(
            wakeIntervalsMinutes = List(5) { 90L },
            sleepDurationMinutes = 60L,
            endHoursAgo = 0.5,
        )
        val metrics = extractor.computeMetrics(intervals)
        val quality = extractor.computeQuality(intervals, rawRecordCount = intervals.size, metrics = metrics)

        assertTrue(quality.isFresh)
        assertEquals(5, quality.completedIntervalCount)
        assertTrue(quality.hasSufficientZoneIndependentEvidence)
    }

    @Test
    fun `quality fails when invalid record rate is too high`() {
        val intervals = buildConsecutiveSleeps(
            wakeIntervalsMinutes = List(5) { 90L },
            sleepDurationMinutes = 60L,
            endHoursAgo = 0.5,
        )
        val metrics = extractor.computeMetrics(intervals)
        val quality = extractor.computeQuality(intervals, rawRecordCount = 9, metrics = metrics)

        assertTrue(quality.invalidRecordRate > SleepPredictionTuning.MAX_INVALID_RATE)
        assertFalse(quality.hasSufficientZoneIndependentEvidence)
    }

    @Test
    fun `local day coverage is computed but does not affect gating bar`() {
        val intervals = buildConsecutiveSleeps(
            wakeIntervalsMinutes = List(5) { 60L },
            sleepDurationMinutes = 60L,
            endHoursAgo = 0.25,
        )
        val metrics = extractor.computeMetrics(intervals)
        val quality = extractor.computeQuality(intervals, rawRecordCount = intervals.size, metrics = metrics)

        assertTrue(quality.localDayCoverage < SleepPredictionTuning.MIN_LOCAL_DAYS)
        assertFalse(quality.isLocalDayCoverageSufficient)
        assertTrue(quality.hasSufficientZoneIndependentEvidence)
    }

    @Test
    fun `spring forward sleep uses real epoch duration`() {
        val dstExtractor = SleepFeatureExtractor(
            Clock.fixed(Instant.parse("2024-03-10T12:00:00Z"), ZoneOffset.UTC),
            ZoneId.of("America/New_York"),
        )
        val interval = fixedInterval("2024-03-10T06:30:00Z", "2024-03-10T07:30:00Z", SleepType.NIGHT_SLEEP)

        val metrics = dstExtractor.computeMetrics(listOf(interval))

        assertEquals(3_600_000L, metrics.sleepLast24hMillis)
    }

    @Test
    fun `fall back sleep uses real epoch duration`() {
        val dstExtractor = SleepFeatureExtractor(
            Clock.fixed(Instant.parse("2024-11-03T12:00:00Z"), ZoneOffset.UTC),
            ZoneId.of("America/New_York"),
        )
        val interval = fixedInterval("2024-11-03T05:30:00Z", "2024-11-03T07:30:00Z", SleepType.NIGHT_SLEEP)

        val metrics = dstExtractor.computeMetrics(listOf(interval))

        assertEquals(7_200_000L, metrics.sleepLast24hMillis)
    }

    @Test
    fun `open in-progress sleep does not contribute to lastWake or wake intervals`() {
        val features = extractor.extract(
            listOf(
                sleepRecord(4.0, 3.0),
                sleepRecord(1.0, null),
            ),
            emptyList(),
        )

        assertEquals(nowInstant.minusMillis(hoursMs(3.0)).toEpochMilli(), features.metrics.lastWakeMillis)
        assertTrue(features.metrics.completedWakeIntervals.isEmpty())
    }

    private fun hoursMs(hours: Double): Long = (hours * 3_600_000).toLong()

    private fun sleepRecord(
        startHoursAgo: Double,
        endHoursAgo: Double?,
        type: SleepType = SleepType.NAP,
    ): SleepRecord = SleepRecord(
        startTime = nowInstant.minusMillis(hoursMs(startHoursAgo)),
        endTime = endHoursAgo?.let { nowInstant.minusMillis(hoursMs(it)) },
        sleepType = type,
    )

    private fun feed(startHoursAgo: Double, endHoursAgo: Double?): BreastfeedingSession =
        BreastfeedingSession(
            startTime = nowInstant.minusMillis(hoursMs(startHoursAgo)),
            endTime = endHoursAgo?.let { nowInstant.minusMillis(hoursMs(it)) },
            startingSide = BreastSide.LEFT,
        )

    private fun interval(
        startHoursAgo: Double,
        endHoursAgo: Double,
        type: SleepType = SleepType.NAP,
    ): SleepInterval = SleepInterval.from(
        nowInstant.minusMillis(hoursMs(startHoursAgo)).toEpochMilli(),
        nowInstant.minusMillis(hoursMs(endHoursAgo)).toEpochMilli(),
        type,
    )!!

    private fun fixedInterval(start: String, end: String, type: SleepType): SleepInterval =
        SleepInterval.from(
            Instant.parse(start).toEpochMilli(),
            Instant.parse(end).toEpochMilli(),
            type,
        )!!

    private fun buildConsecutiveSleeps(
        wakeIntervalsMinutes: List<Long>,
        sleepDurationMinutes: Long,
        endHoursAgo: Double,
    ): List<SleepInterval> {
        val sleepDurationMillis = sleepDurationMinutes * 60_000L
        var endMillis = nowInstant.minusMillis(hoursMs(endHoursAgo)).toEpochMilli()
        val intervals = mutableListOf<SleepInterval>()
        intervals.add(SleepInterval.from(endMillis - sleepDurationMillis, endMillis, SleepType.NAP)!!)
        for (wakeMinutes in wakeIntervalsMinutes.asReversed()) {
            val nextEndMillis = intervals.first().startMillis - wakeMinutes * 60_000L
            intervals.add(0, SleepInterval.from(nextEndMillis - sleepDurationMillis, nextEndMillis, SleepType.NAP)!!)
        }
        return intervals
    }
}
