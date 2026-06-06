package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class UpdateSleepEntryUseCaseTest {

    private lateinit var repository: SleepRepository
    private lateinit var useCase: UpdateSleepEntryUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = UpdateSleepEntryUseCase(repository)
    }

    @Test
    fun `invoke preserves provided timezone provenance`() = runTest {
        val start = Instant.parse("2026-04-08T09:30:00Z")
        val end = Instant.parse("2026-04-08T11:00:00Z")
        val slot = slot<SleepRecord>()
        coJustRun { repository.updateRecord(capture(slot)) }

        useCase(
            id = 7L,
            startTime = start,
            endTime = end,
            type = SleepType.NAP,
            timezoneId = "America/New_York",
        )

        coVerify(exactly = 1) { repository.updateRecord(any()) }
        assertEquals("America/New_York", slot.captured.timezoneId)
    }
}
