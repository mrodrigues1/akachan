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

    private fun sufficientMetrics(lastWakeMillis: Long, medianIntervalMillis: Long) = SleepMetrics(
        lastWakeMillis = lastWakeMillis,
        lastSleepType = SleepType.NAP,
        lastSleepDurationMillis = Duration.ofMinutes(60).toMillis(),
        completedWakeIntervals = listOf(medianIntervalMillis),
        medianWakeIntervalMillis = medianIntervalMillis,
        wakeIntervalIqrMillis = Duration.ofMinutes(10).toMillis(),
        sleepLast24hMillis = Duration.ofHours(4).toMillis(),
        daySleepTodayMillis = Duration.ofHours(2).toMillis(),
        napCountToday = 2,
        medianBedtimeMinuteOfDay = null,
        medianMorningWakeMinuteOfDay = null,
    )

    private fun features(
        quality: EvidenceQuality = sufficientQuality(),
        metrics: SleepMetrics = sufficientMetrics(
            lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli(),
            medianIntervalMillis = Duration.ofMinutes(90).toMillis(),
        ),
        feedIntervals: List<BreastfeedInterval> = emptyList(),
    ) = SleepFeatures(
        validIntervals = emptyList(),
        feedIntervals = feedIntervals,
        metrics = metrics,
        quality = quality,
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
    fun `Window bestEstimate is blend of age prior and personal median`() {
        // We need sufficient quality to get a Window — use fully personalized quality
        val fullQuality = sufficientQuality(completedCount = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS)
        val lastWakeMillis = baseNow.minusSeconds(3600).toEpochMilli()
        val personalMedian = Duration.ofMinutes(120).toMillis()
        val metrics = sufficientMetrics(lastWakeMillis, personalMedian)

        val result = SleepWindowPredictor.predict(features(quality = fullQuality, metrics = metrics), ageInWeeks, baseNow)
        val window = (result as SleepPredictionState.Window).window
        assertEquals(Confidence.MEDIUM, window.confidence)
        // At full personalization (qualityC=1): wakeTarget = 0.4*agePrior + 0.6*personal
        // agePrior midpoint for 20w = (90+180)/2 = 135 min
        val expectedTargetMillis = (0.4 * Duration.ofMinutes(135).toMillis() + 0.6 * personalMedian).toLong()
        val expectedBestEstimate = Instant.ofEpochMilli(lastWakeMillis + expectedTargetMillis)
        assertEquals(expectedBestEstimate, window.bestEstimate)
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
    fun `ALGORITHM_VERSION is phase2 personalized wake version`() {
        assertEquals(
            "sleep-pred-phase2-personalized-wake-1",
            SleepPredictionTuning.ALGORITHM_VERSION,
            "ALGORITHM_VERSION must be bumped when changing prediction algorithm",
        )
    }
}
