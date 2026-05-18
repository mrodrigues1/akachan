package com.babytracker.domain.model

import java.time.Instant

data class InventorySummary(
    val totalMl: Int,
    val bagCount: Int,
    val oldestBagDate: Instant?,
) {
    companion object {
        val Empty = InventorySummary(totalMl = 0, bagCount = 0, oldestBagDate = null)
    }
}
