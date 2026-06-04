package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepType

data class SegmentKey(val ageBand: Int, val sleepType: SleepType) {
    fun label(): String = "${ageBand}w+ ${sleepType.name.lowercase().replace('_', ' ')}"
}

fun ageBandFor(ageInWeeks: Int): Int = when {
    ageInWeeks < 6 -> 0
    ageInWeeks < 8 -> 6
    ageInWeeks < 12 -> 8
    ageInWeeks < 16 -> 12
    ageInWeeks < 24 -> 16
    ageInWeeks < 36 -> 24
    else -> 36
}
