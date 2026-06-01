package com.babytracker.tile

import android.os.Build
import android.service.quicksettings.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TileRenderMapperTest {

    @Test
    fun activeAvailabilityMapsToActiveTileState() {
        assertEquals(Tile.STATE_ACTIVE, TileAvailability.ACTIVE.toQsTileState())
    }

    @Test
    fun inactiveAvailabilityMapsToInactiveTileState() {
        assertEquals(Tile.STATE_INACTIVE, TileAvailability.INACTIVE.toQsTileState())
    }

    @Test
    fun unavailableAvailabilityMapsToUnavailableTileState() {
        assertEquals(Tile.STATE_UNAVAILABLE, TileAvailability.UNAVAILABLE.toQsTileState())
    }

    @Test
    fun subtitleIsReturnedOnAndroidQAndNewer() {
        val state = TileRenderState(TileAvailability.ACTIVE, "Stop Feed", "12m")

        assertEquals("12m", tileSubtitleForSdk(state, Build.VERSION_CODES.Q))
    }

    @Test
    fun subtitleIsOmittedBeforeAndroidQ() {
        val state = TileRenderState(TileAvailability.ACTIVE, "Stop Feed", "12m")

        assertNull(tileSubtitleForSdk(state, Build.VERSION_CODES.P))
    }
}
