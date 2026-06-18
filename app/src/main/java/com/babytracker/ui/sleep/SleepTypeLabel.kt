package com.babytracker.ui.sleep

import androidx.annotation.StringRes
import com.babytracker.R
import com.babytracker.domain.model.SleepType

/**
 * Localized label resource for a [SleepType]. The domain `SleepType.label` stays the
 * persistence/parsing key (see `toSleepTypeOrNull`); UI display resolves via this resource.
 */
@StringRes
internal fun SleepType.labelRes(): Int = when (this) {
    SleepType.NAP -> R.string.sleep_type_nap
    SleepType.NIGHT_SLEEP -> R.string.sleep_type_night
}
