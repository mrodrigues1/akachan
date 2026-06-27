package com.babytracker.domain.usecase.features

import com.babytracker.domain.model.FeatureDomain
import com.babytracker.domain.model.FeatureSelection
import com.babytracker.domain.repository.FeatureToggleRepository
import javax.inject.Inject

class SetDomainEnabledUseCase @Inject constructor(
    private val repository: FeatureToggleRepository,
) {
    /** Returns false when the >=1 invariant rejected the change (nothing was written). */
    suspend operator fun invoke(domain: FeatureDomain, enabled: Boolean): Boolean =
        repository.applyEnabledChange { FeatureSelection.setDomain(it, domain, enabled) }
}
