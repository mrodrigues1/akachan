package com.babytracker.sharing.domain.model

data class MergedFeedHistory(
    val entries: List<BottleFeedSnapshot>,
    val pendingOpIds: Set<String> = emptySet(),
)
