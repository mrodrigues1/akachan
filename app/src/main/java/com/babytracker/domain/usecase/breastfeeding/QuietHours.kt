package com.babytracker.domain.usecase.breastfeeding

import java.time.Instant
import java.time.ZoneId

/**
 * True when [endpoint]'s local minute-of-day falls inside the daily quiet-hours window
 * [startMinute, endMinute), handling windows that wrap past midnight. Returns false when the
 * window is empty (start == end). Shared by the breastfeeding interval/prediction use cases.
 */
internal fun isEndpointInQuietHours(
    endpoint: Instant,
    zoneId: ZoneId,
    startMinute: Int,
    endMinute: Int,
): Boolean {
    if (startMinute == endMinute) return false
    val localMinute = endpoint.atZone(zoneId).toLocalTime().toSecondOfDay() / 60
    return if (startMinute < endMinute) {
        localMinute in startMinute until endMinute
    } else {
        localMinute >= startMinute || localMinute <= endMinute
    }
}
