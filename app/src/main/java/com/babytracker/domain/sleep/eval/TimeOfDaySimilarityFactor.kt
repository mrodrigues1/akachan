package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepType
import com.babytracker.domain.sleep.feature.SleepMetrics

object TimeOfDaySimilarityFactor {

    fun adjustment(
        metrics: SleepMetrics,
        nextType: SleepType,
        candidateMinuteOfDay: Int?,
        hasQualifiedTimezoneProvenance: Boolean,
    ): SleepPredictionFactor {
        if (!hasQualifiedTimezoneProvenance) return SleepPredictionFactor.Neutral

        val targetMinute = when (nextType) {
            SleepType.NIGHT_SLEEP -> metrics.medianBedtimeMinuteOfDay
            SleepType.NAP -> null
        } ?: return SleepPredictionFactor.Neutral

        // Phase 3 replaces this neutral return with a bounded local-time adjustment once
        // per-record timezone provenance is stored. Parameters are kept to avoid API churn.
        return if (candidateMinuteOfDay == null || candidateMinuteOfDay == targetMinute) {
            SleepPredictionFactor.Neutral
        } else {
            SleepPredictionFactor.Neutral
        }
    }
}
