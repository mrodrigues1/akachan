package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.babytracker.domain.repository.FeedSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : FeedSettingsRepository {

    private companion object {
        // Keys are reused verbatim from the previous SettingsRepository ownership so existing
        // users keep their saved feed settings (no migration). Backup/restore reads and writes
        // the same keys, so it keeps working unchanged.
        val MAX_PER_BREAST_MINUTES = intPreferencesKey("max_per_breast_minutes")
        val MAX_TOTAL_FEED_MINUTES = intPreferencesKey("max_total_feed_minutes")
        val PREDICTIVE_ENABLED = booleanPreferencesKey("predictive_enabled")
        val PREDICTIVE_LEAD_MINUTES = intPreferencesKey("predictive_lead_minutes")

        const val DEFAULT_LEAD_MINUTES = 15
        val ALLOWED_LEAD_MINUTES = setOf(5, 10, 15, 30)
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

    override fun getPredictiveEnabled(): Flow<Boolean> =
        dataStore.data.map { it[PREDICTIVE_ENABLED] ?: false }

    override suspend fun setPredictiveEnabled(enabled: Boolean) {
        dataStore.edit { it[PREDICTIVE_ENABLED] = enabled }
    }

    override fun getPredictiveLeadMinutes(): Flow<Int> =
        dataStore.data.map { prefs ->
            val stored = prefs[PREDICTIVE_LEAD_MINUTES] ?: DEFAULT_LEAD_MINUTES
            if (stored in ALLOWED_LEAD_MINUTES) stored else DEFAULT_LEAD_MINUTES
        }

    override suspend fun setPredictiveLeadMinutes(minutes: Int) {
        // Allowlist enforced at the repository boundary so the coordinator never computes
        // triggerAt with a bogus offset. Invalid input collapses to the default (15).
        val sanitized = if (minutes in ALLOWED_LEAD_MINUTES) minutes else DEFAULT_LEAD_MINUTES
        dataStore.edit { it[PREDICTIVE_LEAD_MINUTES] = sanitized }
    }
}
