package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class SleepRecordTest {

    private val start = Instant.ofEpochMilli(1_000)

    @Test
    fun `accepts a null endTime for an in-progress session`() {
        SleepRecord(startTime = start, endTime = null, sleepType = SleepType.NAP)
    }

    @Test
    fun `accepts an endTime after startTime`() {
        SleepRecord(startTime = start, endTime = start.plusSeconds(1), sleepType = SleepType.NAP)
    }

    @Test
    fun `rejects an endTime equal to startTime`() {
        assertThrows(IllegalArgumentException::class.java) {
            SleepRecord(startTime = start, endTime = start, sleepType = SleepType.NAP)
        }
    }

    @Test
    fun `rejects an endTime before startTime`() {
        assertThrows(IllegalArgumentException::class.java) {
            SleepRecord(startTime = start, endTime = start.minusSeconds(1), sleepType = SleepType.NAP)
        }
    }
}
