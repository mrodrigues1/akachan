# Firebase → Supabase Migration — Design

**Status:** Draft
**Date:** 2026-06-17
**Author:** mrodrigues1
**Supersedes parts of:** `specs/SPEC-005-SHARING-FEATURE.md`

---

## 1. Goal

Replace the Firebase backend (Firestore + anonymous Firebase Auth) used by the
partner-sharing feature with Supabase (Postgres + GoTrue anonymous auth +
Realtime). End state: zero `com.google.firebase` dependencies in the app;
sharing runs entirely on Supabase Cloud.

This migration touches **only** the `sharing/` feature. No tracking data (Room)
changes. The rest of the app is untouched.

---

## 2. Why this is tractable

The codebase already isolates Firebase behind a clean seam:

- `SharingRepository` (interface) is the abstraction boundary. ViewModels and use
  cases depend on it, never on Firebase types.
- `FirestoreSharingService` is the **only** class that calls Firestore/Auth APIs.
- `SharingModule` is the only DI provider of `FirebaseFirestore` / `FirebaseAuth`.
- `AntiPatternTest` already asserts `com.google.firebase` imports exist nowhere
  except `sharing/` and `SharingModule`.

So the migration is: add a `SupabaseSharingService` implementing the same
behaviour, rebind the repository to it, then delete the Firebase code.

### Key simplification: snapshots are ephemeral

The primary device regenerates the full `ShareSnapshot` from local Room on every
tracking action (`SyncToFirestoreUseCase`). The remote store holds only a
**derived, disposable cache** — there is no source-of-truth data living in
Firestore. Therefore **no historical data migration is required**. After cutover,
primaries re-create their share documents in Supabase on the next sync; existing
Firebase share codes are abandoned (acceptable: read-only convenience data,
re-shareable in seconds).

---

## 3. Decisions (locked)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Data model | **Normalized relational tables** | Idiomatic Postgres, typed columns, queryable, RLS per table. More mapping work than a JSONB blob but a cleaner long-term schema. |
| Offline writes | **Local Room outbox + WorkManager retry** | `supabase-kt` has no offline persistence (Firestore did). Preserve the current offline-first partner feed-logging behaviour rather than regress it. |
| Hosting | **Supabase Cloud (managed)** | No infra to run; mirrors the current Firebase-as-a-service model. Local Supabase CLI/Docker used for tests + CI (replaces the Firebase emulator). |
| Cutover | **Full replace** | Remove Firebase entirely. Since snapshots regenerate, there is no dual-run data-consistency burden. |

---

## 4. Current → Target mapping

| Concern | Firebase (today) | Supabase (target) |
|---------|------------------|-------------------|
| Anonymous identity | `FirebaseAuth.signInAnonymously()` → `uid` | `supabase.auth.signInAnonymously()` → session user `id` |
| Store | Firestore document `shares/{code}` + sub-collections | Postgres tables (see §5) |
| Primary write | `set(..., SetOptions.merge())` | Postgrest `upsert` |
| Partner read | `document(code).get()` | Postgrest `select` |
| Real-time feed ops | `addSnapshotListener` → `callbackFlow` | Realtime `postgresChangeFlow<PostgresAction>` |
| Offline feed-op write | Firestore SDK local queue | Room outbox table + WorkManager |
| Batch delete | `firestore.batch()` | Postgrest `delete().in("op_id", ids)` |
| Access control | `firestore.rules` | Postgres Row Level Security (RLS) policies |
| Local test backend | Firebase emulator (port 8080) | `supabase start` (local Docker stack) |

---

## 5. Postgres schema (normalized)

All tables in schema `public`. Primary key of a share is the 8-char share code.

```
shares
  code           text primary key            -- 8-char uppercase share code
  owner_uid      text not null               -- Supabase auth user id of primary
  created_at     timestamptz not null default now()
  last_sync_at   timestamptz

baby
  code           text primary key references shares(code) on delete cascade
  ... typed columns mirroring BabySnapshot ...

sessions            -- breastfeeding; one row per SessionSnapshot
  code           text references shares(code) on delete cascade
  client_id      text                        -- stable id from snapshot
  ... typed columns ...
  primary key (code, client_id)

sleep_records       -- one row per SleepSnapshot (same shape pattern)
sleep_prediction    -- one row per share (nullable fields)
bottle_feeds        -- one row per BottleFeedSnapshot
milk_bags           -- one row per MilkBagSnapshot
inventory           -- one row per share (totalMl, bagCount, updatedAtMs)
growth              -- one row per GrowthSnapshot (type, takenAtMs, valueCanonical, notes)
milestones          -- one row per MilestoneSnapshot (title, dateEpochDay, timeMinuteOfDay, note)
                    --   NB: milestone photos stay on-device, never synced

partners
  code           text references shares(code) on delete cascade
  uid            text not null               -- partner auth user id
  connected_at   timestamptz not null default now()
  primary key (code, uid)

feed_ops
  op_id          text primary key
  code           text references shares(code) on delete cascade
  author_uid     text not null
  action         text not null               -- create | update | delete
  entry_client_id text not null
  created_at_ms  bigint not null
  timestamp_ms   bigint
  volume_ml      int
  type           text
  notes          text
  consumed_bag_id bigint
```

Exact columns of each snapshot table are derived 1:1 from the existing
`*Snapshot` domain models in `sharing/domain/model/`. Each `sync*` call replaces
the rows for that share + section (delete-by-code then insert, or upsert keyed on
the natural key) inside a single Postgrest call where possible.

### Domain ↔ row conversion

Per project convention (no Mapper classes): extension functions on the snapshot
types, e.g. `fun SessionSnapshot.toRow(code: String): SessionRow` and
`fun SessionRow.toSnapshot(): SessionSnapshot`, living beside the Supabase
service. Rows are `@Serializable` data classes for Postgrest (de)serialization.

---

## 6. Row Level Security (translation of `firestore.rules`)

RLS policies replicate the current rules. Anonymous users are authenticated
(GoTrue issues a JWT for anonymous sessions), so `auth.uid()` is available.

| Resource | Firestore rule | RLS policy |
|----------|----------------|-----------|
| `shares` read | any authed user | `SELECT` allowed to any authenticated role (share code is the capability) |
| `shares` create | `request.resource.owner.uid == auth.uid` | `INSERT` with `owner_uid = auth.uid()` |
| `shares` update/delete | `auth.uid == resource.owner.uid` | `UPDATE`/`DELETE` where `owner_uid = auth.uid()` |
| snapshot tables (baby/sessions/…) | governed via parent doc owner | `INSERT/UPDATE/DELETE` where the parent `shares.owner_uid = auth.uid()`; `SELECT` to any authed user |
| `partners` create | `auth.uid == partnerUid` & share exists | `INSERT` where `uid = auth.uid()` and share exists |
| `partners` read/delete | partner self or owner | `uid = auth.uid()` OR caller owns the share |
| `feed_ops` read | owner, or connected partner reading own | owner of share, OR (connected partner AND `author_uid = auth.uid()`) |
| `feed_ops` create/update | connected partner, `authorUid == auth.uid`, valid payload | same predicate; field validation enforced by a `CHECK` constraint / trigger mirroring `isValidFeedOp` |
| `feed_ops` delete | owner or author | owner of share OR `author_uid = auth.uid()` |

`isOwner` / `isConnectedPartner` become SQL helper functions (`security definer`)
to avoid recursive RLS evaluation. The `isValidFeedOp` field validation
(action enum, volume range 1..5000, key whitelist) becomes table `CHECK`
constraints plus a trigger for cross-field rules.

---

## 7. Component design

```
sharing/
  data/
    supabase/
      SupabaseSharingService.kt     # replaces FirestoreSharingService
      SupabaseRowMapping.kt         # @Serializable rows + ext-fn conversions
      outbox/
        FeedOpOutboxEntity.kt       # Room entity (in main DB)
        FeedOpOutboxDao.kt
        FeedOpSyncWorker.kt         # WorkManager CoroutineWorker
    repository/
      SharingRepositoryImpl.kt      # unchanged: still delegates to a service
  domain/ ...                       # unchanged
di/
  SupabaseModule.kt                 # provides SupabaseClient (Auth, Postgrest, Realtime)
```

- **`SharingRepository` interface: unchanged.** This is the whole point of the
  seam — `SharingRepositoryImpl` swaps its injected service from
  `FirestoreSharingService` to `SupabaseSharingService` with no interface churn.
- **`SupabaseClient`** provided once as a `@Singleton` with `install(Auth)`,
  `install(Postgrest)`, `install(Realtime)`. URL + anon key from `BuildConfig`
  (injected via Gradle, not committed) — never hard-coded.
- **Offline outbox:** `writeFeedOp` inserts into a Room `feed_op_outbox` table and
  enqueues a `FeedOpSyncWorker` (unique work, exponential backoff). The worker
  drains pending rows to Supabase and marks them synced. This survives process
  death and offline periods — matching today's Firestore offline guarantee.
  `observeOwnFeedOps` unions Realtime rows with not-yet-synced outbox rows so the
  author sees their own pending writes immediately.

---

## 8. Build & config changes

- `gradle/libs.versions.toml`: add `supabase-kt` BOM + `auth-kt`, `postgrest-kt`,
  `realtime-kt`, and the Ktor client engine (`ktor-client-okhttp` /
  `ktor-client-android`) that `supabase-kt` requires. Remove `firebase-bom`,
  `firebase-firestore`, `firebase-auth`, and the `google-services` plugin.
- `app/build.gradle.kts`: drop `alias(libs.plugins.google.services)` + firebase
  deps; add supabase + ktor. Add `SUPABASE_URL` / `SUPABASE_ANON_KEY` to
  `buildConfigField` sourced from `local.properties` / CI secrets.
- Delete `app/google-services.json`, `firebase.json`, `firestore.rules`.
- Add `supabase/` dir: `config.toml`, SQL migrations (`supabase/migrations/`),
  RLS policies. `supabase start` provides the local stack for tests/CI.

---

## 9. Testing strategy

- **Unit tests:** mock `SharingRepository` / the Supabase service exactly as the
  existing Firestore tests mock today. Use-case and ViewModel tests are unchanged
  (they depend on the interface).
- **Row-mapping tests:** mirror `FirestoreSnapshotMappingTest` →
  `SupabaseRowMappingTest` (snapshot ↔ row round-trip).
- **Integration / round-trip tests:** the existing
  `FirestoreSharingService*RoundTripTest` suite is re-pointed at a local Supabase
  instance (`supabase start`) instead of the Firebase emulator. These verify RLS,
  realtime delivery, and the outbox drain end-to-end.
- **Outbox tests:** Room in-memory DB + a faked Supabase service to verify queue,
  retry, and dedupe semantics.
- **`AntiPatternTest`:** updated to **forbid** `com.google.firebase` everywhere
  (including `sharing/` and `SharingModule`) and to require Supabase imports stay
  inside `sharing/` + `SupabaseModule`.

---

## 10. Migration / cutover sequence

The work is sliced into PR-sized increments (see §11). During the transition both
backends may briefly coexist in the build, but **the binding flips exactly once**
in the cutover PR; the Firebase code is then deleted in the final PR. There is no
permanent feature flag and no dual-write.

Rollback within the transition: revert the cutover PR (re-binds Firestore impl,
which still exists until the cleanup PR). After the cleanup PR ships, rollback is
a normal git revert of the whole series.

---

## 11. Work breakdown (one Linear issue per PR)

1. **Supabase project, schema & RLS.** Create Cloud project; author SQL schema
   (normalized tables, §5); RLS policies + helper functions + feed-op CHECK
   constraints (§6); `supabase/` config for local stack. No app code.
2. **Add supabase-kt + Hilt client provider.** Deps in version catalog;
   `SupabaseModule` providing `SupabaseClient` (Auth/Postgrest/Realtime);
   `BuildConfig` keys. No behaviour change.
3. **Supabase anonymous auth.** Implement anonymous sign-in returning the user
   id; this is the identity primitive the rest depends on.
4. **Primary snapshot sync (write path).** `SupabaseRowMapping` + all `sync*`
   functions, plus share lifecycle (`createShareDocument`, `isShareCodeValid`,
   `deleteShareDocument`) via Postgrest.
5. **Partner connect & fetch (read path).** `registerPartner`, `getPartners`,
   `revokePartner`, `isPartnerConnected`, `fetchSnapshot`.
6. **Feed ops realtime.** `observeFeedOps` / `observeOwnFeedOps` via
   `postgresChangeFlow`; online `writeFeedOp` / `deleteFeedOps`.
7. **Offline write outbox.** Room outbox entity/DAO + `FeedOpSyncWorker`; route
   `writeFeedOp` through it; union pending writes into `observeOwnFeedOps`.
8. **Cutover.** Rebind `SharingRepositoryImpl` to `SupabaseSharingService`;
   re-point round-trip/integration tests to local Supabase; green CI.
9. **Remove Firebase & update docs.** Delete `FirestoreSharingService`, firebase
   deps + `google-services` plugin + `google-services.json` + `firestore.rules` +
   firebase emulator config; flip `AntiPatternTest`; update `SPEC-005`,
   `CLAUDE.md`, `AGENTS.md`, `docs/AI_REPO_MAP.md`.

Dependencies: 1 → 2 → 3 → {4, 5} → 6 → 7 → 8 → 9. (4 and 5 can be parallel.)

---

## 12. Risks & non-goals

**Risks**
- **No SDK offline cache.** Mitigated by the outbox (issue 7). Read-side staleness
  while offline is accepted (partner dashboard is best-effort live data).
- **RLS correctness.** Firestore rules are battle-tested; RLS rewrites are the
  highest-risk part. Mitigated by the integration test suite running against a
  real local Supabase (issue 8).
- **Realtime auth.** Realtime must use the authenticated (anon-JWT) connection so
  RLS applies to subscriptions. Covered in issue 6.

**Non-goals**
- No change to local tracking data, Room schema (other than the new outbox
  table), or any non-sharing feature.
- No upgrade from anonymous auth to real accounts (kept identical to today).
- No new sharing features — behaviour parity only.
- No historical data migration (snapshots regenerate; see §2).
