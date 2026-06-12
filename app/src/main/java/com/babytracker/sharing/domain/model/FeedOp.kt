package com.babytracker.sharing.domain.model

enum class FeedOpAction { CREATE, UPDATE, DELETE }

data class FeedOp(
    val opId: String,
    val action: FeedOpAction,
    val entryClientId: String,
    val authorUid: String,
    val createdAtMs: Long,
    val timestampMs: Long? = null,
    val volumeMl: Int? = null,
    val type: String? = null,
    val notes: String? = null,
    val consumedBagId: Long? = null,
)
