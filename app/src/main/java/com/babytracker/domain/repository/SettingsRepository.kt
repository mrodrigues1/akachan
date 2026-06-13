package com.babytracker.domain.repository

import com.babytracker.domain.model.HomeTile
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.export.domain.model.BackupData
import com.babytracker.sharing.domain.model.AppMode
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

interface SettingsRepository {
    fun getThemeConfig(): Flow<ThemeConfig>
    suspend fun setThemeConfig(themeConfig: ThemeConfig)
    fun getVolumeUnit(): Flow<VolumeUnit>
    suspend fun setVolumeUnit(unit: VolumeUnit)
    fun getHomeTileOrder(): Flow<List<HomeTile>>
    suspend fun setHomeTileOrder(order: List<HomeTile>)
    suspend fun clearHomeTileOrder()
    fun isOnboardingComplete(): Flow<Boolean>
    suspend fun setOnboardingComplete(complete: Boolean)
    fun getMaxPerBreastMinutes(): Flow<Int>
    suspend fun setMaxPerBreastMinutes(minutes: Int)
    fun getMaxTotalFeedMinutes(): Flow<Int>
    suspend fun setMaxTotalFeedMinutes(minutes: Int)
    fun getWakeTime(): Flow<LocalTime?>
    suspend fun setWakeTime(time: LocalTime)
    fun getAutoUpdateEnabled(): Flow<Boolean>
    suspend fun setAutoUpdateEnabled(enabled: Boolean)
    fun getRichNotificationsEnabled(): Flow<Boolean>
    suspend fun setRichNotificationsEnabled(enabled: Boolean)
    fun getAppMode(): Flow<AppMode>
    suspend fun setAppMode(mode: AppMode)
    fun getShareCode(): Flow<String?>
    suspend fun setShareCode(code: String)
    suspend fun clearShareCode()

    /**
     * Atomically clears partner state (removes the share code and sets [AppMode.NONE]) only if the
     * currently stored share code still equals [code]. Returns true if it cleared, false if the
     * stored code had already changed (e.g. the partner reconnected to a different primary).
     * This prevents a stale background revoke from erasing a newer connection.
     */
    suspend fun clearPartnerStateIfShareCodeMatches(code: String): Boolean

    fun getPredictiveEnabled(): Flow<Boolean>
    suspend fun setPredictiveEnabled(enabled: Boolean)
    fun getPredictiveLeadMinutes(): Flow<Int>
    suspend fun setPredictiveLeadMinutes(minutes: Int)
    fun getQuietHoursStartMinute(): Flow<Int>
    suspend fun setQuietHoursStartMinute(minuteOfDay: Int)
    fun getQuietHoursEndMinute(): Flow<Int>
    suspend fun setQuietHoursEndMinute(minuteOfDay: Int)
    fun getNapReminderEnabled(): Flow<Boolean>
    suspend fun setNapReminderEnabled(enabled: Boolean)
    fun getNapReminderDelayMinutes(): Flow<Int>
    suspend fun setNapReminderDelayMinutes(minutes: Int)
    fun getPredictiveSleepEnabled(): Flow<Boolean>
    suspend fun setPredictiveSleepEnabled(enabled: Boolean)
    fun getPredictiveSleepLeadMinutes(): Flow<Int>
    suspend fun setPredictiveSleepLeadMinutes(minutes: Int)
    fun isImportInProgress(): Flow<Boolean>
    suspend fun markImportInProgress(startedAt: Long)
    suspend fun restoreFromBackup(data: BackupData)
}
