# Pumping Sessions & Milk Inventory — Design Spec

**Status:** Approved (design phase) — pending implementation plan
**Linear project:** [Pumping Sessions & Milk Inventory](https://linear.app/akachan/project/pumping-sessions-and-milk-inventory-9c8b359b3e75/overview)
**Date:** 2026-05-16

---

## 1. Purpose

Let parents log breast-pump sessions and manage a freezer/fridge inventory of stored milk bags. Mirrors existing breastfeeding/sleep feature patterns. All data is local except a small inventory summary that piggybacks on the existing partner-sharing channel.

## 2. Decisions (one-line summary)

| # | Decision | Choice |
|---|----------|--------|
| 1 | Logging UX | Live timer **and** manual entry (both) |
| 2 | Session → bag link | Auto-prompt bag creation on session stop |
| 3 | Units | mL only |
| 4 | Storage location (freezer/fridge) | Skip — single pool |
| 5 | Home placement | Card on summary grid; no bottom-bar button |
| 6 | Sharing | Partial — inventory **total** only (no per-session sync) |
| 7 | Bag removal | Soft delete via `usedAt` timestamp |
| 8 | Edit/delete sessions | Full pattern, mirrors `EditBreastfeedingSessionSheet` |
| 9 | Notifications | Skip for v1 |
| 10 | Pumping session shape | Single `breast: LEFT/RIGHT/BOTH` enum (no side-switch model) |
| 11 | Screen split | `PumpingScreen` + `PumpingHistoryScreen` + `InventoryScreen` (3 routes) |

## 3. Architecture

Three-layer clean architecture (existing project convention). No multi-module change. Two new top-level feature folders mirror `breastfeeding/` and `sleep/`.

### Package layout (added)

```
domain/
  model/
    PumpingSession.kt
    PumpingBreast.kt              # enum LEFT, RIGHT, BOTH
    MilkBag.kt
    InventorySummary.kt
  repository/
    PumpingRepository.kt          # interface
    InventoryRepository.kt        # interface
  usecase/
    pumping/
      StartPumpingSessionUseCase
      StopPumpingSessionUseCase
      PausePumpingSessionUseCase
      ResumePumpingSessionUseCase
      SavePumpingSessionUseCase   # manual entry (one-shot full session)
      UpdatePumpingSessionUseCase
      DeletePumpingSessionUseCase
      GetPumpingHistoryUseCase
      ValidatePumpingEdit
    inventory/
      AddMilkBagUseCase           # accepts optional sourceSessionId
      MarkBagUsedUseCase          # soft delete (sets usedAt)
      DeleteMilkBagUseCase        # hard delete (mistakes)
      GetInventoryUseCase         # Flow<List<MilkBag>> active bags, oldest first
      GetInventorySummaryUseCase  # Flow<InventorySummary>

data/
  local/
    entity/
      PumpingEntity.kt
      MilkBagEntity.kt
    dao/
      PumpingDao.kt
      MilkBagDao.kt
  repository/
    PumpingRepositoryImpl.kt
    InventoryRepositoryImpl.kt

ui/
  pumping/
    PumpingScreen.kt              # timer + manual entry modes
    PumpingHistoryScreen.kt
    PumpingViewModel.kt
    PumpingHistoryViewModel.kt
    EditPumpingSessionSheet.kt
    AddBagPromptSheet.kt          # invoked on session stop
  inventory/
    InventoryScreen.kt
    InventoryViewModel.kt
    AddBagSheet.kt                # manual bag add (no session link)
```

### Routes (`navigation/Routes.kt`)

```kotlin
const val PUMPING = "pumping"
const val PUMPING_HISTORY = "pumping/history"
const val INVENTORY = "inventory"
```

## 4. Data model

### Domain

```kotlin
data class PumpingSession(
    val id: Long = 0,
    val startTime: Instant,
    val endTime: Instant? = null,
    val breast: PumpingBreast,
    val volumeMl: Int? = null,
    val notes: String? = null,
    val pausedAt: Instant? = null,
    val pausedDurationMs: Long = 0
) {
    val isInProgress: Boolean get() = endTime == null
    val isPaused: Boolean get() = pausedAt != null
    val duration: Duration? get() = endTime?.let { Duration.between(startTime, it) }
    fun activeDurationUntil(until: Instant): Duration =
        Duration.between(startTime, endTime ?: until)
            .minus(Duration.ofMillis(pausedDurationMs))
            .coerceAtLeast(Duration.ZERO)
}

enum class PumpingBreast { LEFT, RIGHT, BOTH }

data class MilkBag(
    val id: Long = 0,
    val collectionDate: Instant,
    val volumeMl: Int,
    val sourceSessionId: Long? = null,
    val usedAt: Instant? = null,
    val notes: String? = null,
    val createdAt: Instant
) {
    val isActive: Boolean get() = usedAt == null
}

data class InventorySummary(
    val totalMl: Int,
    val bagCount: Int,
    val oldestBagDate: Instant?
)
```

### Room schema (DB version 2 → 3)

#### `pumping_sessions`

| Column | Type | Notes |
|--------|------|-------|
| `id` | INTEGER PK autoGenerate | |
| `start_time` | INTEGER NOT NULL | epoch ms |
| `end_time` | INTEGER NULLABLE | null = in progress |
| `breast` | TEXT NOT NULL | "LEFT" / "RIGHT" / "BOTH" |
| `volume_ml` | INTEGER NULLABLE | filled at stop / manual entry |
| `notes` | TEXT NULLABLE | |
| `paused_at` | INTEGER NULLABLE | epoch ms; null = running |
| `paused_duration_ms` | INTEGER NOT NULL DEFAULT 0 | |

Index: `pumping_sessions(start_time DESC)` for history queries.

#### `milk_bags`

| Column | Type | Notes |
|--------|------|-------|
| `id` | INTEGER PK autoGenerate | |
| `collection_date` | INTEGER NOT NULL | epoch ms |
| `volume_ml` | INTEGER NOT NULL | > 0 |
| `source_session_id` | INTEGER NULLABLE | FK → `pumping_sessions.id` ON DELETE SET NULL |
| `used_at` | INTEGER NULLABLE | null = active, set = soft-deleted |
| `notes` | TEXT NULLABLE | |
| `created_at` | INTEGER NOT NULL | epoch ms |

Indices: `milk_bags(used_at)` (active-bag queries), `milk_bags(collection_date)` (oldest-bag query), `milk_bags(source_session_id)` (FK lookup).

#### Migration `MIGRATION_2_3`

`BabyTrackerDatabase.kt` adds `MIGRATION_2_3` that runs `CREATE TABLE pumping_sessions ...`, `CREATE TABLE milk_bags ...`, then creates the indices above. Foreign key declared on `milk_bags.source_session_id` with `ON DELETE SET NULL`.

### Entity ↔ domain conversion

Extension functions on entity/domain types (no `Mapper` classes), e.g.:

```kotlin
fun PumpingEntity.toDomain(): PumpingSession = ...
fun PumpingSession.toEntity(): PumpingEntity = ...
fun MilkBagEntity.toDomain(): MilkBag = ...
fun MilkBag.toEntity(): MilkBagEntity = ...
```

## 5. UI / UX flow

### Home screen integration

Summary card area becomes a 2×2 grid. Existing Feeding/Sleep on row 1; new Pumping/Inventory on row 2.

- **Pumping card** — `tertiaryContainer` (green), icon 🥛 / `Icons.Outlined.WaterDrop`. Body shows live elapsed clock if active, else "Last X ago" or "Tap to log". Tap → `PUMPING` route.
- **Inventory card** — `surfaceVariant` (neutral), icon 🧊. Body `"${totalMl} mL · ${bagCount} bags"` or "No bags stored". Tap → `INVENTORY` route.

Bottom action bar stays Feeding / Sleep only — no third button.

### `PumpingScreen`

Segmented control toggles between two modes:

- **Timer mode** (default) — big elapsed clock, breast selector pill row (LEFT/RIGHT/BOTH), Start/Pause/Resume/Stop FAB. On Stop → `AddBagPromptSheet`.
- **Manual entry mode** — form: start time picker, end time picker, breast, volume mL, notes. Save → `SavePumpingSessionUseCase` and prompts `AddBagPromptSheet`.

TopAppBar action: history icon → `PUMPING_HISTORY`.

### `AddBagPromptSheet`

Bottom sheet shown after a session stops (timer or manual). Pre-fills:

- `collectionDate` = session end time (editable)
- `volumeMl` = session volume (editable, required, > 0)
- `notes` = blank

Buttons: **Add to Stash** → `AddMilkBagUseCase` with `sourceSessionId`. **Skip** → no bag created.

### `PumpingHistoryScreen`

`LazyColumn` of `HistoryCard` rows (reuses `ui/component/HistoryCard.kt`). Row content: date/time, breast pill, volume, duration. Tap row → `EditPumpingSessionSheet`. Swipe-to-delete or long-press → confirm delete.

### `EditPumpingSessionSheet`

Modal bottom sheet mirroring `EditBreastfeedingSessionSheet`. Fields prefilled. `ValidatePumpingEdit` enforces: end > start, volume > 0 if set, paused duration ≤ active span.

### `InventoryScreen`

- Header card — `InventorySummary`: total mL, bag count, oldest bag date.
- "Add bag" FAB → `AddBagSheet` (manual entry, no session link).
- `LazyColumn` of active bags, sorted oldest first (FIFO consumption order). Row: date, volume, age ("3d ago"). Trailing IconButton **Mark used** → `MarkBagUsedUseCase`. Overflow menu: **Delete** (hard).

### `AddBagSheet`

Bottom sheet for manual bag entry: collection date picker (defaults to now), volume mL (required), notes. Save → `AddMilkBagUseCase` with `sourceSessionId = null`.

## 6. Sharing integration

Pumping sessions and bag rows stay **local-only**. Only an inventory summary syncs.

`sharing/domain/model/ShareSnapshot.kt` adds:

```kotlin
val inventoryTotalMl: Int? = null,
val inventoryBagCount: Int? = null,
val inventoryUpdatedAt: Long? = null   // epoch ms
```

`DomainToSnapshot.kt` adds a helper that pulls `InventorySummary` into those fields.

`SyncToFirestoreUseCase` gains an `InventoryRepository` injection. Sync runs after **every** inventory mutation that can change `InventorySummary`:

- `AddMilkBagUseCase` — bag added
- `MarkBagUsedUseCase` — bag soft-deleted
- `DeleteMilkBagUseCase` — bag hard-deleted (must not leave partner snapshot stale)

Every trigger writes `inventoryTotalMl`, `inventoryBagCount`, and `inventoryUpdatedAt`. Tests assert each mutation path updates all three Firestore fields.

`PartnerDashboardScreen` adds a read-only inventory card: `"${totalMl} mL · ${bagCount} bags stored"`.

No new Firestore sub-collections. Firestore document path stays `shares/{shareCode}` with 3 added top-level fields.

## 7. DI wiring

- `DatabaseModule` — provide `PumpingDao` and `MilkBagDao`; register `MIGRATION_2_3`.
- `RepositoryModule` — `@Binds` `PumpingRepository` → `PumpingRepositoryImpl`, `InventoryRepository` → `InventoryRepositoryImpl`; both `@Singleton`.

No new Hilt module — feature fits in existing modules.

## 8. Error handling

- Use cases throw on invalid input (`IllegalArgumentException`). No `Result<>` wrapper. End-before-start, negative or zero volume, etc.
- ViewModels call use cases inside `viewModelScope.launch` and apply `Flow.catchAndLog()` on read flows.
- UI shows inline validation errors in sheets (e.g. "Volume must be greater than 0").
- `ValidatePumpingEdit` mirrors `ValidateBreastfeedingEdit` (returns a sealed validation result consumed by the sheet).
- DB migration failure follows the same policy as `MIGRATION_1_2`: no destructive fallback, let Room throw and surface as install failure.

## 9. Testing

### Unit tests (`src/test/`)

- `StartPumpingSessionUseCaseTest`, `StopPumpingSessionUseCaseTest`, `PausePumpingSessionUseCaseTest`, `ResumePumpingSessionUseCaseTest`
- `SavePumpingSessionUseCaseTest` (manual entry path)
- `UpdatePumpingSessionUseCaseTest`, `DeletePumpingSessionUseCaseTest`
- `ValidatePumpingEditTest`
- `AddMilkBagUseCaseTest` — verifies `sourceSessionId` FK link, `createdAt` set, triggers inventory sync
- `MarkBagUsedUseCaseTest` — `usedAt` set, row not deleted, triggers inventory sync
- `DeleteMilkBagUseCaseTest` — row removed, triggers inventory sync (partner snapshot must not go stale)
- `InventorySyncTriggerTest` — for each of Add/MarkUsed/Delete, assert `SyncToFirestoreUseCase` invoked with refreshed `inventoryTotalMl`, `inventoryBagCount`, `inventoryUpdatedAt`
- `GetInventoryUseCaseTest` — excludes used bags, ordered oldest first
- `GetInventorySummaryUseCaseTest` — total math, oldest bag, excludes used bags
- `GetPumpingHistoryUseCaseTest`
- `PumpingViewModelTest`, `PumpingHistoryViewModelTest`, `InventoryViewModelTest` — Turbine flow asserts
- `DomainToSnapshotTest` — inventory fields populated when summary present

### Instrumentation tests (`src/androidTest/`)

**DAO + migration:**

- `PumpingDaoTest` — insert/update/delete/query w/ in-memory Room
- `MilkBagDaoTest` — active-vs-used filtering, FK `ON DELETE SET NULL` when source session deleted
- `MigrationTest` extended w/ `MIGRATION_2_3` — verify schema after migrating from v2

**Compose UI (`createComposeRule()`):**

- `PumpingScreenTest` — Timer mode: tap Start → elapsed clock renders, breast selector toggles, Pause/Resume swap labels, Stop opens `AddBagPromptSheet`. Manual mode: enter values → Save invokes VM event. Validation: empty volume blocks Save with visible error.
- `AddBagPromptSheetTest` — Add invokes use case with `sourceSessionId`; Skip closes sheet without creating bag.
- `PumpingHistoryScreenTest` — list renders rows, tap row opens edit sheet, swipe-to-delete confirms.
- `EditPumpingSessionSheetTest` — fields prefilled, save propagates, validation errors render.
- `InventoryScreenTest` — summary header math (total/count/oldest), FAB opens `AddBagSheet`, Mark-used button removes row from active list, empty state copy.
- `AddBagSheetTest` — manual bag add path, validation.
- `HomeScreenTest` — extended assertions for new Pumping + Inventory cards, tap → nav callback fired.

UI tests use fake ViewModels (no Hilt in test); existing `*ScreenTest.kt` pattern in `ui/`.

### Quality gates

Before commit: `./gradlew ktlintFormat && ./gradlew detekt && ./gradlew test`. Pre-commit hook handles auto-fix.

## 10. Non-goals (explicit)

- Expiry alerts and oldest-bag warnings
- Barcode scanning for bags
- Notifications (ongoing pumping notification, max-time alarms, action receivers)
- Pumping schedule generator (parallel to `GenerateSleepScheduleUseCase`)
- Oz unit conversion / unit preference
- Storage location (freezer/fridge enum)
- Pumping session sync to partner (only total inventory mL syncs)
- Multi-bag-from-one-session UI (one prompt → one bag; user can add more manually)

## 11. Open follow-ups (post-v1)

- Add expiry alerts driven by `collectionDate + freezer_shelf_life` (config setting).
- Storage-location enum if real users ask for it.
- Pumping session sync to partner — likely most-requested gap.
- Pumping schedule prediction.
