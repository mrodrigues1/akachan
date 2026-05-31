package com.babytracker.tile

enum class TileAvailability {
    ACTIVE,
    INACTIVE,
    UNAVAILABLE,
}

data class TileRenderState(
    val availability: TileAvailability,
    val label: String,
    val subtitle: String?,
)
