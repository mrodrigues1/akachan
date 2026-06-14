package com.babytracker.domain.usecase.milestone

import com.babytracker.domain.model.MilestoneAchievement
import com.babytracker.domain.repository.MilestoneRepository
import javax.inject.Inject

class LogMilestoneUseCase @Inject constructor(
    private val repository: MilestoneRepository,
) {
    suspend operator fun invoke(achievement: MilestoneAchievement): Long =
        repository.logAchievement(achievement)
}
