# Home Daily Feeding Summary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **UI CRAFT GATE:** The summary card task MUST begin by invoking the `impeccable` skill (craft mode).

**LINEAR_ISSUE:** AKA-113

**Goal:** Show today's aggregated feeding summary (total bottle volume + total feed count across bottle and breastfeeding) on the Home screen.

**Architecture:** A new `ObserveTodayFeedingSummaryUseCase` combines breastfeeding history and bottle feeds, filters to the current day, and emits a `TodayFeedingSummary`. `HomeViewModel` wraps its existing `combine` graph in one additional outer `combine` that folds in the summary and the volume-unit preference (avoids touching the already-maxed inner combines). `HomeScreen` renders a compact summary card using `formatVolume`.

**Tech Stack:** Compose Material 3, Hilt, Coroutines/Flow `combine`, JUnit 5 + MockK + Turbine.

**Dependencies:** Plan 01 (`BottleFeed`), Plan 03 (`ObserveBottleFeedsUseCase`), Plan 02 (`VolumeUnit`, `formatVolume`). Existing `GetBreastfeedingHistoryUseCase`, `SettingsRepository`. Independent of plan 05 (defines its own summary model).

**Suggested implementation branch:** `feat/home-feeding-summary`

---

## File Structure

- Create `app/src/main/java/com/babytracker/domain/model/TodayFeedingSummary.kt`.
- Create `app/src/main/java/com/babytracker/domain/usecase/feeding/ObserveTodayFeedingSummaryUseCase.kt`.
- Modify `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt` — add to state + outer combine.
- Modify `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt` — summary card.
- Modify `app/src/main/res/values/strings.xml`.
- Tests: `ObserveTodayFeedingSummaryUseCaseTest`, extend `HomeViewModelTest`, optional `HomeScreenTest` assertion.

---

## Task 1: TodayFeedingSummary model

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/TodayFeedingSummary.kt`

- [ ] **Step 1: Create the model**

```kotlin
package com.babytracker.domain.model

data class TodayFeedingSummary(
    val bottleVolumeMl: Int = 0,
    val bottleCount: Int = 0,
    val breastfeedingCount: Int = 0,
) {
    val totalFeedCount: Int get() = bottleCount + breastfeedingCount
    val hasAny: Boolean get() = totalFeedCount > 0
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/TodayFeedingSummary.kt
git commit -m "feat(breastfeeding): add TodayFeedingSummary model"
```

---

## Task 2: ObserveTodayFeedingSummaryUseCase (TDD)

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/feeding/ObserveTodayFeedingSummaryUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/feeding/ObserveTodayFeedingSummaryUseCaseTest.kt`

Behaviour: combine breastfeeding sessions + bottle feeds; keep only entries whose local date (in the injected zone) equals `now()`'s local date; sum bottle volume, count bottles, count breastfeeding sessions.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.domain.usecase.feeding

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.usecase.bottlefeed.ObserveBottleFeedsUseCase
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class ObserveTodayFeedingSummaryUseCaseTest {

    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-06-10T20:00:00Z")

    @Test
    fun `sums today's feeds and ignores other days`() = runTest {
        val todayBottleA = BottleFeed(1, Instant.parse("2026-06-10T08:00:00Z"), 120, FeedType.FORMULA, createdAt = Instant.EPOCH)
        val todayBottleB = BottleFeed(2, Instant.parse("2026-06-10T12:00:00Z"), 90, FeedType.BREAST_MILK, createdAt = Instant.EPOCH)
        val yesterdayBottle = BottleFeed(3, Instant.parse("2026-06-09T12:00:00Z"), 200, FeedType.FORMULA, createdAt = Instant.EPOCH)
        val todaySession = BreastfeedingSession(1, Instant.parse("2026-06-10T15:00:00Z"), Instant.parse("2026-06-10T15:10:00Z"), BreastSide.LEFT)
        val yesterdaySession = BreastfeedingSession(2, Instant.parse("2026-06-09T15:00:00Z"), Instant.parse("2026-06-09T15:10:00Z"), BreastSide.RIGHT)

        val getBreastfeeding = mockk<GetBreastfeedingHistoryUseCase>()
        val observeBottles = mockk<ObserveBottleFeedsUseCase>()
        every { getBreastfeeding() } returns flowOf(listOf(todaySession, yesterdaySession))
        every { observeBottles() } returns flowOf(listOf(todayBottleA, todayBottleB, yesterdayBottle))

        val summary = ObserveTodayFeedingSummaryUseCase(getBreastfeeding, observeBottles, zone) { now }().first()

        assertEquals(210, summary.bottleVolumeMl)
        assertEquals(2, summary.bottleCount)
        assertEquals(1, summary.breastfeedingCount)
        assertEquals(3, summary.totalFeedCount)
    }
}
```

- [ ] **Step 2: Run to verify it fails** → FAIL.

- [ ] **Step 3: Implement**

```kotlin
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
```

- [ ] **Step 4: Run to verify it passes** → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/feeding/ObserveTodayFeedingSummaryUseCase.kt app/src/test/java/com/babytracker/domain/usecase/feeding/ObserveTodayFeedingSummaryUseCaseTest.kt
git commit -m "feat(usecase): observe today's feeding summary"
```

> `ZoneId` and `now: () -> Instant` are provided by Hilt. If a `ZoneId` provider was added in plan 05 reuse it; otherwise add `@Provides fun provideZoneId(): ZoneId = ZoneId.systemDefault()` to an existing app module. Confirm with `rg "ZoneId" app/src/main/java/com/babytracker/di`.

---

## Task 3: Wire into HomeViewModel (TDD)

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt`
- Test: extend `app/src/test/java/com/babytracker/ui/home/HomeViewModelTest.kt`

- [ ] **Step 1: Add to `HomeUiState`**

```kotlin
val todayFeedingSummary: TodayFeedingSummary = TodayFeedingSummary(),
val volumeUnit: VolumeUnit = VolumeUnit.ML,
```
Imports: `com.babytracker.domain.model.TodayFeedingSummary`, `com.babytracker.domain.model.VolumeUnit`.

- [ ] **Step 2: Inject the use case** — add to the constructor:
```kotlin
observeTodayFeedingSummary: ObserveTodayFeedingSummaryUseCase,
```
Import it.

- [ ] **Step 3: Wrap the existing combine in one outer combine.** The current `val uiState: StateFlow<HomeUiState> = combine(...) { ... }.stateIn(...)` becomes:

```kotlin
private val baseState = combine(
    // ... the ENTIRE existing combine(...) { partial, pumpingActive, inventorySummary, sleepPrediction -> partial.copy(...) } expression, unchanged ...
)

val uiState: StateFlow<HomeUiState> = combine(
    baseState,
    observeTodayFeedingSummary(),
    settingsRepository.getVolumeUnit(),
) { base, summary, unit ->
    base.copy(todayFeedingSummary = summary, volumeUnit = unit)
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.Eagerly,
    initialValue = HomeUiState(),
)
```

> Move `.stateIn(...)` from the old expression onto the new outer combine only. The inner graph keeps emitting `HomeUiState` (now treated as the partial `base`). `settingsRepository` is already a constructor field.

- [ ] **Step 4: Extend the test** — add stubs for the new flows in the existing `HomeViewModelTest` setup so the combine completes, and assert the summary surfaces:

```kotlin
every { observeTodayFeedingSummary() } returns flowOf(
    TodayFeedingSummary(bottleVolumeMl = 150, bottleCount = 1, breastfeedingCount = 2),
)
every { settingsRepository.getVolumeUnit() } returns flowOf(VolumeUnit.ML)
// ...
assertEquals(150, viewModel.uiState.value.todayFeedingSummary.bottleVolumeMl)
```

- [ ] **Step 5: Run tests**

Run: `./gradlew test --tests "com.babytracker.ui.home.HomeViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt app/src/test/java/com/babytracker/ui/home/HomeViewModelTest.kt
git commit -m "feat(breastfeeding): surface today feeding summary in HomeViewModel"
```

---

## Task 4: Home summary card UI

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: REQUIRED — invoke `impeccable` (craft mode)** to design a compact "Today" feeding card consistent with the existing Home cards (inventory summary, prediction). Show total bottle volume via `formatVolume(summary.bottleVolumeMl, uiState.volumeUnit)` and `summary.totalFeedCount` feeds. Hide or show an empty hint when `!summary.hasAny`.

- [ ] **Step 2: Add strings**

```xml
<string name="home_today_feeding_title">Today\'s feeding</string>
<string name="home_today_feeding_summary">%1$s · %2$d feeds</string>
<string name="home_today_feeding_empty">No feeds logged yet today</string>
```

- [ ] **Step 3: Render the card** in `HomeScreen` reading `uiState.todayFeedingSummary` and `uiState.volumeUnit`, placed near the existing summary cards.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/home/HomeScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(breastfeeding): add today feeding summary card to home"
```

---

## Acceptance Criteria

- Home shows today's total bottle volume (in the user's unit) and total feed count (bottle + breastfeeding).
- Counts/volumes reset at local midnight (day computed in the injected zone) and update live as feeds are logged.
- `HomeViewModel`'s existing combine graph is preserved; only one outer combine is added.
- Summary card built under `impeccable` craft. `./gradlew test` passes.

## Self-Review Notes

- `TodayFeedingSummary` is defined here (not reused from plan 05's `DailyFeedingTotals`) so this plan does not depend on plan 05.
- The outer-combine wrap avoids editing the already-large inner combines (which are at the typed-overload limit), reducing regression risk on Home.
- Day boundary uses an injected `ZoneId` for testability; `now()` injected for deterministic "today".
