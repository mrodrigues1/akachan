package com.babytracker.ui.component

import androidx.annotation.StringRes
import com.babytracker.R
import com.babytracker.domain.model.DiaperType

/**
 * Localized label resource for a [DiaperType]. UI layer resolves it via
 * `stringResource(type.labelRes())`; the domain `label` stays locale-agnostic
 * because it doubles as the persistence/serialization token.
 */
@StringRes
internal fun DiaperType.labelRes(): Int = when (this) {
    DiaperType.WET -> R.string.diaper_type_wet
    DiaperType.DIRTY -> R.string.diaper_type_dirty
    DiaperType.BOTH -> R.string.diaper_type_both
}
