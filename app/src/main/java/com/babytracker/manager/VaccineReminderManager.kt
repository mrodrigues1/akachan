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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
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
        if (record.status != VaccineStatus.SCHEDULED) return
        val scheduled = record.scheduledDate ?: return
        if (!settings.getReminderEnabled().first()) return
        val leadDays = settings.getReminderLeadDays().first()
        val triggerAtMs = computeTriggerAtMs(
            scheduledMs = scheduled.toEpochMilli(),
            leadDays = leadDays,
            nowMs = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
        ) ?: return // no sensible reminder window before the shot

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMs,
            buildPendingIntent(record.id),
        )
        Log.d(TAG, "Scheduled vaccine reminder id=${record.id} at $triggerAtMs")
    }

    /**
     * Reminder trigger: ~09:00 local, [leadDays] before the scheduled date. If that lead window
     * has already passed, fall back to the NEXT 09:00 strictly after now — but never at/after the
     * scheduled instant (no immediate late-night ping, no post-shot ping). Returns null when no
     * window remains. Pure + testable without AlarmManager.
     */
    internal fun computeTriggerAtMs(scheduledMs: Long, leadDays: Int, nowMs: Long, zone: ZoneId): Long? {
        if (scheduledMs <= nowMs) return null
        fun nineAmOn(date: LocalDate): Long =
            date.atTime(REMINDER_HOUR_OF_DAY, 0).atZone(zone).toInstant().toEpochMilli()
        val scheduledDate = Instant.ofEpochMilli(scheduledMs).atZone(zone).toLocalDate()
        val leadTrigger = nineAmOn(scheduledDate.minusDays(leadDays.toLong()))
        val candidate = if (leadTrigger > nowMs) {
            leadTrigger
        } else {
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            val today9 = nineAmOn(today)
            if (today9 > nowMs) today9 else nineAmOn(today.plusDays(1))
        }
        return candidate.takeIf { it < scheduledMs }
    }

    override fun cancel(recordId: Long) {
        alarmManager.cancel(buildPendingIntent(recordId))
    }

    override suspend fun rescheduleAll() {
        val enabled = settings.getReminderEnabled().first()
        val future = repository.getScheduledFutureAfter(System.currentTimeMillis())
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_VACCINE_ID = "vaccine_id"
        const val REMINDER_HOUR_OF_DAY = 9
        private const val RC_BASE = 5000L
        private const val RECEIVER_CLASS = "com.babytracker.receiver.VaccineReminderReceiver"
        private const val TAG = "VaccineReminderManager"
    }
}
