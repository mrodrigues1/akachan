package com.babytracker.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Settings for the sleep reminder feature (nap reminders + predictive sleep), persisted via
 * DataStore. Kept separate from [SettingsRepository] because it is an opt-in, self-contained
 * feature reached from the Sleep screen's own settings.
 *
 * Quiet hours are intentionally NOT owned here: they are shared with predictive *feeding*
 * notifications and therefore remain on [SettingsRepository].
 */
interface SleepSettingsRepository {
    fun getNapReminderEnabled(): Flow<Boolean>
    suspend fun setNapReminderEnabled(enabled: Boolean)

    fun getNapReminderDelayMinutes(): Flow<Int>
    suspend fun setNapReminderDelayMinutes(minutes: Int)

    fun getPredictiveSleepEnabled(): Flow<Boolean>
    suspend fun setPredictiveSleepEnabled(enabled: Boolean)

    fun getPredictiveSleepLeadMinutes(): Flow<Int>
    suspend fun setPredictiveSleepLeadMinutes(minutes: Int)
}
