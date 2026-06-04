# AKA-91: Home Prediction Card + Sleep-Screen Recommendation Section

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose `SleepPredictionState` in the UI — a compact debug-flagged card on HomeScreen and a full recommendation section on SleepTrackingScreen, covering all 7 state variants.

**Architecture:** `PredictSleepWindowUseCase` emits `Flow<SleepPredictionState>`. Both ViewModels inject the use case but gate the actual subscription behind `BuildConfig.DEBUG` — release builds receive `flowOf(SleepPredictionState.Unavailable("release"))` with zero DB observation cost. `HomeViewModel` adds the gated flow to the outer `combine` as a 4th arg (the inner combine already saturates 5 slots). `SleepViewModel` collects it imperatively in `init`, consistent with how `wakeTime` and `lastSleepSummary` are wired there. Both UI surfaces are additionally hidden via `BuildConfig.DEBUG` in the composable layer. `Unavailable` state → early-return (renders nothing).

**Tech Stack:** Jetpack Compose, Hilt, Kotlin Coroutines/Flow, `PredictSleepWindowUseCase`, `SleepPredictionState`, `SleepWindow`, `EvidenceProgress`, `Confidence`, `BuildConfig`

---

## File Map

| Action | Path |
|--------|------|
| Modify | `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt` |
| Modify | `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt` |
| Modify | `app/src/main/java/com/babytracker/ui/sleep/SleepViewModel.kt` |
| Modify | `app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt` |
| Modify | `app/src/test/java/com/babytracker/ui/home/HomeViewModelTest.kt` |
| Modify | `app/src/test/java/com/babytracker/ui/sleep/SleepViewModelTest.kt` |
| Create | `app/src/main/java/com/babytracker/ui/sleep/SleepPredictionCard.kt` |
| Create | `app/src/main/java/com/babytracker/ui/sleep/SleepRecommendationSection.kt` |
| Create | `app/src/androidTest/java/com/babytracker/ui/home/HomeSleepPredictionCardTest.kt` |
| Create | `app/src/androidTest/java/com/babytracker/ui/sleep/SleepRecommendationSectionTest.kt` |

---

### Task 1: Wire `PredictSleepWindowUseCase` into HomeViewModel

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt`

The outer `combine` currently wraps 3 flows. Add the prediction as a 4th, gated so release builds never observe tracking data.

- [ ] **Step 1: Add `sleepPrediction` field to `HomeUiState`**

Replace the `HomeUiState` data class (lines 34–48):

```kotlin
data class HomeUiState(
    val baby: Baby? = null,
    val recentFeedings: List<BreastfeedingSession> = emptyList(),
    val recentSleepRecords: List<SleepRecord> = emptyList(),
    val activeSession: BreastfeedingSession? = null,
    val activeSleepRecord: SleepRecord? = null,
    val nextRecommendedSide: BreastSide? = null,
    val lastNightSleepDuration: Duration? = null,
    val lastSessionStartTime: Instant? = null,
    val lastSleepEndTime: Instant? = null,
    val appMode: AppMode = AppMode.NONE,
    val pumpingActive: PumpingSession? = null,
    val inventorySummary: InventorySummary = InventorySummary.Empty,
    val nextFeedPrediction: FeedPrediction? = null,
    val sleepPrediction: SleepPredictionState = SleepPredictionState.Unavailable("loading"),
)
```

- [ ] **Step 2: Add imports and inject `PredictSleepWindowUseCase`**

Add to the import block:

```kotlin
import com.babytracker.BuildConfig
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import kotlinx.coroutines.flow.flowOf
```

Add to the `HomeViewModel` constructor after `predictNextFeed: PredictNextFeedUseCase,`:

```kotlin
predictSleepWindow: PredictSleepWindowUseCase,
```

- [ ] **Step 3: Extend the outer `combine` to 4 flows, gated by `BuildConfig.DEBUG`**

The current outer combine (lines 110–121) ends with:

```kotlin
        pumpingRepository.getActiveSession(),
        inventoryRepository.getSummary(),
    ) { partial, pumpingActive, inventorySummary ->
        partial.copy(
            pumpingActive = pumpingActive,
            inventorySummary = inventorySummary,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState()
    )
```

Replace with:

```kotlin
        pumpingRepository.getActiveSession(),
        inventoryRepository.getSummary(),
        if (BuildConfig.DEBUG) predictSleepWindow() else flowOf(SleepPredictionState.Unavailable("release")),
    ) { partial, pumpingActive, inventorySummary, sleepPrediction ->
        partial.copy(
            pumpingActive = pumpingActive,
            inventorySummary = inventorySummary,
            sleepPrediction = sleepPrediction,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState()
    )
```

- [ ] **Step 4: Build to confirm compilation**

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:compileReleaseKotlin
```

Expected: Both BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/home/HomeViewModel.kt
git commit -m "feat(ui): wire PredictSleepWindowUseCase into HomeViewModel (debug-gated)"
```

---

### Task 2: Wire `PredictSleepWindowUseCase` into SleepViewModel

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepViewModel.kt`

`SleepViewModel` is imperative (MutableStateFlow + `copy` updates). Collect the prediction flow in `init`, guarded so the subscription only runs in debug builds.

- [ ] **Step 1: Add `sleepPrediction` field to `SleepUiState`**

Replace `SleepUiState` (lines 55–70) — add the field after `activeTimePicker`:

```kotlin
data class SleepUiState(
    val schedule: SleepSchedule? = null,
    val isLoading: Boolean = false,
    val wakeTime: LocalTime? = null,
    val lastSleepSummary: LastSleepSummaryState = LastSleepSummaryState.Empty,
    val showEntrySheet: Boolean = false,
    val entryType: SleepType = SleepType.NAP,
    val entryStartTime: LocalTime = LocalTime.now(),
    val entryEndTime: LocalTime = LocalTime.now(),
    val entryError: String? = null,
    val entryDurationPreview: Duration? = null,
    val pendingDeleteRecord: SleepRecord? = null,
    val editingRecord: SleepRecord? = null,
    val isRegressionExpanded: Boolean = true,
    val activeTimePicker: SleepTimePickerTarget? = null,
    val sleepPrediction: SleepPredictionState = SleepPredictionState.Unavailable("loading"),
)
```

- [ ] **Step 2: Add imports and inject `PredictSleepWindowUseCase`**

Add to the import block:

```kotlin
import com.babytracker.BuildConfig
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
```

Add `private val predictSleepWindow: PredictSleepWindowUseCase,` to the `SleepViewModel` constructor after `private val syncToFirestore: SyncToFirestoreUseCase,`.

- [ ] **Step 3: Collect in `init`, gated by `BuildConfig.DEBUG`**

Add this block inside `init {}`, before the `loadSchedule()` call:

```kotlin
        if (BuildConfig.DEBUG) {
            viewModelScope.launch {
                predictSleepWindow().collect { prediction ->
                    _uiState.value = _uiState.value.copy(sleepPrediction = prediction)
                }
            }
        }
```

- [ ] **Step 4: Build to confirm compilation**

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:compileReleaseKotlin
```

Expected: Both BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/sleep/SleepViewModel.kt
git commit -m "feat(ui): wire PredictSleepWindowUseCase into SleepViewModel (debug-gated)"
```

---

### Task 3: Update existing ViewModel unit tests

**Files:**
- Modify: `app/src/test/java/com/babytracker/ui/home/HomeViewModelTest.kt`
- Modify: `app/src/test/java/com/babytracker/ui/sleep/SleepViewModelTest.kt`

Both `createViewModel()` factories pass constructor args by position. Adding `PredictSleepWindowUseCase` breaks compilation. Fix both, add one propagation test each. Unit tests run as debug builds (`BuildConfig.DEBUG == true`) so the real flow path is exercised.

- [ ] **Step 1: Update `HomeViewModelTest.kt`**

Add to the import block:

```kotlin
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
```

Add a field alongside the other mocks:

```kotlin
    private lateinit var predictSleepWindow: PredictSleepWindowUseCase
```

In `setUp()`, after `predictNextFeed = mockk()`:

```kotlin
        predictSleepWindow = mockk()
        every { predictSleepWindow() } returns flowOf(SleepPredictionState.Unavailable("test"))
```

In `createViewModel()`, add the new parameter after `predictNextFeed,`:

```kotlin
    private fun createViewModel() = HomeViewModel(
        getBabyProfile,
        getBreastfeedingHistory,
        getSleepHistory,
        syncToFirestore,
        settingsRepository,
        pumpingRepository,
        inventoryRepository,
        predictNextFeed,
        predictSleepWindow,
    )
```

Add one new test at the bottom of the class:

```kotlin
    @Test
    fun sleepPrediction_flowsThroughToUiState() = runTest {
        val state = SleepPredictionState.CurrentlySleeping
        every { predictSleepWindow() } returns flowOf(state)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(state, viewModel.uiState.value.sleepPrediction)
    }
```

- [ ] **Step 2: Update `SleepViewModelTest.kt`**

Add to the import block:

```kotlin
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
```

Add a field alongside the other mocks:

```kotlin
    private lateinit var predictSleepWindow: PredictSleepWindowUseCase
```

In `setUp()`, after `syncToFirestore = mockk()`:

```kotlin
        predictSleepWindow = mockk()
        every { predictSleepWindow() } returns flowOf(SleepPredictionState.Unavailable("test"))
```

In `createViewModel()`, add the new parameter after `syncToFirestore,`:

```kotlin
    private fun createViewModel() = SleepViewModel(
        saveSleepEntry,
        updateSleepEntry,
        deleteSleepEntry,
        getSleepHistory,
        generateSchedule,
        getBabyProfile,
        settingsRepository,
        startRecord,
        stopRecord,
        sleepNotificationScheduler,
        napReminderScheduler,
        syncToFirestore,
        predictSleepWindow,
    )
```

Add one new test at the bottom of the class:

```kotlin
    @Test
    fun `sleepPrediction flowsThroughToUiState`() = runTest {
        val state = SleepPredictionState.CurrentlySleeping
        every { predictSleepWindow() } returns flowOf(state)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(state, viewModel.uiState.value.sleepPrediction)
    }
```

- [ ] **Step 3: Run the unit test suite to confirm existing tests still pass**

```bash
./gradlew :app:testDebugUnitTest -PfastTests
```

Expected: BUILD SUCCESSFUL, all tests PASS (fast mode skips architecture tests).

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/babytracker/ui/home/HomeViewModelTest.kt
git add app/src/test/java/com/babytracker/ui/sleep/SleepViewModelTest.kt
git commit -m "test(ui): update ViewModel tests for PredictSleepWindowUseCase constructor param"
```

---

### Task 4: Create `SleepPredictionCard` composable

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/sleep/SleepPredictionCard.kt`

Compact card for the Home screen. Handles all 7 `SleepPredictionState` variants. `Unavailable` → early return. `Window` → collapsible safe-sleep prompt via `AnimatedVisibility`.

- [ ] **Step 1: Create the file**

```kotlin
package com.babytracker.ui.sleep

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepWindow
import com.babytracker.util.formatTime

@Composable
internal fun SleepPredictionCard(
    state: SleepPredictionState,
    modifier: Modifier = Modifier,
) {
    if (state is SleepPredictionState.Unavailable) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "SLEEP PREDICTION",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            when (state) {
                is SleepPredictionState.Window -> WindowCardContent(window = state.window)
                is SleepPredictionState.NeedMoreData -> NeedMoreDataCardContent(progress = state.progress)
                SleepPredictionState.Overdue -> OverdueCardContent()
                SleepPredictionState.CueLed -> CueLedCardContent()
                SleepPredictionState.CurrentlySleeping -> CurrentlySleepingCardContent()
                SleepPredictionState.AfterActiveFeed -> AfterActiveFeedCardContent()
                is SleepPredictionState.Unavailable -> Unit
            }
        }
    }
}

@Composable
private fun WindowCardContent(window: SleepWindow) {
    var safetyExpanded by remember { mutableStateOf(false) }
    val timeRange = "${window.windowStart.formatTime()}–${window.windowEnd.formatTime()}"
    val primary = "Next sleep ~${window.bestEstimate.formatTime()} · $timeRange"
    Text(
        text = primary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.semantics { contentDescription = primary },
    )
    if (window.confidence == Confidence.LOW) {
        Text(
            text = "low confidence",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(role = Role.Button) { safetyExpanded = !safetyExpanded }
            .semantics {
                contentDescription =
                    if (safetyExpanded) "Collapse safe-sleep tip" else "Expand safe-sleep tip"
            },
    ) {
        Text(
            text = "Safe sleep",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = if (safetyExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
        )
    }
    AnimatedVisibility(visible = safetyExpanded) {
        Text(
            text = window.safetyPrompt,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun NeedMoreDataCardContent(progress: EvidenceProgress) {
    LinearProgressIndicator(
        progress = { progress.completedIntervals.toFloat() / progress.requiredIntervals.coerceAtLeast(1) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = progress.hint,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun OverdueCardContent() {
    Text(
        text = "Watch for cues — next opportunity soon",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun CueLedCardContent() {
    Text(
        text = "Watching baby's cues — no fixed window right now",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun CurrentlySleepingCardContent() {
    Text(
        text = "Baby is sleeping",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun AfterActiveFeedCardContent() {
    Text(
        text = "Feeding now — next sleep window appears after feed ends",
        style = MaterialTheme.typography.bodyMedium,
    )
}
```

- [ ] **Step 2: Build to confirm compilation**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/sleep/SleepPredictionCard.kt
git commit -m "feat(ui): add SleepPredictionCard composable for home screen"
```

---

### Task 5: Add `SleepPredictionCard` to HomeScreen

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`

The 2×2 summary grid `Column` closes at line 421. The TIP card `AnimatedVisibility` begins at line 423. Insert the prediction card between them.

- [ ] **Step 1: Add imports**

Add to the existing import block:

```kotlin
import com.babytracker.BuildConfig
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.ui.sleep.SleepPredictionCard
```

- [ ] **Step 2: Insert the debug-flagged card**

Find the surrounding context (current lines 420–425):

```kotlin
                }
            }

            // Tip card — suggests which side to try next (based on the less-used side last session)
            AnimatedVisibility(
```

Replace with:

```kotlin
                }
            }

            if (BuildConfig.DEBUG) {
                SleepPredictionCard(state = uiState.sleepPrediction)
            }

            // Tip card — suggests which side to try next (based on the less-used side last session)
            AnimatedVisibility(
```

- [ ] **Step 3: Build debug and release**

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:compileReleaseKotlin
```

Expected: Both BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/home/HomeScreen.kt
git commit -m "feat(ui): add debug-flagged SleepPredictionCard to HomeScreen"
```

---

### Task 6: Create `SleepRecommendationSection` composable

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/sleep/SleepRecommendationSection.kt`

Full detail view for the Sleep screen. Accepts `state: SleepPredictionState` and `schedule: SleepSchedule?` (for plan-vs-predictor framing). `Unavailable` → early return. `Window` → best estimate, time range, confidence badge, reason list, plan-vs-predictor row (when schedule has an upcoming nap), feed prompt (when non-null), safety prompt (always).

- [ ] **Step 1: Create the file**

```kotlin
package com.babytracker.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepSchedule
import com.babytracker.domain.model.SleepWindow
import com.babytracker.util.formatTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a")

@Composable
internal fun SleepRecommendationSection(
    state: SleepPredictionState,
    schedule: SleepSchedule?,
    now: LocalTime = LocalTime.now(),
    modifier: Modifier = Modifier,
) {
    if (state is SleepPredictionState.Unavailable) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "SLEEP RECOMMENDATION",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.height(12.dp))
            when (state) {
                is SleepPredictionState.Window ->
                    WindowSectionContent(window = state.window, schedule = schedule, now = now)
                is SleepPredictionState.NeedMoreData ->
                    NeedMoreDataSectionContent(progress = state.progress)
                SleepPredictionState.Overdue -> OverdueSectionContent()
                SleepPredictionState.CueLed -> CueLedSectionContent()
                SleepPredictionState.CurrentlySleeping -> CurrentlySleepingSectionContent()
                SleepPredictionState.AfterActiveFeed -> AfterActiveFeedSectionContent()
                is SleepPredictionState.Unavailable -> Unit
            }
        }
    }
}

@Composable
private fun WindowSectionContent(window: SleepWindow, schedule: SleepSchedule?, now: LocalTime) {
    Text(
        text = "~${window.bestEstimate.formatTime()}",
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = "${window.windowStart.formatTime()}–${window.windowEnd.formatTime()}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
    )

    Spacer(Modifier.height(8.dp))
    ConfidenceBadge(confidence = window.confidence)

    if (window.reasons.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        window.reasons.forEach { reason ->
            Row(modifier = Modifier.padding(vertical = 1.dp)) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(text = reason, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    val nextNap = schedule?.napTimes?.firstOrNull { it.startTime >= now }
    if (nextNap != null) {
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "DAY PLAN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    text = nextNap.startTime.format(TIME_FORMATTER),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "NEXT WINDOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    text = "~${window.bestEstimate.formatTime()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    window.feedPrompt?.let { prompt ->
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text(
            text = prompt,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
    Text(
        text = window.safetyPrompt,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
    )
}

@Composable
private fun ConfidenceBadge(confidence: Confidence) {
    val (bg, fg) = when (confidence) {
        Confidence.LOW ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        Confidence.MEDIUM ->
            MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
        Confidence.HIGH ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(shape = MaterialTheme.shapes.extraSmall, color = bg) {
        Text(
            text = confidence.name,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun NeedMoreDataSectionContent(progress: EvidenceProgress) {
    LinearProgressIndicator(
        progress = { progress.completedIntervals.toFloat() / progress.requiredIntervals.coerceAtLeast(1) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Text(text = progress.hint, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun OverdueSectionContent() {
    Text(
        text = "Watch for sleep cues — the window may open soon.",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun CueLedSectionContent() {
    Text(
        text = "Watching baby's cues — no fixed window right now.",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun CurrentlySleepingSectionContent() {
    Text(
        text = "Baby is currently sleeping. Next window prediction will appear after wake.",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun AfterActiveFeedSectionContent() {
    Text(
        text = "A feed is in progress. Sleep window prediction appears after the feed ends.",
        style = MaterialTheme.typography.bodyMedium,
    )
}
```

- [ ] **Step 2: Build to confirm compilation**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/sleep/SleepRecommendationSection.kt
git commit -m "feat(ui): add SleepRecommendationSection composable for sleep screen"
```

---

### Task 7: Add `SleepRecommendationSection` to SleepTrackingScreen

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt`

Insert after the `SleepSummaryRow` item (lines 220–226) and before the `TODAY` label item (line 227).

- [ ] **Step 1: Add import**

Add to the existing import block:

```kotlin
import com.babytracker.BuildConfig
```

- [ ] **Step 2: Insert the section in the LazyColumn**

Find:

```kotlin
            item {
                SleepSummaryRow(
                    totalSleep = totalSleepToday,
                    napCount = napCount,
                    nightSleep = nightSleepDuration
                )
            }
            item {
                Text(
                    text = "TODAY",
```

Replace with:

```kotlin
            item {
                SleepSummaryRow(
                    totalSleep = totalSleepToday,
                    napCount = napCount,
                    nightSleep = nightSleepDuration
                )
            }
            if (BuildConfig.DEBUG) {
                item {
                    SleepRecommendationSection(
                        state = uiState.sleepPrediction,
                        schedule = uiState.schedule,
                    )
                }
            }
            item {
                Text(
                    text = "TODAY",
```

- [ ] **Step 3: Build debug and release**

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:compileReleaseKotlin
```

Expected: Both BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt
git commit -m "feat(ui): add debug-flagged SleepRecommendationSection to SleepTrackingScreen"
```

---

### Task 8: Compose tests for `SleepPredictionCard`

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/ui/home/HomeSleepPredictionCardTest.kt`

Mirrors the `HomeScreenPredictionTest` pattern: `@RunWith(AndroidJUnit4::class)` + `createAndroidComposeRule<ComponentActivity>()`. `internal` visibility is accessible across packages within the same module.

- [ ] **Step 1: Create the test file**

```kotlin
package com.babytracker.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepWindow
import com.babytracker.ui.sleep.SleepPredictionCard
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class HomeSleepPredictionCardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun windowState(
        confidence: Confidence = Confidence.MEDIUM,
        feedPrompt: String? = null,
    ) = SleepPredictionState.Window(
        SleepWindow(
            windowStart = Instant.parse("2024-01-01T14:20:00Z"),
            windowEnd = Instant.parse("2024-01-01T14:50:00Z"),
            bestEstimate = Instant.parse("2024-01-01T14:35:00Z"),
            confidence = confidence,
            reasons = listOf("awake 2h05"),
            feedPrompt = feedPrompt,
            safetyPrompt = "Always place baby on their back to sleep.",
        )
    )

    private fun needMoreDataState() = SleepPredictionState.NeedMoreData(
        EvidenceProgress(
            completedIntervals = 3,
            requiredIntervals = 10,
            localDays = 1,
            requiredLocalDays = 3,
            hint = "Log a few more naps with both sleep and wake times.",
        )
    )

    @Test
    fun windowState_showsNextSleepText() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = windowState()) }
        }
        composeRule.onNodeWithText("Next sleep", substring = true).assertIsDisplayed()
    }

    @Test
    fun windowState_lowConfidence_showsLowConfidenceText() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = windowState(confidence = Confidence.LOW)) }
        }
        composeRule.onNodeWithText("low confidence").assertIsDisplayed()
    }

    @Test
    fun windowState_mediumConfidence_doesNotShowLowConfidenceText() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = windowState(confidence = Confidence.MEDIUM)) }
        }
        composeRule.onNodeWithText("low confidence").assertDoesNotExist()
    }

    @Test
    fun windowState_safeSleepCollapsed_byDefault() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = windowState()) }
        }
        composeRule.onNodeWithText("Always place baby on their back to sleep.").assertDoesNotExist()
    }

    @Test
    fun windowState_safeSleepExpandsOnToggle() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = windowState()) }
        }
        composeRule.onNodeWithText("Safe sleep").performClick()
        composeRule.onNodeWithText("Always place baby on their back to sleep.").assertIsDisplayed()
    }

    @Test
    fun needMoreDataState_showsHint() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = needMoreDataState()) }
        }
        composeRule.onNodeWithText("Log a few more naps with both sleep and wake times.", substring = true).assertIsDisplayed()
    }

    @Test
    fun overdueState_showsCueText() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = SleepPredictionState.Overdue) }
        }
        composeRule.onNodeWithText("Watch for cues", substring = true).assertIsDisplayed()
    }

    @Test
    fun cueLedState_showsCueLedText() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = SleepPredictionState.CueLed) }
        }
        composeRule.onNodeWithText("Watching baby's cues", substring = true).assertIsDisplayed()
    }

    @Test
    fun currentlySleepingState_showsSleepingText() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = SleepPredictionState.CurrentlySleeping) }
        }
        composeRule.onNodeWithText("Baby is sleeping").assertIsDisplayed()
    }

    @Test
    fun afterActiveFeedState_showsFeedingText() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = SleepPredictionState.AfterActiveFeed) }
        }
        composeRule.onNodeWithText("Feeding now", substring = true).assertIsDisplayed()
    }

    @Test
    fun unavailableState_rendersNothing() {
        composeRule.setContent {
            BabyTrackerTheme { SleepPredictionCard(state = SleepPredictionState.Unavailable("no data")) }
        }
        composeRule.onNodeWithText("SLEEP PREDICTION").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run the tests (requires emulator)**

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.babytracker.ui.home.HomeSleepPredictionCardTest
```

Expected: 11 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/babytracker/ui/home/HomeSleepPredictionCardTest.kt
git commit -m "test(ui): add Compose tests for SleepPredictionCard all 7 states"
```

---

### Task 9: Compose tests for `SleepRecommendationSection`

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/ui/sleep/SleepRecommendationSectionTest.kt`

- [ ] **Step 1: Create the test file**

```kotlin
package com.babytracker.ui.sleep

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.ScheduleEntry
import com.babytracker.domain.model.ScheduleMode
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepSchedule
import com.babytracker.domain.model.SleepWindow
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class SleepRecommendationSectionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun windowState(
        confidence: Confidence = Confidence.MEDIUM,
        feedPrompt: String? = null,
        safetyPrompt: String = "Always place baby on their back to sleep.",
    ) = SleepPredictionState.Window(
        SleepWindow(
            windowStart = Instant.parse("2024-01-01T14:20:00Z"),
            windowEnd = Instant.parse("2024-01-01T14:50:00Z"),
            bestEstimate = Instant.parse("2024-01-01T14:35:00Z"),
            confidence = confidence,
            reasons = listOf("awake 2h05", "based on recent wake patterns"),
            feedPrompt = feedPrompt,
            safetyPrompt = safetyPrompt,
        )
    )

    private fun needMoreDataState() = SleepPredictionState.NeedMoreData(
        EvidenceProgress(
            completedIntervals = 3,
            requiredIntervals = 10,
            localDays = 1,
            requiredLocalDays = 3,
            hint = "Log a few more naps with both sleep and wake times.",
        )
    )

    private fun scheduleWithNapAt(napTime: LocalTime) = SleepSchedule(
        ageInWeeks = 20,
        mode = ScheduleMode.CLOCK_ALIGNED,
        wakeWindows = emptyList(),
        napTimes = listOf(ScheduleEntry(startTime = napTime, duration = Duration.ofMinutes(90), label = "Nap")),
        bedtime = LocalTime.of(19, 0),
        bedtimeWindow = LocalTime.of(18, 30)..LocalTime.of(19, 30),
        totalSleepRecommendation = Duration.ofHours(14)..Duration.ofHours(16),
        totalSleepLogged = null,
        regressionWarning = null,
        napTransitionSuggestion = null,
        lastFeedTime = null,
        isPersonalized = false,
    )

    @Test
    fun windowState_showsConfidenceBadge_medium() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = windowState(confidence = Confidence.MEDIUM), schedule = null)
            }
        }
        composeRule.onNodeWithText("MEDIUM").assertIsDisplayed()
    }

    @Test
    fun windowState_showsConfidenceBadge_low() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = windowState(confidence = Confidence.LOW), schedule = null)
            }
        }
        composeRule.onNodeWithText("LOW").assertIsDisplayed()
    }

    @Test
    fun windowState_showsReasons() {
        composeRule.setContent {
            BabyTrackerTheme { SleepRecommendationSection(state = windowState(), schedule = null) }
        }
        composeRule.onNodeWithText("awake 2h05").assertIsDisplayed()
        composeRule.onNodeWithText("based on recent wake patterns").assertIsDisplayed()
    }

    @Test
    fun windowState_safetyPromptAlwaysShown() {
        composeRule.setContent {
            BabyTrackerTheme { SleepRecommendationSection(state = windowState(), schedule = null) }
        }
        composeRule.onNodeWithText("Always place baby on their back to sleep.").assertIsDisplayed()
    }

    @Test
    fun windowState_feedPromptShown_whenPresent() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(
                    state = windowState(feedPrompt = "a breastfeed may be due near this window"),
                    schedule = null,
                )
            }
        }
        composeRule.onNodeWithText("a breastfeed may be due near this window").assertIsDisplayed()
    }

    @Test
    fun windowState_feedPromptNotShown_whenNull() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = windowState(feedPrompt = null), schedule = null)
            }
        }
        composeRule.onNodeWithText("a breastfeed may be due near this window").assertDoesNotExist()
    }

    @Test
    fun needMoreDataState_showsHint() {
        composeRule.setContent {
            BabyTrackerTheme { SleepRecommendationSection(state = needMoreDataState(), schedule = null) }
        }
        composeRule.onNodeWithText("Log a few more naps with both sleep and wake times.", substring = true).assertIsDisplayed()
    }

    @Test
    fun overdueState_showsMessage() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = SleepPredictionState.Overdue, schedule = null)
            }
        }
        composeRule.onNodeWithText("Watch for sleep cues", substring = true).assertIsDisplayed()
    }

    @Test
    fun cueLedState_showsMessage() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = SleepPredictionState.CueLed, schedule = null)
            }
        }
        composeRule.onNodeWithText("Watching baby's cues", substring = true).assertIsDisplayed()
    }

    @Test
    fun currentlySleepingState_showsMessage() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = SleepPredictionState.CurrentlySleeping, schedule = null)
            }
        }
        composeRule.onNodeWithText("Baby is currently sleeping", substring = true).assertIsDisplayed()
    }

    @Test
    fun afterActiveFeedState_showsMessage() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = SleepPredictionState.AfterActiveFeed, schedule = null)
            }
        }
        composeRule.onNodeWithText("A feed is in progress", substring = true).assertIsDisplayed()
    }

    @Test
    fun unavailableState_rendersNothing() {
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(
                    state = SleepPredictionState.Unavailable("no data"),
                    schedule = null,
                )
            }
        }
        composeRule.onNodeWithText("SLEEP RECOMMENDATION").assertDoesNotExist()
    }

    @Test
    fun windowState_planVsPredictorRow_appearsWhenFutureNapExists() {
        val now = LocalTime.of(14, 0)
        val schedule = scheduleWithNapAt(LocalTime.of(15, 0))
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = windowState(), schedule = schedule, now = now)
            }
        }
        composeRule.onNodeWithText("DAY PLAN").assertIsDisplayed()
    }

    @Test
    fun windowState_planVsPredictorRow_hiddenWhenAllNapsPast() {
        val now = LocalTime.of(16, 0)
        val schedule = scheduleWithNapAt(LocalTime.of(15, 0))
        composeRule.setContent {
            BabyTrackerTheme {
                SleepRecommendationSection(state = windowState(), schedule = schedule, now = now)
            }
        }
        composeRule.onNodeWithText("DAY PLAN").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run the tests (requires emulator)**

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.babytracker.ui.sleep.SleepRecommendationSectionTest
```

Expected: 14 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/babytracker/ui/sleep/SleepRecommendationSectionTest.kt
git commit -m "test(ui): add Compose tests for SleepRecommendationSection all 7 states"
```

---

### Task 10: Code quality pass

- [ ] **Step 1: Run ktlint format**

```bash
./gradlew ktlintFormat
```

Expected: Reformatting applied automatically.

- [ ] **Step 2: Run detekt**

```bash
./gradlew detekt
```

Expected: BUILD SUCCESSFUL. If violations found, fix the code — never suppress.

- [ ] **Step 3: Run full unit test suite**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: All tests PASS.

- [ ] **Step 4: Commit any fixes**

Only if step 1 or 2 produced changes:

```bash
git add -A
git commit -m "fix(detekt): resolve code quality violations after AKA-91 implementation"
```

---

## Self-Review

### Spec coverage

| Requirement | Task |
|-------------|------|
| Home compact card — all 7 states | Tasks 4, 5 |
| `Window`: time + range + confidence hint | Task 4 `WindowCardContent` |
| `NeedMoreData`: progress + hint | Task 4 `NeedMoreDataCardContent` |
| `Overdue`: cue message | Task 4 `OverdueCardContent` |
| `CueLed`: cue-led message | Task 4 `CueLedCardContent` |
| `CurrentlySleeping` | Task 4 `CurrentlySleepingCardContent` |
| `AfterActiveFeed` | Task 4 `AfterActiveFeedCardContent` |
| Safe-sleep compact/collapsible on home card | Task 4 `AnimatedVisibility` safe-sleep section |
| Sleep screen full detail section — all 7 states | Tasks 6, 7 |
| Window: best estimate + time range | Task 6 `WindowSectionContent` |
| Confidence badge LOW / MEDIUM | Task 6 `ConfidenceBadge` |
| Reason list | Task 6 reasons `forEach` |
| Feed prompt conditional on `feedPrompt != null` | Task 6 `window.feedPrompt?.let` |
| Safety prompt always present for Window | Task 6 unconditional safety `Text` |
| Plan-vs-next-window framing | Task 6 `nextNap` comparison row |
| `sleepPrediction` added to `HomeUiState` | Task 1 |
| `sleepPrediction` added to `SleepUiState` | Task 2 |
| `PredictSleepWindowUseCase` wired, release-gated in HomeViewModel | Task 1 `BuildConfig.DEBUG` |
| `PredictSleepWindowUseCase` wired, release-gated in SleepViewModel | Task 2 `BuildConfig.DEBUG` |
| Existing ViewModel tests updated (compile + propagation) | Task 3 |
| Debug flag hides home card in release | Task 5 `BuildConfig.DEBUG` guard |
| Debug flag hides sleep section in release | Task 7 `BuildConfig.DEBUG` guard |
| Compose tests for card, all states | Task 8 — 11 tests |
| Compose tests for section, all states + schedule branch | Task 9 — 14 tests (incl. future nap shows / past nap hidden) |

All 7 `SleepPredictionState` variants covered in both composables: Window ✓ NeedMoreData ✓ CueLed ✓ CurrentlySleeping ✓ AfterActiveFeed ✓ Overdue ✓ Unavailable (early return) ✓

### Release-build isolation

In release: both ViewModels receive `flowOf(SleepPredictionState.Unavailable("release"))` — zero DB queries, zero `PredictSleepWindowUseCase.invoke()` calls. The use case object is still created by Hilt (it is injected) but never called. The UI surfaces are additionally blocked by `BuildConfig.DEBUG` guards in the composable layer, so the `Unavailable` early-return is a belt-and-suspenders guarantee.

### Placeholder scan

No TBD, TODO, "implement later", "similar to Task N", or "add appropriate" phrases present.

### Type consistency

- `SleepPredictionState.Unavailable("loading")` matches `data class Unavailable(val reason: String)` ✓
- `SleepWindow(windowStart, windowEnd, bestEstimate, confidence, reasons, feedPrompt, safetyPrompt)` — all 7 fields ✓
- `EvidenceProgress(completedIntervals, requiredIntervals, localDays, requiredLocalDays, hint)` — all 5 fields ✓
- `formatTime()` called as `Instant.formatTime()` — consistent with `PredictionCopy.kt` pattern ✓
- `SleepRecommendationSection(state, schedule)` parameter names match every call site ✓
- `SleepPredictionCard(state)` parameter name matches every call site ✓
- `HorizontalDivider()` — current M3 API ✓
- `schedule?.napTimes?.firstOrNull { it.startTime >= now }` — `ScheduleEntry.startTime: LocalTime`, `LocalTime` is `Comparable` ✓
- `HomeViewModel.createViewModel()` in test now passes 9 args — matches new constructor ✓
- `SleepViewModel.createViewModel()` in test now passes 13 args — matches new constructor ✓
