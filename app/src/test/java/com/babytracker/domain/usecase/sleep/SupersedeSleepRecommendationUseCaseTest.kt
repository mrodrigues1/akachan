package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.model.RecommendationOutcome
import com.babytracker.domain.repository.SleepRecommendationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SupersedeSleepRecommendationUseCaseTest {

    private lateinit var repository: SleepRecommendationRepository
    private lateinit var createFeedback: CreateSleepRecommendationFeedbackUseCase
    private lateinit var useCase: SupersedeSleepRecommendationUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        createFeedback = mockk(relaxed = true)
        coEvery { createFeedback(any(), any(), any(), any(), any()) } returns 1L
        useCase = SupersedeSleepRecommendationUseCase(repository, createFeedback)
    }

    @Test
    fun `marks the recommendation SUPERSEDED`() = runTest {
        useCase(42L)

        coVerify { repository.updateLifecycle(42L, RecommendationLifecycle.SUPERSEDED) }
    }

    @Test
    fun `creates a paired SUPERSEDED feedback row`() = runTest {
        useCase(42L)

        coVerify { createFeedback(42L, RecommendationOutcome.SUPERSEDED) }
    }

    @Test
    fun `updates the lifecycle before creating feedback`() = runTest {
        useCase(42L)

        coVerifyOrder {
            repository.updateLifecycle(42L, RecommendationLifecycle.SUPERSEDED)
            createFeedback(42L, RecommendationOutcome.SUPERSEDED)
        }
    }

    @Test
    fun `markScheduled marks the recommendation SCHEDULED without creating feedback`() = runTest {
        useCase.markScheduled(42L)

        coVerify { repository.updateLifecycle(42L, RecommendationLifecycle.SCHEDULED) }
        coVerify(exactly = 0) { createFeedback(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `recordFeedback delegates straight through to the feedback use case`() = runTest {
        val sleepStart = Instant.parse("2026-06-06T10:00:00Z")
        val bestEstimate = Instant.parse("2026-06-06T09:50:00Z")
        coEvery {
            createFeedback(42L, RecommendationOutcome.ACTED_IN_WINDOW, 7L, sleepStart, bestEstimate)
        } returns 9L

        val result = useCase.recordFeedback(
            recommendationId = 42L,
            outcome = RecommendationOutcome.ACTED_IN_WINDOW,
            actualSleepRecordId = 7L,
            sleepStartTime = sleepStart,
            windowBestEstimate = bestEstimate,
        )

        assertEquals(9L, result)
        coVerify(exactly = 0) { repository.updateLifecycle(any(), any()) }
    }
}
