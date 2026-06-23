package com.babytracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.babytracker.BuildConfig
import com.babytracker.debug.DebugDataSeeder
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.usecase.baby.BootstrapBabyProfileUseCase
import com.babytracker.domain.repository.DoctorVisitSettingsRepository
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.repository.InventorySettingsRepository
import com.babytracker.domain.repository.VaccineSettingsRepository
import com.babytracker.manager.DoctorVisitReminderScheduler
import com.babytracker.manager.FeatureSuppressionCoordinator
import com.babytracker.manager.PredictiveFeedNotificationCoordinator
import com.babytracker.manager.PredictiveSleepNotificationCoordinator
import com.babytracker.manager.StashExpirationScheduler
import com.babytracker.manager.VaccineReminderScheduler
import com.babytracker.ui.milestone.evictMilestoneBitmapCache
import com.babytracker.ui.milestone.trimMilestoneBitmapCache
import com.babytracker.util.DoctorVisitNotificationHelper
import com.babytracker.util.NotificationHelper
import com.babytracker.util.VaccineNotificationHelper
import com.babytracker.util.createPredictiveFeedNotificationChannel
import com.babytracker.util.createPredictiveSleepNotificationChannel
import com.babytracker.widget.MilkStashWidgetSyncManager
import com.babytracker.widget.WidgetSyncManager
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BabyTrackerApp : Application(), Configuration.Provider {

    // workerFactory must resolve synchronously: workManagerConfiguration is read by WorkManager
    // during early init. Everything else below is start-only and wrapped in Lazy so the Hilt graph
    // (Room DB builder + 14 migrations, DataStore, repositories, the predictive use cases) is not
    // constructed on the main thread during onCreate — it is resolved on the IO appScope instead.
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var predictiveCoordinator: Lazy<PredictiveFeedNotificationCoordinator>
    @Inject lateinit var predictiveSleepCoordinator: Lazy<PredictiveSleepNotificationCoordinator>
    @Inject lateinit var featureSuppressionCoordinator: Lazy<FeatureSuppressionCoordinator>
    @Inject lateinit var debugDataSeeder: Lazy<DebugDataSeeder>
    @Inject lateinit var widgetSyncManager: Lazy<WidgetSyncManager>
    @Inject lateinit var milkStashWidgetSyncManager: Lazy<MilkStashWidgetSyncManager>
    @Inject lateinit var inventorySettings: Lazy<InventorySettingsRepository>
    @Inject lateinit var stashExpirationScheduler: Lazy<StashExpirationScheduler>
    @Inject lateinit var featureToggleRepository: Lazy<FeatureToggleRepository>
    @Inject lateinit var bootstrapBabyProfile: Lazy<BootstrapBabyProfileUseCase>
    @Inject lateinit var vaccineReminderScheduler: Lazy<VaccineReminderScheduler>
    @Inject lateinit var vaccineSettings: Lazy<VaccineSettingsRepository>
    @Inject lateinit var doctorVisitReminderScheduler: Lazy<DoctorVisitReminderScheduler>
    @Inject lateinit var doctorVisitSettings: Lazy<DoctorVisitSettingsRepository>

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Channel creation (8 cross-process binder calls) and coordinator startup don't need the main
        // thread and only have to complete before the first notification — run them on the IO scope so
        // they're off the cold-start critical path. Channels are created before any coordinator starts.
        appScope.launch {
            createNotificationChannels()
            predictiveCoordinator.get().start()
            predictiveSleepCoordinator.get().start()
            featureSuppressionCoordinator.get().start()
            widgetSyncManager.get().start()
            milkStashWidgetSyncManager.get().start()
        }
        reconcileStashExpirationAlarm()
        reconcileVaccineReminders()
        reconcileDoctorVisitReminders()
        appScope.launch { runCatching { bootstrapBabyProfile.get().invoke() } }
        if (BuildConfig.DEBUG) {
            appScope.launch { debugDataSeeder.get().seedIfEmpty() }
        }
    }

    // The milestone photo cache is the only large process-lifetime allocation in the app and there
    // was previously no trim-memory handling at all. Hooking these lets the system reclaim up to its
    // full budget on demand instead of killing the process; the cache transparently re-decodes later.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        trimMilestoneBitmapCache(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        evictMilestoneBitmapCache()
    }

    private fun createNotificationChannels() {
        NotificationHelper.createBreastfeedingNotificationChannel(this)
        NotificationHelper.createSleepNotificationChannel(this)
        NotificationHelper.createStashExpirationNotificationChannel(this)
        NotificationHelper.createPartnerStashNotificationChannel(this)
        VaccineNotificationHelper.createChannel(this)
        DoctorVisitNotificationHelper.createChannel(this)
        createPredictiveFeedNotificationChannel(this)
        createPredictiveSleepNotificationChannel(this)
    }

    private fun reconcileStashExpirationAlarm() {
        appScope.launch {
            runCatching {
                val inventoryEnabled =
                    AppFeature.INVENTORY in featureToggleRepository.get().getEnabledFeatures().first()
                if (
                    inventoryEnabled &&
                    inventorySettings.get().getExpirationEnabled().first() &&
                    inventorySettings.get().getExpirationNotifEnabled().first()
                ) {
                    stashExpirationScheduler.get().scheduleDaily(
                        inventorySettings.get().getExpirationNotifTimeMinutes().first(),
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
                if (vaccineSettings.get().getReminderEnabled().first()) {
                    vaccineReminderScheduler.get().rescheduleAll()
                }
            }
        }
    }

    // Re-arm doctor-visit reminders on cold start, mirroring reconcileVaccineReminders().
    private fun reconcileDoctorVisitReminders() {
        appScope.launch {
            runCatching {
                if (doctorVisitSettings.get().getReminderEnabled().first()) {
                    doctorVisitReminderScheduler.get().rescheduleAll()
                }
            }
        }
    }
}
