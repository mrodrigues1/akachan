package com.babytracker.domain.usecase.feeding

import com.babytracker.domain.model.TodayFeedingSummary
import com.babytracker.domain.usecase.bottlefeed.ObserveBottleFeedsUseCase
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class ObserveTodayFeedingSummaryUseCase @Inject constructor(
    private val getBreastfeedingHistory: GetBreastfeedingHistoryUseCase,
    private val observeBottleFeeds: ObserveBottleFeedsUseCase,
    private val zone: ZoneId,
    private val now: () -> Instant,
) {
    operator fun invoke(): Flow<TodayFeedingSummary> =
        combine(getBreastfeedingHistory(), observeBottleFeeds()) { sessions, bottles ->
            val today = now().atZone(zone).toLocalDate()
            val todayBottles = bottles.filter { it.timestamp.atZone(zone).toLocalDate() == today }
            val todaySessions = sessions.filter { it.startTime.atZone(zone).toLocalDate() == today }
            TodayFeedingSummary(
                bottleVolumeMl = todayBottles.sumOf { it.volumeMl },
                bottleCount = todayBottles.size,
                breastfeedingCount = todaySessions.size,
            )
        }
}
