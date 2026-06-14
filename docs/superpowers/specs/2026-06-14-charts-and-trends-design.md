# Charts & Trends — Design

**Linear project:** [Charts & Trends](https://linear.app/akachan/project/charts-and-trends-3586d837ff11) (team AKA)
**Date:** 2026-06-14
**Status:** Approved design, ready for planning

## Summary

A new read-only **Trends** hub screen that visualizes three derived metrics over a
selectable date range (7 / 14 / 30 days):

1. **Feeding frequency** — feeds per day (column chart)
2. **Sleep duration** — night-sleep vs nap hours per day (stacked column chart)
3. **Average feeding interval** — mean hours between consecutive feeds per day (line chart)

Reached from a new reorderable **Home tile** that opens `Routes.TRENDS`. All data is
aggregated from existing Room DAOs — **no new entities, no Room migration**.

## Decisions (confirmed)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Charting library | **Vico 3.1.0** (`compose-m3` module) | Native Compose, reads `MaterialTheme.colorScheme` → inherits the Baby palette + dark mode automatically, so charts "match the app visual style" with no manual color plumbing. Actively maintained (release 2026-06-05). |
| v1 scope | All three charts | Matches the full project scope. |
| Entry point | New Home tile → dedicated `Routes.TRENDS` | Consistent with how Growth / Milestones / Sleep are reached. |

**Note on Growth charts:** the existing Growth feature renders its WHO-percentile chart with
hand-rolled Compose `Canvas`. Migrating Growth to Vico is **out of scope** for this project
(separate, already-shipped feature; its specialized percentile-curve overlay is not a Vico
fit). Possible future consistency follow-up, not tracked here.

## Charting library integration

Add to `gradle/libs.versions.toml`:

```toml
[versions]
vico = "3.1.0"

[libraries]
vico-compose-m3 = { group = "com.patrykandpatrick.vico", name = "compose-m3", version.ref = "vico" }
```

`compose-m3` transitively pulls the core `compose` module. Wire into `app/build.gradle.kts`
dependencies. Update the CLAUDE.md tech-stack table (Charting row) per the repo convention.

Core Vico API used: `CartesianChartHost`, `rememberCartesianChart`,
`rememberColumnCartesianLayer`, `rememberLineCartesianLayer`, and
`CartesianChartModelProducer` populated via `runTransaction { columnSeries { … } }` /
`lineSeries { … }`.

## Architecture

Follows the established layering (domain use cases → ViewModel `StateFlow<UiState>` → Compose
screen). No Mapper classes, no Result wrappers, exceptions propagate (per CLAUDE.md).

### Domain models — `domain/trends/`

Pure Kotlin, zero framework imports.

```kotlin
enum class TrendRange(val days: Int) { SEVEN_DAYS(7), FOURTEEN_DAYS(14), THIRTY_DAYS(30) }

data class DailyFeedingCount(val date: LocalDate, val count: Int)
data class DailySleepDuration(val date: LocalDate, val nightHours: Double, val napHours: Double)
data class DailyFeedingInterval(val date: LocalDate, val averageHours: Double?) // null when <2 feeds that day
```

> `LocalDate` is `java.time` (used elsewhere in the domain, e.g. Growth) — acceptable as it is a
> value type, not an Android framework type.

### Use cases — `domain/usecase/trends/` (one per chart, single responsibility)

- `GetFeedingFrequencyTrendUseCase(range): List<DailyFeedingCount>`
- `GetSleepDurationTrendUseCase(range): List<DailySleepDuration>`
- `GetFeedingIntervalTrendUseCase(range): List<DailyFeedingInterval>`

Each `suspend operator fun invoke(range: TrendRange)`. They read a windowed slice from the
relevant repositories and bucket by **local calendar day** (`ZoneId.systemDefault()`), emitting
one entry per day in the range (including zero-value days, so charts show gaps honestly).

### Repository methods

Reuse existing interfaces. The underlying DAOs already expose windowed reads
(`BreastfeedingDao.getCompletedSessionsBetween`, `SleepDao.getCompletedRecordsBetween`,
`BottleFeedDao.getSince`). Add thin repository methods that surface a "since timestamp"
windowed read where one is missing, returning **domain models** (not entities):

- `BreastfeedingRepository.getCompletedSessionsSince(start: Instant): List<BreastfeedingSession>`
- `BottleFeedRepository.getFeedsSince(start: Instant): List<BottleFeed>` (or reuse existing observe/getAllOnce + filter)
- `SleepRepository.getCompletedRecordsSince(start: Instant): List<SleepRecord>`

Prefer windowed queries over `getAllOnce` to avoid loading the full history.

### ViewModel — `ui/trends/TrendsViewModel.kt`

```kotlin
data class TrendsUiState(
    val range: TrendRange = TrendRange.SEVEN_DAYS,
    val isLoading: Boolean = true,
    val feedingFrequency: List<DailyFeedingCount> = emptyList(),
    val sleepDuration: List<DailySleepDuration> = emptyList(),
    val feedingInterval: List<DailyFeedingInterval> = emptyList(),
)
```

Exposes `StateFlow<TrendsUiState>`; `onRangeSelected(range)` recomputes all three datasets.
Loads on init for the default range. Empty per-chart datasets drive empty-state cards.

### UI — `ui/trends/`

- `TrendsScreen` — top app bar ("Trends"), a range selector (Material 3 `SingleChoiceSegmentedButtonRow`: 7d / 14d / 30d), and three titled `Card`s, each holding one Vico chart.
- One composable per chart (`FeedingFrequencyChart`, `SleepDurationChart`, `FeedingIntervalChart`), each translating its dataset into a `CartesianChartModelProducer`.
- **Empty state** per card: when a dataset has no non-zero data, show a friendly placeholder ("Not enough data yet — keep tracking") instead of an empty chart.
- **Accessibility:** each chart `Box` gets a `semantics { contentDescription = … }` summarizing the trend in words (mirrors the Growth chart's `chartContentDescription` approach), since Vico canvases are opaque to screen readers.

### Navigation & Home tile

- `Routes.TRENDS = "trends"`.
- `composable(Routes.TRENDS)` in `AppNavGraph` → `TrendsScreen`, with back nav.
- Add `TRENDS` to the `HomeTile` enum and to `DEFAULT_ORDER` (placed near GROWTH/MILESTONES). `HomeTile.reconcile` auto-appends it for existing users who already have a saved order.
- Add a `TrendsHomeCard` + an `onTrends` callback in the Home callbacks, wired in `HomeTileContent`.

### DI

Use cases are constructor-injected (no new Hilt module). Vico requires no DI.

## Data flow

```
Home "Trends" tile ──navigate──▶ TrendsScreen
        │
        ▼
TrendsViewModel (init + onRangeSelected)
        │  invoke(range)
        ▼
GetFeedingFrequencyTrendUseCase ─▶ BreastfeedingRepository + BottleFeedRepository
GetSleepDurationTrendUseCase    ─▶ SleepRepository
GetFeedingIntervalTrendUseCase  ─▶ BreastfeedingRepository + BottleFeedRepository
        │  bucket by local day
        ▼
TrendsUiState ─▶ CartesianChartModelProducer (per chart) ─▶ Vico CartesianChartHost
```

## Aggregation rules

- **Day bucketing:** `Instant → LocalDate` in `ZoneId.systemDefault()`. The range is the last
  `range.days` calendar days ending today (inclusive). Every day in the window is represented,
  even with zero data.
- **"Feed" definition:** a completed **breastfeeding session** (bucketed by `startTime`) **plus**
  a **bottle feed** (bucketed by `timestamp`). In-progress breastfeeding sessions (null
  `endTime`) are excluded.
- **Feeding frequency:** count of feeds per day.
- **Sleep duration:** for each completed sleep record, attribute its **whole duration** (hours)
  to its **start day**, split into `nightHours` (`SleepType.NIGHT_SLEEP`) and `napHours`
  (`SleepType.NAP`). Records crossing midnight are attributed to the start day (documented
  simplification; avoids per-day splitting in v1).
- **Average feeding interval:** order all feed timestamps in the window ascending; a gap is the
  hours between two consecutive feeds, attributed to the **later** feed's day; per day, average
  those gaps. Days with fewer than two feeds (no in-day gap) → `averageHours = null` → the line
  shows a break rather than a misleading zero.

## Error & empty handling

- Empty/sparse data → per-chart empty-state card; never a crashed or blank chart.
- Exceptions propagate (no `Result<T>` wrapper).
- Completed-only queries naturally exclude active/in-progress sessions.

## Testing

Per [the plan workflow](../../../CLAUDE.md): implement first, then tests; all green before PR.

- **Use-case unit tests** (JUnit5 + MockK + `runTest`), one class per use case:
  - correct day bucketing and zero-fill across the window
  - empty range
  - single feed in a day → null interval; multi-feed gap averaging
  - nap vs night split; midnight-crossing attribution
  - timezone/DST boundary sanity
- **ViewModel test** (Turbine): init load, `onRangeSelected` recomputes, loading→loaded, empty datasets.
- **Compose UI test** (`createComposeRule`, Robolectric): `TrendsScreen` renders three cards,
  range selector switches range, empty placeholder shows with no data. Assert via `testTag` /
  `contentDescription` (Vico pixels are not asserted).
- **Konsist:** naming conventions hold for the new ViewModel / UiState / use cases.

## Decomposition into PRs (for planning — one issue per PR)

1. **Foundation:** add Vico dependency + `domain/trends/` models (`TrendRange`, three `Daily*`
   data classes) + repository "since" methods. CLAUDE.md tech-stack table update.
2. **Feeding frequency use case** + unit tests.
3. **Sleep duration use case** + unit tests.
4. **Feeding interval use case** + unit tests.
5. **TrendsViewModel + TrendsUiState** + ViewModel tests.
6. **TrendsScreen UI** (three Vico charts, range selector, empty states, a11y) + Home tile +
   navigation wiring + Compose UI tests.

## Non-goals

- Predictive analytics (separate "Predictive Feeding Reminders" project).
- Exporting charts as images (v1).
- Growth curves (separate Growth Tracking project) and diaper-count chart (until Diaper ships).
- Migrating the existing Growth Canvas chart to Vico.
