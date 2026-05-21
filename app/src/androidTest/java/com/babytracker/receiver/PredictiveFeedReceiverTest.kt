package com.babytracker.receiver

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.babytracker.util.NotificationHelper
import com.babytracker.util.createPredictiveFeedNotificationChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PredictiveFeedReceiverTest {

    @get:Rule
    val grantNotificationPermission: GrantPermissionRule =
        GrantPermissionRule.grant("android.permission.POST_NOTIFICATIONS")

    private lateinit var context: Context
    private lateinit var nm: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        nm = context.getSystemService(NotificationManager::class.java)
        createPredictiveFeedNotificationChannel(context)
        nm.cancel(NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID)
    }

    @Test
    fun postsNotificationOnFireAction() {
        val intent = Intent(context, PredictiveFeedReceiver::class.java).apply {
            action = PredictiveFeedReceiver.ACTION_FIRE
            putExtra(
                PredictiveFeedReceiver.EXTRA_PREDICTED_AT_MS,
                Instant.now().plusSeconds(15 * 60L).toEpochMilli(),
            )
        }
        PredictiveFeedReceiver().onReceive(context, intent)

        val posted = nm.activeNotifications.firstOrNull {
            it.id == NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID
        }
        assertNotNull(posted)
        assertEquals(NotificationHelper.PREDICTIVE_FEED_CHANNEL_ID, posted!!.notification.channelId)
    }

    @Test
    fun dropsFireWhenPredictionAlreadyStale() {
        val stale = Instant.now().minusSeconds(30 * 60L).toEpochMilli()
        val intent = Intent(context, PredictiveFeedReceiver::class.java).apply {
            action = PredictiveFeedReceiver.ACTION_FIRE
            putExtra(PredictiveFeedReceiver.EXTRA_PREDICTED_AT_MS, stale)
        }
        PredictiveFeedReceiver().onReceive(context, intent)

        val posted = nm.activeNotifications.firstOrNull {
            it.id == NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID
        }
        assertNull(posted)
    }

    @Test
    fun fireCopyRecomputesMinutesFromNow() {
        val predictedAt = Instant.now().plusSeconds(5 * 60L).toEpochMilli()
        val intent = Intent(context, PredictiveFeedReceiver::class.java).apply {
            action = PredictiveFeedReceiver.ACTION_FIRE
            putExtra(PredictiveFeedReceiver.EXTRA_PREDICTED_AT_MS, predictedAt)
        }
        PredictiveFeedReceiver().onReceive(context, intent)

        val posted = nm.activeNotifications.first {
            it.id == NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID
        }
        val body = posted.notification.extras.getCharSequence("android.text").toString()
        assertTrue("Body did not recompute minutes: $body", body.contains("in 4 min") || body.contains("in 5 min"))
    }

    @Test
    fun snoozeDiscardsCurrentNotification() {
        val predictedAt = Instant.now().plusSeconds(20 * 60L).toEpochMilli()
        // First post a notification so there is something to cancel.
        val fireIntent = Intent(context, PredictiveFeedReceiver::class.java).apply {
            action = PredictiveFeedReceiver.ACTION_FIRE
            putExtra(PredictiveFeedReceiver.EXTRA_PREDICTED_AT_MS, predictedAt)
        }
        PredictiveFeedReceiver().onReceive(context, fireIntent)
        assertNotNull(
            nm.activeNotifications.firstOrNull { it.id == NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID },
        )

        // Snooze: should cancel the existing notification and schedule a new alarm.
        val snoozeIntent = Intent(context, PredictiveFeedReceiver::class.java).apply {
            action = PredictiveFeedReceiver.ACTION_SNOOZE
            putExtra(PredictiveFeedReceiver.EXTRA_PREDICTED_AT_MS, predictedAt)
        }
        PredictiveFeedReceiver().onReceive(context, snoozeIntent)

        assertNull(
            nm.activeNotifications.firstOrNull { it.id == NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID },
        )
    }

    @Test
    fun dropsFireWhenPredictedAtIsZero() {
        val intent = Intent(context, PredictiveFeedReceiver::class.java).apply {
            action = PredictiveFeedReceiver.ACTION_FIRE
            // No EXTRA_PREDICTED_AT_MS → defaults to 0
        }
        PredictiveFeedReceiver().onReceive(context, intent)

        assertNull(
            nm.activeNotifications.firstOrNull { it.id == NotificationHelper.PREDICTIVE_FEED_NOTIFICATION_ID },
        )
    }
}
