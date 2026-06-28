package com.babytracker.ui.component

import androidx.annotation.StringRes
import com.babytracker.R
import com.babytracker.domain.model.DiaperType

/**
 * Localized label resource for a [DiaperType]. UI layer resolves it via
 * `stringResource(type.labelRes())`. Persistence/serialization uses the enum
 * `name` (see `toDiaperTypeSafe`), not this label.
 */
@StringRes
internal fun DiaperType.labelRes(): Int = when (this) {
    DiaperType.WET -> R.string.diaper_type_wet
    DiaperType.DIRTY -> R.string.diaper_type_dirty
    DiaperType.BOTH -> R.string.diaper_type_both
}
