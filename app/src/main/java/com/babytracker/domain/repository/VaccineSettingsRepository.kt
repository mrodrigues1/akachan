package com.babytracker.domain.repository

import kotlinx.coroutines.flow.Flow

interface VaccineSettingsRepository {
    fun getReminderEnabled(): Flow<Boolean>
    suspend fun setReminderEnabled(enabled: Boolean)
    fun getReminderLeadDays(): Flow<Int>
    suspend fun setReminderLeadDays(days: Int)
    fun getToScheduleLeadDays(): Flow<Int>
    suspend fun setToScheduleLeadDays(days: Int)
}
