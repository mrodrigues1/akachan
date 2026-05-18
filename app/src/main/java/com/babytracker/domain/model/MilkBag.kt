package com.babytracker.domain.model

import java.time.Instant

data class MilkBag(
    val id: Long = 0,
    val collectionDate: Instant,
    val volumeMl: Int,
    val sourceSessionId: Long? = null,
    val usedAt: Instant? = null,
    val notes: String? = null,
    val createdAt: Instant,
) {
    val isActive: Boolean get() = usedAt == null
}
