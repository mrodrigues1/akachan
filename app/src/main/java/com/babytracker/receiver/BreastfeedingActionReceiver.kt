package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BreastfeedingActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.babytracker.BREASTFEEDING_ACTION"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_SWITCH = "switch"
        const val ACTION_STOP = "stop"
        const val ACTION_DISMISS = "dismiss"
        const val ACTION_KEEP_GOING = "keep_going"
    }
    override fun onReceive(context: Context, intent: Intent) = Unit
}
