package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.babytracker.domain.repository.VaccineSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccineSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : VaccineSettingsRepository {

    private companion object {
        val REMINDER_ENABLED = booleanPreferencesKey("vaccine_reminder_enabled")
        val REMINDER_LEAD_DAYS = intPreferencesKey("vaccine_reminder_lead_days")
        const val DEFAULT_LEAD_DAYS = 7
        val ALLOWED_LEAD_DAYS = setOf(1, 3, 7, 14)
    }

    override fun getReminderEnabled(): Flow<Boolean> =
        dataStore.data.map { it[REMINDER_ENABLED] ?: false }

    override suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[REMINDER_ENABLED] = enabled }
    }

    override fun getReminderLeadDays(): Flow<Int> =
        dataStore.data.map { prefs ->
            val stored = prefs[REMINDER_LEAD_DAYS] ?: DEFAULT_LEAD_DAYS
            if (stored in ALLOWED_LEAD_DAYS) stored else DEFAULT_LEAD_DAYS
        }

    override suspend fun setReminderLeadDays(days: Int) {
        val sanitized = if (days in ALLOWED_LEAD_DAYS) days else DEFAULT_LEAD_DAYS
        dataStore.edit { it[REMINDER_LEAD_DAYS] = sanitized }
    }
}
