package com.babytracker.domain.model

enum class VaccineStatus { SCHEDULED, ADMINISTERED }

fun String.toVaccineStatusOrNull(): VaccineStatus? = when (this) {
    VaccineStatus.SCHEDULED.name -> VaccineStatus.SCHEDULED
    VaccineStatus.ADMINISTERED.name -> VaccineStatus.ADMINISTERED
    else -> null
}

// Defaults to ADMINISTERED: an unknown/corrupt stored value is treated as a logged shot
// rather than a pending schedule, so it never fabricates a future reminder.
fun String.toVaccineStatusSafe(): VaccineStatus = toVaccineStatusOrNull() ?: VaccineStatus.ADMINISTERED
