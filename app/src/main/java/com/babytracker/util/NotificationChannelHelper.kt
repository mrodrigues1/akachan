package com.babytracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes

/** Creates a notification channel (no-op below API 26). Shared by the notification helpers. */
internal fun createNotificationChannel(
    context: Context,
    channelId: String,
    @StringRes nameRes: Int,
    importance: Int,
    @StringRes descriptionRes: Int,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(channelId, context.getString(nameRes), importance)
            .apply { description = context.getString(descriptionRes) }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
