package com.babytracker.domain.repository

import com.babytracker.domain.model.ThemeConfig
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getThemeConfig(): Flow<ThemeConfig>
    suspend fun setThemeConfig(themeConfig: ThemeConfig)
    fun isOnboardingComplete(): Flow<Boolean>
    suspend fun setOnboardingComplete(complete: Boolean)
    fun getMaxPerBreastMinutes(): Flow<Int>
    suspend fun setMaxPerBreastMinutes(minutes: Int)
    fun getMaxTotalFeedMinutes(): Flow<Int>
    suspend fun setMaxTotalFeedMinutes(minutes: Int)
}
