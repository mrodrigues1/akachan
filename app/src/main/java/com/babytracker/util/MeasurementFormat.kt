package com.babytracker.util

import com.babytracker.domain.model.MeasurementSystem
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Unit conversion and display formatting for growth measurements. Storage is
 * always canonical — grams for weight, millimetres for length/head-circumference
 * — so these helpers live at the UI edge and never touch persistence. Switching
 * the user's [MeasurementSystem] therefore never migrates stored data.
 */

private const val GRAMS_PER_KILOGRAM = 1000.0
private const val MILLIMETRES_PER_CENTIMETRE = 10.0
private const val GRAMS_PER_OUNCE = 28.349523125
private const val OUNCES_PER_POUND = 16
private const val MILLIMETRES_PER_INCH = 25.4

// ---------------------------------------------------------------------------
// Weight (canonical = grams)
// ---------------------------------------------------------------------------

fun gramsToKilograms(grams: Long): Double = grams / GRAMS_PER_KILOGRAM

fun kilogramsToGrams(kilograms: Double): Long = (kilograms * GRAMS_PER_KILOGRAM).roundToLong()

/**
 * Splits grams into whole pounds and whole ounces, carrying at 16 oz so a value
 * that rounds up to a full pound is never shown as "N lb 16 oz".
 */
fun gramsToPoundsOunces(grams: Long): Pair<Int, Int> {
    val totalOunces = (grams / GRAMS_PER_OUNCE).roundToInt()
    return (totalOunces / OUNCES_PER_POUND) to (totalOunces % OUNCES_PER_POUND)
}

fun poundsOuncesToGrams(pounds: Int, ounces: Int): Long =
    ((pounds * OUNCES_PER_POUND + ounces) * GRAMS_PER_OUNCE).roundToLong()

fun formatWeight(grams: Long, system: MeasurementSystem): String = when (system) {
    MeasurementSystem.METRIC -> String.format(Locale.US, "%.2f kg", gramsToKilograms(grams))
    MeasurementSystem.IMPERIAL -> {
        val (pounds, ounces) = gramsToPoundsOunces(grams)
        "$pounds lb $ounces oz"
    }
}

// ---------------------------------------------------------------------------
// Length / head circumference (canonical = millimetres)
// ---------------------------------------------------------------------------

fun millimetresToCentimetres(millimetres: Long): Double = millimetres / MILLIMETRES_PER_CENTIMETRE

fun centimetresToMillimetres(centimetres: Double): Long =
    (centimetres * MILLIMETRES_PER_CENTIMETRE).roundToLong()

fun millimetresToInches(millimetres: Long): Double = millimetres / MILLIMETRES_PER_INCH

fun inchesToMillimetres(inches: Double): Long = (inches * MILLIMETRES_PER_INCH).roundToLong()

fun formatLength(millimetres: Long, system: MeasurementSystem): String = when (system) {
    MeasurementSystem.METRIC ->
        String.format(Locale.US, "%.1f cm", millimetresToCentimetres(millimetres))
    MeasurementSystem.IMPERIAL ->
        String.format(Locale.US, "%.1f in", millimetresToInches(millimetres))
}

/** Short unit label for an input field, e.g. "kg" / "lb" or "cm" / "in". */
fun weightUnitLabel(system: MeasurementSystem): String =
    if (system == MeasurementSystem.METRIC) "kg" else "lb"

fun lengthUnitLabel(system: MeasurementSystem): String =
    if (system == MeasurementSystem.METRIC) "cm" else "in"
