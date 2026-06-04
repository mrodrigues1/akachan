package com.babytracker.manager

import android.content.Context
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

class SleepNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : SleepNotificationScheduler {

    override suspend fun show(sessionId: Long, sleepType: SleepType, startTime: Instant) {
        val richEnabled = settingsRepository.getRichNotificationsEnabled().first()
        NotificationHelper.showSleepActive(
            context, sessionId, sleepType.name, startTime.toEpochMilli(), richEnabled
        )
    }

    override fun cancel() {
        NotificationHelper.cancelNotification(context, NotificationHelper.SLEEP_NOTIFICATION_ID)
    }
}
