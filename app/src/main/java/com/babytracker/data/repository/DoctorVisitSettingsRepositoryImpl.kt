package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.babytracker.domain.repository.DoctorVisitSettingsRepository
import com.babytracker.domain.repository.DoctorVisitSettingsRepository.Companion.ALLOWED_LEAD_DAYS
import com.babytracker.domain.repository.DoctorVisitSettingsRepository.Companion.DEFAULT_LEAD_DAYS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorVisitSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : DoctorVisitSettingsRepository {
    private val enabledKey = booleanPreferencesKey("doctor_visit_reminder_enabled")
    private val leadDaysKey = intPreferencesKey("doctor_visit_reminder_lead_days")

    override fun getReminderEnabled(): Flow<Boolean> =
        dataStore.data.map { it[enabledKey] ?: false }

    override suspend fun setReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }

    override fun getReminderLeadDays(): Flow<Int> =
        dataStore.data.map { prefs -> (prefs[leadDaysKey] ?: DEFAULT_LEAD_DAYS).sanitizedLeadDays() }

    override suspend fun setReminderLeadDays(days: Int) {
        dataStore.edit { it[leadDaysKey] = days.sanitizedLeadDays() }
    }

    private fun Int.sanitizedLeadDays(): Int = if (this in ALLOWED_LEAD_DAYS) this else DEFAULT_LEAD_DAYS
}
