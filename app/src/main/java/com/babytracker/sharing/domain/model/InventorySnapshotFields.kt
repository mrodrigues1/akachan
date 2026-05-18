package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.InventorySummary

data class InventorySnapshotFields(
    val totalMl: Int,
    val bagCount: Int,
    val updatedAtMs: Long,
)

fun InventorySummary.toSnapshotFields(updatedAtMs: Long): InventorySnapshotFields =
    InventorySnapshotFields(
        totalMl = totalMl,
        bagCount = bagCount,
        updatedAtMs = updatedAtMs,
    )
