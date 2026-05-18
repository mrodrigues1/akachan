package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class DeletePumpingSessionUseCaseTest {

    private lateinit var repository: PumpingRepository
    private lateinit var useCase: DeletePumpingSessionUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        useCase = DeletePumpingSessionUseCase(repository)
    }

    @Test
    fun forwardsSessionToRepositoryDelete() = runTest {
        val session = PumpingSession(
            id = 7L,
            startTime = Instant.parse("2026-05-16T10:00:00Z"),
            breast = PumpingBreast.LEFT,
        )
        useCase(session)

        coVerify(exactly = 1) { repository.delete(session) }
    }
}
