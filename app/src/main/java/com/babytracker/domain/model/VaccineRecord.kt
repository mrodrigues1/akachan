package com.babytracker.domain.model

import java.time.Instant
import java.time.ZoneId

data class VaccineRecord(
    val id: Long = 0,
    val name: String,
    val doseLabel: String? = null,
    val status: VaccineStatus,
    val scheduledDate: Instant? = null,
    val administeredDate: Instant? = null,
    val notes: String? = null,
    val createdAt: Instant,
)

/**
 * Overdue is day-granular, not instant-granular: a dose is overdue only once its scheduled calendar
 * day is wholly in the past. A dose scheduled for today is never overdue (it reads as "due today"),
 * which is why a same-day dose shows a countdown instead of "Overdue by 0 days".
 */
fun VaccineRecord.isOverdue(now: Instant, zone: ZoneId): Boolean =
    status == VaccineStatus.SCHEDULED &&
        scheduledDate != null &&
        scheduledDate.isDayBefore(now, zone)

/**
 * Past-target is the to-schedule analogue of [isOverdue]: a to-schedule dose whose target calendar
 * day is wholly in the past. Used only for in-section flagging on the lists — to-schedule doses never
 * drive the dashboard hero, so this is intentionally separate from [isOverdue] (which stays
 * scheduled-only).
 */
fun VaccineRecord.isPastTarget(now: Instant, zone: ZoneId): Boolean =
    status == VaccineStatus.TO_SCHEDULE &&
        scheduledDate != null &&
        scheduledDate.isDayBefore(now, zone)

/** True when this instant's calendar day in [zone] is wholly before [now]'s — the day-granular past check. */
private fun Instant.isDayBefore(now: Instant, zone: ZoneId): Boolean =
    atZone(zone).toLocalDate().isBefore(now.atZone(zone).toLocalDate())
