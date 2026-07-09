package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ShouldUpdateWakeTimeTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 4, 9)

    @Test
    fun `returns false for a nap even if it ends today`() {
        val endTime = today.atTime(7, 0).atZone(zone).toInstant()

        val result = shouldUpdateWakeTimeFor(endTime, SleepType.NAP, emptyList(), zone, today)

        assertFalse(result)
    }

    @Test
    fun `returns false when the night sleep ends on a different day`() {
        val endTime = today.minusDays(1).atTime(6, 0).atZone(zone).toInstant()

        val result = shouldUpdateWakeTimeFor(endTime, SleepType.NIGHT_SLEEP, emptyList(), zone, today)

        assertFalse(result)
    }

    @Test
    fun `returns true when it is the only night sleep ending today`() {
        val endTime = today.atTime(7, 0).atZone(zone).toInstant()

        val result = shouldUpdateWakeTimeFor(endTime, SleepType.NIGHT_SLEEP, emptyList(), zone, today)

        assertTrue(result)
    }

    @Test
    fun `returns false when another night sleep already ends later today`() {
        val endTime = today.atTime(4, 0).atZone(zone).toInstant()
        val later = SleepRecord(
            id = 1L,
            startTime = today.atTime(5, 0).atZone(zone).toInstant(),
            endTime = today.atTime(8, 0).atZone(zone).toInstant(),
            sleepType = SleepType.NIGHT_SLEEP,
        )

        val result = shouldUpdateWakeTimeFor(endTime, SleepType.NIGHT_SLEEP, listOf(later), zone, today)

        assertFalse(result)
    }

    @Test
    fun `returns true when this record is the latest ending today`() {
        val earlier = SleepRecord(
            id = 1L,
            startTime = today.atTime(0, 0).atZone(zone).toInstant(),
            endTime = today.atTime(4, 0).atZone(zone).toInstant(),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        val endTime = today.atTime(8, 0).atZone(zone).toInstant()

        val result = shouldUpdateWakeTimeFor(endTime, SleepType.NIGHT_SLEEP, listOf(earlier), zone, today)

        assertTrue(result)
    }

    @Test
    fun `returns true when tied with the latest other end time`() {
        val other = SleepRecord(
            id = 1L,
            startTime = today.atTime(0, 0).atZone(zone).toInstant(),
            endTime = today.atTime(7, 0).atZone(zone).toInstant(),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        val endTime = today.atTime(7, 0).atZone(zone).toInstant()

        val result = shouldUpdateWakeTimeFor(endTime, SleepType.NIGHT_SLEEP, listOf(other), zone, today)

        assertTrue(result)
    }

    @Test
    fun `ignores naps when finding the latest other night-sleep end`() {
        val nap = SleepRecord(
            id = 1L,
            startTime = today.atTime(9, 0).atZone(zone).toInstant(),
            endTime = today.atTime(10, 0).atZone(zone).toInstant(),
            sleepType = SleepType.NAP,
        )
        val endTime = today.atTime(7, 0).atZone(zone).toInstant()

        val result = shouldUpdateWakeTimeFor(endTime, SleepType.NIGHT_SLEEP, listOf(nap), zone, today)

        assertTrue(result)
    }

    @Test
    fun `excludes the record being edited when checking for a later night sleep`() {
        // Editing record id=1, which itself currently has the "latest" end time today; without
        // excludingId it would compare against itself and always report false.
        val self = SleepRecord(
            id = 1L,
            startTime = today.atTime(0, 0).atZone(zone).toInstant(),
            endTime = today.atTime(8, 0).atZone(zone).toInstant(),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        val newEndTime = today.atTime(6, 0).atZone(zone).toInstant()

        val result = shouldUpdateWakeTimeFor(
            newEndTime,
            SleepType.NIGHT_SLEEP,
            listOf(self),
            zone,
            today,
            excludingId = 1L,
        )

        assertTrue(result)
    }
}
