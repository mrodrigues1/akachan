package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.util.goAsyncWithTimeout

/**
 * Shared scaffold for reminder boot receivers that re-arm scheduled reminders after
 * BOOT_COMPLETED / time / timezone changes. Subclasses supply the feature's [tag], enabled
 * flag ([reminderEnabled]), and reschedule action ([rescheduleAll]); the boot wiring is identical.
 */
abstract class ReminderBootReceiver : BroadcastReceiver() {

    protected abstract val tag: String

    protected abstract suspend fun reminderEnabled(): Boolean

    protected abstract suspend fun rescheduleAll()

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldHandle(intent.action)) return
        goAsyncWithTimeout(tag) { handle() }
    }

    internal fun shouldHandle(action: String?): Boolean = action in HANDLED_ACTIONS

    internal suspend fun handle() {
        if (!reminderEnabled()) return
        rescheduleAll()
        Log.d(tag, "Re-armed reminders after boot/time change")
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
