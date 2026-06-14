package com.babytracker.domain.growth

import kotlinx.serialization.Serializable

/**
 * One row of a WHO Child Growth Standards LMS table.
 *
 * The LMS method models a skewed distribution at each age with three parameters:
 * - [l] (lambda) — Box-Cox power that removes skewness
 * - [m] (mu) — the median (50th percentile) measurement at this age
 * - [s] (sigma) — coefficient of variation
 *
 * @param ageMonths age in completed months (0..24)
 * @param m median in the metric's canonical display unit (kg for weight, cm for
 *   length/head-circumference — matching the WHO published tables)
 */
@Serializable
data class LmsPoint(
    val ageMonths: Int,
    val l: Double,
    val m: Double,
    val s: Double,
)
