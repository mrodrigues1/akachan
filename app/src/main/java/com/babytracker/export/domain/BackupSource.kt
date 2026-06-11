package com.babytracker.export.domain

import com.babytracker.export.domain.model.BabyBackup
import com.babytracker.export.domain.model.BottleFeedBackup
import com.babytracker.export.domain.model.BreastfeedingBackup
import com.babytracker.export.domain.model.MilkBagBackup
import com.babytracker.export.domain.model.PumpingBackup
import com.babytracker.export.domain.model.SettingsBackup
import com.babytracker.export.domain.model.SleepBackup

data class TrackingSnapshot(
    val breastfeeding: List<BreastfeedingBackup>,
    val sleep: List<SleepBackup>,
    val pumping: List<PumpingBackup>,
    val milkBags: List<MilkBagBackup>,
    val bottleFeeds: List<BottleFeedBackup>,
)

data class PreferencesSnapshot(
    val baby: BabyBackup?,
    val settings: SettingsBackup,
)

/** Build-time metadata stamped into every backup. Provided by Hilt (see ExportModule). */
data class ExportMetadata(
    val appVersion: String,
    val roomSchemaVersion: Int,
)

/**
 * Reads exportable state with per-store consistency:
 * [readTracking] reads all Room tables in one transaction; [readPreferences]
 * reads baby + settings from one DataStore Preferences snapshot.
 */
interface BackupSource {
    suspend fun readTracking(): TrackingSnapshot
    suspend fun readPreferences(): PreferencesSnapshot
}
