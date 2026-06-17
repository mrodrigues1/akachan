package com.babytracker.domain.usecase.features

import com.babytracker.domain.model.FeatureDomain
import com.babytracker.domain.model.FeatureSelection
import com.babytracker.domain.repository.FeatureToggleRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SetDomainEnabledUseCase @Inject constructor(
    private val repository: FeatureToggleRepository,
) {
    /** Returns false when the >=1 invariant rejected the change (nothing was written). */
    suspend operator fun invoke(domain: FeatureDomain, enabled: Boolean): Boolean {
        val current = repository.getEnabledFeatures().first()
        val next = FeatureSelection.setDomain(current, domain, enabled)
        if (next == current) return false
        repository.setEnabledFeatures(next)
        return true
    }
}
