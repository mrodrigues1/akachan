package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.babytracker.domain.repository.InventorySettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventorySettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : InventorySettingsRepository {

    private companion object {
        val EXPIRATION_ENABLED = booleanPreferencesKey("stash_expiration_enabled")
        val EXPIRATION_DAYS = intPreferencesKey("stash_expiration_days")
        val NOTIF_ENABLED = booleanPreferencesKey("stash_expiration_notif_enabled")
        val NOTIF_TIME_MINUTES = intPreferencesKey("stash_expiration_notif_time_minutes")

        const val DEFAULT_DAYS = 15
        const val MIN_DAYS = 1
        const val DEFAULT_NOTIF_TIME_MINUTES = 480 // 08:00
        const val MAX_MINUTE_OF_DAY = 1439
    }

    override fun getExpirationEnabled(): Flow<Boolean> =
        dataStore.data.map { it[EXPIRATION_ENABLED] ?: false }

    override suspend fun setExpirationEnabled(enabled: Boolean) {
        dataStore.edit { it[EXPIRATION_ENABLED] = enabled }
    }

    override fun getExpirationDays(): Flow<Int> =
        dataStore.data.map { (it[EXPIRATION_DAYS] ?: DEFAULT_DAYS).coerceAtLeast(MIN_DAYS) }

    override suspend fun setExpirationDays(days: Int) {
        dataStore.edit { it[EXPIRATION_DAYS] = days.coerceAtLeast(MIN_DAYS) }
    }

    override fun getExpirationNotifEnabled(): Flow<Boolean> =
        dataStore.data.map { it[NOTIF_ENABLED] ?: false }

    override suspend fun setExpirationNotifEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIF_ENABLED] = enabled }
    }

    override fun getExpirationNotifTimeMinutes(): Flow<Int> =
        dataStore.data.map {
            (it[NOTIF_TIME_MINUTES] ?: DEFAULT_NOTIF_TIME_MINUTES).coerceIn(0, MAX_MINUTE_OF_DAY)
        }

    override suspend fun setExpirationNotifTimeMinutes(minuteOfDay: Int) {
        dataStore.edit { it[NOTIF_TIME_MINUTES] = minuteOfDay.coerceIn(0, MAX_MINUTE_OF_DAY) }
    }
}
