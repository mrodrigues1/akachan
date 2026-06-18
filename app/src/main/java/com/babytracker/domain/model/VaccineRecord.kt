package com.babytracker.domain.model

import java.time.Instant

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

fun VaccineRecord.effectiveDate(): Instant = administeredDate ?: scheduledDate ?: createdAt

fun VaccineRecord.isOverdue(now: Instant): Boolean =
    status == VaccineStatus.SCHEDULED && scheduledDate != null && scheduledDate.isBefore(now)
