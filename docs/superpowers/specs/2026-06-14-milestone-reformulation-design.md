# Milestone Reformulation — Free-form Moments — Design Spec

- **Date:** 2026-06-14
- **App:** BabyTracker (Akachan)
- **Status:** Approved for implementation
- **Supersedes:** the Milestone-tracker portion of `2026-06-13-milestone-growth-design.md`
  (Growth tracking is unaffected.)

## 1. Summary

Reformulate the Milestones feature from a fixed WHO gross-motor milestone catalog
into a free-form **moments journal**. The screen opens to a blank page; the parent
captures any moment of their baby with a **title** (required), **date** (required),
**time** (optional), **photo** (optional), and **note** (optional). Moments support
full create / read / update / delete, plus a dedicated detail view.

The section gets its own visual identity — a **purple / violet** accent — distinct
from feeding (pink), sleep (blue), tertiary (green), and warning (amber).

## 2. Goals / Non-goals

**Goals**
- Capture a free-form moment: title (required), date (required), time (optional),
  photo (optional), note (optional).
- List all moments, newest first, with a clear empty state.
- View a single moment's full details on its own screen (large photo, note).
- Edit and delete moments.
- A distinct purple section color, applied to the Milestones cards, screens, and Home tile.
- Keep moments flowing into the existing partner-sharing snapshot (read-only) and local backup.

**Non-goals (YAGNI for v1)**
- Categories / tags / milestone "types".
- Reminders or notifications.
- Multiple photos per moment (one optional photo).
- Syncing photos to Firestore (photos stay on-device, unchanged).
- Reordering moments manually (always sorted by date/time).

## 3. Data model

### Domain
Remove the `Milestone` enum, `MilestoneAchievement`, and `MilestoneProgress`. Replace
with a single pure-Kotlin data class (no framework imports):

```kotlin
data class Milestone(
    val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val time: LocalTime? = null,
    val photoUri: String? = null,
    val note: String? = null,
)
```

### Room
Replace `milestone_achievements` with a new `milestones` table:

```
MilestoneEntity  (table: milestones)
  id: Long (PK, autoGen)
  title: String
  date_epoch_day: Long          // LocalDate.toEpochDay()
  time_minute_of_day: Int?      // 0..1439, null = no time recorded
  photo_uri: String?
  note: String?
```

Index on `date_epoch_day` for ordered reads.

Entity ↔ domain conversion via extension functions (`toDomain()`, `toEntity()`) — no Mapper classes.

### Migration 11 → 12
Database version bumps 11 → 12.

```sql
DROP TABLE IF EXISTS milestone_achievements;
CREATE TABLE IF NOT EXISTS milestones (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    title TEXT NOT NULL,
    date_epoch_day INTEGER NOT NULL,
    time_minute_of_day INTEGER,
    photo_uri TEXT,
    note TEXT
);
CREATE INDEX IF NOT EXISTS index_milestones_date_epoch_day ON milestones(date_epoch_day);
```

Old WHO-milestone rows are intentionally discarded (per product decision: the feature
is being reformulated; a blank start is acceptable).

## 4. Repository

```kotlin
interface MilestoneRepository {
    fun getMilestones(): Flow<List<Milestone>>          // sorted date DESC, time DESC
    fun getMilestone(id: Long): Flow<Milestone?>
    suspend fun addMilestone(milestone: Milestone): Long
    suspend fun updateMilestone(milestone: Milestone)
    suspend fun deleteMilestone(id: Long)
}
```

`MilestoneRepositoryImpl` (@Singleton) delegates to `MilestoneDao`. Sorting is done in
the query (`ORDER BY date_epoch_day DESC, time_minute_of_day DESC`); rows with no time
sort after timed rows on the same day (NULLs last via `time_minute_of_day IS NULL` tiebreak).

`MilestoneDao`:
- `@Insert suspend fun insert(e): Long`
- `@Update suspend fun update(e)`
- `@Query("DELETE FROM milestones WHERE id = :id") suspend fun deleteById(id: Long)`
- `@Query(...) fun getAll(): Flow<List<MilestoneEntity>>`
- `@Query("... WHERE id = :id") fun getById(id: Long): Flow<MilestoneEntity?>`
- `@Query("SELECT * FROM milestones") suspend fun getAllOnce(): List<MilestoneEntity>` (backup)

## 5. Use cases (`domain/usecase/milestone/`)

Each single-responsibility, `suspend operator fun invoke(...)` (Get variants return Flow),
injecting `MilestoneRepository`:

- `AddMilestoneUseCase(milestone) : Long`
- `UpdateMilestoneUseCase(milestone)`
- `DeleteMilestoneUseCase(id: Long)`
- `GetMilestonesUseCase() : Flow<List<Milestone>>`
- `GetMilestoneUseCase(id: Long) : Flow<Milestone?>`

## 6. UI / navigation

### Routes
- `MILESTONES = "milestones"` (exists) — list screen.
- `MILESTONE_DETAIL = "milestones/{milestoneId}"` (new) — detail screen, `milestoneId: Long` arg.

### MilestonesScreen
- Blank/empty state when no moments: friendly copy + illustrative icon + prompt to tap **+**.
- `LazyColumn` of moment cards: photo thumbnail (or purple placeholder icon), title,
  formatted date (+ time if present). Card tap → navigate to detail.
- `FloatingActionButton` (purple) opens the editor sheet in "add" mode.
- `MilestonesViewModel`: exposes `MilestonesUiState(moments, isLoading)`; handlers
  `onAdd`, `onUpdate`, `onDelete`; resyncs partner snapshot after writes (existing pattern).

### MilestoneEditorSheet (replaces LogMilestoneSheet)
- `ModalBottomSheet` used for both add and edit.
- Fields: title `OutlinedTextField` (required); date via `DatePickerDialog`; time via
  `TimePicker` dialog with a "no time" / clear affordance (optional); photo picker
  (reuses `persistMilestonePhoto` / `rememberMilestoneBitmap` / `MilestonePhotoCleaner`);
  note `OutlinedTextField` (optional).
- Save disabled while title is blank or a photo is persisting.
- Photo cleanup: on replace/delete, the previous app-owned photo file is removed only
  after the new record commits (existing `MilestonePhotoCleaner` flow).

### MilestoneDetailScreen
- Loads one moment by id via `MilestoneDetailViewModel(SavedStateHandle)`.
- Large photo (if present), title, date/time, note.
- Top-bar actions: **edit** (opens `MilestoneEditorSheet` prefilled) and **delete**
  (confirmation dialog → delete → pop back to list).

### Home tile
`HomeTile.MILESTONES` already exists and is wired. Restyle `MilestonesHomeCard` with the
purple accent and show the moment count (e.g. "12 moments") or a "Capture a moment" prompt
when empty.

## 7. Section color (extended tokens)

All three M3 accent slots (primary/secondary/tertiary) are already taken. Following the
established **Amber/Warning** pattern, add a Purple family to `ui/theme/Color.kt` as
top-level `val`s, consumed by importing them by name (NOT via `MaterialTheme.colorScheme`):

```
Purple900 / Purple700 / Purple200 / Purple100      // raw scale
MilestonePurple              = Purple700            // primary action (light)
MilestoneContainerPurple     = Purple200            // container (light)
OnMilestoneContainerPurple   = Purple900            // on-container text (light)
MilestonePurpleDark          = <brighter violet>    // primary (dark)
MilestoneContainerPurpleDark = Purple900            // container (dark)
OnMilestoneContainerPurpleDark = Purple200          // on-container text (dark)
```

Exact hexes chosen at implementation with WCAG contrast ≥ 4.5:1 against their backgrounds
(white-on-`MilestonePurple`, `OnMilestoneContainerPurple`-on-`MilestoneContainerPurple`).
Candidate light primary ≈ `#6D4AA8`, container ≈ `#E6DCF5`, on-container ≈ `#2E1A52`.
A small composable helper resolves the light/dark pair from `isSystemInDarkTheme()`.

## 8. Partner sharing

`ShareSnapshot.milestones` stays (`= emptyList()` default preserves back-compat). Reshape
`MilestoneSnapshot`:

```kotlin
data class MilestoneSnapshot(
    val title: String,
    val dateEpochDay: Long,
    val timeMinuteOfDay: Int? = null,
    val note: String? = null,
)
```

- `Milestone.toSnapshot()` populates it (photo still dropped — never synced).
- `FirestoreSnapshotMapping`: update `milestoneToMap` / `mapToMilestone` for the new fields.
- Partner dashboard: render moments read-only (title, date/time, note).

## 9. Backup / export

Reshape `MilestoneBackup` to `{ title, dateEpochDay, timeMinuteOfDay?, note? }`.
- `BackupConverters`: `MilestoneEntity.toBackup()` / `MilestoneBackup.toEntity()` updated.
- `BackupSourceImpl`: unchanged call site (`getAllOnce().map { it.toBackup() }`).
- `BackupImporterImpl.mergeMilestones`: no unique enum key anymore — dedup by
  `(title, dateEpochDay, timeMinuteOfDay)` tuple so repeated restores don't duplicate.
- `ValidateBackupUseCase`: drop the `Milestone.valueOf` enum check and the duplicate-name
  check; validate `title` is non-blank and `dateEpochDay >= 0`.
- Bump `CURRENT_BACKUP_FORMAT_VERSION`. Milestone entries from older-format backups (which
  carried an enum `milestone` field, no `title`) are imported best-effort: defaulted/blank
  titles are skipped on import.

## 10. Testing

- Use cases + ViewModels: JUnit 5 + MockK + Turbine.
- `MilestoneRepositoryImpl`: sorting + entity/domain mapping.
- Room migration 11 → 12: migration test (old table dropped, new table present).
- Screens: Robolectric / Compose UI tests — empty state, add flow, list render, detail,
  edit, delete-with-confirmation.
- Backup round-trip: export → import dedup behavior; validate rejects blank title.

## 11. Out-of-scope clean-up touched by this change

Files updated because they reference the removed `Milestone` enum / `MilestoneAchievement`:
`MilestonesViewModel`, `MilestonesScreen`, `LogMilestoneSheet`, `MilestoneRepository(Impl)`,
`MilestoneDao`, `MilestoneAchievementEntity`, the three milestone use cases, `DomainToSnapshot`,
`ShareSnapshot`, `FirestoreSnapshotMapping`, `BackupConverters`, `BackupImporterImpl`,
`BackupData`, `BackupSource`, `ValidateBackupUseCase`, `PartnerDashboardScreen`, plus their tests.
