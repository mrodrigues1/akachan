package com.babytracker.domain.repository

import com.babytracker.domain.model.Milestone
import kotlinx.coroutines.flow.Flow

interface MilestoneRepository {
    /** All moments, newest first. */
    fun getMilestones(): Flow<List<Milestone>>
    fun getMilestone(id: Long): Flow<Milestone?>
    suspend fun addMilestone(milestone: Milestone): Long
    suspend fun updateMilestone(milestone: Milestone)
    suspend fun deleteMilestone(id: Long)
}
