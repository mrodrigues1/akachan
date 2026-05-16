# Task 5 — Inventory use cases + sharing fields + sync trigger

> Part of the [Pumping & Inventory implementation plan](../2026-05-16-pumping-and-inventory-overview.md). Implement first, then write tests, then commit.

**Goal:** Add inventory mutations and wire them to the existing Firestore sync channel. Every mutation that changes the inventory summary triggers `SyncToFirestoreUseCase` with the new summary so the partner snapshot never goes stale.

**Depends on:** Task 3 (InventoryRepository).

## Files

- Modify: `app/src/main/java/com/babytracker/sharing/domain/model/ShareSnapshot.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/domain/model/DomainToSnapshot.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/data/firebase/FirestoreSharingService.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/data/repository/SharingRepositoryImpl.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/domain/repository/SharingRepository.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/usecase/SyncToFirestoreUseCase.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/usecase/GenerateShareCodeUseCase.kt`
- Create: `app/src/main/java/com/babytracker/domain/usecase/inventory/AddMilkBagUseCase.kt`
- Create: `app/src/main/java/com/babytracker/domain/usecase/inventory/MarkBagUsedUseCase.kt`
- Create: `app/src/main/java/com/babytracker/domain/usecase/inventory/DeleteMilkBagUseCase.kt`
- Create: `app/src/main/java/com/babytracker/domain/usecase/inventory/GetInventoryUseCase.kt`
- Create: `app/src/main/java/com/babytracker/domain/usecase/inventory/GetInventorySummaryUseCase.kt`
- Tests under `app/src/test/java/com/babytracker/domain/usecase/inventory/`
- Tests under `app/src/test/java/com/babytracker/sharing/...`

## Implementation

### Step 1: Extend `ShareSnapshot.kt`

Append three nullable fields. Existing fields stay where they are.

```kotlin
data class ShareSnapshot(
    val lastSyncAt: Instant,
    val baby: BabySnapshot,
    val sessions: List<SessionSnapshot>,
    val sleepRecords: List<SleepSnapshot>,
    val inventoryTotalMl: Int? = null,
    val inventoryBagCount: Int? = null,
    val inventoryUpdatedAt: Long? = null,
)
```

### Step 2: Extend `DomainToSnapshot.kt`

Add an `InventorySummary` → snapshot-fields helper:

```kotlin
import com.babytracker.domain.model.InventorySummary

data class InventorySnapshotFields(
    val totalMl: Int,
    val bagCount: Int,
    val updatedAtMs: Long,
)

fun InventorySummary.toSnapshotFields(updatedAtMs: Long): InventorySnapshotFields =
    InventorySnapshotFields(
        totalMl = totalMl,
        bagCount = bagCount,
        updatedAtMs = updatedAtMs,
    )
```

The DTO keeps `SyncToFirestoreUseCase` decoupled from `InventorySummary`'s internal representation.

### Step 3: Extend `SharingRepository.kt`

Add a method that writes only the three inventory fields:

```kotlin
suspend fun syncInventory(
    code: ShareCode,
    fields: com.babytracker.sharing.domain.model.InventorySnapshotFields,
)
```

### Step 4: Implement in `FirestoreSharingService` + `SharingRepositoryImpl`

> **Critical:** `FirestoreSharingService.fetchSnapshot` reads from the nested `data` map on `shares/{code}` (see existing `syncSessions` / `syncSleepRecords` / `syncBaby` — they all write under `"data"`). The new inventory write **must** follow that pattern, otherwise partner devices will not see the update.

`FirestoreSharingService.syncInventory(code, fields)` writes under `data.*` via `SetOptions.merge()`:

```kotlin
suspend fun syncInventory(code: String, fields: InventorySnapshotFields) {
    val data = mapOf(
        "data" to mapOf(
            "lastSyncAt" to Timestamp.now(),
            "inventoryTotalMl" to fields.totalMl,
            "inventoryBagCount" to fields.bagCount,
            "inventoryUpdatedAt" to fields.updatedAtMs,
        ),
    )
    firestore.collection(SHARES).document(code).set(data, SetOptions.merge()).await()
}
```

Also extend `snapshotToMap` to include the three new fields so `syncFullSnapshot` round-trips them:

```kotlin
private fun snapshotToMap(snapshot: ShareSnapshot): Map<String, Any?> = mapOf(
    "lastSyncAt" to Timestamp(snapshot.lastSyncAt.epochSecond, snapshot.lastSyncAt.nano),
    "baby" to babyToMap(snapshot.baby),
    "sessions" to snapshot.sessions.map { sessionToMap(it) },
    "sleepRecords" to snapshot.sleepRecords.map { sleepToMap(it) },
    "inventoryTotalMl" to snapshot.inventoryTotalMl,
    "inventoryBagCount" to snapshot.inventoryBagCount,
    "inventoryUpdatedAt" to snapshot.inventoryUpdatedAt,
)
```

Extend `mapToSnapshot` to read them back. Casts must accept both `Int` and `Long` from Firestore (the SDK stores small numbers as `Long`):

```kotlin
return ShareSnapshot(
    lastSyncAt = lastSyncAt,
    baby = baby,
    sessions = sessions,
    sleepRecords = sleepRecords,
    inventoryTotalMl = (data["inventoryTotalMl"] as? Number)?.toInt(),
    inventoryBagCount = (data["inventoryBagCount"] as? Number)?.toInt(),
    inventoryUpdatedAt = (data["inventoryUpdatedAt"] as? Number)?.toLong(),
)
```

`SharingRepositoryImpl.syncInventory(...)` delegates to the service. Mirror the existing `syncSessions` / `syncSleepRecords` pattern (look at the file for exact return type / error handling).

### Step 5: Extend `SyncToFirestoreUseCase.kt`

Add `INVENTORY` to the enum and a private `syncInventory(code)` branch. Inject `InventoryRepository` and a `() -> Instant` clock:

```kotlin
class SyncToFirestoreUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
    private val babyRepository: BabyRepository,
    private val breastfeedingRepository: BreastfeedingRepository,
    private val sleepRepository: SleepRepository,
    private val inventoryRepository: InventoryRepository,
    private val now: () -> Instant,
) {
    enum class SyncType { FULL, SESSIONS, SLEEP_RECORDS, BABY, INVENTORY }
    // ...
    when (syncType) {
        SyncType.FULL -> syncFull(code)
        SyncType.SESSIONS -> syncSessions(code)
        SyncType.SLEEP_RECORDS -> syncSleepRecords(code)
        SyncType.BABY -> syncBaby(code)
        SyncType.INVENTORY -> syncInventory(code)
    }
    // ...
    private suspend fun syncInventory(code: ShareCode) {
        val summary = inventoryRepository.currentSummary()
        sharingRepository.syncInventory(
            code,
            summary.toSnapshotFields(updatedAtMs = now().toEpochMilli()),
        )
    }
}
```

Also extend `syncFull(code)` to populate the three new fields on the full `ShareSnapshot` it sends:

```kotlin
val summary = inventoryRepository.currentSummary()
val updatedAtMs = now().toEpochMilli()
sharingRepository.syncFullSnapshot(
    code,
    ShareSnapshot(
        lastSyncAt = Instant.ofEpochMilli(updatedAtMs),
        baby = baby?.toSnapshot() ?: BabySnapshot("", 0L, emptyList()),
        sessions = sessions.map { it.toSnapshot() },
        sleepRecords = sleepRecords.map { it.toSnapshot() },
        inventoryTotalMl = summary.totalMl,
        inventoryBagCount = summary.bagCount,
        inventoryUpdatedAt = updatedAtMs,
    ),
)
```

### Step 5b: Update `GenerateShareCodeUseCase.kt`

The use case builds the **initial** share snapshot before any incremental sync runs. Without an update here, a primary user with pre-existing milk bags creates a share document with `inventoryTotalMl = null` etc., and partner totals stay blank until the next inventory mutation.

Inject `InventoryRepository` and populate the three fields in `buildSnapshot()`:

```kotlin
class GenerateShareCodeUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
    private val babyRepository: BabyRepository,
    private val breastfeedingRepository: BreastfeedingRepository,
    private val sleepRepository: SleepRepository,
    private val inventoryRepository: InventoryRepository,
    private val now: () -> Instant,
) {
    // invoke() / generateUniqueCode() unchanged

    private suspend fun buildSnapshot(): ShareSnapshot {
        val baby = babyRepository.getBabyProfile().first()
        val sessions = breastfeedingRepository.getRecentSessions(SYNC_LIMIT)
        val sleepRecords = sleepRepository.getRecentRecords(SYNC_LIMIT)
        val summary = inventoryRepository.currentSummary()
        val timestamp = now()
        return ShareSnapshot(
            lastSyncAt = timestamp,
            baby = baby?.toSnapshot() ?: BabySnapshot("", 0L, emptyList()),
            sessions = sessions.map { it.toSnapshot() },
            sleepRecords = sleepRecords.map { it.toSnapshot() },
            inventoryTotalMl = summary.totalMl,
            inventoryBagCount = summary.bagCount,
            inventoryUpdatedAt = timestamp.toEpochMilli(),
        )
    }
}
```

Replace the hard-coded `Instant.now()` with the injected `now: () -> Instant` so the test can pin a deterministic timestamp. `DatabaseModule.provideNowProvider()` is already in the graph.

### Step 6: Inventory use cases

All three mutation use cases call `syncToFirestore(SyncType.INVENTORY)` after a successful repository write. The `runCatching` wrapper mirrors what `BreastfeedingViewModel` does — sync failures must not roll back the local mutation.

`AddMilkBagUseCase.kt`:

```kotlin
package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import java.time.Instant
import javax.inject.Inject

class AddMilkBagUseCase @Inject constructor(
    private val repository: InventoryRepository,
    private val syncToFirestore: SyncToFirestoreUseCase,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(
        collectionDate: Instant,
        volumeMl: Int,
        sourceSessionId: Long? = null,
        notes: String? = null,
    ): Long {
        require(volumeMl > 0) { "Volume must be greater than 0" }
        val bag = MilkBag(
            collectionDate = collectionDate,
            volumeMl = volumeMl,
            sourceSessionId = sourceSessionId,
            notes = notes,
            createdAt = now(),
        )
        val id = repository.insert(bag)
        runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.INVENTORY) }
        return id
    }
}
```

`MarkBagUsedUseCase.kt`:

```kotlin
package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import java.time.Instant
import javax.inject.Inject

class MarkBagUsedUseCase @Inject constructor(
    private val repository: InventoryRepository,
    private val syncToFirestore: SyncToFirestoreUseCase,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(bag: MilkBag) {
        if (bag.usedAt != null) return
        repository.update(bag.copy(usedAt = now()))
        runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.INVENTORY) }
    }
}
```

`DeleteMilkBagUseCase.kt`:

```kotlin
package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import javax.inject.Inject

class DeleteMilkBagUseCase @Inject constructor(
    private val repository: InventoryRepository,
    private val syncToFirestore: SyncToFirestoreUseCase,
) {
    suspend operator fun invoke(bag: MilkBag) {
        repository.delete(bag)
        runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.INVENTORY) }
    }
}
```

`GetInventoryUseCase.kt`:

```kotlin
package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetInventoryUseCase @Inject constructor(
    private val repository: InventoryRepository,
) {
    operator fun invoke(): Flow<List<MilkBag>> = repository.getActiveBags()
}
```

`GetInventorySummaryUseCase.kt`:

```kotlin
package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetInventorySummaryUseCase @Inject constructor(
    private val repository: InventoryRepository,
) {
    operator fun invoke(): Flow<InventorySummary> = repository.getSummary()
}
```

## Tests

### Inventory use cases

For each of `AddMilkBagUseCase`, `MarkBagUsedUseCase`, `DeleteMilkBagUseCase`:

- Assert `repository.{insert|update|delete}` is invoked once with the expected entity.
- Assert `syncToFirestore(SyncType.INVENTORY)` is invoked **after** the mutation (use `coVerifyOrder`).
- Assert that throwing `syncToFirestore` does not propagate (test that the use case returns normally when sync throws).
- Cover invariants: `AddMilkBagUseCase` rejects `volumeMl <= 0`; `MarkBagUsedUseCase` is a no-op for already-used bags.

Sketch (`AddMilkBagUseCaseTest`):

```kotlin
package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class AddMilkBagUseCaseTest {
    private lateinit var repository: InventoryRepository
    private lateinit var sync: SyncToFirestoreUseCase
    private lateinit var useCase: AddMilkBagUseCase
    private val fixedNow = Instant.parse("2026-05-16T10:00:00Z")

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        sync = mockk(relaxed = true)
        useCase = AddMilkBagUseCase(repository, sync) { fixedNow }
    }

    @Test
    fun `persists bag with createdAt now and syncs inventory`() = runTest {
        val captured = slot<MilkBag>()
        coEvery { repository.insert(capture(captured)) } returns 9L

        val id = useCase(
            collectionDate = Instant.parse("2026-05-16T09:30:00Z"),
            volumeMl = 120,
            sourceSessionId = 4L,
        )

        assertEquals(9L, id)
        assertEquals(fixedNow, captured.captured.createdAt)
        assertNotNull(captured.captured.sourceSessionId)
        coVerifyOrder {
            repository.insert(any())
            sync(SyncToFirestoreUseCase.SyncType.INVENTORY)
        }
    }

    @Test
    fun `rejects non-positive volume`() {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { useCase(fixedNow, volumeMl = 0) }
        }
    }

    @Test
    fun `swallows sync failures`() = runTest {
        coEvery { sync(any()) } throws RuntimeException("network down")
        useCase(fixedNow, volumeMl = 50) // no throw == pass
    }
}
```

### `SyncToFirestoreUseCaseInventoryTest`

Asserts that the new branches behave correctly. Two scenarios:

1. `invoke(SyncType.INVENTORY)` when `AppMode == PRIMARY` and a share code exists: calls `sharingRepository.syncInventory` once with `totalMl`, `bagCount`, `updatedAtMs`.
2. `invoke(SyncType.INVENTORY)` when `AppMode != PRIMARY`: no call to `sharingRepository`.

Also extend `SyncToFirestoreUseCaseTest` (or add a new test) to cover `SyncType.FULL` populating the three inventory fields.

### `DomainToSnapshotTest`

Add `inventorySummary toSnapshotFields populates totalMl, bagCount, updatedAt`.

### `FirestoreSharingServiceInventoryRoundTripTest` (unit, MockK Firestore)

Verifies the write path lands under `data.*` and round-trips through `fetchSnapshot`.

- Stub `FirebaseFirestore` so `set(map, SetOptions.merge())` captures the written map.
- After `syncInventory(code, InventorySnapshotFields(totalMl = 240, bagCount = 3, updatedAtMs = 12_345L))`, assert the captured map equals:

  ```kotlin
  mapOf(
      "data" to mapOf(
          "lastSyncAt" to any<Timestamp>(),
          "inventoryTotalMl" to 240,
          "inventoryBagCount" to 3,
          "inventoryUpdatedAt" to 12_345L,
      ),
  )
  ```

- Separately, stub `get().await()` to return a `DocumentSnapshot` whose `get("data")` returns the above inner map. Call `fetchSnapshot(code)`. Assert `ShareSnapshot.inventoryTotalMl == 240`, `inventoryBagCount == 3`, `inventoryUpdatedAt == 12_345L`.

Reject the write if it falls outside `"data"` — this is the regression the Codex review surfaced.

### `GenerateShareCodeUseCaseInventoryTest`

- Stub `inventoryRepository.currentSummary()` to return `InventorySummary(totalMl = 480, bagCount = 4, oldestBagDate = Instant.parse("2026-05-15T10:00:00Z"))`.
- Capture the `ShareSnapshot` passed to `sharingRepository.syncFullSnapshot`.
- Assert `inventoryTotalMl == 480`, `inventoryBagCount == 4`, `inventoryUpdatedAt == fixedNow.toEpochMilli()`.

Covers the "primary user adds milk bags before enabling sharing" path.

## Verify

```
./gradlew ktlintFormat
./gradlew detekt
./gradlew test --tests "com.babytracker.domain.usecase.inventory.*"
./gradlew test --tests "com.babytracker.sharing.*"
```

Expected: all green.

## Commit (two commits)

```
feat(sharing): add inventory summary fields to ShareSnapshot

Extends ShareSnapshot with inventoryTotalMl, inventoryBagCount, and
inventoryUpdatedAt. Adds a syncInventory path to SyncToFirestoreUseCase
and SharingRepository that writes only the three fields.
```

```
feat(inventory): add milk bag use cases

Introduces AddMilkBagUseCase, MarkBagUsedUseCase, DeleteMilkBagUseCase,
GetInventoryUseCase, and GetInventorySummaryUseCase. Each mutation
triggers SyncToFirestoreUseCase(INVENTORY) so the partner snapshot
stays consistent after every change.
```
