package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class ObserveBottleFeedsUseCaseTest {

    @Test
    fun `emits feeds from repository`() = runTest {
        val repository = mockk<BottleFeedRepository>()
        val feed = BottleFeed(
            id = 1, clientId = "client-1", timestamp = Instant.ofEpochMilli(1_000), volumeMl = 90,
            type = FeedType.BREAST_MILK, createdAt = Instant.ofEpochMilli(1_000),
        )
        every { repository.getAll() } returns flowOf(listOf(feed))

        val result = ObserveBottleFeedsUseCase(repository)().first()

        assertEquals(listOf(feed), result)
    }
}
