package com.babytracker.sharing.domain.model

data class MergedFeedHistory(
    val entries: List<BottleFeedSnapshot>,
    val pendingOpCount: Int,
    val pendingOpIds: Set<String> = emptySet(),
)
