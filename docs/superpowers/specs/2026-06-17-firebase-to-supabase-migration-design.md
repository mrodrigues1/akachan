# Firebase → Supabase Migration — Design

**Status:** Draft
**Date:** 2026-06-17
**Author:** mrodrigues1
**Supersedes parts of:** `specs/SPEC-005-SHARING-FEATURE.md`

---

## 1. Goal

Replace the Firebase backend (Firestore + anonymous Firebase Auth) used by the
partner-sharing feature with Supabase (Postgres + GoTrue **Google OAuth** +
Realtime). End state: zero `com.google.firebase` dependencies in the app;
sharing runs entirely on Supabase Cloud, gated behind Google sign-in.

**Auth change (2026-07-06):** the original draft kept anonymous auth. It now
uses **Google Sign-In** (native, via Android Credential Manager → Supabase
`signInWith(IDToken)`). This is not a like-for-like swap: anonymous sign-in was
silent, contextless, and called on every sharing operation (11 sites). Google
sign-in is **interactive** — it needs an Activity and user choice, so it happens
**once** behind a UI gate; the persisted GoTrue session is then reused by every
call site (which now read the current user id, they do not re-authenticate).

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
| Identity | **Google Sign-In (native Credential Manager)** | Replaces anonymous auth. Stable identity across devices/reinstalls; interactive once, session persisted by GoTrue. Native in-app account picker (androidx.credentials + googleid → `signInWith(IDToken)`) — no browser bounce. Needs a Google Cloud OAuth **Web** client id configured in Supabase's Google provider. |
| Data model | **Normalized relational tables** | Idiomatic Postgres, typed columns, queryable, RLS per table. More mapping work than a JSONB blob but a cleaner long-term schema. |
| Offline writes | **Local Room outbox + WorkManager retry** | `supabase-kt` has no offline persistence (Firestore did). Preserve the current offline-first partner feed-logging behaviour rather than regress it. |
| Hosting | **Supabase Cloud (managed)** | No infra to run; mirrors the current Firebase-as-a-service model. Local Supabase CLI/Docker used for tests + CI (replaces the Firebase emulator). |
| Cutover | **Full replace** | Remove Firebase entirely. Since snapshots regenerate, there is no dual-run data-consistency burden. |

---

## 4. Current → Target mapping

| Concern | Firebase (today) | Supabase (target) |
|---------|------------------|-------------------|
| Identity (first sign-in) | `FirebaseAuth.signInAnonymously()` (silent) → `uid` | **interactive** Credential Manager Google flow → `supabase.auth.signInWith(IDToken)` → session user `id` |
| Identity (later calls) | `signInAnonymously()` re-called silently everywhere | `auth.currentUserOrNull()?.id` from the persisted session — non-interactive |
| Sign-in UI | none (anonymous is invisible) | "Sign in with Google" gate on the sharing entry screens before generate/connect |
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

All tables in schema `public`. Primary key of a share is the 8-char share code;
every child table `references shares(code) on delete cascade`. Columns are
derived 1:1 from the `*Snapshot` models in `sharing/domain/model/ShareSnapshot.kt`
plus `FeedOp.kt` / `SleepOp.kt`. **15 tables** (the original 2026-06-17 draft
listed 10 — `diapers`, `doctor_visits`, and `sleep_ops` were added as those
features shipped; `sleep_prediction` became a semantic payload):

```
shares            code pk, owner_uid, created_at, last_sync_at
baby              code pk → shares; name, birth_date_ms, allergies text[]
sessions          (code, id) pk; start_time, end_time, starting_side, switch_time,
                  paused_duration_ms, notes, paused_at_ms      -- paused_at_ms null = running
sleep_records     (code, id) pk; start_time, end_time, sleep_type, notes,
                  client_id, started_by                        -- SPEC-008 partner sleep
sleep_prediction  code pk → shares; state_label, window_start, window_end,
                  best_estimate, confidence, reasons jsonb, feed_due bool, generated_at
                  -- semantic payload (AKA-302): reasons = discriminated SleepReason maps
                  --   (each {type, ...params}), NOT localized strings; no feed_prompt text.
                  --   Each device resolves reasons to text in its own locale at the UI edge.
bottle_feeds      id identity pk, code → shares; timestamp, volume_ml, type,
                  client_id, author, notes                     -- surrogate id: owner feeds
                  --   default client_id '' and would collide on (code, client_id); sync is
                  --   delete-by-code + insert so the DB assigns id, client_id stays a column.
milk_bags         (code, id) pk; collection_date_ms, volume_ml, notes
inventory         code pk → shares; total_ml, bag_count, updated_at_ms
growth            (code, type, taken_at_ms) pk; value_canonical, notes
milestones        (code, title, date_epoch_day) pk; time_minute_of_day, note
                  -- milestone photos stay on-device, never synced
diapers           id identity pk, code → shares; timestamp, type, notes   -- surrogate id
doctor_visits     (code, date) pk; provider_name    -- only date + providerName sync;
                  --   questions / clinical notes stay local, never in the partner snapshot
partners          (code, uid) pk; connected_at
feed_ops          op_id pk, code → shares; author_uid, action, entry_client_id,
                  created_at_ms, timestamp_ms, volume_ml, type, notes, consumed_bag_id
                  -- action ∈ create | update | delete
sleep_ops         op_id pk, code → shares; author_uid, action, entry_client_id,
                  created_at_ms, start_time_ms, end_time_ms, sleep_type, notes
                  -- SPEC-008; action ∈ start | stop | update; write-once (an edit is a
                  --   new 'update' row, not a mutation of a prior op)
```

Each `sync*` call replaces the rows for that share + section (delete-by-code then
insert, or upsert keyed on the natural key) inside a single Postgrest call where
possible.

### Domain ↔ row conversion

Per project convention (no Mapper classes): extension functions on the snapshot
types, e.g. `fun SessionSnapshot.toRow(code: String): SessionRow` and
`fun SessionRow.toSnapshot(): SessionSnapshot`, living beside the Supabase
service. Rows are `@Serializable` data classes for Postgrest (de)serialization.

---

## 6. Row Level Security (translation of `firestore.rules`)

RLS policies replicate the current rules. Google-signed-in users are
authenticated (GoTrue issues a JWT on `signInWith(IDToken)`), so `auth.uid()` is
available — the policies are identical to what anonymous auth would have used;
only the identity source changed.

| Resource | Firestore rule | RLS policy |
|----------|----------------|-----------|
| `shares` read | any authed user | `SELECT` allowed to any authenticated role (share code is the capability) |
| `shares` create | `request.resource.owner.uid == auth.uid` | `INSERT` with `owner_uid = auth.uid()` |
| `shares` update/delete | `auth.uid == resource.owner.uid` | `UPDATE`/`DELETE` where `owner_uid = auth.uid()` |
| snapshot tables (baby/sessions/sleep_records/sleep_prediction/bottle_feeds/milk_bags/inventory/growth/milestones/diapers/doctor_visits) | governed via parent doc owner | `INSERT/UPDATE/DELETE` where the parent `shares.owner_uid = auth.uid()`; `SELECT` to any authed user |
| `partners` create | `auth.uid == partnerUid` & share exists | `INSERT` where `uid = auth.uid()` and share exists |
| `partners` read/delete | partner self or owner | `uid = auth.uid()` OR caller owns the share |
| `feed_ops` read | owner, or connected partner reading own | owner of share, OR (connected partner AND `author_uid = auth.uid()`) |
| `feed_ops` create/update | connected partner, `authorUid == auth.uid`, valid payload | same predicate; field validation enforced by a `CHECK` constraint / trigger mirroring `isValidFeedOp` |
| `feed_ops` delete | owner or author | owner of share OR `author_uid = auth.uid()` |
| `sleep_ops` read/create/delete | same owner/connected-partner-own shape as `feed_ops`; **no update** (write-once — an edit is a new `update` row) | identical predicates to `feed_ops` for read/insert/delete; field validation mirrors `isValidSleepOp` |

`isOwner` / `isConnectedPartner` become SQL helper functions (`security definer`)
to avoid recursive RLS evaluation. The `isValidFeedOp` / `isValidSleepOp` field
validation (action enum, feed `volume_ml` range 1..5000, `entry_client_id` /
`notes` length bounds — 64 / 2000 chars) becomes table `CHECK` constraints plus a
trigger for cross-field rules (feed: `delete` omits volume/type, `consumed_bag_id`
only on `create`; sleep: `start` carries `start_time_ms`, `stop` carries
`end_time_ms`).

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
ui/sharing/
  ManageSharingScreen.kt            # + "Sign in with Google" gate before generate
  ConnectPartnerScreen.kt           # + "Sign in with Google" gate before connect
di/
  SupabaseModule.kt                 # provides SupabaseClient (Auth, Postgrest, Realtime)
```

- **Auth splits into two methods.** `signInWithGoogle(activityContext): String`
  is **interactive** (Credential Manager account picker) and called once from the
  UI gate; `currentUserId(): String` is **non-interactive** — it reads the
  persisted GoTrue session (`auth.currentUserOrNull()?.id`) and throws if not
  signed in. Every existing `signInAnonymously()` call site (the use cases and
  ViewModels that submit ops / observe / fetch) switches to `currentUserId()`; it
  must **not** trigger interactive sign-in from inside a flow or an op submit.
- **Session persistence:** GoTrue's `Auth` plugin persists the session and
  auto-refreshes tokens (the supabase-kt default `SettingsSessionManager`), so
  the one-time Google sign-in survives process death exactly like the old
  anonymous session did. No manual token handling.
- **Context plumbing:** Credential Manager needs an Activity context. The
  composable holds it (`LocalContext`) and hands it to `viewModel.signIn(context)`
  → `service.signInWithGoogle(context)`. Deliberate pragmatic exception to the
  "no Context in ViewModel" habit — it is the standard Credential Manager shape.

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

### Op-pipeline genericity (AKACHAN-298)

This design predates partner sleep ops (SPEC-008), which shipped a second op
pipeline (`SleepOp`) as a near-clone of the feed one — Process/Submit/Apply use
cases, codec, and service methods all duplicated per feature. AKACHAN-298
(closed as superseded by this migration) requires the Supabase op/outbox layer
to be built **generic from day one**: one pipeline parameterized on an *OpKind*
(codec, apply hook, sync/publish type, notify accumulation), with Feed and
Sleep as thin adapters. Do not re-clone `FeedOp*` files into `SleepOp*` files.
Constraints the OpKind seam must absorb (verified against today's code):

- **Per-kind apply rules stay separate** — ownership gating, idempotent
  re-apply after a crash between push and delete, end-time clamping. The
  `Apply*OpUseCase` classes are the adapters, not the duplication.
- **Notification accumulator lifetime differs deliberately:** feed dedupes
  consumed-bag notifications by `entryClientId` *across* retry attempts; sleep
  rebuilds *per* attempt and coalesces to the last change. The OpKind must own
  accumulation, not just expose a notify callback.
- **Per-kind publish type:** feed pushes one merged
  `BOTTLE_FEEDS_AND_INVENTORY` write; sleep pushes `SLEEP_RECORDS`.

---

## 8. Build & config changes

- `gradle/libs.versions.toml`: add `supabase-kt` BOM + `auth-kt`, `postgrest-kt`,
  `realtime-kt`, and the Ktor client engine (`ktor-client-okhttp` /
  `ktor-client-android`) that `supabase-kt` requires. Add the **Google Sign-In**
  deps: `androidx.credentials:credentials`,
  `androidx.credentials:credentials-play-services-auth`, and
  `com.google.android.libraries.identity.googleid:googleid`. Remove `firebase-bom`,
  `firebase-firestore`, `firebase-auth`, and the `google-services` plugin.
- `app/build.gradle.kts`: drop `alias(libs.plugins.google.services)` + firebase
  deps; add supabase + ktor + credentials/googleid. Add `SUPABASE_URL` /
  `SUPABASE_ANON_KEY` **and `GOOGLE_WEB_CLIENT_ID`** (the Google Cloud OAuth Web
  client id — the `serverClientId` passed to Credential Manager) to
  `buildConfigField`, sourced from `local.properties` / CI secrets. Never commit them.
- Delete `app/google-services.json`, `firebase.json`, `firestore.rules`. (Native
  Credential Manager does **not** need `google-services.json` — the Web client id
  is enough; the file was Firebase-specific.)
- Add `supabase/` dir: `config.toml`, SQL migrations (`supabase/migrations/`),
  RLS policies. `config.toml` enables the **Google** external provider (not
  `enable_anonymous_sign_ins`); the Cloud project's Auth settings get the same
  Google client id + secret. `supabase start` provides the local stack for
  tests/CI.

**Google Cloud / Supabase setup (one-time, not code):** create a Google Cloud
OAuth **Web** client (this id + secret go into Supabase's Google provider and the
`GOOGLE_WEB_CLIENT_ID` build field) **and** an **Android** OAuth client (package
name + SHA-1 of debug + release signing keys) so Credential Manager will return a
token for the app. Both live under the same Google Cloud project.

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
3. **Supabase Google auth.** Implement the interactive
   `signInWithGoogle(context)` (Credential Manager → `signInWith(IDToken)`) plus
   the non-interactive `currentUserId()`, and add the "Sign in with Google" gate
   to the two sharing entry screens. This is the identity primitive the rest
   depends on. Also configures the Google OAuth clients + Supabase provider.
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
- **Realtime auth.** Realtime must use the authenticated (Google-JWT) connection
  so RLS applies to subscriptions. Covered in issue 6.
- **Google sign-in is now a hard gate + friction.** Sharing is unusable without a
  Google account + Play Services, and Credential Manager fails silently if the
  Android OAuth client's SHA-1 (debug **and** release signing keys) isn't
  registered — a common first-run trap. The token-nonce round-trip
  (`skip_nonce_check = false`) must match between the request and GoTrue. Covered
  in issue 3; verified end-to-end on a real device at cutover (issue 8).

**Non-goals**
- No change to local tracking data, Room schema (other than the new outbox
  table), or any non-sharing feature.
- No auth providers beyond Google (no email/password, no Apple, no anonymous
  fallback). Sharing requires a Google sign-in; the rest of the app stays
  fully usable without any account.
- No account/profile management UI beyond the sign-in gate and a sign-out
  affordance where "stop sharing" already lives — behaviour parity otherwise.
- No historical data migration (snapshots regenerate; see §2).
