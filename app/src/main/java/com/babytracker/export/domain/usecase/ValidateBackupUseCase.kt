package com.babytracker.export.domain.usecase

import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.BabySex
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.GrowthType
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.model.toSleepTypeOrNull
import com.babytracker.domain.model.toVaccineStatusOrNull
import com.babytracker.domain.model.toVaccineStatusSafe
import com.babytracker.export.domain.BackupTooNewException
import com.babytracker.export.domain.InvalidBackupException
import com.babytracker.export.domain.model.BackupData
import com.babytracker.export.domain.model.CURRENT_BACKUP_FORMAT_VERSION
import com.babytracker.export.domain.model.VaccineBackup
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ValidateBackupUseCase @Inject constructor(
    private val json: Json,
) {
    operator fun invoke(jsonString: String): BackupData {
        val parsed = json.decodeFromString(BackupData.serializer(), jsonString)
        val migrated = applyVersionGate(parsed)
        val canonical = canonicalizeContent(migrated)
        validateContent(canonical)
        return canonical
    }

    private fun applyVersionGate(data: BackupData): BackupData = when {
        data.backupFormatVersion > CURRENT_BACKUP_FORMAT_VERSION ->
            throw BackupTooNewException(data.backupFormatVersion)
        data.backupFormatVersion < CURRENT_BACKUP_FORMAT_VERSION ->
            migrateOlder(data)
        else -> data
    }

    private fun migrateOlder(data: BackupData): BackupData = when (data.backupFormatVersion) {
        // v1 had no growth/milestones/sex; v2 added growth + WHO-enum milestones. The milestone
        // model was reformulated into free-form moments in v3, so pre-v3 milestone entries (which
        // used the removed enum shape) are dropped; everything else carries over unchanged.
        1, 2 -> data.copy(
            backupFormatVersion = CURRENT_BACKUP_FORMAT_VERSION,
            milestones = emptyList(),
        )
        // v3 added free-form milestones, v4 added diapers, v5 added vaccines; all carry forward
        // unchanged. The new doctorVisits/visitQuestions fields default to emptyList(), so no data
        // synthesis is needed.
        3, 4, 5 -> data.copy(backupFormatVersion = CURRENT_BACKUP_FORMAT_VERSION)
        else -> throw InvalidBackupException("Unsupported backup format v${data.backupFormatVersion}")
    }

    private fun canonicalizeContent(data: BackupData): BackupData =
        data.copy(
            sleep = data.sleep.map { backup ->
                backup.copy(sleepType = backup.sleepType.toSleepTypeOrNull()?.name ?: backup.sleepType)
            },
        )

    private fun validateContent(data: BackupData) {
        runCatching {
            data.breastfeeding.forEach { BreastSide.valueOf(it.startingSide) }
            data.sleep.forEach {
                it.sleepType.toSleepTypeOrNull() ?: throw IllegalArgumentException(it.sleepType)
            }
            data.pumping.forEach { PumpingBreast.valueOf(it.breast) }
            data.bottleFeeds.forEach { FeedType.valueOf(it.type) }
            data.baby?.allergies?.forEach { AllergyType.valueOf(it) }
            data.baby?.sex?.let { BabySex.valueOf(it) }
            data.growth.forEach { GrowthType.valueOf(it.type) }
            data.vaccines.forEach { it.status.toVaccineStatusOrNull() ?: throw IllegalArgumentException(it.status) }
            ThemeConfig.valueOf(data.settings.themeConfig)
        }.onFailure { throw InvalidBackupException("Backup contains an invalid enum value: ${it.message}") }

        validateScalarInvariants(data)
        validateMilkBagReferences(data)
        validateBottleFeedReferences(data)
    }

    private fun validateScalarInvariants(data: BackupData) {
        validateBreastfeedingInvariants(data)
        validateSleepInvariants(data)
        validatePumpingInvariants(data)
        validateMilkBagInvariants(data)
        validateBottleFeedInvariants(data)
        validateGrowthInvariants(data)
        validateMilestoneInvariants(data)
        validateVaccineInvariants(data)
    }

    private fun validateVaccineInvariants(data: BackupData) {
        data.vaccines.forEach { validateVaccine(it) }
    }

    private fun validateVaccine(v: VaccineBackup) {
        if (v.name.isBlank()) bad("vaccine ${v.id} has a blank name")
        if (v.createdAt < 0) bad("vaccine ${v.id} has negative created time")
        if (v.scheduledDate != null && v.scheduledDate < 0) bad("vaccine ${v.id} has negative scheduled date")
        if (v.administeredDate != null && v.administeredDate < 0) {
            bad("vaccine ${v.id} has negative administered date")
        }
        when (v.status.toVaccineStatusSafe()) {
            VaccineStatus.TO_SCHEDULE ->
                if (v.scheduledDate == null) bad("to-schedule vaccine ${v.id} has no target date")
            VaccineStatus.SCHEDULED ->
                if (v.scheduledDate == null) bad("scheduled vaccine ${v.id} has no scheduled date")
            VaccineStatus.ADMINISTERED ->
                if (v.administeredDate == null) bad("administered vaccine ${v.id} has no administered date")
        }
    }

    private fun validateGrowthInvariants(data: BackupData) {
        data.growth.forEach {
            if (it.takenAtMs < 0) bad("growth ${it.id} has negative timestamp")
            if (it.valueCanonical <= 0) bad("growth ${it.id} has non-positive value")
        }
    }

    private fun validateMilestoneInvariants(data: BackupData) {
        data.milestones.forEach {
            if (it.title.isBlank()) bad("Backup contains a milestone with a blank title")
            if (it.dateEpochDay < 0) bad("milestone \"${it.title}\" has negative date")
            if (it.timeMinuteOfDay != null && it.timeMinuteOfDay !in 0..MAX_MINUTE_OF_DAY) {
                bad("milestone \"${it.title}\" has out-of-range time")
            }
        }
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

    private fun validateBottleFeedInvariants(data: BackupData) {
        data.bottleFeeds.forEach {
            if (it.timestamp < 0) bad("bottle feed ${it.id} has negative timestamp")
            if (it.createdAt < 0) bad("bottle feed ${it.id} has negative created time")
            if (it.volumeMl <= 0) bad("bottle feed ${it.id} has non-positive volume")
        }
        val ids = data.bottleFeeds.map { it.id }
        if (ids.size != ids.toSet().size) bad("Backup contains duplicate bottle feed ids")
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

    private fun validateBottleFeedReferences(data: BackupData) {
        val bagsById = data.milkBags.associateBy { it.id }
        data.bottleFeeds.forEach { feed ->
            val ref = feed.linkedMilkBagId ?: return@forEach
            val bag = bagsById[ref]
                ?: bad("Bottle feed references milk bag id $ref not present in backup")
            // A consumed feed must point at a used bag; an active (usedAt == null) bag would be
            // double-counted by inventory queries that filter on used_at IS NULL.
            if (bag.usedAt == null) bad("Bottle feed references active milk bag id $ref")
        }
    }

    private companion object {
        const val MAX_MINUTE_OF_DAY = 1439
    }
}
