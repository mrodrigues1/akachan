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
    fun `feedChipLabel pairs side with elapsed`() {
        val start = now.minus(Duration.ofMinutes(80))
        assertEquals("Right · 1h 20m", feedChipLabel(BreastSide.RIGHT, start, now))
    }

    @Test
    fun `feedChipLabel falls back when no feed recorded`() {
        assertEquals("No feeds yet", feedChipLabel(null, null, now))
    }

    @Test
    fun `sleepChipLabel describes sleeping with duration`() {
        val since = now.minus(Duration.ofMinutes(45))
        assertEquals("Sleeping 45m", sleepChipLabel(SleepState.SLEEPING, since, now))
    }

    @Test
    fun `sleepChipLabel describes awake with duration`() {
        val since = now.minus(Duration.ofHours(2))
        assertEquals("Awake 2h 00m", sleepChipLabel(SleepState.AWAKE, since, now))
    }

    @Test
    fun `sleepChipLabel falls back when no sleep recorded`() {
        assertEquals("No sleep yet", sleepChipLabel(SleepState.NONE, null, now))
    }

    @Test
    fun `feedTitle uses side label or empty copy`() {
        assertEquals("Left", feedTitle(BreastSide.LEFT))
        assertEquals("No feeds yet", feedTitle(null))
    }

    @Test
    fun `feedValue renders elapsed-ago or null`() {
        val start = now.minus(Duration.ofMinutes(80))
        assertEquals("1h 20m ago", feedValue(start, now))
        assertNull(feedValue(null, now))
    }

    @Test
    fun `sleepTitle maps each state`() {
        assertEquals("Sleeping", sleepTitle(SleepState.SLEEPING))
        assertEquals("Awake", sleepTitle(SleepState.AWAKE))
        assertEquals("No sleep logged", sleepTitle(SleepState.NONE))
    }

    @Test
    fun `sleepValue renders by state`() {
        val sleepingSince = now.minus(Duration.ofMinutes(45))
        assertEquals("45m", sleepValue(SleepState.SLEEPING, sleepingSince, now))

        val awakeSince = now.minus(Duration.ofMinutes(30))
        assertEquals("30m ago", sleepValue(SleepState.AWAKE, awakeSince, now))

        assertNull(sleepValue(SleepState.NONE, null, now))
    }
}
