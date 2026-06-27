package com.babytracker.domain.usecase.features

import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.repository.FeatureToggleRepository
import kotlinx.coroutines.flow.first

/**
 * Read the current selection, apply [transform], and persist only if it changed. Returns false when
 * [transform] left the set untouched (e.g. the >=1 invariant rejected the change, nothing written).
 * Shared by the domain- and feature-level enable use cases.
 */
internal suspend fun FeatureToggleRepository.applyEnabledChange(
    transform: (Set<AppFeature>) -> Set<AppFeature>,
): Boolean {
    val current = getEnabledFeatures().first()
    val next = transform(current)
    if (next == current) return false
    setEnabledFeatures(next)
    return true
}
