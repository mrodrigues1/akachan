package com.babytracker.domain.model

/**
 * The three growth metrics tracked against WHO Child Growth Standards.
 * Canonical storage units differ per type (see [GrowthMeasurement.valueCanonical]).
 */
enum class GrowthType {
    WEIGHT,
    LENGTH,
    HEAD_CIRC,
}
