package com.babytracker.manager

import com.babytracker.util.PENDING_INTENT_IMMUTABLE_UPDATE

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.receiver.NapReminderReceiver
import com.babytracker.receiver.NapReminderReceiver.Companion.EXTRA_TRIGGER_AT_MS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NapReminderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sleepSettingsRepository: SleepSettingsRepository,
    private val featureToggleRepository: FeatureToggleRepository,
) : NapReminderScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(napEndTime: Instant, delayMinutes: Int) {
        val triggerAtMs = napEndTime.plusSeconds(delayMinutes.toLong() * 60).toEpochMilli()
        val pi = buildPendingIntent(triggerAtMs)
        alarmManager.cancel(pi)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SCHEDULE_EXACT_ALARM permission revoked; falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        }
    }

    override fun cancel() {
        alarmManager.cancel(buildPendingIntent())
    }

    override suspend fun scheduleIfEnabled(napEndTime: Instant) {
        val sleepEnabled = AppFeature.SLEEP in featureToggleRepository.getEnabledFeatures().first()
        val enabled = sleepEnabled && sleepSettingsRepository.getNapReminderEnabled().first()
        if (enabled) {
            val delayMinutes = sleepSettingsRepository.getNapReminderDelayMinutes().first()
            schedule(napEndTime, delayMinutes)
        }
    }

    private fun buildPendingIntent(triggerAtMs: Long = -1L): PendingIntent {
        val intent = Intent(context, NapReminderReceiver::class.java).apply {
            if (triggerAtMs > 0L) putExtra(EXTRA_TRIGGER_AT_MS, triggerAtMs)
        }
        return PendingIntent.getBroadcast(
            context,
            RC_NAP_REMINDER,
            intent,
            PENDING_INTENT_IMMUTABLE_UPDATE,
        )
    }

    companion object {
        const val RC_NAP_REMINDER = 3001
        private const val TAG = "NapReminderManager"
    }
}
