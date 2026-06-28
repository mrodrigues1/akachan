package com.babytracker.ui.sleep

import com.babytracker.domain.model.ScheduleEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalTime

class SleepScheduleTimelineTest {

    @Test
    fun `timeline combines each wake window with its following sleep block`() {
        val items = buildSleepTimelineItems(
            napTimes = listOf(
                ScheduleEntry(
                    startTime = LocalTime.of(9, 0),
                    duration = Duration.ofMinutes(45),
                    napNumber = 1,
                ),
                ScheduleEntry(
                    startTime = LocalTime.of(12, 0),
                    duration = Duration.ofMinutes(60),
                    napNumber = 2,
                )
            ),
            wakeWindows = listOf(
                Duration.ofMinutes(90),
                Duration.ofMinutes(120),
                Duration.ofMinutes(150)
            ),
            bedtime = LocalTime.of(19, 30),
            bedtimeWindow = LocalTime.of(19, 0)..LocalTime.of(20, 0)
        )

        assertEquals(3, items.size)
        assertEquals(Duration.ofMinutes(90), items[0].wakeWindow)
        assertEquals(1, items[0].napNumber)
        assertFalse(items[0].isBedtime)
        assertEquals(Duration.ofMinutes(150), items[2].wakeWindow)
        assertTrue(items[2].isBedtime)
        assertEquals(LocalTime.of(19, 0)..LocalTime.of(20, 0), items[2].bedtimeWindow)
    }
}
