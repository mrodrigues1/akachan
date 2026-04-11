package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.babytracker.util.NotificationHelper

class BreastfeedingNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val NOTIFICATION_TYPE_MAX_TOTAL = "max_total"
        const val NOTIFICATION_TYPE_SWITCH_SIDE = "switch_side"
        private const val TAG = "NotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationType = intent.getStringExtra("notification_type")
        Log.d(TAG, "Broadcast received: type=$notificationType")

        if (notificationType == null) {
            Log.w(TAG, "No notification_type extra found in intent")
            return
        }

        when (notificationType) {
            NOTIFICATION_TYPE_MAX_TOTAL -> {
                Log.i(TAG, "Triggering max total time notification")
                NotificationHelper.showBreastfeedingTimeNotification(context)
            }
            NOTIFICATION_TYPE_SWITCH_SIDE -> {
                Log.i(TAG, "Triggering switch side notification")
                NotificationHelper.showSwitchSideNotification(context)
            }
            else -> {
                Log.w(TAG, "Unknown notification type: $notificationType")
            }
        }
    }
}
