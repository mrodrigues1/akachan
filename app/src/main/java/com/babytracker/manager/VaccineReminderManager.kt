package com.babytracker.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.domain.repository.VaccineSettingsRepository
import com.babytracker.util.PENDING_INTENT_IMMUTABLE_UPDATE
import com.babytracker.util.computeReminderTriggerAtMs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccineReminderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: VaccineSettingsRepository,
    private val repository: VaccineRepository,
) : VaccineReminderScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override suspend fun schedule(record: VaccineRecord) {
        // Always clear any prior alarm for this id (idempotent re-arm).
        cancel(record.id)
        val leadDays = when (record.status) {
            VaccineStatus.SCHEDULED -> settings.getReminderLeadDays().first()
            VaccineStatus.TO_SCHEDULE -> settings.getToScheduleLeadDays().first()
            VaccineStatus.ADMINISTERED -> return // a logged shot never carries a reminder
        }
        val target = record.scheduledDate ?: return
        if (!settings.getReminderEnabled().first()) return
        val triggerAtMs = computeTriggerAtMs(
            scheduledMs = target.toEpochMilli(),
            leadDays = leadDays,
            nowMs = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
        ) ?: return // no sensible reminder window before the target

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMs,
            buildPendingIntent(record.id),
        )
        Log.d(TAG, "Scheduled vaccine reminder id=${record.id} status=${record.status} at $triggerAtMs")
    }

    /** Delegates to the shared [computeReminderTriggerAtMs]; kept as a seam for unit tests. */
    internal fun computeTriggerAtMs(scheduledMs: Long, leadDays: Int, nowMs: Long, zone: ZoneId): Long? =
        computeReminderTriggerAtMs(scheduledMs, leadDays, nowMs, zone)

    override fun cancel(recordId: Long) {
        alarmManager.cancel(buildPendingIntent(recordId))
    }

    override suspend fun rescheduleAll() {
        val enabled = settings.getReminderEnabled().first()
        val nowMs = System.currentTimeMillis()
        val future = repository.getScheduledFutureAfter(nowMs) + repository.getToScheduleFutureAfter(nowMs)
        future.forEach { record ->
            if (enabled) schedule(record) else cancel(record.id)
        }
    }

    private fun buildPendingIntent(recordId: Long): PendingIntent {
        val intent = Intent()
            .setClassName(context.packageName, RECEIVER_CLASS)
            .setData(Uri.parse("vaccine://reminder/$recordId"))
            .putExtra(EXTRA_VACCINE_ID, recordId)
        return PendingIntent.getBroadcast(
            context,
            (RC_BASE + recordId).toInt(),
            intent,
            PENDING_INTENT_IMMUTABLE_UPDATE,
        )
    }

    companion object {
        const val EXTRA_VACCINE_ID = "vaccine_id"
        private const val RC_BASE = 5000L
        private const val RECEIVER_CLASS = "com.babytracker.receiver.VaccineReminderReceiver"
        private const val TAG = "VaccineReminderManager"
    }
}
