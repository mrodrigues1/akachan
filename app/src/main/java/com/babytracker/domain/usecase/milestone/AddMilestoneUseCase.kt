package com.babytracker.domain.usecase.milestone

import com.babytracker.domain.model.Milestone
import com.babytracker.domain.repository.MilestoneRepository
import javax.inject.Inject

class AddMilestoneUseCase @Inject constructor(
    private val repository: MilestoneRepository,
) {
    suspend operator fun invoke(milestone: Milestone): Long =
        repository.addMilestone(milestone)
}
