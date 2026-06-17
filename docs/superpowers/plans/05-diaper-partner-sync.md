# Diaper Partner Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**LINEAR_ISSUE:** AKA-163

**Goal:** Sync diaper changes into the read-only partner snapshot (Firestore) and render last-change + today-count on the partner dashboard. Diaper log/edit/delete trigger a targeted sync, mirroring bottle feeds.

**Architecture:** Adds `DiaperSnapshot` to `ShareSnapshot`, a `DiaperChange.toSnapshot()` mapper, Firestore map ↔ snapshot conversion, a `SyncType.DIAPERS` targeted sync (interface → impl → `FirestoreSharingService`), and sync triggers inside the plan-2 diaper use cases. The partner side already exposes the full `ShareSnapshot` in `PartnerDashboardUiState`, so the dashboard reads `snapshot.diapers` directly via a new `PartnerDiaperCard`. The new `diapers` field is optional/defaulted, so older partner readers ignore it (same as `bottleFeeds`/`growth`/`milestones`).

**Tech Stack:** Firebase Firestore KTX, Hilt, Kotlin, JUnit 5 + MockK.

**Dependencies:** Plan 1 (`DiaperRepository`, `DiaperChange`, `DiaperType`), Plan 2 (diaper use cases — modified here to add sync). Independent of plans 3/4.

**Suggested implementation branch:** `feat/diaper-partner-sync`

**Project convention:** Implement first, then tests. Commit after each task. Pre-commit hook runs ktlint/detekt. All sharing work stays in `sharing/` and `di/SharingModule.kt` per project rules.

---

### Task 1: `DiaperSnapshot` + domain mapper

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/domain/model/ShareSnapshot.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/domain/model/DomainToSnapshot.kt`
- Test: `app/src/test/java/com/babytracker/sharing/domain/model/DiaperSnapshotMappingTest.kt`

- [ ] **Step 1:** In `ShareSnapshot.kt`, add the field to the `ShareSnapshot` data class (after `milestones`):

```kotlin
val diapers: List<DiaperSnapshot> = emptyList(),
```

and add the snapshot type near the other `*Snapshot` classes:

```kotlin
data class DiaperSnapshot(
    val timestamp: Long,
    val type: String,
    val notes: String? = null,
)
```

- [ ] **Step 2:** In `DomainToSnapshot.kt`, add (with `import com.babytracker.domain.model.DiaperChange`):

```kotlin
fun DiaperChange.toSnapshot(): DiaperSnapshot = DiaperSnapshot(
    timestamp = timestamp.toEpochMilli(),
    type = type.name,
    notes = notes,
)
```

- [ ] **Step 3:** Test the mapper:

```kotlin
package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class DiaperSnapshotMappingTest {
    @Test
    fun `maps domain to snapshot`() {
        val snap = DiaperChange(
            id = 1,
            timestamp = Instant.ofEpochMilli(1_234),
            type = DiaperType.BOTH,
            notes = "leak",
            createdAt = Instant.ofEpochMilli(2_000),
        ).toSnapshot()
        assertEquals(1_234L, snap.timestamp)
        assertEquals("BOTH", snap.type)
        assertEquals("leak", snap.notes)
    }
}
```

- [ ] **Step 4: Run** `./gradlew test --tests "com.babytracker.sharing.domain.model.DiaperSnapshotMappingTest"` — expect PASS.
- [ ] **Step 5: Commit** `feat(sharing): add DiaperSnapshot and domain mapper`

---

### Task 2: Firestore map ↔ snapshot conversion

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/data/firebase/FirestoreSnapshotMapping.kt`
- Test: `app/src/test/java/com/babytracker/sharing/data/firebase/DiaperFirestoreMappingTest.kt`

- [ ] **Step 1:** Add `import com.babytracker.sharing.domain.model.DiaperSnapshot`, then the two converters:

```kotlin
internal fun diaperToMap(diaper: DiaperSnapshot): Map<String, Any?> = mapOf(
    "timestamp" to diaper.timestamp,
    "type" to diaper.type,
    "notes" to diaper.notes,
)

internal fun mapToDiaper(map: Map<*, *>): DiaperSnapshot = DiaperSnapshot(
    timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
    type = map["type"] as? String ?: "WET",
    notes = map["notes"] as? String,
)
```

- [ ] **Step 2:** Wire into `snapshotToMap` — add to the returned map:

```kotlin
"diapers" to snapshot.diapers.map { diaperToMap(it) },
```

- [ ] **Step 3:** Wire into `mapToSnapshot` — add the read and pass it to the `ShareSnapshot(...)` constructor:

```kotlin
val diapers = (data["diapers"] as? List<*>)
    ?.filterIsInstance<Map<*, *>>()
    ?.map { mapToDiaper(it) }
    .orEmpty()
```

```kotlin
// inside ShareSnapshot(...)
diapers = diapers,
```

- [ ] **Step 4:** Test the round trip:

```kotlin
package com.babytracker.sharing.data.firebase

import com.babytracker.sharing.domain.model.DiaperSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiaperFirestoreMappingTest {
    @Test
    fun `diaper map round trips`() {
        val original = DiaperSnapshot(timestamp = 5_000, type = "DIRTY", notes = "note")
        val restored = mapToDiaper(diaperToMap(original))
        assertEquals(original, restored)
    }

    @Test
    fun `missing fields fall back to defaults`() {
        val restored = mapToDiaper(mapOf<String, Any?>())
        assertEquals(0L, restored.timestamp)
        assertEquals("WET", restored.type)
        assertEquals(null, restored.notes)
    }
}
```

- [ ] **Step 5: Run** `./gradlew test --tests "com.babytracker.sharing.data.firebase.DiaperFirestoreMappingTest"` — expect PASS.
- [ ] **Step 6: Commit** `feat(sharing): map diapers in Firestore snapshot conversion`

---

### Task 3: `syncDiapers` through the sharing layer

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/domain/repository/SharingRepository.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/data/repository/SharingRepositoryImpl.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/data/firebase/FirestoreSharingService.kt`

- [ ] **Step 1:** In `SharingRepository.kt`, add the import for `DiaperSnapshot` and the method:

```kotlin
suspend fun syncDiapers(code: ShareCode, diapers: List<DiaperSnapshot>)
```

- [ ] **Step 2:** In `SharingRepositoryImpl.kt`, add the import and the override:

```kotlin
override suspend fun syncDiapers(code: ShareCode, diapers: List<DiaperSnapshot>) =
    service.syncDiapers(code.value, diapers)
```

- [ ] **Step 3:** In `FirestoreSharingService.kt`, add the method (mirrors `syncBottleFeeds`; `diaperToMap` is in the same package, so no import is needed for it):

```kotlin
suspend fun syncDiapers(code: String, diapers: List<DiaperSnapshot>) {
    val data = mapOf(
        "data" to mapOf(
            "lastSyncAt" to Timestamp.now(),
            "diapers" to diapers.map { diaperToMap(it) },
        ),
    )
    firestore.collection(SHARES).document(code).set(data, SetOptions.merge()).await()
}
```

(add `import com.babytracker.sharing.domain.model.DiaperSnapshot`)

- [ ] **Step 4: Commit** `feat(sharing): add syncDiapers to the sharing repository`

---

### Task 4: Wire diapers into `SnapshotSources` + `SyncToFirestoreUseCase`

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/usecase/SnapshotSources.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/usecase/SyncToFirestoreUseCase.kt`
- Test: `app/src/test/java/com/babytracker/sharing/usecase/SyncToFirestoreDiapersTest.kt`

- [ ] **Step 1:** In `SnapshotSources`, inject the diaper repository (add the import):

```kotlin
val diaper: DiaperRepository,
```

- [ ] **Step 2:** In `SyncToFirestoreUseCase`:
  - Add `DIAPERS` to the enum: `enum class SyncType { FULL, SESSIONS, SLEEP_RECORDS, BABY, INVENTORY, BOTTLE_FEEDS, DIAPERS }`
  - Add the branch in `when (syncType)`: `SyncType.DIAPERS -> syncDiapers(code)`
  - In `syncFull(...)`, read diapers and include them in the `ShareSnapshot`:

```kotlin
val diapers = sources.diaper.observeAll().first().take(SYNC_LIMIT)
```

```kotlin
// inside ShareSnapshot(...)
diapers = diapers.map { it.toSnapshot() },
```

  - Add the private sync function (add `import com.babytracker.sharing.domain.model.toSnapshot` is already present; ensure `DiaperChange.toSnapshot` resolves):

```kotlin
private suspend fun syncDiapers(code: ShareCode) {
    val diapers = sources.diaper.observeAll().first().take(SYNC_LIMIT)
    sharingRepository.syncDiapers(code, diapers.map { it.toSnapshot() })
}
```

- [ ] **Step 3:** Test the targeted sync (mirror existing `SyncToFirestoreUseCase` tests — mock `SnapshotSources`, `SharingRepository`, settings returning `PRIMARY` + a share code):

```kotlin
package com.babytracker.sharing.usecase

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.sharing.domain.model.DiaperSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class SyncToFirestoreDiapersTest {
    @Test
    fun `DIAPERS sync pushes mapped diapers`() = runTest {
        val sharingRepository = mockk<com.babytracker.sharing.domain.repository.SharingRepository>(relaxed = true)
        val diaperRepo = mockk<DiaperRepository>()
        val sources = mockk<SnapshotSources>()
        every { sources.diaper } returns diaperRepo
        every { diaperRepo.observeAll() } returns flowOf(
            listOf(
                DiaperChange(id = 1, timestamp = Instant.ofEpochMilli(10), type = DiaperType.WET, createdAt = Instant.ofEpochMilli(10)),
            ),
        )
        val settings = mockk<com.babytracker.domain.repository.SettingsRepository>(relaxed = true)
        every { settings.getAppMode() } returns flowOf(com.babytracker.sharing.domain.model.AppMode.PRIMARY)
        every { settings.getShareCode() } returns flowOf("ABCD")
        val sleepSettings = mockk<com.babytracker.domain.repository.SleepSettingsRepository>(relaxed = true)

        val useCase = SyncToFirestoreUseCase(sharingRepository, settings, sleepSettings, sources) { Instant.ofEpochMilli(99) }

        val captured = slot<List<DiaperSnapshot>>()
        coEvery { sharingRepository.syncDiapers(any(), capture(captured)) } returns Unit

        useCase(SyncToFirestoreUseCase.SyncType.DIAPERS)

        assertEquals(1, captured.captured.size)
        assertEquals("WET", captured.captured.first().type)
        coVerify { sharingRepository.syncDiapers(any(), any()) }
    }
}
```

> If the existing sync tests use a shared fake/builder for `SnapshotSources` and settings, follow that pattern instead of re-mocking here.

- [ ] **Step 4: Run** `./gradlew test --tests "com.babytracker.sharing.usecase.SyncToFirestoreDiapersTest"` — expect PASS.
- [ ] **Step 5: Commit** `feat(sharing): sync diapers in full and targeted snapshots`

---

### Task 5: Trigger sync from the diaper use cases

**Files:**
- Modify: `app/src/main/java/com/babytracker/domain/usecase/diaper/LogDiaperChangeUseCase.kt`
- Modify: `app/src/main/java/com/babytracker/domain/usecase/diaper/EditDiaperChangeUseCase.kt`
- Modify: `app/src/main/java/com/babytracker/domain/usecase/diaper/DeleteDiaperChangeUseCase.kt`
- Modify: the three corresponding tests under `app/src/test/java/com/babytracker/domain/usecase/diaper/`

- [ ] **Step 1:** Inject `SyncToFirestoreUseCase` into each use case and fire a targeted sync after the mutation, mirroring `LogBottleFeedUseCase` (`runCatching` so a sync failure never fails the local write). Example for `LogDiaperChangeUseCase`:

```kotlin
class LogDiaperChangeUseCase @Inject constructor(
    private val repository: DiaperRepository,
    private val syncToFirestore: SyncToFirestoreUseCase,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(type: DiaperType, timestamp: Instant, notes: String? = null): Long {
        require(!timestamp.isAfter(now())) { "Diaper time cannot be in the future" }
        val id = repository.insert(
            DiaperChange(
                timestamp = timestamp,
                type = type,
                notes = notes?.takeIf { it.isNotBlank() },
                createdAt = now(),
            ),
        )
        runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.DIAPERS) }
        return id
    }
}
```

Apply the same `syncToFirestore` injection + `runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.DIAPERS) }` (after `update` / `deleteById`) in `EditDiaperChangeUseCase` and `DeleteDiaperChangeUseCase` (add the import `com.babytracker.sharing.usecase.SyncToFirestoreUseCase`).

- [ ] **Step 2:** Update the plan-2 tests to pass a relaxed `SyncToFirestoreUseCase` mock to each constructor, e.g.:

```kotlin
private val sync = mockk<SyncToFirestoreUseCase>(relaxed = true)
// ...
useCase = LogDiaperChangeUseCase(repository, sync) { fixedNow }
```

Add a verification to one test per use case: `coVerify { sync(SyncToFirestoreUseCase.SyncType.DIAPERS) }` after a successful mutation.

- [ ] **Step 3: Run** `./gradlew test --tests "com.babytracker.domain.usecase.diaper.*"` — expect PASS.
- [ ] **Step 4: Commit** `feat(diaper): sync to partner after log, edit, and delete`

---

### Task 6: `PartnerDiaperCard` on the dashboard

**Files:**
- Create: `app/src/main/java/com/babytracker/ui/partner/PartnerDiaperCard.kt`
- Modify: `app/src/main/java/com/babytracker/ui/partner/PartnerDashboardScreen.kt`

- [ ] **Step 1:** Create the read-only card (computes today count + last change from `snapshot.diapers`):

```kotlin
package com.babytracker.ui.partner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babytracker.sharing.domain.model.DiaperSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val diaperTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

@Composable
fun PartnerDiaperCard(
    diapers: List<DiaperSnapshot>,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val today = Instant.now().atZone(zone).toLocalDate()
    val todayCount = diapers.count { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == today }
    val last = diapers.maxByOrNull { it.timestamp }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🧷 Diapers", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (todayCount == 1) "1 change today" else "$todayCount changes today",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            last?.let {
                val time = diaperTimeFormatter.format(
                    Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalTime(),
                )
                Text(
                    text = "Last: ${it.type.lowercase().replaceFirstChar(Char::uppercase)} at $time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}
```

- [ ] **Step 2:** In `PartnerDashboardScreen.kt`, render the card in the snapshot content (where the other per-domain cards such as the feeding/sleep cards are composed). Find the block that reads `uiState.snapshot` (non-null) and add:

```kotlin
PartnerDiaperCard(diapers = snapshot.diapers)
```

placing it alongside the existing summary cards (e.g., after the feeding card). Match the surrounding spacing/`Modifier` usage.

- [ ] **Step 3: Build** `./gradlew assembleDebug` — expect SUCCESS.
- [ ] **Step 4: Commit** `feat(partner): show diaper summary on the partner dashboard`

---

## Acceptance Criteria

- `./gradlew build` and `./gradlew test` pass.
- Logging/editing/deleting a diaper change (in PRIMARY mode with a share code) pushes the updated `diapers` array to Firestore; a sync failure does not fail the local write.
- A full sync includes diapers; older partner-app readers ignore the new field without error.
- The partner dashboard shows "N changes today" and the last change type + time from the shared snapshot.
- All diaper work in the sharing layer stays within `sharing/` and the partner UI within `ui/partner/`.

## Self-Review Notes

- Spec coverage: snapshot field, domain mapper, Firestore map round-trip, targeted + full sync, use-case triggers, partner card — all covered.
- Type consistency: `DiaperSnapshot(timestamp: Long, type: String, notes: String?)` is used identically in `ShareSnapshot`, `DomainToSnapshot`, `FirestoreSnapshotMapping`, `SharingRepository.syncDiapers`, and `PartnerDiaperCard`. `SyncType.DIAPERS` is referenced by the use cases.
- Cross-plan note: this plan changes the plan-2 use-case constructors (adds `SyncToFirestoreUseCase`), so the plan-2 unit tests are updated here in Task 5 — do not skip that step or those tests won't compile.
- `tertiaryContainer`/`onTertiaryContainer` are standard M3 tokens.
