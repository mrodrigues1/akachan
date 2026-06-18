package com.babytracker.ui.component

import androidx.annotation.StringRes
import com.babytracker.R
import com.babytracker.domain.model.BreastSide

/**
 * Localized label resource for a [BreastSide]. UI layer resolves it via
 * `stringResource(side.labelRes())`; the domain `displayName()` stays locale-agnostic.
 */
@StringRes
internal fun BreastSide.labelRes(): Int = when (this) {
    BreastSide.LEFT -> R.string.side_left
    BreastSide.RIGHT -> R.string.side_right
}
