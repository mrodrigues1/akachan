package com.babytracker.sharing.domain.model

/** Ops carried across snapshot/op emissions need identity + age. Both [FeedOp] and [SleepOp] satisfy it. */
interface PendingOp {
    val opId: String
    val createdAtMs: Long
}

// Generalized from the old PENDING_SLEEP_OP_TTL_MS: a never-applied op (rejected by the primary, or
// the primary offline) stops pinning the optimistic overlay after this window.
const val PENDING_OP_TTL_MS = 60_000L

data class Reconciled<O>(
    val effectiveOps: List<O>,
    val nextTracked: List<O>,
)

/**
 * Retains an optimistic op overlay until the snapshot reflects it OR the TTL elapses — so an op the
 * primary deletes BEFORE its snapshot update arrives keeps overlaying instead of flickering out.
 *
 * Pure: call once per combined (snapshot, ops) emission, threading [Reconciled.nextTracked] back in as
 * [tracked]. Live ops are always included (an idempotent overlay if the snapshot already shows them);
 * a vanished op is carried only while still fresh and not yet reflected.
 */
fun <O : PendingOp> reconcilePendingOps(
    isReflected: (op: O) -> Boolean,
    liveOps: List<O>,
    tracked: List<O>,
    nowMs: Long,
    ttlMs: Long = PENDING_OP_TTL_MS,
): Reconciled<O> {
    val liveIds = liveOps.mapTo(mutableSetOf()) { it.opId }
    val carried = tracked.filter { op ->
        op.opId !in liveIds &&
            nowMs - op.createdAtMs <= ttlMs &&
            !isReflected(op)
    }
    val effectiveOps = liveOps + carried
    // Carry forward anything still fresh and unreflected; drop converged/expired ops so a snapshot-
    // confirmed entry can't reappear from a stale overlay on the next emission.
    val nextTracked = effectiveOps.filter { op ->
        nowMs - op.createdAtMs <= ttlMs && !isReflected(op)
    }
    return Reconciled(effectiveOps, nextTracked)
}
