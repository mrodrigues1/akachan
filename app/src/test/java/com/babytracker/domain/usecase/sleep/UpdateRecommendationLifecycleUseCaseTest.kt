package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.repository.SleepRecommendationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UpdateRecommendationLifecycleUseCaseTest {

    private lateinit var repository: SleepRecommendationRepository
    private lateinit var useCase: UpdateRecommendationLifecycleUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        coEvery { repository.updateLifecycle(any(), any()) } returns 1
        useCase = UpdateRecommendationLifecycleUseCase(repository)
    }

    @Test
    fun `delegates to repository with correct id and lifecycle`() = runTest {
        useCase(42L, RecommendationLifecycle.SCHEDULED)
        coVerify(exactly = 1) { repository.updateLifecycle(42L, RecommendationLifecycle.SCHEDULED) }
    }

    @Test
    fun `FIRED lifecycle is delegated correctly`() = runTest {
        useCase(7L, RecommendationLifecycle.FIRED)
        coVerify { repository.updateLifecycle(7L, RecommendationLifecycle.FIRED) }
    }

    @Test
    fun `SUPERSEDED lifecycle is delegated correctly`() = runTest {
        useCase(3L, RecommendationLifecycle.SUPERSEDED)
        coVerify { repository.updateLifecycle(3L, RecommendationLifecycle.SUPERSEDED) }
    }

    @Test
    fun `returns 0 when row is already in terminal state`() = runTest {
        coEvery { repository.updateLifecycle(any(), any()) } returns 0

        val result = useCase(1L, RecommendationLifecycle.SCHEDULED)

        assertEquals(0, result)
    }

    @Test
    fun `returns 1 when update succeeds`() = runTest {
        val result = useCase(1L, RecommendationLifecycle.SCHEDULED)

        assertEquals(1, result)
    }
}
