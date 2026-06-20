package com.babytracker.domain.repository

import kotlinx.coroutines.flow.Flow

interface DoctorVisitSettingsRepository {
    fun getReminderEnabled(): Flow<Boolean>
    suspend fun setReminderEnabled(enabled: Boolean)
    fun getReminderLeadDays(): Flow<Int>
    suspend fun setReminderLeadDays(days: Int)

    companion object {
        val ALLOWED_LEAD_DAYS = setOf(1, 3, 7, 14)
        const val DEFAULT_LEAD_DAYS = 1
    }
}
