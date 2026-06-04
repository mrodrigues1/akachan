package com.babytracker.domain.sleep.eval

import java.time.Instant

data class EvalReport(
    val algorithmVersion: String,
    val evaluatedAt: Instant,
    val segments: List<SegmentResult>,
    val totalAnchors: Int,
    val totalScored: Int,
)
