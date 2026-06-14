# Trends ViewModel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

LINEAR_ISSUE: AKA-146

**Goal:** Add `TrendsViewModel` + `TrendsUiState` that expose the three trend series for a user-selectable date range and recompute when the range changes.

**Architecture:** A single `StateFlow<TrendsUiState>`. A `MutableStateFlow<TrendRange>` drives `flatMapLatest`; for each range the flow emits a loading state, then a loaded state populated by calling the three `suspend` use cases. Matches the codebase's `stateIn` + `WhileSubscribed` ViewModel convention (see `GrowthViewModel`, `MilestonesViewModel`).

**Tech Stack:** Hilt (`@HiltViewModel`), Coroutines/Flow, JUnit 5 + MockK + Turbine + `runTest`.

**Dependencies:** Plan 01 (`feat/trends-domain`) must be merged or present — needs `TrendRange` and the three use cases.

**Suggested implementation branch:** `feat/trends-viewmodel`

**Workflow note:** implement-first, then tests (CLAUDE.md / user preference).

**Spec:** `docs/superpowers/specs/2026-06-14-charts-and-trends-design.md`

---

## File Structure

- Create `app/src/main/java/com/babytracker/ui/trends/TrendsViewModel.kt` — `TrendsUiState` + `TrendsViewModel`.
- Test `app/src/test/java/com/babytracker/ui/trends/TrendsViewModelTest.kt`.

---

### Task 1: TrendsUiState + TrendsViewModel

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/trends/TrendsViewModel.kt`

- [ ] **Step 1: Write the ViewModel**

```kotlin
package com.babytracker.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.trends.DailyFeedingCount
import com.babytracker.domain.trends.DailyFeedingInterval
import com.babytracker.domain.trends.DailySleepDuration
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.usecase.trends.GetFeedingFrequencyTrendUseCase
import com.babytracker.domain.usecase.trends.GetFeedingIntervalTrendUseCase
import com.babytracker.domain.usecase.trends.GetSleepDurationTrendUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TrendsUiState(
    val range: TrendRange = TrendRange.SEVEN_DAYS,
    val isLoading: Boolean = true,
    val feedingFrequency: List<DailyFeedingCount> = emptyList(),
    val sleepDuration: List<DailySleepDuration> = emptyList(),
    val feedingInterval: List<DailyFeedingInterval> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val getFeedingFrequencyTrend: GetFeedingFrequencyTrendUseCase,
    private val getSleepDurationTrend: GetSleepDurationTrendUseCase,
    private val getFeedingIntervalTrend: GetFeedingIntervalTrendUseCase,
) : ViewModel() {

    private val selectedRange = MutableStateFlow(TrendRange.SEVEN_DAYS)

    val uiState: StateFlow<TrendsUiState> =
        selectedRange.flatMapLatest { range ->
            flow {
                emit(TrendsUiState(range = range, isLoading = true))
                emit(
                    TrendsUiState(
                        range = range,
                        isLoading = false,
                        feedingFrequency = getFeedingFrequencyTrend(range),
                        sleepDuration = getSleepDurationTrend(range),
                        feedingInterval = getFeedingIntervalTrend(range),
                    ),
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = TrendsUiState(),
        )

    fun onRangeSelected(range: TrendRange) {
        selectedRange.value = range
    }

    companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

---

### Task 2: ViewModel tests

**Files:**
- Test: `app/src/test/java/com/babytracker/ui/trends/TrendsViewModelTest.kt`

- [ ] **Step 1: Write tests**

```kotlin
package com.babytracker.ui.trends

import app.cash.turbine.test
import com.babytracker.domain.trends.DailyFeedingCount
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.usecase.trends.GetFeedingFrequencyTrendUseCase
import com.babytracker.domain.usecase.trends.GetFeedingIntervalTrendUseCase
import com.babytracker.domain.usecase.trends.GetSleepDurationTrendUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TrendsViewModelTest {
    private val frequency: GetFeedingFrequencyTrendUseCase = mockk()
    private val sleep: GetSleepDurationTrendUseCase = mockk()
    private val interval: GetFeedingIntervalTrendUseCase = mockk()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        coEvery { sleep(any()) } returns emptyList()
        coEvery { interval(any()) } returns emptyList()
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TrendsViewModel(frequency, sleep, interval)

    @Test
    fun `loads default 7-day range then settles loaded`() = runTest {
        coEvery { frequency(TrendRange.SEVEN_DAYS) } returns
            listOf(DailyFeedingCount(LocalDate.of(2026, 6, 14), 5))

        viewModel().uiState.test {
            // initialValue
            assertEquals(TrendRange.SEVEN_DAYS, awaitItem().range)
            // loading emission
            awaitItem() // isLoading = true
            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertEquals(1, loaded.feedingFrequency.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onRangeSelected recomputes for new range`() = runTest {
        coEvery { frequency(any()) } returns emptyList()

        val vm = viewModel()
        vm.uiState.test {
            awaitItem() // initial
            awaitItem() // loading 7d
            awaitItem() // loaded 7d
            vm.onRangeSelected(TrendRange.THIRTY_DAYS)
            awaitItem() // loading 30d
            val loaded = awaitItem()
            assertEquals(TrendRange.THIRTY_DAYS, loaded.range)
            assertFalse(loaded.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

> Note: exact emission counts may vary by one depending on `stateIn` conflation; if Turbine
> reports an extra/!missing emission, adjust the `awaitItem()` sequence to match observed output —
> the assertions on `range`/`isLoading` are the invariants that matter.

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "com.babytracker.ui.trends.TrendsViewModelTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/trends/TrendsViewModel.kt app/src/test/java/com/babytracker/ui/trends/TrendsViewModelTest.kt
git commit -m "feat(trends): add TrendsViewModel with range selection"
```

---

## Acceptance Criteria

- `TrendsViewModel` exposes a single `StateFlow<TrendsUiState>` and an `onRangeSelected(TrendRange)` handler (matches CLAUDE.md ViewModel convention).
- Default range is 7 days; switching range re-invokes all three use cases.
- `isLoading` is `true` during recomputation and `false` once data is loaded.
- ViewModel tests pass: `./gradlew test --tests "com.babytracker.ui.trends.TrendsViewModelTest"`.
- `./gradlew build` succeeds; ktlint + detekt clean.

## Self-Review Notes

- Spec coverage: range selector state + per-range recompute (spec "Date range selector" + ViewModel section). Empty datasets flow straight through to drive UI empty states in Plan 03.
- Type consistency: use-case names and `TrendRange` match Plan 01 exactly; `TrendsUiState` field names (`feedingFrequency`, `sleepDuration`, `feedingInterval`) are reused verbatim by Plan 03.
- No placeholders.
