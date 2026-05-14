package com.babytracker.ui.sleep

import com.babytracker.domain.model.ScheduleEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
                    label = "Nap 1",
                    emoji = "N"
                ),
                ScheduleEntry(
                    startTime = LocalTime.of(12, 0),
                    duration = Duration.ofMinutes(60),
                    label = "Nap 2",
                    emoji = "N"
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
        assertEquals("Nap 1", items[0].label)
        assertFalse(items[0].isBedtime)
        assertEquals(Duration.ofMinutes(150), items[2].wakeWindow)
        assertEquals("Bedtime", items[2].label)
        assertEquals("7:00 PM - 8:00 PM", items[2].detail)
    }
}
