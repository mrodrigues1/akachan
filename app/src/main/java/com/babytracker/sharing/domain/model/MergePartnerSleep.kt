package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepType

// A never-applied op (primary offline) stops pinning the UI after this window; op existence within
// the window is otherwise the pending boundary (Plan 04 deletes the op once applied or dropped).
const val PENDING_SLEEP_OP_TTL_MS = 60_000L

data class MergedActiveSleep(
    val session: SleepSnapshot?,
    val stopping: Boolean = false,
)

/**
 * Resolves the active sleep session the partner should see, overlaying its own fresh pending ops on
 * the authoritative snapshot. Truth table:
 *  - snapshot active present -> show it (a pending START converges to it; a pending STOP for its
 *    clientId surfaces a transient "stopping…").
 *  - no snapshot active, fresh START with no later STOP -> synthetic optimistic active session.
 *  - no snapshot active, START already stopped (or only STOP pending) -> no active session.
 */
fun mergeActiveSleep(
    activeSnapshot: SleepSnapshot?,
    pendingOps: List<SleepOp>,
    nowMs: Long,
    ttlMs: Long = PENDING_SLEEP_OP_TTL_MS,
): MergedActiveSleep {
    val fresh = pendingOps.filter { nowMs - it.createdAtMs <= ttlMs }
    if (activeSnapshot != null) {
        val stopping = fresh.any {
            it.action == SleepOpAction.STOP && it.entryClientId == activeSnapshot.clientId
        }
        return MergedActiveSleep(activeSnapshot, stopping)
    }
    val latestStart = fresh.filter { it.action == SleepOpAction.START }.maxByOrNull { it.createdAtMs }
        ?: return MergedActiveSleep(null)
    val stoppedAfter = fresh.any {
        it.action == SleepOpAction.STOP &&
            it.entryClientId == latestStart.entryClientId &&
            it.createdAtMs >= latestStart.createdAtMs
    }
    return if (stoppedAfter) MergedActiveSleep(null) else MergedActiveSleep(latestStart.toOptimisticActive())
}

private fun SleepOp.toOptimisticActive(): SleepSnapshot = SleepSnapshot(
    id = 0,
    startTime = startTimeMs ?: createdAtMs,
    endTime = null,
    sleepType = sleepType ?: SleepType.NAP.name,
    notes = notes,
    clientId = entryClientId,
    startedBy = SleepAuthor.PARTNER.name,
)

/**
 * Overlays the partner's own fresh pending UPDATE on a single session snapshot for optimistic
 * display. Reverts automatically once the op is gone (applied / dropped / deleted) or past the TTL —
 * a dropped edit (e.g. it targeted an OWNER session) is deleted by Plan 04, so the snapshot wins.
 */
data class MergedSleepHistory(
    val entries: List<SleepSnapshot>,
    val pendingOpIds: Set<String>,
)

/**
 * The partner's sleep history: snapshot records overlaid with the partner's own fresh pending edits
 * (optimistic). Op existence (within the TTL) is the pending boundary — once Plan 04 applies/drops
 * and deletes an edit op, the snapshot wins again. Newest first.
 */
fun mergeSleepHistory(
    snapshotRecords: List<SleepSnapshot>,
    pendingOps: List<SleepOp>,
    nowMs: Long,
    ttlMs: Long = PENDING_SLEEP_OP_TTL_MS,
): MergedSleepHistory = MergedSleepHistory(
    entries = snapshotRecords
        .map { mergeSleepEdit(it, pendingOps, nowMs, ttlMs) }
        .sortedByDescending { it.startTime },
    pendingOpIds = pendingOps.mapTo(mutableSetOf()) { it.opId },
)

fun mergeSleepEdit(
    session: SleepSnapshot,
    pendingOps: List<SleepOp>,
    nowMs: Long,
    ttlMs: Long = PENDING_SLEEP_OP_TTL_MS,
): SleepSnapshot {
    val edit = pendingOps
        .filter {
            it.action == SleepOpAction.UPDATE &&
                it.entryClientId == session.clientId &&
                nowMs - it.createdAtMs <= ttlMs
        }
        .maxByOrNull { it.createdAtMs }
        ?: return session
    return session.copy(
        startTime = edit.startTimeMs ?: session.startTime,
        endTime = edit.endTimeMs,
        sleepType = edit.sleepType ?: session.sleepType,
        notes = edit.notes,
    )
}
