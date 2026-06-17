package com.babytracker.domain.model

/**
 * Pure transitions over the enabled-feature set, enforcing the "always >=1 enabled" invariant.
 * Shared by the onboarding picker (local state) and the Settings use cases (persisted state).
 * A transition that would leave the set empty is rejected by returning [current] unchanged.
 */
object FeatureSelection {

    fun setFeature(current: Set<AppFeature>, feature: AppFeature, enabled: Boolean): Set<AppFeature> {
        val next = if (enabled) current + feature else current - feature
        return next.ifEmpty { current }
    }

    fun setDomain(current: Set<AppFeature>, domain: FeatureDomain, enabled: Boolean): Set<AppFeature> {
        val next = if (enabled) current + domain.features else current - domain.features.toSet()
        return next.ifEmpty { current }
    }
}
