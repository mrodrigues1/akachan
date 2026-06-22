package com.babytracker.domain.sleep

/**
 * Median of [values]; null when empty. Odd-sized lists return the middle element; even-sized lists
 * average the two middle values with integer truncation. Shared by the sleep feature extractor and
 * window predictor.
 */
internal fun median(values: List<Long>): Long? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[middle]
    } else {
        (sorted[middle - 1] + sorted[middle]) / 2
    }
}
