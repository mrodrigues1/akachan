package com.babytracker

import android.content.ComponentName
import android.content.Intent
import android.service.quicksettings.TileService
import com.babytracker.navigation.Routes
import com.babytracker.tile.FeedTileService
import com.babytracker.tile.SleepTileService
import com.babytracker.util.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityIntentTest {

    @Test
    fun navRouteFromIntentPrefersExplicitNavigationExtra() {
        val intent = Intent(TileService.ACTION_QS_TILE_PREFERENCES).apply {
            putExtra(NotificationHelper.EXTRA_NAV_ROUTE, Routes.SLEEP_TRACKING)
            putExtra(
                Intent.EXTRA_COMPONENT_NAME,
                ComponentName("com.babytracker", FeedTileService::class.java.name),
            )
        }

        assertEquals(Routes.SLEEP_TRACKING, navRouteFromIntent(intent))
    }

    @Test
    fun navRouteFromIntentMapsFeedTilePreferencesToBreastfeeding() {
        val intent = preferencesIntent(FeedTileService::class.java.name)

        assertEquals(Routes.BREASTFEEDING, navRouteFromIntent(intent))
    }

    @Test
    fun navRouteFromIntentMapsSleepTilePreferencesToSleepTracking() {
        val intent = preferencesIntent(SleepTileService::class.java.name)

        assertEquals(Routes.SLEEP_TRACKING, navRouteFromIntent(intent))
    }

    @Test
    fun navRouteFromIntentMapsUnknownTilePreferencesToHome() {
        val intent = preferencesIntent("com.babytracker.tile.UnknownTileService")

        assertEquals(Routes.HOME, navRouteFromIntent(intent))
    }

    @Test
    fun navRouteFromIntentReturnsNullWithoutRouteOrTilePreferencesAction() {
        assertNull(navRouteFromIntent(Intent()))
    }

    private fun preferencesIntent(className: String): Intent =
        Intent(TileService.ACTION_QS_TILE_PREFERENCES).apply {
            putExtra(
                Intent.EXTRA_COMPONENT_NAME,
                ComponentName("com.babytracker", className),
            )
        }
}
