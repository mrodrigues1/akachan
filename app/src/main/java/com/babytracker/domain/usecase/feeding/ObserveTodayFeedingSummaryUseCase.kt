package com.babytracker.domain.usecase.feeding

import com.babytracker.domain.model.TodayFeedingSummary
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class ObserveTodayFeedingSummaryUseCase @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val bottleFeedRepository: BottleFeedRepository,
    private val zone: ZoneId,
    private val now: () -> Instant,
) {
    operator fun invoke(): Flow<TodayFeedingSummary> =
        combine(
            // ponytail: the newest 100 rows per source bound the scan; a single day with >100
            // feeds would undercount — real-world rate is ~8-12/day, so the ceiling is theoretical.
            breastfeedingRepository.getRecentSessionsFlow(TODAY_SUMMARY_LIMIT),
            bottleFeedRepository.getRecentFlow(TODAY_SUMMARY_LIMIT),
        ) { sessions, bottles ->
            val today = now().atZone(zone).toLocalDate()
            val todayBottles = bottles.filter { it.timestamp.atZone(zone).toLocalDate() == today }
            val todaySessions = sessions.filter { it.startTime.atZone(zone).toLocalDate() == today }
            TodayFeedingSummary(
                bottleVolumeMl = todayBottles.sumOf { it.volumeMl },
                bottleCount = todayBottles.size,
                breastfeedingCount = todaySessions.size,
            )
        }

    private companion object {
        const val TODAY_SUMMARY_LIMIT = 100
    }
}
