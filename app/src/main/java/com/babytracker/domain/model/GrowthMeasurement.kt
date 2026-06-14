package com.babytracker.domain.model

import java.time.Instant

/**
 * A single growth measurement for the baby.
 *
 * [valueCanonical] is always stored in the canonical unit for its [type]:
 * - [GrowthType.WEIGHT] → grams
 * - [GrowthType.LENGTH] / [GrowthType.HEAD_CIRC] → millimetres
 *
 * Unit conversion and display formatting happen at the UI edge so storage stays
 * stable regardless of the user's metric/imperial preference.
 */
data class GrowthMeasurement(
    val id: Long = 0,
    val takenAt: Instant,
    val type: GrowthType,
    val valueCanonical: Long,
    val notes: String? = null,
)
