package com.babytracker.util

import com.babytracker.domain.model.VolumeUnit
import java.util.Locale
import kotlin.math.roundToInt

private const val ML_PER_US_FL_OZ = 29.5735

fun mlToOz(volumeMl: Int): Double = volumeMl / ML_PER_US_FL_OZ

/**
 * Formats a millilitre volume for display in the user's chosen [unit].
 * ML -> "120 ml". OZ -> one decimal, trailing ".0" dropped ("4.1 oz", "10 oz").
 */
fun formatVolume(volumeMl: Int, unit: VolumeUnit): String = when (unit) {
    VolumeUnit.ML -> "$volumeMl ml"
    VolumeUnit.OZ -> {
        val oz = mlToOz(volumeMl)
        val rounded = (oz * 10).roundToInt() / 10.0
        val text = if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
        "$text oz"
    }
}
