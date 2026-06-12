package com.babytracker.sharing.usecase

import android.util.Log
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class ApplyFeedOpUseCaseTest {
    private val repository: BottleFeedRepository = mockk()
    private val fixedNow = Instant.parse("2026-06-01T10:00:00Z")
    private lateinit var applyFeedOp: ApplyFeedOpUseCase

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        applyFeedOp = ApplyFeedOpUseCase(repository) { fixedNow }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `create inserts partner feed with bag consume`() = runTest {
        val feed = slot<BottleFeed>()
        coEvery { repository.getByClientId("client-1") } returns null
        coEvery { repository.insertWithBagConsume(capture(feed), 7L, fixedNow) } returns 1L

        val result = applyFeedOp(createOp(consumedBagId = 7L))

        assertTrue(result)
        assertEquals(FeedAuthor.PARTNER, feed.captured.author)
        assertEquals("client-1", feed.captured.clientId)
        coVerify { repository.insertWithBagConsume(any(), 7L, fixedNow) }
    }

    @Test
    fun `create forces partner author regardless of author uid`() = runTest {
        val feed = slot<BottleFeed>()
        coEvery { repository.getByClientId("client-1") } returns null
        coEvery { repository.insertWithBagConsume(capture(feed), null, fixedNow) } returns 1L

        applyFeedOp(createOp(authorUid = "owner-uid"))

        assertEquals(FeedAuthor.PARTNER, feed.captured.author)
    }

    @Test
    fun `create colliding with owner entry is dropped`() = runTest {
        coEvery { repository.getByClientId("client-1") } returns feed(author = FeedAuthor.OWNER)

        val result = applyFeedOp(createOp())

        assertFalse(result)
        coVerify(exactly = 0) { repository.insertWithBagConsume(any(), any(), any()) }
        coVerify(exactly = 0) { repository.updateDetails(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `idempotent create reapply updates partner entry and keeps existing link`() = runTest {
        coEvery { repository.getByClientId("client-1") } returns feed(author = FeedAuthor.PARTNER, linkedMilkBagId = 9L)
        coEvery { repository.updateDetails(4L, any(), 120, FeedType.FORMULA, 9L, "note") } returns true

        val result = applyFeedOp(createOp(consumedBagId = 7L, notes = "note"))

        assertTrue(result)
        coVerify { repository.updateDetails(4L, any(), 120, FeedType.FORMULA, 9L, "note") }
        coVerify(exactly = 0) { repository.insertWithBagConsume(any(), any(), any()) }
    }

    @Test
    fun `update on partner entry applies and preserves linked bag`() = runTest {
        coEvery { repository.getByClientId("client-1") } returns feed(author = FeedAuthor.PARTNER, linkedMilkBagId = 8L)
        coEvery { repository.updateDetails(4L, any(), 120, FeedType.FORMULA, 8L, "note") } returns true

        val result = applyFeedOp(updateOp(consumedBagId = 99L, notes = "note"))

        assertTrue(result)
        coVerify { repository.updateDetails(4L, any(), 120, FeedType.FORMULA, 8L, "note") }
    }

    @Test
    fun `forged update against owner entry is dropped`() = runTest {
        coEvery { repository.getByClientId("client-1") } returns feed(author = FeedAuthor.OWNER)

        val result = applyFeedOp(updateOp())

        assertFalse(result)
        coVerify(exactly = 0) { repository.updateDetails(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `update after primary deleted entry is dropped`() = runTest {
        coEvery { repository.getByClientId("client-1") } returns null

        val result = applyFeedOp(updateOp())

        assertFalse(result)
        coVerify(exactly = 0) { repository.updateDetails(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `delete on partner entry restores inventory`() = runTest {
        val existing = feed(author = FeedAuthor.PARTNER)
        coEvery { repository.getByClientId("client-1") } returns existing
        coEvery { repository.deleteWithInventoryRestore(existing) } returns true

        val result = applyFeedOp(deleteOp())

        assertTrue(result)
        coVerify { repository.deleteWithInventoryRestore(existing) }
    }

    @Test
    fun `forged delete against owner entry is dropped`() = runTest {
        coEvery { repository.getByClientId("client-1") } returns feed(author = FeedAuthor.OWNER)

        val result = applyFeedOp(deleteOp())

        assertFalse(result)
        coVerify(exactly = 0) { repository.deleteWithInventoryRestore(any()) }
    }

    @Test
    fun `invalid payloads are dropped`() = runTest {
        coEvery { repository.getByClientId(any()) } returns null
        val invalidOps = listOf(
            createOp(volumeMl = 0),
            createOp(volumeMl = null),
            createOp(timestampMs = null),
            createOp(type = null),
            createOp(type = "WATER"),
            createOp(timestampMs = fixedNow.plusSeconds(1).toEpochMilli()),
        )

        invalidOps.forEach { op ->
            assertFalse(applyFeedOp(op), op.toString())
        }
        coVerify(exactly = 0) { repository.insertWithBagConsume(any(), any(), any()) }
    }

    private fun createOp(
        authorUid: String = "partner-uid",
        timestampMs: Long? = Instant.parse("2026-06-01T09:00:00Z").toEpochMilli(),
        volumeMl: Int? = 120,
        type: String? = "FORMULA",
        notes: String? = null,
        consumedBagId: Long? = null,
    ) = FeedOp(
        opId = "op-create",
        action = FeedOpAction.CREATE,
        entryClientId = "client-1",
        authorUid = authorUid,
        createdAtMs = Instant.parse("2026-06-01T09:00:00Z").toEpochMilli(),
        timestampMs = timestampMs,
        volumeMl = volumeMl,
        type = type,
        notes = notes,
        consumedBagId = consumedBagId,
    )

    private fun updateOp(
        notes: String? = null,
        consumedBagId: Long? = null,
    ) = createOp(notes = notes, consumedBagId = consumedBagId).copy(
        opId = "op-update",
        action = FeedOpAction.UPDATE,
    )

    private fun deleteOp() = FeedOp(
        opId = "op-delete",
        action = FeedOpAction.DELETE,
        entryClientId = "client-1",
        authorUid = "partner-uid",
        createdAtMs = Instant.parse("2026-06-01T09:00:00Z").toEpochMilli(),
    )

    private fun feed(author: FeedAuthor, linkedMilkBagId: Long? = null) = BottleFeed(
        id = 4L,
        clientId = "client-1",
        timestamp = Instant.parse("2026-06-01T08:00:00Z"),
        volumeMl = 90,
        type = FeedType.BREAST_MILK,
        linkedMilkBagId = linkedMilkBagId,
        createdAt = Instant.parse("2026-06-01T08:00:00Z"),
        author = author,
    )
}
