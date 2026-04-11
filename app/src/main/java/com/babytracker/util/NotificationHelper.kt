package com.babytracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.babytracker.MainActivity
import com.babytracker.R

object NotificationHelper {
    const val BREASTFEEDING_CHANNEL_ID = "breastfeeding_notifications"
    const val BREASTFEEDING_NOTIFICATION_ID = 1001
    const val SWITCH_SIDE_NOTIFICATION_ID = 1002
    private const val TAG = "NotificationHelper"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BREASTFEEDING_CHANNEL_ID,
                "Breastfeeding Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for breastfeeding session timers"
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $BREASTFEEDING_CHANNEL_ID")
        } else {
            Log.d(TAG, "Skipping channel creation on pre-Oreo device")
        }
    }

    fun showBreastfeedingTimeNotification(
        context: Context,
        notificationId: Int = BREASTFEEDING_NOTIFICATION_ID
    ) {
        Log.d(TAG, "Showing breastfeeding time notification (ID: $notificationId)")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Breastfeeding Time is Up!")
            .setContentText("The breastfeeding session has reached the maximum time. It's time to stop.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Notification posted successfully")
    }

    fun showSwitchSideNotification(
        context: Context,
        notificationId: Int = SWITCH_SIDE_NOTIFICATION_ID
    ) {
        Log.d(TAG, "Showing switch side notification (ID: $notificationId)")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Time to Switch Sides")
            .setContentText("It's time to switch to the other breast for balanced feeding.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Notification posted successfully")
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancel(notificationId)
        Log.d(TAG, "Notification cancelled (ID: $notificationId)")
    }
}
