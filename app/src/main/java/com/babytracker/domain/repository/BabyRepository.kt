package com.babytracker.domain.repository

import com.babytracker.domain.model.Baby
import kotlinx.coroutines.flow.Flow

interface BabyRepository {
    fun getBabyProfile(): Flow<Baby?>
    suspend fun saveBabyProfile(baby: Baby)
    fun isOnboardingComplete(): Flow<Boolean>
}
