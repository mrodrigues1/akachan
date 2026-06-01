package com.babytracker.tile

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.babytracker.MainActivity
import com.babytracker.navigation.Routes
import com.babytracker.util.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TileIntentsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun feedTileIntentTargetsMainActivityWithFeedRoute() {
        val intent = feedTileIntent(context)

        assertMainActivityIntent(intent)
        assertEquals(Routes.BREASTFEEDING, intent.getStringExtra(NotificationHelper.EXTRA_NAV_ROUTE))
    }

    @Test
    fun sleepTileIntentTargetsMainActivityWithSleepRoute() {
        val intent = sleepTileIntent(context)

        assertMainActivityIntent(intent)
        assertEquals(Routes.SLEEP_TRACKING, intent.getStringExtra(NotificationHelper.EXTRA_NAV_ROUTE))
    }

    @Test
    fun unavailableTileIntentTargetsMainActivityWithoutFeatureRoute() {
        val intent = unavailableTileIntent(context)

        assertMainActivityIntent(intent)
        assertFalse(intent.hasExtra(NotificationHelper.EXTRA_NAV_ROUTE))
    }

    @Test
    fun activeFeedStateRoutesToBreastfeeding() {
        val intent = feedIntentForState(
            context,
            TileRenderState(TileAvailability.ACTIVE, "Stop Feed", "4m"),
        )

        assertEquals(Routes.BREASTFEEDING, intent.getStringExtra(NotificationHelper.EXTRA_NAV_ROUTE))
    }

    @Test
    fun inactiveFeedStateRoutesToBreastfeeding() {
        val intent = feedIntentForState(
            context,
            TileRenderState(TileAvailability.INACTIVE, "Start Feed", null),
        )

        assertEquals(Routes.BREASTFEEDING, intent.getStringExtra(NotificationHelper.EXTRA_NAV_ROUTE))
    }

    @Test
    fun unavailableFeedStateRoutesWithoutFeatureRoute() {
        val intent = feedIntentForState(
            context,
            TileRenderState(TileAvailability.UNAVAILABLE, "Feed", null),
        )

        assertFalse(intent.hasExtra(NotificationHelper.EXTRA_NAV_ROUTE))
    }

    @Test
    fun activeSleepStateRoutesToSleepTracking() {
        val intent = sleepIntentForState(
            context,
            TileRenderState(TileAvailability.ACTIVE, "Stop Sleep", "15m"),
        )

        assertEquals(Routes.SLEEP_TRACKING, intent.getStringExtra(NotificationHelper.EXTRA_NAV_ROUTE))
    }

    @Test
    fun inactiveSleepStateRoutesToSleepTracking() {
        val intent = sleepIntentForState(
            context,
            TileRenderState(TileAvailability.INACTIVE, "Start Sleep", null),
        )

        assertEquals(Routes.SLEEP_TRACKING, intent.getStringExtra(NotificationHelper.EXTRA_NAV_ROUTE))
    }

    @Test
    fun unavailableSleepStateRoutesWithoutFeatureRoute() {
        val intent = sleepIntentForState(
            context,
            TileRenderState(TileAvailability.UNAVAILABLE, "Sleep", null),
        )

        assertFalse(intent.hasExtra(NotificationHelper.EXTRA_NAV_ROUTE))
    }

    private fun assertMainActivityIntent(intent: Intent) {
        assertEquals(context.packageName, intent.component?.packageName)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP, intent.flags)
    }
}
