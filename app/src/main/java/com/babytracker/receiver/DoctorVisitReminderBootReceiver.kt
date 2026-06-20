package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.repository.DoctorVisitSettingsRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import com.babytracker.util.goAsyncWithTimeout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class DoctorVisitReminderBootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: DoctorVisitSettingsRepository

    @Inject lateinit var scheduler: DoctorVisitReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldHandle(intent.action)) return
        goAsyncWithTimeout(TAG) { handle() }
    }

    internal fun shouldHandle(action: String?): Boolean = action in HANDLED_ACTIONS

    internal suspend fun handle() {
        if (!settings.getReminderEnabled().first()) return
        scheduler.rescheduleAll()
        Log.d(TAG, "Re-armed doctor visit reminders after boot/time change")
    }

    private companion object {
        const val TAG = "DoctorVisitReminderBoot"
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
