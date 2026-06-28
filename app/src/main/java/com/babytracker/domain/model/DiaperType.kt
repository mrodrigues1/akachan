package com.babytracker.domain.model

enum class DiaperType { WET, DIRTY, BOTH }

/** Parses a stored type token (`type.name`); defaults to [DiaperType.WET] on corruption. */
fun String.toDiaperTypeSafe(): DiaperType =
    DiaperType.entries.firstOrNull { it.name == this } ?: DiaperType.WET
