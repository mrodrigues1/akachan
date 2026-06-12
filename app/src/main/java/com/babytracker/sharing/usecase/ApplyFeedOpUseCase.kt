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

    /** @return true if Room changed (snapshot push required). Invalid/forged ops return false. */
    suspend operator fun invoke(op: FeedOp): Boolean = when (op.action) {
        FeedOpAction.CREATE -> applyCreate(op)
        FeedOpAction.UPDATE -> applyUpdate(op)
        FeedOpAction.DELETE -> applyDelete(op)
    }

    private suspend fun applyCreate(op: FeedOp): Boolean {
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
                true
            }
            existing.author == FeedAuthor.OWNER -> drop(op, "create collides with OWNER entry")
            else -> {
                // Idempotent re-apply (crash between push and delete): upsert-by-clientId.
                // consumedBagId is create-only and was already consumed on first apply — not re-linked.
                repository.updateDetails(
                    id = existing.id,
                    timestamp = payload.timestamp,
                    volumeMl = payload.volumeMl,
                    type = payload.type,
                    linkedMilkBagId = existing.linkedMilkBagId,
                    notes = op.notes,
                )
            }
        }
    }

    private suspend fun applyUpdate(op: FeedOp): Boolean {
        val payload = validatedPayload(op) ?: return drop(op, "invalid payload")
        val existing = repository.getByClientId(op.entryClientId)
            ?: return drop(op, "entry missing (deleted on primary)")
        if (existing.author != FeedAuthor.PARTNER) return drop(op, "update targets OWNER entry")
        return repository.updateDetails(
            id = existing.id,
            timestamp = payload.timestamp,
            volumeMl = payload.volumeMl,
            type = payload.type,
            linkedMilkBagId = existing.linkedMilkBagId,
            notes = op.notes,
        )
    }

    private suspend fun applyDelete(op: FeedOp): Boolean {
        val existing = repository.getByClientId(op.entryClientId)
            ?: return drop(op, "entry already gone")
        if (existing.author != FeedAuthor.PARTNER) return drop(op, "delete targets OWNER entry")
        return repository.deleteWithInventoryRestore(existing)
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

    private fun drop(op: FeedOp, reason: String): Boolean {
        Log.w(TAG, "Dropping feed op ${op.opId} (${op.action} ${op.entryClientId}): $reason")
        return false
    }

    private companion object {
        const val TAG = "ApplyFeedOp"
    }
}
