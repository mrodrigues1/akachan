package com.babytracker.domain.usecase.pumping

/** Reason a proposed pumping edit is invalid. The UI layer maps each value to a localized message. */
enum class PumpingEditError {
    START_IN_FUTURE,
    END_BEFORE_START,
    END_IN_FUTURE,
    PAUSE_EXCEEDS_SESSION,
    VOLUME_NOT_POSITIVE,
}
