package com.babytracker.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalTime

data class SleepSchedule(
    val ageInWeeks: Int,
    val mode: ScheduleMode,
    val wakeWindows: List<Duration>,
    val napTimes: List<ScheduleEntry>,
    val bedtime: LocalTime,
    val bedtimeWindow: ClosedRange<LocalTime>,
    val totalSleepRecommendation: ClosedRange<Duration>,
    val totalSleepLogged: Duration?,
    val regressionWarning: RegressionInfo?,
    val napTransitionSuggestion: String?,
    val lastFeedTime: Instant?,
    val isPersonalized: Boolean
)

data class ScheduleEntry(
    val startTime: LocalTime,
    val duration: Duration,
    val label: String,
    val isAdjusted: Boolean = false
)

enum class ScheduleMode(val label: String) {
    DEMAND_DRIVEN("Demand-driven"),
    CLOCK_ALIGNED("Clock-aligned")
}

data class RegressionInfo(
    val name: String,
    val description: String,
    val durationWeeks: String
)
