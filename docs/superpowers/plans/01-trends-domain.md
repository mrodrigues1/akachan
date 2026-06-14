# Trends Domain & Use Cases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

LINEAR_ISSUE: AKA-145

**Goal:** Add the pure-Kotlin domain models and three aggregation use cases that turn existing breastfeeding/bottle/sleep data into per-day trend series for the Charts & Trends feature.

**Architecture:** Pure domain layer, no Android/Vico imports. Three single-responsibility use cases read windowed slices from existing repositories and bucket by local calendar day using the existing `groupByLocalDate` philosophy and an injected `java.time.Clock` (codebase convention). No new repository or Room code — the required windowed reads already exist.

**Timezone note (reviewed decision):** Day bucketing uses `clock.zone`, matching every other time-based use case in this codebase (`PredictNextFeedUseCase`, `GenerateSleepScheduleUseCase`, etc.). `di/TimeModule` binds a singleton `Clock.systemDefaultZone()`, so the resolved zone is captured at process start; a device timezone change mid-process is reflected only after restart. This is an accepted, app-wide limitation — deviating only for Trends (e.g. a custom `() -> ZoneId` provider) would be inconsistent and is explicitly out of scope. Using `clock.zone` (rather than `ZoneId.systemDefault()`) is also what keeps the use-case tests deterministic, since `Clock.fixed(instant, zone)` pins both the instant and the zone. Task 3 includes a zone-boundary grouping test to lock in correct local-day bucketing.

**Tech Stack:** Kotlin, Hilt (constructor injection), JUnit 5 + MockK + `runTest`.

**Dependencies:** None. This is the first plan; plans 02 (ViewModel) and 03 (UI) depend on it.

**Suggested implementation branch:** `feat/trends-domain`

**Workflow note:** This project uses **implement-first, then tests** (see CLAUDE.md / user preference) — not test-first TDD.

**Spec:** `docs/superpowers/specs/2026-06-14-charts-and-trends-design.md`

---

## File Structure

- Create `app/src/main/java/com/babytracker/domain/trends/TrendModels.kt` — `TrendRange` enum + three `Daily*` data classes.
- Create `app/src/main/java/com/babytracker/domain/trends/TrendWindow.kt` — shared window helpers.
- Create `app/src/main/java/com/babytracker/domain/usecase/trends/GetFeedingFrequencyTrendUseCase.kt`
- Create `app/src/main/java/com/babytracker/domain/usecase/trends/GetSleepDurationTrendUseCase.kt`
- Create `app/src/main/java/com/babytracker/domain/usecase/trends/GetFeedingIntervalTrendUseCase.kt`
- Create test files mirroring the production paths under `app/src/test/java/...`.

Reused (do not modify): `BreastfeedingRepository.getCompletedSessionsBetween`, `SleepRepository.getCompletedRecordsSince`, `BottleFeedRepository.getSince`, `di/TimeModule` (`Clock` binding), `SleepType`.

---

### Task 1: Domain models

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/trends/TrendModels.kt`

- [ ] **Step 1: Write the models**

```kotlin
package com.babytracker.domain.trends

import java.time.LocalDate

/** Selectable look-back windows for the Trends screen. */
enum class TrendRange(val days: Int) {
    SEVEN_DAYS(7),
    FOURTEEN_DAYS(14),
    THIRTY_DAYS(30),
}

/** Number of feeds (breastfeeding sessions + bottle feeds) on one local day. */
data class DailyFeedingCount(val date: LocalDate, val count: Int)

/** Sleep hours on one local day, split by sleep type. */
data class DailySleepDuration(
    val date: LocalDate,
    val nightHours: Double,
    val napHours: Double,
) {
    val totalHours: Double get() = nightHours + napHours
}

/** Mean hours between consecutive same-day feeds; null when fewer than two feeds that day. */
data class DailyFeedingInterval(val date: LocalDate, val averageHours: Double?)
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

---

### Task 2: Window helpers

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/trends/TrendWindow.kt`
- Test: `app/src/test/java/com/babytracker/domain/trends/TrendWindowTest.kt`

- [ ] **Step 1: Write the helpers**

```kotlin
package com.babytracker.domain.trends

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The [days] consecutive local dates ending at [today] (inclusive), oldest first. */
fun trendWindowDates(today: LocalDate, days: Int): List<LocalDate> =
    (days - 1L downTo 0L).map { today.minusDays(it) }

/** Inclusive start instant of the window's first day, at start-of-day in [zone]. */
fun windowStartInstant(today: LocalDate, days: Int, zone: ZoneId): Instant =
    today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant()
```

- [ ] **Step 2: Write tests**

```kotlin
package com.babytracker.domain.trends

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class TrendWindowTest {
    @Test
    fun `trendWindowDates returns days dates oldest first inclusive of today`() {
        val today = LocalDate.of(2026, 6, 14)
        val dates = trendWindowDates(today, 7)
        assertEquals(7, dates.size)
        assertEquals(LocalDate.of(2026, 6, 8), dates.first())
        assertEquals(today, dates.last())
    }

    @Test
    fun `windowStartInstant is start of the first day in zone`() {
        val zone = ZoneId.of("UTC")
        val start = windowStartInstant(LocalDate.of(2026, 6, 14), 7, zone)
        assertEquals(LocalDate.of(2026, 6, 8).atStartOfDay(zone).toInstant(), start)
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "com.babytracker.domain.trends.TrendWindowTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/trends/ app/src/test/java/com/babytracker/domain/trends/
git commit -m "feat(trends): add trend domain models and window helpers"
```

---

### Task 3: Feeding frequency use case

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/trends/GetFeedingFrequencyTrendUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/trends/GetFeedingFrequencyTrendUseCaseTest.kt`

- [ ] **Step 1: Write the use case**

```kotlin
package com.babytracker.domain.usecase.trends

import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.trends.DailyFeedingCount
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.trends.trendWindowDates
import com.babytracker.domain.trends.windowStartInstant
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Counts feeds (completed breastfeeding sessions + bottle feeds) per local day over [range]. */
class GetFeedingFrequencyTrendUseCase @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val bottleFeedRepository: BottleFeedRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(range: TrendRange): List<DailyFeedingCount> {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val start = windowStartInstant(today, range.days, zone)
        val now = Instant.now(clock)

        val sessionInstants = breastfeedingRepository
            .getCompletedSessionsBetween(start, now) // already capped at `now`
            .map { it.startTime }
        val bottleInstants = bottleFeedRepository
            .getSince(start)
            .first()
            .map { it.timestamp }
            .filter { !it.isAfter(now) } // cap future-dated bottle feeds, matching the session cap

        val countByDate = (sessionInstants + bottleInstants)
            .groupingBy { it.atZone(zone).toLocalDate() }
            .eachCount()

        return trendWindowDates(today, range.days).map { date ->
            DailyFeedingCount(date, countByDate[date] ?: 0)
        }
    }
}
```

- [ ] **Step 2: Write tests**

```kotlin
package com.babytracker.domain.usecase.trends

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.trends.TrendRange
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class GetFeedingFrequencyTrendUseCaseTest {
    private val zone = ZoneOffset.UTC
    // Fixed "now" = 2026-06-14T12:00:00Z
    private val clock = Clock.fixed(Instant.parse("2026-06-14T12:00:00Z"), zone)
    private lateinit var breastfeeding: BreastfeedingRepository
    private lateinit var bottle: BottleFeedRepository
    private lateinit var useCase: GetFeedingFrequencyTrendUseCase

    private fun session(start: String) = BreastfeedingSession(
        id = 0, startTime = Instant.parse(start), endTime = Instant.parse(start).plusSeconds(600),
        startingSide = BreastSide.LEFT,
    )

    private fun bottle(ts: String) = BottleFeed(
        id = 0, clientId = ts, timestamp = Instant.parse(ts), volumeMl = 100,
        type = FeedType.FORMULA, createdAt = Instant.parse(ts),
    )

    @BeforeEach
    fun setup() {
        breastfeeding = mockk()
        bottle = mockk()
        useCase = GetFeedingFrequencyTrendUseCase(breastfeeding, bottle, clock)
    }

    @Test
    fun `counts breastfeeding and bottle feeds per day with zero-fill`() = runTest {
        coEvery { breastfeeding.getCompletedSessionsBetween(any(), any()) } returns listOf(
            session("2026-06-14T08:00:00Z"),
            session("2026-06-14T11:00:00Z"),
        )
        every { bottle.getSince(any()) } returns flowOf(
            listOf(bottle("2026-06-13T09:00:00Z")),
        )

        val result = useCase(TrendRange.SEVEN_DAYS)

        assertEquals(7, result.size)
        assertEquals(2, result.first { it.date.toString() == "2026-06-14" }.count)
        assertEquals(1, result.first { it.date.toString() == "2026-06-13" }.count)
        assertEquals(0, result.first { it.date.toString() == "2026-06-12" }.count)
    }

    @Test
    fun `empty data yields all zero days`() = runTest {
        coEvery { breastfeeding.getCompletedSessionsBetween(any(), any()) } returns emptyList()
        every { bottle.getSince(any()) } returns flowOf(emptyList())

        val result = useCase(TrendRange.SEVEN_DAYS)

        assertEquals(7, result.size)
        assertEquals(0, result.sumOf { it.count })
    }

    @Test
    fun `buckets near-midnight feeds by the clock's local day`() = runTest {
        // Zone UTC-3 (Sao Paulo, no DST in 2026). 2026-06-14T02:00Z == 2026-06-13 23:00 local.
        val zone = ZoneId.of("America/Sao_Paulo")
        val saoPauloClock = Clock.fixed(Instant.parse("2026-06-14T12:00:00Z"), zone)
        val zonedUseCase = GetFeedingFrequencyTrendUseCase(breastfeeding, bottle, saoPauloClock)
        coEvery { breastfeeding.getCompletedSessionsBetween(any(), any()) } returns listOf(
            session("2026-06-14T02:00:00Z"), // 2026-06-13 23:00 local
        )
        every { bottle.getSince(any()) } returns flowOf(emptyList())

        val result = zonedUseCase(TrendRange.SEVEN_DAYS)

        assertEquals(1, result.first { it.date.toString() == "2026-06-13" }.count)
        assertEquals(0, result.first { it.date.toString() == "2026-06-14" }.count)
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.trends.GetFeedingFrequencyTrendUseCaseTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/trends/GetFeedingFrequencyTrendUseCase.kt app/src/test/java/com/babytracker/domain/usecase/trends/GetFeedingFrequencyTrendUseCaseTest.kt
git commit -m "feat(trends): add feeding frequency trend use case"
```

---

### Task 4: Sleep duration use case

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/trends/GetSleepDurationTrendUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/trends/GetSleepDurationTrendUseCaseTest.kt`

- [ ] **Step 1: Write the use case**

```kotlin
package com.babytracker.domain.usecase.trends

import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.trends.DailySleepDuration
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.trends.trendWindowDates
import com.babytracker.domain.trends.windowStartInstant
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Sums completed sleep hours per local day (by start day), split night vs nap, over [range]. */
class GetSleepDurationTrendUseCase @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(range: TrendRange): List<DailySleepDuration> {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val start = windowStartInstant(today, range.days, zone)
        val now = Instant.now(clock)

        val byDate = sleepRepository.getCompletedRecordsSince(start)
            // Exclude future-dated entries (SaveSleepEntryUseCase only checks end > start), so a
            // sleep starting later today can never inflate today's bar.
            .filter { !it.startTime.isAfter(now) }
            .groupBy { it.startTime.atZone(zone).toLocalDate() }

        return trendWindowDates(today, range.days).map { date ->
            val dayRecords = byDate[date].orEmpty()
            fun hoursOf(type: SleepType) = dayRecords
                .filter { it.sleepType == type }
                .sumOf { (it.duration?.toMinutes() ?: 0L) / 60.0 }
            DailySleepDuration(
                date = date,
                nightHours = hoursOf(SleepType.NIGHT_SLEEP),
                napHours = hoursOf(SleepType.NAP),
            )
        }
    }
}
```

- [ ] **Step 2: Write tests**

```kotlin
package com.babytracker.domain.usecase.trends

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.trends.TrendRange
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class GetSleepDurationTrendUseCaseTest {
    private val clock = Clock.fixed(Instant.parse("2026-06-14T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var sleep: SleepRepository
    private lateinit var useCase: GetSleepDurationTrendUseCase

    private fun record(start: String, endHoursLater: Long, type: SleepType) = SleepRecord(
        id = 0, startTime = Instant.parse(start),
        endTime = Instant.parse(start).plusSeconds(endHoursLater * 3600), sleepType = type,
    )

    @BeforeEach
    fun setup() {
        sleep = mockk()
        useCase = GetSleepDurationTrendUseCase(sleep, clock)
    }

    @Test
    fun `splits night and nap hours by start day`() = runTest {
        coEvery { sleep.getCompletedRecordsSince(any()) } returns listOf(
            record("2026-06-14T01:00:00Z", 8, SleepType.NIGHT_SLEEP),
            record("2026-06-14T13:00:00Z", 2, SleepType.NAP),
            record("2026-06-14T15:00:00Z", 1, SleepType.NAP),
        )

        val day = useCase(TrendRange.SEVEN_DAYS).first { it.date.toString() == "2026-06-14" }

        assertEquals(8.0, day.nightHours, 0.001)
        assertEquals(3.0, day.napHours, 0.001)
        assertEquals(11.0, day.totalHours, 0.001)
    }

    @Test
    fun `days with no sleep are zero`() = runTest {
        coEvery { sleep.getCompletedRecordsSince(any()) } returns emptyList()
        val result = useCase(TrendRange.SEVEN_DAYS)
        assertEquals(7, result.size)
        assertEquals(0.0, result.sumOf { it.totalHours }, 0.001)
    }

    @Test
    fun `future-dated sleep on today is excluded`() = runTest {
        // clock now = 2026-06-14T12:00Z; this record starts at 14:00 today (after now).
        coEvery { sleep.getCompletedRecordsSince(any()) } returns listOf(
            record("2026-06-14T14:00:00Z", 1, SleepType.NAP),
        )
        val day = useCase(TrendRange.SEVEN_DAYS).first { it.date.toString() == "2026-06-14" }
        assertEquals(0.0, day.totalHours, 0.001)
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.trends.GetSleepDurationTrendUseCaseTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/trends/GetSleepDurationTrendUseCase.kt app/src/test/java/com/babytracker/domain/usecase/trends/GetSleepDurationTrendUseCaseTest.kt
git commit -m "feat(trends): add sleep duration trend use case"
```

---

### Task 5: Feeding interval use case

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/trends/GetFeedingIntervalTrendUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/trends/GetFeedingIntervalTrendUseCaseTest.kt`

- [ ] **Step 1: Write the use case**

```kotlin
package com.babytracker.domain.usecase.trends

import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.trends.DailyFeedingInterval
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.trends.trendWindowDates
import com.babytracker.domain.trends.windowStartInstant
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Average hours between consecutive same-day feeds per local day; null when <2 feeds that day. */
class GetFeedingIntervalTrendUseCase @Inject constructor(
    private val breastfeedingRepository: BreastfeedingRepository,
    private val bottleFeedRepository: BottleFeedRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(range: TrendRange): List<DailyFeedingInterval> {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val start = windowStartInstant(today, range.days, zone)
        val now = Instant.now(clock)

        val feedInstants =
            breastfeedingRepository.getCompletedSessionsBetween(start, now).map { it.startTime } +
                bottleFeedRepository.getSince(start).first().map { it.timestamp }
                    .filter { !it.isAfter(now) } // cap future-dated bottle feeds
        val byDate = feedInstants.groupBy { it.atZone(zone).toLocalDate() }

        return trendWindowDates(today, range.days).map { date ->
            val sorted = byDate[date].orEmpty().sorted()
            val average = if (sorted.size < 2) {
                null
            } else {
                sorted.zipWithNext { a, b -> Duration.between(a, b).toMinutes() / 60.0 }.average()
            }
            DailyFeedingInterval(date, average)
        }
    }
}
```

- [ ] **Step 2: Write tests**

```kotlin
package com.babytracker.domain.usecase.trends

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.trends.TrendRange
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class GetFeedingIntervalTrendUseCaseTest {
    private val clock = Clock.fixed(Instant.parse("2026-06-14T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var breastfeeding: BreastfeedingRepository
    private lateinit var bottle: BottleFeedRepository
    private lateinit var useCase: GetFeedingIntervalTrendUseCase

    private fun session(start: String) = BreastfeedingSession(
        startTime = Instant.parse(start), endTime = Instant.parse(start).plusSeconds(300),
        startingSide = BreastSide.LEFT,
    )

    private fun bottle(ts: String) = BottleFeed(
        clientId = ts, timestamp = Instant.parse(ts), volumeMl = 100,
        type = FeedType.FORMULA, createdAt = Instant.parse(ts),
    )

    @BeforeEach
    fun setup() {
        breastfeeding = mockk()
        bottle = mockk()
        useCase = GetFeedingIntervalTrendUseCase(breastfeeding, bottle, clock)
    }

    @Test
    fun `averages same-day gaps in hours`() = runTest {
        // Feeds at 06:00, 09:00, 12:00 -> gaps 3h and 3h -> avg 3.0
        coEvery { breastfeeding.getCompletedSessionsBetween(any(), any()) } returns listOf(
            session("2026-06-14T06:00:00Z"),
            session("2026-06-14T09:00:00Z"),
        )
        every { bottle.getSince(any()) } returns flowOf(listOf(bottle("2026-06-14T12:00:00Z")))

        val day = useCase(TrendRange.SEVEN_DAYS).first { it.date.toString() == "2026-06-14" }
        assertEquals(3.0, day.averageHours!!, 0.001)
    }

    @Test
    fun `single feed day has null average`() = runTest {
        coEvery { breastfeeding.getCompletedSessionsBetween(any(), any()) } returns listOf(
            session("2026-06-13T10:00:00Z"),
        )
        every { bottle.getSince(any()) } returns flowOf(emptyList())

        val day = useCase(TrendRange.SEVEN_DAYS).first { it.date.toString() == "2026-06-13" }
        assertNull(day.averageHours)
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.trends.GetFeedingIntervalTrendUseCaseTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/trends/GetFeedingIntervalTrendUseCase.kt app/src/test/java/com/babytracker/domain/usecase/trends/GetFeedingIntervalTrendUseCaseTest.kt
git commit -m "feat(trends): add feeding interval trend use case"
```

---

## Acceptance Criteria

- `TrendRange`, `DailyFeedingCount`, `DailySleepDuration`, `DailyFeedingInterval` exist in `domain/trends/` with zero framework imports.
- All three use cases return exactly `range.days` entries, oldest day first, with zero-fill for empty days.
- Feeds = completed breastfeeding sessions (by `startTime`) + bottle feeds (by `timestamp`).
- Sleep hours attributed to the record's start day, split `NIGHT_SLEEP` vs `NAP`.
- Feeding interval averages same-day consecutive gaps; `<2` feeds → `null`.
- New unit tests pass: `./gradlew test --tests "com.babytracker.domain.*trends*"` (all green).
- `./gradlew build` succeeds; ktlint + detekt clean (enforced by pre-commit hook).

## Self-Review Notes

- Spec coverage: feeding-frequency, sleep-duration, feeding-interval all implemented; range windowing matches the spec's 7/14/30 window definition (last N calendar days, inclusive of today).
- Type consistency: `trendWindowDates`/`windowStartInstant` signatures identical across all three use cases; `Clock` injected the same way everywhere.
- No placeholders; every step has runnable code/commands.
