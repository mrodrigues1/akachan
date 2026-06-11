package com.babytracker.export.domain

import com.babytracker.export.domain.model.BackupData

data class ImportCounts(
    val breastfeedingInserted: Int,
    val sleepInserted: Int,
    val pumpingInserted: Int,
    val milkBagsInserted: Int,
    val bottleFeedsInserted: Int,
)

/** Merges validated backup tracking rows into Room in a single transaction. */
interface BackupImporter {
    suspend fun merge(data: BackupData): ImportCounts
}
