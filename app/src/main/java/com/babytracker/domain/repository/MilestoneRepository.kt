package com.babytracker.domain.repository

import com.babytracker.domain.model.Milestone
import com.babytracker.domain.model.MilestoneAchievement
import kotlinx.coroutines.flow.Flow

interface MilestoneRepository {
    suspend fun logAchievement(achievement: MilestoneAchievement): Long
    fun getAchievements(): Flow<List<MilestoneAchievement>>
    suspend fun deleteAchievement(milestone: Milestone)
}
