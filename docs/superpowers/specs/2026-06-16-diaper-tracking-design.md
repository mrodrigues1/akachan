# Diaper Tracking — Design

- **Linear project:** [Diaper Tracking](https://linear.app/akachan/project/diaper-tracking-4047a6659cd7)
- **Status:** Design approved → ready for issue breakdown
- **Date:** 2026-06-16

## Overview

Log diaper changes with a timestamp and a type (wet, dirty, or both). This is one of the
most frequent questions from nurses and pediatricians in a newborn's first weeks, so the
logging flow must be fast and one-handed. A diaper change is a **point-in-time event** — it
has no duration — which makes it the simplest tracked event in the app. The feature mirrors
the existing `BottleFeed` feature end-to-end (bottom-sheet quick-log, Log/Edit/Delete/Observe
use cases, a home tile, a daily-grouped history screen, and a partner-shared snapshot).

## Scope

- Diaper log entry: timestamp, type (wet / dirty / both), optional notes.
- Bottom-sheet quick-log from a Home tile (one tap to open, one tap per type).
- Home tile summary: last diaper change time + count today.
- Diaper history screen with daily grouping, edit, and delete.
- Partner sharing: diaper data flows into the read-only shared snapshot and renders on the
  partner dashboard.

## Non-goals

- Color / consistency tracking (too clinical for v1) — from the Linear project.
- Diaper brand tracking — from the Linear project.
- Quick Settings tile / Glance widget for diaper logging.
- Diaper-change reminders / notifications.
- Trends-screen integration (diaper frequency charts) — deferred to a later project.

## Decisions (from brainstorming)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Logging UX | Bottom-sheet quick-log (like `BottleFeedSheet`) | Fastest one-handed flow for the highest-frequency event. |
| Partner sync | Included in v1 | Diaper count is exactly what a partner wants visible; reuses the existing snapshot pipeline. |
| History edit/delete | Supported | Consistent with Sleep/Bottle history; parents mis-tap the type often. |

## Domain Model

Pure Kotlin, zero framework imports (DDD principle from `SPEC-001`).

```kotlin
// domain/model/DiaperType.kt
enum class DiaperType(val label: String, val emoji: String) {
    WET("Wet", "💧"),
    DIRTY("Dirty", "💩"),
    BOTH("Both", "🌀"),
}
fun String.toDiaperTypeOrNull(): DiaperType?   // name- and label-tolerant, mirrors toSleepTypeOrNull
fun String.toDiaperTypeSafe(): DiaperType      // defaults to WET

// domain/model/DiaperChange.kt
data class DiaperChange(
    val id: Long = 0,
    val timestamp: Instant,
    val type: DiaperType,
    val notes: String? = null,
    val createdAt: Instant,
)

// domain/model/TodayDiaperSummary.kt
data class TodayDiaperSummary(
    val count: Int = 0,
    val lastChangeAt: Instant? = null,
) {
    val hasAny: Boolean get() = count > 0
}
```

## Architecture

Follows the project's three-layer clean architecture (UI → Domain → Data) with unidirectional
flow. New artifacts by layer:

### Data layer
- `data/local/entity/DiaperEntity.kt` — `@Entity(tableName = "diaper_changes")`; columns
  `id`, `timestamp`, `type`, `notes`, `created_at`. Index on `timestamp`. `toDomain()` /
  `toEntity()` extension functions (no Mapper class).
- `data/local/dao/DiaperDao.kt` — `observeAll(): Flow<List<DiaperEntity>>`,
  `observeLatest(): Flow<DiaperEntity?>`, `getBetween(start, end)`, `insert`, `update`,
  `deleteById`.
- `data/repository/DiaperRepositoryImpl.kt` — implements `DiaperRepository`, `@Singleton`.
- `BabyTrackerDatabase` — add `DiaperEntity` to `entities`, bump `version = 12 → 13`, add
  `diaperDao()`, add `MIGRATION_12_13` (CREATE TABLE + CREATE INDEX on `timestamp`). No
  active-session trigger (instantaneous event, unlike sleep/breastfeeding).
- `di/DatabaseModule.kt` — provide `DiaperDao`. `di/RepositoryModule.kt` — `@Binds` the
  repository interface to the impl.

### Domain layer
- `domain/repository/DiaperRepository.kt` — Flow-returning interface.
- `domain/usecase/diaper/` — `LogDiaperChangeUseCase`, `EditDiaperChangeUseCase`,
  `DeleteDiaperChangeUseCase`, `ObserveDiaperChangesUseCase`, `ObserveTodayDiaperSummaryUseCase`
  (combines the changes flow with a `now`/`ZoneId` filter, mirroring
  `ObserveTodayFeedingSummaryUseCase`).

### UI layer
- `domain/model/HomeTile.kt` — add `DIAPER` to the enum and to `DEFAULT_ORDER`. `reconcile()`
  auto-appends it for existing users, so no DataStore migration is needed.
- `ui/diaper/DiaperViewModel.kt` + `DiaperUiState` — exposes today summary + latest change,
  handles log/edit/delete events.
- `ui/diaper/DiaperSheet.kt` — bottom sheet: three large type buttons (Wet/Dirty/Both),
  editable timestamp defaulting to "now", optional notes field. Reused for edit.
- `ui/home/HomeTileContent.kt` — `DiaperHomeCard` rendering last-change "X ago" + today count;
  add `onDiaper` to `HomeTileCallbacks` and wire through `HomeScreen` / `HomeViewModel`.
- `ui/diaper/DiaperHistoryScreen.kt` — list grouped by day (most recent first), tap row to
  edit via the sheet, delete with undo. `navigation/Routes.kt` adds `DIAPER_HISTORY`;
  `AppNavGraph.kt` adds the composable destination.

### Sharing layer
- `sharing/domain/model/ShareSnapshot.kt` — add `diapers: List<DiaperSnapshot> = emptyList()`
  and the `DiaperSnapshot` data class (`timestamp`, `type`, `notes`).
- `sharing/usecase/SnapshotSources.kt` — inject `DiaperRepository`.
- `sharing/domain/model/DomainToSnapshot.kt` — map domain → snapshot.
- `sharing/data/firebase/FirestoreSnapshotMapping.kt` — read/write the `diapers` array.
- `ui/partner/PartnerDashboardScreen.kt` + `PartnerDashboardViewModel.kt` — `PartnerDiaperCard`
  showing last change + today count from the fetched snapshot.

## Data Flow

```
DiaperSheet ──onLog──▶ DiaperViewModel ──▶ LogDiaperChangeUseCase ──▶ DiaperRepository ──▶ DiaperDao
HomeScreen ◀── DiaperUiState ◀── ObserveTodayDiaperSummaryUseCase ◀── observeAll() Flow ◀── Room
SyncToFirestoreUseCase ──▶ SnapshotSources.diaper ──▶ DomainToSnapshot ──▶ Firestore ──▶ Partner dashboard
```

## Error Handling

- Use cases let exceptions propagate (no `Result<>` wrapper, per project anti-goals).
- Delete is undoable in history (snackbar undo re-inserts the captured `DiaperChange`).
- Editing a non-existent row (deleted on another device) is a no-op update; the observed Flow
  reconciles the UI.

## Testing

- **Unit (`src/test/`):** JUnit 5 + MockK + Turbine. Cover entity↔domain mapping, each use case,
  `ObserveTodayDiaperSummaryUseCase` day-boundary logic, and the `toDiaperTypeOrNull/Safe`
  parsers.
- **DAO:** Robolectric + Room in-memory; insert/update/delete/observe + the migration.
- **Migration:** `MigrationTestHelper` 12→13 round-trip.
- **Compose UI (`src/androidTest/`):** `DiaperSheet` logs each type; `DiaperHistoryScreen`
  groups by day and supports edit/delete; the home tile shows the summary.

## Proposed Issue Breakdown (one per PR)

1. **Data layer** — `DiaperType`, `DiaperChange`, `DiaperEntity` + mapping, `DiaperDao`,
   `DiaperRepository`(+Impl), DI bindings, DB v13 + `MIGRATION_12_13`. Unit + DAO + migration tests.
2. **Domain use cases** — Log/Edit/Delete/Observe + `ObserveTodayDiaperSummaryUseCase` +
   `TodayDiaperSummary`. Unit tests.
3. **Home tile + quick-log sheet** — `HomeTile.DIAPER`, `DiaperHomeCard`, `DiaperSheet`,
   `DiaperViewModel`/`UiState`, Home wiring. Tests + Compose UI test.
4. **History screen** — `DiaperHistoryScreen` (daily grouping, edit, delete-with-undo), route +
   nav graph. Tests + UI test.
5. **Partner sync** — `DiaperSnapshot` in `ShareSnapshot`, `SnapshotSources`, `DomainToSnapshot`,
   `FirestoreSnapshotMapping`, `PartnerDiaperCard`. Tests.

Each issue builds on the previous and is independently shippable. Final granularity is set when
these are converted to Linear issues with implementation plans.

## Risks / Notes

- DB version bump touches `BabyTrackerDatabase` (shared file); keep the migration additive.
- Partner snapshot schema is read by older partner app versions — the new `diapers` field is
  optional/defaulted, so older readers ignore it safely (same pattern as `bottleFeeds`,
  `growth`, `milestones`).
- `HomeTile` ordering is auto-reconciled; verify the new tile lands in a sensible default slot.
