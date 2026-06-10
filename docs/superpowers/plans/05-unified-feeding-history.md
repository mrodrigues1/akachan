# Unified Feeding History + Per-Day Stats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **UI CRAFT GATE:** Every task that builds or changes a composable MUST begin by invoking the `impeccable` skill (craft mode).

**LINEAR_ISSUE:** AKA-112

**Goal:** A single chronological feeding history that interleaves bottle and breastfeeding entries, grouped by day with per-day totals (volume + feed count), where bottle entries can be edited (reusing the plan-04 sheet) and deleted (with confirmation), mirroring the milk-stash list interactions.

**Architecture:** `ObserveFeedingHistoryUseCase` combines `GetBreastfeedingHistoryUseCase` and `ObserveBottleFeedsUseCase` into a time-sorted `List<FeedEntry>` (a sealed domain type). A pure `groupFeedEntriesByDay(...)` function turns that into `List<FeedingDayGroup>` with computed `DailyFeedingTotals`. `FeedingHistoryViewModel` exposes the groups plus the volume unit and a delete handler. `UnifiedFeedingHistoryScreen` renders day headers + `HistoryCard` rows; tapping a bottle row opens the reused `BottleFeedSheet` in edit mode; long-press/overflow on a bottle row opens a delete confirmation. Breastfeeding rows are read-only here (their editing lives in the breastfeeding feature).

**Tech Stack:** Compose Material 3, Hilt, Coroutines/Flow `combine`, JUnit 5 + MockK + Turbine, Compose UI test.

**Dependencies:** Plan 01 (`BottleFeed`), Plan 02 (`VolumeUnit`, `formatVolume`), Plan 03 (`ObserveBottleFeedsUseCase`, `DeleteBottleFeedUseCase`), Plan 04 (`BottleFeedSheet`, `BottleFeedViewModel.loadForEdit`). Existing `GetBreastfeedingHistoryUseCase`.

**Suggested implementation branch:** `feat/unified-feeding-history`

---

## File Structure

- Create `app/src/main/java/com/babytracker/domain/model/FeedEntry.kt` — sealed entry + `DailyFeedingTotals` + `FeedingDayGroup`.
- Create `app/src/main/java/com/babytracker/domain/usecase/feeding/ObserveFeedingHistoryUseCase.kt`.
- Create `app/src/main/java/com/babytracker/domain/usecase/feeding/GroupFeedEntries.kt` — pure grouping function.
- Create `app/src/main/java/com/babytracker/ui/feeding/FeedingHistoryViewModel.kt`.
- Create `app/src/main/java/com/babytracker/ui/feeding/UnifiedFeedingHistoryScreen.kt`.
- Modify `app/src/main/java/com/babytracker/navigation/Routes.kt` — `FEEDING_HISTORY`.
- Modify `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt` — route + Home callback.
- Modify `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt` — entry to feeding history.
- Modify `app/src/main/res/values/strings.xml`.
- Tests: `ObserveFeedingHistoryUseCaseTest`, `GroupFeedEntriesTest`, `FeedingHistoryViewModelTest`, `UnifiedFeedingHistoryScreenTest`.

---

## Task 1: FeedEntry + grouping models

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/FeedEntry.kt`

- [ ] **Step 1: Create the models**

```kotlin
package com.babytracker.domain.model

import java.time.Instant
import java.time.LocalDate

sealed interface FeedEntry {
    val timestamp: Instant

    data class Bottle(val feed: BottleFeed) : FeedEntry {
        override val timestamp: Instant get() = feed.timestamp
    }

    data class Breastfeeding(val session: BreastfeedingSession) : FeedEntry {
        override val timestamp: Instant get() = session.startTime
    }
}

data class DailyFeedingTotals(
    val bottleVolumeMl: Int,
    val bottleCount: Int,
    val breastfeedingCount: Int,
) {
    val totalFeedCount: Int get() = bottleCount + breastfeedingCount
}

data class FeedingDayGroup(
    val date: LocalDate,
    val totals: DailyFeedingTotals,
    val entries: List<FeedEntry>,
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/model/FeedEntry.kt
git commit -m "feat(breastfeeding): add unified FeedEntry and daily totals models"
```

---

## Task 2: ObserveFeedingHistoryUseCase (TDD)

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/feeding/ObserveFeedingHistoryUseCase.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/feeding/ObserveFeedingHistoryUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.domain.usecase.feeding

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedEntry
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

class ObserveFeedingHistoryUseCaseTest {

    @Test
    fun `interleaves bottle and breastfeeding sorted newest first`() = runTest {
        val bottle = BottleFeed(
            id = 1, timestamp = Instant.ofEpochMilli(2_000), volumeMl = 100,
            type = FeedType.FORMULA, createdAt = Instant.ofEpochMilli(2_000),
        )
        val session = BreastfeedingSession(
            id = 1, startTime = Instant.ofEpochMilli(3_000), endTime = Instant.ofEpochMilli(3_500),
            startingSide = BreastSide.LEFT,
        )
        val getBreastfeeding = mockk<GetBreastfeedingHistoryUseCase>()
        val observeBottles = mockk<ObserveBottleFeedsUseCase>()
        every { getBreastfeeding() } returns flowOf(listOf(session))
        every { observeBottles() } returns flowOf(listOf(bottle))

        val result = ObserveFeedingHistoryUseCase(getBreastfeeding, observeBottles)().first()

        assertEquals(2, result.size)
        // session at 3_000 is newer than bottle at 2_000
        assertEquals(Instant.ofEpochMilli(3_000), result[0].timestamp)
        assertEquals(FeedEntry.Bottle(bottle), result[1])
    }
}
```

- [ ] **Step 2: Run to verify it fails** → FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.domain.usecase.feeding

import com.babytracker.domain.model.FeedEntry
import com.babytracker.domain.usecase.bottlefeed.ObserveBottleFeedsUseCase
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ObserveFeedingHistoryUseCase @Inject constructor(
    private val getBreastfeedingHistory: GetBreastfeedingHistoryUseCase,
    private val observeBottleFeeds: ObserveBottleFeedsUseCase,
) {
    operator fun invoke(): Flow<List<FeedEntry>> =
        combine(getBreastfeedingHistory(), observeBottleFeeds()) { sessions, bottles ->
            val entries = sessions.map { FeedEntry.Breastfeeding(it) } +
                bottles.map { FeedEntry.Bottle(it) }
            entries.sortedByDescending { it.timestamp }
        }
}
```

- [ ] **Step 4: Run to verify it passes** → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/feeding/ObserveFeedingHistoryUseCase.kt app/src/test/java/com/babytracker/domain/usecase/feeding/ObserveFeedingHistoryUseCaseTest.kt
git commit -m "feat(usecase): combine bottle and breastfeeding into feeding history"
```

---

## Task 3: groupFeedEntriesByDay pure function (TDD)

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/usecase/feeding/GroupFeedEntries.kt`
- Test: `app/src/test/java/com/babytracker/domain/usecase/feeding/GroupFeedEntriesTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.domain.usecase.feeding

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedEntry
import com.babytracker.domain.model.FeedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class GroupFeedEntriesTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun `groups by day and sums bottle volume and counts`() {
        val day1Bottle = FeedEntry.Bottle(
            BottleFeed(1, Instant.parse("2026-06-01T08:00:00Z"), 120, FeedType.FORMULA, createdAt = Instant.EPOCH),
        )
        val day1BottleB = FeedEntry.Bottle(
            BottleFeed(2, Instant.parse("2026-06-01T12:00:00Z"), 90, FeedType.BREAST_MILK, createdAt = Instant.EPOCH),
        )
        val day1Breast = FeedEntry.Breastfeeding(
            BreastfeedingSession(1, Instant.parse("2026-06-01T15:00:00Z"), Instant.parse("2026-06-01T15:10:00Z"), BreastSide.LEFT),
        )
        val day2Bottle = FeedEntry.Bottle(
            BottleFeed(3, Instant.parse("2026-06-02T09:00:00Z"), 150, FeedType.FORMULA, createdAt = Instant.EPOCH),
        )

        val groups = groupFeedEntriesByDay(listOf(day2Bottle, day1Breast, day1BottleB, day1Bottle), zone)

        assertEquals(2, groups.size)
        // newest day first
        assertEquals(150, groups[0].totals.bottleVolumeMl)
        assertEquals(1, groups[0].totals.totalFeedCount)
        // day 1: 120 + 90 = 210 ml, 2 bottles + 1 breast = 3 feeds
        assertEquals(210, groups[1].totals.bottleVolumeMl)
        assertEquals(2, groups[1].totals.bottleCount)
        assertEquals(1, groups[1].totals.breastfeedingCount)
        assertEquals(3, groups[1].totals.totalFeedCount)
        // entries within a day are newest-first
        assertEquals(Instant.parse("2026-06-01T15:00:00Z"), groups[1].entries.first().timestamp)
    }
}
```

- [ ] **Step 2: Run to verify it fails** → FAIL.

- [ ] **Step 3: Implement**

```kotlin
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
```

- [ ] **Step 4: Run to verify it passes** → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/domain/usecase/feeding/GroupFeedEntries.kt app/src/test/java/com/babytracker/domain/usecase/feeding/GroupFeedEntriesTest.kt
git commit -m "feat(usecase): group feeding entries by day with totals"
```

---

## Task 4: FeedingHistoryViewModel (TDD)

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/feeding/FeedingHistoryViewModel.kt`
- Test: `app/src/test/java/com/babytracker/ui/feeding/FeedingHistoryViewModelTest.kt`

`FeedingHistoryUiState`: `days: List<FeedingDayGroup> = emptyList()`, `volumeUnit: VolumeUnit = ML`, `isLoading: Boolean = true`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.babytracker.ui.feeding

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedEntry
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.bottlefeed.DeleteBottleFeedUseCase
import com.babytracker.domain.usecase.feeding.ObserveFeedingHistoryUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class FeedingHistoryViewModelTest {

    private val observe = mockk<ObserveFeedingHistoryUseCase>()
    private val delete = mockk<DeleteBottleFeedUseCase>(relaxed = true)
    private val settings = mockk<SettingsRepository>()
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach fun setup() = Dispatchers.setMain(dispatcher)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `maps entries into day groups`() = runTest {
        val bottle = BottleFeed(1, Instant.parse("2026-06-01T08:00:00Z"), 120, FeedType.FORMULA, createdAt = Instant.EPOCH)
        every { observe() } returns flowOf(listOf(FeedEntry.Bottle(bottle)))
        every { settings.getVolumeUnit() } returns flowOf(VolumeUnit.ML)

        val vm = FeedingHistoryViewModel(observe, delete, settings, ZoneId.of("UTC"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.days.size)
        assertEquals(120, vm.uiState.value.days.first().totals.bottleVolumeMl)
    }

    @Test
    fun `onDeleteBottle delegates to use case`() = runTest {
        val bottle = BottleFeed(1, Instant.parse("2026-06-01T08:00:00Z"), 120, FeedType.FORMULA, createdAt = Instant.EPOCH)
        every { observe() } returns flowOf(emptyList())
        every { settings.getVolumeUnit() } returns flowOf(VolumeUnit.ML)

        val vm = FeedingHistoryViewModel(observe, delete, settings, ZoneId.of("UTC"))
        vm.onDeleteBottle(bottle)
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { delete(bottle) }
    }
}
```

- [ ] **Step 2: Run to verify it fails** → FAIL.

- [ ] **Step 3: Implement**

```kotlin
package com.babytracker.ui.feeding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedingDayGroup
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.bottlefeed.DeleteBottleFeedUseCase
import com.babytracker.domain.usecase.feeding.ObserveFeedingHistoryUseCase
import com.babytracker.domain.usecase.feeding.groupFeedEntriesByDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class FeedingHistoryUiState(
    val days: List<FeedingDayGroup> = emptyList(),
    val volumeUnit: VolumeUnit = VolumeUnit.ML,
    val isLoading: Boolean = true,
)

@HiltViewModel
class FeedingHistoryViewModel @Inject constructor(
    observeFeedingHistory: ObserveFeedingHistoryUseCase,
    private val deleteBottleFeed: DeleteBottleFeedUseCase,
    settingsRepository: SettingsRepository,
    private val zone: ZoneId,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedingHistoryUiState())
    val uiState: StateFlow<FeedingHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                observeFeedingHistory(),
                settingsRepository.getVolumeUnit(),
            ) { entries, unit ->
                FeedingHistoryUiState(
                    days = groupFeedEntriesByDay(entries, zone),
                    volumeUnit = unit,
                    isLoading = false,
                )
            }.collect { next -> _uiState.update { next } }
        }
    }

    fun onDeleteBottle(feed: BottleFeed) {
        viewModelScope.launch { deleteBottleFeed(feed) }
    }
}
```

> Provide `ZoneId` via Hilt. If no `ZoneId` provider exists, add one in a small `@Provides fun provideZoneId(): ZoneId = ZoneId.systemDefault()` in an existing app-level module (e.g. `DispatchersModule` or `DatabaseModule`'s companion module). Confirm one doesn't already exist first with `rg "ZoneId" app/src/main/java/com/babytracker/di`.

- [ ] **Step 4: Run to verify it passes** → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/feeding/FeedingHistoryViewModel.kt app/src/test/java/com/babytracker/ui/feeding/FeedingHistoryViewModelTest.kt
git commit -m "feat(breastfeeding): add FeedingHistoryViewModel"
```

---

## Task 5: UnifiedFeedingHistoryScreen (impeccable) + route + entry + edit/delete

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/feeding/UnifiedFeedingHistoryScreen.kt`
- Modify: `app/src/main/java/com/babytracker/navigation/Routes.kt`, `AppNavGraph.kt`, `HomeScreen.kt`, `strings.xml`

- [ ] **Step 1: REQUIRED — invoke `impeccable` (craft mode)** for: the day-header with totals chip, the bottle vs breastfeeding `HistoryCard` rows (distinct badge emoji/colour — e.g. 🍼 bottle, 🤱 breastfeeding), the empty state, and the delete confirmation dialog. Match the existing `BreastfeedingHistoryScreen`/`HistoryCard` styling.

- [ ] **Step 2: Add route + strings**

`Routes.kt`:
```kotlin
const val FEEDING_HISTORY = "feeding/history"
```
`strings.xml`:
```xml
<string name="feeding_history_title">Feeding history</string>
<string name="feeding_history_empty">No feeds logged yet</string>
<string name="feeding_history_day_totals">%1$s · %2$d feeds</string>
<string name="feeding_delete_title">Delete feed?</string>
<string name="feeding_delete_message">This bottle feed will be removed. This cannot be undone.</string>
<string name="delete">Delete</string>
```

- [ ] **Step 3: Build the screen.** Structure:
  - `Scaffold` + `TopAppBar(title = feeding_history_title, navigationIcon = back)`.
  - Host a `BottleFeedViewModel = hiltViewModel()` for edits; keep local `var editing by remember { mutableStateOf(false) }` and `var pendingDelete by remember { mutableStateOf<BottleFeed?>(null) }`.
  - `LazyColumn` over `uiState.days`: a `stickyHeader`/header composable showing `day.date` + `formatVolume(day.totals.bottleVolumeMl, uiState.volumeUnit)` and `totalFeedCount`; then each `entry`:
    - `FeedEntry.Bottle` → `HistoryCard(title = type label, subtitle = time + optional "from stash", trailing = formatVolume(feed.volumeMl, unit), badgeEmoji = "🍼", onClick = { bottleVm.loadForEdit(feed...); editing = true })` with a long-press/overflow setting `pendingDelete = feed`.
    - `FeedEntry.Breastfeeding` → `HistoryCard(... badgeEmoji = "🤱", trailing = duration, onClick = null)` (read-only here).
  - When `editing`, render `BottleFeedSheet(state = bottleState, ... onDismiss = { editing = false })`; observe `bottleState.saved` to close: `LaunchedEffect(bottleState.saved) { if (bottleState.saved) editing = false }`.
  - When `pendingDelete != null`, show an `AlertDialog` (confirm → `viewModel.onDeleteBottle(it); pendingDelete = null`).

```kotlin
@Composable
fun UnifiedFeedingHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: FeedingHistoryViewModel = hiltViewModel(),
    bottleViewModel: BottleFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bottleState by bottleViewModel.uiState.collectAsStateWithLifecycle()
    // ... Scaffold + LazyColumn per Step 3 ...
}
```

- [ ] **Step 4: Register route + Home entry.** In `AppNavGraph.kt`:
```kotlin
composable(Routes.FEEDING_HISTORY) {
    UnifiedFeedingHistoryScreen(onNavigateBack = { navController.popBackStack() })
}
```
Add `onNavigateToFeedingHistory = { navController.navigate(Routes.FEEDING_HISTORY) }` to the Home composable block, and the matching `onNavigateToFeedingHistory: () -> Unit = {}` param + entry affordance in `HomeScreen.kt`.

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/feeding/UnifiedFeedingHistoryScreen.kt app/src/main/java/com/babytracker/navigation/Routes.kt app/src/main/java/com/babytracker/navigation/AppNavGraph.kt app/src/main/java/com/babytracker/ui/home/HomeScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(breastfeeding): add unified feeding history screen with edit and delete"
```

---

## Task 6: Screen UI test

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/ui/feeding/UnifiedFeedingHistoryScreenTest.kt`

- [ ] **Step 1: Write tests** with `createComposeRule()` + seeded VM state (or fakes):
  - Renders day header with totals and both a bottle and a breastfeeding row.
  - Tapping a bottle row opens the edit sheet.
  - Triggering delete shows the confirmation dialog; confirming invokes the delete handler.
  - Empty state shows when no days.

- [ ] **Step 2: Run (device/CI)**

Run: `./gradlew connectedAndroidTest --tests "com.babytracker.ui.feeding.UnifiedFeedingHistoryScreenTest"`
Expected: PASS or defer to CI.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/babytracker/ui/feeding/UnifiedFeedingHistoryScreenTest.kt
git commit -m "test(breastfeeding): cover unified feeding history screen"
```

---

## Acceptance Criteria

- One screen shows bottle + breastfeeding entries interleaved, newest-first, grouped by day.
- Each day header shows total bottle volume (in the user's unit) and total feed count.
- Tapping a bottle row opens the plan-04 sheet pre-populated for edit; saving updates the entry.
- A bottle row can be deleted via a confirmation dialog (mirrors the stash list interaction); breastfeeding rows are read-only here.
- Grouping/total logic is a pure, unit-tested function; combine logic is unit-tested.
- Composables built under `impeccable` craft. `./gradlew test` passes; screen UI test passes on device/CI.

## Self-Review Notes

- Breastfeeding edit is intentionally out of scope on this screen (it belongs to the breastfeeding feature); only bottle entries are editable/deletable here, matching the project scope.
- Reuses `BottleFeedSheet` + `BottleFeedViewModel.loadForEdit` from plan 04 — no duplicate sheet.
- `ZoneId` injected for testable day-grouping (UTC in tests); grouping is pure.
- Day groups and within-day entries both sorted newest-first; verified in `GroupFeedEntriesTest`.
