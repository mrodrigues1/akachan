package com.babytracker.ui.pumping

import androidx.annotation.StringRes
import com.babytracker.R
import com.babytracker.domain.model.PumpingBreast

/** Localized label resource for a [PumpingBreast]; resolve via `stringResource(breast.labelRes())`. */
@StringRes
internal fun PumpingBreast.labelRes(): Int = when (this) {
    PumpingBreast.LEFT -> R.string.side_left
    PumpingBreast.RIGHT -> R.string.side_right
    PumpingBreast.BOTH -> R.string.pumping_breast_both
}
