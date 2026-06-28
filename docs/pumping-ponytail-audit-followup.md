# Pumping Section — Ponytail Over-Engineering Audit (follow-up)

> **STATUS: COMPLETE ✅** — all 3 findings executed and merged (#360 finding 1, #361 findings 2-3),
> and the out-of-scope i18n gap was extracted and merged (#362, tracked on AKA-185). The pumping
> section is now lean against this audit. Detail preserved below as a record.

Fresh whole-section pass. Scope: **over-engineering / complexity only**. Correctness bugs,
security, performance, and i18n gaps are out of scope (noted but routed elsewhere). This was a
report; every finding has since been applied (see per-finding status).

Context: the prior `docs/pumping-ponytail-audit.md` found exactly one safe cut
(`PumpingBreast.displayName()`), which has **already been executed** (commit `f043e2180`). This
pass re-scans the current tree for what that audit missed.

Files audited (`com.babytracker.*`):
- `domain/model/PumpingSession.kt`, `domain/model/PumpingBreast.kt`
- `domain/repository/PumpingRepository.kt`, `data/repository/PumpingRepositoryImpl.kt`
- `data/local/dao/PumpingDao.kt`, `data/local/entity/PumpingEntity.kt`
- `domain/usecase/pumping/*`
- `ui/pumping/*` (PumpingViewModel, PumpingHistoryViewModel, PumpingScreen, PumpingHistoryScreen, EditPumpingSessionSheet, AddBagPromptSheet, PumpingBreastLabel)
- cross-checked against `ui/component/EditDateTimeField.kt`, `ui/component/SectionLabel.kt`

**Bottom line:** one genuinely big cut the prior audit missed — `PumpingScreen`'s manual-entry tab
hand-rolls its own date/time row, pickers, and `Instant` helpers that already exist as shared
`ui.component` functions, and the *sibling* `EditPumpingSessionSheet` already reuses those shared
ones. Deleting the duplicates is ~**-90 lines, -0 deps**. Everything else is marginal or deliberate
convention (re-confirmed).

---

## Ranked findings

```
delete: PumpingScreen manual-mode date/time stack (ManualFieldRow, ManualFieldCell, ManualDatePicker, ManualTimePicker, Instant.toManualDateLabel, Instant.withManualDate, Instant.withManualTime, enum ManualField). Reuse shared EditDateTimeRow / EditDatePicker / EditTimePicker / Instant.toEditDateLabel / withEditedDate / withEditedTime / EditField — EditPumpingSessionSheet already does. [ui/pumping/PumpingScreen.kt:699-939]
shrink: private SectionLabel(text) wrapper that only forces color=primary over shared ui.component.SectionLabel(text, color). Inline as SectionLabel(text, color = MaterialTheme.colorScheme.primary) at 3 call sites. [ui/pumping/EditPumpingSessionSheet.kt:251]
delete: stale KDoc referencing PumpingBreast.displayName() — that function was deleted in f043e2180; the comment now points at nothing. [ui/pumping/PumpingBreastLabel.kt:7-10]
```

```
net: -90 lines, -0 deps possible (finding 1 is ~-90; findings 2-3 ~0 net / -3 comment lines).
```

---

## Validation & feasibility (execute-later notes)

### 1. ✅ DONE (this branch) — PumpingScreen manual mode duplicates shared edit components  *(~-90 lines, the real cut)*

> Executed: `ManualFieldRow`/`ManualFieldCell`/`ManualDatePicker`/`ManualTimePicker`/`toManualDateLabel`/
> `withManualDate`/`withManualTime` deleted; manual mode now calls shared `EditDateTimeRow`/
> `EditDatePicker`/`EditTimePicker`/`toEditDateLabel`/`withEditedDate`/`withEditedTime`. The local
> `enum ManualField` was kept (the sibling's `EditField` is `private`; importing it adds coupling for
> no gain). All 11 `PumpingScreenTest` instrumented tests pass on emulator.

`PumpingScreen.kt` (Manual tab) defines a complete private copy of the date/time editing stack:

| Private in `PumpingScreen.kt` | Shared equivalent in `ui/component/EditDateTimeField.kt` | Difference |
|---|---|---|
| `ManualFieldRow` (735) | `EditDateTimeRow` (38) | shared adds an unused `placeholder` param (defaults false) — drop-in |
| `ManualFieldCell` (759) | `EditDateTimeCell` (65, private behind the Row) | manual uses `height(56.dp)` + `titleSmall`; shared uses `64.dp` + `titleMedium` |
| `ManualDatePicker` (864) | `EditDatePicker` (92) | identical logic; manual uses `android.R.string.ok`, shared uses `R.string.ok` |
| `ManualTimePicker` (891) | `EditTimePicker` (121) | byte-for-byte identical logic |
| `Instant.toManualDateLabel` (918) | `Instant.toEditDateLabel` (142) | identical (`LocalDate.now(zone)` vs `LocalDate.now()` — same default zone) |
| `Instant.withManualDate` (929) | `Instant.withEditedDate` (152) | identical |
| `Instant.withManualTime` (935) | `Instant.withEditedTime` (158) | identical |
| `enum ManualField { START, END }` (915) | `enum EditField { START, END }` (private in EditPumpingSessionSheet) | identical shape |

**This is proven safe by the sibling file.** `EditPumpingSessionSheet.kt` (the *edit* path for the
same `PumpingSession`) already imports and uses `EditDateTimeRow`, `EditDatePicker`,
`EditTimePicker`, `toEditDateLabel`, `withEditedDate`, `withEditedTime` from `ui.component`. The
manual *entry* path is the only place still carrying a private copy — pure historical drift.

**Feasibility: high.** Replace the manual helpers with the shared imports. The Manual tab's
date/time rows currently feed the picker through `ManualField` + a local `original.withManualDate/Time`
branch; that maps 1:1 onto `EditField` + `withEditedDate/Time`.

**Behavior delta to accept:** the two manual field cells become 8.dp taller (56→64) and one
typography step larger (`titleSmall`→`titleMedium`), matching the edit sheet. This is *more*
consistent, not a regression. No string/label changes.

**Test risk: low.** `app/src/androidTest/.../PumpingScreenTest.kt` asserts only on text
(`"Save Session"`, `"Left"/"Right"/"Both"`, `"Enter volume in mL"`) — never on cell height or
typography. `toManualDateLabel`/`toEditDateLabel` produce identical strings, so any date-label
assertions stay green. Run `:app:connectedAndroidTest --tests "*PumpingScreenTest*"` (needs a device)
plus the unit suite to confirm.

**Action sketch:**
- Delete `ManualFieldRow`, `ManualFieldCell`, `ManualDatePicker`, `ManualTimePicker`,
  `Instant.toManualDateLabel`, `Instant.withManualDate`, `Instant.withManualTime`, and
  `enum ManualField` from `PumpingScreen.kt`.
- Add imports: `com.babytracker.ui.component.{EditDateTimeRow, EditDatePicker, EditTimePicker,
  toEditDateLabel, withEditedDate, withEditedTime}`.
- In `ManualModeContent`, swap `ManualFieldRow(...)`→`EditDateTimeRow(...)`,
  `manual.startTime.toManualDateLabel()`→`.toEditDateLabel()`, the picker call sites to
  `EditDatePicker`/`EditTimePicker`, `withManualDate`/`withManualTime`→`withEditedDate`/`withEditedTime`,
  and the `ManualField` state holders to `EditField` (or a fresh local enum if you'd rather not import
  the sheet's private one — define a single shared `EditField` once if you touch both).
- Drop now-unused imports in `PumpingScreen.kt` (DatePicker, DatePickerDialog, TimePicker,
  rememberDatePickerState, rememberTimePickerState, DialogProperties, ZoneOffset, DateTimeFormatter,
  Locale, AlertDialog, etc. — let ktlint/compiler flag them).

> Note: `EditField` is currently `private` inside `EditPumpingSessionSheet.kt`. Simplest path is a
> local `enum` in `PumpingScreen.kt` reusing the shared *helpers* (the big win) without sharing the
> enum. Promoting `EditField` to an internal shared enum is optional polish, not required.

### 2. ✅ DONE (#361) — inline private `SectionLabel(text)` wrapper  *(marginal, ~0 net)*

> Executed: the private wrapper was deleted; the 3 call sites bind to the shared
> `ui.component.SectionLabel(text)` (whose `color` already defaults to `primary`), so no color arg is
> needed. Verified by `EditPumpingSessionSheetTest` (10/10).

`EditPumpingSessionSheet.kt:251` still wraps the shared `ui.component.SectionLabel(text, color)` only
to inject `color = MaterialTheme.colorScheme.primary` — but `SectionLabel`'s `color` **already
defaults to `MaterialTheme.colorScheme.primary`** (`SectionLabel.kt:19`). So the wrapper passes the
default explicitly and adds nothing. The 3 call sites (135, 145, 159) can call the shared
`SectionLabel(text)` directly and the local wrapper deletes entirely.

**Feasibility: trivial, but net ~0** (delete ~6-line wrapper, 3 call sites unchanged). Carried over
from prior audit finding 5; do it only when touching the file (e.g. alongside finding 1 is a separate
file, so skip unless convenient). The shared default makes this *cleaner* than prior audit implied —
no color arg needed at all.

### 3. ✅ DONE (#361) — stale `displayName()` KDoc  *(-3 comment lines)*

> Executed: trimmed the `PumpingBreastLabel` KDoc to a single accurate line; the dead
> `displayName()` reference is gone.

`PumpingBreastLabel.kt:7-10` doc comment says *"The domain `displayName()` stays locale-agnostic"* —
but `PumpingBreast.displayName()` was deleted in `f043e2180`. The comment references a symbol that no
longer exists. Trim to a one-liner (e.g. *"Localized label resource for a [PumpingBreast]."*).

**Feasibility: trivial**, comment-only, no test impact. Fold into finding 1's commit or its own.

---

## Out of scope — routed elsewhere (NOT over-engineering)

- **i18n gaps — ✅ ROUTED + FIXED (#362):** hardcoded English `"Session paused"` /
  `"Session in progress"` (`PumpingScreen.kt`) and the `"BREAST"` literal passed to `SectionLabel`
  (`EditPumpingSessionSheet.kt`) were missing `stringResource` calls. Routed to the i18n project
  (AKA-185) and then wired to existing resources (`breastfeeding_status_paused` /
  `breastfeeding_status_in_progress` / `pumping_breast_caps`, all with pt-BR copy) — no new strings
  needed. `ui/pumping/` now has zero UI-facing literals.

## Re-confirmed lean / deliberate (no action) — agreeing with prior audit

- **Delegation use cases** (`DeletePumpingSessionUseCase`, `GetPumpingHistoryUseCase`) — codebase-wide
  single-responsibility convention mirrored across every feature; cutting only pumping's two creates
  inconsistency. Leave (project-wide decision or nothing).
- **Duplicated `require()` validation** in `SavePumpingSessionUseCase` / `UpdatePumpingSessionUseCase`
  — cheap domain invariants at a trust boundary; the only guard for non-UI callers (backup
  import/export). Defense-in-depth, keep.
- **`promptedStoppedSessionIds` second guard set** in `PumpingViewModel` — distinct lifetime from
  `stoppingSessionIds` (the latter is removed on failure to allow retry; the prompt guard must survive
  that retry to avoid a double bag-prompt). Keep.
- **`now: () -> Instant` injection** — standard testability clock seam.
- **`StopPumpingSessionUseCase` conditional-update + refetch fallback** — defensive concurrency
  (correctness), not over-engineering.
- **`PumpingRepository`** — all 8 methods have a live caller. No dead interface surface.
- **`PumpingDao.getAllSessionsOnce()`** — used by backup export/import. Not dead.

---

## Status — all done

1. ✅ **Finding 1** — DONE (#360). Manual mode reuses shared edit date/time components. `PumpingScreenTest` 11/11.
2. ✅ **Finding 2** — DONE (#361). Redundant private `SectionLabel` wrapper deleted.
3. ✅ **Finding 3** — DONE (#361). Stale `displayName()` KDoc trimmed.
4. ✅ **i18n gap** (out of scope here) — ROUTED to AKA-185 + FIXED (#362).

Nothing outstanding. Pumping is lean against this audit.

```
net realized (all findings applied): ~-95 lines, -0 deps. Plus the i18n gap closed (#362).
```
