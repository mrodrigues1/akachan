package com.babytracker.ui.sleep

import androidx.annotation.StringRes
import com.babytracker.R
import com.babytracker.domain.model.RegressionType
import com.babytracker.domain.model.ScheduleMode

/** Localized badge label for a schedule [ScheduleMode]. */
@StringRes
internal fun ScheduleMode.labelRes(): Int = when (this) {
    ScheduleMode.DEMAND_DRIVEN -> R.string.schedule_mode_demand
    ScheduleMode.CLOCK_ALIGNED -> R.string.schedule_mode_clock
}

/** Localized name of a sleep [RegressionType]. */
@StringRes
internal fun RegressionType.nameRes(): Int = when (this) {
    RegressionType.FOUR_MONTH -> R.string.regression_4month_name
    RegressionType.EIGHT_TO_TEN_MONTH -> R.string.regression_8to10month_name
    RegressionType.TWELVE_MONTH -> R.string.regression_12month_name
}

/** Localized description of a sleep [RegressionType]. */
@StringRes
internal fun RegressionType.descriptionRes(): Int = when (this) {
    RegressionType.FOUR_MONTH -> R.string.regression_4month_desc
    RegressionType.EIGHT_TO_TEN_MONTH -> R.string.regression_8to10month_desc
    RegressionType.TWELVE_MONTH -> R.string.regression_12month_desc
}

/** Localized typical-duration of a sleep [RegressionType]. */
@StringRes
internal fun RegressionType.durationRes(): Int = when (this) {
    RegressionType.FOUR_MONTH,
    RegressionType.EIGHT_TO_TEN_MONTH -> R.string.regression_duration_2_6_weeks
    RegressionType.TWELVE_MONTH -> R.string.regression_duration_1_3_weeks
}
