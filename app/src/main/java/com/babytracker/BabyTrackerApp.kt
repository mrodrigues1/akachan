package com.babytracker

import android.app.Application
import com.babytracker.BuildConfig
import com.babytracker.debug.DebugDataSeeder
import com.babytracker.manager.PredictiveFeedNotificationCoordinator
import com.babytracker.util.NotificationHelper
import com.babytracker.util.createPredictiveFeedNotificationChannel
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BabyTrackerApp : Application() {

    @Inject lateinit var predictiveCoordinator: PredictiveFeedNotificationCoordinator
    @Inject lateinit var debugDataSeeder: DebugDataSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createBreastfeedingNotificationChannel(this)
        NotificationHelper.createSleepNotificationChannel(this)
        createPredictiveFeedNotificationChannel(this)
        predictiveCoordinator.start()
        if (BuildConfig.DEBUG) {
            appScope.launch { debugDataSeeder.seedIfEmpty() }
        }
    }
}
