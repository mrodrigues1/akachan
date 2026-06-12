package com.babytracker.domain.usecase.feeding

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedEntry
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.usecase.bottlefeed.ObserveBottleFeedsUseCase
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
        val getBreastfeeding = mockk<GetBreastfeedingHistoryUseCase>()
        val observeBottles = mockk<ObserveBottleFeedsUseCase>()
        every { getBreastfeeding() } returns flowOf(listOf(session))
        every { observeBottles() } returns flowOf(listOf(bottle))

        val result = ObserveFeedingHistoryUseCase(getBreastfeeding, observeBottles)().first()

        assertEquals(2, result.size)
        assertEquals(Instant.ofEpochMilli(3_000), result[0].timestamp)
        assertEquals(FeedEntry.Bottle(bottle), result[1])
    }
}
