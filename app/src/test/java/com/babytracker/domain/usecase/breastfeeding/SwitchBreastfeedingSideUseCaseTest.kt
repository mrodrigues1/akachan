package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SwitchBreastfeedingSideUseCaseTest {

    private lateinit var repository: BreastfeedingRepository
    private lateinit var useCase: SwitchBreastfeedingSideUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = SwitchBreastfeedingSideUseCase(repository)
    }

    @Test
    fun `invoke_setsSwitchTimeOnSession`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(600),
            endTime = null,
            startingSide = BreastSide.LEFT,
            switchTime = null
        )
        val updatedSlot = slot<BreastfeedingSession>()
        coJustRun { repository.updateSession(capture(updatedSlot)) }

        useCase(session)

        assertNotNull(updatedSlot.captured.switchTime)
    }

    @Test
    fun `invoke_doesNotOverrideExistingSwitch`() = runTest {
        val existingSwitch = Instant.now().minusSeconds(300)
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(600),
            endTime = null,
            startingSide = BreastSide.LEFT,
            switchTime = existingSwitch
        )

        useCase(session)

        coVerify(exactly = 0) { repository.updateSession(any()) }
    }

    @Test
    fun `invoke_callsRepositoryUpdate`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(600),
            endTime = null,
            startingSide = BreastSide.LEFT,
            switchTime = null
        )
        coJustRun { repository.updateSession(any()) }

        useCase(session)

        coVerify(exactly = 1) { repository.updateSession(any()) }
    }
}
