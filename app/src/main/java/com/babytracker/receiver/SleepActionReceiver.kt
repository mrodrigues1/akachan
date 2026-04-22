package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SleepActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.babytracker.SLEEP_ACTION"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_STOP = "stop"
    }
    override fun onReceive(context: Context, intent: Intent) = Unit
}
