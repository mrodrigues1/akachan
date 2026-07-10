package com.babytracker.domain.usecase.feeding

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedEntry
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ObserveFeedingHistoryUseCaseTest {

    @Test
    fun `interleaves bottle and breastfeeding sorted newest first`() = runTest {
        val bottle = BottleFeed(
            id = 1L,
            clientId = "client-1",
            timestamp = Instant.ofEpochMilli(2_000),
            volumeMl = 100,
            type = FeedType.FORMULA,
            createdAt = Instant.ofEpochMilli(2_000),
        )
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.ofEpochMilli(3_000),
            endTime = Instant.ofEpochMilli(3_500),
            startingSide = BreastSide.LEFT,
        )
        val getBreastfeeding = mockk<BreastfeedingRepository>()
        val observeBottles = mockk<BottleFeedRepository>()
        every { getBreastfeeding.getRecentSessionsFlow(any()) } returns flowOf(listOf(session))
        every { observeBottles.getRecentFlow(any()) } returns flowOf(listOf(bottle))

        val window = ObserveFeedingHistoryUseCase(getBreastfeeding, observeBottles)(limit = 50).first()

        assertEquals(2, window.entries.size)
        assertEquals(Instant.ofEpochMilli(3_000), window.entries[0].timestamp)
        assertEquals(FeedEntry.Bottle(bottle), window.entries[1])
        assertFalse(window.hasMore)
    }

    @Test
    fun `queries one row past the limit per source and flags hasMore`() = runTest {
        // limit 2 → each source queried with 3; 3 sessions + 0 bottles merge to 3 > 2 → hasMore.
        val sessions = List(3) { i ->
            BreastfeedingSession(
                id = i + 1L,
                startTime = Instant.ofEpochMilli(3_000L - i),
                endTime = Instant.ofEpochMilli(3_500L - i),
                startingSide = BreastSide.LEFT,
            )
        }
        val getBreastfeeding = mockk<BreastfeedingRepository>()
        val observeBottles = mockk<BottleFeedRepository>()
        every { getBreastfeeding.getRecentSessionsFlow(3) } returns flowOf(sessions)
        every { observeBottles.getRecentFlow(3) } returns flowOf(emptyList())

        val window = ObserveFeedingHistoryUseCase(getBreastfeeding, observeBottles)(limit = 2).first()

        assertEquals(2, window.entries.size)
        assertTrue(window.hasMore)
    }
}
