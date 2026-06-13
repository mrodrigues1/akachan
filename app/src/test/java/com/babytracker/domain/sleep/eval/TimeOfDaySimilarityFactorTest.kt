package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepType
import com.babytracker.domain.sleep.feature.SleepMetrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class TimeOfDaySimilarityFactorTest {

    @Test
    fun `disabled when timezone provenance is unqualified`() {
        val factor = TimeOfDaySimilarityFactor.adjustment(
            metrics = metricsWithBedtimeHistory(),
            nextType = SleepType.NIGHT_SLEEP,
            candidateMinuteOfDay = 18 * 60,
            hasQualifiedTimezoneProvenance = false,
        )

        assertEquals(SleepPredictionFactor.Neutral, factor)
    }

    @Test
    fun `neutral when provenance is qualified but history is missing`() {
        val factor = TimeOfDaySimilarityFactor.adjustment(
            metrics = metricsWithBedtimeHistory().copy(medianBedtimeMinuteOfDay = null),
            nextType = SleepType.NIGHT_SLEEP,
            candidateMinuteOfDay = 18 * 60,
            hasQualifiedTimezoneProvenance = true,
        )

        assertEquals(SleepPredictionFactor.Neutral, factor)
    }

    private fun metricsWithBedtimeHistory() = SleepMetrics(
        lastWakeMillis = 0,
        lastSleepType = SleepType.NAP,
        lastSleepDurationMillis = Duration.ofMinutes(60).toMillis(),
        completedWakeIntervals = List(10) { Duration.ofMinutes(90).toMillis() },
        medianWakeIntervalMillis = Duration.ofMinutes(90).toMillis(),
        wakeIntervalIqrMillis = Duration.ofMinutes(10).toMillis(),
        sleepLast24hMillis = Duration.ofHours(4).toMillis(),
        daySleepTodayMillis = Duration.ofHours(2).toMillis(),
        napCountToday = 2,
        medianBedtimeMinuteOfDay = 19 * 60,
        medianMorningWakeMinuteOfDay = 7 * 60,
    )
}
