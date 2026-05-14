package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalTime

class ScheduleEntryTest {

    @Test
    fun `default emoji is sleeping face for nap entries`() {
        // Regression: NapCard previously hardcoded the emoji; the model
        // now owns it so callers read entry.emoji.
        val entry = ScheduleEntry(
            startTime = LocalTime.of(9, 0),
            duration = Duration.ofMinutes(60),
            label = "Nap 1",
        )
        assertEquals("😴", entry.emoji)
    }

    @Test
    fun `custom emoji is preserved`() {
        val entry = ScheduleEntry(
            startTime = LocalTime.of(20, 0),
            duration = Duration.ofHours(11),
            label = "Bedtime",
            emoji = "🌙",
        )
        assertEquals("🌙", entry.emoji)
    }
}
