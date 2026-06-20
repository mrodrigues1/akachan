package com.babytracker.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.repository.DoctorVisitSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorVisitReminderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DoctorVisitRepository,
    private val settings: DoctorVisitSettingsRepository,
) : DoctorVisitReminderScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override suspend fun schedule(visit: DoctorVisit) {
        // Always clear any prior alarm for this id (idempotent re-arm).
        cancel(visit.id)
        if (!settings.getReminderEnabled().first()) return
        val leadDays = settings.getReminderLeadDays().first()
        val triggerAtMs = computeTriggerAtMs(
            visitMs = visit.date.toEpochMilli(),
            leadDays = leadDays,
            nowMs = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
        ) ?: return // no sensible reminder window before the visit

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMs,
            buildPendingIntent(visit.id),
        )
        Log.d(TAG, "Scheduled doctor visit reminder id=${visit.id} at $triggerAtMs")
    }

    /**
     * Reminder trigger: ~09:00 local, [leadDays] before the visit date. If that lead window has
     * already passed, fall back to the NEXT 09:00 strictly after now — but never at/after the visit
     * instant (no immediate late-night ping, no post-visit ping). Returns null when no window
     * remains or the visit is already past. Pure + testable without AlarmManager.
     */
    internal fun computeTriggerAtMs(visitMs: Long, leadDays: Int, nowMs: Long, zone: ZoneId): Long? {
        if (visitMs <= nowMs) return null
        fun nineAmOn(date: LocalDate): Long =
            date.atTime(REMINDER_HOUR_OF_DAY, 0).atZone(zone).toInstant().toEpochMilli()
        val visitDate = Instant.ofEpochMilli(visitMs).atZone(zone).toLocalDate()
        val leadTrigger = nineAmOn(visitDate.minusDays(leadDays.toLong()))
        val candidate = if (leadTrigger > nowMs) {
            leadTrigger
        } else {
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            val today9 = nineAmOn(today)
            if (today9 > nowMs) today9 else nineAmOn(today.plusDays(1))
        }
        return candidate.takeIf { it < visitMs }
    }

    override fun cancel(visitId: Long) {
        alarmManager.cancel(buildPendingIntent(visitId))
    }

    override suspend fun rescheduleAll() {
        val enabled = settings.getReminderEnabled().first()
        val upcoming = repository.getUpcomingVisitsAfter(System.currentTimeMillis())
        upcoming.forEach { visit ->
            if (enabled) schedule(visit) else cancel(visit.id)
        }
    }

    private fun buildPendingIntent(visitId: Long): PendingIntent {
        val intent = Intent()
            .setClassName(context.packageName, RECEIVER_CLASS)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("doctor_visit://reminder/$visitId"))
            .putExtra(EXTRA_VISIT_ID, visitId)
        return PendingIntent.getBroadcast(
            context,
            (RC_BASE + visitId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_VISIT_ID = "visit_id"
        const val ACTION_FIRE = "com.babytracker.DOCTOR_VISIT_REMINDER_FIRE"
        const val REMINDER_HOUR_OF_DAY = 9
        private const val RC_BASE = 6000L
        private const val RECEIVER_CLASS = "com.babytracker.receiver.DoctorVisitReminderReceiver"
        private const val TAG = "DoctorVisitReminderManager"
    }
}
