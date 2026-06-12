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
): List<BottleFeedSnapshot> {
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

    return (byClientId.values + legacy).sortedByDescending { it.timestamp }
}
