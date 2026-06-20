package com.babytracker.domain.model

import java.time.Instant

data class DoctorVisit(
    val id: Long = 0,
    val date: Instant,
    val providerName: String? = null,
    val notes: String? = null,
    val snapshotLabel: String? = null,
    val snapshotCreatedAt: Instant? = null,
    val createdAt: Instant,
)

fun DoctorVisit.isUpcoming(now: Instant): Boolean = date.isAfter(now)

fun DoctorVisit.hasSnapshot(): Boolean = snapshotLabel != null
