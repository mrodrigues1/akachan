# Sleep Cue Row on Sleep Screen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract `CueQuickTapRow` from `HomeScreen.kt` into a shared component and wire it into `SleepTrackingScreen`, so parents on the sleep screen can log cues without navigating away.

**Architecture:** Three layers — a new `ui/component/CueQuickTapRow.kt` becomes the single source of truth; `SleepViewModel` gains `LogBabyEventUseCase` injection and `onCueTapped`; `SleepTrackingScreen` adds one `LazyColumn` item after `SleepRecommendationSection`. `HomeScreen` drops its private copy and imports from the component.

**Tech Stack:** Jetpack Compose (FilterChip, animateFloatAsState, spring), Hilt (`@HiltViewModel`), `LogBabyEventUseCase`, `SnapshotStateSet`, coroutines.

---

## File Map

| File | Action |
|------|--------|
| `app/src/main/java/com/babytracker/ui/component/CueQuickTapRow.kt` | **Create** — public composable + file-private emoji/label extensions |
| `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt` | **Modify** — remove `internal CueQuickTapRow`, private emoji/label; remove 5 now-unused imports (`mutableStateSetOf`, `rememberCoroutineScope`, `BabyEventType`, `Job`, `launch`); add component import |
| `app/src/androidTest/java/com/babytracker/ui/home/HomeScreenTest.kt` | **Modify** — add explicit `import com.babytracker.ui.component.CueQuickTapRow` |
| `app/src/androidTest/java/com/babytracker/ui/sleep/SleepTrackingScreenTest.kt` | **Modify** — add component import; add 6-chip presence test |
| `app/src/main/java/com/babytracker/ui/sleep/SleepViewModel.kt` | **Modify** — inject `LogBabyEventUseCase`; add `onCueTapped` |
| `app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt` | **Modify** — add component import; add `CueQuickTapRow` item |

---

## Task 1: Create `CueQuickTapRow` component

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/component/CueQuickTapRow.kt`

The composable body is lifted verbatim from `HomeScreen.kt` (lines 886–930). Visibility changes: `internal` → `public`. Extension properties stay `private` to this file.

- [ ] **Step 1: Create the component file**

```kotlin
package com.babytracker.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.BabyEventType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BabyEventType.emoji: String
    get() = when (this) {
        BabyEventType.SLEEPY_CUE -> "😪"
        BabyEventType.HUNGER_CUE -> "😋"
        BabyEventType.FUSSY -> "😣"
        BabyEventType.SICK -> "🤒"
        BabyEventType.TEETHING -> "🦷"
        BabyEventType.TRAVEL -> "✈️"
    }

private val BabyEventType.label: String
    get() = when (this) {
        BabyEventType.SLEEPY_CUE -> "Sleepy"
        BabyEventType.HUNGER_CUE -> "Hungry"
        BabyEventType.FUSSY -> "Fussy"
        BabyEventType.SICK -> "Sick"
        BabyEventType.TEETHING -> "Teething"
        BabyEventType.TRAVEL -> "Travel"
    }

@Composable
fun CueQuickTapRow(
    onCueTapped: (BabyEventType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cues = remember { BabyEventType.entries }
    val tappedCues = remember { mutableStateSetOf<BabyEventType>() }
    val removalJobs = remember { HashMap<BabyEventType, Job>() }
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cues.forEach { type ->
            val selected = type in tappedCues
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.08f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "cue-chip-scale-${type.name}",
            )
            FilterChip(
                selected = selected,
                onClick = {
                    onCueTapped(type)
                    tappedCues.add(type)
                    removalJobs[type]?.cancel()
                    removalJobs[type] = scope.launch {
                        delay(1_200L)
                        tappedCues.remove(type)
                        removalJobs.remove(type)
                    }
                },
                label = {
                    Text(
                        text = "${type.emoji} ${type.label}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                modifier = Modifier.scale(scale),
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL (no errors in `CueQuickTapRow.kt`)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/component/CueQuickTapRow.kt
git commit -m "feat(ui): extract CueQuickTapRow to shared component"
```

---

## Task 2: Fix `HomeScreenTest.kt` import

**Files:**
- Modify: `app/src/androidTest/java/com/babytracker/ui/home/HomeScreenTest.kt`

`HomeScreenTest.kt` is in `package com.babytracker.ui.home` and currently resolves `CueQuickTapRow` via same-package visibility (`internal` in `HomeScreen.kt`). Task 3 will delete that `internal` declaration — this task adds the explicit import first, while `internal` still exists, so the transition is seamless. Explicit imports take precedence over same-package resolution in Kotlin; both coexisting briefly is safe.

- [ ] **Step 1: Add explicit import to `HomeScreenTest.kt`**

After line 26 (`import java.time.Instant`), add:
```kotlin
import com.babytracker.ui.component.CueQuickTapRow
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/babytracker/ui/home/HomeScreenTest.kt
git commit -m "test(ui): add explicit CueQuickTapRow import ahead of HomeScreen refactor"
```

---

## Task 3: Remove private copies from `HomeScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`

`HomeScreen.kt` currently declares `CueQuickTapRow` as `internal` (line 886) and owns two file-private extension properties for `BabyEventType` (lines 101–119). All three blocks move to the component. The call site at line 456 stays untouched. `HomeScreenTest.kt` already has the explicit import (Task 2), so removing `internal` won't break the build.

- [ ] **Step 1: Add component import to `HomeScreen.kt`**

In the imports block, add between line 88 (`import com.babytracker.ui.breastfeeding.PredictionCopy`) and line 89 (`import com.babytracker.ui.sleep.SleepPredictionCard`) — alphabetically `component` sorts between `breastfeeding` and `sleep`:
```kotlin
import com.babytracker.ui.component.CueQuickTapRow
```

- [ ] **Step 2: Remove the private emoji extension property**

Delete these 9 lines (currently at ~101–109):
```kotlin
private val BabyEventType.emoji: String
    get() = when (this) {
        BabyEventType.SLEEPY_CUE -> "😪"
        BabyEventType.HUNGER_CUE -> "😋"
        BabyEventType.FUSSY -> "😣"
        BabyEventType.SICK -> "🤒"
        BabyEventType.TEETHING -> "🦷"
        BabyEventType.TRAVEL -> "✈️"
    }
```

- [ ] **Step 3: Remove the private label extension property**

Delete these 9 lines (currently at ~111–119):
```kotlin
private val BabyEventType.label: String
    get() = when (this) {
        BabyEventType.SLEEPY_CUE -> "Sleepy"
        BabyEventType.HUNGER_CUE -> "Hungry"
        BabyEventType.FUSSY -> "Fussy"
        BabyEventType.SICK -> "Sick"
        BabyEventType.TEETHING -> "Teething"
        BabyEventType.TRAVEL -> "Travel"
    }
```

- [ ] **Step 4: Remove the `internal CueQuickTapRow` composable**

Delete the entire block (currently at ~885–930):
```kotlin
@Composable
internal fun CueQuickTapRow(
    onCueTapped: (BabyEventType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cues = remember { BabyEventType.entries }
    val tappedCues = remember { mutableStateSetOf<BabyEventType>() }
    val removalJobs = remember { HashMap<BabyEventType, Job>() }
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cues.forEach { type ->
            val selected = type in tappedCues
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.08f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "cue-chip-scale-${type.name}",
            )
            FilterChip(
                selected = selected,
                onClick = {
                    onCueTapped(type)
                    tappedCues.add(type)
                    removalJobs[type]?.cancel()
                    removalJobs[type] = scope.launch {
                        delay(1_200L)
                        tappedCues.remove(type)
                        removalJobs.remove(type)
                    }
                },
                label = {
                    Text(
                        text = "${type.emoji} ${type.label}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                modifier = Modifier.scale(scale),
            )
        }
    }
}
```

- [ ] **Step 5: Remove five now-unused imports**

All five are exclusively used by the removed blocks. Delete:
```kotlin
import androidx.compose.runtime.mutableStateSetOf   // line 66 — used only by tappedCues initializer
import androidx.compose.runtime.rememberCoroutineScope  // line 69 — used only by scope
import com.babytracker.domain.model.BabyEventType   // line 81 — used only by emoji/label extensions and CueQuickTapRow body
import kotlinx.coroutines.Job                        // line 97 — used only by HashMap<BabyEventType, Job>
import kotlinx.coroutines.launch                     // line 99 — used only by scope.launch inside CueQuickTapRow
```

(`import kotlinx.coroutines.delay` stays — it is used by `produceState` blocks in `ActiveSleepTimer` and similar composables. The pre-commit hook runs `ktlintFormat` which will auto-remove any stragglers if you miss one.)

- [ ] **Step 6: Verify it compiles**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL — no ambiguous reference errors. The call site `CueQuickTapRow(onCueTapped = viewModel::onCueTapped)` resolves to the imported component. `HomeScreenTest.kt` resolves via its explicit import added in Task 2.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/home/HomeScreen.kt
git commit -m "refactor(ui): remove private CueQuickTapRow from HomeScreen; import from component"
```

---

## Task 4: Add test to `SleepTrackingScreenTest.kt`

**Files:**
- Modify: `app/src/androidTest/java/com/babytracker/ui/sleep/SleepTrackingScreenTest.kt`

Add the import and a test asserting all 6 cue chips render inside `BabyTrackerTheme`. The test renders `CueQuickTapRow` directly (not via `SleepTrackingScreen`) to avoid Hilt setup — same pattern as `HomeScreenTest.cueRow_allChipsDisplayed`. Because it renders the component directly, this test passes as soon as Task 1 is complete; it is not a TDD failing-then-passing test. Its value is locking the 6-chip contract into the sleep test suite so any regression breaks the CI run for sleep tests specifically.

- [ ] **Step 1: Add import to `SleepTrackingScreenTest.kt`**

After the existing imports block (after line 26: `import java.time.LocalTime`), add:
```kotlin
import com.babytracker.ui.component.CueQuickTapRow
```

- [ ] **Step 2: Add the 6-chip presence test**

Before the closing `}` of the `SleepTrackingScreenTest` class (after the `sheetState` helper, which ends at line 155; line 156 is the class-closing `}`), add:

```kotlin
@Test
fun cueRow_allSixChipsDisplayed() {
    composeRule.setContent {
        BabyTrackerTheme {
            CueQuickTapRow(onCueTapped = {})
        }
    }
    composeRule.onNodeWithText("😪 Sleepy").assertIsDisplayed()
    composeRule.onNodeWithText("😋 Hungry").assertIsDisplayed()
    composeRule.onNodeWithText("😣 Fussy").assertIsDisplayed()
    composeRule.onNodeWithText("🤒 Sick").assertIsDisplayed()
    composeRule.onNodeWithText("🦷 Teething").assertIsDisplayed()
    composeRule.onNodeWithText("✈️ Travel").assertIsDisplayed()
}
```

- [ ] **Step 3: Verify it compiles**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL — the test compiles because `CueQuickTapRow` exists in the component (Task 1). Actual test execution requires a connected device/emulator (`./gradlew connectedAndroidTest`).

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/babytracker/ui/sleep/SleepTrackingScreenTest.kt
git commit -m "test(sleep): add 6-chip presence test for CueQuickTapRow on sleep screen"
```

---

## Task 5: Inject `LogBabyEventUseCase` into `SleepViewModel`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepViewModel.kt`

`LogBabyEventUseCase` is in `com.babytracker.domain.usecase.baby`. The handler mirrors `HomeViewModel.onCueTapped` exactly — fire-and-forget inside `runCatching` so DB errors never crash the UI.

- [ ] **Step 1: Add imports to `SleepViewModel.kt`**

After line 22 (`import com.babytracker.sharing.usecase.SyncToFirestoreUseCase`), add:
```kotlin
import com.babytracker.domain.model.BabyEventType
import com.babytracker.domain.usecase.baby.LogBabyEventUseCase
```

- [ ] **Step 2: Add `LogBabyEventUseCase` constructor parameter**

In the `@HiltViewModel class SleepViewModel @Inject constructor(` block, after `private val predictSleepWindow: PredictSleepWindowUseCase,` (the last existing param), add:
```kotlin
private val logBabyEvent: LogBabyEventUseCase,
```

The constructor should now end:
```kotlin
    private val predictSleepWindow: PredictSleepWindowUseCase,
    private val logBabyEvent: LogBabyEventUseCase,
) : ViewModel() {
```

- [ ] **Step 3: Add `onCueTapped` handler**

After `fun refreshSchedule()` (line 325), add:
```kotlin
fun onCueTapped(type: BabyEventType) {
    viewModelScope.launch { runCatching { logBabyEvent(type) } }
}
```

No `SleepViewModelTest` is added — `HomeViewModelTest` already covers `onCueTapped_delegatesToLogBabyEventUseCase` for the identical handler pattern. Adding a mirror test is deliberate scope exclusion (see spec §Testing).

- [ ] **Step 4: Verify it compiles**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL — Hilt resolves `LogBabyEventUseCase` because it is already `@Inject`-annotated and its `BabyEventRepository` is bound in `RepositoryModule`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/sleep/SleepViewModel.kt
git commit -m "feat(sleep): inject LogBabyEventUseCase into SleepViewModel; add onCueTapped"
```

---

## Task 6: Wire `CueQuickTapRow` into `SleepTrackingScreen`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt`

Add one `LazyColumn` item immediately after the `SleepRecommendationSection` item (currently at lines 227–232). No conditional rendering — matches home screen behaviour.

- [ ] **Step 1: Add import to `SleepTrackingScreen.kt`**

After the existing imports (after line 67: `import androidx.hilt.navigation.compose.hiltViewModel`), add:
```kotlin
import com.babytracker.ui.component.CueQuickTapRow
```

- [ ] **Step 2: Add `CueQuickTapRow` item after `SleepRecommendationSection`**

Locate this block (currently at lines 227–232):
```kotlin
            item {
                SleepRecommendationSection(
                    state = uiState.sleepPrediction,
                    schedule = uiState.schedule,
                )
            }
```

Immediately after its closing `}`, insert:
```kotlin
            item {
                CueQuickTapRow(onCueTapped = viewModel::onCueTapped)
            }
```

- [ ] **Step 3: Verify it compiles**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/sleep/SleepTrackingScreen.kt
git commit -m "feat(sleep): add CueQuickTapRow below SleepRecommendationSection"
```

---

## Task 7: Full validation

- [ ] **Step 1: Run unit tests**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, all unit tests pass.

- [ ] **Step 2: Run instrumentation tests (requires connected device/emulator)**

```bash
./gradlew connectedAndroidTest
```

Expected: All tests pass, including:
- `HomeScreenTest.cueRow_allChipsDisplayed` ✓
- `HomeScreenTest.cueRow_tapCallsCallback` ✓
- `HomeScreenTest.cueRow_chipIsSelectedThenReturnsToUnselectedAfterDelay` ✓
- `HomeScreenTest.cueRow_repeatedTapExtendsSelectedWindow` ✓
- `SleepTrackingScreenTest.cueRow_allSixChipsDisplayed` ✓

---

## Self-Review

**Spec coverage check:**

| Spec requirement | Task |
|-----------------|------|
| New `CueQuickTapRow.kt` in `ui/component/` | Task 1 |
| `public` visibility, file-private emoji/label | Task 1 |
| `removalJobs` map cancels in-flight removal on re-tap | Task 1 |
| `HomeScreenTest.kt` adds explicit import | Task 2 |
| `HomeScreen.kt` removes private copies, imports from component | Task 3 |
| `SleepTrackingScreenTest.kt` adds import + 6-chip test | Task 4 |
| `SleepViewModel` gains `LogBabyEventUseCase` + `onCueTapped` | Task 5 |
| `SleepTrackingScreen` adds item after `SleepRecommendationSection` | Task 6 |
| No `UiState` changes | All tasks — confirmed absent |
| `SleepViewModelTest` deliberately skipped | Task 5 — noted inline, confirmed absent |
