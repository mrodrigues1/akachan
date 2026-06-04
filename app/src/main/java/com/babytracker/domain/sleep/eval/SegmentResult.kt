package com.babytracker.domain.sleep.eval

enum class SegmentStatus { PASS, BLOCK_INSUFFICIENT_DATA }

data class SegmentResult(
    val key: SegmentKey,
    val anchorCount: Int,
    val scoredCount: Int,
    val maeMinutes: Double?,
    val inWindowPct: Double?,
    val missedWindowRate: Double?,
    val status: SegmentStatus,
    val blockReason: String?,
)
