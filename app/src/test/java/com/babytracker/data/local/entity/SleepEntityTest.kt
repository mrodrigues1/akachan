package com.babytracker.data.local.entity

import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class SleepEntityTest {

    @Test
    fun `toEntity then toDomain round-trips all fields`() {
        val domain = SleepRecord(
            id = 7,
            startTime = Instant.ofEpochMilli(1_000),
            endTime = Instant.ofEpochMilli(2_000),
            sleepType = SleepType.NIGHT_SLEEP,
            notes = "restless",
            timezoneId = "America/New_York",
            clientId = "client-7",
            startedBy = SleepAuthor.PARTNER,
        )

        val restored = domain.toEntity().toDomain()

        assertEquals(domain, restored)
    }

    @Test
    fun `toDomain falls back to OWNER on unknown startedBy instead of crashing`() {
        val entity = SleepEntity(
            id = 1,
            startTime = 1_000,
            sleepType = "NAP",
            clientId = "client-1",
            startedBy = "garbage",
        )

        assertEquals(SleepAuthor.OWNER, entity.toDomain().startedBy)
    }

    @Test
    fun `toDomain nulls out a genuinely inverted endTime instead of crashing`() {
        val entity = SleepEntity(
            id = 1,
            startTime = 2_000,
            endTime = 1_000,
            sleepType = "NAP",
            clientId = "client-1",
        )

        assertEquals(null, entity.toDomain().endTime)
    }

    @Test
    fun `toDomain nudges a zero-duration completed row instead of surfacing it as active`() {
        val entity = SleepEntity(
            id = 1,
            startTime = 2_000,
            endTime = 2_000,
            sleepType = "NAP",
            clientId = "client-1",
        )

        assertEquals(Instant.ofEpochMilli(2_001), entity.toDomain().endTime)
    }
}
