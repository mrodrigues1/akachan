package com.babytracker.domain.trends

import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import kotlinx.coroutines.flow.first
import java.time.Instant

/** Start instants of completed breastfeeding sessions in `[start, now]` (the query already caps at `now`). */
suspend fun BreastfeedingRepository.feedInstantsSince(start: Instant, now: Instant): List<Instant> =
    getCompletedSessionsBetween(start, now).map { it.startTime }

/** Bottle-feed instants since [start], dropping future-dated feeds so they match the session cap at [now]. */
suspend fun BottleFeedRepository.feedInstantsSince(start: Instant, now: Instant): List<Instant> =
    getSince(start).first().map { it.timestamp }.filter { !it.isAfter(now) }
