package com.babytracker.domain.model

import java.time.Instant

data class DiaperChange(
    val id: Long = 0,
    val timestamp: Instant,
    val type: DiaperType,
    val notes: String? = null,
    val createdAt: Instant,
)
