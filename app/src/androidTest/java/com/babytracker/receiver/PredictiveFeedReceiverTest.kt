package com.babytracker.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.util.NotificationHelper
import com.babytracker.util.PENDING_INTENT_IMMUTABLE_UPDATE
import com.babytracker.util.createPredictiveFeedNotificationChannel
import com.babytracker.util.showPredictiveReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PredictiveFeedReceiverTest {

    private lateinit var context: Context
    private lateinit var nm: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        nm = context.getSystemService(NotificationManager::class.java)
        createPredictiveFeedNotificationChannel(context)
        nm.cancel(NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID)
        awaitNotificationAbsent(NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID)
    }

    // Rendering assertions call the notification helper directly, mirroring PredictiveSleepReceiverTest.
    // The receiver can't be driven here for a "posted" assertion: it is an @AndroidEntryPoint receiver,
    // so onReceive re-injects the real SettingsRepository (overwriting any test double), and the real
    // quiet-hours default (00:00–08:00) would suppress the notification depending on wall-clock time.
    @Test
    fun postsNotificationOnFireAction() {
        val predictedAt = Instant.now().plusSeconds(15 * 60L).toEpochMilli()
        showPredictiveReminder(context, predictedAt, snoozePendingIntent(predictedAt))

        val posted = awaitNotification(NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID)
        assertNotNull(posted)
        assertEquals(NotificationHelper.PREDICTIVE_FEED_CHANNEL_ID, posted!!.notification.channelId)
    }

    @Test
    fun fireCopyRecomputesMinutesFromNow() {
        val predictedAt = Instant.now().plusSeconds(5 * 60L).toEpochMilli()
        showPredictiveReminder(context, predictedAt, snoozePendingIntent(predictedAt))

        val posted = awaitNotification(NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID)
        assertNotNull("Notification not posted", posted)
        val body = posted!!.notification.extras.getCharSequence("android.text").toString()
        assertTrue("Body did not recompute minutes: $body", body.contains("in 4 min") || body.contains("in 5 min"))
    }

    // Drop paths are quiet-hours-independent (staleness is decided before quiet hours; a zero
    // predictedAt returns before goAsync), so driving the real receiver stays deterministic.
    @Test
    fun dropsFireWhenPredictionAlreadyStale() {
        val stale = Instant.now().minusSeconds(30 * 60L).toEpochMilli()
        PredictiveFeedReceiver().onReceive(context, fireIntent(stale))

        Thread.sleep(300)
        assertNull(nm.activeNotifications.firstOrNull { it.id == NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID })
    }

    @Test
    fun snoozeDiscardsCurrentNotification() {
        val predictedAt = Instant.now().plusSeconds(20 * 60L).toEpochMilli()
        showPredictiveReminder(context, predictedAt, snoozePendingIntent(predictedAt))
        assertNotNull("Fire did not post notification", awaitNotification(NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID))

        val snoozeIntent = Intent(context, PredictiveFeedReceiver::class.java).apply {
            action = PredictiveFeedReceiver.ACTION_SNOOZE
            putExtra(PredictiveFeedReceiver.EXTRA_PREDICTED_AT_MS, predictedAt)
        }
        PredictiveFeedReceiver().onReceive(context, snoozeIntent)

        awaitNotificationAbsent(NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID)
        assertNull(nm.activeNotifications.firstOrNull { it.id == NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID })
    }

    @Test
    fun dropsFireWhenPredictedAtIsZero() {
        val intent = Intent(context, PredictiveFeedReceiver::class.java).apply {
            action = PredictiveFeedReceiver.ACTION_FIRE
        }
        PredictiveFeedReceiver().onReceive(context, intent)

        Thread.sleep(300)
        assertNull(nm.activeNotifications.firstOrNull { it.id == NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID })
    }

    private fun fireIntent(predictedAtEpochMs: Long) =
        Intent(context, PredictiveFeedReceiver::class.java).apply {
            action = PredictiveFeedReceiver.ACTION_FIRE
            putExtra(PredictiveFeedReceiver.EXTRA_PREDICTED_AT_MS, predictedAtEpochMs)
        }

    private fun snoozePendingIntent(predictedAt: Long): PendingIntent {
        val intent = Intent(context, PredictiveFeedReceiver::class.java).apply {
            action = PredictiveFeedReceiver.ACTION_SNOOZE
            putExtra(PredictiveFeedReceiver.EXTRA_PREDICTED_AT_MS, predictedAt)
        }
        return PendingIntent.getBroadcast(
            context, PredictiveFeedReceiver.REQUEST_CODE_SNOOZE, intent,
            PENDING_INTENT_IMMUTABLE_UPDATE,
        )
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
