package com.babytracker.manager

import com.babytracker.domain.model.SleepType
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepSessionNotificationCoordinator @Inject constructor(
    private val sleepNotificationScheduler: SleepNotificationScheduler,
    private val napReminderScheduler: NapReminderScheduler,
) {
    fun cancelNotification() = sleepNotificationScheduler.cancel()

    suspend fun showNotification(id: Long, sleepType: SleepType, startTime: Instant) =
        sleepNotificationScheduler.show(id, sleepType, startTime)

    fun cancelNapReminder() = napReminderScheduler.cancel()

    suspend fun scheduleNapReminderIfEnabled(napEndTime: Instant) =
        napReminderScheduler.scheduleIfEnabled(napEndTime)
}
