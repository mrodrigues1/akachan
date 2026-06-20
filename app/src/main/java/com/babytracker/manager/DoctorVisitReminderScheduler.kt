package com.babytracker.manager

import com.babytracker.domain.model.DoctorVisit

interface DoctorVisitReminderScheduler {
    suspend fun schedule(visit: DoctorVisit)
    fun cancel(visitId: Long)
    suspend fun rescheduleAll()
}
