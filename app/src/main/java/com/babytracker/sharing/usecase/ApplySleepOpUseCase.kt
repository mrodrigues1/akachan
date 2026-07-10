package com.babytracker.sharing.usecase

import android.util.Log
import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepOpAction
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** What a partner sleep op did to Room, plus the notification (if any) the caller should coalesce. */
enum class SleepNotifyKind { STARTED, STOPPED, EDITED }

data class PartnerSleepNotification(val kind: SleepNotifyKind, val sleepType: SleepType)

/**
 * @param roomChanged true if Room changed (a snapshot push is required).
 * @param notification non-null only for a partner op that *actually changed* Room and should notify
 *   the main partner once. Null for re-applies, converges, drops, and no-ops, so an idempotent
 *   replay never re-fires a notification.
 */
data class SleepOpApplyResult(
    val roomChanged: Boolean,
    val notification: PartnerSleepNotification? = null,
)

/**
 * Applies a single partner-authored sleep op to Room. The primary is the authoritative point:
 * START/STOP are shared (ungated), UPDATE is owner-gated — the partner may edit only sessions it
 * started (`startedBy == PARTNER`); security rules cannot see Room state, so this is enforced here.
 */
class ApplySleepOpUseCase internal constructor(
    private val repository: SleepRepository,
    private val now: () -> Instant,
) {
    @Inject
    constructor(repository: SleepRepository) : this(repository, Instant::now)

    suspend operator fun invoke(op: SleepOp): SleepOpApplyResult = when (op.action) {
        SleepOpAction.START -> applyStart(op)
        SleepOpAction.STOP -> applyStop(op)
        SleepOpAction.UPDATE -> applyUpdate(op)
    }

    private suspend fun applyStart(op: SleepOp): SleepOpApplyResult {
        val payload = validatedTimes(op) ?: return drop(op, "invalid start payload")
        // Idempotent re-apply (crash between push and delete): the session already exists.
        if (repository.getByClientId(op.entryClientId) != null) return unchanged()
        // Converge, do not create a second active record. Not a hostile drop — the partner UI
        // reconciles to whichever session is active once the re-published snapshot arrives.
        if (repository.getActiveRecord() != null) return unchanged()

        repository.insertRecord(
            SleepRecord(
                clientId = op.entryClientId,
                startTime = payload.startTime,
                endTime = null,
                sleepType = payload.sleepType,
                notes = op.notes,
                startedBy = SleepAuthor.PARTNER,
                timezoneId = ZoneId.systemDefault().id,
            ),
        )
        return SleepOpApplyResult(true, PartnerSleepNotification(SleepNotifyKind.STARTED, payload.sleepType))
    }

    private suspend fun applyStop(op: SleepOp): SleepOpApplyResult {
        val endTime = op.endTimeMs?.let(Instant::ofEpochMilli)?.takeIf { !it.isAfter(now()) }
            ?: return drop(op, "stop missing/future endTime")
        val existing = repository.getByClientId(op.entryClientId) ?: return drop(op, "stop target missing")
        if (existing.endTime != null) return unchanged() // already ended
        // Clamp so a stale-clock stop never produces a negative or zero duration. Persistence rounds to
        // milliseconds (Instant.toEpochMilli()), so the bump must survive that round-trip.
        val clampedEnd = if (!endTime.isAfter(existing.startTime)) existing.startTime.plusMillis(1) else endTime
        repository.updateRecord(existing.copy(endTime = clampedEnd))
        return SleepOpApplyResult(true, PartnerSleepNotification(SleepNotifyKind.STOPPED, existing.sleepType))
    }

    private suspend fun applyUpdate(op: SleepOp): SleepOpApplyResult {
        val payload = validatedTimes(op) ?: return drop(op, "invalid update payload")
        val existing = repository.getByClientId(op.entryClientId) ?: return drop(op, "update target missing")
        // Edit only own: drop edits aimed at sessions the owner started.
        if (existing.startedBy != SleepAuthor.PARTNER) return drop(op, "update targets OWNER session")
        // Last-write-wins (no field-level merge); endTime null keeps the session in progress.
        repository.updateRecord(
            existing.copy(
                startTime = payload.startTime,
                endTime = payload.endTime,
                sleepType = payload.sleepType,
                notes = op.notes,
            ),
        )
        return SleepOpApplyResult(true, PartnerSleepNotification(SleepNotifyKind.EDITED, payload.sleepType))
    }

    private data class ValidTimes(val startTime: Instant, val endTime: Instant?, val sleepType: SleepType)

    // Shared by START and UPDATE: both require a non-future startTime + valid sleepType, and an
    // optional endTime that, if present, is not in the future and is strictly after startTime
    // (SleepRecord's invariant rejects endTime <= startTime).
    private fun validatedTimes(op: SleepOp): ValidTimes? {
        val startTime = op.startTimeMs?.let(Instant::ofEpochMilli) ?: return null
        val sleepType = op.sleepType ?: return null
        if (startTime.isAfter(now())) return null
        val endTime = op.endTimeMs?.let(Instant::ofEpochMilli)
        if (endTime != null && (endTime.isAfter(now()) || !endTime.isAfter(startTime))) return null
        return ValidTimes(startTime, endTime, sleepType)
    }

    private fun unchanged() = SleepOpApplyResult(roomChanged = false)

    private fun drop(op: SleepOp, reason: String): SleepOpApplyResult {
        Log.w(TAG, "Dropping sleep op ${op.opId} (${op.action} ${op.entryClientId}): $reason")
        return SleepOpApplyResult(roomChanged = false)
    }

    private companion object {
        const val TAG = "ApplySleepOp"
    }
}
