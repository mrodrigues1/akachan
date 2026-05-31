package com.babytracker.tile

fun shouldOpenAppOnTap(
    isSecure: Boolean,
    isLocked: Boolean,
    state: TileAvailability,
): TileTapAction =
    if (state == TileAvailability.UNAVAILABLE || (isSecure && isLocked)) {
        TileTapAction.OPEN_APP
    } else {
        TileTapAction.TOGGLE_SILENTLY
    }
