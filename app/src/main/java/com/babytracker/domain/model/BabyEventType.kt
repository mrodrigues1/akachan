package com.babytracker.domain.model

enum class BabyEventType {
    SLEEPY_CUE,
    HUNGER_CUE,
    FUSSY,
    SICK,
    TEETHING,
    TRAVEL;

    val isDisruption: Boolean
        get() = this == SICK || this == TEETHING || this == TRAVEL
}
