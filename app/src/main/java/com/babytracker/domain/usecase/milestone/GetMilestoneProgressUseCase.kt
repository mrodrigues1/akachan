package com.babytracker.domain.usecase.milestone

import com.babytracker.domain.model.Milestone
import com.babytracker.domain.model.MilestoneProgress
import com.babytracker.domain.repository.MilestoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Joins the full WHO milestone catalog with any logged achievements, so the UI
 * always renders all six milestones in their declared order — achieved or not.
 */
class GetMilestoneProgressUseCase @Inject constructor(
    private val repository: MilestoneRepository,
) {
    operator fun invoke(): Flow<List<MilestoneProgress>> =
        repository.getAchievements().map { achievements ->
            val byMilestone = achievements.associateBy { it.milestone }
            Milestone.entries.map { milestone ->
                MilestoneProgress(milestone, byMilestone[milestone])
            }
        }
}
