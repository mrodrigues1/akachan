package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.repository.NewSleepRecommendation
import com.babytracker.domain.repository.SleepRecommendationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class PersistSleepRecommendationUseCaseTest {

    private lateinit var repository: SleepRecommendationRepository
    private val fixedNow = Instant.parse("2026-06-06T10:00:00Z")
    private val nowProvider: () -> Instant = { fixedNow }

    private lateinit var useCase: PersistSleepRecommendationUseCase

    private val window = SleepWindow(
        windowStart = fixedNow.plusSeconds(1800),
        windowEnd = fixedNow.plusSeconds(5400),
        bestEstimate = fixedNow.plusSeconds(3600),
        sleepType = SleepType.NAP,
        confidence = Confidence.MEDIUM,
        reasons = emptyList(),
        feedDue = false,
    )

    @BeforeEach
    fun setup() {
        repository = mockk()
        useCase = PersistSleepRecommendationUseCase(repository, nowProvider)
    }

    @Test
    fun `returns new row ID when insert succeeds`() = runTest {
        coEvery { repository.insertRecommendation(any()) } returns 42L

        val id = useCase(anchorSleepId = 7L, window = window)

        assertEquals(42L, id)
    }

    @Test
    fun `falls back to getIdByAnchorTypeVersion when insert returns -1`() = runTest {
        coEvery { repository.insertRecommendation(any()) } returns -1L
        coEvery {
            repository.getIdByAnchorTypeVersion(7L, "NAP", SleepPredictionTuning.ALGORITHM_VERSION)
        } returns 99L

        val id = useCase(anchorSleepId = 7L, window = window)

        assertEquals(99L, id)
        coVerify { repository.getIdByAnchorTypeVersion(7L, "NAP", SleepPredictionTuning.ALGORITHM_VERSION) }
    }

    @Test
    fun `inserts with the window's own sleep type`() = runTest {
        val recSlot = slot<NewSleepRecommendation>()
        coEvery { repository.insertRecommendation(capture(recSlot)) } returns 1L

        useCase(anchorSleepId = 7L, window = window.copy(sleepType = SleepType.NIGHT_SLEEP))

        assertEquals("NIGHT_SLEEP", recSlot.captured.type)
    }

    @Test
    fun `inserts with GENERATED lifecycle`() = runTest {
        val recSlot = slot<NewSleepRecommendation>()
        coEvery { repository.insertRecommendation(capture(recSlot)) } returns 1L

        useCase(anchorSleepId = 7L, window = window)

        assertEquals(RecommendationLifecycle.GENERATED, recSlot.captured.lifecycle)
    }

    @Test
    fun `inserts with current ALGORITHM_VERSION`() = runTest {
        val recSlot = slot<NewSleepRecommendation>()
        coEvery { repository.insertRecommendation(capture(recSlot)) } returns 1L

        useCase(anchorSleepId = 7L, window = window)

        assertEquals(SleepPredictionTuning.ALGORITHM_VERSION, recSlot.captured.algorithmVersion)
    }

    @Test
    fun `window epoch millis are passed to repository`() = runTest {
        val recSlot = slot<NewSleepRecommendation>()
        coEvery { repository.insertRecommendation(capture(recSlot)) } returns 1L

        useCase(anchorSleepId = 7L, window = window)

        assertEquals(window.windowStart.toEpochMilli(), recSlot.captured.windowStart)
        assertEquals(window.bestEstimate.toEpochMilli(), recSlot.captured.bestEstimate)
    }
}
