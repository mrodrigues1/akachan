package com.babytracker.ui.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimerDisplayTest {

    @Test
    fun formatElapsedAsMinutesSeconds_underOneMinute_formatsAsMmSs() {
        assertEquals("00:45", formatElapsedAsMinutesSeconds(45))
    }

    @Test
    fun formatElapsedAsMinutesSeconds_oneMinuteAndFiveSeconds_formatsAsMmSs() {
        assertEquals("01:05", formatElapsedAsMinutesSeconds(65))
    }

    @Test
    fun formatElapsedAsMinutesSeconds_overOneHour_keepsTotalMinutes() {
        assertEquals("61:01", formatElapsedAsMinutesSeconds(3661))
    }

    @Test
    fun shouldAnimateTimerRing_runningWithRing_returnsTrue() {
        assertEquals(true, shouldAnimateTimerRing(hasRing = true, isRunning = true))
    }

    @Test
    fun shouldAnimateTimerRing_runningWithoutRing_returnsFalse() {
        assertEquals(false, shouldAnimateTimerRing(hasRing = false, isRunning = true))
    }

    @Test
    fun shouldAnimateTimerRing_pausedWithRing_returnsFalse() {
        assertEquals(false, shouldAnimateTimerRing(hasRing = true, isRunning = false))
    }
}
