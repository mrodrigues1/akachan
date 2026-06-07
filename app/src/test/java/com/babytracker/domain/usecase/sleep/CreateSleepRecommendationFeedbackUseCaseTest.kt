package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.RecommendationOutcome
import com.babytracker.domain.repository.SleepRecommendationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class CreateSleepRecommendationFeedbackUseCaseTest {

    private lateinit var repository: SleepRecommendationRepository
    private val fixedNow = Instant.parse("2026-06-06T10:00:00Z")
    private val nowProvider: () -> Instant = { fixedNow }

    private lateinit var useCase: CreateSleepRecommendationFeedbackUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        coEvery { repository.insertFeedback(any(), any(), any(), any(), any()) } returns 1L
        useCase = CreateSleepRecommendationFeedbackUseCase(repository, nowProvider)
    }

    @Test
    fun `ACTED_IN_WINDOW — sleep start 10 min late gives positive error_minutes`() = runTest {
        val bestEstimate = Instant.parse("2026-06-06T10:00:00Z")
        val sleepStart = bestEstimate.plusSeconds(600)

        useCase(
            recommendationId = 1L,
            outcome = RecommendationOutcome.ACTED_IN_WINDOW,
            actualSleepRecordId = 10L,
            sleepStartTime = sleepStart,
            windowBestEstimate = bestEstimate,
        )

        coVerify {
            repository.insertFeedback(1L, 10L, 10, RecommendationOutcome.ACTED_IN_WINDOW, fixedNow.toEpochMilli())
        }
    }

    @Test
    fun `ACTED_IN_WINDOW — sleep start 10 min early gives negative error_minutes`() = runTest {
        val bestEstimate = Instant.parse("2026-06-06T10:00:00Z")
        val sleepStart = bestEstimate.minusSeconds(600)

        useCase(
            recommendationId = 2L,
            outcome = RecommendationOutcome.ACTED_IN_WINDOW,
            actualSleepRecordId = 20L,
            sleepStartTime = sleepStart,
            windowBestEstimate = bestEstimate,
        )

        coVerify {
            repository.insertFeedback(2L, 20L, -10, RecommendationOutcome.ACTED_IN_WINDOW, any())
        }
    }

    @Test
    fun `QUIET_HOURS_SUPPRESSED — null sleep params produce null error_minutes`() = runTest {
        useCase(
            recommendationId = 3L,
            outcome = RecommendationOutcome.QUIET_HOURS_SUPPRESSED,
        )

        coVerify {
            repository.insertFeedback(3L, null, null, RecommendationOutcome.QUIET_HOURS_SUPPRESSED, any())
        }
    }

    @Test
    fun `SUPERSEDED — null sleep params produce null error_minutes`() = runTest {
        useCase(
            recommendationId = 4L,
            outcome = RecommendationOutcome.SUPERSEDED,
        )

        coVerify { repository.insertFeedback(4L, null, null, RecommendationOutcome.SUPERSEDED, any()) }
    }

    @Test
    fun `uses nowProvider for createdAt`() = runTest {
        useCase(recommendationId = 5L, outcome = RecommendationOutcome.SUPERSEDED)

        coVerify { repository.insertFeedback(any(), any(), any(), any(), fixedNow.toEpochMilli()) }
    }
}
