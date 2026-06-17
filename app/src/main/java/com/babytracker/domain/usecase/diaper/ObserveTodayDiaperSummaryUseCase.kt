package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.model.TodayDiaperSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class ObserveTodayDiaperSummaryUseCase @Inject constructor(
    private val observeDiaperChanges: ObserveDiaperChangesUseCase,
    private val zone: ZoneId,
    private val now: () -> Instant,
) {
    operator fun invoke(): Flow<TodayDiaperSummary> =
        observeDiaperChanges().map { changes ->
            val today = now().atZone(zone).toLocalDate()
            TodayDiaperSummary(
                count = changes.count { it.timestamp.atZone(zone).toLocalDate() == today },
                lastChangeAt = changes.maxByOrNull { it.timestamp }?.timestamp,
            )
        }
}
