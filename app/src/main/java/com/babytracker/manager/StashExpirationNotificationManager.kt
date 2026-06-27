package com.babytracker.manager

import com.babytracker.util.PENDING_INTENT_IMMUTABLE_UPDATE

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StashExpirationNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : StashExpirationScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun scheduleDaily(timeMinuteOfDay: Int) {
        val minute = timeMinuteOfDay.coerceIn(0, MAX_MINUTE_OF_DAY)
        val triggerAtMs = nextTriggerEpochMs(minute)
        // One-shot inexact alarm; recomputed and re-armed by the receiver after each fire
        // and by the boot/TIME_SET receiver, so wall-clock time is preserved across DST.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, buildPendingIntent())
        Log.d(TAG, "Scheduled next stash expiration alarm at minute=$minute (triggerAt=$triggerAtMs)")
    }

    override fun cancel() {
        alarmManager.cancel(buildPendingIntent())
        Log.d(TAG, "Cancelled stash expiration alarm")
    }

    private fun nextTriggerEpochMs(minuteOfDay: Int): Long {
        val zone = ZoneId.systemDefault()
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        val todayTrigger = LocalDate.now(zone).atTime(time).atZone(zone)
        val trigger = if (todayTrigger.toInstant().toEpochMilli() <= System.currentTimeMillis()) {
            todayTrigger.plusDays(1)
        } else {
            todayTrigger
        }
        return trigger.toInstant().toEpochMilli()
    }

    private fun buildPendingIntent(): PendingIntent {
        // Target the receiver by class-name string to avoid a compile-time dependency on
        // StashExpirationReceiver (created in AKA-80) — breaks the manager<->receiver cycle.
        // The receiver is declared in AndroidManifest, so it is never stripped.
        val intent = Intent().setClassName(context.packageName, STASH_RECEIVER_CLASS)
        return PendingIntent.getBroadcast(
            context,
            RC_STASH_EXPIRATION,
            intent,
            PENDING_INTENT_IMMUTABLE_UPDATE,
        )
    }

    companion object {
        const val RC_STASH_EXPIRATION = 4001
        const val MAX_MINUTE_OF_DAY = 1439
        private const val STASH_RECEIVER_CLASS = "com.babytracker.receiver.StashExpirationReceiver"
        private const val TAG = "StashExpirationManager"
    }
}
