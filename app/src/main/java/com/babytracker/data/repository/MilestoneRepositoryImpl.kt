package com.babytracker.data.repository

import com.babytracker.data.local.dao.MilestoneDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.repository.MilestoneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MilestoneRepositoryImpl @Inject constructor(
    private val dao: MilestoneDao,
) : MilestoneRepository {

    override fun getMilestones(): Flow<List<Milestone>> =
        dao.getAll().mapList { it.toDomain() }

    override fun getMilestone(id: Long): Flow<Milestone?> =
        dao.getById(id).map { it?.toDomain() }

    override suspend fun addMilestone(milestone: Milestone): Long =
        dao.insert(milestone.toEntity().copy(id = 0))

    override suspend fun updateMilestone(milestone: Milestone) =
        dao.update(milestone.toEntity())

    override suspend fun deleteMilestone(id: Long) = dao.deleteById(id)
}
