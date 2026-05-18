package com.babytracker.domain.model

enum class PumpingBreast { LEFT, RIGHT, BOTH }

fun PumpingBreast.displayName(): String = when (this) {
    PumpingBreast.LEFT -> "Left"
    PumpingBreast.RIGHT -> "Right"
    PumpingBreast.BOTH -> "Both"
}
