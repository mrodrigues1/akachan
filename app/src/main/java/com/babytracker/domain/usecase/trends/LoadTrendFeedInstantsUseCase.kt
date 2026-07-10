package com.babytracker.domain.usecase.trends

import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.trends.feedInstantsSince
import com.babytracker.domain.trends.windowStartInstant
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Feed instants over a trend window, loaded once and shared by the three feed trends. */
data class TrendFeedInstants(
    val breast: List<Instant> = emptyList(),
    val bottle: List<Instant> = emptyList(),
) {
    val all: List<Instant> get() = breast + bottle
}

/**
 * One-shot load of every feed instant in [TrendRange]'s window. Frequency, interval, and rhythm all
 * consume this same dataset — loading it once here instead of three times inside the use cases.
 */
class LoadTrendFeedInstantsUseCase @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val bottleFeedRepository: BottleFeedRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(range: TrendRange): TrendFeedInstants {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val start = windowStartInstant(today, range.days, zone)
        val now = Instant.now(clock)
        return TrendFeedInstants(
            breast = breastfeedingRepository.feedInstantsSince(start, now),
            bottle = bottleFeedRepository.feedInstantsSince(start, now),
        )
    }
}
