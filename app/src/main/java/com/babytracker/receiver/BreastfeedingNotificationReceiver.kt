package com.babytracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.babytracker.util.NotificationHelper

class BreastfeedingNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationType = intent.getStringExtra("notification_type") ?: return

        when (notificationType) {
            NOTIFICATION_TYPE_MAX_TOTAL -> {
                NotificationHelper.showBreastfeedingTimeNotification(context)
            }
            NOTIFICATION_TYPE_SWITCH_SIDE -> {
                NotificationHelper.showSwitchSideNotification(context)
            }
        }
    }

    companion object {
        const val NOTIFICATION_TYPE_MAX_TOTAL = "max_total"
        const val NOTIFICATION_TYPE_SWITCH_SIDE = "switch_side"
    }
}
