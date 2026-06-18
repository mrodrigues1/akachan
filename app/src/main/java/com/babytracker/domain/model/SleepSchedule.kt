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
    val napTransitionSuggestion: NapTransition?,
    val lastFeedTime: Instant?,
    val isPersonalized: Boolean
)

data class ScheduleEntry(
    val startTime: LocalTime,
    val duration: Duration,
    val napNumber: Int,
    val isAdjusted: Boolean = false,
    val emoji: String = "😴"
)

enum class ScheduleMode {
    DEMAND_DRIVEN,
    CLOCK_ALIGNED
}

/**
 * Known infant sleep regression. Locale-agnostic; the UI resolves [type] to a name,
 * description, and typical-duration string (see `ui/sleep/RegressionText.kt`).
 */
data class RegressionInfo(val type: RegressionType)

enum class RegressionType {
    FOUR_MONTH,
    EIGHT_TO_TEN_MONTH,
    TWELVE_MONTH,
}

/**
 * Suggested nap-count transition. The UI formats it into a localized sentence.
 */
data class NapTransition(
    val fromNaps: Int,
    val toNaps: Int,
    val avgNapsPerDay: Double,
)
