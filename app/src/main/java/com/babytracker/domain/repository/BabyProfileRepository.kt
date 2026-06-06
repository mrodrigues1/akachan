package com.babytracker.domain.repository

import com.babytracker.domain.model.BabyProfile

interface BabyProfileRepository {
    suspend fun getProfile(): BabyProfile?
    suspend fun upsertProfile(profile: BabyProfile)
}
