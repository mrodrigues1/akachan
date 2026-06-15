package com.babytracker.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * A free-form moment a parent captures for their baby — a first smile, a trip,
 * a funny face. Title and [date] are required; everything else is optional.
 * [time] is null when the parent recorded only the day.
 */
data class Milestone(
    val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val time: LocalTime? = null,
    val photoUri: String? = null,
    val note: String? = null,
)
