package com.babytracker.domain.model

import java.time.LocalDate

/**
 * Records that the baby achieved a [Milestone]. At most one achievement exists
 * per milestone (logging again replaces the previous record).
 */
data class MilestoneAchievement(
    val id: Long = 0,
    val milestone: Milestone,
    val achievedOn: LocalDate,
    val photoUri: String? = null,
    val notes: String? = null,
)
