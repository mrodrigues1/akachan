package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.domain.repository.VaccineSettingsRepository
import com.babytracker.manager.VaccineReminderScheduler
import com.babytracker.util.goAsyncWithTimeout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class VaccineReminderBootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: VaccineSettingsRepository
    @Inject lateinit var scheduler: VaccineReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldHandle(intent.action)) return
        goAsyncWithTimeout(TAG) { handle() }
    }

    internal fun shouldHandle(action: String?): Boolean = action in HANDLED_ACTIONS

    internal suspend fun handle() {
        if (!settings.getReminderEnabled().first()) return
        scheduler.rescheduleAll()
        Log.d(TAG, "Re-armed vaccine reminders after boot/time change")
    }

    private companion object {
        const val TAG = "VaccineReminderBoot"
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
