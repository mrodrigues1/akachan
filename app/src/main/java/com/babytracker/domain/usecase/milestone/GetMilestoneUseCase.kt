package com.babytracker.domain.usecase.milestone

import com.babytracker.domain.model.Milestone
import com.babytracker.domain.repository.MilestoneRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMilestoneUseCase @Inject constructor(
    private val repository: MilestoneRepository,
) {
    operator fun invoke(id: Long): Flow<Milestone?> = repository.getMilestone(id)
}
