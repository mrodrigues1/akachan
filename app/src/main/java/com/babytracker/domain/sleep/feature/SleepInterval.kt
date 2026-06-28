package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import java.time.Duration

data class SleepInterval(
    val startMillis: Long,
    val endMillis: Long?,
    val sleepType: SleepType,
    val timezoneId: String? = null,
) {
    val isCompleted: Boolean
        get() = endMillis != null

    companion object {
        private val maxNapMillis = Duration.ofHours(SleepPredictionTuning.MAX_NAP_DURATION_HOURS).toMillis()
        private val maxNightSleepMillis =
            Duration.ofHours(SleepPredictionTuning.MAX_NIGHT_SLEEP_DURATION_HOURS).toMillis()

        fun from(record: SleepRecord): SleepInterval? =
            from(
                startMillis = record.startTime.toEpochMilli(),
                endMillis = record.endTime?.toEpochMilli(),
                sleepType = record.sleepType,
                timezoneId = record.timezoneId,
            )

        fun from(
            startMillis: Long,
            endMillis: Long?,
            sleepType: SleepType,
            timezoneId: String? = null,
        ): SleepInterval? {
            if (endMillis != null) {
                if (endMillis <= startMillis) return null
                if (sleepType == SleepType.NAP && endMillis - startMillis > maxNapMillis) return null
                if (sleepType == SleepType.NIGHT_SLEEP && endMillis - startMillis > maxNightSleepMillis) return null
            }
            return SleepInterval(startMillis, endMillis, sleepType, timezoneId)
        }
    }
}
