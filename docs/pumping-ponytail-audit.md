# Pumping Section — Ponytail Over-Engineering Audit

Scope: over-engineering / complexity only. Correctness bugs, security, and performance
are out of scope (route those to a normal review). This is a report — nothing was applied.

Files audited (`com.babytracker.*`):
- `domain/model/PumpingSession.kt`, `domain/model/PumpingBreast.kt`
- `domain/repository/PumpingRepository.kt`, `data/repository/PumpingRepositoryImpl.kt`
- `data/local/dao/PumpingDao.kt`, `data/local/entity/PumpingEntity.kt`
- `domain/usecase/pumping/*` (Start, Stop, Pause, Resume, Save, Update, Delete, GetHistory, ValidatePumpingEdit, PumpingEditError)
- `ui/pumping/*` (PumpingViewModel, PumpingHistoryViewModel, PumpingScreen, PumpingHistoryScreen, EditPumpingSessionSheet, AddBagPromptSheet, PumpingBreastLabel)

**Bottom line:** the pumping section mirrors the already-audited breastfeeding section and is
largely lean. Exactly **one** clean, safe cut exists. Everything else is either a deliberate
project-wide convention or cheap defensive code — flagged for completeness, but **not recommended**.

---

## Ranked findings

`delete: PumpingBreast.displayName(). Nothing — it has zero callers; UI uses labelRes(). [domain/model/PumpingBreast.kt:5]`
`yagni: DeletePumpingSessionUseCase + GetPumpingHistoryUseCase (one-line repo passthroughs). Inline to the repo — BUT project-wide convention; leave. [domain/usecase/pumping/]`
`shrink: use-case require() re-validating volume>0 / end>start already checked in the VM. Drop the requires — BUT cheap invariants; leave. [SavePumpingSessionUseCase.kt:19, UpdatePumpingSessionUseCase.kt:20]`
`shrink: PumpingViewModel.promptedStoppedSessionIds — second guard set, mostly redundant with stoppingSessionIds for a single active session. Leave; cheap idempotency. [ui/pumping/PumpingViewModel.kt:81]`
`shrink: private SectionLabel(text) wrapper over the shared component (3 calls). Inline color arg — marginal. [ui/pumping/EditPumpingSessionSheet.kt:251]`

`net: -5 lines, -0 deps safely (finding 1 only). Up to ~-25 more only if the use-case convention is dropped project-wide (out of scope for one section).`

---

## Validation & feasibility (execute-later notes)

### 1. ✅ EXECUTE — delete `PumpingBreast.displayName()`  *(safe, ~-5 lines)*

`domain/model/PumpingBreast.kt`:
```kotlin
fun PumpingBreast.displayName(): String = when (this) {
    PumpingBreast.LEFT -> "Left"
    PumpingBreast.RIGHT -> "Right"
    PumpingBreast.BOTH -> "Both"
}
```

**Validated dead.** Searched all `.kt` (main + test + androidTest): the only `displayName()`
callers reference `BreastSide.displayName()` (a *different* type, used by `SideSelectorTest`).
`PumpingBreast.displayName()` has **no caller**. UI everywhere resolves the localized label via
`PumpingBreast.labelRes()` (`ui/pumping/PumpingBreastLabel.kt`) — and the doc comment there even
says the domain `displayName()` "stays locale-agnostic", but nothing actually consumes it.

It's also a hardcoded-English helper, so it could never have been a UI path under the active i18n work.

**Action:** delete lines 5–9, keeping only `enum class PumpingBreast { LEFT, RIGHT, BOTH }`.
No imports change. No test references it. `./gradlew test` should stay green.

> Note: `BreastSide.displayName()` (breastfeeding) is *kept* — it has live test callers. Only the
> pumping copy is dead.

### 2. ❌ DO NOT EXECUTE — pure-delegation use cases  *(convention, not bloat)*

`DeletePumpingSessionUseCase` and `GetPumpingHistoryUseCase` are one-line passthroughs:
```kotlin
suspend operator fun invoke(session: PumpingSession) = repository.delete(session)
operator fun invoke(): Flow<List<PumpingSession>> = repository.getAllSessions()
```

In isolation these look like YAGNI wrappers. **But validation shows they are a deliberate,
codebase-wide pattern:** `DeleteBreastfeedingSessionUseCase` / `GetBreastfeedingHistoryUseCase`
are byte-for-byte equivalents, `CLAUDE.md` mandates single-responsibility use cases, and the prior
`docs/breastfeeding-simplification-audit.md` explicitly contested then **left** the same wrappers
("use-case-convention call", no action).

**Feasibility: low.** Cutting pumping's two would make it the only feature reaching the repo
directly from the VM — inconsistency costs more than the ~20 lines saved. Only worth doing as a
project-wide decision (all features at once), which is out of scope for a single-section audit.

### 3. ❌ DO NOT EXECUTE — duplicated volume/end validation  *(defense-in-depth)*

The `require(volumeMl > 0)` / `require(endTime.isAfter(startTime))` in `SavePumpingSessionUseCase`
and `UpdatePumpingSessionUseCase` repeat checks the ViewModel (`onManualSave`) and
`validatePumpingEdit` already perform before calling them — so on the real UI path the `require`s
are unreachable.

**Feasibility: low / risky.** They are cheap domain invariants and the only guard if a use case is
ever called from a non-UI caller (export/import, future widget). Removing them trades a tiny line
count for a behavior change at a trust boundary — exactly what ponytail says *not* to simplify away.
Leave them.

### 4. ❌ DO NOT EXECUTE — `promptedStoppedSessionIds`  *(cheap idempotency)*

`PumpingViewModel` keeps two `MutableSet<Long>`: `stoppingSessionIds` (blocks concurrent stops) and
`promptedStoppedSessionIds` (blocks showing the add-bag prompt twice). For a single active session
the second is *mostly* implied by the first, so it reads as redundant.

**Feasibility: low.** It's 3 lines guarding a user-visible duplicate-prompt, and the two sets have
subtly different lifetimes (`stoppingSessionIds` is *removed* on failure to allow retry; the prompt
guard must survive that retry). Removing it risks a double bag-prompt on retry. Not worth it.

### 5. ⚠️ OPTIONAL — inline private `SectionLabel` wrapper  *(marginal)*

`EditPumpingSessionSheet.kt:251` defines a private `SectionLabel(text)` that only adds
`color = MaterialTheme.colorScheme.primary` over the shared `ui.component.SectionLabel(text, color)`.
3 call sites. Could be inlined as `SectionLabel(text, color = MaterialTheme.colorScheme.primary)`.

**Feasibility: works, but marginal** (~net 0 lines after expanding 3 call sites). Skip unless touching
the file anyway. (Also note: one call passes a hardcoded `"BREAST"` literal at line 159 — that's an
i18n gap, **out of scope** for this over-engineering pass; route to the i18n project.)

---

## What was checked and is genuinely lean (no action)

- `PumpingRepository` — all 8 methods have a live caller (Start/Stop/Pause/Resume/Update/Delete/history/active). No dead interface surface.
- `PumpingDao.getAllSessionsOnce()` — used by backup export/import (`BackupSourceImpl`, `BackupImporterImpl`), not dead.
- `now: () -> Instant` injection — the codebase's standard clock seam for testability; keep.
- `StopPumpingSessionUseCase` conditional-update + refetch fallback — defensive concurrency (correctness), not over-engineering.
- `mapList` (`RepositoryExt.kt`) — shared one-liner with multiple callers; keep.
- `PumpingMode` (MANUAL/TIMER), `validatePumpingEdit` + `PumpingEditError` enum — real feature surface / testable validation, not speculative.

---

## Suggested execution plan (next session)

1. Apply **finding 1 only**: delete `PumpingBreast.displayName()`.
2. `./gradlew test` (or targeted `:app:testDebugUnitTest`) to confirm green.
3. Commit: `refactor(pumping): drop unused PumpingBreast.displayName`.
4. Leave findings 2–5 as documented (convention / defensive / marginal).

`net realized: -5 lines, -0 deps.`
