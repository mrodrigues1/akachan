package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.BuildConfig
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.util.showPredictiveSleepReminder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class PredictiveSleepReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        // Guard against a debug-scheduled alarm firing on a release build after upgrade.
        // The manifest declares the receiver for all build types (required for AlarmManager
        // to resolve the PendingIntent), so this is the last line of defence.
        if (!BuildConfig.DEBUG) {
            Log.d(TAG, "Dropping alarm in release build")
            return
        }
        when (intent.action) {
            ACTION_FIRE -> postReminder(context, intent)
            else -> Log.w(TAG, "Unknown action ${intent.action}")
        }
    }

    private fun postReminder(context: Context, intent: Intent) {
        val bestEstimateMs = intent.getLongExtra(EXTRA_BEST_ESTIMATE_MS, 0L)
        if (bestEstimateMs <= 0L) {
            Log.w(TAG, "Missing bestEstimate; dropping fire")
            return
        }
        val nowMs = System.currentTimeMillis()
        val staleAfterMs = bestEstimateMs + MAX_STALE_MINUTES * 60_000L
        if (nowMs > staleAfterMs) {
            Log.i(TAG, "Prediction stale by ${(nowMs - bestEstimateMs) / 60_000L}m; dropping fire")
            return
        }
        // Re-read feature flag and quiet hours at fire time to handle inexact-alarm delivery
        // that may land inside a quiet window scheduled after the alarm was set.
        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val enabled = settingsRepository.getPredictiveSleepEnabled().first()
                if (!enabled) {
                    Log.d(TAG, "Feature disabled at fire time; dropping")
                    return@launch
                }
                val quietStart = settingsRepository.getQuietHoursStartMinute().first()
                val quietEnd = settingsRepository.getQuietHoursEndMinute().first()
                if (isInsideQuietHours(nowMs, quietStart, quietEnd)) {
                    Log.d(TAG, "Fire time inside quiet hours; suppressing notification")
                    return@launch
                }
                showPredictiveSleepReminder(context = context, bestEstimateMs = bestEstimateMs)
            } catch (e: SecurityException) {
                Log.w(TAG, "POST_NOTIFICATIONS denied; skipping reminder", e)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.babytracker.PREDICTIVE_SLEEP_FIRE"
        const val EXTRA_BEST_ESTIMATE_MS = "best_estimate_ms"
        const val REQUEST_CODE_PREDICTIVE_SLEEP = 1005
        private const val MAX_STALE_MINUTES = 20L
        private const val TAG = "PredictiveSleepRx"

        fun isInsideQuietHours(nowMs: Long, quietStartMinute: Int, quietEndMinute: Int): Boolean {
            if (quietStartMinute == quietEndMinute) return false
            val minuteOfDay = Instant.ofEpochMilli(nowMs)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .let { it.hour * 60 + it.minute }
            return if (quietStartMinute < quietEndMinute) {
                minuteOfDay in quietStartMinute until quietEndMinute
            } else {
                minuteOfDay >= quietStartMinute || minuteOfDay < quietEndMinute
            }
        }
    }
}
