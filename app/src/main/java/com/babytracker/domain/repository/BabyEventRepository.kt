package com.babytracker.domain.repository

import com.babytracker.domain.model.BabyEvent
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface BabyEventRepository {
    suspend fun logEvent(event: BabyEvent)
    fun getEventsSince(cutoff: Instant): Flow<List<BabyEvent>>
}
