package com.babytracker.export.domain.usecase

import com.babytracker.export.domain.BackupSource
import com.babytracker.export.domain.ExportMetadata
import com.babytracker.export.domain.model.BackupData
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

        val backup = BackupData(
            roomSchemaVersion = metadata.roomSchemaVersion,
            appVersion = metadata.appVersion,
            exportedAt = System.currentTimeMillis(),
            baby = prefs.baby,
            settings = prefs.settings,
            breastfeeding = tracking.breastfeeding,
            sleep = tracking.sleep,
            pumping = tracking.pumping,
            milkBags = tracking.milkBags,
            bottleFeeds = tracking.bottleFeeds,
            growth = tracking.growth,
            milestones = tracking.milestones,
            diapers = tracking.diapers,
            // vaccines were read into the snapshot but never written here (AKA-199 oversight);
            // included now alongside the doctor-visit fields so the backup is complete.
            vaccines = tracking.vaccines,
            doctorVisits = tracking.doctorVisits,
            visitQuestions = tracking.visitQuestions,
        )

        return json.encodeToString(BackupData.serializer(), backup)
    }
}
