package com.babytracker.domain.trends

import java.time.LocalDate

/** Selectable look-back windows for the Trends screen. */
enum class TrendRange(val days: Int) {
    SEVEN_DAYS(7),
    FOURTEEN_DAYS(14),
    THIRTY_DAYS(30),
}

/** Number of feeds (breastfeeding sessions + bottle feeds) on one local day. */
data class DailyFeedingCount(val date: LocalDate, val count: Int)

/** Sleep hours on one local day, split by sleep type. */
data class DailySleepDuration(
    val date: LocalDate,
    val nightHours: Double,
    val napHours: Double,
) {
    val totalHours: Double get() = nightHours + napHours
}

/** Mean hours between consecutive same-day feeds; null when fewer than two feeds that day. */
data class DailyFeedingInterval(val date: LocalDate, val averageHours: Double?)

/** Daily feed count paired with total sleep hours, for the Feeds-vs-Sleep overlay chart. */
data class DailyFeedVsSleep(
    val date: LocalDate,
    val feedCount: Int,
    val sleepHours: Double,
)

/**
 * One day's 24h timeline: sleep blocks plus feed marks, as fractions of the day in [0, 1).
 * Breast and bottle feeds are kept apart so the rhythm strip can color them differently.
 */
data class DayRhythm(
    val date: LocalDate,
    val sleepBlocks: List<RhythmBlock>,
    val breastFeedMarks: List<Float>,
    val bottleFeedMarks: List<Float>,
)

/** A sleep interval clipped to a single day, expressed as fractions of that day in [0, 1]. */
data class RhythmBlock(
    val startFraction: Float,
    val endFraction: Float,
    val isNight: Boolean,
)
