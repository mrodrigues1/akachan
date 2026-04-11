package com.babytracker

import android.app.Application
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BabyTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create notification channel on app startup (required for Android 8.0+)
        NotificationHelper.createNotificationChannel(this)
    }
}
