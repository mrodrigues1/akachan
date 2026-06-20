package com.babytracker.domain.model

enum class SleepType(val label: String, val emoji: String) {
    NAP("Nap", "😴"),
    NIGHT_SLEEP("Night Sleep", ""),
}

fun String.toSleepTypeOrNull(): SleepType? = when (this) {
    SleepType.NAP.name, SleepType.NAP.label -> SleepType.NAP
    SleepType.NIGHT_SLEEP.name, SleepType.NIGHT_SLEEP.label -> SleepType.NIGHT_SLEEP
    else -> null
}

fun String.toSleepTypeSafe(): SleepType = toSleepTypeOrNull() ?: SleepType.NAP
