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
 * The partner's sleep history: snapshot records overlaid with the partner's own fresh pending ops
 * (optimistic), mirroring [mergeFeedHistory]. ALL ops are applied in createdAtMs order — the same order
 * the primary applies them — so a START synthesizes an active session, a STOP ends it, and an UPDATE is
 * last-write-wins; in particular an UPDATE *before* a STOP can't reopen the session the STOP completed.
 * This surfaces a session the partner just started/ended before the primary re-publishes, so the
 * dashboard/history don't look like "nothing saved". Op existence (within the TTL) is the pending
 * boundary — once the primary applies/drops and deletes the op, the snapshot wins again. Newest first.
 */
fun mergeSleepHistory(
    snapshotRecords: List<SleepSnapshot>,
    pendingOps: List<SleepOp>,
    nowMs: Long,
    ttlMs: Long = PENDING_OP_TTL_MS,
): MergedSleepHistory {
    val fresh = pendingOps.filter { nowMs - it.createdAtMs <= ttlMs }
    val (legacy, identified) = snapshotRecords.partition { it.clientId.isEmpty() }
    val byClientId = identified.associateBy { it.clientId }.toMutableMap()

    fresh.sortedBy { it.createdAtMs }.forEach { op ->
        when (op.action) {
            SleepOpAction.START -> {
                val sleepType = op.sleepType ?: return@forEach
                if (op.entryClientId !in byClientId) {
                    byClientId[op.entryClientId] = SleepSnapshot(
                        id = 0,
                        startTime = op.startTimeMs ?: op.createdAtMs,
                        endTime = null,
                        sleepType = sleepType,
                        notes = op.notes,
                        clientId = op.entryClientId,
                        startedBy = SleepAuthor.PARTNER.name,
                    )
                }
            }
            SleepOpAction.STOP -> {
                // No-op if already ended, matching ApplySleepOpUseCase.applyStop.
                val existing = byClientId[op.entryClientId] ?: return@forEach
                if (existing.endTime == null) {
                    val end = op.endTimeMs ?: op.createdAtMs
                    byClientId[op.entryClientId] = existing.copy(endTime = maxOf(end, existing.startTime))
                }
            }
            SleepOpAction.UPDATE -> {
                // Last-write-wins, including endTime (null reopens the session) — same as applyUpdate.
                val existing = byClientId[op.entryClientId] ?: return@forEach
                byClientId[op.entryClientId] = existing.copy(
                    startTime = op.startTimeMs ?: existing.startTime,
                    endTime = op.endTimeMs,
                    sleepType = op.sleepType ?: existing.sleepType,
                    notes = op.notes,
                )
            }
        }
    }

    return MergedSleepHistory(
        entries = (byClientId.values + legacy).sortedByDescending { it.startTime },
        pendingOpIds = fresh.mapTo(mutableSetOf()) { it.opId },
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

/**
 * For the history overlay (synthesize/complete sessions): a START is reflected once the snapshot has
 * the session; a STOP only once the snapshot session is *ended* — NOT merely gone (unlike
 * [sleepActiveReflected]). Keeping the STOP pinned until the snapshot shows the ended session is what
 * stops the completed entry from briefly flickering back to active during the primary's
 * delete-op-before-publish-snapshot window. UPDATE per [sleepEditReflected].
 */
fun sleepHistoryReflected(op: SleepOp, snapshotRecords: List<SleepSnapshot>): Boolean {
    val entry = snapshotRecords.firstOrNull { it.clientId.isNotEmpty() && it.clientId == op.entryClientId }
    return when (op.action) {
        SleepOpAction.START -> entry != null
        SleepOpAction.STOP -> entry != null && entry.endTime != null
        SleepOpAction.UPDATE -> sleepEditReflected(op, snapshotRecords)
    }
}
