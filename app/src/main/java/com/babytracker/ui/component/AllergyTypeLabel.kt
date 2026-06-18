package com.babytracker.ui.component

import androidx.annotation.StringRes
import com.babytracker.R
import com.babytracker.domain.model.AllergyType

/**
 * Localized display label for an [AllergyType]. The domain enum stays locale-agnostic (its
 * `name` is the persistence/sync token); the UI resolves display text via this resource.
 */
@StringRes
fun AllergyType.labelRes(): Int = when (this) {
    AllergyType.CMPA -> R.string.allergy_cmpa
    AllergyType.SOY -> R.string.allergy_soy
    AllergyType.EGG -> R.string.allergy_egg
    AllergyType.WHEAT -> R.string.allergy_wheat
    AllergyType.PEANUT -> R.string.allergy_peanut
    AllergyType.TREE_NUTS -> R.string.allergy_tree_nuts
    AllergyType.OTHER -> R.string.allergy_other
}
