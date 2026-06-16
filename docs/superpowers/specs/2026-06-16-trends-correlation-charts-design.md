# Trends Correlation Charts — Design

**Date:** 2026-06-16
**Status:** Approved design, ready for planning
**Builds on:** [Charts & Trends](./2026-06-14-charts-and-trends-design.md) (the existing Trends screen with 3 charts)

## Summary

Two **new charts** added to the existing **Trends** screen, both surfacing relationships
*across* data domains rather than a single metric over time:

1. **Feeds vs Sleep overlay** — daily feed count (columns) and total sleep hours (line) on a
   shared date axis with dual Y-axes. Answers "do bigger feeding days line up with more sleep?"
2. **24-hour rhythm strip** — one horizontal row per day across a midnight→midnight timeline,
   showing sleep blocks (bars) and feeds (dots). Reveals the daily routine forming, cluster
   feeds, and the growing longest-night stretch.

Both are **appended after** the existing 3 charts (Add alongside — existing charts unchanged).
All data is aggregated from existing repositories — **no new entities, no Room migration**.

## Decisions (confirmed)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Charts in scope | Feeds vs Sleep overlay + 24h rhythm strip | User picked these two from a candidate set of 7. |
| Placement | Append after existing 3 cards | "Add alongside" — keep current charts, longer scroll. |
| Overlay feed metric | **Feed count (all feeds)** | Universal across feeding styles. Intake-ml would read 0 on breastfed days (misleading). Duplicating the existing feeds/day series is acceptable — the sleep-line overlay is the new value. |
| Rhythm layout | **Horizontal day-rows** | Mobile-friendly, easy to scan the night-stretch growing down the column. Radial clock is cramped on phone and harder to compare days. |
| Rhythm renderer | Custom Compose `Canvas` (not Vico) | Vico has no per-day timeline/Gantt layer; the strip is a bespoke draw. |

## Architecture

Follows the established layering (domain use cases → ViewModel `StateFlow<UiState>` → Compose
screen). No Mapper classes, no Result wrappers, exceptions propagate (per CLAUDE.md).

### Domain models — `domain/trends/TrendModels.kt` (extend existing file)

Pure Kotlin, zero framework imports (`LocalDate` value type already used here).

```kotlin
/** Daily feed count paired with total sleep hours, for the overlay chart. */
data class DailyFeedVsSleep(
    val date: LocalDate,
    val feedCount: Int,
    val sleepHours: Double,
)

/** One day's 24h timeline: sleep blocks + feed marks, as fractions of the day in [0,1). */
data class DayRhythm(
    val date: LocalDate,
    val sleepBlocks: List<RhythmBlock>,
    val feedMarks: List<Float>,
)

/** A sleep interval clipped to a single day, expressed as fractions of that day. */
data class RhythmBlock(
    val startFraction: Float,
    val endFraction: Float,
    val isNight: Boolean,
)
```

### Use cases — `domain/usecase/trends/` (one per chart, single responsibility)

**`GetFeedVsSleepTrendUseCase`** — composes the two *existing* use cases rather than
re-querying repositories:

```kotlin
class GetFeedVsSleepTrendUseCase @Inject constructor(
    private val getFeedingFrequencyTrend: GetFeedingFrequencyTrendUseCase,
    private val getSleepDurationTrend: GetSleepDurationTrendUseCase,
) {
    suspend operator fun invoke(range: TrendRange): List<DailyFeedVsSleep>
}
```

Both inputs emit one entry per day in `trendWindowDates` order, so they are index-aligned;
zip by index into `DailyFeedVsSleep`. No new repository access, no duplicated day-bucketing.

**`GetDayRhythmTrendUseCase`** — reads completed sleep records + feeds (breastfeeding starts +
bottle timestamps) over the window, then per local day:

- **Clip each sleep interval to `[dayStart, dayEnd)`** of every day it overlaps. A 19:00→06:00
  sleep emits a tail block on day N (19:00→24:00) and a head block on day N+1 (00:00→06:00).
  Each clipped block's endpoints are converted to fractions of the 24h day (0.0 = midnight,
  1.0 = next midnight).
- Each block carries `isNight = (record.sleepType == NIGHT_SLEEP)`.
- **Feeds** → fraction-of-day marks on their bucketed day.
- Days with no data → empty `sleepBlocks` / `feedMarks`.
- Excludes in-progress sleep (null `endTime`) and future-dated entries — matches the existing
  use cases' rules. Uses `clock.zone` for bucketing (consistent with the existing use cases,
  which inject `java.time.Clock`).

### Repository methods

Reuse existing windowed reads already added by the Charts & Trends project
(`getCompletedSessionsBetween`, `getCompletedRecordsSince`, `BottleFeedRepository.getSince`).
The rhythm use case needs sleep records *with end times* (already provided by
`getCompletedRecordsSince`). **No new repository methods expected.**

### ViewModel — `ui/trends/TrendsViewModel.kt`

Add two fields and inject two use cases; same `flatMapLatest` flow.

```kotlin
data class TrendsUiState(
    val range: TrendRange = TrendRange.SEVEN_DAYS,
    val isLoading: Boolean = true,
    val feedingFrequency: List<DailyFeedingCount> = emptyList(),
    val sleepDuration: List<DailySleepDuration> = emptyList(),
    val feedingInterval: List<DailyFeedingInterval> = emptyList(),
    val feedVsSleep: List<DailyFeedVsSleep> = emptyList(),
    val dayRhythm: List<DayRhythm> = emptyList(),
)
```

### UI — `ui/trends/`

- **`FeedVsSleepCard`** (in `TrendsScreen.kt`) — Vico chart with two layers in one
  `rememberCartesianChart`:
  - Column layer = `feedCount` → **start axis**, title "Feeds".
  - Line layer = `sleepHours` → **end axis** (`verticalAxisPosition = Axis.Position.Vertical.End`),
    title "Sleep hrs".
  - Shared bottom date axis reusing `dateAxisFormatter` + `dayAxisItemPlacer`.
  - Empty when *all* `feedCount == 0` **and** *all* `sleepHours == 0`.
  - Reuse the existing `rememberChartLabel` / `rememberAxisTitle` so labels stay legible in dark
    system mode (the pinned-color fix already in `TrendsScreen.kt`).
- **`RhythmStripCard`** (in `TrendsScreen.kt`) + new file **`ui/trends/RhythmStrip.kt`** holding
  the bespoke draw (keeps `TrendsScreen.kt`, already ~380 lines, from bloating):
  - Header time ruler: `12a · 6a · 12p · 6p · 12a`.
  - One `Canvas` row per day: background track, sleep rects (night = `secondary`,
    nap = `secondaryContainer` — matching the existing sleep chart), feed dots = `primary`,
    leading `dd/MM` date label.
  - Legend: sleep / nap / feed.
  - Respects the 7/14/30 range selector (rows scroll within the existing
    `verticalScroll`).
  - Empty when every day has empty `sleepBlocks` **and** `feedMarks`.
  - **Accessibility:** `semantics { contentDescription = … }` summary, e.g. "Daily rhythm over
    N days. Longest night sleep stretch about H hours." (Canvas is opaque to screen readers,
    mirroring the existing charts.)

### DI

Both new use cases are constructor-injected; `GetFeedVsSleepTrendUseCase` depends on the two
existing use cases (also constructor-injected). No new Hilt module.

## Data flow

```
TrendsViewModel (onRangeSelected / init)
        │ invoke(range)
        ├─▶ GetFeedVsSleepTrendUseCase ─▶ GetFeedingFrequencyTrendUseCase + GetSleepDurationTrendUseCase
        │        (zip per-day by index)
        └─▶ GetDayRhythmTrendUseCase ─▶ SleepRepository + BreastfeedingRepository + BottleFeedRepository
                 (clip sleep to day windows → fractions; feeds → fraction marks)
        ▼
TrendsUiState ─▶ FeedVsSleepCard (Vico dual-axis) + RhythmStripCard (Compose Canvas)
```

## Aggregation rules

- **Day bucketing:** `Instant → LocalDate` in `clock.zone`, last `range.days` calendar days
  ending today (inclusive); every day represented even with zero data.
- **Overlay feed count:** same definition as the existing feeds/day chart (completed
  breastfeeding sessions by `startTime` + bottle feeds by `timestamp`).
- **Overlay sleep hours:** same as the existing sleep chart (`nightHours + napHours`,
  whole duration attributed to start day).
- **Rhythm sleep blocks:** unlike the overlay/existing sleep chart, the rhythm strip **splits a
  midnight-crossing sleep across day rows** (this is the chart's whole point — showing a real
  overnight block). Fraction = `secondsFromMidnight / 86400`, clamped to `[0,1]`.
- **Rhythm feed marks:** feed `Instant` → fraction of its bucketed local day.

## Error & empty handling

- Empty/sparse data → per-card empty-state placeholder ("Not enough data yet — keep tracking"),
  reusing the existing `ChartCard` empty pattern; never a crashed or blank chart.
- Exceptions propagate (no `Result<T>` wrapper).
- In-progress sleep and future-dated entries excluded (matches existing use cases).

## Testing

Per [the plan workflow](../../../CLAUDE.md): implement first, then tests; all green before PR.

- **`GetFeedVsSleepTrendUseCaseTest`** (JUnit5 + MockK + `runTest`): per-day index alignment of
  feed count vs sleep hours; empty window; a day with feeds but no sleep (and vice versa).
- **`GetDayRhythmTrendUseCaseTest`**: **midnight-split** (one overnight record → tail block on
  day N + head block on day N+1 with correct fractions); nap vs night `isNight` flag; in-progress
  sleep excluded; future-dated entry capped; feed-mark fractions; empty days; DST-boundary sanity.
- **ViewModel test** (Turbine): `onRangeSelected` recomputes the two new datasets; loading→loaded.
- **Compose UI test** (`createComposeRule`, Robolectric): both new cards render; empty placeholders
  show with no data; assert via `testTag` (`trends_feedvssleep_chart`, `trends_rhythm_chart`) and
  `contentDescription` (Canvas/Vico pixels not asserted).
- **Konsist:** naming conventions hold for the new use cases.

## Decomposition into PRs (for planning — one issue per PR)

1. **Domain + Feeds-vs-Sleep use case:** add `DailyFeedVsSleep` model +
   `GetFeedVsSleepTrendUseCase` (composing existing use cases) + unit tests.
2. **Rhythm use case:** add `DayRhythm` / `RhythmBlock` models + `GetDayRhythmTrendUseCase`
   (midnight-split clipping) + unit tests.
3. **UI:** wire both into `TrendsViewModel` / `TrendsUiState`; add `FeedVsSleepCard`
   (Vico dual-axis) and `RhythmStripCard` + `ui/trends/RhythmStrip.kt` Canvas; empty states;
   a11y; ViewModel + Compose tests.

## Non-goals

- Changing or removing the existing 3 charts.
- Intake-ml or supply-vs-demand charts (candidate set, not chosen this round).
- Growth / milestone correlation overlays (sparse-vs-dense; future round).
- Radial clock layout, per-feed-type coloring of rhythm dots, exporting charts as images.
- New persisted entities or Room migration.
