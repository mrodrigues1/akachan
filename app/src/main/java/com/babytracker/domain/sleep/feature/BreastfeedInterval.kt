package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepPredictionTuning
import java.time.Duration

data class BreastfeedInterval(
    val startMillis: Long,
    val endMillis: Long?,
) {
    companion object {
        private val maxFeedMillis = Duration.ofHours(SleepPredictionTuning.MAX_FEED_DURATION_HOURS).toMillis()

        fun from(session: BreastfeedingSession): BreastfeedInterval? {
            val startMillis = session.startTime.toEpochMilli()
            val endMillis = session.endTime?.toEpochMilli()
            if (endMillis != null && endMillis <= startMillis) return null
            if (endMillis != null && endMillis - startMillis > maxFeedMillis) return null
            return BreastfeedInterval(startMillis, endMillis)
        }
    }
}
