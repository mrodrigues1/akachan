package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SwitchBreastfeedingSideUseCaseTest {

    private val now = Instant.parse("2026-01-15T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var repository: BreastfeedingRepository
    private lateinit var useCase: SwitchBreastfeedingSideUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = SwitchBreastfeedingSideUseCase(repository, clock)
    }

    @Test
    fun invokeSetsSwitchTimeOnSession() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = now.minusSeconds(600),
            endTime = null,
            startingSide = BreastSide.LEFT,
            switchTime = null
        )
        val updatedSlot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(updatedSlot)) }

        val result = useCase(session)

        assertNotNull(updatedSlot.captured.switchTime)
        assertEquals(now, result.switchTime)
        assertEquals(now, updatedSlot.captured.switchTime)
    }

    @Test
    fun invokeDoesNotOverrideExistingSwitch() = runTest {
        val existingSwitch = now.minusSeconds(300)
        val session = BreastfeedingSession(
            id = 1L,
            startTime = now.minusSeconds(600),
            endTime = null,
            startingSide = BreastSide.LEFT,
            switchTime = existingSwitch
        )

        val result = useCase(session)

        assertSame(session, result)
        coVerify(exactly = 0) { repository.updateSession(any()) }
    }

    @Test
    fun invokeCallsRepositoryUpdate() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = now.minusSeconds(600),
            endTime = null,
            startingSide = BreastSide.LEFT,
            switchTime = null
        )
        coJustRun { repository.updateSession(any()) }

        useCase(session)

        coVerify(exactly = 1) { repository.updateSession(any()) }
    }
}
