package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import java.time.Instant
import javax.inject.Inject

class UpdateBreastfeedingSessionUseCase @Inject constructor(
    private val repository: BreastfeedingRepository,
    private val nowProvider: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(
        session: BreastfeedingSession,
        newStart: Instant,
        newEnd: Instant?,
    ) {
        val (newPausedDurationMs, newPausedAt) = foldPause(session, newStart, newEnd)

        val error = validateBreastfeedingEdit(
            startTime = newStart,
            endTime = newEnd,
            pausedDurationMs = newPausedDurationMs,
            now = nowProvider(),
        )
        require(error == null) { error!! }

        val clampedSwitch = session.switchTime?.takeIf { sw ->
            !sw.isBefore(newStart) && (newEnd == null || !sw.isAfter(newEnd))
        }

        repository.updateSession(
            session.copy(
                startTime = newStart,
                endTime = newEnd,
                switchTime = clampedSwitch,
                pausedAt = newPausedAt,
                pausedDurationMs = newPausedDurationMs,
            )
        )
    }
}

/**
 * Returns the (pausedDurationMs, pausedAt) pair that should be persisted after an edit.
 *
 * When the edit closes an in-progress paused session (newEnd != null && session.pausedAt != null),
 * the pause interval between pausedAt (or newStart, whichever is later) and newEnd is folded into
 * pausedDurationMs, and pausedAt is cleared. Without this fold, the closed session would still be
 * marked paused and its computed active duration would silently include the trailing pause.
 *
 * When the edit keeps the session in-progress (newEnd == null), pausedAt is preserved only when it
 * still falls within the new range; pausedDurationMs is untouched.
 */
internal fun foldPause(
    session: BreastfeedingSession,
    newStart: Instant,
    newEnd: Instant?,
): Pair<Long, Instant?> {
    val pausedAt = session.pausedAt ?: return session.pausedDurationMs to null

    if (newEnd == null) {
        val keep = !pausedAt.isBefore(newStart)
        return session.pausedDurationMs to (if (keep) pausedAt else null)
    }

    val pauseStart = if (pausedAt.isBefore(newStart)) newStart else pausedAt
    if (!pauseStart.isBefore(newEnd)) {
        // pausedAt is at or after newEnd: closing session before its own pause is impossible.
        // Return a duration exceeding the session length so validation rejects it.
        val sessionMs = java.time.Duration.between(newStart, newEnd).toMillis()
        return (sessionMs + 1L) to null
    }
    val extraMs = java.time.Duration.between(pauseStart, newEnd).toMillis()
    return (session.pausedDurationMs + extraMs) to null
}
