package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.model.TodayDiaperSummary
import com.babytracker.domain.repository.DiaperRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class ObserveTodayDiaperSummaryUseCase @Inject constructor(
    private val diaperRepository: DiaperRepository,
    private val zone: ZoneId,
    private val now: () -> Instant,
) {
    operator fun invoke(): Flow<TodayDiaperSummary> =
        diaperRepository.observeRecent(TODAY_SUMMARY_LIMIT).map { changes ->
            val today = now().atZone(zone).toLocalDate()
            TodayDiaperSummary(
                count = changes.count { it.timestamp.atZone(zone).toLocalDate() == today },
                // observeRecent is timestamp DESC, so the first row is the newest change overall.
                lastChangeAt = changes.firstOrNull()?.timestamp,
            )
        }

    private companion object {
        // ponytail: bounds the scan to the newest 100 rows; a single day with >100 changes would
        // undercount — real-world rate is ~8-10/day, so the ceiling is theoretical.
        const val TODAY_SUMMARY_LIMIT = 100
    }
}
