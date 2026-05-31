package com.babytracker.tile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TileTapPolicyTest {

    @Test
    fun secureAndLockedOpensApp() {
        assertEquals(
            TileTapAction.OPEN_APP,
            shouldOpenAppOnTap(isSecure = true, isLocked = true, state = TileAvailability.INACTIVE),
        )
    }

    @Test
    fun secureAndUnlockedToggles() {
        assertEquals(
            TileTapAction.TOGGLE_SILENTLY,
            shouldOpenAppOnTap(isSecure = true, isLocked = false, state = TileAvailability.INACTIVE),
        )
    }

    @Test
    fun notSecureAndLockedToggles() {
        assertEquals(
            TileTapAction.TOGGLE_SILENTLY,
            shouldOpenAppOnTap(isSecure = false, isLocked = true, state = TileAvailability.INACTIVE),
        )
    }

    @Test
    fun unavailableOpensAppWhenLocked() {
        assertEquals(
            TileTapAction.OPEN_APP,
            shouldOpenAppOnTap(isSecure = false, isLocked = true, state = TileAvailability.UNAVAILABLE),
        )
    }

    @Test
    fun unavailableOpensAppWhenUnlocked() {
        assertEquals(
            TileTapAction.OPEN_APP,
            shouldOpenAppOnTap(isSecure = false, isLocked = false, state = TileAvailability.UNAVAILABLE),
        )
    }

    @Test
    fun unavailableOpensAppEvenWhenNotSecure() {
        assertEquals(
            TileTapAction.OPEN_APP,
            shouldOpenAppOnTap(isSecure = true, isLocked = false, state = TileAvailability.UNAVAILABLE),
        )
    }
}
