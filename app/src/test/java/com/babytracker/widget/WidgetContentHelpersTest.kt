package com.babytracker.widget

import com.babytracker.domain.model.BreastSide
import com.babytracker.widget.data.SleepState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class WidgetContentHelpersTest {

    private val now: Instant = Instant.parse("2026-05-28T12:00:00Z")

    @Test
    fun `label maps LEFT to "Left"`() {
        assertEquals("Left", BreastSide.LEFT.label())
    }

    @Test
    fun `label maps RIGHT to "Right"`() {
        assertEquals("Right", BreastSide.RIGHT.label())
    }

    @Test
    fun `feedLabel uses side label or empty copy`() {
        assertEquals("Left", feedLabel(BreastSide.LEFT))
        assertEquals("Right", feedLabel(BreastSide.RIGHT))
        assertEquals("No feeds yet", feedLabel(null))
    }

    @Test
    fun `feedValue renders short elapsed or null`() {
        val start = now.minus(Duration.ofMinutes(80))
        assertEquals("1h 20m", feedValue(start, now))
        assertNull(feedValue(null, now))
    }

    @Test
    fun `sleepLabel maps each state`() {
        assertEquals("Sleeping", sleepLabel(SleepState.SLEEPING))
        assertEquals("Awake", sleepLabel(SleepState.AWAKE))
        assertEquals("No sleep yet", sleepLabel(SleepState.NONE))
    }

    @Test
    fun `sleepValue renders short elapsed by state`() {
        val sleepingSince = now.minus(Duration.ofMinutes(45))
        assertEquals("45m", sleepValue(SleepState.SLEEPING, sleepingSince, now))

        val awakeSince = now.minus(Duration.ofMinutes(30))
        assertEquals("30m", sleepValue(SleepState.AWAKE, awakeSince, now))

        assertNull(sleepValue(SleepState.NONE, null, now))
    }
}
