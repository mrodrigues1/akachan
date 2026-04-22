package com.babytracker

import android.app.Application
import com.babytracker.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BabyTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createBreastfeedingNotificationChannel(this)
        NotificationHelper.createSleepNotificationChannel(this)
    }
}
