package com.babytracker.domain.repository

import com.babytracker.domain.model.ThemeConfig
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

interface SettingsRepository {
    fun getThemeConfig(): Flow<ThemeConfig>
    suspend fun setThemeConfig(themeConfig: ThemeConfig)
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
}
