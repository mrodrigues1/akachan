package com.babytracker.domain.model

enum class DiaperType(val label: String, val emoji: String) {
    WET("Wet", "💧"),
    DIRTY("Dirty", "💩"),
    BOTH("Both", "🌀"),
}

fun String.toDiaperTypeOrNull(): DiaperType? = when (this) {
    DiaperType.WET.name, DiaperType.WET.label -> DiaperType.WET
    DiaperType.DIRTY.name, DiaperType.DIRTY.label -> DiaperType.DIRTY
    DiaperType.BOTH.name, DiaperType.BOTH.label -> DiaperType.BOTH
    else -> null
}

fun String.toDiaperTypeSafe(): DiaperType = toDiaperTypeOrNull() ?: DiaperType.WET
