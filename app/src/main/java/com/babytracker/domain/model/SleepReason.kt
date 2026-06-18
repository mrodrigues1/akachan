package com.babytracker.domain.model

/**
 * Structured, locale-agnostic explanation for a sleep-window prediction. The domain emits
 * these with raw parameters only; the UI and the partner-sync boundary resolve each variant
 * to a localized string via `SleepReason.resolve(context)` (see `util/SleepReasonText.kt`).
 * This keeps the predictor free of Android resource references per the architecture rules.
 */
sealed interface SleepReason {
    data class FullyPersonalized(val nextType: SleepType) : SleepReason
    data class Blended(val percent: Int, val nextType: SleepType) : SleepReason
    data class TypicalWakeWindow(
        val ageInWeeks: Int,
        val minMinutes: Long,
        val maxMinutes: Long,
    ) : SleepReason
    data class TypeSpecificPattern(val nextType: SleepType, val intervalCount: Int) : SleepReason
    data class CombinedHistory(val nextType: SleepType) : SleepReason
    data object Disruption : SleepReason
    data object CircadianSlot : SleepReason
    data class NapDeficit(val deficit: Int) : SleepReason
    data class SleepDebt(val earlierWindow: Boolean) : SleepReason
}
