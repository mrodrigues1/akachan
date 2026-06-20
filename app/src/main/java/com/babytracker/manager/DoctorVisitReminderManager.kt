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
import com.babytracker.util.PENDING_INTENT_IMMUTABLE_UPDATE
import com.babytracker.util.computeReminderTriggerAtMs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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

    /** Delegates to the shared [computeReminderTriggerAtMs]; kept as a seam for unit tests. */
    internal fun computeTriggerAtMs(visitMs: Long, leadDays: Int, nowMs: Long, zone: ZoneId): Long? =
        computeReminderTriggerAtMs(visitMs, leadDays, nowMs, zone)

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
            PENDING_INTENT_IMMUTABLE_UPDATE,
        )
    }

    companion object {
        const val EXTRA_VISIT_ID = "visit_id"
        const val ACTION_FIRE = "com.babytracker.DOCTOR_VISIT_REMINDER_FIRE"
        private const val RC_BASE = 6000L
        private const val RECEIVER_CLASS = "com.babytracker.receiver.DoctorVisitReminderReceiver"
        private const val TAG = "DoctorVisitReminderManager"
    }
}
