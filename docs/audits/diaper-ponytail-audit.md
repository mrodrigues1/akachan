# Diaper Section — Ponytail Over-Engineering Audit

> One-shot repo-wide audit of the **diaper** subsystem for over-engineering only.
> Scope: deletion / simplification / stdlib-or-native replacement. **Out of scope:** correctness bugs, security, performance.
> Every finding below was **validated** (callers grepped, impls counted, serialization paths traced). Findings that looked cuttable but turned out load-bearing are listed under "Rejected" so the negative is recorded, not silently skipped.
>
> Branch: `chore/diaper-ponytail-audit`. Nothing is applied — this is an execution checklist for another session.

---

## Files in scope (production)

```
domain/model/DiaperType.kt, DiaperChange.kt, TodayDiaperSummary.kt
domain/repository/DiaperRepository.kt
data/repository/DiaperRepositoryImpl.kt
data/local/dao/DiaperDao.kt
data/local/entity/DiaperEntity.kt
domain/usecase/diaper/{Log,Edit,Delete,ObserveDiaperChanges,ObserveTodayDiaperSummary}UseCase.kt
ui/diaper/{DiaperScreen,DiaperSheet,DiaperHistoryScreen,DiaperViewModel,DiaperHistoryViewModel}.kt
ui/component/DiaperTypeLabel.kt
ui/theme/DiaperPalette.kt
```
(export/sharing/widget diaper code mirrors other features and was checked only for cross-references, not audited as diaper-owned.)

---

## Ranked findings (biggest cut first)

### 1. `delete` — Dead `DiaperType.emoji` + `DiaperType.label` + the label-parse branches
**~12 lines main + ~10 lines test**

`DiaperType` carries two constructor properties that nothing live reads:

- **`emoji`** — never read anywhere. (`rg "\.emoji" main/java` hits only `WidgetContent`/`SleepScheduleScreen`, which are unrelated `emoji` props on other types.)
- **`label`** — read **only** inside `toDiaperTypeOrNull`'s `DiaperType.X.label` branches. Those branches are dead: the persisted/serialized form of a diaper type is **always `type.name`** (`DiaperEntity.toEntity` → `type.name`; `DiaperChange.toSnapshot` → `type.name`; `DiaperBackup` round-trips the entity's `name` string). The label form (`"Wet"`/`"Dirty"`/`"Both"`) is never written, so it can never be parsed back. The partner side never parses the type string at all (`PartnerDiaperTile` only counts).

> The doc comment in `DiaperTypeLabel.kt` claims the domain `label` "doubles as the persistence/serialization token." That is **factually wrong** — persistence uses `.name` (`"WET"`), not `.label` (`"Wet"`). Fix/remove that comment as part of this cut.

Localized labels for the UI already come from `DiaperType.labelRes()` (`DiaperTypeLabel.kt`); icons from `DiaperTypeIcon`. The enum properties are pure redundancy.

**Replacement** — collapse `DiaperType.kt` to a bare enum + a name-based safe parser:

```kotlin
package com.babytracker.domain.model

enum class DiaperType { WET, DIRTY, BOTH }

fun String.toDiaperTypeSafe(): DiaperType =
    DiaperType.entries.firstOrNull { it.name == this } ?: DiaperType.WET
```

This deletes `emoji`, `label`, and the entire `toDiaperTypeOrNull` helper (its only non-dead behavior was name-matching, now folded into `toDiaperTypeSafe`).

**Validated:**
- `toDiaperTypeOrNull` callers: only `toDiaperTypeSafe` + `DiaperTypeTest`. No production caller.
- `toDiaperTypeSafe` callers: only `DiaperEntity.toDomain`.
- `DiaperType.label` readers: only the dead branches above.
- `DiaperType.emoji` readers: none.

**Execution note:** Update `DiaperTypeTest.kt` — drop the `toDiaperTypeOrNull` label-parsing and null-return assertions; keep/retarget the name-parse + fallback cases onto `toDiaperTypeSafe` (e.g. `"WET".toDiaperTypeSafe() == WET`, `"nope".toDiaperTypeSafe() == WET`). Then `./gradlew test --tests "*DiaperTypeTest"`.

---

### 2. `delete` — Unused `DiaperRepository.observeLatest` (interface + impl + DAO)
**~5 lines main + DAO-test cases**

`observeLatest()` exists on `DiaperRepository`, `DiaperRepositoryImpl`, and `DiaperDao` with **zero callers**. The widget's "latest" trigger uses feed/sleep `observeLatest*`, not diaper (`WidgetSyncManager` lines 37–38). The home tile's "last change" comes from `ObserveTodayDiaperSummaryUseCase` via `observeAll().maxByOrNull`, not `observeLatest`.

Delete all three:
- `DiaperRepository.kt:9` — `fun observeLatest(): Flow<DiaperChange?>`
- `DiaperRepositoryImpl.kt:21-22` — override
- `DiaperDao.kt:15-16` — `observeLatest()` `@Query`

**Validated:** `rg "observeLatest\b" main/java` returns only the interface/impl/dao definitions for diaper — no consumer. Interface has exactly one implementation (`DiaperRepositoryImpl`), so removing from the interface is safe.

**Execution note:** Remove the matching case in `DiaperDaoTest.kt` (androidTest). `unused import Flow` may remain in the DAO — let ktlintFormat (pre-commit hook) drop it.

---

### 3. `delete` — Unused `DiaperRepository.getById` (interface + impl + DAO)
**~4 lines main + DAO-test cases**

`getById(id)` exists on `DiaperRepository`, `DiaperRepositoryImpl`, and `DiaperDao` with **zero callers**. Edit flows pass the full `DiaperChange` already in hand (`DiaperHistoryViewModel.loadForEdit` → `DiaperViewModel` → `EditDiaperChangeUseCase`), so nothing ever fetches a diaper by id. (Contrast vaccine/pumping, which legitimately `getById` before mutate — diaper does not.)

Delete all three:
- `DiaperRepository.kt:14` — `suspend fun getById(id: Long): DiaperChange?`
- `DiaperRepositoryImpl.kt:30` — override
- `DiaperDao.kt:29-30` — `getById` `@Query`

**Validated:** the only `getById` reference in `domain/ui/sharing/widget` for diaper is the interface declaration itself; `GeneratePdfReportUseCase` uses `diaperRepository.getBetween`, not `getById`.

**Execution note:** Remove the matching `DiaperDaoTest.kt` case (androidTest).

---

### 4. `yagni` (optional / borderline) — `ObserveDiaperChangesUseCase` is a pure pass-through
**~12 lines, but cuts against project convention — recommend KEEP**

`ObserveDiaperChangesUseCase` is `repository.observeAll()` with no added logic, injected into `DiaperHistoryViewModel` and `ObserveTodayDiaperSummaryUseCase`. A pure delegating use case is textbook YAGNI.

**However:** `CLAUDE.md` mandates single-responsibility use cases as the standard read path, and every other section (feeding, sleep, vaccine…) wraps its observe-all the same way. Removing only diaper's would make it the odd one out and force VMs to depend on the repository directly. **Recommendation: leave it** unless a project-wide "drop pass-through observe use cases" decision is made. Listed for completeness, not for execution.

---

## Rejected (looked cuttable, are load-bearing — do NOT touch)

- **`DiaperDao.getAllOnce`** — used by `BackupSourceImpl` (export) and `BackupImporterImpl` (dedup). Keep.
- **`getRecent` / `getBetween`** — `getRecent` feeds partner sync (`SyncToFirestoreUseCase`, `SnapshotSources`, `DebugPartnerSnapshotBuilder`); `getBetween` feeds the PDF report (`GeneratePdfReportUseCase`). Keep.
- **`now: () -> Instant` injection** (Log/Edit use cases, ViewModel) — the project's clock seam for deterministic tests, provided by `DatabaseModule.provideNowProvider`. Not over-engineering. Keep.
- **`toDiaperTypeSafe` defaulting to `WET`** — corruption guard for malformed stored strings (correctness, out of audit scope). Keep.
- **`DiaperPalette` + `diaperColors()`** — mirrors the established per-section palette pattern (Vaccine, etc.); extended non-M3 tokens must live outside `MaterialTheme`. Keep.
- **`DiaperTypeLabel.kt`** (one-function file) — clean, matches the `*Label` convention; only the inaccurate doc comment needs fixing (see Finding 1). Keep the file.
- **Shared `DiaperViewModel` for add + edit** — `editingId`-driven, reused by sheet and history. Reasonable, keep.

---

## Net impact

```
net: ~21 lines main (-emoji/-label/-toDiaperTypeOrNull ~12, -observeLatest ~5, -getById ~4),
     ~20 lines test, -0 deps.
```

No dependencies removable — the diaper section adds none of its own; it rides Room/Hilt/Compose already in the catalog.

## Suggested execution order & commits

1. `refactor(db): drop unused diaper getById + observeLatest` — Findings 2 & 3 (interface/impl/dao + DaoTest).
2. `refactor(diaper): collapse DiaperType to bare enum` — Finding 1 (DiaperType.kt + DiaperTypeTest + fix DiaperTypeLabel comment).

Run `./gradlew test` before each commit (pre-commit hook runs ktlintFormat + detekt). Finding 4: skip.
