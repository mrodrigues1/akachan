package com.babytracker.domain.usecase.milestone

import com.babytracker.domain.model.Milestone
import com.babytracker.domain.repository.MilestoneRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMilestonesUseCase @Inject constructor(
    private val repository: MilestoneRepository,
) {
    operator fun invoke(): Flow<List<Milestone>> = repository.getMilestones()
}
