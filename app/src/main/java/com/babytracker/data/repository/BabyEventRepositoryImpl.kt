package com.babytracker.data.repository

import com.babytracker.data.local.dao.BabyEventDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.BabyEvent
import com.babytracker.domain.repository.BabyEventRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BabyEventRepositoryImpl @Inject constructor(
    private val dao: BabyEventDao,
) : BabyEventRepository {

    override suspend fun logEvent(event: BabyEvent) = dao.insertEvent(event.toEntity())

    override fun getEventsSince(cutoff: Instant): Flow<List<BabyEvent>> =
        dao.getEventsSince(cutoff.toEpochMilli()).mapList { it.toDomain() }
}
