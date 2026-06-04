package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import com.babytracker.manager.NapReminderScheduler
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class SleepActionReceiver : BroadcastReceiver() {

    @Inject lateinit var stopRecord: StopSleepRecordUseCase
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var napReminderScheduler: NapReminderScheduler

    companion object {
        const val ACTION = "com.babytracker.SLEEP_ACTION"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_STOP = "stop"
        private const val TAG = "SleepActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                withTimeout(10_000L) {
                    handle(context, intent)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "onReceive timed out", e)
            } finally {
                result.finish()
            }
        }
    }

    internal suspend fun handle(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        Log.d(TAG, "Handling action=$action sessionId=$sessionId")

        if (action == ACTION_STOP) {
            val stoppedRecord = stopRecord(sessionId)
            NotificationHelper.cancelNotification(context, NotificationHelper.SLEEP_NOTIFICATION_ID)
            if (stoppedRecord != null && stoppedRecord.sleepType == SleepType.NAP) {
                val enabled = settingsRepository.getNapReminderEnabled().first()
                if (enabled) {
                    val delay = settingsRepository.getNapReminderDelayMinutes().first()
                    napReminderScheduler.schedule(Instant.now(), delay)
                }
            }
        }
    }
}
