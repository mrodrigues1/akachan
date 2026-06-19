package com.babytracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.babytracker.BuildConfig
import com.babytracker.debug.DebugDataSeeder
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.usecase.baby.BootstrapBabyProfileUseCase
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.repository.InventorySettingsRepository
import com.babytracker.domain.repository.VaccineSettingsRepository
import com.babytracker.manager.FeatureSuppressionCoordinator
import com.babytracker.manager.PredictiveFeedNotificationCoordinator
import com.babytracker.manager.PredictiveSleepNotificationCoordinator
import com.babytracker.manager.StashExpirationScheduler
import com.babytracker.manager.VaccineReminderScheduler
import com.babytracker.util.NotificationHelper
import com.babytracker.util.VaccineNotificationHelper
import com.babytracker.util.createPredictiveFeedNotificationChannel
import com.babytracker.util.createPredictiveSleepNotificationChannel
import com.babytracker.widget.MilkStashWidgetSyncManager
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
    @Inject lateinit var featureSuppressionCoordinator: FeatureSuppressionCoordinator
    @Inject lateinit var debugDataSeeder: DebugDataSeeder
    @Inject lateinit var widgetSyncManager: WidgetSyncManager
    @Inject lateinit var milkStashWidgetSyncManager: MilkStashWidgetSyncManager
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var inventorySettings: InventorySettingsRepository
    @Inject lateinit var stashExpirationScheduler: StashExpirationScheduler
    @Inject lateinit var featureToggleRepository: FeatureToggleRepository
    @Inject lateinit var bootstrapBabyProfile: BootstrapBabyProfileUseCase
    @Inject lateinit var vaccineReminderScheduler: VaccineReminderScheduler
    @Inject lateinit var vaccineSettings: VaccineSettingsRepository

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
        NotificationHelper.createPartnerStashNotificationChannel(this)
        VaccineNotificationHelper.createChannel(this)
        createPredictiveFeedNotificationChannel(this)
        predictiveCoordinator.start()
        createPredictiveSleepNotificationChannel(this)
        predictiveSleepCoordinator.start()
        featureSuppressionCoordinator.start()
        widgetSyncManager.start()
        milkStashWidgetSyncManager.start()
        reconcileStashExpirationAlarm()
        reconcileVaccineReminders()
        appScope.launch { runCatching { bootstrapBabyProfile() } }
        if (BuildConfig.DEBUG) {
            appScope.launch { debugDataSeeder.seedIfEmpty() }
        }
    }

    private fun reconcileStashExpirationAlarm() {
        appScope.launch {
            runCatching {
                val inventoryEnabled =
                    AppFeature.INVENTORY in featureToggleRepository.getEnabledFeatures().first()
                if (
                    inventoryEnabled &&
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

    // Re-arm vaccine reminders on cold start (covers the OS dropping inexact alarms without a
    // BOOT_COMPLETED, e.g. after force-stop), mirroring reconcileStashExpirationAlarm().
    private fun reconcileVaccineReminders() {
        appScope.launch {
            runCatching {
                if (vaccineSettings.getReminderEnabled().first()) {
                    vaccineReminderScheduler.rescheduleAll()
                }
            }
        }
    }
}
