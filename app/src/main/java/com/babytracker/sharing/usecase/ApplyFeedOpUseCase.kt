package com.babytracker.sharing.usecase

import android.util.Log
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import java.time.Instant
import javax.inject.Inject

/**
 * Outcome of applying a single feed op.
 *
 * @param roomChanged true if Room changed (a snapshot push is required).
 * @param consumedBagId non-null only for a *freshly created* partner feed that consumed a stash
 *   bag. Null for re-applies, updates, deletes, drops, and creates without a linked bag. This is
 *   the only signal that lets the caller fire a one-shot stash-consumption notification without
 *   re-notifying on idempotent replays.
 */
data class FeedOpApplyResult(
    val roomChanged: Boolean,
    val consumedBagId: Long? = null,
)

/**
 * Applies a single partner-authored feed op to Room. The primary is the authoritative
 * ownership enforcement point: security rules cannot see Room state, so update/delete
 * are allowed only on entries whose `author == PARTNER`, and create always forces
 * `author = PARTNER` regardless of the payload.
 */
class ApplyFeedOpUseCase internal constructor(
    private val repository: BottleFeedRepository,
    private val now: () -> Instant,
) {
    @Inject
    constructor(repository: BottleFeedRepository) : this(repository, Instant::now)

    /** Invalid/forged ops return [FeedOpApplyResult] with `roomChanged = false`. */
    suspend operator fun invoke(op: FeedOp): FeedOpApplyResult = when (op.action) {
        FeedOpAction.CREATE -> applyCreate(op)
        FeedOpAction.UPDATE -> applyUpdate(op)
        FeedOpAction.DELETE -> applyDelete(op)
    }

    private suspend fun applyCreate(op: FeedOp): FeedOpApplyResult {
        val payload = validatedPayload(op) ?: return drop(op, "invalid payload")
        val existing = repository.getByClientId(op.entryClientId)
        return when {
            existing == null -> {
                repository.insertWithBagConsume(
                    BottleFeed(
                        clientId = op.entryClientId,
                        timestamp = payload.timestamp,
                        volumeMl = payload.volumeMl,
                        type = payload.type,
                        notes = op.notes,
                        createdAt = Instant.ofEpochMilli(op.createdAtMs),
                        author = FeedAuthor.PARTNER,
                    ),
                    consumedBagId = op.consumedBagId,
                    usedAt = now(),
                )
                // consumedBagId is reported only on this fresh insert so the caller notifies once.
                FeedOpApplyResult(roomChanged = true, consumedBagId = op.consumedBagId)
            }
            existing.author == FeedAuthor.OWNER -> drop(op, "create collides with OWNER entry")
            else -> {
                // Idempotent re-apply (crash between push and delete): upsert-by-clientId.
                // consumedBagId is create-only and was already consumed on first apply — not re-linked
                // and not re-reported, so a replay never fires a duplicate notification.
                FeedOpApplyResult(
                    roomChanged = repository.updateDetails(
                        id = existing.id,
                        timestamp = payload.timestamp,
                        volumeMl = payload.volumeMl,
                        type = payload.type,
                        linkedMilkBagId = existing.linkedMilkBagId,
                        notes = op.notes,
                    ),
                )
            }
        }
    }

    private suspend fun applyUpdate(op: FeedOp): FeedOpApplyResult {
        val payload = validatedPayload(op) ?: return drop(op, "invalid payload")
        val existing = repository.getByClientId(op.entryClientId)
            ?: return drop(op, "entry missing (deleted on primary)")
        if (existing.author != FeedAuthor.PARTNER) return drop(op, "update targets OWNER entry")
        return FeedOpApplyResult(
            roomChanged = repository.updateDetails(
                id = existing.id,
                timestamp = payload.timestamp,
                volumeMl = payload.volumeMl,
                type = payload.type,
                linkedMilkBagId = existing.linkedMilkBagId,
                notes = op.notes,
            ),
        )
    }

    private suspend fun applyDelete(op: FeedOp): FeedOpApplyResult {
        val existing = repository.getByClientId(op.entryClientId)
            ?: return drop(op, "entry already gone")
        if (existing.author != FeedAuthor.PARTNER) return drop(op, "delete targets OWNER entry")
        return FeedOpApplyResult(roomChanged = repository.deleteWithInventoryRestore(existing))
    }

    private data class ValidPayload(val timestamp: Instant, val volumeMl: Int, val type: FeedType)

    private fun validatedPayload(op: FeedOp): ValidPayload? {
        val timestampMs = op.timestampMs ?: return null
        val volumeMl = op.volumeMl ?: return null
        val type = op.type?.let { raw -> FeedType.entries.firstOrNull { it.name == raw } } ?: return null
        if (volumeMl <= 0) return null
        val timestamp = Instant.ofEpochMilli(timestampMs)
        if (timestamp.isAfter(now())) return null
        return ValidPayload(timestamp, volumeMl, type)
    }

    private fun drop(op: FeedOp, reason: String): FeedOpApplyResult {
        Log.w(TAG, "Dropping feed op ${op.opId} (${op.action} ${op.entryClientId}): $reason")
        return FeedOpApplyResult(roomChanged = false)
    }

    private companion object {
        const val TAG = "ApplyFeedOp"
    }
}
