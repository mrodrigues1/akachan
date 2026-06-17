package com.babytracker.ui.common

import androidx.compose.ui.graphics.Color

/**
 * Optional section accent for [DateTimeFieldRow]. When supplied, the label and the
 * date/time picker dialogs are re-themed to this accent instead of the global M3 primary,
 * so a section with its own palette (e.g. Diapers) keeps its colour through the pickers.
 *
 * [accent]/[onAccent] drive the bright slots (selected clock number, hand, AM/PM, buttons);
 * [container]/[onContainer] drive the softer selected hour/minute field box.
 */
data class FieldAccent(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
)
