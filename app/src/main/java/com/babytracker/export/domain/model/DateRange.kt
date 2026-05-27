package com.babytracker.export.domain.model

import java.time.Duration
import java.time.Instant

data class DateRange(
    val start: Instant,
    val end: Instant,
) {
    init {
        require(!start.isAfter(end)) { "DateRange start ($start) must not be after end ($end)" }
    }

    companion object {
        fun lastDays(days: Long, now: Instant = Instant.now()): DateRange =
            DateRange(start = now.minus(Duration.ofDays(days)), end = now)

        fun allTime(now: Instant = Instant.now()): DateRange =
            DateRange(start = Instant.EPOCH, end = now)
    }
}
