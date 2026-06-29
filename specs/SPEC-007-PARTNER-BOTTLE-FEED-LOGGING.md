# SPEC-007: Partner Bottle Feed Logging

**Status:** Approved design — 2026-06-12
**Linear project:** [Collaborative Partner View](https://linear.app/akachan/project/collaborative-partner-view-7fb7b4c75cf6)
**Depends on:** SPEC-005 (Sharing feature)

## Goal

First write path from the partner device to the primary. The partner can log bottle feeds with the same flow the primary has today — including selecting a milk bag from the stash (volume prefilled, bag consumed) — and can view the full feeding history. The partner can edit/delete only entries they created; the primary can edit/delete everything.

## Requirements (decided)

| Decision | Choice |
|---|---|
| Stash interaction | Consume-only: partner selects an active bag while logging a breast-milk feed; bag is marked used on the primary's stash. No adding/editing bags. |
| Sync freshness | Realtime while the primary app is foregrounded (Firestore listener) + automatic catch-up on app open + a ~15-min WorkManager background drain while the primary is closed ([background-drain design](../docs/superpowers/specs/2026-06-29-partner-op-background-drain-design.md)). No FCM/push. |
| Ownership | Sticks to creator. Primary editing a partner entry does not change ownership; the partner can still edit/delete it. Conflicts resolve last-write-wins per entry. |
| Offline | Partner writes queue via Firestore's built-in offline persistence and sync when network returns. |
| Attribution UX | Partner-created entries show a "Partner" badge in feed history on both devices. Primary entries stay unlabeled. |

## Out of scope

- Partner adding/editing/deleting milk bags (later phase of the project).
- Changing the linked milk bag when the partner edits an own entry (`consumedBagId` is create-only; the partner deletes and re-logs instead — delete already restores the bag).
- Sleep or breastfeeding session write paths (later phases; this spec's op-inbox pattern is the template).
- Multi-partner support.
- Conflict resolution beyond last-write-wins.

## Architecture: op-inbox (CQRS-lite)

The primary device remains the single source of truth. The partner never mutates shared state directly; it writes **operation documents** to a Firestore subcollection. The primary applies ops into Room, re-publishes the snapshot (existing pipeline), then deletes the applied ops.

```
Partner app                         Firestore                        Primary app
-----------                         ---------                        -----------
LogPartnerFeedUseCase  ──write──▶  shares/{code}/feedOps/{opId}
                                        │
                                        ▼ (listener, foreground only)
                                   ObserveFeedOpsUseCase ──▶ ApplyFeedOpUseCase ──▶ Room
                                                                  │
                                   shares/{code} "data"  ◀──push── SyncToFirestoreUseCase
                                        │                          │
Partner dashboard      ◀──fetch───      └──────── delete op ◀──────┘ (only after push)
(snapshot + own pending ops merged)
```

Rejected alternatives:
- **Shared per-entry collection** (both devices listen/write `shares/{code}/feeds/{id}`): true bidirectional but requires a two-way Room↔Firestore mirror on the primary (echo suppression, dedupe) and leaves two parallel sync systems. Overkill for the first write path.
- **Partner writes the snapshot doc directly:** the primary's full-snapshot push races the partner's array merge — whole-field last-write-wins guarantees lost updates.

## Data model

### Domain

```kotlin
enum class FeedAuthor { OWNER, PARTNER }

data class BottleFeed(
    val id: Long = 0,
    val clientId: String,          // NEW: UUID, stable cross-device identity
    val timestamp: Instant,
    val volumeMl: Int,
    val type: FeedType,
    val linkedMilkBagId: Long? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val author: FeedAuthor = FeedAuthor.OWNER,  // NEW
)
```

`author` is an enum, not a uid: the partner's anonymous Firebase uid changes on reinstall, which would strand their old entries as uneditable. Single-partner scope makes the enum sufficient.

### Room — migration 8 → 9

`bottle_feeds` gains:
- `client_id TEXT NOT NULL` — backfill existing rows with `lower(hex(randomblob(16)))`; unique index.
- `author TEXT NOT NULL DEFAULT 'OWNER'`.

### Snapshot (`ShareSnapshot`)

- `BottleFeedSnapshot` gains `clientId: String`, `author: String`, `notes: String?` (the partner needs the full entry to edit its own).
- New `MilkBagSnapshot(id: Long, collectionDateMs: Long, volumeMl: Int, notes: String?)`; `ShareSnapshot.milkBags: List<MilkBagSnapshot>` contains **active bags only**. Bags live only on the primary, so the primary's Room `Long` id is a stable opaque reference. The existing `INVENTORY` sync type carries `milkBags` alongside the current totals.

### Firestore op document — `shares/{code}/feedOps/{opId}`

| Field | Type | Notes |
|---|---|---|
| (doc id) | UUID string | op id, client-generated |
| `action` | `"create" \| "update" \| "delete"` | |
| `entryClientId` | UUID string | target feed entry |
| `authorUid` | string | partner auth uid — security rules only |
| `createdAtMs` | long | client timestamp; ordering + LWW |
| `timestampMs` | long | payload (create/update) |
| `volumeMl` | int | payload (create/update) |
| `type` | string | payload (create/update) |
| `notes` | string? | payload (create/update) |
| `consumedBagId` | long? | payload (create only) — primary's bag id |

### Security rules (version-controlled — blocking requirement)

This feature opens the first partner→primary write surface; its gating rules must live in the repo, not only in the Firebase console. Deliverables:

- `firestore.rules` at the repo root, containing the **existing** share/partner rules (imported from the console) plus the new `feedOps` rules below. Console-only rules are no longer acceptable once a write path exists — unreviewable rules can silently drift into blocking all partner writes or permitting unauthorized ops.
- Deployment via `firebase deploy --only firestore:rules` documented as a release step (`firebase.json` + `.firebaserc` added alongside).
- Security-rules tests against the Firebase emulator (`@firebase/rules-unit-testing`, Node toolchain under `firebase/` — outside the Android app, so the no-new-integrations rule is untouched) covering at minimum:
  - connected partner can create an op with `authorUid == auth.uid`
  - non-partner (not in `partners/`) is denied
  - `authorUid` spoofing (op with someone else's uid) is denied
  - owner and op author can delete an op; other uids are denied

```
match /shares/{code} {
  // existing read/owner-write rules unchanged
  match /feedOps/{opId} {
    allow read: if isOwner(code) || request.auth.uid == resource.data.authorUid;
    allow create, update: if isConnectedPartner(code)
                          && request.resource.data.authorUid == request.auth.uid;
    allow delete: if isOwner(code)
                  || request.auth.uid == resource.data.authorUid;
  }
}
```

Note: rules can verify who wrote an op, but cannot see Room state — they cannot know whether `entryClientId` targets an OWNER entry. Entry-level ownership is therefore enforced on the primary at apply time (below). Rules and apply-time checks are complementary layers, and both are blocking requirements.

## Sync flows

### Primary (inbound — new)

- `ObserveFeedOpsUseCase` (`sharing/usecase/`): Firestore listener on `feedOps`, active only while the app is foregrounded in PRIMARY mode (lifecycle-aware). Catch-up on app open is automatic — the listener replays all docs present. A ~15-min `PartnerOpDrainWorker` (WorkManager) performs a one-shot, server-sourced drain of the same inbox while the app is closed, so a partner op no longer waits for the next app open — see the [background-drain design](../docs/superpowers/specs/2026-06-29-partner-op-background-drain-design.md).
- `ApplyFeedOpUseCase` per op:
  - **Ownership authorization (blocking requirement):** the primary is the authoritative enforcement point — Firestore rules cannot inspect Room ownership, and the snapshot exposes every entry's `clientId` to the partner, so a buggy or malicious connected client can forge an update/delete op targeting an OWNER entry. Update/delete ops are applied **only if the existing entry has `author == PARTNER`**; ops targeting OWNER entries are dropped and logged. Create ops force `author = PARTNER` regardless of payload.
  - **create** → upsert-by-`clientId` `BottleFeed(author = PARTNER)`; if the `clientId` already exists with `author == OWNER`, drop the op (forged upsert). If `consumedBagId` refers to a still-active bag → mark it used. If the bag was consumed/deleted meanwhile → insert the feed without the link (feed truth outranks the stash link).
  - **update** → locate by `entryClientId`; apply only when `author == PARTNER`, update fields. Entry missing (primary deleted it) → drop the op silently.
  - **delete** → only when `author == PARTNER`, via existing `deleteWithInventoryRestore` path (linked-bag restore already implemented).
  - Primary re-validates payloads (volume > 0, timestamp not in future); invalid ops are dropped.
- Order per batch: apply to Room → push snapshot (`BOTTLE_FEEDS` + `INVENTORY`) → delete op docs. Deleting only after the push prevents the entry flickering out of the partner's merged history. A crash between push and delete causes a re-apply, which idempotency absorbs (create = upsert, update/delete = no-op when already applied/gone).
- Primary edits/deletes of any entry (including partner-authored) keep today's local path + snapshot push; `author` is never changed.

### Partner (write path — new)

- `LogPartnerFeedUseCase`, `EditPartnerFeedUseCase`, `DeletePartnerFeedUseCase` (`sharing/usecase/`): each writes one op doc. Firestore offline persistence queues writes transparently.
- Edit/delete use cases require the target entry's `author == PARTNER` (defense-in-depth on top of UI gating and security rules).
- History = merge of `snapshot.bottleFeeds` and the partner's own pending ops (listener on `feedOps` filtered to own uid; includes local-pending writes). A pending op for an `entryClientId` overrides the snapshot version of that entry — optimistic UI.

### Conflict semantics

Last-write-wins per entry by **apply order on the primary**: ops within a batch apply in `createdAtMs` order, and whichever write (local edit or applied op) reaches the primary's Room last wins. No `updatedAt` column is needed. A partner update for an entry the primary deleted is dropped — the entry stays gone.

## UI

### Partner app

- Partner dashboard gains a "Log bottle" action and a "Feeding history" entry point (new route, e.g. `PARTNER_FEED_HISTORY`).
- `BottleFeedSheet` / history-list composables currently bind to `BottleFeedViewModel` (local Room). Extract stateless content composables; the partner side gets `PartnerBottleFeedViewModel` + `PartnerFeedHistoryViewModel` backed by snapshot + pending ops. No BaseViewModel (anti-goal).
- Stash picker: identical sheet behavior to the primary (select active bag → volume prefilled), fed from `snapshot.milkBags`.
- Edit/delete affordances rendered only on `author == PARTNER` entries; OWNER entries display read-only.

### Both apps

- The shared history-item composable takes the entry author and renders a "Partner" badge on `PARTNER` entries. OWNER entries stay unlabeled.

## Error handling

| Failure | Behavior |
|---|---|
| Partner revoked mid-write | Security rules deny → surface through the existing `PartnerAccessRevokedException` flow (clear partner state, exit dashboard). |
| Invalid payload reaches primary | Re-validated on apply; op dropped. |
| Forged op targets an OWNER entry | Apply-time ownership check rejects it; op dropped and logged, entry untouched. |
| Selected bag consumed before apply | Feed applied without bag link. |
| Crash between snapshot push and op delete | Op re-applied; idempotency makes it a no-op. |
| Snapshot doc nears Firestore 1 MB ceiling | Pre-existing constraint; new fields marginally increase entry size — no approach change. |

## Testing

- **Unit (JUnit 5 + MockK + Turbine):**
  - `ApplyFeedOpUseCase`: create/update/delete paths, idempotent re-apply, bag-already-used fallback, invalid-payload drop, update-after-delete drop.
  - `ApplyFeedOpUseCase` ownership enforcement: forged update/delete ops against OWNER entries leave them unmodified; create op colliding with an OWNER `clientId` is dropped; create payload cannot set `author = OWNER`.
  - Partner use cases: correct op payloads; edit/delete refuse OWNER entries.
  - History merge: pending op overrides snapshot entry; delete op hides entry.
  - ViewModel gating: edit/delete only exposed for PARTNER entries in partner mode.
- **Migration test 8→9** (androidTest, in-memory Room): backfilled `client_id` non-null + unique, `author` defaults to OWNER.
- **Snapshot round-trip** with new fields (`clientId`, `author`, `notes`, `milkBags`).
