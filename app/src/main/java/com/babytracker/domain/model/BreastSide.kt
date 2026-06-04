package com.babytracker.domain.model

enum class BreastSide {
    LEFT, RIGHT
}

fun BreastSide.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }
