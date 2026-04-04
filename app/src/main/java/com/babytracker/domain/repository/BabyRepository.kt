package com.babytracker.domain.repository

import com.babytracker.domain.model.Baby
import kotlinx.coroutines.flow.Flow

interface BabyRepository {
    fun getBaby(): Flow<Baby?>
    suspend fun saveBaby(baby: Baby)
}
