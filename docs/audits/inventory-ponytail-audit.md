# Milk Inventory / Stash Section — Ponytail Over-Engineering Audit

> One-shot audit of the **milk inventory / stash** subsystem for over-engineering only.
> Scope: deletion / simplification / stdlib-or-native replacement. **Out of scope:** correctness bugs, security, performance.
> Every finding below was **validated** (callers grepped across `main` + tests, impls counted, DI/manifest checked). Items that looked cuttable but turned out load-bearing are listed under "Rejected" so the negative is recorded, not silently skipped.
>
> Branch: `chore/inventory-ponytail-audit`. Nothing is applied — this is an execution checklist for another session.

**Files in scope (production):**
`domain/model/{MilkBag,MilkBagWithExpiration,ExpirationStatus,InventorySummary}.kt`,
`domain/repository/{InventoryRepository,InventorySettingsRepository}.kt`,
`domain/usecase/inventory/*` (7 use cases),
`data/repository/{InventoryRepositoryImpl,InventorySettingsRepositoryImpl}.kt`,
`data/local/dao/MilkBagDao.kt`,
`ui/inventory/{InventoryScreen,AddBagSheet,InventoryViewModel,InventorySettingsScreen,InventorySettingsViewModel}.kt`,
`widget/MilkStash*` (8 files),
`manager/{StashExpirationScheduler,StashExpirationNotificationManager}.kt`,
`receiver/{StashExpirationReceiver,StashExpirationBootReceiver}.kt`,
`util/StashNotificationHelper.kt`,
`di/{InventorySettingsModule,WidgetModule}.kt`.

**Verdict up front:** this subsystem is lean. There is no dep bloat, no factory-for-one-product, no speculative config layer. The only real cuts are a handful of truly dead methods/fields and one dead widget size bucket — ~13 production lines.

---

## Ranked findings (biggest cut first)

### 1. `delete` — Dead `getAllBags()` (DESC) across all three layers
**~5 production lines + the MilkBagDao test cases that exercise it**

`getAllBags(): Flow<List<MilkBag>>` (the all-rows, `collection_date DESC` variant) has **zero callers anywhere** — not in production, not in unit tests.

- `domain/repository/InventoryRepository.kt:10` — interface method, delete
- `data/repository/InventoryRepositoryImpl.kt:24-25` — impl, delete
- `data/local/dao/MilkBagDao.kt:16-17` — `@Query(... ORDER BY collection_date DESC)`, delete

**Validated:** `rg "getAllBags\(\)"` over the whole repo hits only the three definitions above plus `app/src/androidTest/.../MilkBagDaoTest.kt` (4 blocks). The live "show me bags" path uses `getActiveBags()` (active-only, ASC); the receiver and backup use `getAllBagsOnce()` (suspend). Nothing consumes the Flow-of-all-bags. Active-bags filtering already lives in `getActiveBags()`/`getActiveSummary()`, so this is genuinely orphaned, not "the only place that includes used bags."

**Execution note:** delete the three definitions. The four `MilkBagDaoTest` blocks that called `bagDao.getAllBags()` assert real behavior (FK-set-null, update-editable-fields), so they were **migrated** to the kept suspend `getAllBagsOnce()` readback rather than deleted. `./gradlew test` + `./gradlew build` after (Room/DAO change).

> **Executed** on `chore/inventory-ponytail-audit`.

---

### 2. `delete` — Dead `getById()` on the repository layer
**~3 production lines + the `InventoryRepositoryImplTest` case**

`InventoryRepository.getById(id)` has **no production caller** — only `InventoryRepositoryImplTest` calls it. The dead repository wrapper goes:

- `domain/repository/InventoryRepository.kt:13` — interface method, delete
- `data/repository/InventoryRepositoryImpl.kt:38` — impl, delete

**Refinement (validated during execution):** the underlying `MilkBagDao.getById` query is **retained** — `BottleFeedDaoTest` legitimately uses it for clean per-id `usedAt`-swap assertions, so it is not dead. Only the never-called repository abstraction is removed; rewriting that test to `getAllBagsOnce().first { … }` would be strictly worse readability for no production gain.

**Validated:** `rg "repository.getById|inventory.getById"` over `app/src/main` returns nothing for inventory (the hits are vaccine/pumping/sleep). The partner-sharing snapshot path reads via `getActiveBags()` / `currentSummary()`, never `getById`. Edit/mark-used/delete all operate on the full `MilkBag` already in UI state, so no lookup-by-id is needed.

**Execution note:** delete the interface + impl method + the `getById` test in `InventoryRepositoryImplTest`. `./gradlew test` + `./gradlew build`.

> **Executed** on `chore/inventory-ponytail-audit` (DAO query kept — live test consumer).

---

### 3. `delete` — Dead `MilkBag.isActive` computed property
**~2 lines + its test**

`MilkBag.isActive: Boolean get() = usedAt == null` (`domain/model/MilkBag.kt:14`) is referenced **only** in `MilkBagTest`. Production code checks `usedAt == null` / `bag.usedAt != null` directly (e.g. `MarkBagUsedUseCase.kt:15`, `StashExpirationReceiver.kt:49 .filter { it.usedAt == null }`).

**Validated:** `rg "\.isActive"` hits only `MilkBagTest.kt` (2 asserts) for this type. Same pattern the sleep audit flagged for `BreastfeedInterval.isActive`.

**Execution note:** delete the property + the two `MilkBagTest` assertions. `./gradlew test`.

---

### 4. `delete` — Dead `WIDE_SIZE` widget responsive bucket
**~2 lines**

`MilkStashWidget.kt:44` declares `WIDE_SIZE = DpSize(200.dp, 57.dp)` and lists it in the `SizeMode.Responsive` set (line 17), but the `when` in `provideGlance` (lines 25-34) never references it. A 200×57 widget falls into the `size.width >= WIDE_SHORT_SIZE.width` branch → `MilkStashWideContent` — **identical** to what `WIDE_SHORT_SIZE` already produces. There is no `MilkStashWideLargeContent`, so the extra bucket buys nothing but an extra size Glance has to resolve.

- Remove `WIDE_SIZE` from the `setOf(...)` on line 17
- Delete the `val WIDE_SIZE` constant on line 44

**Validated:** `rg "WIDE_SIZE"` shows the only references are the declaration + the set entry (the `BabyWidget` hits are a different widget's `COMPACT_WIDE_SIZE`/`MEDIUM_SIZE`). No test asserts on `MilkStashWidget.WIDE_SIZE`.

**Execution note:** pure constant removal; `./gradlew test`. (Leave `SMALL/WIDE_SHORT/TALL/MEDIUM` — each maps to a distinct branch.)

---

## Net impact

```
net: -5 lines   dead getAllBags() chain (interface + impl + DAO query)   [#1]
     -3 lines   dead getById() repository wrapper (interface + impl)      [#2]
     -2 lines   dead MilkBag.isActive                                     [#3]
     -2 lines   dead WIDE_SIZE widget bucket                              [#4]
------------------------------------------------------------------------
~ -12 production lines, plus orphaned test cases removed/migrated for #1-#3.
0 dependencies removable (no dep bloat in this subsystem).
```

**Suggested execution order:** 3 → 4 (smallest, zero-risk, test-only fallout) → 2 → 1 (DAO/Room changes; run `./gradlew build` after each). Run `./gradlew test` after every step; the pre-commit hook handles ktlint/detekt.

---

## Rejected — looked cuttable, validated as load-bearing (do NOT touch)

- **`GetInventoryUseCase` / `GetInventorySummaryUseCase`** — thin one-line forwarders, but `GetInventoryUseCase` has **2** callers (`ObserveInventoryWithExpirationUseCase`, `BottleFeedViewModel`) and inlining either would force injecting `InventoryRepository` straight into a ViewModel, breaking the use-case-only access convention. CLAUDE.md explicitly allows thin use cases. Keep.
- **`MilkStashWidgetUpdater` interface (single impl `GlanceMilkStashWidgetUpdater`)** — used as a DI/test seam by `MilkStashWidgetSyncManager` *and* `MilkStashWidgetRefreshWorker`, both of which mock it in unit tests (`MilkStashWidgetSyncManagerTest`, `MilkStashWidgetRefreshWorkerTest`). Same allowance the sleep audit granted to the scheduler interfaces. Keep.
- **Reactive `MilkStashWidgetSyncManager` (debounced Flow) + periodic `MilkStashWidgetRefreshWorker` (15-min)** — looks redundant, but they cover different lifetimes: the sync flow only runs while the app process is alive (`ApplicationScope`), the worker is the cross-process / post-process-death backstop and the initial population path (`onEnabled`). Cutting the worker is a behavior/correctness change, out of audit scope. Keep both.
- **`currentSummary()` (suspend) vs `getSummary()` (Flow)** — `currentSummary` = `getSummary().first()`, but it has 2 callers (`MilkStashWidgetDataLoader`, `DebugPartnerSnapshotBuilder`) that need a one-shot value without dragging Flow plumbing into the widget/debug paths. Keep.
- **`MilkBagDao.getAllBagsOnce()` (suspend, ASC)** — distinct from the dead `getAllBags()` Flow; 3 production callers (`StashExpirationReceiver`, `BackupSourceImpl`, `BackupImporterImpl`). Keep.
- **`update(bag)` (full entity) vs `updateActiveDetails(...)` (partial, `used_at IS NULL` guarded, returns rows-affected)** — different semantics: `MarkBagUsedUseCase` needs the full-row update to set `usedAt`; `UpdateMilkBagUseCase` needs the guarded partial that refuses to edit an already-consumed bag (returns Boolean → `check(...)`). Not a merge candidate. Keep.
- **`sumVolumeForIds(ids)`** — single dedicated SQL aggregate, but it's a deliberate perf seam for `PartnerFeedNotificationManager` (sum in one query instead of N `getById`s). Documented, has a real caller. Keep.
- **`EditBagSheetState.saveToken`** — guards a stale-async-completion race in the edit flow (a newer edit must not be clobbered by an older in-flight save). Correctness mechanism, not over-engineering — out of scope. Keep.
- **`now: () -> Instant` injected into `AddMilkBagUseCase` / `UpdateMilkBagUseCase` / `MarkBagUsedUseCase`** — project-wide testability seam (deterministic clock in tests), not config-nobody-sets. Keep.
- **`InventorySettingsRepository` (separate from `SettingsRepository`)** — 1 impl, but it's an opt-in self-contained feature with its own DataStore keys; per-feature repository split is the project convention, and every method has a production caller (`InventorySettingsViewModel`, both receivers). Keep.
- **`di/InventorySettingsModule`** — 2 `@Binds`, could fold into `RepositoryModule`/`NotificationSchedulerModule`, but per-feature modules are the established convention (same call the sleep audit made for `SleepSettingsModule`). Keep.
- **`StashExpirationScheduler` interface (single impl)** — DI binding seam in `InventorySettingsModule`; also lets the receiver depend on the abstraction. Allowed. Keep.
- **Class-name-string `PendingIntent` targeting in `StashExpirationNotificationManager` (`setClassName(..., STASH_RECEIVER_CLASS)`)** — deliberate, to break the manager↔receiver compile cycle; documented inline. Not over-engineering. Keep.
- **`MilkStashWidgetData.EMPTY` vs `DISABLED` sentinels** — distinct render states (load-failure/empty vs feature-off neutral tile); both consumed in `MilkStashWidgetContent`. Keep.
- **5 widget size composables** (`Small`/`Tall`/`Wide`/`MediumFilled`/`MediumEmpty`) + the `formatVolume`/`volumeNumber`/`volumeUnitLabel` helpers — each renders a genuinely different layout/typography for its launcher cell; not duplication. Keep (only the *unused* `WIDE_SIZE` bucket in #4 goes).
- **`ObserveInventoryWithExpirationUseCase` date-as-Flow design** — the injected `dateFlow` is load-bearing: `combine` only re-runs on upstream emission, so without an external date push statuses would go stale past midnight. Documented. Keep.
