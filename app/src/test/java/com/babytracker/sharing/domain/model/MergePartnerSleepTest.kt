package com.babytracker.sharing.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MergePartnerSleepTest {

    private val now = 100_000L

    private fun activeSnap(clientId: String = "snap-cid") = SleepSnapshot(
        id = 1, startTime = 90_000L, endTime = null, sleepType = "NAP", notes = null,
        clientId = clientId, startedBy = "PARTNER",
    )

    private fun op(
        action: SleepOpAction,
        clientId: String,
        createdAtMs: Long = now,
        startTimeMs: Long? = null,
        endTimeMs: Long? = null,
        sleepType: String? = null,
        notes: String? = null,
    ) = SleepOp("op-$clientId-$action", action, clientId, "uid", createdAtMs, startTimeMs, endTimeMs, sleepType, notes)

    // --- mergeActiveSleep ---

    @Test
    fun `authoritative active session wins over a pending start (convergence)`() {
        val merged = mergeActiveSleep(
            activeSnap("snap-cid"),
            listOf(op(SleepOpAction.START, "other-cid", startTimeMs = 95_000L, sleepType = "NAP")),
            now,
        )
        assertEquals("snap-cid", merged.session?.clientId)
        assertFalse(merged.stopping)
    }

    @Test
    fun `pending stop for the active session surfaces stopping`() {
        val merged = mergeActiveSleep(
            activeSnap("snap-cid"),
            listOf(op(SleepOpAction.STOP, "snap-cid", endTimeMs = now)),
            now,
        )
        assertEquals("snap-cid", merged.session?.clientId)
        assertTrue(merged.stopping)
    }

    @Test
    fun `fresh pending start with no active snapshot shows a synthetic session`() {
        val merged = mergeActiveSleep(
            activeSnapshot = null,
            pendingOps = listOf(op(SleepOpAction.START, "new-cid", startTimeMs = 99_000L, sleepType = "NIGHT_SLEEP")),
            nowMs = now,
        )
        assertEquals("new-cid", merged.session?.clientId)
        assertEquals(99_000L, merged.session?.startTime)
        assertNull(merged.session?.endTime)
        assertEquals("NIGHT_SLEEP", merged.session?.sleepType)
        assertEquals("PARTNER", merged.session?.startedBy)
    }

    @Test
    fun `start then stop with no active snapshot clears the session`() {
        val merged = mergeActiveSleep(
            activeSnapshot = null,
            pendingOps = listOf(
                op(SleepOpAction.START, "c", createdAtMs = 99_000L, startTimeMs = 99_000L, sleepType = "NAP"),
                op(SleepOpAction.STOP, "c", createdAtMs = 99_500L, endTimeMs = 99_500L),
            ),
            nowMs = now,
        )
        assertNull(merged.session)
    }

    @Test
    fun `stale pending start past the ttl is ignored`() {
        val merged = mergeActiveSleep(
            activeSnapshot = null,
            pendingOps = listOf(op(SleepOpAction.START, "old", createdAtMs = 0L, startTimeMs = 0L, sleepType = "NAP")),
            nowMs = now,
        )
        assertNull(merged.session)
    }

    // --- mergeSleepEdit ---

    @Test
    fun `pending update overlays edited fields on the matching session`() {
        val session = activeSnap("cid").copy(endTime = 95_000L, sleepType = "NAP")
        val edited = mergeSleepEdit(
            session,
            listOf(op(SleepOpAction.UPDATE, "cid", startTimeMs = 91_000L, endTimeMs = 94_000L, sleepType = "NIGHT_SLEEP", notes = "fixed")),
            now,
        )
        assertEquals(91_000L, edited.startTime)
        assertEquals(94_000L, edited.endTime)
        assertEquals("NIGHT_SLEEP", edited.sleepType)
        assertEquals("fixed", edited.notes)
    }

    @Test
    fun `no pending update reverts to the snapshot`() {
        val session = activeSnap("cid")
        assertEquals(session, mergeSleepEdit(session, emptyList(), now))
    }

    @Test
    fun `stale pending update reverts to the snapshot`() {
        val session = activeSnap("cid")
        val edited = mergeSleepEdit(
            session,
            listOf(op(SleepOpAction.UPDATE, "cid", createdAtMs = 0L, startTimeMs = 1L, sleepType = "NIGHT_SLEEP")),
            now,
        )
        assertEquals(session, edited)
    }

    @Test
    fun `pending update for another session is ignored`() {
        val session = activeSnap("cid")
        val edited = mergeSleepEdit(
            session,
            listOf(op(SleepOpAction.UPDATE, "other", startTimeMs = 1L, sleepType = "NIGHT_SLEEP")),
            now,
        )
        assertEquals(session, edited)
    }

    // --- mergeSleepHistory ---

    @Test
    fun `history is sorted newest first and overlays pending edits`() {
        val older = activeSnap("a").copy(id = 1, startTime = 10_000L, endTime = 11_000L)
        val newer = activeSnap("b").copy(id = 2, startTime = 50_000L, endTime = 51_000L, sleepType = "NAP")

        val merged = mergeSleepHistory(
            snapshotRecords = listOf(older, newer),
            pendingOps = listOf(op(SleepOpAction.UPDATE, "b", startTimeMs = 52_000L, endTimeMs = 53_000L, sleepType = "NIGHT_SLEEP")),
            nowMs = now,
        )

        assertEquals(listOf("b", "a"), merged.entries.map { it.clientId })
        // The pending edit is overlaid on the matching row.
        assertEquals("NIGHT_SLEEP", merged.entries.first().sleepType)
        assertEquals(52_000L, merged.entries.first().startTime)
    }

    @Test
    fun `history exposes pending op ids for consumed-op detection`() {
        val merged = mergeSleepHistory(
            snapshotRecords = listOf(activeSnap("a").copy(endTime = 1L)),
            pendingOps = listOf(op(SleepOpAction.UPDATE, "a", createdAtMs = now, startTimeMs = 1L, sleepType = "NAP")),
            nowMs = now,
        )
        assertEquals(setOf("op-a-UPDATE"), merged.pendingOpIds)
    }
}
