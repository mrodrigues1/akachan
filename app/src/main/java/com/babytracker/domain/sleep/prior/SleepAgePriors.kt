package com.babytracker.domain.sleep.prior

import com.babytracker.domain.model.RegressionInfo
import java.time.Duration
import java.time.LocalTime

object SleepAgePriors {

    fun getDefaultWakeWindows(ageInWeeks: Int): List<Duration> = when {
        ageInWeeks < 6 -> listOf(45, 45, 45, 45, 45).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 8 -> listOf(60, 60, 75, 75).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 12 -> listOf(75, 80, 90, 90).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 16 -> listOf(90, 105, 120).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 24 -> listOf(105, 135, 150).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 36 -> listOf(150, 180, 210).map { Duration.ofMinutes(it.toLong()) }
        else -> listOf(180, 210, 240).map { Duration.ofMinutes(it.toLong()) }
    }

    fun getWakeWindowBounds(ageInWeeks: Int): Pair<Duration, Duration> = when {
        ageInWeeks < 6 -> Duration.ofMinutes(30) to Duration.ofMinutes(60)
        ageInWeeks < 8 -> Duration.ofMinutes(45) to Duration.ofMinutes(90)
        ageInWeeks < 12 -> Duration.ofMinutes(60) to Duration.ofMinutes(120)
        ageInWeeks < 16 -> Duration.ofMinutes(75) to Duration.ofMinutes(150)
        ageInWeeks < 24 -> Duration.ofMinutes(90) to Duration.ofMinutes(180)
        ageInWeeks < 36 -> Duration.ofMinutes(120) to Duration.ofMinutes(210)
        else -> Duration.ofMinutes(150) to Duration.ofMinutes(240)
    }

    fun getBedtimeWindow(ageInWeeks: Int): ClosedRange<LocalTime> = when {
        ageInWeeks < 6 -> LocalTime.of(21, 0)..LocalTime.of(23, 0)
        ageInWeeks < 12 -> LocalTime.of(20, 0)..LocalTime.of(22, 0)
        ageInWeeks < 16 -> LocalTime.of(19, 30)..LocalTime.of(21, 0)
        ageInWeeks < 24 -> LocalTime.of(19, 0)..LocalTime.of(20, 0)
        else -> LocalTime.of(18, 30)..LocalTime.of(20, 0)
    }

    fun getTotalSleepRecommendation(ageInWeeks: Int): ClosedRange<Duration> = when {
        ageInWeeks < 16 -> Duration.ofHours(14)..Duration.ofHours(17)
        else -> Duration.ofHours(12)..Duration.ofHours(16)
    }

    // Biological nap-count threshold for transition detection (sleep science), NOT the
    // count the scheduler generates. For ages < 24 weeks this is one higher than
    // getScheduledNapCount — young infants have no distinct bedtime in the sleep-science model.
    // Only use this in detectNapTransition; use getScheduledNapCount for prediction/scheduling.
    fun getNapTransitionThreshold(ageInWeeks: Int): Int = when {
        ageInWeeks < 6 -> 5
        ageInWeeks < 12 -> 4
        ageInWeeks < 16 -> 3
        ageInWeeks < 24 -> 3
        ageInWeeks < 36 -> 2
        else -> 2
    }

    fun getScheduledNapCount(ageInWeeks: Int): Int = getDefaultWakeWindows(ageInWeeks).size - 1

    fun getNapWakeWindowMidpoint(ageInWeeks: Int): Duration {
        val windows = getDefaultWakeWindows(ageInWeeks)
        val napWindows = if (windows.size > 1) windows.dropLast(1) else windows
        val avgMillis = napWindows.map { it.toMillis() }.average().toLong()
        return Duration.ofMillis(avgMillis)
    }

    fun getPreBedtimeWakeWindowMidpoint(ageInWeeks: Int): Duration =
        getDefaultWakeWindows(ageInWeeks).last()

    fun detectRegression(ageInWeeks: Int): RegressionInfo? = when {
        ageInWeeks in 14..22 -> RegressionInfo(
            name = "4-Month Sleep Regression",
            description = "Your baby's sleep architecture is maturing from 2-stage to 4-stage cycles. " +
                "This is a permanent and healthy change. Sleep may be more disrupted than usual.",
            durationWeeks = "2-6 weeks"
        )
        ageInWeeks in 32..44 -> RegressionInfo(
            name = "8-10 Month Sleep Regression",
            description = "Object permanence, separation anxiety, and motor milestones (crawling, pulling up) " +
                "can temporarily disrupt sleep patterns.",
            durationWeeks = "2-6 weeks"
        )
        ageInWeeks in 48..55 -> RegressionInfo(
            name = "12-Month Sleep Regression",
            description = "Walking, early language development, and nap resistance may cause temporary " +
                "sleep disruption.",
            durationWeeks = "1-3 weeks"
        )
        else -> null
    }
}
