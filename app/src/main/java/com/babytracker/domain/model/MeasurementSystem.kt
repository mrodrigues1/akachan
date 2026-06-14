package com.babytracker.domain.model

/**
 * User preference for how growth measurements are entered and displayed.
 * Storage stays canonical (grams / millimetres); this only affects the UI edge.
 */
enum class MeasurementSystem {
    METRIC,
    IMPERIAL,
}
