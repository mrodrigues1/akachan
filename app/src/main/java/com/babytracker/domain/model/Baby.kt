package com.babytracker.domain.model

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

data class Baby(
    val name: String,
    val birthDate: LocalDate,
    val allergies: List<AllergyType> = emptyList(),
    val customAllergyNote: String? = null,
) {
    val ageInDays: Long
        get() = ChronoUnit.DAYS.between(birthDate, LocalDate.now())

    val ageInWeeks: Int
        get() = (ageInDays / 7).toInt()

    val ageInMonths: Int
        get() = Period.between(birthDate, LocalDate.now()).toTotalMonths().toInt()
}
