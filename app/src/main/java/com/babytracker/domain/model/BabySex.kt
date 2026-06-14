package com.babytracker.domain.model

/**
 * Biological sex of the baby. Required because WHO growth-standard percentile
 * curves are sex-specific. [UNSPECIFIED] is the safe default for existing users
 * (no value stored) — measurements can still be logged, but sex-dependent
 * percentile ranks are suppressed until a sex is chosen.
 */
enum class BabySex(val label: String) {
    MALE("Boy"),
    FEMALE("Girl"),
    UNSPECIFIED("Prefer not to say"),
}
