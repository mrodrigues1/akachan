# Breastfeeding Screen Audit Fixes

**Source:** `/impeccable audit breastfeeding screen` — scored 13/20 (Acceptable)
**Target:** Fix all P1 + P2 issues, then P3s if time allows.

---

## Context

Native Android Jetpack Compose app. Design system uses Material 3 with custom Akachan tokens:
- Carnation Pink = feeding domain (`primary` / `primaryContainer`)
- Sleep Blue = sleep domain (`secondary` / `secondaryContainer`)
- Warm Cream = universal surface (`surface`)
- All semantic tokens in `ui/theme/Color.kt` + `ui/theme/Theme.kt`

Files in scope:
- `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingScreen.kt`
- `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingHistoryScreen.kt`
- `app/src/main/java/com/babytracker/ui/breastfeeding/BreastfeedingViewModel.kt`
- `app/src/main/java/com/babytracker/ui/component/SideSelector.kt`
- `app/src/main/java/com/babytracker/ui/component/HistoryCard.kt`
- `app/src/main/java/com/babytracker/ui/component/TimerDisplay.kt`

---

## Task List

### P1 — Fix before anything else

#### Task 1.1 — SideSelector: add selected state semantics
**File:** `SideSelector.kt:40–88`
**Problem:** TalkBack users hear "Left" / "Right" but never "selected" — violates WCAG 4.1.2.
**Fix:**
```kotlin
Card(
    onClick = { onSideSelected(side) },
    modifier = Modifier
        .weight(1f)
        .height(88.dp)
        .semantics {
            role = Role.RadioButton
            selected = isSelected
            contentDescription = "$label${if (isSelected) ", selected" else ""}"
        },
    ...
)
```
Add `import androidx.compose.ui.semantics.Role` and `import androidx.compose.ui.semantics.selected`.

#### Task 1.2 — BreastfeedingScreen: remove Unicode `●` from status strings
**File:** `BreastfeedingScreen.kt:145` and `BreastfeedingScreen.kt:233`

Line 145:
```kotlin
// Before
val statusText = if (activeSession.isPaused) "Session paused" else "● Session in progress"

// After
val statusText = if (activeSession.isPaused) "Session paused" else "Session in progress"
```

Line 233 — the per-side card label:
```kotlin
// Before
text = if (isCurrentSide) "● ${side.displayName()}" else side.displayName(),

// After
text = side.displayName(),
```
The `primaryContainer` fill already signals the current side visually.

---

### P2 — Fix before release

#### Task 2.1 — BreastfeedingHistoryScreen: fix right breast badge color
**File:** `BreastfeedingHistoryScreen.kt:127–132`
**Problem:** Right breast uses `secondaryContainer` (Sleep Blue domain) in a feeding-domain screen — semantic color violation.
**Fix:** Use `primaryContainer` for both sides. Differentiate with opacity if desired.
```kotlin
badgeColor = MaterialTheme.colorScheme.primaryContainer
// Remove the if (isLeft) ... else ... branch entirely; both sides are feeding domain
```

#### Task 2.2 — BreastfeedingViewModel: remove 1-second duration ticker
**File:** `BreastfeedingViewModel.kt:136–149`
**Problem:** `while(true) + delay(1000L)` loop emits full `_uiState` every second → constant recompositions. `TimerDisplay` already handles its own ticking internally from `startTimeMillis`.

The per-side duration cards (`firstSideDuration`, `secondSideDuration`) are the only reason this loop exists. These should tick locally in the composable, not from the ViewModel.

**Fix:**
1. Remove the 1-second while loop coroutine from `init` entirely (lines 136–149).
2. In `BreastfeedingScreen.kt`, derive `firstSideDuration` and `secondSideDuration` locally with a `LaunchedEffect` that ticks every second, reading from `activeSession.startTime`, `activeSession.switchTime`, `activeSession.pausedDurationMs`.
3. Keep `currentSide` computation in the ViewModel (it's a discrete event: changes on switch/start) — remove it from the ticker and compute it once in the `combine` block at line 81 (it's already computed there via `sessionDurations?.first`).

Local composable tick pattern:
```kotlin
// Inside BreastfeedingScreen, within the activeSession != null branch
var firstSideDuration by remember { mutableStateOf(Duration.ZERO) }
var secondSideDuration by remember { mutableStateOf(Duration.ZERO) }

LaunchedEffect(activeSession.id, activeSession.isPaused) {
    while (true) {
        val now = if (activeSession.isPaused) activeSession.pausedAt!! else Instant.now()
        firstSideDuration = activeSession.switchTime
            ?.let { Duration.between(activeSession.startTime, it) }
            ?: Duration.between(activeSession.startTime, now)
                .minus(Duration.ofMillis(activeSession.pausedDurationMs))
        secondSideDuration = activeSession.switchTime
            ?.let { Duration.between(it, now).minus(Duration.ofMillis(activeSession.pausedDurationMs)) }
            ?: Duration.ZERO
        if (activeSession.isPaused) break
        delay(1000L)
    }
}
```
Remove `firstSideDuration` and `secondSideDuration` from `BreastfeedingUiState` and `currentSide` can remain (it's event-driven).

**Note:** After removing the ticker, remove the fields from `BreastfeedingUiState` if they are no longer needed there: `firstSideDuration`, `secondSideDuration`. Keep `currentSide` since it's computed correctly in the `combine` block.

#### Task 2.3 — BreastfeedingScreen: fix Start Session button typography
**File:** `BreastfeedingScreen.kt:368`
**Problem:** Uses `titleSmall` — should be `labelLarge` like all other buttons on this screen.
```kotlin
// Before
Text("Start Session", style = MaterialTheme.typography.titleSmall)

// After
Text("Start Session", style = MaterialTheme.typography.labelLarge)
```

#### Task 2.4 — BreastfeedingHistoryScreen: memoize sort + duration fold
**File:** `BreastfeedingHistoryScreen.kt:98–115`
**Problem:** `sortedByDescending` + `fold` run on every recomposition.
```kotlin
// Before (inside composable body)
grouped.entries.sortedByDescending { it.key }.forEach { (date, sessions) ->
    val totalDuration = sessions
        .filter { it.endTime != null }
        ...
        .fold(Duration.ZERO) { acc, d -> acc + d }
    ...
}

// After
val sortedGroups = remember(grouped) {
    grouped.entries
        .sortedByDescending { it.key }
        .map { (date, sessions) ->
            val totalDuration = sessions
                .filter { it.endTime != null }
                .mapNotNull { session ->
                    session.endTime?.let { end -> Duration.between(session.startTime, end) }
                }
                .fold(Duration.ZERO) { acc, d -> acc + d }
            Triple(date, sessions, totalDuration)
        }
}
```
Then iterate `sortedGroups` instead.

---

### P3 — Polish

#### Task 3.1 — SideSelector: allow tile height to grow with large font scale
**File:** `SideSelector.kt:44` and `SideSelector.kt:65`
```kotlin
// Before
.height(88.dp)

// After
.heightIn(min = 88.dp)
```
Both occurrences (outer Card modifier and inner Column modifier).

#### Task 3.2 — HistoryCard: merge semantics for single TalkBack focus stop
**File:** `HistoryCard.kt:44–84`
**Problem:** 4 separate TalkBack focus stops per card (badge, title, subtitle, trailing).
```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp)
        .semantics(mergeDescendants = true) {},  // add this
    verticalAlignment = Alignment.CenterVertically,
)
```

#### Task 3.3 — TimerDisplay: include paused state in contentDescription
**File:** `TimerDisplay.kt:93–98`
```kotlin
// Before
val timerDescription = if (hasRing) {
    "Session timer: $timeText, $percent% of $maxMinutes minutes"
} else {
    "Session timer: $timeText"
}

// After
val pausedSuffix = if (!isRunning) ", paused" else ""
val timerDescription = if (hasRing) {
    "Session timer: $timeText, $percent% of $maxMinutes minutes$pausedSuffix"
} else {
    "Session timer: $timeText$pausedSuffix"
}
```

#### Task 3.4 — BreastfeedingScreen + HistoryScreen: add heading semantics
**Files:** `BreastfeedingScreen.kt:333`, `BreastfeedingHistoryScreen.kt` (empty state title)

```kotlin
// BreastfeedingScreen.kt:333
Text(
    text = "Start a feeding session",
    style = MaterialTheme.typography.headlineLarge,
    modifier = Modifier.semantics { heading() }
)
```

Add `import androidx.compose.ui.semantics.heading` where needed.

---

## After All Fixes

Run in order:
```bash
./gradlew ktlintFormat
./gradlew detekt
./gradlew test
```

Fix any lint/detekt violations before committing. Commit each logical group separately using Conventional Commits:
- `fix(ui): add selected state semantics to SideSelector`
- `fix(ui): remove unicode bullet from status strings`
- `fix(ui): use primaryContainer for both breast sides in history`
- `perf(breastfeeding): move duration ticking to composable, remove ViewModel ticker`
- `fix(ui): use labelLarge typography on Start Session button`
- `perf(breastfeeding): memoize history sort and duration fold`
- `fix(ui): allow SideSelector tiles to grow with large font scale`
- `fix(ui): merge HistoryCard semantics for single TalkBack focus`
- `fix(ui): include paused state in TimerDisplay contentDescription`
- `fix(ui): add heading semantics to section titles`
