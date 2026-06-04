package com.babytracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.babytracker.BuildConfig
import com.babytracker.debug.DebugDataSeeder
import com.babytracker.domain.repository.InventorySettingsRepository
import com.babytracker.manager.PredictiveFeedNotificationCoordinator
import com.babytracker.manager.PredictiveSleepNotificationCoordinator
import com.babytracker.manager.StashExpirationScheduler
import com.babytracker.util.NotificationHelper
import com.babytracker.util.createPredictiveFeedNotificationChannel
import com.babytracker.util.createPredictiveSleepNotificationChannel
import com.babytracker.widget.WidgetSyncManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BabyTrackerApp : Application(), Configuration.Provider {

    @Inject lateinit var predictiveCoordinator: PredictiveFeedNotificationCoordinator
    @Inject lateinit var predictiveSleepCoordinator: PredictiveSleepNotificationCoordinator
    @Inject lateinit var debugDataSeeder: DebugDataSeeder
    @Inject lateinit var widgetSyncManager: WidgetSyncManager
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var inventorySettings: InventorySettingsRepository
    @Inject lateinit var stashExpirationScheduler: StashExpirationScheduler

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
        NotificationHelper.createStashExpirationNotificationChannel(this)
        createPredictiveFeedNotificationChannel(this)
        predictiveCoordinator.start()
        if (BuildConfig.DEBUG) {
            createPredictiveSleepNotificationChannel(this)
            predictiveSleepCoordinator.start()
        }
        widgetSyncManager.start()
        reconcileStashExpirationAlarm()
        if (BuildConfig.DEBUG) {
            appScope.launch { debugDataSeeder.seedIfEmpty() }
        }
    }

    private fun reconcileStashExpirationAlarm() {
        appScope.launch {
            runCatching {
                if (
                    inventorySettings.getExpirationEnabled().first() &&
                    inventorySettings.getExpirationNotifEnabled().first()
                ) {
                    stashExpirationScheduler.scheduleDaily(
                        inventorySettings.getExpirationNotifTimeMinutes().first(),
                    )
                }
            }
        }
    }
}
