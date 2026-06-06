package com.babytracker.domain.model

import java.time.LocalDate

data class BabyProfile(
    val dateOfBirth: LocalDate?,
    val dueDate: LocalDate?,
    val isDueDateUserProvided: Boolean,
    val homeTimezoneId: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
