package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.FeedAuthor

/**
 * Merges the last fetched snapshot with the partner's own unconsumed ops.
 *
 * Op existence is the pending boundary: once the primary applies an op and publishes the updated
 * snapshot, Plan 04 deletes the op. While an op document still exists, it must keep overriding the
 * fetched snapshot even if Firestore has already acknowledged the local write.
 */
fun mergeFeedHistory(
    snapshotFeeds: List<BottleFeedSnapshot>,
    pendingOps: List<FeedOp>,
): MergedFeedHistory {
    val (legacy, identified) = snapshotFeeds.partition { it.clientId.isEmpty() }
    val byClientId = identified.associateBy { it.clientId }.toMutableMap()

    // Keep ordering aligned with ProcessFeedOpsUseCase so optimistic history matches primary apply.
    pendingOps.sortedBy { it.createdAtMs }.forEach { op ->
        when (op.action) {
            FeedOpAction.CREATE, FeedOpAction.UPDATE -> {
                val timestampMs = op.timestampMs ?: return@forEach
                val volumeMl = op.volumeMl ?: return@forEach
                val type = op.type ?: return@forEach
                byClientId[op.entryClientId] = BottleFeedSnapshot(
                    timestamp = timestampMs,
                    volumeMl = volumeMl,
                    type = type,
                    clientId = op.entryClientId,
                    author = FeedAuthor.PARTNER.name,
                    notes = op.notes,
                )
            }
            FeedOpAction.DELETE -> byClientId.remove(op.entryClientId)
        }
    }

    return MergedFeedHistory(
        entries = (byClientId.values + legacy).sortedByDescending { it.timestamp },
        pendingOpIds = pendingOps.mapTo(mutableSetOf()) { it.opId },
    )
}

/** True once the snapshot already shows the op's effect, so the optimistic overlay can be dropped. */
fun feedOpReflected(op: FeedOp, snapshotFeeds: List<BottleFeedSnapshot>): Boolean {
    val entry = snapshotFeeds.firstOrNull { it.clientId.isNotEmpty() && it.clientId == op.entryClientId }
    return when (op.action) {
        FeedOpAction.DELETE -> entry == null
        FeedOpAction.CREATE, FeedOpAction.UPDATE ->
            entry != null &&
                entry.timestamp == op.timestampMs &&
                entry.volumeMl == op.volumeMl &&
                entry.type == op.type &&
                entry.notes == op.notes
    }
}
