package com.babytracker.domain.model

// Display text lives at the UI edge (see ui/sleep/SleepTypeLabel.kt) so this stays a pure parsing key.
enum class SleepType {
    NAP,
    NIGHT_SLEEP,
}

fun String.toSleepTypeOrNull(): SleepType? = SleepType.entries.find { it.name == this }

// Backups exported (or pre-migration Room rows written) before the display label was removed from
// this enum may still carry "Nap"/"Night Sleep" instead of the enum name — Firestore partner sync
// always wrote SleepType.name (see DomainToSnapshot.kt), so it never produced these. This
// canonicalizes only those two known legacy values; anything else still returns null.
fun String.toSleepTypeWithLegacyLabelOrNull(): SleepType? = toSleepTypeOrNull() ?: when (this) {
    "Nap" -> SleepType.NAP
    "Night Sleep" -> SleepType.NIGHT_SLEEP
    else -> null
}

fun String.toSleepTypeSafe(): SleepType = toSleepTypeWithLegacyLabelOrNull() ?: SleepType.NAP
