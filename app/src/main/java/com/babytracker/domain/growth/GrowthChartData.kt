package com.babytracker.domain.growth

import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.model.GrowthType

/**
 * Everything the Growth screen needs to render one metric: the raw measurements,
 * the points to plot (age vs value in WHO units), the WHO reference [curves], and
 * the latest computed percentile rank.
 *
 * [curves] is empty and [latestPercentile] is null when the baby's sex is
 * unspecified (WHO curves are sex-specific) or when the latest measurement falls
 * outside the supported age range.
 */
data class GrowthChartData(
    val type: GrowthType,
    val measurements: List<GrowthMeasurement>,
    val plotted: List<GrowthPlotPoint>,
    val curves: List<WhoCurve>,
    val latestPercentile: Double?,
    val isSexSpecified: Boolean,
)

/** A measurement positioned for the chart: age on the X axis, WHO-unit value on Y. */
data class GrowthPlotPoint(
    val measurementId: Long,
    val ageMonths: Double,
    val value: Double,
)

/**
 * Orders measurements oldest-to-newest. Measurements are date-only (stored at
 * start-of-day), so multiple same-day entries share a [GrowthMeasurement.takenAt];
 * the autoincrement [GrowthMeasurement.id] breaks the tie so the most recently
 * inserted row always wins recency.
 */
val GROWTH_RECENCY: Comparator<GrowthMeasurement> =
    compareBy<GrowthMeasurement> { it.takenAt }.thenBy { it.id }

/** The most recent measurement using [GROWTH_RECENCY], or null when empty. */
fun List<GrowthMeasurement>.latestByRecency(): GrowthMeasurement? = maxWithOrNull(GROWTH_RECENCY)
