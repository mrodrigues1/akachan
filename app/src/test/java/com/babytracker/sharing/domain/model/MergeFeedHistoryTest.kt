package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MergeFeedHistoryTest {

    @Test
    fun `pending create appears in merged list`() {
        val merged = mergeFeedHistory(
            snapshotFeeds = emptyList(),
            pendingOps = listOf(createOp(entryClientId = "new-entry")),
        )

        assertEquals(listOf("new-entry"), merged.map { it.clientId })
        assertEquals(FeedAuthor.PARTNER.name, merged.single().author)
    }

    @Test
    fun `pending update overrides snapshot entry fields`() {
        val merged = mergeFeedHistory(
            snapshotFeeds = listOf(feed(clientId = "entry-1", volumeMl = 80, notes = "old")),
            pendingOps = listOf(createOp(action = FeedOpAction.UPDATE, entryClientId = "entry-1", volumeMl = 120, notes = "new")),
        )

        val feed = merged.single()
        assertEquals(120, feed.volumeMl)
        assertEquals("new", feed.notes)
        assertEquals(FeedAuthor.PARTNER.name, feed.author)
    }

    @Test
    fun `pending delete hides snapshot entry`() {
        val merged = mergeFeedHistory(
            snapshotFeeds = listOf(feed(clientId = "entry-1")),
            pendingOps = listOf(deleteOp(entryClientId = "entry-1")),
        )

        assertEquals(emptyList<BottleFeedSnapshot>(), merged)
    }

    @Test
    fun `ops on same entry apply in created order`() {
        val merged = mergeFeedHistory(
            snapshotFeeds = listOf(feed(clientId = "entry-1")),
            pendingOps = listOf(
                deleteOp(entryClientId = "entry-1", createdAtMs = 200L),
                createOp(action = FeedOpAction.UPDATE, entryClientId = "entry-1", createdAtMs = 100L),
            ),
        )

        assertFalse(merged.any { it.clientId == "entry-1" })
    }

    @Test
    fun `legacy snapshot rows survive merging untouched`() {
        val legacyOne = feed(clientId = "", timestamp = 1_000L, notes = "legacy one")
        val legacyTwo = feed(clientId = "", timestamp = 2_000L, notes = "legacy two")

        val merged = mergeFeedHistory(
            snapshotFeeds = listOf(legacyOne, legacyTwo),
            pendingOps = listOf(createOp(entryClientId = "new-entry", timestampMs = 3_000L)),
        )

        assertEquals(listOf("new-entry", "", ""), merged.map { it.clientId })
        assertEquals(listOf("new", "legacy two", "legacy one"), merged.map { it.notes })
    }

    @Test
    fun `result is sorted by timestamp descending`() {
        val merged = mergeFeedHistory(
            snapshotFeeds = listOf(feed(clientId = "old", timestamp = 1_000L)),
            pendingOps = listOf(createOp(entryClientId = "new", timestampMs = 3_000L)),
        )

        assertEquals(listOf("new", "old"), merged.map { it.clientId })
    }

    @Test
    fun `merged result includes pending op count`() {
        val merged = mergeFeedHistoryWithPendingCount(
            snapshotFeeds = emptyList(),
            pendingOps = listOf(
                createOp(entryClientId = "new-entry"),
                deleteOp(entryClientId = "old-entry"),
            ),
        )

        assertEquals(2, merged.pendingOpCount)
        assertEquals(setOf("op-100", "op-delete-100"), merged.pendingOpIds)
        assertEquals(listOf("new-entry"), merged.entries.map { it.clientId })
    }

    private fun feed(
        clientId: String,
        timestamp: Long = 1_000L,
        volumeMl: Int = 90,
        notes: String? = null,
    ) = BottleFeedSnapshot(
        timestamp = timestamp,
        volumeMl = volumeMl,
        type = FeedType.FORMULA.name,
        clientId = clientId,
        author = FeedAuthor.OWNER.name,
        notes = notes,
    )

    private fun createOp(
        action: FeedOpAction = FeedOpAction.CREATE,
        entryClientId: String = "entry-1",
        createdAtMs: Long = 100L,
        timestampMs: Long = 2_000L,
        volumeMl: Int = 100,
        notes: String? = "new",
    ) = FeedOp(
        opId = "op-$createdAtMs",
        action = action,
        entryClientId = entryClientId,
        authorUid = "partner-uid",
        createdAtMs = createdAtMs,
        timestampMs = timestampMs,
        volumeMl = volumeMl,
        type = FeedType.BREAST_MILK.name,
        notes = notes,
    )

    private fun deleteOp(
        entryClientId: String,
        createdAtMs: Long = 100L,
    ) = FeedOp(
        opId = "op-delete-$createdAtMs",
        action = FeedOpAction.DELETE,
        entryClientId = entryClientId,
        authorUid = "partner-uid",
        createdAtMs = createdAtMs,
    )
}
