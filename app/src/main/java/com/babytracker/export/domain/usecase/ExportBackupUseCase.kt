package com.babytracker.export.domain.usecase

import com.babytracker.export.domain.BackupSource
import com.babytracker.export.domain.ExportMetadata
import com.babytracker.export.domain.model.BackupData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.OutputStream
import javax.inject.Inject

class ExportBackupUseCase @Inject constructor(
    private val source: BackupSource,
    private val json: Json,
    private val metadata: ExportMetadata,
) {
    /**
     * Streams the backup straight to [out] so the whole dataset is never materialized as one giant
     * in-memory String *and* ByteArray (the [invoke] path holds both). The caller (BackupFileWriter)
     * runs this on Dispatchers.IO, where serialization is interleaved with the stream writes — peak
     * heap is the [BackupData] graph plus a small streaming buffer instead of ~2× the dataset.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun encodeTo(out: OutputStream) {
        json.encodeToStream(BackupData.serializer(), buildBackup(), out)
    }

    /**
     * String variant retained for tests and any caller that needs the full JSON in memory. Prefer
     * [encodeTo] for the on-device backup path. Serializing is CPU-bound, so it runs off the main
     * thread (the Room reads in [buildBackup] already run off-main via the BackupSource).
     */
    suspend operator fun invoke(): String {
        val backup = buildBackup()
        return withContext(Dispatchers.Default) { json.encodeToString(BackupData.serializer(), backup) }
    }

    private suspend fun buildBackup(): BackupData {
        val tracking = source.readTracking()
        val prefs = source.readPreferences()
        return BackupData(
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
    }
}
