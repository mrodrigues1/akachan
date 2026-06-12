# Plan 04: Primary op-inbox — observe, apply, re-publish, delete

LINEAR_ISSUE: [AKA-120](https://linear.app/akachan/issue/AKA-120/spec-007-plan-04-primary-op-inbox-observe-apply-re-publish-delete)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The primary device consumes partner-written op documents from `shares/{code}/feedOps`, applies them to Room with ownership enforcement, re-publishes the snapshot, then deletes applied ops (SPEC-007 "Sync flows → Primary (inbound)").

**Architecture:** Op-inbox (CQRS-lite). A Firestore listener (`ObserveFeedOpsUseCase`) is active only while the app is foregrounded in PRIMARY mode — wired via `repeatOnLifecycle(STARTED)` in `MainActivity` (this codebase's first Firestore listener; partner fetch today is one-shot). Per batch, `ProcessFeedOpsUseCase` sorts ops by `createdAtMs`, applies each via `ApplyFeedOpUseCase`, pushes `BOTTLE_FEEDS` + `INVENTORY` snapshots, and only then deletes the op docs — a crash between push and delete causes a re-apply that idempotency absorbs. The primary is the **authoritative ownership enforcement point**: security rules (Plan 03) cannot see Room state.

**Tech Stack:** Kotlin Coroutines + Flow (`callbackFlow`), Firestore KTX, Room, Hilt, JUnit 5 + MockK + Turbine.

**Spec:** `specs/SPEC-007-PARTNER-BOTTLE-FEED-LOGGING.md` — "Architecture", "Firestore op document", "Sync flows → Primary", "Conflict semantics", "Error handling", "Testing".

**Dependencies:** Plan 01 (`clientId`/`author`), Plan 02 (snapshot fields for the push). Plan 03's rules must be deployed before this ships to users, but nothing here compiles against it.

**Suggested branch:** `feat/primary-feed-op-apply`

---

## File Structure

- Create: `app/src/main/java/com/babytracker/sharing/domain/model/FeedOp.kt` — op domain model + action enum.
- Create: `app/src/main/java/com/babytracker/sharing/usecase/ObserveFeedOpsUseCase.kt` — listener flow.
- Create: `app/src/main/java/com/babytracker/sharing/usecase/ApplyFeedOpUseCase.kt` — per-op apply with ownership gate.
- Create: `app/src/main/java/com/babytracker/sharing/usecase/ProcessFeedOpsUseCase.kt` — orchestration loop (mode gate, ordering, push-then-delete).
- Modify: `app/src/main/java/com/babytracker/sharing/data/firebase/FirestoreSharingService.kt` — `observeFeedOps`, `deleteFeedOps`, op mapping.
- Modify: `app/src/main/java/com/babytracker/sharing/domain/repository/SharingRepository.kt` + `SharingRepositoryImpl.kt` — surface the two service calls.
- Modify: `app/src/main/java/com/babytracker/domain/repository/BottleFeedRepository.kt` + `app/src/main/java/com/babytracker/data/repository/BottleFeedRepositoryImpl.kt` — `getByClientId`, `insertWithBagConsume`.
- Modify: `app/src/main/java/com/babytracker/data/local/dao/BottleFeedDao.kt` — `getByClientId` query + `insertWithBagConsume` transaction.
- Modify: `app/src/main/java/com/babytracker/MainActivity.kt` — lifecycle wiring.
- Test: `app/src/test/java/com/babytracker/sharing/usecase/ApplyFeedOpUseCaseTest.kt`, `ProcessFeedOpsUseCaseTest.kt` (new), plus DAO/repository test extensions.

---

### Task 1: `FeedOp` domain model

**Files:**
- Create: `app/src/main/java/com/babytracker/sharing/domain/model/FeedOp.kt`

- [ ] **Step 1:** Mirror the spec's op document exactly (`authorUid` is rules-only and still carried for logging):

```kotlin
package com.babytracker.sharing.domain.model

enum class FeedOpAction { CREATE, UPDATE, DELETE }

data class FeedOp(
    val opId: String,
    val action: FeedOpAction,
    val entryClientId: String,
    val authorUid: String,
    val createdAtMs: Long,
    val timestampMs: Long? = null,
    val volumeMl: Int? = null,
    val type: String? = null,
    val notes: String? = null,
    val consumedBagId: Long? = null,
)
```

### Task 2: Firestore service — listen, map, delete

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/data/firebase/FirestoreSharingService.kt`

- [ ] **Step 1: Listener flow** (each emission = full current contents; replay-on-attach gives the spec's automatic catch-up on app open):

```kotlin
fun observeFeedOps(code: String): Flow<List<FeedOp>> = callbackFlow {
    val registration = firestore.collection(SHARES).document(code)
        .collection(FEED_OPS)
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

Imports: `kotlinx.coroutines.channels.awaitClose`, `kotlinx.coroutines.flow.callbackFlow`. Add `private const val FEED_OPS = "feedOps"` to the companion.

- [ ] **Step 2: Mapping** (`internal` for testability, like the snapshot mappers from Plan 02; unknown `action` → `null` → op skipped, never crash the listener):

```kotlin
internal fun mapToFeedOp(opId: String, map: Map<*, *>): FeedOp? {
    val action = (map["action"] as? String)?.let { raw ->
        FeedOpAction.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    } ?: return null
    return FeedOp(
        opId = opId,
        action = action,
        entryClientId = map["entryClientId"] as? String ?: return null,
        authorUid = map["authorUid"] as? String ?: "",
        createdAtMs = (map["createdAtMs"] as? Number)?.toLong() ?: return null,
        timestampMs = (map["timestampMs"] as? Number)?.toLong(),
        volumeMl = (map["volumeMl"] as? Number)?.toInt(),
        type = map["type"] as? String,
        notes = map["notes"] as? String,
        consumedBagId = (map["consumedBagId"] as? Number)?.toLong(),
    )
}
```

- [ ] **Step 3: Batched delete**

```kotlin
suspend fun deleteFeedOps(code: String, opIds: List<String>) {
    if (opIds.isEmpty()) return
    val batch = firestore.batch()
    opIds.forEach { opId ->
        batch.delete(
            firestore.collection(SHARES).document(code).collection(FEED_OPS).document(opId),
        )
    }
    batch.commit().await()
}
```

(Firestore batches cap at 500 writes — far above any realistic op backlog; chunk with `opIds.chunked(450)` anyway, it's one line: `opIds.chunked(450).forEach { chunk -> ... }`.)

- [ ] **Step 4: Repository surface** — add to `SharingRepository` + impl pass-throughs:

```kotlin
fun observeFeedOps(code: ShareCode): Flow<List<FeedOp>>
suspend fun deleteFeedOps(code: ShareCode, opIds: List<String>)
```

### Task 3: Room — lookup by clientId + transactional create-with-bag

**Files:**
- Modify: `app/src/main/java/com/babytracker/data/local/dao/BottleFeedDao.kt`
- Modify: `app/src/main/java/com/babytracker/domain/repository/BottleFeedRepository.kt`
- Modify: `app/src/main/java/com/babytracker/data/repository/BottleFeedRepositoryImpl.kt`

- [ ] **Step 1: DAO query + transaction** (reuses the existing `isMilkBagActive`/`markMilkBagUsed` primitives in this DAO):

```kotlin
@Query("SELECT * FROM bottle_feeds WHERE client_id = :clientId LIMIT 1")
suspend fun getByClientId(clientId: String): BottleFeedEntity?

@Transaction
suspend fun insertWithBagConsume(entity: BottleFeedEntity, consumedBagId: Long?, usedAt: Long): Long {
    // Spec: feed truth outranks the stash link — if the bag was consumed/deleted
    // meanwhile, insert the feed without the link.
    val linkBag = consumedBagId != null && isMilkBagActive(consumedBagId)
    val id = insert(entity.copy(linkedMilkBagId = if (linkBag) consumedBagId else null))
    if (linkBag) markMilkBagUsed(consumedBagId!!, usedAt)
    return id
}
```

- [ ] **Step 2: Repository interface + impl**

```kotlin
// interface
suspend fun getByClientId(clientId: String): BottleFeed?
suspend fun insertWithBagConsume(feed: BottleFeed, consumedBagId: Long?, usedAt: Instant): Long

// impl
override suspend fun getByClientId(clientId: String): BottleFeed? =
    dao.getByClientId(clientId)?.toDomain()

override suspend fun insertWithBagConsume(feed: BottleFeed, consumedBagId: Long?, usedAt: Instant): Long =
    dao.insertWithBagConsume(feed.toEntity(), consumedBagId, usedAt.toEpochMilli())
```

### Task 4: `ApplyFeedOpUseCase` — ownership-gated apply

**Files:**
- Create: `app/src/main/java/com/babytracker/sharing/usecase/ApplyFeedOpUseCase.kt`

- [ ] **Step 1:** Full implementation. Every drop path returns `false` and logs — dropped ops are deleted upstream like applied ones (they must not poison the inbox), the boolean only signals "Room changed, snapshot push needed":

```kotlin
package com.babytracker.sharing.usecase

import android.util.Log
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import java.time.Instant
import javax.inject.Inject

class ApplyFeedOpUseCase @Inject constructor(
    private val repository: BottleFeedRepository,
    private val now: () -> Instant = Instant::now,
) {
    /** @return true if Room changed (snapshot push required). Invalid/forged ops return false. */
    suspend operator fun invoke(op: FeedOp): Boolean = when (op.action) {
        FeedOpAction.CREATE -> applyCreate(op)
        FeedOpAction.UPDATE -> applyUpdate(op)
        FeedOpAction.DELETE -> applyDelete(op)
    }

    private suspend fun applyCreate(op: FeedOp): Boolean {
        val payload = validatedPayload(op) ?: return drop(op, "invalid payload")
        val existing = repository.getByClientId(op.entryClientId)
        return when {
            existing == null -> {
                repository.insertWithBagConsume(
                    BottleFeed(
                        clientId = op.entryClientId,
                        timestamp = payload.timestamp,
                        volumeMl = payload.volumeMl,
                        type = payload.type,
                        notes = op.notes,
                        createdAt = Instant.ofEpochMilli(op.createdAtMs),
                        author = FeedAuthor.PARTNER, // forced regardless of payload (spec)
                    ),
                    consumedBagId = op.consumedBagId,
                    usedAt = now(),
                )
                true
            }
            existing.author == FeedAuthor.OWNER -> drop(op, "create collides with OWNER entry")
            else -> {
                // idempotent re-apply (crash between push and delete): upsert-by-clientId.
                // consumedBagId is create-only and was already consumed on first apply — not re-linked.
                repository.updateDetails(
                    id = existing.id,
                    timestamp = payload.timestamp,
                    volumeMl = payload.volumeMl,
                    type = payload.type,
                    linkedMilkBagId = existing.linkedMilkBagId,
                    notes = op.notes,
                )
            }
        }
    }

    private suspend fun applyUpdate(op: FeedOp): Boolean {
        val payload = validatedPayload(op) ?: return drop(op, "invalid payload")
        val existing = repository.getByClientId(op.entryClientId)
            ?: return drop(op, "entry missing (deleted on primary)")
        if (existing.author != FeedAuthor.PARTNER) return drop(op, "update targets OWNER entry")
        return repository.updateDetails(
            id = existing.id,
            timestamp = payload.timestamp,
            volumeMl = payload.volumeMl,
            type = payload.type,
            linkedMilkBagId = existing.linkedMilkBagId, // consumedBagId is create-only (spec)
            notes = op.notes,
        )
    }

    private suspend fun applyDelete(op: FeedOp): Boolean {
        val existing = repository.getByClientId(op.entryClientId)
            ?: return drop(op, "entry already gone")
        if (existing.author != FeedAuthor.PARTNER) return drop(op, "delete targets OWNER entry")
        return repository.deleteWithInventoryRestore(existing)
    }

    private data class ValidPayload(val timestamp: Instant, val volumeMl: Int, val type: FeedType)

    private fun validatedPayload(op: FeedOp): ValidPayload? {
        val timestampMs = op.timestampMs ?: return null
        val volumeMl = op.volumeMl ?: return null
        val type = op.type?.let { raw -> FeedType.entries.firstOrNull { it.name == raw } } ?: return null
        if (volumeMl <= 0) return null
        val timestamp = Instant.ofEpochMilli(timestampMs)
        if (timestamp.isAfter(now())) return null
        return ValidPayload(timestamp, volumeMl, type)
    }

    private fun drop(op: FeedOp, reason: String): Boolean {
        Log.w(TAG, "Dropping feed op ${op.opId} (${op.action} ${op.entryClientId}): $reason")
        return false
    }

    private companion object {
        const val TAG = "ApplyFeedOp"
    }
}
```

Check `FeedType` for its actual enum entries when writing validation tests (`FeedType.entries` lookup avoids `valueOf` crashes on hostile strings).

### Task 5: `ProcessFeedOpsUseCase` — orchestration

**Files:**
- Create: `app/src/main/java/com/babytracker/sharing/usecase/ProcessFeedOpsUseCase.kt`

- [ ] **Step 1:** Mode-gated listener collection; per batch: sort → apply → push → delete:

```kotlin
package com.babytracker.sharing.usecase

import android.util.Log
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.domain.model.ShareCode
import com.babytracker.sharing.domain.repository.SharingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class ProcessFeedOpsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val sharingRepository: SharingRepository,
    private val applyFeedOp: ApplyFeedOpUseCase,
    private val syncToFirestore: SyncToFirestoreUseCase,
) {
    /** Suspends while collecting; cancel via the caller's scope (lifecycle-aware). */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend operator fun invoke() {
        combine(
            settingsRepository.getAppMode(),
            settingsRepository.getShareCode(),
        ) { mode, code -> if (mode == AppMode.PRIMARY && code != null) ShareCode(code) else null }
            .distinctUntilChanged()
            .flatMapLatest { code ->
                if (code == null) flowOf(null) else sharingRepository.observeFeedOps(code).map { code to it }
            }
            // transient listener errors (offline, permission churn) must not kill the
            // pipeline for the rest of the foreground session — back off and re-attach
            .retryWhen { cause, attempt ->
                Log.w(TAG, "feed op listener error (attempt $attempt); retrying", cause)
                delay(min(RETRY_BASE_MS * (attempt + 1), RETRY_MAX_MS))
                true
            }
            // serial collect — NOT collectLatest: a new emission must not cancel a batch
            // mid-flight between the Room apply and the snapshot push
            .collect { batch ->
                if (batch == null) return@collect
                val (code, ops) = batch
                if (ops.isEmpty()) return@collect
                runCatching { processBatch(code, ops) }
                    .onFailure { Log.w(TAG, "feed op batch failed; will retry on next emission", it) }
            }
    }

    private suspend fun processBatch(code: ShareCode, ops: List<com.babytracker.sharing.domain.model.FeedOp>) {
        ops.sortedBy { it.createdAtMs }.forEach { applyFeedOp(it) }
        // Push unconditionally for any non-empty batch (spec order: apply -> push -> delete).
        // Do NOT gate the push on "anyApplied": after a cancellation/crash replay, an
        // already-applied delete reports false (row gone), and gating would delete the op
        // without ever publishing the corrected snapshot — partner keeps a ghost entry.
        // A forged-only batch pushes an identical snapshot: two redundant doc writes, harmless.
        syncToFirestore(SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS)
        syncToFirestore(SyncToFirestoreUseCase.SyncType.INVENTORY)
        // delete only after the push; dropped (invalid/forged) ops are deleted too —
        // they must not poison the inbox
        sharingRepository.deleteFeedOps(code, ops.map { it.opId })
    }

    private companion object {
        const val TAG = "ProcessFeedOps"
        const val RETRY_BASE_MS = 5_000L
        const val RETRY_MAX_MS = 60_000L
    }
}
```

Fix imports per actual `SettingsRepository` API (`getShareCode(): Flow<String?>` — verify name at implementation time; it is used in `SyncToFirestoreUseCase` today). Add `import kotlinx.coroutines.flow.map`, `import kotlinx.coroutines.flow.retryWhen`, `import kotlinx.coroutines.delay`, `import kotlin.math.min`.

Cancellation safety: lifecycle stop can still cancel a batch between the Room apply and the push. That is the same window as a crash, and the recovery is identical — on re-attach the listener replays the still-present op docs, re-apply is idempotent, and the unconditional push publishes the corrected snapshot before the ops are deleted.

- [ ] **Step 2: MainActivity wiring** — inject and collect, foreground-only:

```kotlin
@Inject
lateinit var processFeedOps: ProcessFeedOpsUseCase

// in onCreate, after super.onCreate:
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        processFeedOps()
    }
}
```

Imports: `androidx.lifecycle.Lifecycle`, `androidx.lifecycle.lifecycleScope`, `androidx.lifecycle.repeatOnLifecycle`, `kotlinx.coroutines.launch`. `repeatOnLifecycle(STARTED)` cancels the listener when backgrounded and re-attaches on return — re-attach replays all pending docs (the spec's "catch-up on app open is automatic").

### Task 6: Tests

**Files:**
- Create: `app/src/test/java/com/babytracker/sharing/usecase/ApplyFeedOpUseCaseTest.kt`
- Create: `app/src/test/java/com/babytracker/sharing/usecase/ProcessFeedOpsUseCaseTest.kt`

- [ ] **Step 1: `ApplyFeedOpUseCaseTest`** — JUnit 5 + MockK, `repository = mockk()`, fixed `now`. Cover (spec "Testing" list, one test each):
  - create inserts `author = PARTNER` with bag consume (`coVerify { repository.insertWithBagConsume(match { it.author == FeedAuthor.PARTNER }, 7L, any()) }`)
  - create payload cannot set author — op carries no author field by construction; assert inserted author is PARTNER even though `authorUid` is arbitrary
  - create colliding with OWNER `clientId` → drop (`coVerify(exactly = 0) { repository.insertWithBagConsume(any(), any(), any()) }`, returns false)
  - idempotent create re-apply (existing PARTNER entry) → `updateDetails` with existing link, no second bag consume
  - update on PARTNER entry applies; update preserves `linkedMilkBagId` (consumedBagId create-only)
  - forged update/delete against OWNER entry → entry untouched, returns false
  - update after primary deleted entry → drop, returns false
  - delete on PARTNER entry → `deleteWithInventoryRestore`
  - invalid payloads dropped: volume ≤ 0, missing volume/timestamp/type, unknown type string, future timestamp

Example shape:

```kotlin
@Test
fun `forged delete against OWNER entry is dropped`() = runTest {
    coEvery { repository.getByClientId("owner-entry") } returns ownerFeed
    val result = applyFeedOp(deleteOp(entryClientId = "owner-entry"))
    assertFalse(result)
    coVerify(exactly = 0) { repository.deleteWithInventoryRestore(any()) }
}
```

- [ ] **Step 2: `ProcessFeedOpsUseCaseTest`** — MockK + Turbine where needed. Cover:
  - ops applied in `createdAtMs` order (capture invocation order with `mutableListOf` + `coEvery ... answers`)
  - snapshot pushes (`BOTTLE_FEEDS` then `INVENTORY`) happen before `deleteFeedOps` (MockK `coVerifyOrder`)
  - push happens even when every op was dropped/already-applied (replay-after-cancellation regression: a DELETE op whose row is already gone must still result in push-then-delete)
  - PARTNER mode / missing share code → `observeFeedOps` never collected
  - failure inside `processBatch` logged, not rethrown (flow keeps collecting; next emission processed)
  - listener error then recovery: `observeFeedOps` flow throws, pipeline retries (advance virtual time past backoff with `runTest`), a subsequent emission is processed without lifecycle restart
  - a second emission arriving while a batch is in flight does not cancel it (serial `collect` semantics — both batches fully processed)

- [ ] **Step 3: Run**

Run: `./gradlew test --tests "com.babytracker.sharing.usecase.ApplyFeedOpUseCaseTest" --tests "com.babytracker.sharing.usecase.ProcessFeedOpsUseCaseTest"`
Expected: PASS

### Task 7: Full validation + commit

- [ ] **Step 1:** `./gradlew test` → PASS, then `./gradlew build` → PASS (manifest/DI/lifecycle changes warrant the full build).
- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat(sharing): apply partner feed ops on primary with ownership enforcement"
```

---

## Acceptance Criteria

- [ ] Listener active only foregrounded + PRIMARY + share code present; detaches on background; replay-on-attach covers catch-up.
- [ ] Ownership gate (blocking requirement): update/delete apply only when existing `author == PARTNER`; create forces `author = PARTNER`; create colliding with OWNER `clientId` dropped; all drops logged.
- [ ] Bag fallback: inactive/deleted `consumedBagId` → feed inserted without link (feed truth outranks stash link); bag consumed at most once across re-applies.
- [ ] Payload re-validation on primary (volume > 0, timestamp not future, known type) independent of Firestore rules.
- [ ] Batch order: apply (sorted by `createdAtMs`) → push `BOTTLE_FEEDS` + `INVENTORY` → delete ops; push is unconditional for non-empty batches (replay safety); dropped ops also deleted.
- [ ] Pipeline survives transient listener errors (`retryWhen` + capped backoff) and uses serial `collect` so new emissions never cancel an in-flight batch.
- [ ] Primary edits/deletes of any entry keep today's local path; `author` never changes (verify: `updateDetails` does not touch the `author` column).
- [ ] All listed unit tests green; `./gradlew build` green.
