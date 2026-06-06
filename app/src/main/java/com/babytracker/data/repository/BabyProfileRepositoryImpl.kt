package com.babytracker.data.repository

import com.babytracker.data.local.dao.BabyProfileDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.BabyProfile
import com.babytracker.domain.repository.BabyProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BabyProfileRepositoryImpl @Inject constructor(
    private val dao: BabyProfileDao
) : BabyProfileRepository {
    override suspend fun getProfile(): BabyProfile? = dao.getProfile()?.toDomain()

    override suspend fun upsertProfile(profile: BabyProfile) = dao.upsertProfile(profile.toEntity())
}
