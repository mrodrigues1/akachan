package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.babytracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private companion object {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val MAX_PER_BREAST_MINUTES = intPreferencesKey("max_per_breast_minutes")
        val MAX_TOTAL_FEED_MINUTES = intPreferencesKey("max_total_feed_minutes")
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
}
