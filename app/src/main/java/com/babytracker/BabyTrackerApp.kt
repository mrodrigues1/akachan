package com.babytracker

import android.app.Application
import com.babytracker.manager.PredictiveFeedNotificationCoordinator
import com.babytracker.util.NotificationHelper
import com.babytracker.util.createPredictiveFeedNotificationChannel
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BabyTrackerApp : Application() {

    @Inject lateinit var predictiveCoordinator: PredictiveFeedNotificationCoordinator

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createBreastfeedingNotificationChannel(this)
        NotificationHelper.createSleepNotificationChannel(this)
        createPredictiveFeedNotificationChannel(this)
        predictiveCoordinator.start()
    }
}
