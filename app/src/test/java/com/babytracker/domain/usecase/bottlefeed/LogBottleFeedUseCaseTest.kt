package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.usecase.inventory.MarkBagUsedUseCase
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class LogBottleFeedUseCaseTest {

    private lateinit var repository: BottleFeedRepository
    private lateinit var markBagUsed: MarkBagUsedUseCase
    private lateinit var syncToFirestore: SyncToFirestoreUseCase
    private val now = Instant.ofEpochMilli(10_000)
    private lateinit var useCase: LogBottleFeedUseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        markBagUsed = mockk(relaxed = true)
        syncToFirestore = mockk(relaxed = true)
        useCase = LogBottleFeedUseCase(repository, markBagUsed, SyncedWrite(syncToFirestore)) { now }
    }

    @Test
    fun `logs formula feed without touching inventory`() = runTest {
        val captured = slot<BottleFeed>()
        coEvery { repository.insert(capture(captured)) } returns 5

        val id = useCase(
            timestamp = Instant.ofEpochMilli(9_000),
            volumeMl = 120,
            type = FeedType.FORMULA,
            linkedBag = null,
            notes = "evening",
        )

        assertEquals(5, id)
        assertEquals(FeedType.FORMULA, captured.captured.type)
        assertEquals(null, captured.captured.linkedMilkBagId)
        coVerify(exactly = 0) { markBagUsed(any()) }
        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS) }
    }

    @Test
    fun `logs breast-milk feed and consumes the linked bag`() = runTest {
        val bag = MilkBag(
            id = 7,
            collectionDate = Instant.ofEpochMilli(1_000),
            volumeMl = 120,
            createdAt = Instant.ofEpochMilli(1_000),
        )
        val captured = slot<BottleFeed>()
        coEvery { repository.insert(capture(captured)) } returns 9

        val id = useCase(
            timestamp = Instant.ofEpochMilli(9_000),
            volumeMl = 100,
            type = FeedType.BREAST_MILK,
            linkedBag = bag,
            notes = null,
        )

        assertEquals(9, id)
        assertEquals(7, captured.captured.linkedMilkBagId)
        coVerify(exactly = 1) { markBagUsed(bag) }
    }

    @Test
    fun `feed insert is not rolled back when bag consumption fails`() = runTest {
        // Documents the deliberate one-way, non-transactional design (mirrors the milk
        // stash): the feed is persisted before the bag is consumed, so a failure in
        // markBagUsed leaves the feed saved and the bag still available. See plan 03.
        val bag = MilkBag(
            id = 7,
            collectionDate = Instant.ofEpochMilli(1_000),
            volumeMl = 120,
            createdAt = Instant.ofEpochMilli(1_000),
        )
        coEvery { repository.insert(any()) } returns 9
        coEvery { markBagUsed(bag) } throws IllegalStateException("inventory write failed")

        assertThrows<IllegalStateException> {
            runBlocking {
                useCase(Instant.ofEpochMilli(9_000), 100, FeedType.BREAST_MILK, bag, null)
            }
        }

        coVerify(exactly = 1) { repository.insert(any()) }
    }

    @Test
    fun `rejects non-positive volume`() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase(Instant.ofEpochMilli(9_000), 0, FeedType.FORMULA, null, null)
            }
        }
    }

    @Test
    fun `rejects future timestamp`() = runTest {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                useCase(Instant.ofEpochMilli(20_000), 100, FeedType.FORMULA, null, null)
            }
        }
    }
}
