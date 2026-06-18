package com.babytracker.ui.breastfeeding

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.babytracker.R
import com.babytracker.domain.model.FeedPrediction
import kotlin.math.abs

/**
 * Locale-agnostic feed-prediction copy. [forPrediction] classifies a [FeedPrediction] into a
 * [Subtitle] carrying only raw values; the composable resolvers below turn it into displayable
 * strings via [stringResource]. This keeps the classification logic unit-testable without a
 * Context and the copy fully translatable.
 */
object PredictionCopy {

    enum class Kind { OVERDUE_NOW, UPCOMING_DETAIL, UPCOMING }

    data class Subtitle(
        val kind: Kind,
        val sampleSize: Int,
        val lowConfidence: Boolean,
        val agoMinutes: Int = 0,
        val minutesUntil: Int = 0,
    )

    private const val OVERDUE_NOW_MIN_MINUTES = 5
    private const val UPCOMING_DETAIL_THRESHOLD_MINUTES = 30
    private const val LOW_CONFIDENCE_SAMPLE_SIZE = 3

    fun forPrediction(prediction: FeedPrediction): Subtitle {
        val lowConfidence = prediction.sampleSize == LOW_CONFIDENCE_SAMPLE_SIZE
        return when {
            prediction.isOverdue && abs(prediction.minutesUntil) >= OVERDUE_NOW_MIN_MINUTES ->
                Subtitle(
                    kind = Kind.OVERDUE_NOW,
                    sampleSize = prediction.sampleSize,
                    lowConfidence = lowConfidence,
                    agoMinutes = abs(prediction.minutesUntil),
                )
            !prediction.isOverdue && prediction.minutesUntil <= UPCOMING_DETAIL_THRESHOLD_MINUTES ->
                Subtitle(
                    kind = Kind.UPCOMING_DETAIL,
                    sampleSize = prediction.sampleSize,
                    lowConfidence = lowConfidence,
                    minutesUntil = prediction.minutesUntil,
                )
            else ->
                Subtitle(
                    kind = Kind.UPCOMING,
                    sampleSize = prediction.sampleSize,
                    lowConfidence = lowConfidence,
                )
        }
    }
}

/** Primary line, e.g. "Likely hungry now · ~7m ago" or "Likely hungry around 5:40 PM". */
@Composable
internal fun PredictionCopy.Subtitle.primaryText(timeLabel: String): String =
    when (kind) {
        PredictionCopy.Kind.OVERDUE_NOW ->
            stringResource(R.string.breastfeeding_predict_hungry_now, agoMinutes)
        else ->
            stringResource(R.string.breastfeeding_predict_hungry_around, timeLabel)
    }

/** Secondary/detail line combining "in ~Xm" and the low-confidence marker; empty when neither. */
@Composable
internal fun PredictionCopy.Subtitle.detailText(): String {
    val secondary = if (kind == PredictionCopy.Kind.UPCOMING_DETAIL) {
        stringResource(R.string.breastfeeding_predict_in_minutes, minutesUntil)
    } else {
        null
    }
    return when {
        secondary != null && lowConfidence ->
            stringResource(R.string.breastfeeding_predict_detail_combined, secondary)
        secondary != null -> secondary
        lowConfidence -> stringResource(R.string.breastfeeding_predict_low_confidence)
        else -> ""
    }
}

/** Full accessibility description: base phrase + optional low-confidence + recent-feeds basis. */
@Composable
internal fun PredictionCopy.Subtitle.contentDescriptionText(timeLabel: String): String {
    val base = when (kind) {
        PredictionCopy.Kind.OVERDUE_NOW ->
            stringResource(R.string.breastfeeding_predict_cd_now, agoMinutes)
        PredictionCopy.Kind.UPCOMING_DETAIL ->
            stringResource(R.string.breastfeeding_predict_cd_around_detail, timeLabel, minutesUntil)
        PredictionCopy.Kind.UPCOMING ->
            stringResource(R.string.breastfeeding_predict_cd_around, timeLabel)
    }
    val withConfidence = if (lowConfidence) {
        stringResource(R.string.breastfeeding_predict_cd_low_confidence, base)
    } else {
        base
    }
    return stringResource(R.string.breastfeeding_predict_cd_basis, withConfidence, sampleSize)
}
