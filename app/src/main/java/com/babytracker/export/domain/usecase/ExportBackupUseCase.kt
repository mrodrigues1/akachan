package com.babytracker.export.domain.usecase

import com.babytracker.export.domain.BackupSource
import com.babytracker.export.domain.ExportMetadata
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.BreastfeedingBackup
import com.babytracker.export.domain.model.PumpingBackup
import com.babytracker.export.domain.model.SleepBackup
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ExportBackupUseCase @Inject constructor(
    private val source: BackupSource,
    private val json: Json,
    private val metadata: ExportMetadata,
) {
    suspend operator fun invoke(): String {
        val tracking = source.readTracking()
        val prefs = source.readPreferences()
        val exportedAt = System.currentTimeMillis()

        val backup = BackupData(
            roomSchemaVersion = metadata.roomSchemaVersion,
            appVersion = metadata.appVersion,
            exportedAt = exportedAt,
            baby = prefs.baby,
            settings = prefs.settings,
            breastfeeding = tracking.breastfeeding.map { it.finalizeIfActive(exportedAt) },
            sleep = tracking.sleep.map { it.finalizeIfActive(exportedAt) },
            pumping = tracking.pumping.map { it.finalizeIfActive(exportedAt) },
            milkBags = tracking.milkBags,
        )

        return json.encodeToString(BackupData.serializer(), backup)
    }

    private fun BreastfeedingBackup.finalizeIfActive(now: Long) =
        if (endTime == null) {
            copy(endTime = now, pausedDurationMs = foldPause(now), pausedAt = null)
        } else {
            this
        }

    private fun SleepBackup.finalizeIfActive(now: Long) =
        if (endTime == null) copy(endTime = now) else this

    private fun PumpingBackup.finalizeIfActive(now: Long) =
        if (endTime == null) {
            copy(endTime = now, pausedDurationMs = foldPumpPause(now), pausedAt = null)
        } else {
            this
        }

    private fun BreastfeedingBackup.foldPause(now: Long): Long =
        pausedDurationMs + (pausedAt?.let { maxOf(0L, now - it) } ?: 0L)

    private fun PumpingBackup.foldPumpPause(now: Long): Long =
        pausedDurationMs + (pausedAt?.let { maxOf(0L, now - it) } ?: 0L)
}
