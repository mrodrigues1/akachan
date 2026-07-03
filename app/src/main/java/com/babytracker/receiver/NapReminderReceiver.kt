package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.util.NotificationHelper
import com.babytracker.util.goAsyncWithTimeout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import javax.inject.Inject

@AndroidEntryPoint
class NapReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var sleepSettingsRepository: SleepSettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val triggerAtMs = intent.getLongExtra(EXTRA_TRIGGER_AT_MS, -1L)
        goAsyncWithTimeout(TAG) { handle(context, triggerAtMs) }
    }

    internal suspend fun handle(context: Context, triggerAtMs: Long) {
        if (triggerAtMs > 0L && System.currentTimeMillis() - triggerAtMs > STALE_THRESHOLD_MS) {
            Log.d(TAG, "Stale alarm suppressed (scheduled=$triggerAtMs)")
            return
        }

        val enabled = sleepSettingsRepository.getNapReminderEnabled().first()
        if (!enabled) {
            Log.d(TAG, "Nap reminder disabled; suppressing")
            return
        }

        val startMinute = settingsRepository.getQuietHoursStartMinute().first()
        val endMinute = settingsRepository.getQuietHoursEndMinute().first()
        val now = LocalTime.now()
        val currentMinute = now.hour * 60 + now.minute

        val inQuietWindow = if (startMinute <= endMinute) {
            currentMinute in startMinute until endMinute
        } else {
            currentMinute >= startMinute || currentMinute < endMinute
        }

        if (inQuietWindow) {
            Log.d(TAG, "In quiet window ($startMinute–$endMinute min); suppressing nap reminder")
            return
        }

        try {
            NotificationHelper.showNapReminder(context)
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS denied; skipping nap reminder", e)
        }
    }

    companion object {
        const val EXTRA_TRIGGER_AT_MS = "trigger_at_ms"
        private const val STALE_THRESHOLD_MS = 2L * 60 * 60 * 1000L
        private const val TAG = "NapReminderReceiver"
    }
}
