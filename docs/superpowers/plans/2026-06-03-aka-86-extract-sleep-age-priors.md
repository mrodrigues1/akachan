# Extract SleepAgePriors — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract six age-prior lookup methods from `GenerateSleepScheduleUseCase` into a pure Kotlin `SleepAgePriors` object at `domain/sleep/prior/`, then refactor the use case to delegate to it — creating the shared source of truth that both `GenerateSleepScheduleUseCase` and the upcoming `PredictSleepWindowUseCase` will consume.

**Architecture:** `SleepAgePriors` is a Kotlin `object` (no constructor, no Android/framework imports, `java.time.*` only). `GenerateSleepScheduleUseCase` replaces its own prior method bodies with direct calls to `SleepAgePriors.*`. The four `Nested` test classes in `GenerateSleepScheduleUseCaseTest` that tested `internal` methods on the use case directly (`WakeWindowTests`, `BedtimeTests`, `TotalSleepTests`, `RegressionTests`) are migrated to call `SleepAgePriors.*` instead; all integration-level test classes stay untouched.

**Tech Stack:** Kotlin, JUnit 5 (`@Test`, `@Nested`), `java.time.*`. No Android SDK in the new production file.

**Linear issue:** [AKA-86](https://linear.app/akachan/issue/AKA-86/extract-sleepagepriors-refactor-generatesleepscheduleusecase-to)
**Branch:** `feat/sleep-prediction-phase-0`

---

## File Map

| Action | Path | Responsibility |
|--------|------|---------------|
| **Create** | `app/src/main/java/com/babytracker/domain/sleep/prior/SleepAgePriors.kt` | Pure Kotlin object with all 6 age-prior lookup functions |
| **Create** | `app/src/test/java/com/babytracker/domain/sleep/prior/SleepAgePriorsTest.kt` | Unit tests covering every age bracket for all 6 methods |
| **Modify** | `app/src/main/java/com/babytracker/domain/usecase/sleep/GenerateSleepScheduleUseCase.kt` | Remove 6 prior methods; delegate to `SleepAgePriors`; drop unused imports |
| **Modify** | `app/src/test/java/com/babytracker/domain/usecase/sleep/GenerateSleepScheduleUseCaseTest.kt` | Migrate `WakeWindowTests`, `BedtimeTests`, `TotalSleepTests`, `RegressionTests` to call `SleepAgePriors.*` |

---

## Task 1: Write failing tests for `SleepAgePriors`

**Files:**
- Create: `app/src/test/java/com/babytracker/domain/sleep/prior/SleepAgePriorsTest.kt`

These tests will fail with "unresolved reference: SleepAgePriors" until Task 2 creates the class. We cover all 7 methods (6 extracted + 1 derived). `getWakeWindowBounds`, `getNapTransitionThreshold`, and `getScheduledNapCount` were either `private` or did not exist on the use case; they become public on `SleepAgePriors` and get dedicated test coverage here. `getScheduledNapCount` is a derived function (`getDefaultWakeWindows(age).size - 1`) added to make the scheduled-nap-count API explicit and unambiguous for future predictor consumers.

- [ ] **Step 1.1: Create `SleepAgePriorsTest.kt`**

Create `app/src/test/java/com/babytracker/domain/sleep/prior/SleepAgePriorsTest.kt`:

```kotlin
package com.babytracker.domain.sleep.prior

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalTime

class SleepAgePriorsTest {

    @Nested
    inner class DefaultWakeWindowsTests {
        @Test
        fun `newborn under 6 weeks has 5 wake windows of 45 minutes`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(3)
            assertEquals(5, windows.size)
            assertTrue(windows.all { it == Duration.ofMinutes(45) })
        }

        @Test
        fun `6-8 weeks has 4 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(7)
            assertEquals(4, windows.size)
            assertEquals(Duration.ofMinutes(60), windows[0])
            assertEquals(Duration.ofMinutes(75), windows[3])
        }

        @Test
        fun `2-3 months has 4 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(10)
            assertEquals(4, windows.size)
            assertEquals(Duration.ofMinutes(75), windows[0])
            assertEquals(Duration.ofMinutes(90), windows[3])
        }

        @Test
        fun `3-4 months has 3 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(14)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(90), windows[0])
            assertEquals(Duration.ofMinutes(120), windows[2])
        }

        @Test
        fun `4-6 months has 3 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(20)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(105), windows[0])
            assertEquals(Duration.ofMinutes(150), windows[2])
        }

        @Test
        fun `6-9 months has 3 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(30)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(150), windows[0])
            assertEquals(Duration.ofMinutes(210), windows[2])
        }

        @Test
        fun `9-12 months has 3 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(40)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(180), windows[0])
            assertEquals(Duration.ofMinutes(240), windows[2])
        }

        @Test
        fun `wake windows are graduated - first is shortest, last is longest`() {
            val ageBrackets = listOf(3, 7, 10, 14, 20, 30, 40)
            for (age in ageBrackets) {
                val windows = SleepAgePriors.getDefaultWakeWindows(age)
                assertTrue(
                    windows.first() <= windows.last(),
                    "Age $age weeks: first window should be <= last window"
                )
            }
        }
    }

    @Nested
    inner class WakeWindowBoundsTests {
        @Test
        fun `newborn bounds are 30-60 minutes`() {
            val (min, max) = SleepAgePriors.getWakeWindowBounds(3)
            assertEquals(Duration.ofMinutes(30), min)
            assertEquals(Duration.ofMinutes(60), max)
        }

        @Test
        fun `6-8 weeks bounds are 45-90 minutes`() {
            val (min, max) = SleepAgePriors.getWakeWindowBounds(7)
            assertEquals(Duration.ofMinutes(45), min)
            assertEquals(Duration.ofMinutes(90), max)
        }

        @Test
        fun `2-3 months bounds are 60-120 minutes`() {
            val (min, max) = SleepAgePriors.getWakeWindowBounds(10)
            assertEquals(Duration.ofMinutes(60), min)
            assertEquals(Duration.ofMinutes(120), max)
        }

        @Test
        fun `3-4 months bounds are 75-150 minutes`() {
            val (min, max) = SleepAgePriors.getWakeWindowBounds(14)
            assertEquals(Duration.ofMinutes(75), min)
            assertEquals(Duration.ofMinutes(150), max)
        }

        @Test
        fun `4-6 months bounds are 90-180 minutes`() {
            val (min, max) = SleepAgePriors.getWakeWindowBounds(20)
            assertEquals(Duration.ofMinutes(90), min)
            assertEquals(Duration.ofMinutes(180), max)
        }

        @Test
        fun `6-9 months bounds are 120-210 minutes`() {
            val (min, max) = SleepAgePriors.getWakeWindowBounds(30)
            assertEquals(Duration.ofMinutes(120), min)
            assertEquals(Duration.ofMinutes(210), max)
        }

        @Test
        fun `9-12 months bounds are 150-240 minutes`() {
            val (min, max) = SleepAgePriors.getWakeWindowBounds(40)
            assertEquals(Duration.ofMinutes(150), min)
            assertEquals(Duration.ofMinutes(240), max)
        }
    }

    @Nested
    inner class BedtimeWindowTests {
        @Test
        fun `newborn bedtime window is 9-11 PM`() {
            val window = SleepAgePriors.getBedtimeWindow(3)
            assertEquals(LocalTime.of(21, 0), window.start)
            assertEquals(LocalTime.of(23, 0), window.endInclusive)
        }

        @Test
        fun `6-12 week bedtime window is 8-10 PM`() {
            val window = SleepAgePriors.getBedtimeWindow(8)
            assertEquals(LocalTime.of(20, 0), window.start)
            assertEquals(LocalTime.of(22, 0), window.endInclusive)
        }

        @Test
        fun `3-4 month bedtime window is 7_30-9 PM`() {
            val window = SleepAgePriors.getBedtimeWindow(14)
            assertEquals(LocalTime.of(19, 30), window.start)
            assertEquals(LocalTime.of(21, 0), window.endInclusive)
        }

        @Test
        fun `5-6 month bedtime window is 7-8 PM`() {
            val window = SleepAgePriors.getBedtimeWindow(20)
            assertEquals(LocalTime.of(19, 0), window.start)
            assertEquals(LocalTime.of(20, 0), window.endInclusive)
        }

        @Test
        fun `7-12 month bedtime window is 6_30-8 PM`() {
            val window = SleepAgePriors.getBedtimeWindow(30)
            assertEquals(LocalTime.of(18, 30), window.start)
            assertEquals(LocalTime.of(20, 0), window.endInclusive)
        }
    }

    @Nested
    inner class TotalSleepRecommendationTests {
        @Test
        fun `0-4 months recommends 14-17 hours`() {
            val rec = SleepAgePriors.getTotalSleepRecommendation(10)
            assertEquals(Duration.ofHours(14), rec.start)
            assertEquals(Duration.ofHours(17), rec.endInclusive)
        }

        @Test
        fun `4-12 months recommends 12-16 hours`() {
            val rec = SleepAgePriors.getTotalSleepRecommendation(20)
            assertEquals(Duration.ofHours(12), rec.start)
            assertEquals(Duration.ofHours(16), rec.endInclusive)
        }
    }

    @Nested
    inner class NapTransitionThresholdTests {
        @Test
        fun `newborn under 6 weeks transition threshold is 5`() {
            assertEquals(5, SleepAgePriors.getNapTransitionThreshold(3))
        }

        @Test
        fun `6-12 weeks transition threshold is 4`() {
            assertEquals(4, SleepAgePriors.getNapTransitionThreshold(8))
        }

        @Test
        fun `12-24 weeks transition threshold is 3`() {
            assertEquals(3, SleepAgePriors.getNapTransitionThreshold(16))
        }

        @Test
        fun `24-36 weeks transition threshold is 2`() {
            assertEquals(2, SleepAgePriors.getNapTransitionThreshold(28))
        }

        @Test
        fun `over 36 weeks transition threshold is 2`() {
            assertEquals(2, SleepAgePriors.getNapTransitionThreshold(40))
        }
    }

    @Nested
    inner class ScheduledNapCountTests {
        @Test
        fun `scheduled nap count is wake-window count minus 1`() {
            listOf(3, 7, 10, 14, 20, 30, 40).forEach { age ->
                assertEquals(
                    SleepAgePriors.getDefaultWakeWindows(age).size - 1,
                    SleepAgePriors.getScheduledNapCount(age),
                    "Age $age weeks: scheduledNapCount should be wakeWindows.size - 1"
                )
            }
        }

        @Test
        fun `newborn under 6 weeks schedules 4 naps`() {
            assertEquals(4, SleepAgePriors.getScheduledNapCount(3))
        }

        @Test
        fun `6-12 weeks schedules 3 naps`() {
            assertEquals(3, SleepAgePriors.getScheduledNapCount(8))
        }

        @Test
        fun `3-12 months schedules 2 naps`() {
            assertEquals(2, SleepAgePriors.getScheduledNapCount(20))
        }
    }

    @Nested
    inner class NapCountParityTests {
        // For ages < 24 weeks the transition threshold is one higher than the scheduled nap
        // count — young infants have no distinct bedtime in the sleep-science model, so the
        // biological count includes what the scheduler treats as "last wake window → bedtime".
        // For ages >= 24 weeks they converge. These tests document the intentional divergence.

        @Test
        fun `for ages under 24 weeks transition threshold exceeds scheduled nap count by 1`() {
            listOf(3, 7, 10, 14, 20).forEach { age ->
                assertEquals(
                    SleepAgePriors.getScheduledNapCount(age) + 1,
                    SleepAgePriors.getNapTransitionThreshold(age),
                    "Age $age weeks: transitionThreshold should be scheduledNapCount + 1"
                )
            }
        }

        @Test
        fun `for ages 24 weeks and over transition threshold matches scheduled nap count`() {
            listOf(24, 30, 40).forEach { age ->
                assertEquals(
                    SleepAgePriors.getScheduledNapCount(age),
                    SleepAgePriors.getNapTransitionThreshold(age),
                    "Age $age weeks: transitionThreshold should equal scheduledNapCount"
                )
            }
        }
    }

    @Nested
    inner class RegressionTests {
        @Test
        fun `4 month regression detected at 16 weeks`() {
            val info = SleepAgePriors.detectRegression(16)
            assertNotNull(info)
            assertEquals("4-Month Sleep Regression", info!!.name)
        }

        @Test
        fun `4 month regression detected at 20 weeks`() {
            val info = SleepAgePriors.detectRegression(20)
            assertNotNull(info)
            assertEquals("4-Month Sleep Regression", info!!.name)
        }

        @Test
        fun `8-10 month regression detected at 36 weeks`() {
            val info = SleepAgePriors.detectRegression(36)
            assertNotNull(info)
            assertEquals("8-10 Month Sleep Regression", info!!.name)
        }

        @Test
        fun `12 month regression detected at 50 weeks`() {
            val info = SleepAgePriors.detectRegression(50)
            assertNotNull(info)
            assertEquals("12-Month Sleep Regression", info!!.name)
        }

        @Test
        fun `no regression at 10 weeks`() {
            assertNull(SleepAgePriors.detectRegression(10))
        }

        @Test
        fun `no regression at 26 weeks`() {
            assertNull(SleepAgePriors.detectRegression(26))
        }

        @Test
        fun `no regression at 46 weeks`() {
            assertNull(SleepAgePriors.detectRegression(46))
        }
    }
}
```

- [ ] **Step 1.2: Verify tests fail with unresolved reference**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.prior.SleepAgePriorsTest" 2>&1 | tail -20
```

Expected: BUILD FAILED — `error: unresolved reference: SleepAgePriors`.

---

## Task 2: Create `SleepAgePriors`

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/sleep/prior/SleepAgePriors.kt`

Methods are copied verbatim from `GenerateSleepScheduleUseCase` with visibility changed to `fun` (public). `RegressionInfo` is imported from `domain.model`. No Android SDK, no `javax.inject`, no `kotlinx.*` — pure `java.time.*` + domain model only.

- [ ] **Step 2.1: Create `SleepAgePriors.kt`**

Create `app/src/main/java/com/babytracker/domain/sleep/prior/SleepAgePriors.kt`:

```kotlin
package com.babytracker.domain.sleep.prior

import com.babytracker.domain.model.RegressionInfo
import java.time.Duration
import java.time.LocalTime

object SleepAgePriors {

    fun getDefaultWakeWindows(ageInWeeks: Int): List<Duration> = when {
        ageInWeeks < 6 -> listOf(45, 45, 45, 45, 45).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 8 -> listOf(60, 60, 75, 75).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 12 -> listOf(75, 80, 90, 90).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 16 -> listOf(90, 105, 120).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 24 -> listOf(105, 135, 150).map { Duration.ofMinutes(it.toLong()) }
        ageInWeeks < 36 -> listOf(150, 180, 210).map { Duration.ofMinutes(it.toLong()) }
        else -> listOf(180, 210, 240).map { Duration.ofMinutes(it.toLong()) }
    }

    fun getWakeWindowBounds(ageInWeeks: Int): Pair<Duration, Duration> = when {
        ageInWeeks < 6 -> Duration.ofMinutes(30) to Duration.ofMinutes(60)
        ageInWeeks < 8 -> Duration.ofMinutes(45) to Duration.ofMinutes(90)
        ageInWeeks < 12 -> Duration.ofMinutes(60) to Duration.ofMinutes(120)
        ageInWeeks < 16 -> Duration.ofMinutes(75) to Duration.ofMinutes(150)
        ageInWeeks < 24 -> Duration.ofMinutes(90) to Duration.ofMinutes(180)
        ageInWeeks < 36 -> Duration.ofMinutes(120) to Duration.ofMinutes(210)
        else -> Duration.ofMinutes(150) to Duration.ofMinutes(240)
    }

    fun getBedtimeWindow(ageInWeeks: Int): ClosedRange<LocalTime> = when {
        ageInWeeks < 6 -> LocalTime.of(21, 0)..LocalTime.of(23, 0)
        ageInWeeks < 12 -> LocalTime.of(20, 0)..LocalTime.of(22, 0)
        ageInWeeks < 16 -> LocalTime.of(19, 30)..LocalTime.of(21, 0)
        ageInWeeks < 24 -> LocalTime.of(19, 0)..LocalTime.of(20, 0)
        else -> LocalTime.of(18, 30)..LocalTime.of(20, 0)
    }

    fun getTotalSleepRecommendation(ageInWeeks: Int): ClosedRange<Duration> = when {
        ageInWeeks < 16 -> Duration.ofHours(14)..Duration.ofHours(17)
        else -> Duration.ofHours(12)..Duration.ofHours(16)
    }

    // Biological nap-count threshold for transition detection (sleep science), NOT the
    // count the scheduler generates. For ages < 24 weeks this is one higher than
    // getScheduledNapCount — young infants have no distinct bedtime in the sleep-science model.
    // Only use this in detectNapTransition; use getScheduledNapCount for prediction/scheduling.
    fun getNapTransitionThreshold(ageInWeeks: Int): Int = when {
        ageInWeeks < 6 -> 5
        ageInWeeks < 12 -> 4
        ageInWeeks < 16 -> 3
        ageInWeeks < 24 -> 3
        ageInWeeks < 36 -> 2
        else -> 2
    }

    fun getScheduledNapCount(ageInWeeks: Int): Int = getDefaultWakeWindows(ageInWeeks).size - 1

    fun detectRegression(ageInWeeks: Int): RegressionInfo? = when {
        ageInWeeks in 14..22 -> RegressionInfo(
            name = "4-Month Sleep Regression",
            description = "Your baby's sleep architecture is maturing from 2-stage to 4-stage cycles. " +
                "This is a permanent and healthy change. Sleep may be more disrupted than usual.",
            durationWeeks = "2-6 weeks"
        )
        ageInWeeks in 32..44 -> RegressionInfo(
            name = "8-10 Month Sleep Regression",
            description = "Object permanence, separation anxiety, and motor milestones (crawling, pulling up) " +
                "can temporarily disrupt sleep patterns.",
            durationWeeks = "2-6 weeks"
        )
        ageInWeeks in 48..55 -> RegressionInfo(
            name = "12-Month Sleep Regression",
            description = "Walking, early language development, and nap resistance may cause temporary " +
                "sleep disruption.",
            durationWeeks = "1-3 weeks"
        )
        else -> null
    }
}
```

- [ ] **Step 2.2: Run `SleepAgePriorsTest` — expect all pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.prior.SleepAgePriorsTest" 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL. All tests pass.

---

## Task 3: Refactor `GenerateSleepScheduleUseCase` to delegate to `SleepAgePriors`

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/usecase/sleep/GenerateSleepScheduleUseCase.kt`

Remove the six prior methods (`getDefaultWakeWindows`, `getWakeWindowBounds`, `getBedtimeWindow`, `getTotalSleepRecommendation`, `getExpectedNapCount`, `detectRegression`). Replace each call site in `invoke()` and `detectNapTransition()` with `SleepAgePriors.*`. Crucially, the `detectNapTransition` call uses `SleepAgePriors.getNapTransitionThreshold(ageInWeeks)` — not `getScheduledNapCount` — because transition detection is comparing observed naps against the sleep-science threshold, not the scheduler output. Drop the `RegressionInfo` import (no longer referenced directly).

- [ ] **Step 3.1: Replace `GenerateSleepScheduleUseCase.kt`**

Replace the full file:

```kotlin
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.ScheduleEntry
import com.babytracker.domain.model.ScheduleMode
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepSchedule
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.sleep.prior.SleepAgePriors
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class GenerateSleepScheduleUseCase @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val breastfeedingRepository: BreastfeedingRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(
        baby: Baby
    ): SleepSchedule {
        val ageInWeeks = ChronoUnit.WEEKS.between(baby.birthDate, LocalDate.now()).toInt()
        val mode = if (ageInWeeks < 16) ScheduleMode.DEMAND_DRIVEN else ScheduleMode.CLOCK_ALIGNED

        val sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS)
        val recentRecords = sleepRepository.getCompletedRecordsSince(sevenDaysAgo)
        val lastFeedSession = breastfeedingRepository.getLastSession()

        val defaultWakeWindows = SleepAgePriors.getDefaultWakeWindows(ageInWeeks)
        val wakeWindowBounds = SleepAgePriors.getWakeWindowBounds(ageInWeeks)

        val personalizationResult = personalizeFromData(
            recentRecords, defaultWakeWindows, wakeWindowBounds
        )
        val wakeWindows = personalizationResult.wakeWindows
        val averageNapDuration = personalizationResult.averageNapDuration
        val isPersonalized = personalizationResult.isPersonalized

        val storedWakeTime = settingsRepository.getWakeTime().first()
        val effectiveWakeTime = storedWakeTime ?: LocalTime.of(7, 0)

        val todayNaps = getTodayNaps(recentRecords)
        val shortNapAdjustment = if (todayNaps.isNotEmpty()) {
            val lastNap = todayNaps.last()
            val napDuration = lastNap.duration
            napDuration != null && napDuration < Duration.ofMinutes(30)
        } else false

        val napTimes = generateNapTimes(
            effectiveWakeTime, wakeWindows, averageNapDuration, ageInWeeks,
            mode, shortNapAdjustment
        )

        val bedtimeWindow = SleepAgePriors.getBedtimeWindow(ageInWeeks)
        val bedtime = calculateBedtime(napTimes, wakeWindows, bedtimeWindow)

        val totalSleepRecommendation = SleepAgePriors.getTotalSleepRecommendation(ageInWeeks)
        val totalSleepLogged = calculateAverageDailySleep(recentRecords)

        val regressionWarning = SleepAgePriors.detectRegression(ageInWeeks)
        val napTransitionSuggestion = detectNapTransition(recentRecords, ageInWeeks)

        return SleepSchedule(
            ageInWeeks = ageInWeeks,
            mode = mode,
            wakeWindows = wakeWindows,
            napTimes = napTimes,
            bedtime = bedtime,
            bedtimeWindow = bedtimeWindow,
            totalSleepRecommendation = totalSleepRecommendation,
            totalSleepLogged = totalSleepLogged,
            regressionWarning = regressionWarning,
            napTransitionSuggestion = napTransitionSuggestion,
            lastFeedTime = lastFeedSession?.startTime,
            isPersonalized = isPersonalized
        )
    }

    // --- Personalization ---

    private data class PersonalizationResult(
        val wakeWindows: List<Duration>,
        val averageNapDuration: Duration?,
        val isPersonalized: Boolean
    )

    private fun personalizeFromData(
        recentRecords: List<SleepRecord>,
        defaultWakeWindows: List<Duration>,
        wakeWindowBounds: Pair<Duration, Duration>
    ): PersonalizationResult {
        val completedNaps = recentRecords.filter { it.sleepType == SleepType.NAP && it.duration != null }

        if (completedNaps.size < 3) {
            return PersonalizationResult(defaultWakeWindows, null, false)
        }

        val avgNapDuration = completedNaps
            .mapNotNull { it.duration }
            .fold(Duration.ZERO) { acc, d -> acc.plus(d) }
            .dividedBy(completedNaps.size.toLong())

        val sortedRecords = recentRecords.sortedBy { it.startTime }
        val actualWakeWindows = mutableListOf<Duration>()
        for (i in 0 until sortedRecords.size - 1) {
            val currentEnd = sortedRecords[i].endTime ?: continue
            val nextStart = sortedRecords[i + 1].startTime
            val gap = Duration.between(currentEnd, nextStart)
            if (!gap.isNegative && gap < Duration.ofHours(6)) {
                actualWakeWindows.add(gap)
            }
        }

        if (actualWakeWindows.isEmpty()) {
            return PersonalizationResult(defaultWakeWindows, avgNapDuration, false)
        }

        val avgActualWakeWindow = actualWakeWindows
            .fold(Duration.ZERO) { acc, d -> acc.plus(d) }
            .dividedBy(actualWakeWindows.size.toLong())

        val (minBound, maxBound) = wakeWindowBounds
        val blendedWindows = defaultWakeWindows.map { defaultWw ->
            val blended = defaultWw.multipliedBy(40).plus(avgActualWakeWindow.multipliedBy(60)).dividedBy(100)
            clampDuration(blended, minBound, maxBound)
        }

        return PersonalizationResult(blendedWindows, avgNapDuration, true)
    }

    // --- Nap Schedule Generation ---

    private fun generateNapTimes(
        wakeUpTime: LocalTime,
        wakeWindows: List<Duration>,
        averageNapDuration: Duration?,
        ageInWeeks: Int,
        mode: ScheduleMode,
        shortNapAdjustment: Boolean
    ): List<ScheduleEntry> {
        val naps = mutableListOf<ScheduleEntry>()
        var currentTime = wakeUpTime
        val napCount = wakeWindows.size - 1

        for (i in 0 until napCount) {
            var ww = wakeWindows[i]

            val isAdjusted = shortNapAdjustment && i == 0
            if (isAdjusted) {
                ww = ww.minus(Duration.ofMinutes(15))
                if (ww.isNegative) ww = Duration.ofMinutes(15)
            }

            currentTime = currentTime.plus(ww)

            if (mode == ScheduleMode.CLOCK_ALIGNED) {
                currentTime = applyCircadianAlignment(currentTime, i, napCount)
            }

            val napDuration = averageNapDuration ?: getDefaultNapDuration(i, ageInWeeks)
            naps.add(
                ScheduleEntry(
                    startTime = currentTime,
                    duration = napDuration,
                    label = "Nap ${i + 1}",
                    isAdjusted = isAdjusted
                )
            )
            currentTime = currentTime.plus(napDuration)
        }
        return naps
    }

    private fun getDefaultNapDuration(napIndex: Int, ageInWeeks: Int): Duration = when {
        napIndex == 0 && ageInWeeks >= 24 -> Duration.ofMinutes(120)
        napIndex == 0 -> Duration.ofMinutes(90)
        else -> Duration.ofMinutes(60)
    }

    private fun applyCircadianAlignment(napTime: LocalTime, napIndex: Int, totalNaps: Int): LocalTime {
        if (napIndex == 0) {
            val target = LocalTime.of(9, 30)
            if (isWithinMinutes(napTime, target, 30)) {
                return shiftToward(napTime, target)
            }
        }
        if (isMiddayNap(napIndex, totalNaps)) {
            val target = LocalTime.of(13, 0)
            if (isWithinMinutes(napTime, target, 45)) {
                return shiftToward(napTime, target)
            }
        }
        return napTime
    }

    private fun isMiddayNap(napIndex: Int, totalNaps: Int): Boolean =
        (napIndex == 1 && totalNaps >= 2) || (napIndex == 0 && totalNaps == 1)

    private fun isWithinMinutes(time: LocalTime, target: LocalTime, minutes: Int): Boolean {
        val diff = kotlin.math.abs(Duration.between(time, target).toMinutes())
        return diff <= minutes
    }

    private fun shiftToward(time: LocalTime, target: LocalTime): LocalTime {
        val diffMinutes = Duration.between(time, target).toMinutes()
        val shiftMinutes = diffMinutes / 2
        return time.plusMinutes(shiftMinutes)
    }

    // --- Bedtime ---

    private fun calculateBedtime(
        napTimes: List<ScheduleEntry>,
        wakeWindows: List<Duration>,
        bedtimeWindow: ClosedRange<LocalTime>
    ): LocalTime {
        if (napTimes.isEmpty()) return bedtimeWindow.start

        val lastNap = napTimes.last()
        val lastNapEnd = lastNap.startTime.plus(lastNap.duration)
        val calculated = lastNapEnd.plus(wakeWindows.last())

        return clampLocalTime(calculated, bedtimeWindow)
    }

    // --- Total Sleep ---

    private fun calculateAverageDailySleep(recentRecords: List<SleepRecord>): Duration? {
        val completedRecords = recentRecords.filter { it.duration != null }
        if (completedRecords.isEmpty()) return null

        val zone = ZoneId.systemDefault()
        val dailySleep = completedRecords
            .groupBy { it.startTime.atZone(zone).toLocalDate() }
            .map { (_, records) ->
                records.mapNotNull { it.duration }.fold(Duration.ZERO) { acc, d -> acc.plus(d) }
            }

        if (dailySleep.isEmpty()) return null

        return dailySleep
            .fold(Duration.ZERO) { acc, d -> acc.plus(d) }
            .dividedBy(dailySleep.size.toLong())
    }

    // --- Nap Transition Detection ---

    private fun detectNapTransition(recentRecords: List<SleepRecord>, ageInWeeks: Int): String? {
        val completedRecords = recentRecords.filter { it.duration != null }
        if (completedRecords.size < 5) {
            return null
        }

        val zone = ZoneId.systemDefault()
        val dailyNapCounts = completedRecords
            .filter { it.sleepType == SleepType.NAP }
            .groupBy { it.startTime.atZone(zone).toLocalDate() }
            .mapValues { it.value.size }

        if (dailyNapCounts.size < 3) {
            return null
        }

        val avgNapsPerDay = dailyNapCounts.values.average()
        val expectedNaps = SleepAgePriors.getNapTransitionThreshold(ageInWeeks)

        val nightRecords = completedRecords.filter { it.sleepType == SleepType.NIGHT_SLEEP }
        val hasNapTransitionPattern = avgNapsPerDay < expectedNaps - 0.5 &&
            hasAdequateNightSleep(nightRecords)
        if (!hasNapTransitionPattern) {
            return null
        }

        val currentNaps = expectedNaps
        val targetNaps = (expectedNaps - 1).coerceAtLeast(1)
        return "Your baby may be ready to transition from $currentNaps to $targetNaps naps. " +
            "They've been averaging ${String.format(Locale.US, "%.1f", avgNapsPerDay)} naps per day " +
            "with good night sleep."
    }

    private fun hasAdequateNightSleep(nightRecords: List<SleepRecord>): Boolean {
        if (nightRecords.isEmpty()) {
            return false
        }

        val avgNightSleep = nightRecords
            .mapNotNull { it.duration }
            .fold(Duration.ZERO) { acc, d -> acc.plus(d) }
            .dividedBy(nightRecords.size.toLong())

        return avgNightSleep >= Duration.ofHours(10)
    }

    // --- Today's Naps ---

    private fun getTodayNaps(recentRecords: List<SleepRecord>): List<SleepRecord> {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        return recentRecords
            .filter { it.sleepType == SleepType.NAP }
            .filter { it.startTime.atZone(zone).toLocalDate() == today }
            .filter { it.endTime != null }
            .sortedBy { it.startTime }
    }

    // --- Utility ---

    private fun clampDuration(value: Duration, min: Duration, max: Duration): Duration = when {
        value < min -> min
        value > max -> max
        else -> value
    }

    private fun clampLocalTime(time: LocalTime, range: ClosedRange<LocalTime>): LocalTime = when {
        time < range.start -> range.start
        time > range.endInclusive -> range.endInclusive
        else -> time
    }
}
```

- [ ] **Step 3.2: Verify compilation**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

---

## Task 4: Migrate `GenerateSleepScheduleUseCaseTest` to call `SleepAgePriors` directly

**Files:**
- Modify: `app/src/test/java/com/babytracker/domain/usecase/sleep/GenerateSleepScheduleUseCaseTest.kt`

The four nested classes that called `internal` methods on the use case (`WakeWindowTests`, `BedtimeTests`, `TotalSleepTests`, `RegressionTests`) must be updated to call `SleepAgePriors.*` — otherwise they will fail with "unresolved reference" now that those `internal fun` no longer exist on the use case.

`ScheduleModeTests`, `NapCountTests`, `PersonalizationTests`, and `IntegrationTests` all drive the use case via `useCase(...)` — they are copied unchanged.

- [ ] **Step 4.1: Replace `GenerateSleepScheduleUseCaseTest.kt`**

Replace the full file:

```kotlin
package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.ScheduleMode
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.sleep.prior.SleepAgePriors
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class GenerateSleepScheduleUseCaseTest {

    private lateinit var sleepRepository: SleepRepository
    private lateinit var breastfeedingRepository: BreastfeedingRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: GenerateSleepScheduleUseCase

    @BeforeEach
    fun setUp() {
        sleepRepository = mockk()
        breastfeedingRepository = mockk()
        settingsRepository = mockk()
        useCase = GenerateSleepScheduleUseCase(sleepRepository, breastfeedingRepository, settingsRepository)
    }

    private fun babyOfAge(weeks: Int): Baby = Baby(
        name = "Test Baby",
        birthDate = LocalDate.now().minusWeeks(weeks.toLong())
    )

    private fun setupEmptyData() {
        coEvery { sleepRepository.getCompletedRecordsSince(any()) } returns emptyList()
        coEvery { breastfeedingRepository.getLastSession() } returns null
        every { settingsRepository.getWakeTime() } returns flowOf(null)
    }

    @Nested
    inner class ScheduleModeTests {
        @Test
        fun `baby under 16 weeks gets demand-driven mode`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(10))
            assertEquals(ScheduleMode.DEMAND_DRIVEN, schedule.mode)
        }

        @Test
        fun `baby at 16 weeks gets clock-aligned mode`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(16))
            assertEquals(ScheduleMode.CLOCK_ALIGNED, schedule.mode)
        }

        @Test
        fun `baby at 30 weeks gets clock-aligned mode`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(30))
            assertEquals(ScheduleMode.CLOCK_ALIGNED, schedule.mode)
        }
    }

    @Nested
    inner class WakeWindowTests {
        @Test
        fun `newborn 0-6 weeks has 5 wake windows of 45 minutes`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(3)
            assertEquals(5, windows.size)
            assertTrue(windows.all { it == Duration.ofMinutes(45) })
        }

        @Test
        fun `6-8 weeks has 4 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(7)
            assertEquals(4, windows.size)
            assertEquals(Duration.ofMinutes(60), windows[0])
            assertEquals(Duration.ofMinutes(75), windows[3])
        }

        @Test
        fun `2-3 months has 4 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(10)
            assertEquals(4, windows.size)
            assertEquals(Duration.ofMinutes(75), windows[0])
            assertEquals(Duration.ofMinutes(90), windows[3])
        }

        @Test
        fun `3-4 months has 3 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(14)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(90), windows[0])
            assertEquals(Duration.ofMinutes(120), windows[2])
        }

        @Test
        fun `4-6 months has 3 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(20)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(105), windows[0])
            assertEquals(Duration.ofMinutes(150), windows[2])
        }

        @Test
        fun `6-9 months has 3 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(30)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(150), windows[0])
            assertEquals(Duration.ofMinutes(210), windows[2])
        }

        @Test
        fun `9-12 months has 3 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(40)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(180), windows[0])
            assertEquals(Duration.ofMinutes(240), windows[2])
        }

        @Test
        fun `wake windows are graduated - first is shortest, last is longest`() {
            val ageBrackets = listOf(3, 7, 10, 14, 20, 30, 40)
            for (age in ageBrackets) {
                val windows = SleepAgePriors.getDefaultWakeWindows(age)
                assertTrue(
                    windows.first() <= windows.last(),
                    "Age $age weeks: first window should be <= last window"
                )
            }
        }
    }

    @Nested
    inner class NapCountTests {
        @Test
        fun `newborn gets 4 naps`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(3))
            assertEquals(4, schedule.napTimes.size)
        }

        @Test
        fun `6 week old gets 3 naps`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(7))
            assertEquals(3, schedule.napTimes.size)
        }

        @Test
        fun `4 month old gets 2 naps`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(20))
            assertEquals(2, schedule.napTimes.size)
        }

        @Test
        fun `8 month old gets 2 naps`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(32))
            assertEquals(2, schedule.napTimes.size)
        }
    }

    @Nested
    inner class BedtimeTests {
        @Test
        fun `newborn bedtime window is 9-11 PM`() {
            val window = SleepAgePriors.getBedtimeWindow(3)
            assertEquals(LocalTime.of(21, 0), window.start)
            assertEquals(LocalTime.of(23, 0), window.endInclusive)
        }

        @Test
        fun `6-12 week bedtime window is 8-10 PM`() {
            val window = SleepAgePriors.getBedtimeWindow(8)
            assertEquals(LocalTime.of(20, 0), window.start)
            assertEquals(LocalTime.of(22, 0), window.endInclusive)
        }

        @Test
        fun `3-4 month bedtime window is 7_30-9 PM`() {
            val window = SleepAgePriors.getBedtimeWindow(14)
            assertEquals(LocalTime.of(19, 30), window.start)
            assertEquals(LocalTime.of(21, 0), window.endInclusive)
        }

        @Test
        fun `5-6 month bedtime window is 7-8 PM`() {
            val window = SleepAgePriors.getBedtimeWindow(20)
            assertEquals(LocalTime.of(19, 0), window.start)
            assertEquals(LocalTime.of(20, 0), window.endInclusive)
        }

        @Test
        fun `7-12 month bedtime window is 6_30-8 PM`() {
            val window = SleepAgePriors.getBedtimeWindow(30)
            assertEquals(LocalTime.of(18, 30), window.start)
            assertEquals(LocalTime.of(20, 0), window.endInclusive)
        }

        @Test
        fun `bedtime is clamped within window`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(30))
            val window = schedule.bedtimeWindow
            assertTrue(
                schedule.bedtime >= window.start,
                "Bedtime ${schedule.bedtime} should be >= ${window.start}"
            )
            assertTrue(
                schedule.bedtime <= window.endInclusive,
                "Bedtime ${schedule.bedtime} should be <= ${window.endInclusive}"
            )
        }
    }

    @Nested
    inner class TotalSleepTests {
        @Test
        fun `0-4 months recommends 14-17 hours`() {
            val rec = SleepAgePriors.getTotalSleepRecommendation(10)
            assertEquals(Duration.ofHours(14), rec.start)
            assertEquals(Duration.ofHours(17), rec.endInclusive)
        }

        @Test
        fun `4-12 months recommends 12-16 hours`() {
            val rec = SleepAgePriors.getTotalSleepRecommendation(20)
            assertEquals(Duration.ofHours(12), rec.start)
            assertEquals(Duration.ofHours(16), rec.endInclusive)
        }

        @Test
        fun `no logged data returns null totalSleepLogged`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(20))
            assertNull(schedule.totalSleepLogged)
        }
    }

    @Nested
    inner class RegressionTests {
        @Test
        fun `4 month regression detected at 16 weeks`() {
            val info = SleepAgePriors.detectRegression(16)
            assertNotNull(info)
            assertEquals("4-Month Sleep Regression", info!!.name)
        }

        @Test
        fun `4 month regression detected at 20 weeks`() {
            val info = SleepAgePriors.detectRegression(20)
            assertNotNull(info)
            assertEquals("4-Month Sleep Regression", info!!.name)
        }

        @Test
        fun `8-10 month regression detected at 36 weeks`() {
            val info = SleepAgePriors.detectRegression(36)
            assertNotNull(info)
            assertEquals("8-10 Month Sleep Regression", info!!.name)
        }

        @Test
        fun `12 month regression detected at 50 weeks`() {
            val info = SleepAgePriors.detectRegression(50)
            assertNotNull(info)
            assertEquals("12-Month Sleep Regression", info!!.name)
        }

        @Test
        fun `no regression at 10 weeks`() {
            assertNull(SleepAgePriors.detectRegression(10))
        }

        @Test
        fun `no regression at 26 weeks`() {
            assertNull(SleepAgePriors.detectRegression(26))
        }

        @Test
        fun `no regression at 46 weeks`() {
            assertNull(SleepAgePriors.detectRegression(46))
        }
    }

    @Nested
    inner class PersonalizationTests {
        @Test
        fun `not personalized with fewer than 3 records`() = runTest {
            val records = listOf(
                createSleepRecord(SleepType.NAP, hoursAgo = 5, durationMin = 60),
                createSleepRecord(SleepType.NAP, hoursAgo = 3, durationMin = 45)
            )
            coEvery { sleepRepository.getCompletedRecordsSince(any()) } returns records
            coEvery { breastfeedingRepository.getLastSession() } returns null
            every { settingsRepository.getWakeTime() } returns flowOf(null)

            val schedule = useCase(babyOfAge(20))
            assertFalse(schedule.isPersonalized)
        }

        @Test
        fun `personalized with 3 or more nap records`() = runTest {
            val baseTime = Instant.now().minus(2, ChronoUnit.DAYS)
            val records = listOf(
                SleepRecord(id = 1, startTime = baseTime, endTime = baseTime.plus(Duration.ofMinutes(60)), sleepType = SleepType.NAP),
                SleepRecord(id = 2, startTime = baseTime.plus(Duration.ofHours(3)), endTime = baseTime.plus(Duration.ofHours(4)), sleepType = SleepType.NAP),
                SleepRecord(id = 3, startTime = baseTime.plus(Duration.ofHours(7)), endTime = baseTime.plus(Duration.ofHours(8)), sleepType = SleepType.NAP),
                SleepRecord(id = 4, startTime = baseTime.plus(Duration.ofHours(26)), endTime = baseTime.plus(Duration.ofHours(27)), sleepType = SleepType.NAP),
                SleepRecord(id = 5, startTime = baseTime.plus(Duration.ofHours(29)), endTime = baseTime.plus(Duration.ofHours(30)), sleepType = SleepType.NAP)
            )
            coEvery { sleepRepository.getCompletedRecordsSince(any()) } returns records
            coEvery { breastfeedingRepository.getLastSession() } returns null
            every { settingsRepository.getWakeTime() } returns flowOf(null)

            val schedule = useCase(babyOfAge(20))
            assertTrue(schedule.isPersonalized)
        }

        @Test
        fun `last feed time is populated when available`() = runTest {
            setupEmptyData()
            val feedTime = Instant.now().minus(2, ChronoUnit.HOURS)
            coEvery { breastfeedingRepository.getLastSession() } returns BreastfeedingSession(
                id = 1,
                startTime = feedTime,
                endTime = feedTime.plus(Duration.ofMinutes(15)),
                startingSide = BreastSide.LEFT
            )

            val schedule = useCase(babyOfAge(20))
            assertEquals(feedTime, schedule.lastFeedTime)
        }

        @Test
        fun `last feed time is null when no sessions`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(20))
            assertNull(schedule.lastFeedTime)
        }
    }

    @Nested
    inner class IntegrationTests {
        @Test
        fun `schedule has correct structure for newborn`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(2))

            assertEquals(ScheduleMode.DEMAND_DRIVEN, schedule.mode)
            assertEquals(4, schedule.napTimes.size)
            assertFalse(schedule.isPersonalized)
            assertNotNull(schedule.bedtimeWindow)
            assertNotNull(schedule.totalSleepRecommendation)
        }

        @Test
        fun `schedule has correct structure for 8 month old`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(32))

            assertEquals(ScheduleMode.CLOCK_ALIGNED, schedule.mode)
            assertEquals(2, schedule.napTimes.size)
            assertNotNull(schedule.regressionWarning)
            assertEquals("8-10 Month Sleep Regression", schedule.regressionWarning!!.name)
        }

        @Test
        fun `nap times are in chronological order`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(20))

            for (i in 0 until schedule.napTimes.size - 1) {
                assertTrue(
                    schedule.napTimes[i].startTime < schedule.napTimes[i + 1].startTime,
                    "Nap ${i + 1} should be before nap ${i + 2}"
                )
            }
        }

        @Test
        fun `stored wake time shifts entire schedule`() = runTest {
            coEvery { sleepRepository.getCompletedRecordsSince(any()) } returns emptyList()
            coEvery { breastfeedingRepository.getLastSession() } returns null

            every { settingsRepository.getWakeTime() } returns flowOf(LocalTime.of(6, 0))
            val scheduleEarly = useCase(babyOfAge(8))

            every { settingsRepository.getWakeTime() } returns flowOf(LocalTime.of(8, 0))
            val scheduleLate = useCase(babyOfAge(8))

            assertTrue(scheduleEarly.napTimes[0].startTime < scheduleLate.napTimes[0].startTime)
        }

        @Test
        fun `null stored wake time falls back to 7am`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(20))
            assertTrue(schedule.napTimes[0].startTime >= LocalTime.of(7, 0))
            assertTrue(schedule.napTimes[0].startTime < LocalTime.of(11, 0))
        }
    }

    private fun createSleepRecord(
        type: SleepType,
        hoursAgo: Long = 5,
        durationMin: Long = 60
    ): SleepRecord {
        val start = Instant.now().minus(hoursAgo, ChronoUnit.HOURS)
        return SleepRecord(
            id = 0,
            startTime = start,
            endTime = start.plus(Duration.ofMinutes(durationMin)),
            sleepType = type
        )
    }
}
```

- [ ] **Step 4.2: Run both test suites — expect all pass**

```bash
./gradlew :app:testDebugUnitTest --tests "com.babytracker.domain.sleep.prior.*" --tests "com.babytracker.domain.usecase.sleep.*" 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL. All tests pass.

---

## Task 5: Format, lint, full suite, commit

**Files:** No changes — verify then commit all four files together.

- [ ] **Step 5.1: Auto-fix formatting**

```bash
./gradlew ktlintFormat 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5.2: Static analysis**

```bash
./gradlew detekt 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL. Fix any violation in the offending file without using `@Suppress`.

- [ ] **Step 5.3: Full fast unit-test suite**

```bash
./gradlew :app:testDebugUnitTest -PfastTests 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL. All tests pass.

- [ ] **Step 5.4: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/sleep/prior/SleepAgePriors.kt
git add app/src/main/java/com/babytracker/domain/usecase/sleep/GenerateSleepScheduleUseCase.kt
git add app/src/test/java/com/babytracker/domain/sleep/prior/SleepAgePriorsTest.kt
git add app/src/test/java/com/babytracker/domain/usecase/sleep/GenerateSleepScheduleUseCaseTest.kt
git commit -m "refactor(sleep): extract SleepAgePriors from GenerateSleepScheduleUseCase [AKA-86]"
```

---

## Acceptance Criteria

- [ ] `SleepAgePriors` lives at `domain/sleep/prior/` with zero Android/framework imports (`java.time.*` and domain model only)
- [ ] `GenerateSleepScheduleUseCase` has no prior method bodies; all six prior calls delegate to `SleepAgePriors.*`
- [ ] All tests pass — both `SleepAgePriorsTest` (new) and the full `GenerateSleepScheduleUseCaseTest` (migrated)
- [ ] `./gradlew ktlintFormat detekt` is clean
