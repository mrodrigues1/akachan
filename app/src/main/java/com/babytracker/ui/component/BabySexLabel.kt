package com.babytracker.ui.component

import androidx.annotation.StringRes
import com.babytracker.R
import com.babytracker.domain.model.BabySex

/**
 * Localized display label for a [BabySex]. The domain enum stays locale-agnostic; the UI
 * resolves it via `stringResource(sex.labelRes())`.
 */
@StringRes
fun BabySex.labelRes(): Int = when (this) {
    BabySex.MALE -> R.string.baby_sex_male
    BabySex.FEMALE -> R.string.baby_sex_female
    BabySex.UNSPECIFIED -> R.string.baby_sex_unspecified
}
