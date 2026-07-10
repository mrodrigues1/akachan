package com.babytracker.util

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.babytracker.R
import com.babytracker.domain.model.SleepType
import com.babytracker.sharing.usecase.PartnerSleepNotification
import com.babytracker.sharing.usecase.SleepNotifyKind
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PartnerSleepNotificationHelperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    @Before
    fun setup() {
        PartnerSleepNotificationHelper.createPartnerSleepNotificationChannel(context)
        VaccineNotificationHelper.createChannel(context)
    }

    @Test
    fun `partner-sleep and vaccine notification ids no longer collide`() {
        assertNotEquals(
            VaccineNotificationHelper.NOTIFICATION_ID,
            PartnerSleepNotificationHelper.PARTNER_SLEEP_NOTIFICATION_ID,
        )
    }

    @Test
    fun `legacy cleanup cancels pre-772 partner-sleep notification under 1011`() {
        val legacy = NotificationCompat.Builder(context, PartnerSleepNotificationHelper.PARTNER_SLEEP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_sleep)
            .setContentTitle("legacy partner sleep")
            .build()
        manager.notify(VaccineNotificationHelper.NOTIFICATION_ID, legacy)

        PartnerSleepNotificationHelper.cancelLegacyCollidingNotification(context)

        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun `legacy cleanup preserves a vaccine notification under 1011`() {
        val vaccine = NotificationCompat.Builder(context, VaccineNotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_limit)
            .setContentTitle("vaccine")
            .build()
        manager.notify(VaccineNotificationHelper.NOTIFICATION_ID, vaccine)

        PartnerSleepNotificationHelper.cancelLegacyCollidingNotification(context)

        assertTrue(
            manager.activeNotifications.map { it.id }
                .contains(VaccineNotificationHelper.NOTIFICATION_ID),
        )
    }

    @Test
    fun `posting a partner-sleep notification leaves a live vaccine notification in the tray`() {
        val vaccine = NotificationCompat.Builder(context, VaccineNotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_limit)
            .setContentTitle("vaccine")
            .build()
        manager.notify(VaccineNotificationHelper.NOTIFICATION_ID, vaccine)

        PartnerSleepNotificationHelper.showPartnerSleepChange(
            context,
            PartnerSleepNotification(SleepNotifyKind.STARTED, SleepType.NAP),
        )

        val activeIds = manager.activeNotifications.map { it.id }
        assertTrue(activeIds.contains(VaccineNotificationHelper.NOTIFICATION_ID))
        assertTrue(activeIds.contains(PartnerSleepNotificationHelper.PARTNER_SLEEP_NOTIFICATION_ID))
    }
}
