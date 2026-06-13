package com.babytracker.ui.bottlefeed

/** Shown when the volume field is empty or not a positive number. */
const val BOTTLE_FEED_VOLUME_ERROR = "Enter a volume greater than 0"

/** Validated bottle feed form input shared by the owner and partner entry screens. */
data class BottleFeedInput(
    val volumeMl: Int,
    val notes: String?,
)

/**
 * Parses and validates the bottle feed form fields.
 *
 * @return the validated input, or null when the volume is missing or not positive.
 */
fun BottleFeedUiState.parseBottleFeedInput(): BottleFeedInput? {
    val volume = volumeText.toIntOrNull()?.takeIf { it > 0 } ?: return null
    return BottleFeedInput(volumeMl = volume, notes = notes.trim().ifBlank { null })
}
