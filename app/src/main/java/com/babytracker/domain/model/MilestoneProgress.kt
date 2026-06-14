package com.babytracker.domain.model

/**
 * One row of the milestone catalog: a [Milestone] paired with its [achievement]
 * if the baby has reached it ([achievement] is null when not yet achieved).
 */
data class MilestoneProgress(
    val milestone: Milestone,
    val achievement: MilestoneAchievement?,
) {
    val isAchieved: Boolean get() = achievement != null
}
