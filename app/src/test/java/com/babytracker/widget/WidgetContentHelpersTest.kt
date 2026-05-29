package com.babytracker.widget

import com.babytracker.domain.model.BreastSide
import com.babytracker.widget.data.FeedState
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
        assertEquals("Last: Left", feedLabel(BreastSide.LEFT, FeedState.RECENT))
        assertEquals("Last: Right", feedLabel(BreastSide.RIGHT, FeedState.RECENT))
        assertEquals("Feeding: Right", feedLabel(BreastSide.RIGHT, FeedState.ACTIVE))
        assertEquals("Paused: Left", feedLabel(BreastSide.LEFT, FeedState.PAUSED))
        assertEquals("No feeds yet", feedLabel(null, FeedState.NONE))
    }

    @Test
    fun `feedValue renders short elapsed or null`() {
        val start = now.minus(Duration.ofMinutes(80))
        assertEquals("1h 20m ago", feedValue(start, FeedState.RECENT, now))
        assertEquals("1h 20m", feedValue(start, FeedState.ACTIVE, now))
        assertNull(feedValue(null, FeedState.RECENT, now))
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

    @Test
    fun `feedContentDescription merges side and elapsed`() {
        val start = now.minus(Duration.ofMinutes(80))
        assertEquals(
            "Last feeding started 1h 20m ago; last side used Right",
            feedContentDescription(BreastSide.RIGHT, FeedState.RECENT, start, now),
        )
        assertEquals(
            "Feeding active: Right side, started 1h 20m ago",
            feedContentDescription(BreastSide.RIGHT, FeedState.ACTIVE, start, now),
        )
    }

    @Test
    fun `feedContentDescription falls back when no feed`() {
        assertEquals("No feeds yet", feedContentDescription(null, FeedState.NONE, null, now))
        assertEquals("No feeds yet", feedContentDescription(BreastSide.LEFT, FeedState.RECENT, null, now))
    }

    @Test
    fun `sleepContentDescription describes each state`() {
        val sleepingSince = now.minus(Duration.ofMinutes(45))
        assertEquals("Sleeping for 45m", sleepContentDescription(SleepState.SLEEPING, sleepingSince, now))

        val awakeSince = now.minus(Duration.ofMinutes(20))
        assertEquals("Awake for 20m", sleepContentDescription(SleepState.AWAKE, awakeSince, now))

        assertEquals("No sleep yet", sleepContentDescription(SleepState.NONE, null, now))
    }

    @Test
    fun `sleepContentDescription falls back when timestamp missing`() {
        assertEquals("Sleeping", sleepContentDescription(SleepState.SLEEPING, null, now))
        assertEquals("Awake", sleepContentDescription(SleepState.AWAKE, null, now))
    }
}
