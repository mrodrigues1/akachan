package com.babytracker.domain.repository

import com.babytracker.domain.model.AppFeature
import kotlinx.coroutines.flow.Flow

interface FeatureToggleRepository {
    fun getEnabledFeatures(): Flow<Set<AppFeature>>
    suspend fun setEnabledFeatures(features: Set<AppFeature>)
}
