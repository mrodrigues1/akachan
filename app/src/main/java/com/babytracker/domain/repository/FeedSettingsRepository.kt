package com.babytracker.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Settings for the breastfeeding (Feed) screen: feeding limits and predictive feeding reminders,
 * persisted via DataStore. Kept separate from [SettingsRepository] and reached from the Feed
 * screen's own settings, mirroring [InventorySettingsRepository] / [SleepSettingsRepository].
 *
 * Quiet hours are intentionally NOT owned here: they are shared with predictive *sleep*
 * notifications (and editable from the Sleep settings screen) and therefore remain on
 * [SettingsRepository].
 */
interface FeedSettingsRepository {
    fun getMaxPerBreastMinutes(): Flow<Int>
    suspend fun setMaxPerBreastMinutes(minutes: Int)

    fun getMaxTotalFeedMinutes(): Flow<Int>
    suspend fun setMaxTotalFeedMinutes(minutes: Int)

    fun getPredictiveEnabled(): Flow<Boolean>
    suspend fun setPredictiveEnabled(enabled: Boolean)

    fun getPredictiveLeadMinutes(): Flow<Int>
    suspend fun setPredictiveLeadMinutes(minutes: Int)
}
