# Trends UI, Navigation & Home Tile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

LINEAR_ISSUE: AKA-147

**Goal:** Add the Vico dependency and the `TrendsScreen` (three Material 3-themed charts + a 7/14/30-day range selector + per-chart empty states + accessibility), reachable from a new reorderable Home tile via `Routes.TRENDS`.

**Architecture:** Presentation layer only. Charts use Vico 3.1.0 (`compose-m3`), styled with `MaterialTheme.colorScheme` colors so they match the Baby palette + dark mode. The screen collects `TrendsViewModel.uiState` and renders one `Card` per metric. Home wiring follows the existing `HomeTile` enum + `HomeTileContent` + `HomeTileCallbacks` pattern; navigation follows the existing `Routes` + `AppNavGraph` pattern.

**Tech Stack:** Jetpack Compose, Material 3, Vico 3.1.0 (compose-m3), Hilt Navigation Compose, Compose UI Test + Robolectric.

**Dependencies:** Plans 01 (`feat/trends-domain`) and 02 (`feat/trends-viewmodel`) must be merged/present — needs `TrendRange`, the `Daily*` models, and `TrendsViewModel`.

**Suggested implementation branch:** `feat/trends-ui`

**Workflow note:** implement-first, then tests (CLAUDE.md / user preference).

**Spec:** `docs/superpowers/specs/2026-06-14-charts-and-trends-design.md`

---

## File Structure

- Modify `gradle/libs.versions.toml` — add Vico version + `vico-compose-m3` library.
- Modify `app/build.gradle.kts` — add `implementation(libs.vico.compose.m3)`.
- Modify `CLAUDE.md` — add a Charting row to the tech-stack table.
- Modify `app/src/main/java/com/babytracker/navigation/Routes.kt` — add `TRENDS`.
- Modify `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt` — add `Routes.TRENDS` composable + thread `onNavigateToTrends` into the HOME composable.
- Modify `app/src/main/java/com/babytracker/domain/model/HomeTile.kt` — add `TRENDS` + place in `DEFAULT_ORDER`.
- Modify `app/src/main/java/com/babytracker/ui/home/HomeTileContent.kt` — add `HomeTile.TRENDS` branch + `onTrends` callback.
- Modify `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt` — add `onNavigateToTrends` param, wire `onTrends` into `HomeTileCallbacks`, add `TrendsHomeCard`.
- Create `app/src/main/java/com/babytracker/ui/trends/TrendsScreen.kt` — screen + range selector + three chart composables + empty states.
- Create `app/src/androidTest/java/com/babytracker/ui/trends/TrendsScreenTest.kt` — Compose UI test (Robolectric).

---

### Task 1: Add Vico dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Add version + library to the catalog**

In `gradle/libs.versions.toml`, under `[versions]` add:

```toml
vico = "3.1.0"
```

Under `[libraries]` add:

```toml
vico-compose-m3 = { group = "com.patrykandpatrick.vico", name = "compose-m3", version.ref = "vico" }
```

- [ ] **Step 2: Add the dependency**

In `app/build.gradle.kts`, in the `dependencies { }` block next to the other Compose UI implementations (e.g. after `implementation(libs.compose.material3)`):

```kotlin
implementation(libs.vico.compose.m3)
```

- [ ] **Step 3: Update CLAUDE.md tech-stack table**

Add a row to the Tech Stack table (after the Reorder row):

```
| Charting | Vico 3.1.0 (compose-m3) |
```

- [ ] **Step 4: Sync & compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Vico resolves from Maven Central).

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts CLAUDE.md
git commit -m "chore(trends): add Vico 3.1.0 charting dependency"
```

---

### Task 2: Route + navigation wiring

**Files:**
- Modify: `app/src/main/java/com/babytracker/navigation/Routes.kt`
- Modify: `app/src/main/java/com/babytracker/navigation/AppNavGraph.kt`

- [ ] **Step 1: Add the route constant**

In `Routes.kt`, alongside `GROWTH`/`MILESTONES`:

```kotlin
const val TRENDS = "trends"
```

- [ ] **Step 2: Register the composable and thread the HOME callback**

In `AppNavGraph.kt`, add `onNavigateToTrends` to the `HomeScreen(...)` call inside `composable(Routes.HOME)`:

```kotlin
onNavigateToTrends = { navController.navigate(Routes.TRENDS) },
```

And add a new destination next to `composable(Routes.GROWTH) { … }`:

```kotlin
composable(Routes.TRENDS) {
    TrendsScreen(onNavigateBack = { navController.popBackStack() })
}
```

Add the import: `import com.babytracker.ui.trends.TrendsScreen`.

- [ ] **Step 3: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: FAIL — `TrendsScreen` / `onNavigateToTrends` not yet defined. (Continue; Tasks 3-4 add them. If you prefer green-between-tasks, do Tasks 3 and 4 before re-running.)

---

### Task 3: Home tile

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/model/HomeTile.kt`
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeTileContent.kt`
- Modify: `app/src/main/java/com/babytracker/ui/home/HomeScreen.kt`

- [ ] **Step 1: Add the enum constant + default order**

In `HomeTile.kt`, add `TRENDS` to the enum (after `MILESTONES`) and to `DEFAULT_ORDER` (after `MILESTONES`). `reconcile()` auto-appends it for existing users, so no migration is needed.

```kotlin
// enum:
    MILESTONES,
    TRENDS,
    TIP,
// DEFAULT_ORDER:
            MILESTONES,
            TRENDS,
            TIP,
```

- [ ] **Step 2: Add the callback + tile branch**

In `HomeTileContent.kt`, add to `HomeTileCallbacks`:

```kotlin
val onTrends: () -> Unit,
```

Add to the `when (tile)` in `HomeTileContent`:

```kotlin
HomeTile.TRENDS -> TrendsHomeCard(callbacks.onTrends, modifier)
```

- [ ] **Step 3: Add the card + thread the param**

In `HomeScreen.kt`:

Add the parameter to `fun HomeScreen(...)` (after `onNavigateToMilestones`):

```kotlin
onNavigateToTrends: () -> Unit = {},
```

Add `onTrends = onNavigateToTrends,` to the `HomeTileCallbacks(...)` constructor call.

Add the card composable (mirror `GrowthHomeCard`):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrendsHomeCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 120.dp)
            .semantics { contentDescription = "Trends. Open feeding and sleep charts." },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
        ) {
            Text(
                text = "📊",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Trends",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Feeding & sleep patterns",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
```

- [ ] **Step 4: Update EVERY `HomeTileCallbacks(...)` construction site**

Adding a required `onTrends` parameter breaks every existing call site — including test source sets. Find them all first:

Run: `rg -rln "HomeTileCallbacks\(" app/src`
Expected sites (verify, do not assume): `app/src/main/.../ui/home/HomeScreen.kt` (done in Step 3) and `app/src/androidTest/java/com/babytracker/ui/home/HomeReorderTest.kt`.

In `HomeReorderTest.kt`, add `onTrends = {},` to the `noOpCallbacks = HomeTileCallbacks(...)` literal. Apply the same to any other site the grep surfaces.

> Why this matters: CI runs `:app:connectedDebugAndroidTest` (see `.github/workflows/pr-checks.yml`), so the `androidTest` source set MUST compile. A missed call site fails CI even though unit tests pass.

- [ ] **Step 5: Update tile-order test expectations**

Find any test asserting `HomeTile.DEFAULT_ORDER` size/contents or tile count/order (e.g. `HomeTileTest`, reorder tests). Update expected counts/orders to include `TRENDS`.

Run: `./gradlew test --tests "com.babytracker.domain.model.HomeTileTest" --tests "com.babytracker.ui.home.*"`
Expected: PASS (after updating expectations).

- [ ] **Step 6: Verify the androidTest source set still compiles**

Run: `./gradlew compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL (catches any missed `HomeTileCallbacks` call site without needing an emulator).

---

### Task 4: TrendsScreen + charts

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/trends/TrendsScreen.kt`

- [ ] **Step 1: Write the screen**

Key structure (full implementation; verify exact Vico symbols against context7 `/patrykandpatrick/vico` if any import differs in 3.1.0):

```kotlin
package com.babytracker.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babytracker.domain.trends.DailyFeedingCount
import com.babytracker.domain.trends.DailyFeedingInterval
import com.babytracker.domain.trends.DailySleepDuration
import com.babytracker.domain.trends.TrendRange
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect

private val RANGE_LABELS = mapOf(
    TrendRange.SEVEN_DAYS to "7d",
    TrendRange.FOURTEEN_DAYS to "14d",
    TrendRange.THIRTY_DAYS to "30d",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Trends") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RangeSelector(
                selected = uiState.range,
                onSelected = viewModel::onRangeSelected,
            )
            FeedingFrequencyCard(uiState.feedingFrequency)
            SleepDurationCard(uiState.sleepDuration)
            FeedingIntervalCard(uiState.feedingInterval)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSelector(
    selected: TrendRange,
    onSelected: (TrendRange) -> Unit,
) {
    val ranges = TrendRange.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ranges.forEachIndexed { index, range ->
            SegmentedButton(
                selected = range == selected,
                onClick = { onSelected(range) },
                shape = SegmentedButtonDefaults.itemShape(index, ranges.size),
                modifier = Modifier.testTag("trends_range_${range.days}"),
            ) {
                Text(RANGE_LABELS.getValue(range))
            }
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    testTag: String,
    isEmpty: Boolean,
    contentDescription: String,
    chart: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (isEmpty) {
                Text(
                    text = "Not enough data yet — keep tracking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .testTag("${testTag}_empty"),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .testTag(testTag)
                        .semantics { this.contentDescription = contentDescription },
                ) {
                    chart()
                }
            }
        }
    }
}

@Composable
private fun FeedingFrequencyCard(data: List<DailyFeedingCount>) {
    val isEmpty = data.all { it.count == 0 }
    val color = MaterialTheme.colorScheme.primary
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        if (!isEmpty) {
            producer.runTransaction {
                columnSeries { series(data.indices.toList(), data.map { it.count }) }
            }
        }
    }
    ChartCard(
        title = "Feeds per day",
        testTag = "trends_feeding_chart",
        isEmpty = isEmpty,
        contentDescription = "Feeds per day. Total ${data.sumOf { it.count }} over ${data.size} days.",
    ) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(fill = fill(color), thickness = 12.dp),
                    ),
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(),
            ),
            modelProducer = producer,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
    }
}

@Composable
private fun SleepDurationCard(data: List<DailySleepDuration>) {
    val isEmpty = data.all { it.totalHours == 0.0 }
    val nightColor = MaterialTheme.colorScheme.secondary
    val napColor = MaterialTheme.colorScheme.tertiary
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        if (!isEmpty) {
            producer.runTransaction {
                columnSeries {
                    series(data.indices.toList(), data.map { it.nightHours })
                    series(data.indices.toList(), data.map { it.napHours })
                }
            }
        }
    }
    ChartCard(
        title = "Sleep hours per day (night + nap)",
        testTag = "trends_sleep_chart",
        isEmpty = isEmpty,
        contentDescription = "Sleep hours per day, night and nap stacked, over ${data.size} days.",
    ) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(fill = fill(nightColor), thickness = 12.dp),
                        rememberLineComponent(fill = fill(napColor), thickness = 12.dp),
                    ),
                    mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(),
            ),
            modelProducer = producer,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
    }
}

@Composable
private fun FeedingIntervalCard(data: List<DailyFeedingInterval>) {
    // Split into contiguous runs of non-null days so gap days BREAK the line rather than letting a
    // single series connect across them (which would imply a false trend through missing days).
    val runs = remember(data) { contiguousNonNullRuns(data) }
    val isEmpty = runs.isEmpty()
    val color = MaterialTheme.colorScheme.primary
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        if (!isEmpty) {
            producer.runTransaction {
                lineSeries { runs.forEach { run -> series(run.map { it.index }, run.map { it.value }) } }
            }
        }
    }
    // One styled line per run (same color); points make single-day runs visible.
    val line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(color)),
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(rememberShapeComponent(fill(color), CircleShape)),
        ),
    )
    ChartCard(
        title = "Average hours between feeds",
        testTag = "trends_interval_chart",
        isEmpty = isEmpty,
        contentDescription = "Average hours between feeds per day over ${data.size} days.",
    ) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    LineCartesianLayer.LineProvider.series(List(runs.size) { line }),
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(),
            ),
            modelProducer = producer,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
    }
}

/** Contiguous runs of days with a non-null average, each item carrying (dayIndex, hours). */
private fun contiguousNonNullRuns(
    data: List<DailyFeedingInterval>,
): List<List<IndexedValue<Double>>> = buildList {
    var current = mutableListOf<IndexedValue<Double>>()
    data.forEachIndexed { index, item ->
        val avg = item.averageHours
        if (avg != null) {
            current.add(IndexedValue(index, avg))
        } else if (current.isNotEmpty()) {
            add(current)
            current = mutableListOf()
        }
    }
    if (current.isNotEmpty()) add(current)
}
```

> **Vico import caveat:** Vico 3.x occasionally relocates symbols across patch releases. If any
> import above fails to resolve, query context7 `/patrykandpatrick/vico` for the exact 3.1.0
> package, or let the IDE/compiler auto-suggest. The public API names
> (`CartesianChartHost`, `rememberCartesianChart`, `rememberColumnCartesianLayer`,
> `rememberLineCartesianLayer`, `CartesianChartModelProducer`, `columnSeries`/`lineSeries`,
> `ColumnCartesianLayer.MergeMode.Stacked`) are stable — only package paths may shift.

- [ ] **Step 2: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Tasks 2-4 together resolve all references).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/navigation/ app/src/main/java/com/babytracker/domain/model/HomeTile.kt app/src/main/java/com/babytracker/ui/home/ app/src/main/java/com/babytracker/ui/trends/TrendsScreen.kt
git commit -m "feat(trends): add Trends screen with Vico charts, Home tile and navigation"
```

---

### Task 5: Compose UI test

**Files:**
- Create: `app/src/androidTest/java/com/babytracker/ui/trends/TrendsScreenTest.kt`

- [ ] **Step 1: Write the test (Robolectric; mirrors `GrowthScreenTest` setup)**

Render `TrendsScreen` with a fake/stubbed ViewModel (or hilt test rule, following the existing `GrowthScreenTest` approach in the repo). Assert:

```kotlin
// Pseudocode-level assertions — match the concrete pattern used by GrowthScreenTest:
// 1. Range selector shows all three options:
composeRule.onNodeWithTag("trends_range_7").assertIsDisplayed()
composeRule.onNodeWithTag("trends_range_14").assertIsDisplayed()
composeRule.onNodeWithTag("trends_range_30").assertIsDisplayed()

// 2. With empty data, the feeding empty placeholder shows:
composeRule.onNodeWithTag("trends_feeding_chart_empty").assertIsDisplayed()

// 3. Selecting a different range invokes onRangeSelected (verify via fake VM/state):
composeRule.onNodeWithTag("trends_range_30").performClick()
// assert the fake VM recorded THIRTY_DAYS
```

> Inspect `app/src/androidTest/java/com/babytracker/ui/growth/GrowthScreenTest.kt` first and copy
> its exact harness. Note: this repo's Compose UI tests live in `androidTest` and run **on a
> device/emulator** via `AndroidJUnitRunner` with the JUnit5 builder
> (`de.mannodermaus.junit5.AndroidJUnit5Builder`) — they are **not** Robolectric unit tests. Use
> JUnit5 (`@Test` from `org.junit.jupiter`) and `createComposeRule()`. Charts are asserted only via
> `testTag` presence / `contentDescription` — never pixels.

- [ ] **Step 2: Compile the androidTest source set (fast, no emulator)**

Run: `./gradlew compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the UI test (requires emulator/device — same task CI shards)**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.babytracker.ui.trends.TrendsScreenTest"`
Expected: PASS. (If no local emulator is available, rely on CI, which runs `:app:connectedDebugAndroidTest` sharded — but the Step 2 compile MUST pass locally first.)

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/babytracker/ui/trends/TrendsScreenTest.kt app/src/androidTest/java/com/babytracker/ui/home/HomeReorderTest.kt
git commit -m "test(trends): add TrendsScreen UI test"
```

---

## Acceptance Criteria

- App builds with Vico 3.1.0; `./gradlew build` succeeds.
- A "Trends" tile appears on the Home grid (reorderable, auto-appended for existing users) and opens `Routes.TRENDS`.
- `TrendsScreen` shows a 7/14/30-day segmented selector and three cards: feeds per day (columns), sleep hours night+nap (stacked columns), average hours between feeds (line).
- Charts use `MaterialTheme.colorScheme` colors (match the Baby palette + dark mode).
- Each chart with no data shows the empty placeholder instead of a chart; each chart `Box` has a `contentDescription`.
- Changing the range updates all three charts.
- Updated Home/HomeTile tests and the new `TrendsScreenTest` pass; ktlint + detekt clean.

## Self-Review Notes

- Spec coverage: all three charts + range selector + empty states + a11y + Vico M3 theming + Home-tile entry point + navigation — every UI item in the spec is covered.
- Type consistency: consumes `TrendsUiState` fields (`range`, `feedingFrequency`, `sleepDuration`, `feedingInterval`) and `onRangeSelected` exactly as defined in Plan 02; `TrendRange.days` used for selector test tags matches Plan 01.
- Known caveat documented: Vico import package paths may shift across 3.x patches — public API is stable; verify against context7 at implement time.
- Empty-state definition: feeding = all counts zero; sleep = all totalHours zero; interval = no non-null days.
- **Sparse interval data:** the interval line is split into contiguous non-null runs (`contiguousNonNullRuns`) so a `<2`-feed day produces a visible break, never a line drawn across the gap. When implementing, manually verify with a dataset like `[3.0, null, 4.0]` that the chart shows two segments/points, not one continuous line. If Vico 3.1.0's `LineProvider.series(List<Line>)` does not render one line-per-series as expected, fall back to plotting points-only (no connecting line) — honesty over aesthetics.
