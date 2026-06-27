package com.babytracker.receiver

import com.babytracker.domain.repository.VaccineSettingsRepository
import com.babytracker.manager.VaccineReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class VaccineReminderBootReceiver : ReminderBootReceiver() {

    @Inject lateinit var settings: VaccineSettingsRepository
    @Inject lateinit var scheduler: VaccineReminderScheduler

    override val tag = "VaccineReminderBoot"

    override suspend fun reminderEnabled(): Boolean = settings.getReminderEnabled().first()

    override suspend fun rescheduleAll() = scheduler.rescheduleAll()
}
