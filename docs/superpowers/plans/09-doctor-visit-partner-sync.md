# Doctor Visit Partner Sync (Read-Only) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-209

**Goal:** Sync doctor **visits** (not questions) to the partner snapshot so a connected partner sees the visit history read-only, reusing the existing Firestore snapshot pipeline.

**Architecture:** The app already syncs a read-only `ShareSnapshot` to Firestore via anonymous auth (`sharing/`). This plan adds a `DoctorVisitSnapshot` list to `ShareSnapshot`, sources it in `SnapshotSources`, maps domain→snapshot in `DomainToSnapshot`, serializes it in `FirestoreSnapshotMapping`, includes it in `SyncToFirestoreUseCase` and the initial share-code snapshot, triggers a sync after visit mutations (so the partner view doesn't go stale), and renders a read-only visits section on the partner dashboard. **Questions are intentionally excluded** (private prep notes). All Firebase-specific work stays inside `sharing/` + `di/SharingModule.kt` (per `CLAUDE.md`).

**Tech Stack:** Firebase Firestore/Auth (BOM 33.7.0), Kotlin, Hilt, Coroutines; JUnit 5 + MockK.

## Global Constraints

- Read-only sync: the partner never writes visits back (mirror diaper/growth, which are read-only on the partner side).
- Additive to `ShareSnapshot`: older snapshots without `doctorVisits` deserialize to an empty list (no crash).
- Keep all Firebase code within `sharing/` and `di/SharingModule.kt`.
- No new remote integrations beyond extending this existing feature.

**Dependencies:** Plan 1 (data — `DoctorVisitRepository.observeAllVisits` / `getAllVisitsOnce`), Plan 2 (domain). Independent of plans 3–8.

**Suggested implementation branch:** `feat/doctor-visit-partner-sync`

**Project convention:** Implement first, then tests. Commit after each task. Pre-commit hook runs ktlint/detekt.

---

### Task 1: `DoctorVisitSnapshot` in `ShareSnapshot`

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/domain/model/ShareSnapshot.kt`

- [ ] **Step 1: Add the snapshot field + DTO** (mirror `DiaperSnapshot`). Visit-only fields; no questions, no snapshot-reference fields (those are local-only).

```kotlin
// in ShareSnapshot data class, add (with a default for backward compatibility):
val doctorVisits: List<DoctorVisitSnapshot> = emptyList(),

// new DTO at the bottom of the file:
data class DoctorVisitSnapshot(
    val date: Long,
    val providerName: String? = null,
    val notes: String? = null,
)
```

- [ ] **Step 2: Commit** `feat(doctor-visit): add DoctorVisitSnapshot to ShareSnapshot`

---

### Task 2: Source visits in `SnapshotSources` + map in `DomainToSnapshot`

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/usecase/SnapshotSources.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/domain/model/DomainToSnapshot.kt`
- Test: `app/src/test/java/com/babytracker/sharing/domain/model/DomainToSnapshotDoctorVisitTest.kt`

- [ ] **Step 1: Add the collaborator** to `SnapshotSources`:

```kotlin
val doctorVisit: DoctorVisitRepository,
```

(add the import; the class is a simple `@Inject` constructor bundle).

- [ ] **Step 2: Map domain → snapshot** in `DomainToSnapshot` (mirror the diaper mapping). Add an extension:

```kotlin
fun DoctorVisit.toSnapshot(): DoctorVisitSnapshot =
    DoctorVisitSnapshot(date = date.toEpochMilli(), providerName = providerName, notes = notes)
```

and include `doctorVisits = visits.map { it.toSnapshot() }` wherever the `ShareSnapshot` is assembled (see `SyncToFirestoreUseCase`, task 3).

- [ ] **Step 3: Test** the mapping (date→epoch-ms, null provider/notes preserved).
- [ ] **Step 4: Run** `./gradlew test --tests "com.babytracker.sharing.domain.model.DomainToSnapshotDoctorVisitTest"` — expect PASS.
- [ ] **Step 5: Commit** `feat(doctor-visit): source + map visits for partner snapshot`

---

### Task 3: Include visits in `SyncToFirestoreUseCase`

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/usecase/SyncToFirestoreUseCase.kt`
- Test: `app/src/test/java/com/babytracker/sharing/usecase/SyncToFirestoreDoctorVisitTest.kt`

- [ ] **Step 1: Read visits + populate the snapshot.** Where the use case reads the other sources (diapers, growth, etc.) to build `ShareSnapshot`, add a read of `sources.doctorVisit.getAllVisitsOnce()` (one-shot, matching how the other lists are pulled — confirm whether the use case uses `getAllOnce`-style reads or `observe().first()`) and set `doctorVisits = visits.map { it.toSnapshot() }`.
- [ ] **Step 2: Initial share-code snapshot.** `GenerateShareCodeUseCase` builds the **first** snapshot a newly-connected partner receives (search `rg "buildSnapshot|GenerateShareCodeUseCase" -g "*.kt"`). Add doctor visits to that snapshot construction too, so a partner doesn't start with an empty visits list until the next full sync. If both use cases share a single snapshot-builder helper, adding visits there covers both; otherwise update both call sites.
- [ ] **Step 3: Test** that the assembled snapshot includes mapped visits (MockK the sources; assert the `ShareSnapshot.doctorVisits` content), for both `SyncToFirestoreUseCase` and the share-code snapshot. Match the existing test setup.
- [ ] **Step 4: Run** `./gradlew test --tests "com.babytracker.sharing.usecase.SyncToFirestoreDoctorVisitTest"` — expect PASS.
- [ ] **Step 5: Commit** `feat(doctor-visit): include visits in sync + share-code snapshot`

---

### Task 4: Trigger sync after visit mutations

**Files:**
- Modify: wherever the app triggers `SyncToFirestoreUseCase` after local mutations (search `rg "SyncToFirestoreUseCase" -g "*.kt"` to find the trigger mechanism — it may be explicit calls in mutation use cases, a sync coordinator/observer, or a `WorkManager` job observing DB changes).

- [ ] **Step 1: Identify the existing trigger pattern.** Determine how an already-synced feature (e.g. diaper) pushes after add/edit/delete:
  - If sync is **observer/coordinator-based** (a component observes repository Flows and re-syncs on change), add the `DoctorVisitRepository.observeAllVisits()` Flow to that observer's set of watched sources. Adding visits to the snapshot (Task 3) + to the observer is then sufficient — no change to plan 2's use cases.
  - If sync is **explicitly triggered from mutation use cases**, add the same trigger call to `AddDoctorVisitUseCase` / `EditDoctorVisitUseCase` / `DeleteDoctorVisitUseCase` (plan 2) after the local write — injecting the sync trigger the way diaper's use cases do. Keep the trigger best-effort (sync failures must not fail the local mutation).
- [ ] **Step 2: Test** that a visit mutation results in a sync trigger (verify via the same test seam the diaper feature uses — e.g. the coordinator observes the new Flow, or the use case invokes the trigger). If the mechanism is observer-based and already generic, a focused test asserting the visits Flow is included in the watched set is sufficient.
- [ ] **Step 3: Build + run** `./gradlew test` for the touched area — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): trigger partner sync on visit mutations`

---

### Task 5: Firestore serialize / deserialize

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/data/firebase/FirestoreSnapshotMapping.kt`
- Test: `app/src/test/java/com/babytracker/sharing/data/firebase/FirestoreSnapshotDoctorVisitTest.kt`

- [ ] **Step 1: Serialize** `doctorVisits` to the Firestore map (list of maps `{date, providerName, notes}`), mirroring the diaper serialization, and **deserialize** it back, defaulting to an empty list when the field is absent (older snapshots).
- [ ] **Step 2: Test** a round-trip (write → map → read) and the missing-field case (old snapshot → empty list, no crash).
- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.sharing.data.firebase.FirestoreSnapshotDoctorVisitTest"` — expect PASS.
- [ ] **Step 4: Commit** `feat(doctor-visit): serialize visits in Firestore snapshot mapping`

---

### Task 6: Partner dashboard read-only visits section

**Files:**
- Modify: the partner dashboard UI (search `rg "DiaperSnapshot" -g "*.kt"` and the partner dashboard composable that renders diaper/growth sections — likely under `ui/partner/`)
- Modify: `app/src/main/res/values/strings.xml` + `values-pt-rBR/strings.xml`

- [ ] **Step 1: Render a read-only visits card/section** on the partner dashboard, listing upcoming + recent visits (date — provider — notes preview), mirroring how the partner diaper/growth section renders. No edit/delete affordances (read-only).
- [ ] **Step 2: Strings**

```xml
<!-- values/strings.xml -->
<string name="partner_doctor_visits_heading">Doctor visits</string>
<string name="partner_doctor_visits_empty">No visits shared</string>
<!-- values-pt-rBR/strings.xml -->
<string name="partner_doctor_visits_heading">Consultas médicas</string>
<string name="partner_doctor_visits_empty">Nenhuma consulta compartilhada</string>
```

- [ ] **Step 3: Build** `./gradlew :app:compileDebugKotlin` — expect success.
- [ ] **Step 4: Commit** `feat(doctor-visit): show visits on partner dashboard`

---

## Acceptance Criteria

- `./gradlew build` and `./gradlew test` pass.
- The owner's sync payload includes mapped doctor visits; questions are never synced.
- Visit add/edit/delete trigger a partner sync (so the partner doesn't see stale visits).
- A newly-connected partner's first snapshot (share-code) already contains existing visits.
- A partner snapshot without `doctorVisits` deserializes to an empty list (no crash).
- The partner dashboard shows a read-only visits section.
- All Firebase work stays within `sharing/` + `di/SharingModule.kt`.
- Strings in en + pt-BR.

## Self-Review Notes

- Spec coverage: `ShareSnapshot` field + DTO, `SnapshotSources` collaborator, `DomainToSnapshot` mapping, `SyncToFirestoreUseCase` + share-code snapshot inclusion, **mutation-triggered sync** (so visits don't go stale on the partner side), Firestore mapping round-trip, partner dashboard section — all present.
- The mutation-trigger task adapts to the repo's existing sync mechanism (observer/coordinator vs. explicit use-case call) rather than assuming one; the implementer mirrors how diaper triggers sync.
- Questions are deliberately excluded (private). Snapshot-reference fields are local-only and not synced.
- Backward compatibility: additive field with an empty-list default at both the data-class and Firestore-mapping layers.
- The implementer must confirm whether `SyncToFirestoreUseCase` pulls sources via one-shot reads or `observe().first()` and match it to avoid a hung/stale sync.
