package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class EditBottleFeedUseCaseTest {

    private lateinit var repository: BottleFeedRepository
    private val now = Instant.ofEpochMilli(10_000)
    private lateinit var useCase: EditBottleFeedUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        useCase = EditBottleFeedUseCase(repository) { now }
    }

    @Test
    fun `updates details when row exists`() = runTest {
        coEvery {
            repository.updateDetails(any(), any(), any(), any(), any(), any())
        } returns true

        useCase(
            id = 3,
            timestamp = Instant.ofEpochMilli(9_000),
            volumeMl = 150,
            type = FeedType.FORMULA,
            linkedMilkBagId = null,
            notes = "edited",
        )

        coVerify {
            repository.updateDetails(3, Instant.ofEpochMilli(9_000), 150, FeedType.FORMULA, null, "edited")
        }
    }

    @Test
    fun `throws when no row updated`() = runTest {
        coEvery { repository.updateDetails(any(), any(), any(), any(), any(), any()) } returns false

        assertThrows<IllegalStateException> {
            runBlocking {
                useCase(99, Instant.ofEpochMilli(9_000), 100, FeedType.FORMULA, null, null)
            }
        }
    }

    @Test
    fun `rejects non-positive volume`() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking { useCase(3, Instant.ofEpochMilli(9_000), 0, FeedType.FORMULA, null, null) }
        }
    }
}
