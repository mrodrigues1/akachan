package com.babytracker.sharing.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReconcilePendingOpsTest {

    private val t0 = 1_000_000L

    private fun feedCreate(clientId: String, createdAtMs: Long = t0) = FeedOp(
        opId = "op-$clientId", action = FeedOpAction.CREATE, entryClientId = clientId,
        authorUid = "uid", createdAtMs = createdAtMs, timestampMs = 5_000L, volumeMl = 90, type = "FORMULA",
    )

    private fun feedSnapshot(clientId: String) = BottleFeedSnapshot(
        timestamp = 5_000L, volumeMl = 90, type = "FORMULA", clientId = clientId, author = "PARTNER",
    )

    // --- A.1 regression: op deleted BEFORE its snapshot update keeps overlaying. ---

    @Test
    fun `feed create op-delete before snapshot update retains overlay then converges`() {
        val op = feedCreate("c1")
        val stale = emptyList<BottleFeedSnapshot>()
        val fresh = listOf(feedSnapshot("c1"))

        // Round 1: op live, snapshot stale.
        val r1 = reconcilePendingOps({ feedOpReflected(it, stale) }, listOf(op), emptyList(), nowMs = t0)
        assertEquals(listOf(op), r1.effectiveOps)
        assertEquals(listOf(op), r1.nextTracked)

        // Round 2: op DELETED from listener, snapshot STILL stale -> overlay retained.
        val r2 = reconcilePendingOps({ feedOpReflected(it, stale) }, emptyList(), r1.nextTracked, nowMs = t0 + 100)
        assertEquals(listOf(op), r2.effectiveOps)

        // Round 3: snapshot now reflects the op -> overlay dropped, snapshot wins.
        val r3 = reconcilePendingOps({ feedOpReflected(it, fresh) }, emptyList(), r2.nextTracked, nowMs = t0 + 200)
        assertTrue(r3.effectiveOps.isEmpty())
        assertTrue(r3.nextTracked.isEmpty())
    }

    @Test
    fun `feed delete op is reflected once the entry disappears from the snapshot`() {
        val op = FeedOp("op-d", FeedOpAction.DELETE, "c1", "uid", t0)
        assertFalse(feedOpReflected(op, listOf(feedSnapshot("c1")))) // still present -> not reflected
        assertTrue(feedOpReflected(op, emptyList())) // gone -> reflected
    }

    @Test
    fun `rejected op is dropped once the TTL elapses`() {
        val op = feedCreate("c1", createdAtMs = t0)
        // Op vanished, snapshot never reflects it, now past TTL.
        val r = reconcilePendingOps(
            { feedOpReflected(it, emptyList()) }, emptyList(), listOf(op), nowMs = t0 + PENDING_OP_TTL_MS + 1,
        )
        assertTrue(r.effectiveOps.isEmpty())
        assertTrue(r.nextTracked.isEmpty())
    }

    @Test
    fun `already-reflected live op is included but never carried`() {
        val op = feedCreate("c1")
        val fresh = listOf(feedSnapshot("c1"))
        // Op still live AND snapshot already reflects it.
        val r = reconcilePendingOps({ feedOpReflected(it, fresh) }, listOf(op), emptyList(), nowMs = t0)
        assertEquals(listOf(op), r.effectiveOps) // live ops always included (idempotent overlay)
        assertTrue(r.nextTracked.isEmpty()) // but reflected -> not carried, so it won't reappear after deletion
    }

    // --- Sleep predicates ---

    private fun record(clientId: String, endTime: Long?, sleepType: String = "NAP", start: Long = 1_000L, notes: String? = null) =
        SleepSnapshot(0, start, endTime, sleepType, notes, clientId = clientId, startedBy = "PARTNER")

    @Test
    fun `sleep start is reflected once the snapshot has the session`() {
        val op = SleepOp("op-s", SleepOpAction.START, "c1", "uid", t0, startTimeMs = 1_000L, sleepType = "NAP")
        assertFalse(sleepActiveReflected(op, emptyList()))
        assertTrue(sleepActiveReflected(op, listOf(record("c1", endTime = null))))
    }

    @Test
    fun `sleep stop is reflected once the snapshot session is ended or gone`() {
        val op = SleepOp("op-x", SleepOpAction.STOP, "c1", "uid", t0, endTimeMs = 9_000L)
        assertFalse(sleepActiveReflected(op, listOf(record("c1", endTime = null)))) // still active -> not reflected
        assertTrue(sleepActiveReflected(op, listOf(record("c1", endTime = 9_000L)))) // ended -> reflected
        assertTrue(sleepActiveReflected(op, emptyList())) // gone -> reflected
    }

    @Test
    fun `sleep edit is reflected once the snapshot record matches the edited fields`() {
        val op = SleepOp("op-e", SleepOpAction.UPDATE, "c1", "uid", t0, startTimeMs = 2_000L, endTimeMs = 8_000L, sleepType = "NIGHT_SLEEP", notes = "x")
        assertFalse(sleepEditReflected(op, listOf(record("c1", endTime = 9_000L)))) // endTime mismatch
        assertTrue(sleepEditReflected(op, listOf(record("c1", endTime = 8_000L, sleepType = "NIGHT_SLEEP", start = 2_000L, notes = "x"))))
        // START/STOP are not history edits -> treated as reflected so they never pin the history overlay.
        assertTrue(sleepEditReflected(SleepOp("op-s", SleepOpAction.START, "c1", "uid", t0), emptyList()))
    }
}
