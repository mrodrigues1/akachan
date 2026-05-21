package com.babytracker.ui.breastfeeding

import com.babytracker.domain.model.FeedPrediction
import com.babytracker.util.formatTime
import kotlin.math.abs

object PredictionCopy {

    data class Subtitle(
        val primary: String,
        val secondary: String? = null,
        val lowConfidence: Boolean = false,
        val contentDescription: String,
    )

    private const val OVERDUE_NOW_MIN_MINUTES = 5
    private const val UPCOMING_DETAIL_THRESHOLD_MINUTES = 30
    private const val LOW_CONFIDENCE_SAMPLE_SIZE = 3

    fun forPrediction(prediction: FeedPrediction): Subtitle {
        val timeLabel = prediction.predictedAt.formatTime()
        val lowConfidence = prediction.sampleSize == LOW_CONFIDENCE_SAMPLE_SIZE
        val confidenceCd = if (lowConfidence) ", low confidence" else ""
        val basisCd = ", based on ${prediction.sampleSize} recent feeds"

        return when {
            prediction.isOverdue && abs(prediction.minutesUntil) >= OVERDUE_NOW_MIN_MINUTES -> {
                val agoMinutes = abs(prediction.minutesUntil)
                Subtitle(
                    primary = "Likely hungry now · ~${agoMinutes}m ago",
                    lowConfidence = lowConfidence,
                    contentDescription = "Likely hungry now, about $agoMinutes minutes ago$confidenceCd$basisCd",
                )
            }
            !prediction.isOverdue && prediction.minutesUntil <= UPCOMING_DETAIL_THRESHOLD_MINUTES -> {
                Subtitle(
                    primary = "Likely hungry around $timeLabel",
                    secondary = "in ~${prediction.minutesUntil}m",
                    lowConfidence = lowConfidence,
                    contentDescription = "Likely hungry around $timeLabel, in about ${prediction.minutesUntil} minutes$confidenceCd$basisCd",
                )
            }
            else -> Subtitle(
                primary = "Likely hungry around $timeLabel",
                lowConfidence = lowConfidence,
                contentDescription = "Likely hungry around $timeLabel$confidenceCd$basisCd",
            )
        }
    }
}
