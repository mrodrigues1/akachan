package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Whether ending a night sleep at [endTime] should update the stored wake-time setting: only when
 * the sleep ends [today], and no other night-sleep record already ending today ends later. Shared
 * by the live stop path (SleepSessionController) and the manual-entry save path (SleepViewModel) so
 * both apply the same "latest end wins" rule instead of diverging.
 */
fun shouldUpdateWakeTimeFor(
    endTime: Instant,
    sleepType: SleepType,
    existingRecords: List<SleepRecord>,
    zone: ZoneId,
    today: LocalDate,
    excludingId: Long? = null,
): Boolean {
    if (sleepType != SleepType.NIGHT_SLEEP) return false
    if (endTime.atZone(zone).toLocalDate() != today) return false
    val latestOtherEnd = existingRecords
        .asSequence()
        .filter { it.sleepType == SleepType.NIGHT_SLEEP && it.id != excludingId }
        .mapNotNull { it.endTime }
        .filter { it.atZone(zone).toLocalDate() == today }
        .maxOrNull()
    return latestOtherEnd == null || !endTime.isBefore(latestOtherEnd)
}
