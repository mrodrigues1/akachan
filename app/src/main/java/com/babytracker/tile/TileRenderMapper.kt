package com.babytracker.tile

import android.os.Build
import android.service.quicksettings.Tile

internal fun TileAvailability.toQsTileState(): Int =
    when (this) {
        TileAvailability.ACTIVE -> Tile.STATE_ACTIVE
        TileAvailability.INACTIVE -> Tile.STATE_INACTIVE
        TileAvailability.UNAVAILABLE -> Tile.STATE_UNAVAILABLE
    }

internal fun tileSubtitleForSdk(state: TileRenderState, sdkInt: Int = Build.VERSION.SDK_INT): String? =
    if (sdkInt >= Build.VERSION_CODES.Q) state.subtitle else null
