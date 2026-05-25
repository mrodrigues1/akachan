package com.babytracker.export.domain.usecase

import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.export.domain.BackupTooNewException
import com.babytracker.export.domain.InvalidBackupException
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.CURRENT_BACKUP_FORMAT_VERSION
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ValidateBackupUseCase @Inject constructor(
    private val json: Json,
) {
    operator fun invoke(jsonString: String): BackupData {
        val parsed = json.decodeFromString(BackupData.serializer(), jsonString)
        val migrated = applyVersionGate(parsed)
        validateContent(migrated)
        return migrated
    }

    private fun applyVersionGate(data: BackupData): BackupData = when {
        data.backupFormatVersion > CURRENT_BACKUP_FORMAT_VERSION ->
            throw BackupTooNewException(data.backupFormatVersion)
        data.backupFormatVersion < CURRENT_BACKUP_FORMAT_VERSION ->
            migrateOlder(data)
        else -> data
    }

    private fun migrateOlder(data: BackupData): BackupData =
        throw InvalidBackupException("Unsupported backup format v${data.backupFormatVersion}")

    private fun validateContent(data: BackupData) {
        runCatching {
            data.breastfeeding.forEach { BreastSide.valueOf(it.startingSide) }
            data.sleep.forEach { SleepType.valueOf(it.sleepType) }
            data.pumping.forEach { PumpingBreast.valueOf(it.breast) }
            data.baby?.allergies?.forEach { AllergyType.valueOf(it) }
            ThemeConfig.valueOf(data.settings.themeConfig)
        }.onFailure { throw InvalidBackupException("Backup contains an invalid enum value: ${it.message}") }

        validateScalarInvariants(data)
        validateMilkBagReferences(data)
    }

    private fun validateScalarInvariants(data: BackupData) {
        validateBreastfeedingInvariants(data)
        validateSleepInvariants(data)
        validatePumpingInvariants(data)
        validateMilkBagInvariants(data)
    }

    private fun validateBreastfeedingInvariants(data: BackupData) {
        data.breastfeeding.forEach {
            checkSpan(it.startTime, it.endTime, "breastfeeding ${it.id}")
            if (it.pausedDurationMs < 0) bad("breastfeeding ${it.id} has negative paused duration")
        }
    }

    private fun validateSleepInvariants(data: BackupData) {
        data.sleep.forEach { checkSpan(it.startTime, it.endTime, "sleep ${it.id}") }
    }

    private fun validatePumpingInvariants(data: BackupData) {
        data.pumping.forEach {
            checkSpan(it.startTime, it.endTime, "pumping ${it.id}")
            if (it.pausedDurationMs < 0) bad("pumping ${it.id} has negative paused duration")
            if (it.volumeMl != null && it.volumeMl < 0) bad("pumping ${it.id} has negative volume")
        }
        val pumpingIds = data.pumping.map { it.id }
        if (pumpingIds.size != pumpingIds.toSet().size) {
            bad("Backup contains duplicate pumping ids")
        }
    }

    private fun validateMilkBagInvariants(data: BackupData) {
        data.milkBags.forEach {
            if (it.collectionDate < 0) bad("milk bag ${it.id} has negative collection date")
            if (it.createdAt < 0) bad("milk bag ${it.id} has negative created time")
            if (it.volumeMl < 0) bad("milk bag ${it.id} has negative volume")
        }
    }

    private fun bad(msg: String): Nothing = throw InvalidBackupException(msg)

    private fun checkSpan(start: Long, end: Long?, label: String) {
        if (start < 0) bad("$label has negative start time")
        if (end != null && end < start) bad("$label end ($end) precedes start ($start)")
    }

    private fun validateMilkBagReferences(data: BackupData) {
        val pumpingIds = data.pumping.map { it.id }.toSet()
        data.milkBags.forEach { bag ->
            val ref = bag.sourceSessionId
            if (ref != null && ref !in pumpingIds) {
                throw InvalidBackupException(
                    "Milk bag references pumping id $ref not present in backup",
                )
            }
        }
    }
}
