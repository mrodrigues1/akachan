package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class DeleteBottleFeedUseCaseTest {

    @Test
    fun `delegates delete to repository`() = runTest {
        val repository = mockk<BottleFeedRepository>(relaxed = true)
        val feed = BottleFeed(
            id = 4, timestamp = Instant.ofEpochMilli(1_000), volumeMl = 90,
            type = FeedType.FORMULA, createdAt = Instant.ofEpochMilli(1_000),
        )

        DeleteBottleFeedUseCase(repository)(feed)

        coVerify { repository.delete(feed) }
    }
}
