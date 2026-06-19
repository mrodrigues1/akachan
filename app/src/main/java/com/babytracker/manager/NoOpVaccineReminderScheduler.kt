package com.babytracker.manager

import com.babytracker.domain.model.VaccineRecord
import javax.inject.Inject

class NoOpVaccineReminderScheduler @Inject constructor() : VaccineReminderScheduler {
    override suspend fun schedule(record: VaccineRecord) = Unit
    override fun cancel(recordId: Long) = Unit
    override suspend fun rescheduleAll() = Unit
}
