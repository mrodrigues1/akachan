package com.babytracker.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.SettingsRepository
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
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val MAX_PER_BREAST_MINUTES = intPreferencesKey("max_per_breast_minutes")
        val MAX_TOTAL_FEED_MINUTES = intPreferencesKey("max_total_feed_minutes")
        val WAKE_TIME_MINUTES = intPreferencesKey("wake_time_minutes")
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        private val RICH_NOTIFICATIONS_ENABLED = booleanPreferencesKey("rich_notifications_enabled")
        val APP_MODE = stringPreferencesKey("app_mode")
        val SHARE_CODE = stringPreferencesKey("share_code")
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

    override fun isOnboardingComplete(): Flow<Boolean> =
        dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    override fun getMaxPerBreastMinutes(): Flow<Int> =
        dataStore.data.map { it[MAX_PER_BREAST_MINUTES] ?: 0 }

    override suspend fun setMaxPerBreastMinutes(minutes: Int) {
        dataStore.edit { it[MAX_PER_BREAST_MINUTES] = minutes }
    }

    override fun getMaxTotalFeedMinutes(): Flow<Int> =
        dataStore.data.map { it[MAX_TOTAL_FEED_MINUTES] ?: 0 }

    override suspend fun setMaxTotalFeedMinutes(minutes: Int) {
        dataStore.edit { it[MAX_TOTAL_FEED_MINUTES] = minutes }
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
}
