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
