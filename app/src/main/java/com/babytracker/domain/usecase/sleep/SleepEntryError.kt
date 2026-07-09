package com.babytracker.domain.usecase.sleep

/** Reason a proposed sleep entry is invalid. The UI layer maps each value to a localized message. */
enum class SleepEntryError {
    END_BEFORE_START,
    DURATION_TOO_LONG,
    OVERLAP,
}
