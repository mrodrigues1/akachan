# Last Feeding Summary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Last Feeding Summary" card below the idle-state controls on the Breastfeeding screen, showing elapsed time since the last session, which breast to start with next, and a per-side duration breakdown — hidden entirely when no previous session exists.

**Architecture:** A new `LastFeedingSummaryState` sealed class is added to `BreastfeedingViewModel.kt` and embedded in `BreastfeedingUiState`. The ViewModel derives the summary from the existing `history` StateFlow combined with a 60-second ticker Flow. The composable for the card lives in `BreastfeedingScreen.kt` and reuses `HistoryCard` for the per-side breakdown.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose + Material 3, Hilt, Coroutines + Flow, JUnit 5, MockK 1.13.13, Turbine 1.2.0.

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `app/src/main/java/com/babytracker/util/DateTimeExt.kt` | **Modify** | Add `Duration.formatElapsedAgo()` extension |
| `app/src/test/java/com/babytracker/util/DateTimeExtTest.kt` | **Modify** | Add tests for `formatElapsedAgo()` |
| `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt` | **Modify** | Add `LastFeedingSummaryState` sealed class, update `BreastfeedingUiState`, add ticker + last-session derivation |
| `app/src/test/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModelTest.kt` | **Modify** | Add tests for side recommendation and elapsed label |
| `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingScreen.kt` | **Modify** | Add `LastFeedingSummaryCard` private composable, update idle state branch to show it |

---

## Task 1: Add `formatElapsedAgo()` extension

**Files:**
- Modify: `app/src/main/java/com/babytracker/util/DateTimeExt.kt`
- Test: `app/src/test/java/com/babytracker/util/DateTimeExtTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `DateTimeExtTest.kt`, before the closing brace of the class:

```kotlin
import java.time.Duration

@Test
fun `formatElapsedAgo_underOneMinute_returnsJustNow`() {
    assertEquals("Just now", Duration.ofSeconds(30).formatElapsedAgo())
}

@Test
fun `formatElapsedAgo_exactlyOneMinute_returnsMinutesAgo`() {
    assertEquals("1m ago", Duration.ofMinutes(1).formatElapsedAgo())
}

@Test
fun `formatElapsedAgo_minutes_returnsMinutesAgo`() {
    assertEquals("14m ago", Duration.ofMinutes(14).formatElapsedAgo())
}

@Test
fun `formatElapsedAgo_hoursAndMinutes_returnsHoursAndMinutesAgo`() {
    assertEquals("2h 14m ago", Duration.ofHours(2).plus(Duration.ofMinutes(14)).formatElapsedAgo())
}

@Test
fun `formatElapsedAgo_exactHour_returnsZeroMinutes`() {
    assertEquals("1h 0m ago", Duration.ofHours(1).formatElapsedAgo())
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
./gradlew :app:test --tests "com.babytracker.util.DateTimeExtTest" 2>&1 | tail -20
```

Expected: FAIL — `formatElapsedAgo` unresolved reference.

- [ ] **Step 3: Implement `formatElapsedAgo()`**

Add to the bottom of `app/src/main/java/com/babytracker/util/DateTimeExt.kt`:

```kotlin
fun Duration.formatElapsedAgo(): String {
    val hours = toHours()
    val minutes = (toMinutes() % 60).toInt()
    return when {
        hours > 0 -> "${hours}h ${minutes}m ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "Just now"
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew :app:test --tests "com.babytracker.util.DateTimeExtTest" 2>&1 | tail -20
```

Expected: All `DateTimeExtTest` tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/util/DateTimeExt.kt \
        app/src/test/java/com/babytracker/util/DateTimeExtTest.kt
git commit -m "feat(breastfeeding): add Duration.formatElapsedAgo() extension"
```

---

## Task 2: Add `LastFeedingSummaryState` sealed class and extend `BreastfeedingUiState`

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt`

- [ ] **Step 1: Add the sealed class and update `BreastfeedingUiState`**

Replace the current `BreastfeedingUiState` data class (lines 33–38 in `BreastfeedingViewModel.kt`) with the following block. Add it **above** the `BreastfeedingUiState` declaration:

```kotlin
sealed class LastFeedingSummaryState {
    object Empty : LastFeedingSummaryState()
    data class Populated(
        val lastSession: BreastfeedingSession,
        val elapsedLabel: String,
        val nextRecommendedSide: BreastSide,
        val firstSideDuration: Duration,
        val secondSideDuration: Duration?
    ) : LastFeedingSummaryState()
}

data class BreastfeedingUiState(
    val activeSession: BreastfeedingSession? = null,
    val selectedSide: BreastSide? = null,
    val maxPerBreastMinutes: Int = 0,
    val maxTotalFeedMinutes: Int = 0,
    val lastFeedingSummary: LastFeedingSummaryState = LastFeedingSummaryState.Empty
)
```

The existing imports already cover `BreastSide`, `BreastfeedingSession`, and `Duration`. No new imports needed for this step.

- [ ] **Step 2: Verify the project compiles**

```
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|warning:" | head -20
```

Expected: no errors (the new field has a default, so existing call sites are unaffected).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt
git commit -m "feat(breastfeeding): add LastFeedingSummaryState sealed class to UiState"
```

---

## Task 3: Wire last-session derivation and minute-ticker in ViewModel

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt`

- [ ] **Step 1: Add the private helper and the coroutine in `init`**

Add the import at the top of the file (after the existing imports):

```kotlin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
```

Add a private function at the bottom of the `BreastfeedingViewModel` class (before the closing brace):

```kotlin
private fun buildLastFeedingSummary(lastSession: BreastfeedingSession?): LastFeedingSummaryState {
    if (lastSession == null) return LastFeedingSummaryState.Empty
    val endTime = lastSession.endTime ?: return LastFeedingSummaryState.Empty

    val elapsed = Duration.between(endTime, Instant.now())
    val elapsedLabel = elapsed.formatElapsedAgo()

    val endingSide = if (lastSession.switchTime != null) {
        if (lastSession.startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT
    } else {
        lastSession.startingSide
    }
    val nextRecommendedSide = if (endingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT

    val firstSideDuration: Duration = lastSession.switchTime
        ?.let { Duration.between(lastSession.startTime, it) }
        ?: Duration.between(lastSession.startTime, endTime)

    val secondSideDuration: Duration? = lastSession.switchTime
        ?.let { Duration.between(it, endTime) }

    return LastFeedingSummaryState.Populated(
        lastSession = lastSession,
        elapsedLabel = elapsedLabel,
        nextRecommendedSide = nextRecommendedSide,
        firstSideDuration = firstSideDuration,
        secondSideDuration = secondSideDuration
    )
}
```

Add the required import for `formatElapsedAgo()` — it is in the `util` package. The existing `BreastfeedingViewModel.kt` does not import from `util`. Add:

```kotlin
import com.babytracker.util.formatElapsedAgo
```

Inside `init { }`, **after** the existing `viewModelScope.launch { combine(...).collect { ... } }` block, add a second launch:

```kotlin
viewModelScope.launch {
    val ticker = flow<Unit> {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(60_000L)
        }
    }
    combine(
        history.map { sessions ->
            sessions.filter { it.endTime != null }.maxByOrNull { it.endTime!! }
        },
        ticker
    ) { lastSession, _ ->
        buildLastFeedingSummary(lastSession)
    }.collect { summary ->
        _uiState.value = _uiState.value.copy(lastFeedingSummary = summary)
    }
}
```

- [ ] **Step 2: Verify the project compiles**

```
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
```

Expected: no errors.

- [ ] **Step 3: Run all existing ViewModel tests to ensure no regression**

```
./gradlew :app:test --tests "com.babytracker.ui.breastfeeding.BreastfeedingViewModelTest" 2>&1 | tail -20
```

Expected: all existing tests PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt
git commit -m "feat(breastfeeding): derive last session summary with minute ticker in ViewModel"
```

---

## Task 4: Add ViewModel unit tests for summary logic

**Files:**
- Test: `app/src/test/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModelTest.kt`

- [ ] **Step 1: Add new imports to the test file**

Add to the import block at the top of `BreastfeedingViewModelTest.kt`:

```kotlin
import io.mockk.unmockkAll
import io.mockk.mockkStatic
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertNull
```

(`unmockkAll` and `mockkStatic` are already available from the `io.mockk` dependency — just add the missing import if not already present. `assertNull` is already imported.)

- [ ] **Step 2: Write all failing tests**

Add the following test methods inside `BreastfeedingViewModelTest`, after the last existing test:

```kotlin
@Test
fun `lastFeedingSummary is Empty when no sessions exist`() = runTest {
    every { getHistory() } returns flowOf(emptyList())
    viewModel = createViewModel()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(LastFeedingSummaryState.Empty, viewModel.uiState.value.lastFeedingSummary)
}

@Test
fun `lastFeedingSummary is Empty when all sessions are still in progress`() = runTest {
    val inProgress = BreastfeedingSession(
        id = 1L,
        startTime = Instant.parse("2026-04-13T10:00:00Z"),
        endTime = null,
        startingSide = BreastSide.LEFT
    )
    every { getHistory() } returns flowOf(listOf(inProgress))
    viewModel = createViewModel()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(LastFeedingSummaryState.Empty, viewModel.uiState.value.lastFeedingSummary)
}

@Test
fun `lastFeedingSummary recommends RIGHT when last session ended on LEFT without switch`() = runTest {
    mockkStatic(Instant::class)
    every { Instant.now() } returns Instant.parse("2026-04-13T12:00:00Z")

    val session = BreastfeedingSession(
        id = 1L,
        startTime = Instant.parse("2026-04-13T10:00:00Z"),
        endTime = Instant.parse("2026-04-13T10:30:00Z"),
        startingSide = BreastSide.LEFT,
        switchTime = null
    )
    every { getHistory() } returns flowOf(listOf(session))
    viewModel = createViewModel()
    testDispatcher.scheduler.advanceUntilIdle()

    val summary = viewModel.uiState.value.lastFeedingSummary
    assertTrue(summary is LastFeedingSummaryState.Populated)
    assertEquals(BreastSide.RIGHT, (summary as LastFeedingSummaryState.Populated).nextRecommendedSide)

    unmockkAll()
}

@Test
fun `lastFeedingSummary recommends LEFT when last session switched from LEFT to RIGHT`() = runTest {
    mockkStatic(Instant::class)
    every { Instant.now() } returns Instant.parse("2026-04-13T12:00:00Z")

    // Started LEFT, switched → ended on RIGHT → recommend LEFT
    val session = BreastfeedingSession(
        id = 1L,
        startTime = Instant.parse("2026-04-13T10:00:00Z"),
        endTime = Instant.parse("2026-04-13T10:30:00Z"),
        startingSide = BreastSide.LEFT,
        switchTime = Instant.parse("2026-04-13T10:15:00Z")
    )
    every { getHistory() } returns flowOf(listOf(session))
    viewModel = createViewModel()
    testDispatcher.scheduler.advanceUntilIdle()

    val summary = viewModel.uiState.value.lastFeedingSummary
    assertTrue(summary is LastFeedingSummaryState.Populated)
    assertEquals(BreastSide.LEFT, (summary as LastFeedingSummaryState.Populated).nextRecommendedSide)

    unmockkAll()
}

@Test
fun `lastFeedingSummary recommends LEFT when last session ended on RIGHT without switch`() = runTest {
    mockkStatic(Instant::class)
    every { Instant.now() } returns Instant.parse("2026-04-13T12:00:00Z")

    val session = BreastfeedingSession(
        id = 1L,
        startTime = Instant.parse("2026-04-13T10:00:00Z"),
        endTime = Instant.parse("2026-04-13T10:30:00Z"),
        startingSide = BreastSide.RIGHT,
        switchTime = null
    )
    every { getHistory() } returns flowOf(listOf(session))
    viewModel = createViewModel()
    testDispatcher.scheduler.advanceUntilIdle()

    val summary = viewModel.uiState.value.lastFeedingSummary as LastFeedingSummaryState.Populated
    assertEquals(BreastSide.LEFT, summary.nextRecommendedSide)

    unmockkAll()
}

@Test
fun `lastFeedingSummary elapsed label formats hours and minutes ago correctly`() = runTest {
    mockkStatic(Instant::class)
    // end at 09:45, now is 12:00 → 2h 15m elapsed
    every { Instant.now() } returns Instant.parse("2026-04-13T12:00:00Z")

    val session = BreastfeedingSession(
        id = 1L,
        startTime = Instant.parse("2026-04-13T09:15:00Z"),
        endTime = Instant.parse("2026-04-13T09:45:00Z"),
        startingSide = BreastSide.RIGHT
    )
    every { getHistory() } returns flowOf(listOf(session))
    viewModel = createViewModel()
    testDispatcher.scheduler.advanceUntilIdle()

    val summary = viewModel.uiState.value.lastFeedingSummary as LastFeedingSummaryState.Populated
    assertEquals("2h 15m ago", summary.elapsedLabel)

    unmockkAll()
}

@Test
fun `lastFeedingSummary computes correct durations when no side switch`() = runTest {
    mockkStatic(Instant::class)
    every { Instant.now() } returns Instant.parse("2026-04-13T12:00:00Z")

    val session = BreastfeedingSession(
        id = 1L,
        startTime = Instant.parse("2026-04-13T10:00:00Z"),
        endTime = Instant.parse("2026-04-13T10:20:00Z"),
        startingSide = BreastSide.LEFT,
        switchTime = null
    )
    every { getHistory() } returns flowOf(listOf(session))
    viewModel = createViewModel()
    testDispatcher.scheduler.advanceUntilIdle()

    val summary = viewModel.uiState.value.lastFeedingSummary as LastFeedingSummaryState.Populated
    assertEquals(Duration.ofMinutes(20), summary.firstSideDuration)
    assertNull(summary.secondSideDuration)

    unmockkAll()
}

@Test
fun `lastFeedingSummary computes correct durations when sides were switched`() = runTest {
    mockkStatic(Instant::class)
    every { Instant.now() } returns Instant.parse("2026-04-13T12:00:00Z")

    // 10:00 start → 10:15 switch → 10:30 end: first=15m, second=15m
    val session = BreastfeedingSession(
        id = 1L,
        startTime = Instant.parse("2026-04-13T10:00:00Z"),
        endTime = Instant.parse("2026-04-13T10:30:00Z"),
        startingSide = BreastSide.LEFT,
        switchTime = Instant.parse("2026-04-13T10:15:00Z")
    )
    every { getHistory() } returns flowOf(listOf(session))
    viewModel = createViewModel()
    testDispatcher.scheduler.advanceUntilIdle()

    val summary = viewModel.uiState.value.lastFeedingSummary as LastFeedingSummaryState.Populated
    assertEquals(Duration.ofMinutes(15), summary.firstSideDuration)
    assertEquals(Duration.ofMinutes(15), summary.secondSideDuration)

    unmockkAll()
}

@Test
fun `lastFeedingSummary picks the most recent completed session from history`() = runTest {
    mockkStatic(Instant::class)
    every { Instant.now() } returns Instant.parse("2026-04-13T12:00:00Z")

    val older = BreastfeedingSession(
        id = 1L,
        startTime = Instant.parse("2026-04-13T08:00:00Z"),
        endTime = Instant.parse("2026-04-13T08:20:00Z"),
        startingSide = BreastSide.LEFT
    )
    val newer = BreastfeedingSession(
        id = 2L,
        startTime = Instant.parse("2026-04-13T10:00:00Z"),
        endTime = Instant.parse("2026-04-13T10:30:00Z"),
        startingSide = BreastSide.RIGHT
    )
    every { getHistory() } returns flowOf(listOf(older, newer))
    viewModel = createViewModel()
    testDispatcher.scheduler.advanceUntilIdle()

    val summary = viewModel.uiState.value.lastFeedingSummary as LastFeedingSummaryState.Populated
    assertEquals(2L, summary.lastSession.id)

    unmockkAll()
}
```

- [ ] **Step 3: Run tests to verify they fail correctly**

```
./gradlew :app:test --tests "com.babytracker.ui.breastfeeding.BreastfeedingViewModelTest" 2>&1 | tail -30
```

Expected: new tests FAIL (referencing `LastFeedingSummaryState` which isn't on the classpath yet for the test runner — it should be after Task 2/3 are done). If Tasks 2 and 3 are already committed, the tests should compile and fail only on assertion mismatches or timing issues.

- [ ] **Step 4: Run all ViewModel tests**

```
./gradlew :app:test --tests "com.babytracker.ui.breastfeeding.BreastfeedingViewModelTest" 2>&1 | tail -30
```

Expected: ALL tests (old + new) PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModelTest.kt
git commit -m "test(breastfeeding): add unit tests for LastFeedingSummaryState logic"
```

---

## Task 5: Add `LastFeedingSummaryCard` composable and update the idle branch

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingScreen.kt`

- [ ] **Step 1: Add new imports to `BreastfeedingScreen.kt`**

Add to the existing import block:

```kotlin
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import com.babytracker.ui.component.HistoryCard
import com.babytracker.util.formatDuration
import com.babytracker.util.formatTime12h
```

(Some of these may already be present — only add what is missing.)

- [ ] **Step 2: Update the idle state branch to show the summary card and add scroll support**

Locate the `else` block inside the outer `Column` in `BreastfeedingScreen` (currently starting at line 303 with `// Idle state`). Replace it with:

```kotlin
} else {
    // Idle state — scrollable so summary card fits on all screen sizes
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Start a feeding session",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        SideSelector(
            selectedSide = uiState.selectedSide,
            onSideSelected = viewModel::onSideSelected
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onStartSessionWithPermission() },
            enabled = uiState.selectedSide != null,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text("Start Session", style = MaterialTheme.typography.titleSmall)
        }

        val summary = uiState.lastFeedingSummary
        if (summary is LastFeedingSummaryState.Populated) {
            Spacer(modifier = Modifier.height(24.dp))
            LastFeedingSummaryCard(summary = summary)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
```

Also update the outer `Column` to remove `verticalArrangement = Arrangement.Center` (so the inner columns control their own layout) and make it `Arrangement.Top`:

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Top
) {
```

- [ ] **Step 3: Add the `LastFeedingSummaryCard` private composable**

Add this private composable function at the bottom of `BreastfeedingScreen.kt`, after the `isNotificationPermissionGranted` function:

```kotlin
@Composable
private fun LastFeedingSummaryCard(summary: LastFeedingSummaryState.Populated) {
    val session = summary.lastSession
    val secondSide = if (session.startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT
    val recommendedLabel = summary.nextRecommendedSide.name
        .lowercase()
        .replaceFirstChar { it.uppercase() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "LAST FEEDING",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = summary.elapsedLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "Start with: $recommendedLabel breast",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Per-side breakdown — reuses HistoryCard from the history screen
            HistoryCard(
                title = "${session.startingSide.name.lowercase().replaceFirstChar { it.uppercase() }} breast",
                subtitle = "First side · ${session.startTime.formatTime12h()}",
                trailing = summary.firstSideDuration.formatDuration(),
                badgeEmoji = "🍼",
                badgeColor = if (session.startingSide == BreastSide.LEFT) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            )

            if (summary.secondSideDuration != null) {
                HistoryCard(
                    title = "${secondSide.name.lowercase().replaceFirstChar { it.uppercase() }} breast",
                    subtitle = "Second side",
                    trailing = summary.secondSideDuration.formatDuration(),
                    badgeEmoji = "🍼",
                    badgeColor = if (secondSide == BreastSide.LEFT) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                )
            }
        }
    }
}
```

- [ ] **Step 4: Verify the project builds**

```
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:" | head -20
```

Expected: no errors.

- [ ] **Step 5: Run all tests to confirm no regressions**

```
./gradlew :app:test 2>&1 | tail -30
```

Expected: ALL tests PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingScreen.kt
git commit -m "feat(breastfeeding): add LastFeedingSummaryCard to idle state"
```

---

## Task 6: Full build and PR

- [ ] **Step 1: Run the full test suite**

```
./gradlew test 2>&1 | tail -30
```

Expected: no failures.

- [ ] **Step 2: Build a debug APK to confirm assembler passes**

```
./gradlew assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Create PR**

```bash
gh pr create \
  --title "feat(breastfeeding): add Last Feeding Summary section to idle state" \
  --body "$(cat <<'EOF'
## Summary
- Adds a **Last Feeding Summary** card below the Start Session controls in the breastfeeding idle state
- Card shows elapsed time since last session (live-updating every minute), side recommendation (opposite of ending side), and per-side duration breakdown using the existing `HistoryCard` composable
- Card is hidden entirely when no previous completed session exists
- Summary state is represented as a `LastFeedingSummaryState` sealed class (`Empty` / `Populated`) inside `BreastfeedingUiState`; all computation (side recommendation, elapsed label, per-side durations) happens in the ViewModel, not the UI

## Test plan
- [ ] Run `./gradlew test` — all unit tests pass
- [ ] Launch the app in the emulator with no prior sessions → only the Start Session controls are visible; no card appears
- [ ] Complete one session → return to the breastfeeding screen in idle state → Last Feeding Summary card appears with correct elapsed time, correct next-side recommendation, and correct per-side durations
- [ ] Complete a session with a side switch → summary shows two `HistoryCard` rows and recommends the starting side
- [ ] Complete a session without switching → summary shows one row and recommends the opposite side
- [ ] Wait one minute on the idle screen → elapsed label updates automatically

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Spec Coverage Check

| Requirement | Task |
|-------------|------|
| Card appears below session controls in idle state | Task 5 |
| Live elapsed time counter ("Xh Ym ago"), updating every minute | Task 3 (ticker), Task 5 (display) |
| Side recommendation (opposite of ending side) | Task 3 (`buildLastFeedingSummary`), Task 4 (tests) |
| Per-side duration breakdown, reusing history composables | Task 5 (`HistoryCard` reuse) |
| Card hidden entirely when no session exists | Task 3 (`Empty` state), Task 5 (conditional rendering) |
| No placeholder/skeleton loaders | Task 5 — only rendered when `Populated` |
| Elapsed time driven by ViewModel StateFlow/derived state, not composable | Task 3 (ticker in `viewModelScope`) |
| Side recommendation logic in ViewModel, not UI | Task 3 (`buildLastFeedingSummary`) |
| Distinct sealed UI states for empty and populated | Task 2 |
| Unit tests for side recommendation and elapsed time | Task 4 |
