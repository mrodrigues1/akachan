package com.babytracker.domain.model

import java.time.Instant

data class BottleFeed(
    val id: Long = 0,
    val timestamp: Instant,
    val volumeMl: Int,
    val type: FeedType,
    val linkedMilkBagId: Long? = null,
    val notes: String? = null,
    val createdAt: Instant,
)
