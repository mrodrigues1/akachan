package com.babytracker.export.domain.model

import kotlinx.serialization.Serializable

const val CURRENT_BACKUP_FORMAT_VERSION = 1

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
)

@Serializable
data class BabyBackup(
    val name: String,
    val birthDateEpochDay: Long,
    val allergies: List<String>,
    val customAllergyNote: String?,
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
