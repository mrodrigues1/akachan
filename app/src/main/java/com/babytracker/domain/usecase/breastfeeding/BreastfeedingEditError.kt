package com.babytracker.domain.usecase.breastfeeding

/** Reason a proposed breastfeeding edit is invalid. The UI layer maps each value to a localized message. */
enum class BreastfeedingEditError {
    START_IN_FUTURE,
    END_IN_FUTURE,
    END_BEFORE_START,
    SESSION_SHORTER_THAN_PAUSES,
}
