package com.babytracker.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun isOnboardingComplete(): Flow<Boolean>
    suspend fun setOnboardingComplete(complete: Boolean)
    fun getMaxPerBreastMinutes(): Flow<Int>
    suspend fun setMaxPerBreastMinutes(minutes: Int)
    fun getMaxTotalFeedMinutes(): Flow<Int>
    suspend fun setMaxTotalFeedMinutes(minutes: Int)
}
