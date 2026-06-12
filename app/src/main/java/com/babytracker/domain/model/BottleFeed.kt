package com.babytracker.domain.model

import java.time.Instant

data class BottleFeed(
    val id: Long = 0,
    val clientId: String,
    val timestamp: Instant,
    val volumeMl: Int,
    val type: FeedType,
    val linkedMilkBagId: Long? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val author: FeedAuthor = FeedAuthor.OWNER,
)
