package com.babytracker.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Settings for the milk stash expiration feature, persisted via DataStore.
 * Kept separate from [SettingsRepository] because it is an opt-in, self-contained feature.
 */
interface InventorySettingsRepository {
    fun getExpirationEnabled(): Flow<Boolean>
    suspend fun setExpirationEnabled(enabled: Boolean)

    fun getExpirationDays(): Flow<Int>
    suspend fun setExpirationDays(days: Int)

    fun getExpirationNotifEnabled(): Flow<Boolean>
    suspend fun setExpirationNotifEnabled(enabled: Boolean)

    fun getExpirationNotifTimeMinutes(): Flow<Int>
    suspend fun setExpirationNotifTimeMinutes(minuteOfDay: Int)
}
