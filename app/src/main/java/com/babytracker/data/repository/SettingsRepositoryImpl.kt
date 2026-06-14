package com.babytracker.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.babytracker.domain.model.HomeTile
import com.babytracker.domain.model.MeasurementSystem
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.export.domain.model.BackupData
import com.babytracker.sharing.domain.model.AppMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DateTimeException
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private companion object {
        const val TAG = "SettingsRepository"
        val THEME_CONFIG = stringPreferencesKey("theme_config")
        val VOLUME_UNIT = stringPreferencesKey("volume_unit")
        val MEASUREMENT_SYSTEM = stringPreferencesKey("measurement_system")
        val HOME_TILE_ORDER = stringPreferencesKey("home_tile_order")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val MAX_PER_BREAST_MINUTES = intPreferencesKey("max_per_breast_minutes")
        val MAX_TOTAL_FEED_MINUTES = intPreferencesKey("max_total_feed_minutes")
        val WAKE_TIME_MINUTES = intPreferencesKey("wake_time_minutes")
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        private val RICH_NOTIFICATIONS_ENABLED = booleanPreferencesKey("rich_notifications_enabled")
        val APP_MODE = stringPreferencesKey("app_mode")
        val SHARE_CODE = stringPreferencesKey("share_code")
        val PREDICTIVE_ENABLED = booleanPreferencesKey("predictive_enabled")
        val PREDICTIVE_LEAD_MINUTES = intPreferencesKey("predictive_lead_minutes")
        val QUIET_HOURS_START_MINUTE = intPreferencesKey("quiet_hours_start_minute")
        val QUIET_HOURS_END_MINUTE = intPreferencesKey("quiet_hours_end_minute")
        // Sleep reminder prefs (nap + predictive sleep) are owned by SleepSettingsRepository.
        // The nap keys are still written here by restoreFromBackup, so they remain defined.
        val NAP_REMINDER_ENABLED = booleanPreferencesKey("nap_reminder_enabled")
        val NAP_REMINDER_DELAY_MINUTES = intPreferencesKey("nap_reminder_delay_minutes")
        const val DEFAULT_LEAD_MINUTES = 15
        val ALLOWED_LEAD_MINUTES = setOf(5, 10, 15, 30)
        val IMPORT_IN_PROGRESS = booleanPreferencesKey("import_in_progress")
        val IMPORT_STARTED_AT = longPreferencesKey("import_started_at")
        val LAST_IMPORT_COMPLETED_AT = longPreferencesKey("last_import_completed_at")
        val BABY_NAME = stringPreferencesKey("baby_name")
        val BABY_BIRTH_DATE = longPreferencesKey("baby_birth_date")
        val ALLERGIES = stringPreferencesKey("allergies")
        val CUSTOM_ALLERGY_NOTE = stringPreferencesKey("custom_allergy_note")
        val BABY_SEX = stringPreferencesKey("baby_sex")
        const val MAX_MINUTE_OF_DAY = 1439
        const val MIN_NAP_DELAY = 1
        const val MAX_NAP_DELAY = 480
    }

    override fun getThemeConfig(): Flow<ThemeConfig> =
        dataStore.data.map { preferences ->
            val themeStr = preferences[THEME_CONFIG] ?: ThemeConfig.SYSTEM.name
            try {
                ThemeConfig.valueOf(themeStr)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid theme config stored: $themeStr", e)
                ThemeConfig.SYSTEM
            }
        }

    override suspend fun setThemeConfig(themeConfig: ThemeConfig) {
        dataStore.edit { it[THEME_CONFIG] = themeConfig.name }
    }

    override fun getVolumeUnit(): Flow<VolumeUnit> =
        dataStore.data.map { prefs ->
            prefs[VOLUME_UNIT]?.let { runCatching { VolumeUnit.valueOf(it) }.getOrNull() } ?: VolumeUnit.ML
        }

    override suspend fun setVolumeUnit(unit: VolumeUnit) {
        dataStore.edit { it[VOLUME_UNIT] = unit.name }
    }

    override fun getMeasurementSystem(): Flow<MeasurementSystem> =
        dataStore.data.map { prefs ->
            prefs[MEASUREMENT_SYSTEM]?.let { runCatching { MeasurementSystem.valueOf(it) }.getOrNull() }
                ?: MeasurementSystem.METRIC
        }

    override suspend fun setMeasurementSystem(system: MeasurementSystem) {
        dataStore.edit { it[MEASUREMENT_SYSTEM] = system.name }
    }

    override fun getHomeTileOrder(): Flow<List<HomeTile>> =
        dataStore.data.map { HomeTile.deserialize(it[HOME_TILE_ORDER]) }

    override suspend fun setHomeTileOrder(order: List<HomeTile>) {
        dataStore.edit { it[HOME_TILE_ORDER] = HomeTile.serialize(order) }
    }

    override suspend fun clearHomeTileOrder() {
        dataStore.edit { it.remove(HOME_TILE_ORDER) }
    }

    override fun isOnboardingComplete(): Flow<Boolean> =
        dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    override fun getWakeTime(): Flow<LocalTime?> =
        dataStore.data.map { preferences ->
            preferences[WAKE_TIME_MINUTES]?.let { minutes ->
                try {
                    LocalTime.of(minutes / 60, minutes % 60)
                } catch (e: DateTimeException) {
                    Log.w(TAG, "Invalid wake time minutes stored: $minutes", e)
                    null
                }
            }
        }

    override suspend fun setWakeTime(time: LocalTime) {
        dataStore.edit { it[WAKE_TIME_MINUTES] = time.hour * 60 + time.minute }
    }

    override fun getAutoUpdateEnabled(): Flow<Boolean> =
        dataStore.data.map { it[AUTO_UPDATE_ENABLED] ?: true }

    override suspend fun setAutoUpdateEnabled(enabled: Boolean) {
        dataStore.edit { it[AUTO_UPDATE_ENABLED] = enabled }
    }

    override fun getRichNotificationsEnabled(): Flow<Boolean> =
        dataStore.data.map { it[RICH_NOTIFICATIONS_ENABLED] ?: true }

    override suspend fun setRichNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[RICH_NOTIFICATIONS_ENABLED] = enabled }
    }

    override fun getAppMode(): Flow<AppMode> =
        dataStore.data.map { preferences ->
            val modeStr = preferences[APP_MODE] ?: AppMode.NONE.name
            try {
                AppMode.valueOf(modeStr)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid app mode stored: $modeStr", e)
                AppMode.NONE
            }
        }

    override suspend fun setAppMode(mode: AppMode) {
        dataStore.edit { it[APP_MODE] = mode.name }
    }

    override fun getShareCode(): Flow<String?> =
        dataStore.data.map { it[SHARE_CODE] }

    override suspend fun setShareCode(code: String) {
        dataStore.edit { it[SHARE_CODE] = code }
    }

    override suspend fun clearShareCode() {
        dataStore.edit { it.remove(SHARE_CODE) }
    }

    override suspend fun clearPartnerStateIfShareCodeMatches(code: String): Boolean {
        var cleared = false
        dataStore.edit { prefs ->
            // Single edit block runs under DataStore's lock, so the compare-and-clear is atomic
            // with respect to ConnectAsPartnerUseCase's setShareCode/setAppMode writes.
            if (prefs[SHARE_CODE] == code) {
                prefs.remove(SHARE_CODE)
                prefs[APP_MODE] = AppMode.NONE.name
                cleared = true
            }
        }
        return cleared
    }

    override fun getQuietHoursStartMinute(): Flow<Int> =
        dataStore.data.map { it[QUIET_HOURS_START_MINUTE] ?: 0 }

    override suspend fun setQuietHoursStartMinute(minuteOfDay: Int) {
        dataStore.edit { it[QUIET_HOURS_START_MINUTE] = minuteOfDay.coerceIn(0, 1439) }
    }

    override fun getQuietHoursEndMinute(): Flow<Int> =
        dataStore.data.map { it[QUIET_HOURS_END_MINUTE] ?: 480 }

    override suspend fun setQuietHoursEndMinute(minuteOfDay: Int) {
        dataStore.edit { it[QUIET_HOURS_END_MINUTE] = minuteOfDay.coerceIn(0, 1439) }
    }

    override fun isImportInProgress(): Flow<Boolean> =
        dataStore.data.map { it[IMPORT_IN_PROGRESS] ?: false }

    override suspend fun markImportInProgress(startedAt: Long) {
        dataStore.edit {
            it[IMPORT_IN_PROGRESS] = true
            it[IMPORT_STARTED_AT] = startedAt
        }
    }

    override suspend fun restoreFromBackup(data: BackupData) {
        // TODO(AKA-132 follow-up): backup/restore does not yet carry home_tile_order across devices.
        dataStore.edit { p ->
            val baby = data.baby
            if (baby != null) {
                p[BABY_NAME] = baby.name
                p[BABY_BIRTH_DATE] = baby.birthDateEpochDay
                p[ALLERGIES] = baby.allergies.joinToString(",")
                if (baby.customAllergyNote != null) p[CUSTOM_ALLERGY_NOTE] = baby.customAllergyNote
                else p.remove(CUSTOM_ALLERGY_NOTE)
                // v1 backups have no sex -> remove the key so it reads back as UNSPECIFIED.
                if (baby.sex != null) p[BABY_SEX] = baby.sex else p.remove(BABY_SEX)
                p[ONBOARDING_COMPLETE] = true
            }

            val s = data.settings
            p[THEME_CONFIG] = s.themeConfig
            p[MAX_PER_BREAST_MINUTES] = s.maxPerBreastMinutes
            p[MAX_TOTAL_FEED_MINUTES] = s.maxTotalFeedMinutes
            val wake = s.wakeTimeMinuteOfDay
            if (wake != null && wake in 0..MAX_MINUTE_OF_DAY) p[WAKE_TIME_MINUTES] = wake
            else p.remove(WAKE_TIME_MINUTES)
            p[AUTO_UPDATE_ENABLED] = s.autoUpdateEnabled
            p[RICH_NOTIFICATIONS_ENABLED] = s.richNotificationsEnabled
            p[PREDICTIVE_ENABLED] = s.predictiveEnabled
            p[PREDICTIVE_LEAD_MINUTES] =
                if (s.predictiveLeadMinutes in ALLOWED_LEAD_MINUTES) s.predictiveLeadMinutes else DEFAULT_LEAD_MINUTES
            p[QUIET_HOURS_START_MINUTE] = s.quietHoursStartMinute.coerceIn(0, MAX_MINUTE_OF_DAY)
            p[QUIET_HOURS_END_MINUTE] = s.quietHoursEndMinute.coerceIn(0, MAX_MINUTE_OF_DAY)
            p[NAP_REMINDER_ENABLED] = s.napReminderEnabled
            p[NAP_REMINDER_DELAY_MINUTES] = s.napReminderDelayMinutes.coerceIn(MIN_NAP_DELAY, MAX_NAP_DELAY)

            p[IMPORT_IN_PROGRESS] = false
            p[LAST_IMPORT_COMPLETED_AT] = System.currentTimeMillis()
        }
    }
}
