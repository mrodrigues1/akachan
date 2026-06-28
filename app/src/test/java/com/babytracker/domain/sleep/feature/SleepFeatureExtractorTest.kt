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
import java.time.Duration
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
        assertEquals("sleep-pred-phase6-personalization-rebalance-1", SleepPredictionTuning.ALGORITHM_VERSION)
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
        assertNull(intervals.last().endMillis)
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
        assertNotNull(intervals.single().endMillis)
    }

    @Test
    fun `computeMetrics with no records returns empty metrics`() {
        val metrics = extractor.computeMetrics(emptyList())

        assertNull(metrics.lastWakeMillis)
        assertNull(metrics.lastSleepType)
        assertTrue(metrics.completedWakeIntervals.isEmpty())
        assertNull(metrics.medianWakeIntervalMillis)
        assertNull(metrics.wakeIntervalIqrMillis)
        assertEquals(0L, metrics.sleepLast24hMillis)
        assertEquals(0, metrics.napCountToday)
        assertNull(metrics.medianBedtimeMinuteOfDay)
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

    @Test
    fun `extract sets currentMinuteOfDay from clock and zone`() {
        val features = extractor.extract(emptyList(), emptyList())

        assertEquals(
            14 * 60,
            features.currentMinuteOfDay,
            "currentMinuteOfDay must be hour*60+minute from the injected clock",
        )
    }

    @Test
    fun `extract sets currentMinuteOfDay from configured non UTC zone`() {
        val tokyoExtractor = SleepFeatureExtractor(clock, ZoneId.of("Asia/Tokyo"))
        val features = tokyoExtractor.extract(emptyList(), emptyList())

        assertEquals(
            23 * 60,
            features.currentMinuteOfDay,
            "currentMinuteOfDay must use the extractor zoneId, not UTC or the device default zone",
        )
    }

    @Test
    fun `SleepMetrics has napWakeP50Millis and bedtimeWakeP50Millis fields`() {
        val metrics = SleepMetrics(
            lastWakeMillis = null,
            lastSleepType = null,
            completedWakeIntervals = emptyList(),
            medianWakeIntervalMillis = null,
            wakeIntervalIqrMillis = null,
            sleepLast24hMillis = 0L,
            napCountToday = 0,
            medianBedtimeMinuteOfDay = null,
            napWakeIntervalCount = 0,
            napWakeP25Millis = null,
            napWakeP50Millis = null,
            napWakeP75Millis = null,
            bedtimeWakeIntervalCount = 0,
            bedtimeWakeP25Millis = null,
            bedtimeWakeP50Millis = null,
            bedtimeWakeP75Millis = null,
        )
        assertEquals(0, metrics.napWakeIntervalCount)
        assertNull(metrics.napWakeP50Millis)
        assertNull(metrics.bedtimeWakeP50Millis)
    }

    // Helper: fixed clock + UTC zone used in type-separation tests
    private val testZone = ZoneOffset.UTC
    private val testNow = Instant.parse("2024-06-15T14:00:00Z")
    private val testClock: Clock = Clock.fixed(testNow, testZone)

    private fun extractor() = SleepFeatureExtractor(testClock, testZone)

    /** 3 NAPs followed by 1 NIGHT_SLEEP in chronological order. */
    private fun mixedRecords(): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        // Nap 1 end=8h ago, Nap 2 end=5h30m ago, Nap 3 end=3h ago, Night end=now-1h
        val ends = listOf(8 * 3600, 5 * 3600 + 30 * 60, 3 * 3600, 1 * 3600)
        val types = listOf(SleepType.NAP, SleepType.NAP, SleepType.NAP, SleepType.NIGHT_SLEEP)
        for (i in ends.indices) {
            val end = testNow.minusSeconds(ends[i].toLong())
            val start = end.minusSeconds(5400) // 90-min sleep
            records += SleepRecord(id++, start, end, types[i])
        }
        return records.sortedBy { it.startTime }
    }

    @Test
    fun `type-separated - nap wake intervals and P50 computed for NAP-labeled gaps`() {
        val features = extractor().extract(mixedRecords(), emptyList())
        val metrics = features.metrics
        // 3 naps → 2 NAP-labeled wake intervals (between N1→N2, N2→N3)
        assertEquals(2, metrics.napWakeIntervalCount)
        assertNotNull(metrics.napWakeP50Millis)
        // Nap1→Nap2 gap: 8h - 5h30m = 2h30m - 90min nap = 60 min
        // Nap2→Nap3 gap: 5h30m - 3h = 2h30m - 90min nap = 60 min
        // Both ≈ 60 min wake interval
        val expectedNapGapMs = Duration.ofMinutes(60).toMillis()
        assertTrue(
            kotlin.math.abs(metrics.napWakeP50Millis!! - expectedNapGapMs) < Duration.ofMinutes(5).toMillis(),
            "napWakeP50 should be ~60 min; got ${metrics.napWakeP50Millis!! / 60000} min"
        )
    }

    @Test
    fun `type-separated - bedtime wake interval computed for NIGHT_SLEEP-labeled gap`() {
        val features = extractor().extract(mixedRecords(), emptyList())
        val metrics = features.metrics
        // Nap3→Night gap: 3h - 1h = 2h - 90min nap = 30 min (short pre-bedtime window)
        assertEquals(1, metrics.bedtimeWakeIntervalCount)
        assertNotNull(metrics.bedtimeWakeP50Millis)
    }

    @Test
    fun `type-separated - naps-only produces zero bedtime intervals`() {
        val napOnly = mixedRecords().filter { it.sleepType == SleepType.NAP }
        val features = extractor().extract(napOnly, emptyList())
        assertEquals(0, features.metrics.bedtimeWakeIntervalCount)
        assertNull(features.metrics.bedtimeWakeP50Millis)
    }

    @Test
    fun `type-separated - nights-only produces zero nap intervals`() {
        // 5 NIGHT_SLEEP records, 16h wake gaps → filtered by MAX_PLAUSIBLE (6h)
        var id = 1L
        val nights = (0 until 5).map { i ->
            val end = testNow.minusSeconds((i * 24 + 6) * 3600L)
            val start = end.minusSeconds(8 * 3600)
            SleepRecord(id++, start, end, SleepType.NIGHT_SLEEP)
        }.sortedBy { it.startTime }
        val features = extractor().extract(nights, emptyList())
        assertEquals(0, features.metrics.napWakeIntervalCount)
        assertNull(features.metrics.napWakeP50Millis)
    }

    @Test
    fun `type-separated - P25 and P75 are null when fewer than 4 type-specific intervals`() {
        // 3 NAP-labeled intervals → P50 valid, P25/P75 null
        var id = 1L
        val records = (0 until 4).map { i ->
            val end = testNow.minusSeconds((i * 3 + 1) * 3600L)
            val start = end.minusSeconds(5400L)
            SleepRecord(id++, start, end, SleepType.NAP)
        }.sortedBy { it.startTime }
        val features = extractor().extract(records, emptyList())
        // 3 intervals (4 records → 3 gaps), each ~90 min
        assertEquals(3, features.metrics.napWakeIntervalCount)
        assertNotNull(features.metrics.napWakeP50Millis)
        assertNull(features.metrics.napWakeP25Millis)
        assertNull(features.metrics.napWakeP75Millis)
    }

    @Test
    fun `avgDailySleepMillis is null when fewer than MIN_COMPLETED_INTERVALS completed sleeps in lookback`() {
        val clock = Clock.fixed(Instant.parse("2024-06-15T14:00:00Z"), utcZone)
        val extractor = SleepFeatureExtractor(clock, utcZone)
        val records = listOf(
            SleepRecord(1, Instant.parse("2024-06-14T20:00:00Z"), Instant.parse("2024-06-15T06:00:00Z"), SleepType.NIGHT_SLEEP)
        )
        val features = extractor.extract(records, emptyList())
        assertNull(features.metrics.avgDailySleepMillis)
    }

    @Test
    fun `avgDailySleepMillis computes total lookback sleep divided by observed day count`() {
        val now = Instant.parse("2024-06-15T14:00:00Z")
        val clock = Clock.fixed(now, utcZone)
        val extractor = SleepFeatureExtractor(clock, utcZone)
        val lookbackStart = now.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
        // Each record starts at 23:00 UTC and ends at 00:00 UTC next day, spanning 2 days each.
        // 5 records × 2 days = {Jun 1..Jun 6} → 6 distinct observed days.
        val records = (0 until 5).map { i ->
            val start = lookbackStart.plus(Duration.ofDays(i.toLong())).plus(Duration.ofHours(9))
            SleepRecord(i.toLong() + 1, start, start.plus(Duration.ofHours(1)), SleepType.NAP)
        }
        val features = extractor.extract(records, emptyList())
        val expectedMillis = Duration.ofHours(5).toMillis() / 6L
        assertEquals(expectedMillis, features.metrics.avgDailySleepMillis)
    }

    @Test
    fun `avgDailySleepMillis clips sleep that started before lookback window`() {
        val now = Instant.parse("2024-06-15T14:00:00Z")
        val clock = Clock.fixed(now, utcZone)
        val extractor = SleepFeatureExtractor(clock, utcZone)
        val lookbackStart = now.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
        val preStart = lookbackStart.minus(Duration.ofHours(2))
        val preEnd = lookbackStart.plus(Duration.ofHours(1))
        val extras = (0 until 4).map { i ->
            val start = lookbackStart.plus(Duration.ofDays(i.toLong() + 1L)).plus(Duration.ofHours(9))
            SleepRecord(i.toLong() + 2, start, start.plus(Duration.ofHours(1)), SleepType.NAP)
        }
        val records = listOf(SleepRecord(1, preStart, preEnd, SleepType.NIGHT_SLEEP)) + extras
        val features = extractor.extract(records, emptyList())
        // Clipped record spans only Jun 1; extras span {Jun 2+3, Jun 3+4, Jun 4+5, Jun 5+6} → 6 distinct days.
        val expectedMillis = Duration.ofHours(5).toMillis() / 6L
        assertEquals(expectedMillis, features.metrics.avgDailySleepMillis)
    }

    @Test
    fun `quality passes when combined IQR wide but both type-specific IQRs are stable`() {
        val intervals = buildWideIqrMixedIntervals()
        val metrics = extractor.computeMetrics(intervals)
        val quality = extractor.computeQuality(intervals, intervals.size, metrics)

        assertTrue(
            metrics.wakeIntervalIqrMillis != null &&
                metrics.wakeIntervalIqrMillis!! > Duration.ofMinutes(45).toMillis(),
            "Fixture combined IQR must exceed 45-min ceiling; got ${metrics.wakeIntervalIqrMillis?.div(60_000)} min",
        )
        assertNotNull(metrics.napWakeP25Millis, "Need >= 4 nap intervals (have ${metrics.napWakeIntervalCount})")
        assertNotNull(metrics.bedtimeWakeP25Millis, "Need >= 4 bedtime intervals (have ${metrics.bedtimeWakeIntervalCount})")
        assertTrue(
            quality.hasSufficientZoneIndependentEvidence,
            "Quality must pass when both type-specific IQRs are stable even if combined IQR exceeds ceiling",
        )
    }

    @Test
    fun `quality fails when combined IQR wide and type-specific quartiles unavailable`() {
        val intervals = buildThinMixedIntervals()
        val metrics = extractor.computeMetrics(intervals)
        val quality = extractor.computeQuality(intervals, intervals.size, metrics)

        assertTrue(
            metrics.wakeIntervalIqrMillis != null &&
                metrics.wakeIntervalIqrMillis!! > Duration.ofMinutes(45).toMillis(),
            "Combined IQR must exceed ceiling; got ${metrics.wakeIntervalIqrMillis?.div(60_000)} min",
        )
        assertNull(metrics.napWakeP25Millis, "napWakeP25 must be null with only 3 nap intervals")
        assertFalse(
            quality.hasSufficientZoneIndependentEvidence,
            "Quality must fail when not enough per-type intervals for type-aware stability check",
        )
    }

    @Test
    fun `computeQuality sets hasQualifiedTimezoneProvenance false when no records have timezoneId`() {
        val records = (1..10).map { i ->
            SleepRecord(
                startTime = nowInstant.minusMillis(hoursMs(i * 3.0 + 1)),
                endTime = nowInstant.minusMillis(hoursMs(i * 3.0)),
                sleepType = SleepType.NAP,
                timezoneId = null,
            )
        }
        val intervals = extractor.buildSleepIntervals(records)
        val metrics = extractor.computeMetrics(intervals)
        val quality = extractor.computeQuality(intervals, records.size, metrics)

        assertFalse(quality.hasQualifiedTimezoneProvenance)
    }

    @Test
    fun `computeQuality sets hasQualifiedTimezoneProvenance true when majority of recent intervals have timezoneId`() {
        val records = (1..10).map { i ->
            SleepRecord(
                startTime = nowInstant.minusMillis(hoursMs(i * 3.0 + 1)),
                endTime = nowInstant.minusMillis(hoursMs(i * 3.0)),
                sleepType = SleepType.NAP,
                timezoneId = if (i <= 8) "America/New_York" else null,
            )
        }
        val intervals = extractor.buildSleepIntervals(records)
        val metrics = extractor.computeMetrics(intervals)
        val quality = extractor.computeQuality(intervals, records.size, metrics)

        assertTrue(quality.hasQualifiedTimezoneProvenance)
    }

    @Test
    fun `computeQuality sets hasQualifiedTimezoneProvenance false when fewer than MIN_COMPLETED_INTERVALS available`() {
        val records = (1..3).map { i ->
            SleepRecord(
                startTime = nowInstant.minusMillis(hoursMs(i * 3.0 + 1)),
                endTime = nowInstant.minusMillis(hoursMs(i * 3.0)),
                sleepType = SleepType.NAP,
                timezoneId = "UTC",
            )
        }
        val intervals = extractor.buildSleepIntervals(records)
        val metrics = extractor.computeMetrics(intervals)
        val quality = extractor.computeQuality(intervals, records.size, metrics)

        assertFalse(quality.hasQualifiedTimezoneProvenance)
    }

    @Test
    fun `hasQualifiedTimezoneProvenance true when records have timezoneId from a different zone than current device`() {
        val records = (1..10).map { i ->
            SleepRecord(
                startTime = nowInstant.minusMillis(hoursMs(i * 3.0 + 1)),
                endTime = nowInstant.minusMillis(hoursMs(i * 3.0)),
                sleepType = SleepType.NAP,
                timezoneId = "America/New_York",
            )
        }
        val intervals = extractor.buildSleepIntervals(records)
        val metrics = extractor.computeMetrics(intervals)
        val quality = extractor.computeQuality(intervals, records.size, metrics)

        assertTrue(quality.hasQualifiedTimezoneProvenance)
    }

    @Test
    fun `hasQualifiedTimezoneProvenance false for legacy records with null timezoneId after device timezone change`() {
        val records = (1..10).map { i ->
            SleepRecord(
                startTime = nowInstant.minusMillis(hoursMs(i * 3.0 + 1)),
                endTime = nowInstant.minusMillis(hoursMs(i * 3.0)),
                sleepType = SleepType.NAP,
                timezoneId = null,
            )
        }
        val intervals = extractor.buildSleepIntervals(records)
        val metrics = extractor.computeMetrics(intervals)
        val quality = extractor.computeQuality(intervals, records.size, metrics)

        assertFalse(quality.hasQualifiedTimezoneProvenance)
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

    private fun buildWideIqrMixedIntervals(): List<SleepInterval> {
        val result = mutableListOf<SleepInterval>()
        val cycleMs = (8 * 60 + 90 + 60 + 90 + 60 + 150) * 60_000L
        var cursor = nowInstant.toEpochMilli() - 6 * cycleMs - 30 * 60_000L
        repeat(6) {
            val nightEnd = cursor + 8 * 60 * 60_000L
            result += SleepInterval.from(cursor, nightEnd, SleepType.NIGHT_SLEEP)!!
            cursor = nightEnd
            val nap1Start = cursor + 90 * 60_000L
            val nap1End = nap1Start + 60 * 60_000L
            result += SleepInterval.from(nap1Start, nap1End, SleepType.NAP)!!
            cursor = nap1End
            val nap2Start = cursor + 90 * 60_000L
            val nap2End = nap2Start + 60 * 60_000L
            result += SleepInterval.from(nap2Start, nap2End, SleepType.NAP)!!
            cursor = nap2End + 150 * 60_000L
        }
        return result.filter { it.endMillis!! <= nowInstant.toEpochMilli() }.sortedBy { it.startMillis }
    }

    private fun buildThinMixedIntervals(): List<SleepInterval> {
        val result = mutableListOf<SleepInterval>()
        val cycleMs = (8 * 60 + 60 + 60 + 180) * 60_000L
        var cursor = nowInstant.toEpochMilli() - 3 * cycleMs - 30 * 60_000L
        repeat(3) {
            val nightEnd = cursor + 8 * 60 * 60_000L
            result += SleepInterval.from(cursor, nightEnd, SleepType.NIGHT_SLEEP)!!
            cursor = nightEnd
            val napStart = cursor + 60 * 60_000L
            val napEnd = napStart + 60 * 60_000L
            result += SleepInterval.from(napStart, napEnd, SleepType.NAP)!!
            cursor = napEnd + 180 * 60_000L
        }
        return result.filter { it.endMillis!! <= nowInstant.toEpochMilli() }.sortedBy { it.startMillis }
    }
}
