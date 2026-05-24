package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalTime
import javax.inject.Inject

@AndroidEntryPoint
class NapReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                withTimeout(10_000L) {
                    handle(context)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "onReceive timed out", e)
            } finally {
                result.finish()
            }
        }
    }

    internal suspend fun handle(context: Context) {
        val enabled = settingsRepository.getNapReminderEnabled().first()
        if (!enabled) {
            Log.d(TAG, "Nap reminder disabled; suppressing stale alarm")
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
        private const val TAG = "NapReminderReceiver"
    }
}
