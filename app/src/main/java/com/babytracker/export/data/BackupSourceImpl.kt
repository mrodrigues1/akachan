package com.babytracker.export.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.export.domain.BackupSource
import com.babytracker.export.domain.PreferencesSnapshot
import com.babytracker.export.domain.TrackingSnapshot
import com.babytracker.export.domain.model.BabyBackup
import com.babytracker.export.domain.model.SettingsBackup
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupSourceImpl @Inject constructor(
    private val db: BabyTrackerDatabase,
    private val dataStore: DataStore<Preferences>,
) : BackupSource {

    private object Keys {
        val BABY_NAME = stringPreferencesKey("baby_name")
        val BABY_BIRTH_DATE = longPreferencesKey("baby_birth_date")
        val ALLERGIES = stringPreferencesKey("allergies")
        val CUSTOM_ALLERGY_NOTE = stringPreferencesKey("custom_allergy_note")
        val BABY_SEX = stringPreferencesKey("baby_sex")
        val THEME_CONFIG = stringPreferencesKey("theme_config")
        val MAX_PER_BREAST_MINUTES = intPreferencesKey("max_per_breast_minutes")
        val MAX_TOTAL_FEED_MINUTES = intPreferencesKey("max_total_feed_minutes")
        val WAKE_TIME_MINUTES = intPreferencesKey("wake_time_minutes")
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        val RICH_NOTIFICATIONS_ENABLED = booleanPreferencesKey("rich_notifications_enabled")
        val PREDICTIVE_ENABLED = booleanPreferencesKey("predictive_enabled")
        val PREDICTIVE_LEAD_MINUTES = intPreferencesKey("predictive_lead_minutes")
        val QUIET_HOURS_START_MINUTE = intPreferencesKey("quiet_hours_start_minute")
        val QUIET_HOURS_END_MINUTE = intPreferencesKey("quiet_hours_end_minute")
        val NAP_REMINDER_ENABLED = booleanPreferencesKey("nap_reminder_enabled")
        val NAP_REMINDER_DELAY_MINUTES = intPreferencesKey("nap_reminder_delay_minutes")
    }

    override suspend fun readTracking(): TrackingSnapshot = db.withTransaction {
        TrackingSnapshot(
            breastfeeding = db.breastfeedingDao().getAllSessionsOnce().map { it.toBackup() },
            sleep = db.sleepDao().getAllRecordsOnce().map { it.toBackup() },
            pumping = db.pumpingDao().getAllSessionsOnce().map { it.toBackup() },
            milkBags = db.milkBagDao().getAllBagsOnce().map { it.toBackup() },
            bottleFeeds = db.bottleFeedDao().getAllOnce().map { it.toBackup() },
            growth = db.growthMeasurementDao().getAllOnce().map { it.toBackup() },
            milestones = db.milestoneDao().getAllOnce().map { it.toBackup() },
            diapers = db.diaperDao().getAllOnce().map { it.toBackup() },
            vaccines = db.vaccineDao().getAllOnce().map { it.toBackup() },
        )
    }

    override suspend fun readPreferences(): PreferencesSnapshot {
        val p = dataStore.data.first()
        return PreferencesSnapshot(baby = parseBaby(p), settings = parseSettings(p))
    }

    private fun parseBaby(p: Preferences): BabyBackup? {
        val name = p[Keys.BABY_NAME]
        val birthDate = p[Keys.BABY_BIRTH_DATE]
        return if (name != null && birthDate != null) {
            BabyBackup(
                name = name,
                birthDateEpochDay = birthDate,
                allergies = p[Keys.ALLERGIES]
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?: emptyList(),
                customAllergyNote = p[Keys.CUSTOM_ALLERGY_NOTE],
                sex = p[Keys.BABY_SEX],
            )
        } else {
            null
        }
    }

    private fun parseSettings(p: Preferences): SettingsBackup {
        val themeConfig = p[Keys.THEME_CONFIG]
            ?.let { runCatching { ThemeConfig.valueOf(it) }.getOrNull() }
            ?.name
            ?: ThemeConfig.SYSTEM.name
        val wakeMinutes = p[Keys.WAKE_TIME_MINUTES]?.takeIf { it in 0..MAX_MINUTE_OF_DAY }
        val predictiveLead = (p[Keys.PREDICTIVE_LEAD_MINUTES] ?: DEFAULT_LEAD_MINUTES)
            .takeIf { it in ALLOWED_LEAD_MINUTES } ?: DEFAULT_LEAD_MINUTES
        return SettingsBackup(
            themeConfig = themeConfig,
            maxPerBreastMinutes = p[Keys.MAX_PER_BREAST_MINUTES] ?: 0,
            maxTotalFeedMinutes = p[Keys.MAX_TOTAL_FEED_MINUTES] ?: 0,
            wakeTimeMinuteOfDay = wakeMinutes,
            autoUpdateEnabled = p[Keys.AUTO_UPDATE_ENABLED] ?: true,
            richNotificationsEnabled = p[Keys.RICH_NOTIFICATIONS_ENABLED] ?: true,
            predictiveEnabled = p[Keys.PREDICTIVE_ENABLED] ?: false,
            predictiveLeadMinutes = predictiveLead,
            quietHoursStartMinute = (p[Keys.QUIET_HOURS_START_MINUTE] ?: 0)
                .coerceIn(0, MAX_MINUTE_OF_DAY),
            quietHoursEndMinute = (p[Keys.QUIET_HOURS_END_MINUTE] ?: 480)
                .coerceIn(0, MAX_MINUTE_OF_DAY),
            napReminderEnabled = p[Keys.NAP_REMINDER_ENABLED] ?: false,
            napReminderDelayMinutes = (p[Keys.NAP_REMINDER_DELAY_MINUTES] ?: 60)
                .coerceIn(MIN_NAP_DELAY_MINUTES, MAX_NAP_DELAY_MINUTES),
        )
    }

    private companion object {
        const val MAX_MINUTE_OF_DAY = 1439
        const val DEFAULT_LEAD_MINUTES = 15
        val ALLOWED_LEAD_MINUTES = setOf(5, 10, 15, 30)
        const val MIN_NAP_DELAY_MINUTES = 1
        const val MAX_NAP_DELAY_MINUTES = 480
    }
}
