package com.babytracker.sharing.usecase

import com.babytracker.domain.model.GrowthMeasurement

private const val GROWTH_PER_TYPE_LIMIT = 10

/**
 * Selects the growth measurements to share. A single global limit could drop an
 * entire metric (e.g. many recent weight entries pushing length/head out of the
 * window), so this caps per [GrowthMeasurement.type] instead — guaranteeing each
 * type's latest value reaches the partner snapshot.
 */
fun List<GrowthMeasurement>.latestPerType(): List<GrowthMeasurement> =
    groupBy { it.type }
        .flatMap { (_, measurements) ->
            measurements.sortedByDescending { it.takenAt }.take(GROWTH_PER_TYPE_LIMIT)
        }
