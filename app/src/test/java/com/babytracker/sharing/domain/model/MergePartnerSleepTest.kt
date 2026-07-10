package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MergePartnerSleepTest {

    private val now = 100_000L

    private fun activeSnap(clientId: String = "snap-cid") = SleepSnapshot(
        id = 1, startTime = 90_000L, endTime = null, sleepType = SleepType.NAP, notes = null,
        clientId = clientId, startedBy = SleepAuthor.PARTNER,
    )

    private fun op(
        action: SleepOpAction,
        clientId: String,
        createdAtMs: Long = now,
        startTimeMs: Long? = null,
        endTimeMs: Long? = null,
        sleepType: SleepType? = null,
        notes: String? = null,
    ) = SleepOp("op-$clientId-$action", action, clientId, "uid", createdAtMs, startTimeMs, endTimeMs, sleepType, notes)

    // --- mergeActiveSleep ---

    @Test
    fun `authoritative active session wins over a pending start (convergence)`() {
        val merged = mergeActiveSleep(
            activeSnap("snap-cid"),
            listOf(op(SleepOpAction.START, "other-cid", startTimeMs = 95_000L, sleepType = SleepType.NAP)),
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
            pendingOps = listOf(op(SleepOpAction.START, "new-cid", startTimeMs = 99_000L, sleepType = SleepType.NIGHT_SLEEP)),
            nowMs = now,
        )
        assertEquals("new-cid", merged.session?.clientId)
        assertEquals(99_000L, merged.session?.startTime)
        assertNull(merged.session?.endTime)
        assertEquals(SleepType.NIGHT_SLEEP, merged.session?.sleepType)
        assertEquals(SleepAuthor.PARTNER, merged.session?.startedBy)
    }

    @Test
    fun `start then stop with no active snapshot clears the session`() {
        val merged = mergeActiveSleep(
            activeSnapshot = null,
            pendingOps = listOf(
                op(SleepOpAction.START, "c", createdAtMs = 99_000L, startTimeMs = 99_000L, sleepType = SleepType.NAP),
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
            pendingOps = listOf(op(SleepOpAction.START, "old", createdAtMs = 0L, startTimeMs = 0L, sleepType = SleepType.NAP)),
            nowMs = now,
        )
        assertNull(merged.session)
    }

    // --- mergeSleepHistory: edits (UPDATE) ---

    @Test
    fun `update overlays edited fields on the matching snapshot row`() {
        val session = activeSnap("cid").copy(endTime = 95_000L, sleepType = SleepType.NAP)
        val merged = mergeSleepHistory(
            snapshotRecords = listOf(session),
            pendingOps = listOf(op(SleepOpAction.UPDATE, "cid", startTimeMs = 91_000L, endTimeMs = 94_000L, sleepType = SleepType.NIGHT_SLEEP, notes = "fixed")),
            nowMs = now,
        )
        val edited = merged.entries.single()
        assertEquals(91_000L, edited.startTime)
        assertEquals(94_000L, edited.endTime)
        assertEquals(SleepType.NIGHT_SLEEP, edited.sleepType)
        assertEquals("fixed", edited.notes)
    }

    @Test
    fun `update for another session is ignored`() {
        val session = activeSnap("cid")
        val merged = mergeSleepHistory(
            snapshotRecords = listOf(session),
            pendingOps = listOf(op(SleepOpAction.UPDATE, "other", startTimeMs = 1L, sleepType = SleepType.NIGHT_SLEEP)),
            nowMs = now,
        )
        assertEquals(session, merged.entries.single())
    }

    @Test
    fun `stale update past the ttl is ignored`() {
        val session = activeSnap("cid")
        val merged = mergeSleepHistory(
            snapshotRecords = listOf(session),
            pendingOps = listOf(op(SleepOpAction.UPDATE, "cid", createdAtMs = 0L, startTimeMs = 1L, sleepType = SleepType.NIGHT_SLEEP)),
            nowMs = now,
        )
        assertEquals(session, merged.entries.single())
    }

    @Test
    fun `an update before a stop does not reopen the completed session (op order matches primary)`() {
        val session = activeSnap("cid") // active: startTime 90_000, endTime null
        val merged = mergeSleepHistory(
            snapshotRecords = listOf(session),
            pendingOps = listOf(
                op(SleepOpAction.UPDATE, "cid", createdAtMs = 95_000L, startTimeMs = 90_000L, endTimeMs = null, sleepType = SleepType.NAP),
                op(SleepOpAction.STOP, "cid", createdAtMs = 96_000L, endTimeMs = 96_000L),
            ),
            nowMs = now,
        )
        assertEquals(96_000L, merged.entries.single().endTime)
    }

    @Test
    fun `a stop before a later update reflects the edit (last-write-wins)`() {
        val session = activeSnap("cid")
        val merged = mergeSleepHistory(
            snapshotRecords = listOf(session),
            pendingOps = listOf(
                op(SleepOpAction.STOP, "cid", createdAtMs = 95_000L, endTimeMs = 95_000L),
                op(SleepOpAction.UPDATE, "cid", createdAtMs = 96_000L, startTimeMs = 90_000L, endTimeMs = 99_000L, sleepType = SleepType.NAP),
            ),
            nowMs = now,
        )
        assertEquals(99_000L, merged.entries.single().endTime)
    }

    // --- mergeSleepHistory ---

    @Test
    fun `history is sorted newest first and overlays pending edits`() {
        val older = activeSnap("a").copy(id = 1, startTime = 10_000L, endTime = 11_000L)
        val newer = activeSnap("b").copy(id = 2, startTime = 50_000L, endTime = 51_000L, sleepType = SleepType.NAP)

        val merged = mergeSleepHistory(
            snapshotRecords = listOf(older, newer),
            pendingOps = listOf(op(SleepOpAction.UPDATE, "b", startTimeMs = 52_000L, endTimeMs = 53_000L, sleepType = SleepType.NIGHT_SLEEP)),
            nowMs = now,
        )

        assertEquals(listOf("b", "a"), merged.entries.map { it.clientId })
        // The pending edit is overlaid on the matching row.
        assertEquals(SleepType.NIGHT_SLEEP, merged.entries.first().sleepType)
        assertEquals(52_000L, merged.entries.first().startTime)
    }

    @Test
    fun `history exposes pending op ids for consumed-op detection`() {
        val merged = mergeSleepHistory(
            snapshotRecords = listOf(activeSnap("a").copy(endTime = 1L)),
            pendingOps = listOf(op(SleepOpAction.UPDATE, "a", createdAtMs = now, startTimeMs = 1L, sleepType = SleepType.NAP)),
            nowMs = now,
        )
        assertEquals(setOf("op-a-UPDATE"), merged.pendingOpIds)
    }

    @Test
    fun `history synthesizes a session from a pending start with no snapshot record`() {
        val merged = mergeSleepHistory(
            snapshotRecords = emptyList(),
            pendingOps = listOf(op(SleepOpAction.START, "new", startTimeMs = 99_000L, sleepType = SleepType.NIGHT_SLEEP)),
            nowMs = now,
        )
        val entry = merged.entries.single()
        assertEquals("new", entry.clientId)
        assertEquals(99_000L, entry.startTime)
        assertNull(entry.endTime)
        assertEquals(SleepType.NIGHT_SLEEP, entry.sleepType)
        assertEquals(SleepAuthor.PARTNER, entry.startedBy)
    }

    @Test
    fun `history completes a snapshot-active session from a pending stop`() {
        val merged = mergeSleepHistory(
            snapshotRecords = listOf(activeSnap("a")), // startTime 90_000, endTime null
            pendingOps = listOf(op(SleepOpAction.STOP, "a", endTimeMs = now)),
            nowMs = now,
        )
        assertEquals(now, merged.entries.single().endTime)
    }

    @Test
    fun `history synthesizes start-then-stop as a completed session (primary never processed it)`() {
        val merged = mergeSleepHistory(
            snapshotRecords = emptyList(),
            pendingOps = listOf(
                op(SleepOpAction.START, "c", createdAtMs = 98_000L, startTimeMs = 98_000L, sleepType = SleepType.NAP),
                op(SleepOpAction.STOP, "c", createdAtMs = 99_000L, endTimeMs = 99_000L),
            ),
            nowMs = now,
        )
        val entry = merged.entries.single()
        assertEquals("c", entry.clientId)
        assertEquals(98_000L, entry.startTime)
        assertEquals(99_000L, entry.endTime)
    }

    @Test
    fun `history stop clamps a before-start end time to the start (no negative duration)`() {
        val merged = mergeSleepHistory(
            snapshotRecords = listOf(activeSnap("a")), // startTime 90_000
            pendingOps = listOf(op(SleepOpAction.STOP, "a", endTimeMs = 80_000L)),
            nowMs = now,
        )
        assertEquals(90_000L, merged.entries.single().endTime)
    }

    @Test
    fun `history stop on a missing session is ignored`() {
        val merged = mergeSleepHistory(
            snapshotRecords = emptyList(),
            pendingOps = listOf(op(SleepOpAction.STOP, "ghost", endTimeMs = now)),
            nowMs = now,
        )
        assertTrue(merged.entries.isEmpty())
    }
}
