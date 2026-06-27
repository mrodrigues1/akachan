package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.domain.usecase.sleep.StopSleepRecordUseCase
import com.babytracker.manager.NapReminderScheduler
import com.babytracker.util.NotificationHelper
import com.babytracker.util.goAsyncWithTimeout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class SleepActionReceiver : BroadcastReceiver() {

    @Inject lateinit var stopRecord: StopSleepRecordUseCase
    @Inject lateinit var sleepSettingsRepository: SleepSettingsRepository
    @Inject lateinit var napReminderScheduler: NapReminderScheduler

    companion object {
        const val ACTION = "com.babytracker.SLEEP_ACTION"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_STOP = "stop"
        private const val TAG = "SleepActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        goAsyncWithTimeout(TAG) { handle(context, intent) }
    }

    internal suspend fun handle(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        Log.d(TAG, "Handling action=$action sessionId=$sessionId")

        if (action == ACTION_STOP) {
            val stoppedRecord = stopRecord(sessionId)
            NotificationHelper.cancelNotification(context, NotificationHelper.SLEEP_NOTIFICATION_ID)
            if (stoppedRecord != null && stoppedRecord.sleepType == SleepType.NAP) {
                val enabled = sleepSettingsRepository.getNapReminderEnabled().first()
                if (enabled) {
                    val delay = sleepSettingsRepository.getNapReminderDelayMinutes().first()
                    napReminderScheduler.schedule(Instant.now(), delay)
                }
            }
        }
    }
}
