package com.babytracker.domain.model

/**
 * Groups [AppFeature]s for the onboarding/Settings picker. A domain with one feature renders as a
 * single switch with no expand affordance; a multi-feature domain shows a domain switch that
 * bulk-toggles its features, plus per-feature switches when expanded.
 */
enum class FeatureDomain(val title: String, val features: List<AppFeature>) {
    FEEDING(
        title = "Feeding",
        features = listOf(
            AppFeature.BREASTFEEDING,
            AppFeature.BOTTLE_FEED,
            AppFeature.PUMPING,
            AppFeature.INVENTORY,
        ),
    ),
    SLEEP("Sleep", listOf(AppFeature.SLEEP)),
    DIAPERS("Diapers", listOf(AppFeature.DIAPERS)),
    GROWTH_DEVELOPMENT(
        title = "Growth & Development",
        features = listOf(AppFeature.GROWTH, AppFeature.MILESTONES),
    ),
    ;

    val isSingleFeature: Boolean get() = features.size == 1
}
