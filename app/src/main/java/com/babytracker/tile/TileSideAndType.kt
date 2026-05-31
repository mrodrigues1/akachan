package com.babytracker.tile

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepType
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

fun alternateSide(last: BreastfeedingSession?): BreastSide =
    when (last?.finalSide()) {
        BreastSide.LEFT -> BreastSide.RIGHT
        BreastSide.RIGHT -> BreastSide.LEFT
        null -> BreastSide.LEFT
    }

fun BreastfeedingSession.finalSide(): BreastSide =
    if (switchTime == null) {
        startingSide
    } else {
        when (startingSide) {
            BreastSide.LEFT -> BreastSide.RIGHT
            BreastSide.RIGHT -> BreastSide.LEFT
        }
    }

fun sleepTypeFor(now: Instant, zoneId: ZoneId): SleepType {
    val local = now.atZone(zoneId).toLocalTime()
    return if (local >= LocalTime.of(19, 0) || local < LocalTime.of(6, 0)) {
        SleepType.NIGHT_SLEEP
    } else {
        SleepType.NAP
    }
}
