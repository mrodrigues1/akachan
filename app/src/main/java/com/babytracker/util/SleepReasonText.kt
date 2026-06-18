package com.babytracker.util

import android.content.Context
import com.babytracker.R
import com.babytracker.domain.model.SleepReason
import com.babytracker.domain.model.SleepType

/**
 * Resolves a locale-agnostic [SleepReason] to a localized string. Used both by the sleep UI
 * (via `LocalContext.current`) and by the partner-sync boundary, where reasons are flattened
 * to strings in the sharer's locale before being written to Firestore.
 */
fun SleepReason.resolve(context: Context): String = when (this) {
    is SleepReason.FullyPersonalized -> context.getString(
        if (nextType == SleepType.NIGHT_SLEEP) {
            R.string.sleep_reason_personalized_night
        } else {
            R.string.sleep_reason_personalized_nap
        },
    )
    is SleepReason.Blended -> context.getString(
        if (nextType == SleepType.NIGHT_SLEEP) {
            R.string.sleep_reason_blended_night
        } else {
            R.string.sleep_reason_blended_nap
        },
        percent,
    )
    is SleepReason.TypicalWakeWindow -> context.getString(
        R.string.sleep_reason_wake_window,
        ageInWeeks,
        minMinutes,
        maxMinutes,
    )
    is SleepReason.TypeSpecificPattern -> context.getString(
        if (nextType == SleepType.NIGHT_SLEEP) {
            R.string.sleep_reason_type_pattern_night
        } else {
            R.string.sleep_reason_type_pattern_nap
        },
        intervalCount,
    )
    is SleepReason.CombinedHistory -> context.getString(
        if (nextType == SleepType.NIGHT_SLEEP) {
            R.string.sleep_reason_combined_night
        } else {
            R.string.sleep_reason_combined_nap
        },
    )
    SleepReason.Disruption -> context.getString(R.string.sleep_reason_disruption)
    SleepReason.CircadianSlot -> context.getString(R.string.sleep_reason_circadian)
    is SleepReason.NapDeficit -> context.resources.getQuantityString(
        R.plurals.sleep_reason_nap_deficit,
        deficit,
        deficit,
    )
    is SleepReason.SleepDebt -> context.getString(
        if (earlierWindow) {
            R.string.sleep_reason_sleep_debt_earlier
        } else {
            R.string.sleep_reason_sleep_debt_later
        },
    )
}
