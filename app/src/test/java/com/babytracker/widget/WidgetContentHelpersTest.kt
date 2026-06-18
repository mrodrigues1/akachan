package com.babytracker.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.babytracker.domain.model.BreastSide
import com.babytracker.widget.data.FeedState
import com.babytracker.widget.data.SleepState
import com.babytracker.widget.data.WidgetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class WidgetContentHelpersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now: Instant = Instant.parse("2026-05-28T12:00:00Z")

    @Test
    fun `label maps LEFT to "Left"`() {
        assertEquals("Left", BreastSide.LEFT.label(context))
    }

    @Test
    fun `label maps RIGHT to "Right"`() {
        assertEquals("Right", BreastSide.RIGHT.label(context))
    }

    @Test
    fun `feed row is omitted when feed disabled`() {
        val data = WidgetData.EMPTY.copy(feedEnabled = false, sleepEnabled = true)
        assertFalse(shouldShowFeedRow(data))
        assertTrue(shouldShowSleepRow(data))
    }

    @Test
    fun `both rows omitted when both disabled`() {
        val data = WidgetData.EMPTY.copy(feedEnabled = false, sleepEnabled = false)
        assertFalse(shouldShowFeedRow(data))
        assertFalse(shouldShowSleepRow(data))
    }

    @Test
    fun `feedLabel uses side label or empty copy`() {
        assertEquals("Last: Left", feedLabel(BreastSide.LEFT, FeedState.RECENT, context))
        assertEquals("Last: Right", feedLabel(BreastSide.RIGHT, FeedState.RECENT, context))
        assertEquals("Feeding: Right", feedLabel(BreastSide.RIGHT, FeedState.ACTIVE, context))
        assertEquals("Paused: Left", feedLabel(BreastSide.LEFT, FeedState.PAUSED, context))
        assertEquals("No feeds yet", feedLabel(null, FeedState.NONE, context))
    }

    @Test
    fun `feedValue renders short elapsed or null`() {
        val start = now.minus(Duration.ofMinutes(80))
        assertEquals("1h 20m ago", feedValue(start, FeedState.RECENT, now, context))
        assertEquals("1h 20m", feedValue(start, FeedState.ACTIVE, now, context))
        assertNull(feedValue(null, FeedState.RECENT, now, context))
    }

    @Test
    fun `feedSupporting adds large-layout context`() {
        val start = now.minus(Duration.ofMinutes(80))

        assertEquals("Started 1h 20m ago", feedSupporting(BreastSide.RIGHT, FeedState.ACTIVE, start, now, context))
        assertEquals("Paused, started 1h 20m ago", feedSupporting(BreastSide.RIGHT, FeedState.PAUSED, start, now, context))
        assertEquals("Last side Right", feedSupporting(BreastSide.RIGHT, FeedState.RECENT, start, now, context))
        assertNull(feedSupporting(null, FeedState.NONE, null, now, context))
    }

    @Test
    fun `sleepLabel maps each state`() {
        assertEquals("Sleeping", sleepLabel(SleepState.SLEEPING, context))
        assertEquals("Awake", sleepLabel(SleepState.AWAKE, context))
        assertEquals("No sleep yet", sleepLabel(SleepState.NONE, context))
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
    fun `sleepSupporting adds large-layout context`() {
        val sleepingSince = now.minus(Duration.ofMinutes(45))
        assertEquals("Asleep 45m", sleepSupporting(SleepState.SLEEPING, sleepingSince, now, context))

        val awakeSince = now.minus(Duration.ofMinutes(20))
        assertEquals("Awake 20m", sleepSupporting(SleepState.AWAKE, awakeSince, now, context))

        assertNull(sleepSupporting(SleepState.NONE, null, now, context))
    }

    @Test
    fun `feedContentDescription merges side and elapsed`() {
        val start = now.minus(Duration.ofMinutes(80))
        assertEquals(
            "Last feeding started 1h 20m ago; last side used Right",
            feedContentDescription(BreastSide.RIGHT, FeedState.RECENT, start, now, context),
        )
        assertEquals(
            "Feeding active: Right side, started 1h 20m ago",
            feedContentDescription(BreastSide.RIGHT, FeedState.ACTIVE, start, now, context),
        )
    }

    @Test
    fun `feedContentDescription falls back when no feed`() {
        assertEquals("No feeds yet", feedContentDescription(null, FeedState.NONE, null, now, context))
        assertEquals("No feeds yet", feedContentDescription(BreastSide.LEFT, FeedState.RECENT, null, now, context))
    }

    @Test
    fun `sleepContentDescription describes each state`() {
        val sleepingSince = now.minus(Duration.ofMinutes(45))
        assertEquals("Sleeping for 45m", sleepContentDescription(SleepState.SLEEPING, sleepingSince, now, context))

        val awakeSince = now.minus(Duration.ofMinutes(20))
        assertEquals("Awake for 20m", sleepContentDescription(SleepState.AWAKE, awakeSince, now, context))

        assertEquals("No sleep yet", sleepContentDescription(SleepState.NONE, null, now, context))
    }

    @Test
    fun `sleepContentDescription falls back when timestamp missing`() {
        assertEquals("Sleeping", sleepContentDescription(SleepState.SLEEPING, null, now, context))
        assertEquals("Awake", sleepContentDescription(SleepState.AWAKE, null, now, context))
    }

    @Test
    fun `widgetStatusSummary favors active care state`() {
        val feedingData = WidgetData.EMPTY.copy(
            lastFeedSide = BreastSide.LEFT,
            lastFeedStart = now.minus(Duration.ofMinutes(15)),
            feedState = FeedState.ACTIVE,
            sleepState = SleepState.SLEEPING,
            sleepSince = now.minus(Duration.ofMinutes(45)),
        )
        assertEquals("Feeding 15m", widgetStatusSummary(feedingData, now, context))

        val sleepingData = WidgetData.EMPTY.copy(
            sleepState = SleepState.SLEEPING,
            sleepSince = now.minus(Duration.ofMinutes(45)),
        )
        assertEquals("Sleeping 45m", widgetStatusSummary(sleepingData, now, context))
    }
}
