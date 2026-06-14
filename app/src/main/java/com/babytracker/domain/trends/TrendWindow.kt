package com.babytracker.domain.trends

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The [days] consecutive local dates ending at [today] (inclusive), oldest first. */
fun trendWindowDates(today: LocalDate, days: Int): List<LocalDate> =
    (days - 1L downTo 0L).map { today.minusDays(it) }

/** Inclusive start instant of the window's first day, at start-of-day in [zone]. */
fun windowStartInstant(today: LocalDate, days: Int, zone: ZoneId): Instant =
    today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant()
