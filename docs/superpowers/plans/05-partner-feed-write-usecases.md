# Plan 05: Partner write path — log/edit/delete ops + history merge

LINEAR_ISSUE: [AKA-121](https://linear.app/akachan/issue/AKA-121/spec-007-plan-05-partner-write-path-logeditdelete-ops-history-merge)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The partner device writes bottle-feed operation documents to `shares/{code}/feedOps` (create/edit/delete) and produces a merged feeding history (snapshot + own pending ops) for optimistic UI (SPEC-007 "Sync flows → Partner (write path)").

**Architecture:** Use cases only — no UI (Plan 06). Each use case writes exactly one op doc; Firestore's built-in offline persistence queues writes transparently, so op writes are **fire-and-forget** (never `await()` the write task — it only completes on server ack and would hang offline). History merging is a pure function over `snapshot.bottleFeeds` + the partner's own pending ops (listener filtered to own uid; local pending writes surface immediately through the listener).

**Tech Stack:** Kotlin Coroutines + Flow, Firestore KTX, Hilt, JUnit 5 + MockK + Turbine.

**Spec:** `specs/SPEC-007-PARTNER-BOTTLE-FEED-LOGGING.md` — "Sync flows → Partner", "Firestore op document", "Error handling", "Testing".

**Dependencies:** Plan 02 (snapshot `clientId`/`author`/`notes`, `MilkBagSnapshot`), Plan 04 (`FeedOp` model + `mapToFeedOp`; if implemented before Plan 04, lift `FeedOp.kt` and the mapper from that plan first). Plan 03's rules must be deployed for runtime.

**Suggested branch:** `feat/partner-feed-write-usecases`

---

## File Structure

- Create: `app/src/main/java/com/babytracker/sharing/usecase/LogPartnerFeedUseCase.kt`
- Create: `app/src/main/java/com/babytracker/sharing/usecase/EditPartnerFeedUseCase.kt`
- Create: `app/src/main/java/com/babytracker/sharing/usecase/DeletePartnerFeedUseCase.kt`
- Create: `app/src/main/java/com/babytracker/sharing/usecase/ObservePartnerFeedHistoryUseCase.kt`
- Create: `app/src/main/java/com/babytracker/sharing/domain/model/MergeFeedHistory.kt` — pure merge function.
- Modify: `app/src/main/java/com/babytracker/sharing/data/firebase/FirestoreSharingService.kt` — `writeFeedOp`, `observeOwnFeedOps`, `feedOpToMap`.
- Modify: `app/src/main/java/com/babytracker/sharing/domain/repository/SharingRepository.kt` + `SharingRepositoryImpl.kt`.
- Test: `app/src/test/java/com/babytracker/sharing/usecase/LogPartnerFeedUseCaseTest.kt`, `EditPartnerFeedUseCaseTest.kt`, `DeletePartnerFeedUseCaseTest.kt`; `app/src/test/java/com/babytracker/sharing/domain/model/MergeFeedHistoryTest.kt`.

---

### Task 1: Firestore service — write + own-ops listener

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/data/firebase/FirestoreSharingService.kt`

- [ ] **Step 1: Serialize an op** (counterpart of Plan 04's `mapToFeedOp`; `internal` for round-trip testing):

```kotlin
internal fun feedOpToMap(op: FeedOp): Map<String, Any?> = buildMap {
    put("action", op.action.name.lowercase())
    put("entryClientId", op.entryClientId)
    put("authorUid", op.authorUid)
    put("createdAtMs", op.createdAtMs)
    op.timestampMs?.let { put("timestampMs", it) }
    op.volumeMl?.let { put("volumeMl", it) }
    op.type?.let { put("type", it) }
    op.notes?.let { put("notes", it) }
    op.consumedBagId?.let { put("consumedBagId", it) }
}
```

`buildMap` + conditional puts matters: Plan 03's rules validate with `keys().hasOnly(...)` and require `consumedBagId`/payload keys to be absent for actions that don't carry them — writing explicit `null`s would still create the keys and be denied.

- [ ] **Step 2: Fire-and-forget write** (NOT suspend on the task — offline queueing):

```kotlin
fun writeFeedOp(code: String, op: FeedOp) {
    firestore.collection(SHARES).document(code)
        .collection(FEED_OPS).document(op.opId)
        .set(feedOpToMap(op))
    // intentionally not awaited: Firestore's offline persistence queues the write;
    // the task only completes on server ack and would hang offline use cases.
}
```

- [ ] **Step 3: Own-pending-ops listener** (drives optimistic merge; `MetadataChanges.INCLUDE` not needed — local pending writes already appear in snapshots):

```kotlin
fun observeOwnFeedOps(code: String, uid: String): Flow<List<FeedOp>> = callbackFlow {
    val registration = firestore.collection(SHARES).document(code)
        .collection(FEED_OPS)
        .whereEqualTo("authorUid", uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { mapToFeedOp(it.id, it.data ?: return@mapNotNull null) })
            }
        }
    awaitClose { registration.remove() }
}
```

Note: rules from Plan 03 only allow reading own ops while connected — a `PERMISSION_DENIED` listener error here is the revocation signal (Task 4).

- [ ] **Step 4: Repository surface** — add to `SharingRepository` + impl pass-throughs:

```kotlin
fun writeFeedOp(code: ShareCode, op: FeedOp)
fun observeOwnFeedOps(code: ShareCode, uid: String): Flow<List<FeedOp>>
```

### Task 2: Partner write use cases

**Files:**
- Create: `LogPartnerFeedUseCase.kt`, `EditPartnerFeedUseCase.kt`, `DeletePartnerFeedUseCase.kt` (all in `app/src/main/java/com/babytracker/sharing/usecase/`)

- [ ] **Step 1: `LogPartnerFeedUseCase`** — mirrors `LogBottleFeedUseCase`'s validation; one CREATE op:

```kotlin
package com.babytracker.sharing.usecase

import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.repository.SharingRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class LogPartnerFeedUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Instant = Instant::now,
) {
    /** @return the new entry's clientId (for optimistic UI focus/scroll). */
    suspend operator fun invoke(
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        selectedBag: MilkBagSnapshot?,
        notes: String?,
    ): String {
        require(volumeMl > 0) { "Volume must be greater than 0" }
        require(!timestamp.isAfter(now())) { "Feed time cannot be in the future" }
        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        val uid = sharingRepository.signInAnonymously()
        val entryClientId = UUID.randomUUID().toString()
        sharingRepository.writeFeedOp(
            code,
            FeedOp(
                opId = UUID.randomUUID().toString(),
                action = FeedOpAction.CREATE,
                entryClientId = entryClientId,
                authorUid = uid,
                createdAtMs = now().toEpochMilli(),
                timestampMs = timestamp.toEpochMilli(),
                volumeMl = volumeMl,
                type = type.name,
                notes = notes,
                consumedBagId = selectedBag?.id,
            ),
        )
        return entryClientId
    }
}
```

(`signInAnonymously()` reuses the existing anonymous session — same call `FetchPartnerDataUseCase` makes per fetch.)

- [ ] **Step 2: `EditPartnerFeedUseCase`** — defense-in-depth ownership check on top of UI gating and rules; `consumedBagId` is create-only (spec: partner deletes and re-logs to change the bag):

```kotlin
class EditPartnerFeedUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(
        entry: BottleFeedSnapshot,
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        notes: String?,
    ) {
        require(entry.author == FeedAuthor.PARTNER.name) { "Only partner-authored entries can be edited" }
        require(entry.clientId.isNotEmpty()) { "Entry has no clientId" }
        require(volumeMl > 0) { "Volume must be greater than 0" }
        require(!timestamp.isAfter(now())) { "Feed time cannot be in the future" }
        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        val uid = sharingRepository.signInAnonymously()
        sharingRepository.writeFeedOp(
            code,
            FeedOp(
                opId = UUID.randomUUID().toString(),
                action = FeedOpAction.UPDATE,
                entryClientId = entry.clientId,
                authorUid = uid,
                createdAtMs = now().toEpochMilli(),
                timestampMs = timestamp.toEpochMilli(),
                volumeMl = volumeMl,
                type = type.name,
                notes = notes,
            ),
        )
    }
}
```

- [ ] **Step 3: `DeletePartnerFeedUseCase`** — same gating, envelope-only op:

```kotlin
class DeletePartnerFeedUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(entry: BottleFeedSnapshot) {
        require(entry.author == FeedAuthor.PARTNER.name) { "Only partner-authored entries can be deleted" }
        require(entry.clientId.isNotEmpty()) { "Entry has no clientId" }
        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        val uid = sharingRepository.signInAnonymously()
        sharingRepository.writeFeedOp(
            code,
            FeedOp(
                opId = UUID.randomUUID().toString(),
                action = FeedOpAction.DELETE,
                entryClientId = entry.clientId,
                authorUid = uid,
                createdAtMs = now().toEpochMilli(),
            ),
        )
    }
}
```

(Same package/imports as Step 1; add `FeedAuthor` + `BottleFeedSnapshot` imports.)

**Why ownership is an enum, not a uid (anticipated review question):** SPEC-007 explicitly rejects binding entries to the partner's Firebase uid — anonymous uids change on reinstall, which would permanently strand the partner's own entries as uneditable. The enum is the spec's decided trade-off under single-partner scope (multi-partner is out of scope). The residual risk — a second *connected* device editing PARTNER entries — requires the owner to have shared the code with that device; connection is the trust boundary, and revocation (Plan 03 rules) severs it completely. Do not "harden" this with uid checks; it would break the spec's reinstall requirement.

### Task 3: History merge — pure function

**Files:**
- Create: `app/src/main/java/com/babytracker/sharing/domain/model/MergeFeedHistory.kt`

- [ ] **Step 1:** Pending ops override the snapshot per `entryClientId` (optimistic UI; spec "Partner" flow):

```kotlin
package com.babytracker.sharing.domain.model

/**
 * Merges the published snapshot history with the partner's own pending ops.
 * A pending op for an entryClientId overrides the snapshot version of that entry:
 * CREATE adds (or replaces, if the snapshot already caught up), UPDATE rewrites
 * fields, DELETE hides. Ops for the same entry apply in createdAtMs order.
 */
fun mergeFeedHistory(
    snapshotFeeds: List<BottleFeedSnapshot>,
    pendingOps: List<FeedOp>,
): List<BottleFeedSnapshot> {
    val byClientId = snapshotFeeds.associateBy { it.clientId }.toMutableMap()
    pendingOps.sortedBy { it.createdAtMs }.forEach { op ->
        when (op.action) {
            FeedOpAction.CREATE, FeedOpAction.UPDATE -> {
                val timestampMs = op.timestampMs ?: return@forEach
                val volumeMl = op.volumeMl ?: return@forEach
                val type = op.type ?: return@forEach
                byClientId[op.entryClientId] = BottleFeedSnapshot(
                    timestamp = timestampMs,
                    volumeMl = volumeMl,
                    type = type,
                    clientId = op.entryClientId,
                    author = "PARTNER",
                    notes = op.notes,
                )
            }
            FeedOpAction.DELETE -> byClientId.remove(op.entryClientId)
        }
    }
    return byClientId.values.sortedByDescending { it.timestamp }
}
```

Entries with empty `clientId` (legacy snapshot rows) can never collide with an op (`entryClientId` is non-empty by rules + use-case `require`) — `associateBy` keeping the last empty-key entry is acceptable only if at most one legacy row exists; to be safe, keep legacy rows aside:

```kotlin
val (legacy, identified) = snapshotFeeds.partition { it.clientId.isEmpty() }
val byClientId = identified.associateBy { it.clientId }.toMutableMap()
// ...ops loop...
return (byClientId.values + legacy).sortedByDescending { it.timestamp }
```

Use the partition version — it is the correct one.

### Task 4: `ObservePartnerFeedHistoryUseCase`

**Files:**
- Create: `app/src/main/java/com/babytracker/sharing/usecase/ObservePartnerFeedHistoryUseCase.kt`

- [ ] **Step 1:** Combine fetched snapshot with the own-ops listener. The snapshot itself is fetch-based today (no listener on the share doc — out of scope here); the flow refreshes the merge whenever pending ops change, and the caller (Plan 06 ViewModel) re-fetches the snapshot on screen entry/refresh:

```kotlin
package com.babytracker.sharing.usecase

import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.model.mergeFeedHistory
import com.babytracker.sharing.domain.repository.SharingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObservePartnerFeedHistoryUseCase @Inject constructor(
    private val sharingRepository: SharingRepository,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * @param snapshotFeeds the bottleFeeds list from the most recent fetched snapshot
     *        (the ViewModel owns snapshot refresh; this flow re-merges on op changes).
     */
    suspend operator fun invoke(snapshotFeeds: List<BottleFeedSnapshot>): Flow<List<BottleFeedSnapshot>> {
        val code = ShareCode(settingsRepository.getShareCode().first() ?: error("No share code"))
        val uid = sharingRepository.signInAnonymously()
        return sharingRepository.observeOwnFeedOps(code, uid)
            .map { ops -> mergeFeedHistory(snapshotFeeds, ops) }
    }
}
```

Revocation: a `PERMISSION_DENIED` from the listener propagates as a flow error; the Plan 06 ViewModel routes it through the existing `PartnerAccessRevokedException` handling (`clearPartnerStateIfShareCodeMatches` + exit dashboard), same as `FetchPartnerDataUseCase` does for fetch.

**The pending boundary (protocol invariant — read this):** "pending" is defined by document existence, not by a flag. An op doc in `feedOps` has, by protocol, not yet been applied to the primary's Room: Plan 04 deletes every processed op (applied *and* dropped) immediately after the snapshot push, and Plan 03's rules give the owner unconditional delete. So the own-ops listener's current contents are exactly the pending set, and merging all of them is correct. Do NOT add acked/processed flags or `MetadataChanges`-based filtering — a server-acked op that still exists is still pending (the primary hasn't consumed it), and it must keep overriding the snapshot or the partner's own write would visibly revert while the primary is offline/backgrounded.

**Stale-snapshot window (must be handled in Plan 06):** when the primary consumes an op, the op vanishes from the listener *and* the merge falls back to the snapshot the ViewModel fetched **before** the apply — the entry would flicker to its pre-op state until the next fetch. The push-before-delete ordering (Plan 04) guarantees the corrected snapshot is already in Firestore at that moment, so the fix is: **the ViewModel re-fetches the snapshot whenever the pending-op set shrinks.** This contract is part of this use case's documentation and is exercised by a Plan 06 ViewModel test (op disappears → `FetchPartnerDataUseCase` re-invoked → merged history shows the applied state, no flicker).

### Task 5: Tests

**Files:**
- Create: `LogPartnerFeedUseCaseTest.kt`, `EditPartnerFeedUseCaseTest.kt`, `DeletePartnerFeedUseCaseTest.kt` under `app/src/test/java/com/babytracker/sharing/usecase/`
- Create: `app/src/test/java/com/babytracker/sharing/domain/model/MergeFeedHistoryTest.kt`

- [ ] **Step 1: Use case payload tests** (MockK; `slot<FeedOp>()` to capture the written op). Cover per spec:
  - log writes CREATE op with all payload fields, fresh UUIDs (`opId != entryClientId`), `consumedBagId = selectedBag.id`, `authorUid` = signed-in uid; returns `entryClientId`
  - log without bag → `consumedBagId == null`
  - log rejects volume ≤ 0 and future timestamp (`assertThrows<IllegalArgumentException>`)
  - edit writes UPDATE op targeting `entry.clientId`, never carries `consumedBagId`
  - edit/delete refuse OWNER entries and empty `clientId` (`assertThrows<IllegalArgumentException>`, `verify(exactly = 0) { sharingRepository.writeFeedOp(any(), any()) }`)
  - delete writes envelope-only DELETE op (no payload fields set)

- [ ] **Step 2: `MergeFeedHistoryTest`** — pure function, no mocks:
  - pending CREATE appears in merged list
  - pending UPDATE overrides snapshot entry's fields
  - pending DELETE hides snapshot entry
  - two ops on same entry apply in `createdAtMs` order (update then delete → hidden)
  - legacy snapshot rows (empty `clientId`) survive merging untouched, even several of them
  - result sorted by timestamp descending

- [ ] **Step 3: Run**

Run: `./gradlew test --tests "com.babytracker.sharing.*"`
Expected: PASS

### Task 6: Full validation + commit

- [ ] **Step 1:** `./gradlew test` → PASS; `./gradlew build` → PASS.
- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat(sharing): partner bottle feed op write path with optimistic history merge"
```

---

## Acceptance Criteria

- [ ] Each use case writes exactly one op doc; writes are fire-and-forget (offline persistence queues them — no `await()` on the write task).
- [ ] Op maps omit absent keys entirely (`buildMap`) so Plan 03's `hasOnly`/required-keys rules accept them; `consumedBagId` only ever on CREATE.
- [ ] Edit/delete `require(author == PARTNER)` — defense-in-depth under UI gating (Plan 06) and rules (Plan 03).
- [ ] `mergeFeedHistory`: pending op overrides snapshot per `entryClientId`; CREATE adds, UPDATE rewrites, DELETE hides; ops apply in `createdAtMs` order; legacy empty-`clientId` rows preserved.
- [ ] Revocation signal documented: own-ops listener `PERMISSION_DENIED` → existing `PartnerAccessRevokedException` flow (wired in Plan 06).
- [ ] Pending boundary documented as protocol invariant (op existence = pending; primary deletes processed ops per Plan 04); no acked/processed flags introduced.
- [ ] Stale-snapshot window documented with its Plan 06 obligation: re-fetch snapshot when the pending-op set shrinks.
- [ ] All listed unit tests green; full suite + build green.
