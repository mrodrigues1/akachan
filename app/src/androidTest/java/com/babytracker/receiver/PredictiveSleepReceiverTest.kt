package com.babytracker.receiver

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.HomeTile
import com.babytracker.domain.model.MeasurementSystem
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepSettingsRepository
import com.babytracker.export.domain.model.BackupData
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.util.NotificationHelper
import com.babytracker.util.createPredictiveSleepNotificationChannel
import com.babytracker.util.showPredictiveSleepReminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class PredictiveSleepReceiverTest {

    private lateinit var context: Context
    private lateinit var nm: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        nm = context.getSystemService(NotificationManager::class.java)
        createPredictiveSleepNotificationChannel(context)
        nm.cancel(NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID)
        awaitNotificationAbsent(NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID)
    }

    @Test
    fun postsNotificationOnFireAction() {
        val bestEstimateMs = Instant.now().plusSeconds(15 * 60L).toEpochMilli()
        showPredictiveSleepReminder(context, bestEstimateMs)

        val posted = awaitNotification(NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID)
        assertNotNull(posted)
        assertEquals(NotificationHelper.PREDICTIVE_SLEEP_CHANNEL_ID, posted!!.notification.channelId)
    }

    @Test
    fun dropsFireWhenPredictionAlreadyStale() {
        val stale = Instant.now().minusSeconds(30 * 60L).toEpochMilli()
        val intent = Intent(context, PredictiveSleepReceiver::class.java).apply {
            action = PredictiveSleepReceiver.ACTION_FIRE
            putExtra(PredictiveSleepReceiver.EXTRA_BEST_ESTIMATE_MS, stale)
        }
        // Stale check runs before goAsync so no repo access needed — but we provide a proper one anyway.
        buildReceiver(enabled = true, quietStart = 0, quietEnd = 0).onReceive(context, intent)

        Thread.sleep(300)
        assertNull(nm.activeNotifications.firstOrNull { it.id == NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID })
    }

    @Test
    fun fireCopyRecomputesMinutesFromNow() {
        val bestEstimateMs = Instant.now().plusSeconds(5 * 60L).toEpochMilli()
        showPredictiveSleepReminder(context, bestEstimateMs)

        val posted = awaitNotification(NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID)
        assertNotNull("Notification not posted", posted)
        val body = posted!!.notification.extras.getCharSequence("android.text").toString()
        assertTrue("Body did not recompute minutes: $body", body.contains("in 4 min") || body.contains("in 5 min"))
    }

    @Test
    fun dropsFireWhenBestEstimateIsZero() {
        val intent = Intent(context, PredictiveSleepReceiver::class.java).apply {
            action = PredictiveSleepReceiver.ACTION_FIRE
        }
        buildReceiver(enabled = true, quietStart = 0, quietEnd = 0).onReceive(context, intent)

        Thread.sleep(300)
        assertNull(nm.activeNotifications.firstOrNull { it.id == NotificationHelper.PREDICTIVE_SLEEP_NOTIFICATION_ID })
    }

    private fun buildReceiver(
        enabled: Boolean,
        quietStart: Int,
        quietEnd: Int,
    ): PredictiveSleepReceiver = PredictiveSleepReceiver().also { receiver ->
        receiver.sleepSettingsRepository = object : SleepSettingsRepository {
            override fun getPredictiveSleepEnabled(): Flow<Boolean> = flowOf(enabled)
            override suspend fun setPredictiveSleepEnabled(enabled: Boolean) = Unit
            override fun getPredictiveSleepLeadMinutes(): Flow<Int> = flowOf(15)
            override suspend fun setPredictiveSleepLeadMinutes(minutes: Int) = Unit
            override fun getNapReminderEnabled(): Flow<Boolean> = flowOf(false)
            override suspend fun setNapReminderEnabled(enabled: Boolean) = Unit
            override fun getNapReminderDelayMinutes(): Flow<Int> = flowOf(60)
            override suspend fun setNapReminderDelayMinutes(minutes: Int) = Unit
        }
        receiver.settingsRepository = object : SettingsRepository {
            override fun getQuietHoursStartMinute(): Flow<Int> = flowOf(quietStart)
            override fun getQuietHoursEndMinute(): Flow<Int> = flowOf(quietEnd)

            override fun getThemeConfig(): Flow<ThemeConfig> = flowOf(ThemeConfig.SYSTEM)
            override suspend fun setThemeConfig(themeConfig: ThemeConfig) = Unit
            override fun getVolumeUnit(): Flow<VolumeUnit> = flowOf(VolumeUnit.ML)
            override suspend fun setVolumeUnit(unit: VolumeUnit) = Unit
            override fun getMeasurementSystem(): Flow<MeasurementSystem> = flowOf(MeasurementSystem.METRIC)
            override suspend fun setMeasurementSystem(system: MeasurementSystem) = Unit
            override fun getHomeTileOrder(): Flow<List<HomeTile>> = flowOf(HomeTile.DEFAULT_ORDER)
            override suspend fun setHomeTileOrder(order: List<HomeTile>) = Unit
            override suspend fun clearHomeTileOrder() = Unit
            override fun isOnboardingComplete(): Flow<Boolean> = flowOf(true)
            override suspend fun setOnboardingComplete(complete: Boolean) = Unit
            override fun getWakeTime(): Flow<LocalTime?> = flowOf(null)
            override suspend fun setWakeTime(time: LocalTime) = Unit
            override fun getAutoUpdateEnabled(): Flow<Boolean> = flowOf(true)
            override suspend fun setAutoUpdateEnabled(enabled: Boolean) = Unit
            override fun getRichNotificationsEnabled(): Flow<Boolean> = flowOf(true)
            override suspend fun setRichNotificationsEnabled(enabled: Boolean) = Unit
            override fun getAppMode(): Flow<AppMode> = flowOf(AppMode.NONE)
            override suspend fun setAppMode(mode: AppMode) = Unit
            override fun getShareCode(): Flow<String?> = flowOf(null)
            override suspend fun setShareCode(code: String) = Unit
            override suspend fun clearShareCode() = Unit
            override suspend fun clearPartnerStateIfShareCodeMatches(code: String): Boolean = false
            override suspend fun setQuietHoursStartMinute(minuteOfDay: Int) = Unit
            override suspend fun setQuietHoursEndMinute(minuteOfDay: Int) = Unit
            override fun isImportInProgress(): Flow<Boolean> = flowOf(false)
            override suspend fun markImportInProgress(startedAt: Long) = Unit
            override suspend fun restoreFromBackup(data: BackupData) = Unit
        }
    }

    private fun awaitNotification(id: Int, timeoutMs: Long = 2_000): android.service.notification.StatusBarNotification? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val n = nm.activeNotifications.firstOrNull { it.id == id }
            if (n != null) return n
            Thread.sleep(100)
        }
        return null
    }

    private fun awaitNotificationAbsent(id: Int, timeoutMs: Long = 2_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (nm.activeNotifications.none { it.id == id }) return
            Thread.sleep(100)
        }
    }
}
