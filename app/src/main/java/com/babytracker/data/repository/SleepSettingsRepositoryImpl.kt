package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.babytracker.domain.repository.SleepSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SleepSettingsRepository {

    private companion object {
        // Keys are reused verbatim from the previous SettingsRepository ownership so existing
        // users keep their saved sleep settings (no migration).
        val NAP_REMINDER_ENABLED = booleanPreferencesKey("nap_reminder_enabled")
        val NAP_REMINDER_DELAY_MINUTES = intPreferencesKey("nap_reminder_delay_minutes")
        val PREDICTIVE_SLEEP_ENABLED = booleanPreferencesKey("predictive_sleep_enabled")
        val PREDICTIVE_SLEEP_LEAD_MINUTES = intPreferencesKey("predictive_sleep_lead_minutes")

        const val DEFAULT_NAP_DELAY_MINUTES = 60
        const val MIN_NAP_DELAY = 1
        const val MAX_NAP_DELAY = 480
        const val DEFAULT_LEAD_MINUTES = 15
        val ALLOWED_LEAD_MINUTES = setOf(5, 10, 15, 30)
    }

    override fun getNapReminderEnabled(): Flow<Boolean> =
        dataStore.data.map { it[NAP_REMINDER_ENABLED] ?: false }

    override suspend fun setNapReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[NAP_REMINDER_ENABLED] = enabled }
    }

    override fun getNapReminderDelayMinutes(): Flow<Int> =
        dataStore.data.map { it[NAP_REMINDER_DELAY_MINUTES] ?: DEFAULT_NAP_DELAY_MINUTES }

    override suspend fun setNapReminderDelayMinutes(minutes: Int) {
        dataStore.edit { it[NAP_REMINDER_DELAY_MINUTES] = minutes.coerceIn(MIN_NAP_DELAY, MAX_NAP_DELAY) }
    }

    override fun getPredictiveSleepEnabled(): Flow<Boolean> =
        dataStore.data.map { it[PREDICTIVE_SLEEP_ENABLED] ?: false }

    override suspend fun setPredictiveSleepEnabled(enabled: Boolean) {
        dataStore.edit { it[PREDICTIVE_SLEEP_ENABLED] = enabled }
    }

    override fun getPredictiveSleepLeadMinutes(): Flow<Int> =
        dataStore.data.map { prefs ->
            val stored = prefs[PREDICTIVE_SLEEP_LEAD_MINUTES] ?: DEFAULT_LEAD_MINUTES
            if (stored in ALLOWED_LEAD_MINUTES) stored else DEFAULT_LEAD_MINUTES
        }

    override suspend fun setPredictiveSleepLeadMinutes(minutes: Int) {
        val sanitized = if (minutes in ALLOWED_LEAD_MINUTES) minutes else DEFAULT_LEAD_MINUTES
        dataStore.edit { it[PREDICTIVE_SLEEP_LEAD_MINUTES] = sanitized }
    }
}
