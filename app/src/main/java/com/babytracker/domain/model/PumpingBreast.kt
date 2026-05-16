package com.babytracker.domain.model

enum class PumpingBreast { LEFT, RIGHT, BOTH }

fun PumpingBreast.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }
