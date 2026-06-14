package com.babytracker.data.repository

import com.babytracker.data.local.dao.MilestoneDao
import com.babytracker.data.local.entity.toDomainOrNull
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.model.MilestoneAchievement
import com.babytracker.domain.repository.MilestoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MilestoneRepositoryImpl @Inject constructor(
    private val dao: MilestoneDao,
) : MilestoneRepository {

    override suspend fun logAchievement(achievement: MilestoneAchievement): Long =
        // Force a fresh row id (0 = autogenerate) so the unique `milestone` index is the only
        // conflict key. REPLACE is delete-then-insert; a stale caller-supplied id paired with a
        // different milestone could otherwise delete an unrelated primary-key row as well.
        dao.upsert(achievement.toEntity().copy(id = 0))

    override fun getAchievements(): Flow<List<MilestoneAchievement>> =
        dao.getAll().map { entities -> entities.mapNotNull { it.toDomainOrNull() } }

    override suspend fun deleteAchievement(milestone: Milestone) =
        dao.deleteByMilestone(milestone.name)
}
