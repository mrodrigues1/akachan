package com.babytracker.domain.growth

import com.babytracker.domain.model.GrowthType
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure-Kotlin implementation of the WHO LMS percentile method. Zero framework
 * imports — fully JVM-unit-testable.
 *
 * Given a measurement and the LMS parameters for the child's age, computes the
 * standard-normal z-score and the corresponding percentile rank. Also produces
 * the standard P3/P15/P50/P85/P97 reference curves for charting.
 */
object WhoPercentileCalculator {

    /** Percentiles charted as reference curves, paired with their normal z-scores. */
    val STANDARD_PERCENTILE_Z: Map<Int, Double> = mapOf(
        3 to -1.8807936,
        15 to -1.0364334,
        50 to 0.0,
        85 to 1.0364334,
        97 to 1.8807936,
    )

    /**
     * z-score for [value] given LMS parameters. Uses the logarithmic special
     * case when [l] is zero (the LMS formula is undefined there).
     */
    fun zScore(value: Double, l: Double, m: Double, s: Double): Double =
        if (l == 0.0) {
            ln(value / m) / s
        } else {
            ((value / m).pow(l) - 1.0) / (l * s)
        }

    /** Inverts [zScore]: the measurement at a given z-score for these LMS params. */
    fun valueForZ(z: Double, l: Double, m: Double, s: Double): Double =
        if (l == 0.0) {
            m * exp(s * z)
        } else {
            m * (1.0 + l * s * z).pow(1.0 / l)
        }

    /** Percentile rank (0..100) for a standard-normal [z]. */
    fun percentileForZ(z: Double): Double = standardNormalCdf(z) * 100.0

    /**
     * Linearly interpolates the LMS parameters at a fractional [ageMonths].
     *
     * Returns null when [points] is empty or when [ageMonths] falls outside the
     * table's covered range. The tables stop at 24 months, so an older child is
     * reported as "unsupported" rather than being silently compared against
     * 24-month data — a deceptively precise health metric would be worse than
     * showing nothing.
     */
    fun lmsAt(ageMonths: Double, points: List<LmsPoint>): LmsPoint? {
        val sorted = points.sortedBy { it.ageMonths }
        val first = sorted.firstOrNull() ?: return null
        val last = sorted.last()
        if (ageMonths < first.ageMonths || ageMonths > last.ageMonths) return null

        val upperIndex = sorted.indexOfFirst { it.ageMonths >= ageMonths }
        val upper = sorted[upperIndex]
        val exactOrFirst = upperIndex == 0 || upper.ageMonths.toDouble() == ageMonths
        if (exactOrFirst) return upper

        val lower = sorted[upperIndex - 1]
        val span = (upper.ageMonths - lower.ageMonths).toDouble()
        val t = (ageMonths - lower.ageMonths) / span
        return LmsPoint(
            ageMonths = ageMonths.toInt(),
            l = lerp(lower.l, upper.l, t),
            m = lerp(lower.m, upper.m, t),
            s = lerp(lower.s, upper.s, t),
        )
    }

    /**
     * Percentile rank (0..100) of [value] at [ageMonths] against [points], or
     * null if the table is empty.
     *
     * @param value measurement in the table's unit (kg for weight, cm for
     *   length/head-circumference).
     */
    fun percentileFor(value: Double, ageMonths: Double, points: List<LmsPoint>): Double? {
        val lms = lmsAt(ageMonths, points) ?: return null
        return percentileForZ(zScore(value, lms.l, lms.m, lms.s))
    }

    /**
     * Converts a canonically-stored measurement (grams for weight, millimetres
     * for length/head-circumference) into the WHO table's unit (kg / cm).
     */
    fun canonicalToWhoUnit(type: GrowthType, valueCanonical: Long): Double = when (type) {
        GrowthType.WEIGHT -> valueCanonical / GRAMS_PER_KILOGRAM
        GrowthType.LENGTH, GrowthType.HEAD_CIRC -> valueCanonical / MILLIMETRES_PER_CENTIMETRE
    }

    /**
     * Percentile rank (0..100) for an app-stored measurement. Takes the canonical
     * value (grams/millimetres) and converts to the WHO unit internally, so
     * callers cannot accidentally feed grams where kilograms are expected.
     * Returns null when the age is unsupported or the table is empty.
     */
    fun percentileForCanonical(
        type: GrowthType,
        valueCanonical: Long,
        ageMonths: Double,
        points: List<LmsPoint>,
    ): Double? = percentileFor(canonicalToWhoUnit(type, valueCanonical), ageMonths, points)

    /**
     * Reference curves for charting. For each entry in [STANDARD_PERCENTILE_Z]
     * returns the measurement value at every age row in [points].
     */
    fun curves(points: List<LmsPoint>): List<WhoCurve> {
        val sorted = points.sortedBy { it.ageMonths }
        return STANDARD_PERCENTILE_Z.entries
            .sortedBy { it.key }
            .map { (percentile, z) ->
                WhoCurve(
                    percentile = percentile,
                    points = sorted.map { lms ->
                        WhoCurvePoint(
                            ageMonths = lms.ageMonths,
                            value = valueForZ(z, lms.l, lms.m, lms.s),
                        )
                    },
                )
            }
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    /**
     * Standard normal CDF via the Abramowitz & Stegun 7.1.26 erf approximation
     * (absolute error < 1.5e-7) — accurate enough for percentile display.
     */
    private fun standardNormalCdf(z: Double): Double = 0.5 * (1.0 + erf(z / SQRT2))

    private fun erf(x: Double): Double {
        val t = 1.0 / (1.0 + ERF_P * abs(x))
        val poly = ((((ERF_A5 * t + ERF_A4) * t + ERF_A3) * t + ERF_A2) * t + ERF_A1) * t
        val y = 1.0 - poly * exp(-x * x)
        return if (x >= 0.0) y else -y
    }

    private const val GRAMS_PER_KILOGRAM = 1000.0
    private const val MILLIMETRES_PER_CENTIMETRE = 10.0

    private val SQRT2 = sqrt(2.0)
    private const val ERF_P = 0.3275911
    private const val ERF_A1 = 0.254829592
    private const val ERF_A2 = -0.284496736
    private const val ERF_A3 = 1.421413741
    private const val ERF_A4 = -1.453152027
    private const val ERF_A5 = 1.061405429
}

/** One WHO reference percentile curve (e.g. P50) across ages. */
data class WhoCurve(
    val percentile: Int,
    val points: List<WhoCurvePoint>,
)

data class WhoCurvePoint(
    val ageMonths: Int,
    val value: Double,
)
