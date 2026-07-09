package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import java.time.Duration
import java.time.Instant

fun maxSleepDurationFor(type: SleepType): Duration = when (type) {
    SleepType.NAP -> Duration.ofHours(SleepPredictionTuning.MAX_NAP_DURATION_HOURS)
    SleepType.NIGHT_SLEEP -> Duration.ofHours(SleepPredictionTuning.MAX_NIGHT_SLEEP_DURATION_HOURS)
}

/**
 * Returns `null` when the proposed sleep entry is valid, otherwise the reason it is invalid.
 * [excludingId] is the id of the record being edited (excluded from the overlap check); pass
 * `null` when adding a new entry. [now] estimates the end of an in-progress overlap candidate
 * (a record with a null endTime).
 *
 * Overlap is checked against every existing record regardless of either record's sleep type —
 * any two sleep records overlapping in time are invalid, not just two night sleeps (issue #748).
 */
fun validateSleepEntry(
    startTime: Instant,
    endTime: Instant,
    type: SleepType,
    existingRecords: List<SleepRecord>,
    now: Instant,
    excludingId: Long? = null,
): SleepEntryError? {
    if (!endTime.isAfter(startTime)) return SleepEntryError.END_BEFORE_START
    if (Duration.between(startTime, endTime) > maxSleepDurationFor(type)) return SleepEntryError.DURATION_TOO_LONG
    val overlaps = existingRecords.any { existing ->
        existing.id != excludingId &&
            startTime.isBefore(existing.endTime ?: now) &&
            endTime.isAfter(existing.startTime)
    }
    if (overlaps) return SleepEntryError.OVERLAP
    return null
}
