package com.babytracker.receiver

import com.babytracker.domain.repository.DoctorVisitSettingsRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class DoctorVisitReminderBootReceiver : ReminderBootReceiver() {

    @Inject lateinit var settings: DoctorVisitSettingsRepository
    @Inject lateinit var scheduler: DoctorVisitReminderScheduler

    override val tag = "DoctorVisitReminderBoot"

    override suspend fun reminderEnabled(): Boolean = settings.getReminderEnabled().first()

    override suspend fun rescheduleAll() = scheduler.rescheduleAll()
}
