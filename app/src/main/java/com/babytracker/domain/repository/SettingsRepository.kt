package com.babytracker.domain.repository

import com.babytracker.domain.model.HomeTile
import com.babytracker.domain.model.MeasurementSystem
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
    fun getMeasurementSystem(): Flow<MeasurementSystem>
    suspend fun setMeasurementSystem(system: MeasurementSystem)
    fun getHomeTileOrder(): Flow<List<HomeTile>>
    suspend fun setHomeTileOrder(order: List<HomeTile>)
    suspend fun clearHomeTileOrder()
    fun isOnboardingComplete(): Flow<Boolean>
    suspend fun setOnboardingComplete(complete: Boolean)
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

    // Quiet hours are shared between predictive feeding and predictive sleep notifications, so they
    // remain here rather than moving to FeedSettingsRepository / SleepSettingsRepository.
    fun getQuietHoursStartMinute(): Flow<Int>
    suspend fun setQuietHoursStartMinute(minuteOfDay: Int)
    fun getQuietHoursEndMinute(): Flow<Int>
    suspend fun setQuietHoursEndMinute(minuteOfDay: Int)
    fun isImportInProgress(): Flow<Boolean>
    suspend fun markImportInProgress(startedAt: Long)
    suspend fun restoreFromBackup(data: BackupData)
}
