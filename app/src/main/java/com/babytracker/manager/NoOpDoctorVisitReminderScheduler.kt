package com.babytracker.manager

import com.babytracker.domain.model.DoctorVisit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpDoctorVisitReminderScheduler @Inject constructor() : DoctorVisitReminderScheduler {
    override suspend fun schedule(visit: DoctorVisit) = Unit
    override fun cancel(visitId: Long) = Unit
    override suspend fun rescheduleAll() = Unit
}
