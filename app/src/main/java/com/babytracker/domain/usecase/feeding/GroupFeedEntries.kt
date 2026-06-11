package com.babytracker.domain.usecase.feeding

import com.babytracker.domain.model.DailyFeedingTotals
import com.babytracker.domain.model.FeedEntry
import com.babytracker.domain.model.FeedingDayGroup
import java.time.ZoneId

fun groupFeedEntriesByDay(
    entries: List<FeedEntry>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<FeedingDayGroup> =
    entries
        .groupBy { it.timestamp.atZone(zone).toLocalDate() }
        .map { (date, dayEntries) ->
            val sorted = dayEntries.sortedByDescending { it.timestamp }
            val bottles = sorted.filterIsInstance<FeedEntry.Bottle>()
            FeedingDayGroup(
                date = date,
                totals = DailyFeedingTotals(
                    bottleVolumeMl = bottles.sumOf { it.feed.volumeMl },
                    bottleCount = bottles.size,
                    breastfeedingCount = sorted.count { it is FeedEntry.Breastfeeding },
                ),
                entries = sorted,
            )
        }
        .sortedByDescending { it.date }
