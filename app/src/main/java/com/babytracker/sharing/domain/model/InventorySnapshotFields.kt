package com.babytracker.sharing.domain.model

data class InventorySnapshotFields(
    val totalMl: Int,
    val bagCount: Int,
    val updatedAtMs: Long,
)
