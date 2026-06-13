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
            circadianFactorProvider = { _, _, _, _, _ -> SleepPredictionFactor.Neutral },
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
            "Half-window should be MIN when no quartile data and combined IQR below floor")
    }

    @Test
    fun `dynamic half-window falls back to combined IQR at exactly MIN_TYPE_INTERVALS without type quartiles`() {
        // AKA-127: at exactly 3 type intervals the type P50 center is trusted, but type-specific
        // quartiles need 4 → they are null. The half-window must bridge with the combined-history
        // IQR rather than flooring to MIN, otherwise a confident center pairs with a too-narrow window.
        // IQR stays within INSTABILITY_CEILING_MINUTES so this mirrors a state the quality gate accepts.
        val lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli()
        val combinedIqrMillis = Duration.ofMinutes(SleepPredictionTuning.INSTABILITY_CEILING_MINUTES - 1).toMillis()
        val metricsBoundary = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
            napWakeIntervalCount = SleepPredictionTuning.MIN_TYPE_INTERVALS,
            napWakeP50Millis = Duration.ofMinutes(90).toMillis(),
        ).copy(wakeIntervalIqrMillis = combinedIqrMillis)
        val result = SleepWindowPredictor.predict(features(metrics = metricsBoundary), ageInWeeks, baseNow)
        val window = (result as SleepPredictionState.Window).window
        val halfWindowMinutes = Duration.between(window.bestEstimate, window.windowEnd).toMinutes()
        assertEquals(
            Duration.ofMillis(combinedIqrMillis / 2).toMinutes(),
            halfWindowMinutes,
            "Half-window should derive from combined IQR (IQR/2) when only the type P50 is available",
        )
        assertTrue(halfWindowMinutes > SleepPredictionTuning.MIN_HALF_WINDOW_MINUTES,
            "Combined-IQR fallback must widen beyond MIN at the 3-interval boundary; got $halfWindowMinutes min")
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
    fun `circadian factor shifts bedtime estimate toward bedtime slot`() {
        val lastWakeMillis = baseNow.minus(Duration.ofMinutes(30)).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(120).toMillis(),
            bedtimeWakeIntervalCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS,
            bedtimeWakeP50Millis = Duration.ofMinutes(120).toMillis(),
        ).copy(lastSleepType = SleepType.NAP, napCountToday = 2)

        val result = SleepWindowPredictor.predict(
            features(
                quality = sufficientQuality(completedCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS),
                metrics = metrics,
                currentMinuteOfDay = 18 * 60,
            ),
            ageInWeeks,
            baseNow,
            circadianFactorProvider = CircadianBiasFactor::adjustment,
        )

        val window = (result as SleepPredictionState.Window).window
        assertTrue(
            window.reasons.any { it.contains("circadian", ignoreCase = true) },
            "Circadian wiring must expose a reason when the factor changes the candidate",
        )
    }

    @Test
    fun `positive circadian adjustment cannot revive stale raw window`() {
        val lastWakeMillis = baseNow.minus(Duration.ofHours(8)).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )

        val result = SleepWindowPredictor.predict(
            features(metrics = metrics, currentMinuteOfDay = 18 * 60),
            ageInWeeks,
            baseNow,
            circadianFactorProvider = { _, _, _, _, _ ->
                SleepPredictionFactor(Duration.ofMinutes(SleepPredictionTuning.CIRCADIAN_MAX_SHIFT_MINUTES))
            },
        )

        assertInstanceOf(
            SleepPredictionState.Overdue::class.java,
            result,
            "Raw stale windows must stay Overdue even when a factor would shift the adjusted estimate forward",
        )
    }

    @Test
    fun `negative circadian adjustment cannot return a stale final window`() {
        val lastWakeMillis = baseNow.minus(Duration.ofMinutes(150)).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )

        val result = SleepWindowPredictor.predict(
            features(metrics = metrics, currentMinuteOfDay = 18 * 60),
            ageInWeeks,
            baseNow,
            circadianFactorProvider = { _, _, _, _, _ ->
                SleepPredictionFactor(Duration.ofMinutes(-SleepPredictionTuning.CIRCADIAN_MAX_SHIFT_MINUTES))
            },
        )

        assertInstanceOf(
            SleepPredictionState.Overdue::class.java,
            result,
            "Adjusted windows must be rechecked for staleness before returning Window",
        )
    }

    @Test
    fun `returns Window at exact overdue grace boundary`() {
        val lastWakeMillis = baseNow.minus(Duration.ofMinutes(150)).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )

        val result = SleepWindowPredictor.predict(features(metrics = metrics), ageInWeeks, baseNow)

        assertInstanceOf(
            SleepPredictionState.Window::class.java,
            result,
            "Exact windowEnd + grace boundary must still be a Window; stale starts only after grace",
        )
    }

    @Test
    fun `stale anchor returns Overdue instead of shifting best estimate to now`() {
        val lastWakeMillis = baseNow.minus(Duration.ofHours(8)).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )

        val result = SleepWindowPredictor.predict(features(metrics = metrics), ageInWeeks, baseNow)

        assertInstanceOf(SleepPredictionState.Overdue::class.java, result)
    }

    @Test
    fun `sleep-debt factor shifts window earlier when injected with negative adjustment`() {
        val lastWakeMillis = baseNow.minus(Duration.ofMinutes(60)).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val neutralResult = SleepWindowPredictor.predict(
            features(metrics = metrics), ageInWeeks, baseNow,
            sleepDebtFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral },
        )
        val debtResult = SleepWindowPredictor.predict(
            features(metrics = metrics), ageInWeeks, baseNow,
            sleepDebtFactorProvider = { _, _, _ ->
                SleepPredictionFactor(adjustment = Duration.ofMinutes(-10))
            },
        )
        val neutralEstimate = (neutralResult as SleepPredictionState.Window).window.bestEstimate
        val debtEstimate = (debtResult as SleepPredictionState.Window).window.bestEstimate
        assertTrue(debtEstimate.isBefore(neutralEstimate),
            "Sleep-debt factor with -10 min adjustment must shift bestEstimate earlier")
    }

    @Test
    fun `sleep-debt factor reason appears in window reasons`() {
        val lastWakeMillis = baseNow.minus(Duration.ofMinutes(60)).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val result = SleepWindowPredictor.predict(
            features(metrics = metrics), ageInWeeks, baseNow,
            sleepDebtFactorProvider = { _, _, _ ->
                SleepPredictionFactor(adjustment = Duration.ofMinutes(-10), reason = "sleep debt reason")
            },
        )
        val window = (result as SleepPredictionState.Window).window
        assertTrue(window.reasons.contains("sleep debt reason"),
            "Factor reason must appear in window.reasons")
    }

    @Test
    fun `negative sleep-debt adjustment cannot make a raw-fresh window stale`() {
        val lastWakeMillis = baseNow.minus(Duration.ofMinutes(60)).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val result = SleepWindowPredictor.predict(
            features(metrics = metrics), ageInWeeks, baseNow,
            sleepDebtFactorProvider = { _, _, _ ->
                SleepPredictionFactor(adjustment = Duration.ofMinutes(-SleepPredictionTuning.SLEEP_DEBT_MAX_SHIFT_MINUTES))
            },
        )
        assertInstanceOf(SleepPredictionState.Window::class.java, result)
    }

    @Test
    fun `large negative sleep-debt adjustment on near-stale window returns Overdue`() {
        val lastWakeMillis = baseNow.minus(Duration.ofHours(5)).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val result = SleepWindowPredictor.predict(
            features(metrics = metrics), ageInWeeks, baseNow,
            sleepDebtFactorProvider = { _, _, _ ->
                SleepPredictionFactor(adjustment = Duration.ofMinutes(-SleepPredictionTuning.SLEEP_DEBT_MAX_SHIFT_MINUTES))
            },
        )
        assertInstanceOf(SleepPredictionState.Overdue::class.java, result,
            "Raw-stale window must return Overdue even when debt factor is applied")
    }

    @Test
    fun `ALGORITHM_VERSION is phase4 iqr-fallback version`() {
        assertEquals(
            "sleep-pred-phase4-iqr-fallback-1",
            SleepPredictionTuning.ALGORITHM_VERSION,
            "ALGORITHM_VERSION must be bumped when changing prediction algorithm",
        )
    }

    @Test
    fun `active disruption lowers HIGH confidence to MEDIUM`() {
        val quality = sufficientQuality(completedCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS)
            .copy(hasQualifiedTimezoneProvenance = true)
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val result = SleepWindowPredictor.predict(
            features(quality = quality, metrics = metrics).copy(hasActiveDisruption = true),
            ageInWeeks,
            baseNow,
        )

        assertInstanceOf(SleepPredictionState.Window::class.java, result)
        assertEquals(Confidence.MEDIUM, (result as SleepPredictionState.Window).window.confidence)
    }

    @Test
    fun `active disruption lowers MEDIUM confidence to LOW`() {
        val quality = sufficientQuality(completedCount = 7) // qualityC = 7/14 = 0.5 → MEDIUM
            .copy(hasQualifiedTimezoneProvenance = false)
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val result = SleepWindowPredictor.predict(
            features(quality = quality, metrics = metrics).copy(hasActiveDisruption = true),
            ageInWeeks,
            baseNow,
        )

        assertInstanceOf(SleepPredictionState.Window::class.java, result)
        assertEquals(Confidence.LOW, (result as SleepPredictionState.Window).window.confidence)
    }

    @Test
    fun `active disruption keeps LOW confidence at LOW`() {
        val quality = sufficientQuality(completedCount = SleepPredictionTuning.MIN_COMPLETED_INTERVALS)
            .copy(hasQualifiedTimezoneProvenance = false)
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val result = SleepWindowPredictor.predict(
            features(quality = quality, metrics = metrics).copy(hasActiveDisruption = true),
            ageInWeeks,
            baseNow,
        )

        assertInstanceOf(SleepPredictionState.Window::class.java, result)
        assertEquals(Confidence.LOW, (result as SleepPredictionState.Window).window.confidence)
    }

    @Test
    fun `no disruption leaves confidence unchanged`() {
        val quality = sufficientQuality(completedCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS)
            .copy(hasQualifiedTimezoneProvenance = true)
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val result = SleepWindowPredictor.predict(
            features(quality = quality, metrics = metrics).copy(hasActiveDisruption = false),
            ageInWeeks,
            baseNow,
        )

        assertInstanceOf(SleepPredictionState.Window::class.java, result)
        assertEquals(Confidence.HIGH, (result as SleepPredictionState.Window).window.confidence)
    }

    @Test
    fun `cue does not shift window best-estimate time — disruption is annotation only`() {
        val quality = sufficientQuality(completedCount = 10)
        val lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli()
        val medianMillis = Duration.ofMinutes(90).toMillis()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = medianMillis,
        )
        val withoutDisruption = SleepWindowPredictor.predict(
            features(quality = quality, metrics = metrics).copy(hasActiveDisruption = false),
            ageInWeeks,
            baseNow,
        ) as SleepPredictionState.Window
        val withDisruption = SleepWindowPredictor.predict(
            features(quality = quality, metrics = metrics).copy(hasActiveDisruption = true),
            ageInWeeks,
            baseNow,
        ) as SleepPredictionState.Window

        assertEquals(withoutDisruption.window.bestEstimate, withDisruption.window.bestEstimate)
        assertEquals(withoutDisruption.window.windowStart, withDisruption.window.windowStart)
        assertEquals(withoutDisruption.window.windowEnd, withDisruption.window.windowEnd)
    }

    @Test
    fun `nap-budget factor shifts NAP window earlier when injected with negative adjustment`() {
        val lastWakeMillis = baseNow.minus(Duration.ofMinutes(60)).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ).copy(lastSleepType = SleepType.NAP, napCountToday = 1)

        val neutralResult = SleepWindowPredictor.predict(
            features(metrics = metrics), ageInWeeks, baseNow,
            napBudgetFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral },
        )
        val budgetResult = SleepWindowPredictor.predict(
            features(metrics = metrics), ageInWeeks, baseNow,
            napBudgetFactorProvider = { _, _, _ ->
                SleepPredictionFactor(adjustment = Duration.ofMinutes(-15))
            },
        )
        val neutralEstimate = (neutralResult as SleepPredictionState.Window).window.bestEstimate
        val budgetEstimate = (budgetResult as SleepPredictionState.Window).window.bestEstimate
        assertTrue(
            budgetEstimate.isBefore(neutralEstimate),
            "Nap-budget factor with -15 min adjustment must shift bestEstimate earlier",
        )
    }

    @Test
    fun `nap-budget factor does not apply to NIGHT_SLEEP predictions (real factor returns Neutral)`() {
        val lastWakeMillis = baseNow.minus(Duration.ofMinutes(60)).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(150).toMillis(),
        ).copy(lastSleepType = SleepType.NAP, napCountToday = 2)

        val neutralResult = SleepWindowPredictor.predict(
            features(metrics = metrics), ageInWeeks, baseNow,
            napBudgetFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral },
        )
        val realBudgetResult = SleepWindowPredictor.predict(
            features(metrics = metrics), ageInWeeks, baseNow,
            napBudgetFactorProvider = NapBudgetFactor::adjustment,
        )
        val neutralEstimate = (neutralResult as SleepPredictionState.Window).window.bestEstimate
        val realEstimate = (realBudgetResult as SleepPredictionState.Window).window.bestEstimate
        assertEquals(
            neutralEstimate,
            realEstimate,
            "Real NapBudgetFactor must return Neutral for NIGHT_SLEEP -> bestEstimate unchanged",
        )
    }

    @Test
    fun `nap-budget factor reason appears in window reasons`() {
        val lastWakeMillis = baseNow.minus(Duration.ofMinutes(60)).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val result = SleepWindowPredictor.predict(
            features(metrics = metrics), ageInWeeks, baseNow,
            napBudgetFactorProvider = { _, _, _ ->
                SleepPredictionFactor(adjustment = Duration.ofMinutes(-10), reason = "nap budget reason")
            },
        )
        val window = (result as SleepPredictionState.Window).window
        assertTrue(
            window.reasons.contains("nap budget reason"),
            "Factor reason must appear in window.reasons",
        )
    }

    @Test
    fun `large negative nap-budget adjustment cannot make a raw-fresh window stale`() {
        val lastWakeMillis = baseNow.minus(Duration.ofMinutes(60)).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val result = SleepWindowPredictor.predict(
            features(metrics = metrics), ageInWeeks, baseNow,
            napBudgetFactorProvider = { _, _, _ ->
                SleepPredictionFactor(adjustment = Duration.ofMinutes(-SleepPredictionTuning.NAP_BUDGET_MAX_SHIFT_MINUTES))
            },
        )
        assertInstanceOf(SleepPredictionState.Window::class.java, result)
    }

    @Test
    fun `summed factor shift exceeding cap is clamped to MAX_TOTAL_FACTOR_SHIFT_MINUTES`() {
        val lastWakeMillis = baseNow.minus(Duration.ofMinutes(60)).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val neutral = SleepWindowPredictor.predict(
            features(metrics = metrics), ageInWeeks, baseNow,
        ) as SleepPredictionState.Window
        // Two factors of -30 min each → raw sum -60 min, exceeds the 45 min cap.
        val clamped = SleepWindowPredictor.predict(
            features(metrics = metrics), ageInWeeks, baseNow,
            sleepDebtFactorProvider = { _, _, _ -> SleepPredictionFactor(adjustment = Duration.ofMinutes(-30)) },
            napBudgetFactorProvider = { _, _, _ -> SleepPredictionFactor(adjustment = Duration.ofMinutes(-30)) },
        ) as SleepPredictionState.Window
        val shiftMinutes = Duration.between(clamped.window.bestEstimate, neutral.window.bestEstimate).toMinutes()
        assertEquals(
            SleepPredictionTuning.MAX_TOTAL_FACTOR_SHIFT_MINUTES, shiftMinutes,
            "Summed factor shift beyond the cap must be clamped to MAX_TOTAL_FACTOR_SHIFT_MINUTES",
        )
    }

    @Test
    fun `summed factor shift within cap is applied unchanged`() {
        val lastWakeMillis = baseNow.minus(Duration.ofMinutes(60)).toEpochMilli()
        val metrics = sufficientMetrics(
            lastWakeMillis = lastWakeMillis,
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val neutral = SleepWindowPredictor.predict(
            features(metrics = metrics), ageInWeeks, baseNow,
        ) as SleepPredictionState.Window
        // Two factors of -10 min each → raw sum -20 min, within the 45 min cap.
        val applied = SleepWindowPredictor.predict(
            features(metrics = metrics), ageInWeeks, baseNow,
            sleepDebtFactorProvider = { _, _, _ -> SleepPredictionFactor(adjustment = Duration.ofMinutes(-10)) },
            napBudgetFactorProvider = { _, _, _ -> SleepPredictionFactor(adjustment = Duration.ofMinutes(-10)) },
        ) as SleepPredictionState.Window
        val shiftMinutes = Duration.between(applied.window.bestEstimate, neutral.window.bestEstimate).toMinutes()
        assertEquals(
            20L, shiftMinutes,
            "Summed factor shift within the cap must be applied unchanged",
        )
    }

    @Test
    fun `confidence is MEDIUM without qualified timezone provenance even when qualityC is high`() {
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val quality = sufficientQuality(completedCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS)
            .copy(hasQualifiedTimezoneProvenance = false)

        val result = SleepWindowPredictor.predict(features(quality = quality, metrics = metrics), ageInWeeks, baseNow)

        assertInstanceOf(SleepPredictionState.Window::class.java, result)
        assertEquals(Confidence.MEDIUM, (result as SleepPredictionState.Window).window.confidence)
    }

    @Test
    fun `confidence is HIGH with qualified timezone provenance and high qualityC`() {
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val quality = sufficientQuality(completedCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS)
            .copy(hasQualifiedTimezoneProvenance = true)

        val result = SleepWindowPredictor.predict(features(quality = quality, metrics = metrics), ageInWeeks, baseNow)

        assertInstanceOf(SleepPredictionState.Window::class.java, result)
        assertEquals(Confidence.HIGH, (result as SleepPredictionState.Window).window.confidence)
    }

    @Test
    fun `confidence is LOW when qualityC is low regardless of provenance`() {
        val metrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        )
        val quality = sufficientQuality(completedCount = SleepPredictionTuning.MIN_COMPLETED_INTERVALS)
            .copy(hasQualifiedTimezoneProvenance = true)

        val result = SleepWindowPredictor.predict(features(quality = quality, metrics = metrics), ageInWeeks, baseNow)

        assertInstanceOf(SleepPredictionState.Window::class.java, result)
        assertEquals(Confidence.LOW, (result as SleepPredictionState.Window).window.confidence)
    }
}
