package com.babytracker.domain.model

import java.time.Instant

data class TodayDiaperSummary(
    val count: Int = 0,
    val lastChangeAt: Instant? = null,
) {
    val hasAny: Boolean get() = count > 0
}
