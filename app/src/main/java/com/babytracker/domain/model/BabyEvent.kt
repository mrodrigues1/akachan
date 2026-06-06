package com.babytracker.domain.model

import java.time.Instant

data class BabyEvent(
    val id: Long = 0,
    val timestamp: Instant,
    val type: BabyEventType,
    val intensity: Int? = null,
    val notes: String? = null,
    val createdAt: Instant,
)
