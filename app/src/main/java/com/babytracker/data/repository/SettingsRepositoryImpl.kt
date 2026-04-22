package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.SettingsRepository
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
        val THEME_CONFIG = stringPreferencesKey("theme_config")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val MAX_PER_BREAST_MINUTES = intPreferencesKey("max_per_breast_minutes")
        val MAX_TOTAL_FEED_MINUTES = intPreferencesKey("max_total_feed_minutes")
        val WAKE_TIME_MINUTES = intPreferencesKey("wake_time_minutes")
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        private val RICH_NOTIFICATIONS_ENABLED = booleanPreferencesKey("rich_notifications_enabled")
    }

    override fun getThemeConfig(): Flow<ThemeConfig> =
        dataStore.data.map { preferences ->
            val themeStr = preferences[THEME_CONFIG] ?: ThemeConfig.SYSTEM.name
            try {
                ThemeConfig.valueOf(themeStr)
            } catch (e: IllegalArgumentException) {
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
}
