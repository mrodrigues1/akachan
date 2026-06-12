# Plan 02: ShareSnapshot feed fields + milk bag snapshot

LINEAR_ISSUE: [AKA-118](https://linear.app/akachan/issue/AKA-118/spec-007-plan-02-sharesnapshot-feed-fields-milk-bag-snapshot)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand the published partner snapshot so the partner can (later) edit its own entries and pick milk bags from the stash: `BottleFeedSnapshot` gains `clientId`/`author`/`notes`, and the snapshot gains an active-bags list (`milkBags`) carried by the existing `INVENTORY` sync type (SPEC-007 "Snapshot" section).

**Architecture:** Pure snapshot-pipeline change on the primary's publish path plus the partner's fetch path. No new sync types, no Firestore listeners, no UI. Old snapshot documents (without the new fields) must still parse — fetch-side mapping defaults `clientId` to `""` and `author` to `"OWNER"`.

**Tech Stack:** Kotlin, Firebase Firestore KTX (map-based serialization, existing style), JUnit 5 + MockK.

**Spec:** `specs/SPEC-007-PARTNER-BOTTLE-FEED-LOGGING.md` — "Snapshot (`ShareSnapshot`)" section.

**Dependencies:** Plan 01 (`BottleFeed.clientId`/`author` must exist).

**Suggested branch:** `feat/share-snapshot-feed-fields`

---

## File Structure

- Modify: `app/src/main/java/com/babytracker/sharing/domain/model/ShareSnapshot.kt` — extend `BottleFeedSnapshot`, add `MilkBagSnapshot`, add `ShareSnapshot.milkBags`.
- Modify: `app/src/main/java/com/babytracker/sharing/domain/model/DomainToSnapshot.kt` — extend `BottleFeed.toSnapshot()`, add `MilkBag.toSnapshot()`.
- Modify: `app/src/main/java/com/babytracker/sharing/domain/repository/SharingRepository.kt` — `syncInventory` gains `milkBags` param.
- Modify: `app/src/main/java/com/babytracker/sharing/data/repository/SharingRepositoryImpl.kt` — pass-through.
- Modify: `app/src/main/java/com/babytracker/sharing/data/firebase/FirestoreSharingService.kt` — map serialization both directions.
- Modify: `app/src/main/java/com/babytracker/sharing/usecase/SyncToFirestoreUseCase.kt` — `FULL` and `INVENTORY` paths supply active bags.
- Test: `app/src/test/java/com/babytracker/sharing/domain/model/DomainToSnapshotTest.kt` — extend.
- Test: `app/src/test/java/com/babytracker/sharing/data/firebase/FirestoreSnapshotMappingTest.kt` — NEW round-trip test.
- Test: existing `SyncToFirestoreUseCaseTest` / `SyncToFirestoreUseCaseInventoryTest` / `SharingRepositoryImplTest` — update for new signature/fields.

---

### Task 1: Snapshot models

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/domain/model/ShareSnapshot.kt`

- [ ] **Step 1: Extend `BottleFeedSnapshot` and add `MilkBagSnapshot`**

```kotlin
data class BottleFeedSnapshot(
    val timestamp: Long,
    val volumeMl: Int,
    val type: String,
    val clientId: String = "",
    val author: String = "OWNER",
    val notes: String? = null,
)

data class MilkBagSnapshot(
    val id: Long,
    val collectionDateMs: Long,
    val volumeMl: Int,
    val notes: String?,
)
```

Defaults on `BottleFeedSnapshot` keep old persisted snapshots and existing test fixtures compiling/parsing; `""`/`"OWNER"` mean "legacy entry, not partner-editable".

- [ ] **Step 2: Add `milkBags` to `ShareSnapshot`**

```kotlin
data class ShareSnapshot(
    val lastSyncAt: Instant,
    val baby: BabySnapshot,
    val sessions: List<SessionSnapshot>,
    val sleepRecords: List<SleepSnapshot>,
    val bottleFeeds: List<BottleFeedSnapshot> = emptyList(),
    val inventoryTotalMl: Int? = null,
    val inventoryBagCount: Int? = null,
    val inventoryUpdatedAt: Long? = null,
    val milkBags: List<MilkBagSnapshot> = emptyList(),
    val sleepPrediction: SleepPredictionSnapshot? = null,
)
```

`milkBags` contains **active bags only** (spec): bags live only on the primary, so the primary's Room `Long` id is a stable opaque reference the partner echoes back in `consumedBagId`.

### Task 2: Domain → snapshot conversions

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/domain/model/DomainToSnapshot.kt`

- [ ] **Step 1: Carry the new feed fields**

```kotlin
fun BottleFeed.toSnapshot(): BottleFeedSnapshot = BottleFeedSnapshot(
    timestamp = timestamp.toEpochMilli(),
    volumeMl = volumeMl,
    type = type.name,
    clientId = clientId,
    author = author.name,
    notes = notes,
)
```

- [ ] **Step 2: Add `MilkBag.toSnapshot()`** (same file, alongside the others; add `MilkBag` import)

```kotlin
fun MilkBag.toSnapshot(): MilkBagSnapshot = MilkBagSnapshot(
    id = id,
    collectionDateMs = collectionDate.toEpochMilli(),
    volumeMl = volumeMl,
    notes = notes,
)
```

### Task 3: Repository signature + sync use case

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/domain/repository/SharingRepository.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/data/repository/SharingRepositoryImpl.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/usecase/SyncToFirestoreUseCase.kt`

- [ ] **Step 1: Interface** — extend `syncInventory`:

```kotlin
suspend fun syncInventory(
    code: ShareCode,
    fields: InventorySnapshotFields,
    milkBags: List<MilkBagSnapshot>,
)
```

(import `MilkBagSnapshot`; impl passes through to the service: `service.syncInventory(code.value, fields, milkBags)`).

- [ ] **Step 2: `SyncToFirestoreUseCase.syncInventory`** — supply active bags (the `InventoryRepository.getActiveBags(): Flow<List<MilkBag>>` accessor already exists; `SnapshotSources.inventory` exposes it):

```kotlin
private suspend fun syncInventory(code: ShareCode) {
    val summary = sources.inventory.currentSummary()
    val activeBags = sources.inventory.getActiveBags().first()
    sharingRepository.syncInventory(
        code,
        summary.toSnapshotFields(updatedAtMs = now().toEpochMilli()),
        activeBags.map { it.toSnapshot() },
    )
}
```

- [ ] **Step 3: `syncFull`** — include `milkBags` in the built `ShareSnapshot`:

```kotlin
val activeBags = sources.inventory.getActiveBags().first()
// ...
ShareSnapshot(
    // ...existing args,
    milkBags = activeBags.map { it.toSnapshot() },
    sleepPrediction = prediction,
)
```

- [ ] **Step 4: Compile** — `./gradlew :app:compileDebugKotlin`. Expected: production code compiles; test sources break (fixed in Task 5).

### Task 4: Firestore serialization (both directions)

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/data/firebase/FirestoreSharingService.kt`

- [ ] **Step 1: Widen mapping visibility for testability.** Change the snapshot mapping functions used below (`snapshotToMap`, `mapToSnapshot`, `bottleFeedToMap`, `mapToBottleFeed`, and the new milk-bag pair) from `private` to `internal`. They stay methods on the service; a unit test can then round-trip maps with a `FirestoreSharingService(mockk(), mockk())` without touching Firestore. (No Mapper class — anti-goal; these remain functions on the existing service.)

- [ ] **Step 2: Feed maps carry the new fields**

```kotlin
internal fun bottleFeedToMap(feed: BottleFeedSnapshot): Map<String, Any?> = mapOf(
    "timestamp" to feed.timestamp,
    "volumeMl" to feed.volumeMl,
    "type" to feed.type,
    "clientId" to feed.clientId,
    "author" to feed.author,
    "notes" to feed.notes,
)

internal fun mapToBottleFeed(map: Map<*, *>): BottleFeedSnapshot = BottleFeedSnapshot(
    timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
    volumeMl = (map["volumeMl"] as? Number)?.toInt() ?: 0,
    type = map["type"] as? String ?: "FORMULA",
    clientId = map["clientId"] as? String ?: "",
    author = map["author"] as? String ?: "OWNER",
    notes = map["notes"] as? String,
)
```

Fallbacks (`""`, `"OWNER"`) are the legacy-document path: a snapshot pushed before this PR has no `clientId`/`author` keys and must still parse.

- [ ] **Step 3: Milk bag maps**

```kotlin
internal fun milkBagToMap(bag: MilkBagSnapshot): Map<String, Any?> = mapOf(
    "id" to bag.id,
    "collectionDateMs" to bag.collectionDateMs,
    "volumeMl" to bag.volumeMl,
    "notes" to bag.notes,
)

internal fun mapToMilkBag(map: Map<*, *>): MilkBagSnapshot = MilkBagSnapshot(
    id = (map["id"] as? Number)?.toLong() ?: 0L,
    collectionDateMs = (map["collectionDateMs"] as? Number)?.toLong() ?: 0L,
    volumeMl = (map["volumeMl"] as? Number)?.toInt() ?: 0,
    notes = map["notes"] as? String,
)
```

- [ ] **Step 4: Wire into snapshot + inventory sync**

In `snapshotToMap`: add `"milkBags" to snapshot.milkBags.map { milkBagToMap(it) },` after the inventory fields.

In `mapToSnapshot`: parse like the other lists —

```kotlin
val milkBags = (data["milkBags"] as? List<*>)
    ?.filterIsInstance<Map<*, *>>()
    ?.map { mapToMilkBag(it) }
    .orEmpty()
```

and pass `milkBags = milkBags` to the returned `ShareSnapshot`.

`syncInventory` gains the bags param and writes them in the same merge:

```kotlin
suspend fun syncInventory(
    code: String,
    fields: InventorySnapshotFields,
    milkBags: List<MilkBagSnapshot>,
) {
    val data = mapOf(
        "data" to mapOf(
            "lastSyncAt" to Timestamp.now(),
            "inventoryTotalMl" to fields.totalMl,
            "inventoryBagCount" to fields.bagCount,
            "inventoryUpdatedAt" to fields.updatedAtMs,
            "milkBags" to milkBags.map { milkBagToMap(it) },
        ),
    )
    firestore.collection(SHARES).document(code).set(data, SetOptions.merge()).await()
}
```

Import `MilkBagSnapshot`.

### Task 5: Tests

**Files:**
- Modify: `app/src/test/java/com/babytracker/sharing/domain/model/DomainToSnapshotTest.kt`
- Create: `app/src/test/java/com/babytracker/sharing/data/firebase/FirestoreSnapshotMappingTest.kt`
- Modify: `app/src/test/java/com/babytracker/sharing/usecase/SyncToFirestoreUseCaseTest.kt`, `SyncToFirestoreUseCaseInventoryTest.kt`, `app/src/test/java/com/babytracker/sharing/data/repository/SharingRepositoryImplTest.kt` — adapt to the new `syncInventory` signature (add a `relaxed` arg or explicit `emptyList()` expectations) and new `BottleFeed` fields; also stub `getActiveBags()` (`every { inventory.getActiveBags() } returns flowOf(emptyList())`) wherever `SyncType.FULL`/`INVENTORY` runs.

- [ ] **Step 1: Conversion tests** — extend `DomainToSnapshotTest`:

```kotlin
@Test
fun `bottle feed snapshot carries clientId author and notes`() {
    val feed = BottleFeed(
        id = 1,
        clientId = "client-1",
        timestamp = Instant.ofEpochMilli(1000),
        volumeMl = 120,
        type = FeedType.BREAST_MILK,
        notes = "after nap",
        createdAt = Instant.ofEpochMilli(900),
        author = FeedAuthor.PARTNER,
    )

    val snapshot = feed.toSnapshot()

    assertEquals("client-1", snapshot.clientId)
    assertEquals("PARTNER", snapshot.author)
    assertEquals("after nap", snapshot.notes)
}

@Test
fun `milk bag snapshot maps id date volume and notes`() {
    val bag = MilkBag(
        id = 7,
        collectionDate = Instant.ofEpochMilli(5000),
        volumeMl = 150,
        notes = "freezer",
        createdAt = Instant.ofEpochMilli(4000),
    )

    val snapshot = bag.toSnapshot()

    assertEquals(MilkBagSnapshot(id = 7, collectionDateMs = 5000, volumeMl = 150, notes = "freezer"), snapshot)
}
```

- [ ] **Step 2: Round-trip test** (the spec's "Snapshot round-trip with new fields" requirement):

```kotlin
package com.babytracker.sharing.data.firebase

import com.babytracker.sharing.domain.model.BabySnapshot
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class FirestoreSnapshotMappingTest {
    private val service = FirestoreSharingService(mockk(), mockk())

    @Test
    fun `snapshot with new feed fields and milk bags round-trips through map`() {
        val snapshot = ShareSnapshot(
            lastSyncAt = Instant.ofEpochSecond(100),
            baby = BabySnapshot("Aiko", 1000L, listOf("dairy")),
            sessions = emptyList(),
            sleepRecords = emptyList(),
            bottleFeeds = listOf(
                BottleFeedSnapshot(
                    timestamp = 2000L,
                    volumeMl = 120,
                    type = "BREAST_MILK",
                    clientId = "client-1",
                    author = "PARTNER",
                    notes = "evening",
                ),
            ),
            milkBags = listOf(MilkBagSnapshot(id = 7, collectionDateMs = 5000L, volumeMl = 150, notes = null)),
        )

        val roundTripped = service.mapToSnapshot(service.snapshotToMap(snapshot))

        assertEquals(snapshot.bottleFeeds, roundTripped.bottleFeeds)
        assertEquals(snapshot.milkBags, roundTripped.milkBags)
    }

    @Test
    fun `legacy bottle feed map without new keys parses with safe defaults`() {
        val legacy = mapOf("timestamp" to 2000L, "volumeMl" to 120, "type" to "FORMULA")

        val parsed = service.mapToBottleFeed(legacy)

        assertEquals("", parsed.clientId)
        assertEquals("OWNER", parsed.author)
        assertEquals(null, parsed.notes)
    }
}
```

- [ ] **Step 3: Run sharing tests**

Run: `./gradlew test --tests "com.babytracker.sharing.*"`
Expected: PASS

### Task 6: Full validation + commit

- [ ] **Step 1:** `./gradlew test` — Expected: PASS
- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat(sharing): publish clientId, author, notes and active milk bags in snapshot"
```

---

## Acceptance Criteria

- [ ] `BottleFeedSnapshot` carries `clientId`, `author`, `notes`; old maps without those keys parse with `""`/`"OWNER"`/`null`.
- [ ] `MilkBagSnapshot` exists; `ShareSnapshot.milkBags` defaults to empty and is populated with **active bags only** by both `FULL` and `INVENTORY` sync paths.
- [ ] No new sync types; `INVENTORY` carries bags alongside existing totals in one merged write.
- [ ] Round-trip unit test (`snapshotToMap` → `mapToSnapshot`) proves field fidelity for feeds + bags; legacy-document defaults proven.
- [ ] Full unit suite green.

## Firestore 1 MB note

The snapshot doc gains ~3 short fields per feed (max 20 feeds synced — `SYNC_LIMIT`) and one small map per active bag. Pre-existing ceiling per spec ("no approach change") — no mitigation in this plan.
