package com.babytracker.domain.model

enum class VaccineStatus { TO_SCHEDULE, SCHEDULED, ADMINISTERED }

fun String.toVaccineStatusOrNull(): VaccineStatus? = when (this) {
    VaccineStatus.TO_SCHEDULE.name -> VaccineStatus.TO_SCHEDULE
    VaccineStatus.SCHEDULED.name -> VaccineStatus.SCHEDULED
    VaccineStatus.ADMINISTERED.name -> VaccineStatus.ADMINISTERED
    else -> null
}

// Defaults to ADMINISTERED: an unknown/corrupt stored value is treated as a logged shot
// rather than a pending schedule, so it never fabricates a future reminder.
fun String.toVaccineStatusSafe(): VaccineStatus = toVaccineStatusOrNull() ?: VaccineStatus.ADMINISTERED
