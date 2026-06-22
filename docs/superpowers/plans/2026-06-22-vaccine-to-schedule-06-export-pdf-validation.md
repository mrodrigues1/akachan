# Vaccine To-Schedule — Plan 06: Export, PDF & Backup Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make to-schedule doses round-trip through backup and appear in the PDF report, without a backup-format-version bump.

**Architecture:** Backup serialization already carries `status` as a raw String (`BackupConverters`), so to-schedule round-trips untouched. `ValidateBackupUseCase` gains the new exhaustive `when` branch (require a target date). The PDF folds to-schedule doses into the existing "Upcoming" sublist (both share `scheduledDate`), keeping the page-count mirror intact.

**Tech Stack:** Kotlin, kotlinx.serialization, Android PdfDocument, JUnit 5, MockK, Robolectric.

## Global Constraints

- **No backup-format-version bump** — `status` is an existing String field gaining a value. `BackupConverters` needs no change.
- An older app importing a TO_SCHEDULE backup degrades that row to `ADMINISTERED` via `toVaccineStatusSafe()` — acceptable, never fabricates a reminder.
- The PDF `drawVaccinesSection` and `countPages` y-advancement MUST stay mirrored — change the draw filter and the count filter together.
- Depends on Plan 01 (`VaccineStatus.TO_SCHEDULE`).

---

### Task 1: `ValidateBackupUseCase` — to-schedule invariant branch

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/domain/usecase/ValidateBackupUseCase.kt`
- Test: `app/src/test/java/com/babytracker/export/domain/usecase/ValidateBackupUseCaseTest.kt` (add cases — confirm exact path/class name in the test tree and match it)

**Interfaces:**
- Consumes: `VaccineStatus.TO_SCHEDULE`; `String.toVaccineStatusOrNull`/`toVaccineStatusSafe`.
- Produces: a TO_SCHEDULE backup row with a target date validates; one without is rejected.

- [ ] **Step 1: Write the failing tests**

Add to the validate-backup test (build a minimal valid `BackupData` with one vaccine via the test's existing helpers/builders — mirror the existing vaccine validation tests in the file):

```kotlin
@Test
fun `accepts a to-schedule vaccine that has a target date`() {
    val json = backupJsonWithVaccine(status = "TO_SCHEDULE", scheduledDate = 1_900_000_000_000, administeredDate = null)
    val result = useCase(json)
    assertEquals(1, result.vaccines.size)
}

@Test
fun `rejects a to-schedule vaccine with no target date`() {
    val json = backupJsonWithVaccine(status = "TO_SCHEDULE", scheduledDate = null, administeredDate = null)
    assertThrows(InvalidBackupException::class.java) { useCase(json) }
}
```

If the test file has no `backupJsonWithVaccine` helper, construct the JSON the same way the existing `scheduled vaccine ... has no scheduled date` test does (reuse that test's fixture and only flip `status`/`scheduledDate`). Imports: `com.babytracker.export.domain.InvalidBackupException`, `org.junit.jupiter.api.Assertions.assertThrows`/`assertEquals`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.babytracker.export.domain.usecase.ValidateBackupUseCaseTest"`
Expected: FAIL — `when (v.status.toVaccineStatusSafe())` is non-exhaustive (compile error) without a `TO_SCHEDULE` branch.

- [ ] **Step 3: Write minimal implementation**

In `ValidateBackupUseCase.kt`, add a branch to the `when` in `validateVaccineInvariants`:

```kotlin
when (v.status.toVaccineStatusSafe()) {
    VaccineStatus.TO_SCHEDULE ->
        if (v.scheduledDate == null) bad("to-schedule vaccine ${v.id} has no target date")
    VaccineStatus.SCHEDULED ->
        if (v.scheduledDate == null) bad("scheduled vaccine ${v.id} has no scheduled date")
    VaccineStatus.ADMINISTERED ->
        if (v.administeredDate == null) bad("administered vaccine ${v.id} has no administered date")
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.babytracker.export.domain.usecase.ValidateBackupUseCaseTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/export/domain/usecase/ValidateBackupUseCase.kt app/src/test/java/com/babytracker/export/domain/usecase/ValidateBackupUseCaseTest.kt
git commit -m "feat(vaccine): validate to-schedule doses in backup import"
```

---

### Task 2: `PdfReportGenerator` — include to-schedule in the upcoming sublist

**Files:**
- Modify: `app/src/main/java/com/babytracker/export/data/PdfReportGenerator.kt`
- Test: `app/src/test/java/com/babytracker/export/data/PdfReportGeneratorTest.kt` (add case if the file exists; otherwise rely on Step 4 build + the existing page-count test)

**Interfaces:**
- Consumes: `VaccineStatus.TO_SCHEDULE`.
- Produces: to-schedule doses render under "Upcoming" (by target date); the page-count mirror still matches the draw.

- [ ] **Step 1: Write the failing test (if a PDF test exists)**

If `PdfReportGeneratorTest.kt` exists and asserts page counts / no-crash, add:

```kotlin
@Test
fun `renders a to-schedule vaccine without crashing and counts it as upcoming`() {
    val data = backupDataWith(
        vaccines = listOf(
            VaccineRecord(id = 1, name = "MMR", status = VaccineStatus.TO_SCHEDULE,
                scheduledDate = Instant.parse("2026-07-10T00:00:00Z"), createdAt = Instant.parse("2026-06-01T00:00:00Z")),
        ),
    )
    val file = generator.generate(data) // use the file's existing entry point / temp-file helper
    assertTrue(file.length() > 0)
}
```

Match the test file's actual fixture builder and generator entry point. If no such test exists, skip to Step 3 and rely on the build check plus the existing count-mirror test.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.babytracker.export.data.PdfReportGeneratorTest"`
Expected: FAIL or page-count mismatch (the to-schedule row isn't drawn but, after the count change, would be counted — or vice versa). If no test file exists, proceed.

- [ ] **Step 3: Write minimal implementation**

In `PdfReportGenerator.kt`:

In `drawVaccinesSection`, broaden the `upcoming` filter to include to-schedule (both carry `scheduledDate`):

```kotlin
val upcoming = vaccines
    .filter { it.status != VaccineStatus.ADMINISTERED }
    .sortedBy { it.scheduledDate ?: it.createdAt }
```

In `countPages` (the mirror), broaden `upcomingCount` identically:

```kotlin
val upcomingCount = data.vaccines.count { it.status != VaccineStatus.ADMINISTERED }
```

(The "Administered" filter stays `== VaccineStatus.ADMINISTERED`, so every vaccine lands in exactly one sublist and the total still sums to `vaccines.size`.)

- [ ] **Step 4: Build + run the tests to verify they pass**

Run: `./gradlew :app:compileDebugKotlin` then `./gradlew test --tests "com.babytracker.export.data.PdfReportGeneratorTest"` (if present).
Expected: BUILD SUCCESSFUL / PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/export/data/PdfReportGenerator.kt app/src/test/java/com/babytracker/export/data/PdfReportGeneratorTest.kt
git commit -m "feat(vaccine): include to-schedule doses in the PDF upcoming list"
```

---

### Task 3: Full validation sweep

**Files:** none (verification only).

- [ ] **Step 1: Run the full unit suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — confirms no remaining exhaustive-`when` breaks anywhere the enum is consumed (e.g. `ObserveVaccineSummaryUseCase` uses equality filters, not a `when`, so it compiles and simply ignores to-schedule on the home tile — intentional, per spec out-of-scope).

- [ ] **Step 2: Full build (resources, manifest, DI, Room)**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. (Catches the R8/Hilt + pt-plural classes of failure that `test` misses.)

- [ ] **Step 3: Commit (only if Step 2 surfaced a fix)**

```bash
git add -A
git commit -m "chore(vaccine): fix issues surfaced by full build for to-schedule status"
```

---

## Self-Review

- **Spec coverage:** backup validation branch (Task 1), PDF rendering (Task 2), full sweep (Task 3). `BackupConverters` confirmed pass-through (no change). No backup/DB version bump (confirmed).
- **Placeholder scan:** PDF/validate test snippets defer to each file's existing fixtures (flagged) — match them rather than invent. All production-code steps carry concrete code.
- **Types:** PDF draw filter and count filter changed identically (`!= ADMINISTERED`), preserving the page-count mirror invariant.
