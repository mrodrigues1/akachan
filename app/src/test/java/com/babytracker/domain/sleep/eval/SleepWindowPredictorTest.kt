package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.sleep.feature.BreastfeedInterval
import com.babytracker.domain.sleep.feature.EvidenceQuality
import com.babytracker.domain.sleep.feature.SleepFeatures
import com.babytracker.domain.sleep.feature.SleepMetrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SleepWindowPredictorTest {

    private val baseNow = Instant.parse("2024-06-15T14:00:00Z")
    private val ageInWeeks = 20 // 90-180 min wake-window bounds

    private fun sufficientQuality(completedCount: Int = 10) = EvidenceQuality(
        lastWakeRecencyMillis = Duration.ofHours(1).toMillis(),
        isFresh = true,
        completedIntervalCount = completedCount,
        localDayCoverage = 5,
        isLocalDayCoverageSufficient = true,
        wakeIntervalIqrMillis = Duration.ofMinutes(10).toMillis(),
        invalidRecordRate = 0f,
        hasSufficientZoneIndependentEvidence = true,
    )

    private fun sufficientMetrics(
        lastWakeMillis: Long,
        medianIntervalMillis: Long,
        napWakeIntervalCount: Int = 0,
        napWakeP50Millis: Long? = null,
        napWakeP25Millis: Long? = null,
        napWakeP75Millis: Long? = null,
        bedtimeWakeIntervalCount: Int = 0,
        bedtimeWakeP50Millis: Long? = null,
        bedtimeWakeP25Millis: Long? = null,
        bedtimeWakeP75Millis: Long? = null,
    ) = SleepMetrics(
        lastWakeMillis = lastWakeMillis,
        lastSleepType = SleepType.NAP,
        lastSleepDurationMillis = Duration.ofMinutes(60).toMillis(),
        completedWakeIntervals = listOf(medianIntervalMillis),
        medianWakeIntervalMillis = medianIntervalMillis,
        wakeIntervalIqrMillis = Duration.ofMinutes(10).toMillis(),
        sleepLast24hMillis = Duration.ofHours(4).toMillis(),
        daySleepTodayMillis = Duration.ofHours(2).toMillis(),
        napCountToday = 1,    // < expectedNaps(20w)=2 → nextType=NAP
        medianBedtimeMinuteOfDay = null,
        medianMorningWakeMinuteOfDay = null,
        napWakeIntervalCount = napWakeIntervalCount,
        napWakeP25Millis = napWakeP25Millis,
        napWakeP50Millis = napWakeP50Millis,
        napWakeP75Millis = napWakeP75Millis,
        bedtimeWakeIntervalCount = bedtimeWakeIntervalCount,
        bedtimeWakeP25Millis = bedtimeWakeP25Millis,
        bedtimeWakeP50Millis = bedtimeWakeP50Millis,
        bedtimeWakeP75Millis = bedtimeWakeP75Millis,
    )

    private fun features(
        quality: EvidenceQuality = sufficientQuality(),
        metrics: SleepMetrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ),
        feedIntervals: List<BreastfeedInterval> = emptyList(),
        currentMinuteOfDay: Int? = null,
    ) = SleepFeatures(
        validIntervals = emptyList(),
        feedIntervals = feedIntervals,
        metrics = metrics,
        quality = quality,
        currentMinuteOfDay = currentMinuteOfDay,
    )

    @Test
    fun `returns Window when quality and metrics are sufficient`() {
        val result = SleepWindowPredictor.predict(features(), ageInWeeks, baseNow)
        assertInstanceOf(SleepPredictionState.Window::class.java, result)
    }

    @Test
    fun `returns NeedMoreData when not fresh`() {
        val staleQuality = sufficientQuality().copy(
            isFresh = false,
            hasSufficientZoneIndependentEvidence = false,
        )
        val result = SleepWindowPredictor.predict(features(quality = staleQuality), ageInWeeks, baseNow)
        assertInstanceOf(SleepPredictionState.NeedMoreData::class.java, result)
    }

    @Test
    fun `returns NeedMoreData when local day coverage insufficient`() {
        val lowCoverageQuality = sufficientQuality().copy(
            localDayCoverage = 1,
            isLocalDayCoverageSufficient = false,
        )
        val result = SleepWindowPredictor.predict(features(quality = lowCoverageQuality), ageInWeeks, baseNow)
        assertInstanceOf(SleepPredictionState.NeedMoreData::class.java, result)
    }

    @Test
    fun `returns NeedMoreData when lastWakeMillis is null`() {
        val metricsNoWake = sufficientMetrics(
            lastWakeMillis = 0L,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ).copy(lastWakeMillis = null)
        val result = SleepWindowPredictor.predict(features(metrics = metricsNoWake), ageInWeeks, baseNow)
        assertInstanceOf(SleepPredictionState.NeedMoreData::class.java, result)
    }

    @Test
    fun `returns NeedMoreData when medianWakeIntervalMillis is null`() {
        val metricsNoMedian = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ).copy(medianWakeIntervalMillis = null)
        val result = SleepWindowPredictor.predict(features(metrics = metricsNoMedian), ageInWeeks, baseNow)
        assertInstanceOf(SleepPredictionState.NeedMoreData::class.java, result)
    }

    @Test
    fun `returns AfterActiveFeed when open feed interval exists`() {
        val openFeed = BreastfeedInterval(
            startMillis = baseNow.minusSeconds(1800).toEpochMilli(),
            endMillis = null,
        )
        val result = SleepWindowPredictor.predict(features(feedIntervals = listOf(openFeed)), ageInWeeks, baseNow)
        assertInstanceOf(SleepPredictionState.AfterActiveFeed::class.java, result)
    }

    @Test
    fun `returns Overdue when window passed OVERDUE_GRACE_MINUTES ago`() {
        // lastWake = 5 h ago, qualityC ≈ 0.71 → wakeTarget ≈ 116 min → bestEstimate ≈ 184 min ago
        // windowEnd ≈ 169 min ago; now - windowEnd ≈ 124 min > 45 min grace → Overdue
        val lastWakeMillis = baseNow.minusSeconds(5 * 3600).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val result = SleepWindowPredictor.predict(features(metrics = metrics), ageInWeeks, baseNow)
        assertInstanceOf(SleepPredictionState.Overdue::class.java, result)
    }

    @Test
    fun `Window bestEstimate uses nap prior midpoint and type-specific P50 when sufficient type intervals`() {
        val fullQuality = sufficientQuality(completedCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS)
        val lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli()
        val personalP50 = Duration.ofMinutes(100).toMillis()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = personalP50,
            napWakeIntervalCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS,
            napWakeP50Millis = personalP50,
        )

        val result = SleepWindowPredictor.predict(features(quality = fullQuality, metrics = metrics), ageInWeeks, baseNow)
        val window = (result as SleepPredictionState.Window).window

        // getNapWakeWindowMidpoint(20w): getDefaultWakeWindows(20) = [105, 135, 150] → non-last = [105,135] avg = 120
        // qualityC = min(FULL_PERSONALIZATION_INTERVALS / FULL_PERSONALIZATION_INTERVALS, 1) = 1.0
        // wakeTarget = (1 - 0.6*1) * 120 + 0.6*1 * 100 = 0.4*120 + 0.6*100 = 48 + 60 = 108 min
        val priorMidpointMillis = Duration.ofMinutes(120).toMillis()
        val expectedTargetMillis = (0.4 * priorMidpointMillis + 0.6 * personalP50).toLong()
        val expectedBestEstimate = Instant.ofEpochMilli(lastWakeMillis + expectedTargetMillis)
        assertEquals(expectedBestEstimate, window.bestEstimate,
            "bestEstimate must use nap prior midpoint (120 min) + type-specific P50")
    }

    @Test
    fun `after NIGHT_SLEEP uses NAP prior regardless of nap count`() {
        val metricsAfterNight = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
            napWakeIntervalCount = SleepPredictionTuning.MIN_TYPE_INTERVALS,
            napWakeP50Millis = Duration.ofMinutes(90).toMillis(),
        ).copy(lastSleepType = SleepType.NIGHT_SLEEP, napCountToday = 99)
        val result = SleepWindowPredictor.predict(
            features(metrics = metricsAfterNight), ageInWeeks, baseNow
        )
        assertInstanceOf(SleepPredictionState.Window::class.java, result)
        val window = (result as SleepPredictionState.Window).window
        assertTrue(window.reasons.any { it.contains("personalized", ignoreCase = true) || it.contains("wake", ignoreCase = true) })
    }

    @Test
    fun `after last NAP of day uses bedtime prior when bedtime intervals sufficient`() {
        // ageInWeeks=20 → expectedNaps=2; napCountToday=2 → nextType=NIGHT_SLEEP
        val metricsLastNap = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(130).toMillis(),
            bedtimeWakeIntervalCount = SleepPredictionTuning.MIN_TYPE_INTERVALS,
            bedtimeWakeP50Millis = Duration.ofMinutes(150).toMillis(),
        ).copy(lastSleepType = SleepType.NAP, napCountToday = 2) // equals expectedNaps → bedtime
        val result = SleepWindowPredictor.predict(
            features(metrics = metricsLastNap), ageInWeeks, baseNow
        )
        assertInstanceOf(SleepPredictionState.Window::class.java, result)
    }

    @Test
    fun `resolveNextSleepType returns NAP after NIGHT_SLEEP regardless of napCountToday`() {
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ).copy(lastSleepType = SleepType.NIGHT_SLEEP, napCountToday = 99)
        assertEquals(SleepType.NAP, SleepWindowPredictor.resolveNextSleepType(metrics, ageInWeeks))
    }

    @Test
    fun `resolveNextSleepType returns NAP when napCountToday below expected`() {
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ).copy(lastSleepType = SleepType.NAP, napCountToday = 1)
        assertEquals(SleepType.NAP, SleepWindowPredictor.resolveNextSleepType(metrics, ageInWeeks))
    }

    @Test
    fun `resolveNextSleepType returns NIGHT_SLEEP when napCountToday equals expected`() {
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ).copy(lastSleepType = SleepType.NAP, napCountToday = 2) // expectedNaps(20w)=2
        assertEquals(SleepType.NIGHT_SLEEP, SleepWindowPredictor.resolveNextSleepType(metrics, ageInWeeks))
    }

    @Test
    fun `resolveNextSleepType returns NIGHT_SLEEP when napCountToday exceeds expected (extra nap day)`() {
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ).copy(lastSleepType = SleepType.NAP, napCountToday = 3)
        assertEquals(SleepType.NIGHT_SLEEP, SleepWindowPredictor.resolveNextSleepType(metrics, ageInWeeks))
    }

    @Test
    fun `resolveNextSleepType returns NAP after midnight when napCountToday resets to zero`() {
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ).copy(lastSleepType = SleepType.NAP, napCountToday = 0)
        assertEquals(SleepType.NAP, SleepWindowPredictor.resolveNextSleepType(metrics, ageInWeeks))
    }

    @Test
    fun `resolveNextSleepType returns NIGHT_SLEEP for skipped nap before learned bedtime threshold`() {
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ).copy(
            lastSleepType = SleepType.NAP,
            napCountToday = 1,
            medianBedtimeMinuteOfDay = 20 * 60,
        )
        assertEquals(
            SleepType.NIGHT_SLEEP,
            SleepWindowPredictor.resolveNextSleepType(metrics, ageInWeeks, currentMinuteOfDay = 19 * 60),
            "Skipped nap with time 60 min before learned bedtime must route to NIGHT_SLEEP",
        )
    }

    @Test
    fun `resolveNextSleepType returns NIGHT_SLEEP for skipped nap just after learned bedtime`() {
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ).copy(
            lastSleepType = SleepType.NAP,
            napCountToday = 1,
            medianBedtimeMinuteOfDay = 20 * 60,
        )
        assertEquals(
            SleepType.NIGHT_SLEEP,
            SleepWindowPredictor.resolveNextSleepType(metrics, ageInWeeks, currentMinuteOfDay = 20 * 60 + 30),
            "Skipped nap 30 min after learned bedtime must route to NIGHT_SLEEP",
        )
    }

    @Test
    fun `resolveNextSleepType returns NIGHT_SLEEP for skipped nap after bedtime across midnight`() {
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ).copy(
            lastSleepType = SleepType.NAP,
            napCountToday = 1,
            medianBedtimeMinuteOfDay = 23 * 60 + 30,
        )
        assertEquals(
            SleepType.NIGHT_SLEEP,
            SleepWindowPredictor.resolveNextSleepType(metrics, ageInWeeks, currentMinuteOfDay = 10),
            "Skipped nap 40 min after a 23:30 learned bedtime must route to NIGHT_SLEEP across midnight",
        )
    }

    @Test
    fun `resolveNextSleepType returns NAP for skipped nap when still far from bedtime`() {
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ).copy(
            lastSleepType = SleepType.NAP,
            napCountToday = 1,
            medianBedtimeMinuteOfDay = 20 * 60,
        )
        assertEquals(
            SleepType.NAP,
            SleepWindowPredictor.resolveNextSleepType(metrics, ageInWeeks, currentMinuteOfDay = 15 * 60),
            "Skipped nap with 300 min until learned bedtime must still route to NAP",
        )
    }

    @Test
    fun `resolveNextSleepType returns NAP for skipped nap when no learned bedtime available`() {
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ).copy(
            lastSleepType = SleepType.NAP,
            napCountToday = 1,
            medianBedtimeMinuteOfDay = null,
        )
        assertEquals(
            SleepType.NAP,
            SleepWindowPredictor.resolveNextSleepType(metrics, ageInWeeks, currentMinuteOfDay = 20 * 60),
            "Bedtime override must not fire when medianBedtimeMinuteOfDay is null",
        )
    }

    @Test
    fun `predict routes skipped nap to bedtime model using features currentMinuteOfDay`() {
        val lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli()
        val bedtimeP50 = Duration.ofMinutes(150).toMillis()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
            bedtimeWakeIntervalCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS,
            bedtimeWakeP50Millis = bedtimeP50,
        ).copy(
            lastSleepType = SleepType.NAP,
            napCountToday = 1,
            medianBedtimeMinuteOfDay = 15 * 60,
        )
        val result = SleepWindowPredictor.predict(
            features(
                quality = sufficientQuality(completedCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS),
                metrics = metrics,
                currentMinuteOfDay = 14 * 60,
            ),
            ageInWeeks,
            baseNow,
        )

        val window = (result as SleepPredictionState.Window).window
        val bedtimePriorMillis = Duration.ofMinutes(150).toMillis()
        val expectedTargetMillis = (0.4 * bedtimePriorMillis + 0.6 * bedtimeP50).toLong()
        val expectedBestEstimate = Instant.ofEpochMilli(lastWakeMillis + expectedTargetMillis)

        assertEquals(
            expectedBestEstimate,
            window.bestEstimate,
            "predict() must route through the bedtime model when currentMinuteOfDay is within learned bedtime cutoff",
        )
        assertTrue(
            window.reasons.any { it.contains("bedtime-specific wake patterns") },
            "Window reasons must show the bedtime-specific path; buildWindow likely did not pass features.currentMinuteOfDay",
        )
    }

    @Test
    fun `falls back to combined median when type-specific count below MIN_TYPE_INTERVALS`() {
        val metricsFewTypeIntervals = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
            napWakeIntervalCount = SleepPredictionTuning.MIN_TYPE_INTERVALS - 1,
            napWakeP50Millis = Duration.ofMinutes(200).toMillis(),
        )
        val result = SleepWindowPredictor.predict(features(metrics = metricsFewTypeIntervals), ageInWeeks, baseNow)
        val window = (result as SleepPredictionState.Window).window
        val unreasonableEstimate = Instant.ofEpochMilli(
            baseNow.minusSeconds(3600).toEpochMilli() + Duration.ofMinutes(180).toMillis()
        )
        assertTrue(window.bestEstimate.isBefore(unreasonableEstimate),
            "bestEstimate should not be driven by ignored type-specific P50 when count < MIN_TYPE_INTERVALS")
    }

    @Test
    fun `dynamic half-window is wider when P25-P75 spread is large`() {
        val lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli()
        val metricsWideSpread = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
            napWakeIntervalCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS,
            napWakeP50Millis = Duration.ofMinutes(90).toMillis(),
            napWakeP25Millis = Duration.ofMinutes(60).toMillis(),
            napWakeP75Millis = Duration.ofMinutes(180).toMillis(),
        )
        val result = SleepWindowPredictor.predict(features(metrics = metricsWideSpread), ageInWeeks, baseNow)
        val window = (result as SleepPredictionState.Window).window
        val halfWindowMinutes = Duration.between(window.bestEstimate, window.windowEnd).toMinutes()
        assertTrue(halfWindowMinutes > SleepPredictionTuning.MIN_HALF_WINDOW_MINUTES,
            "Wide P25-P75 spread should produce wider than minimum half-window; got $halfWindowMinutes min")
    }

    @Test
    fun `dynamic half-window is capped at MAX_HALF_WINDOW_MINUTES`() {
        val lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli()
        val metricsExtremeSpread = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
            napWakeIntervalCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS,
            napWakeP50Millis = Duration.ofMinutes(90).toMillis(),
            napWakeP25Millis = Duration.ofMinutes(30).toMillis(),
            napWakeP75Millis = Duration.ofMinutes(600).toMillis(),
        )
        val result = SleepWindowPredictor.predict(features(metrics = metricsExtremeSpread), ageInWeeks, baseNow)
        val window = (result as SleepPredictionState.Window).window
        val halfWindowMinutes = Duration.between(window.bestEstimate, window.windowEnd).toMinutes()
        assertEquals(SleepPredictionTuning.MAX_HALF_WINDOW_MINUTES, halfWindowMinutes,
            "Half-window must be capped at MAX_HALF_WINDOW_MINUTES")
    }

    @Test
    fun `dynamic half-window defaults to MIN when P25-P75 unavailable`() {
        val lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli()
        val metricsNoQuartiles = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val result = SleepWindowPredictor.predict(features(metrics = metricsNoQuartiles), ageInWeeks, baseNow)
        val window = (result as SleepPredictionState.Window).window
        val halfWindowMinutes = Duration.between(window.bestEstimate, window.windowEnd).toMinutes()
        assertEquals(SleepPredictionTuning.MIN_HALF_WINDOW_MINUTES, halfWindowMinutes,
            "Half-window should be MIN when no quartile data")
    }

    @Test
    fun `Window confidence is LOW when completedIntervalCount below half of FULL_PERSONALIZATION_INTERVALS`() {
        val lowCountQuality = sufficientQuality(completedCount = 3) // 3/14 < 0.5
        val result = SleepWindowPredictor.predict(features(quality = lowCountQuality), ageInWeeks, baseNow)
        val window = (result as SleepPredictionState.Window).window
        assertEquals(Confidence.LOW, window.confidence)
    }

    @Test
    fun `Window feedPrompt is non-null when predicted next feed overlaps window`() {
        // Two completed feeds, 3 h apart; avgInterval = 3 h.
        // lastFeed started at baseNow - 2h → predictedNextFeed = baseNow + 1h.
        // lastWake = 1h ago, bestEstimate ≈ baseNow + 35 min → window ends ≈ baseNow + 50 min.
        // predictedNextFeed (baseNow + 1h) is within windowEnd + 30 min tolerance → non-null prompt.
        val feed1 = BreastfeedInterval(
            startMillis = baseNow.minusSeconds(2 * 3600).toEpochMilli(),
            endMillis = baseNow.minusSeconds(2 * 3600 - 1800).toEpochMilli(),
        )
        val feed2 = BreastfeedInterval(
            startMillis = baseNow.minusSeconds(5 * 3600).toEpochMilli(),
            endMillis = baseNow.minusSeconds(5 * 3600 - 1800).toEpochMilli(),
        )
        val result = SleepWindowPredictor.predict(features(feedIntervals = listOf(feed1, feed2)), ageInWeeks, baseNow)
        val window = (result as SleepPredictionState.Window).window
        assertEquals(true, window.feedPrompt != null, "Expected non-null feedPrompt when predicted feed overlaps window")
    }

    @Test
    fun `Window feedPrompt is null when no recent completed feeds`() {
        val result = SleepWindowPredictor.predict(features(feedIntervals = emptyList()), ageInWeeks, baseNow)
        val window = (result as SleepPredictionState.Window).window
        assertEquals(true, window.feedPrompt == null, "Expected null feedPrompt when no feeds")
    }

    @Test
    fun `ALGORITHM_VERSION is phase2 circadian history version`() {
        assertEquals(
            "sleep-pred-phase2-circadian-history-1",
            SleepPredictionTuning.ALGORITHM_VERSION,
            "ALGORITHM_VERSION must be bumped when changing prediction algorithm",
        )
    }
}
