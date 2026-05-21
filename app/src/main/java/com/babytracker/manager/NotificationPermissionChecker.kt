package com.babytracker.manager

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import javax.inject.Inject

fun interface NotificationPermissionChecker {
    fun areNotificationsEnabled(): Boolean
}

class NotificationPermissionCheckerImpl @Inject constructor(
    private val application: Application,
) : NotificationPermissionChecker {
    override fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(application).areNotificationsEnabled()
}
