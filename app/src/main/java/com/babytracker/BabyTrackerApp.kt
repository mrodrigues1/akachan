package com.babytracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.babytracker.BuildConfig
import com.babytracker.debug.DebugDataSeeder
import com.babytracker.manager.PredictiveFeedNotificationCoordinator
import com.babytracker.util.NotificationHelper
import com.babytracker.util.createPredictiveFeedNotificationChannel
import com.babytracker.widget.WidgetSyncManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BabyTrackerApp : Application(), Configuration.Provider {

    @Inject lateinit var predictiveCoordinator: PredictiveFeedNotificationCoordinator
    @Inject lateinit var debugDataSeeder: DebugDataSeeder
    @Inject lateinit var widgetSyncManager: WidgetSyncManager
    @Inject lateinit var workerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createBreastfeedingNotificationChannel(this)
        NotificationHelper.createSleepNotificationChannel(this)
        createPredictiveFeedNotificationChannel(this)
        predictiveCoordinator.start()
        widgetSyncManager.start()
        if (BuildConfig.DEBUG) {
            appScope.launch { debugDataSeeder.seedIfEmpty() }
        }
    }
}
