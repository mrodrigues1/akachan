package com.babytracker.export.domain.model

import kotlinx.serialization.Serializable

const val CURRENT_BACKUP_FORMAT_VERSION = 6

@Serializable
data class BackupData(
    val backupFormatVersion: Int = CURRENT_BACKUP_FORMAT_VERSION,
    val roomSchemaVersion: Int,
    val appVersion: String,
    val exportedAt: Long,
    val baby: BabyBackup?,
    val settings: SettingsBackup,
    val breastfeeding: List<BreastfeedingBackup>,
    val sleep: List<SleepBackup>,
    val pumping: List<PumpingBackup>,
    val milkBags: List<MilkBagBackup>,
    val bottleFeeds: List<BottleFeedBackup> = emptyList(),
    // Added in format version 2; default-empty so v1 backups still deserialize.
    val growth: List<GrowthBackup> = emptyList(),
    val milestones: List<MilestoneBackup> = emptyList(),
    // Added in format version 4; default-empty so pre-v4 backups still deserialize.
    val diapers: List<DiaperBackup> = emptyList(),
    // Added in format version 5; default-empty so pre-v5 backups still deserialize.
    val vaccines: List<VaccineBackup> = emptyList(),
    // Added in format version 6; default-empty so pre-v6 backups still deserialize.
    val doctorVisits: List<DoctorVisitBackup> = emptyList(),
    val visitQuestions: List<VisitQuestionBackup> = emptyList(),
)

@Serializable
data class DoctorVisitBackup(
    val id: Long,
    val date: Long,
    val providerName: String? = null,
    val notes: String? = null,
    val snapshotLabel: String? = null,
    val snapshotCreatedAt: Long? = null,
    val createdAt: Long,
)

@Serializable
data class VisitQuestionBackup(
    val id: Long,
    val text: String,
    val answered: Boolean = false,
    val visitId: Long? = null,
    val createdAt: Long,
)

@Serializable
data class DiaperBackup(
    val id: Long,
    val timestamp: Long,
    val type: String,
    val notes: String?,
    val createdAt: Long,
)

@Serializable
data class VaccineBackup(
    val id: Long,
    val name: String,
    val doseLabel: String? = null,
    val status: String,
    val scheduledDate: Long? = null,
    val administeredDate: Long? = null,
    val notes: String? = null,
    val createdAt: Long,
)

@Serializable
data class BabyBackup(
    val name: String,
    val birthDateEpochDay: Long,
    val allergies: List<String>,
    val customAllergyNote: String?,
    // Added in format version 2; null for v1 backups -> restored as UNSPECIFIED.
    val sex: String? = null,
)

@Serializable
data class GrowthBackup(
    val id: Long,
    val takenAtMs: Long,
    val type: String,
    val valueCanonical: Long,
    val notes: String?,
)

// Free-form moments (format version 3). Photos are metadata-only in backups: the photo
// file is not archived, so no photoUri is stored. Fields default so that pre-v3 backups
// (which used the removed WHO-enum shape) still deserialize; their entries are dropped on
// import by the version gate.
@Serializable
data class MilestoneBackup(
    val title: String = "",
    val dateEpochDay: Long = 0,
    val timeMinuteOfDay: Int? = null,
    val note: String? = null,
)

@Serializable
data class SettingsBackup(
    val themeConfig: String,
    val maxPerBreastMinutes: Int,
    val maxTotalFeedMinutes: Int,
    val wakeTimeMinuteOfDay: Int?,
    val autoUpdateEnabled: Boolean,
    val richNotificationsEnabled: Boolean,
    val predictiveEnabled: Boolean,
    val predictiveLeadMinutes: Int,
    val quietHoursStartMinute: Int,
    val quietHoursEndMinute: Int,
    val napReminderEnabled: Boolean,
    val napReminderDelayMinutes: Int,
)

@Serializable
data class BreastfeedingBackup(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val startingSide: String,
    val switchTime: Long?,
    val notes: String?,
    val pausedAt: Long?,
    val pausedDurationMs: Long,
)

@Serializable
data class SleepBackup(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val sleepType: String,
    val notes: String?,
    val timezoneId: String? = null,
)

@Serializable
data class PumpingBackup(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val breast: String,
    val volumeMl: Int?,
    val notes: String?,
    val pausedAt: Long?,
    val pausedDurationMs: Long,
)

@Serializable
data class MilkBagBackup(
    val id: Long,
    val collectionDate: Long,
    val volumeMl: Int,
    val sourceSessionId: Long?,
    val usedAt: Long?,
    val notes: String?,
    val createdAt: Long,
)

@Serializable
data class BottleFeedBackup(
    val id: Long,
    val timestamp: Long,
    val volumeMl: Int,
    val type: String,
    val linkedMilkBagId: Long?,
    val notes: String?,
    val createdAt: Long,
)
