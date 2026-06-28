package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepType

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
    ttlMs: Long = PENDING_OP_TTL_MS,
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
    ttlMs: Long = PENDING_OP_TTL_MS,
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
    ttlMs: Long = PENDING_OP_TTL_MS,
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

/**
 * Edit (UPDATE) op is reflected once the snapshot record matches the edited fields. START/STOP do not
 * affect the history overlay, so they are treated as reflected (never pinned by the history merge).
 */
fun sleepEditReflected(op: SleepOp, snapshotRecords: List<SleepSnapshot>): Boolean {
    if (op.action != SleepOpAction.UPDATE) return true
    val entry = snapshotRecords.firstOrNull { it.clientId.isNotEmpty() && it.clientId == op.entryClientId }
        ?: return false
    return (op.startTimeMs == null || entry.startTime == op.startTimeMs) &&
        entry.endTime == op.endTimeMs &&
        (op.sleepType == null || entry.sleepType == op.sleepType) &&
        entry.notes == op.notes
}

/**
 * For the active-session overlay: a START is reflected once the snapshot has that session; a STOP once
 * the snapshot session is ended (or gone); an UPDATE per [sleepEditReflected].
 */
fun sleepActiveReflected(op: SleepOp, snapshotRecords: List<SleepSnapshot>): Boolean {
    val entry = snapshotRecords.firstOrNull { it.clientId.isNotEmpty() && it.clientId == op.entryClientId }
    return when (op.action) {
        SleepOpAction.START -> entry != null
        SleepOpAction.STOP -> entry == null || entry.endTime != null
        SleepOpAction.UPDATE -> sleepEditReflected(op, snapshotRecords)
    }
}
