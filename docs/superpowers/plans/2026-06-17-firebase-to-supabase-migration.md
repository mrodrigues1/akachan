# Firebase → Supabase Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Firestore + anonymous Firebase Auth in the partner-sharing feature with Supabase (Postgres + GoTrue + Realtime), with no behaviour regression and zero `com.google.firebase` dependencies at the end.

> **Reconciled 2026-06-29** against `feat/firebase-to-supabase-migration` HEAD. The sharing code drifted substantially since this plan was first written — see the per-task notes. The plan now targets the *current* surface, not the 2026-06-17 one.

**Architecture:** There is **no `SharingRepository` interface** (the project rule is "no interface unless multiple implementations exist"). The Firebase seam is the concrete `FirestoreSharingService` plus the sleep-op and live-observer **extension functions declared in the same file** (`observeSleepOps`/`writeSleepOp`/`deleteSleepOps`/`getSleepOps`, `observeSnapshot`, `observePartnerConnected`). That seam is injected directly into 12 use cases + `PartnerSleepViewModel` + `ManageSharingViewModel`, and Firebase exception types have **leaked** into 4 use-case/error files (the partner-access-revoked detection). In Firestore a share is **one document** holding the whole snapshot as a nested `data` map; the op inboxes (`feedOps`, `sleepOps`) and `partners` are subcollections. We add a parallel `SupabaseSharingService` that mirrors that surface 1:1 (members **and** the extension functions) but writes/reads **normalized** Postgres tables. Cutover swaps the injected concrete type + imports at every call site and ports the leaked Firebase-exception handling to Supabase errors (no binding flip — there is no binding). Offline op writes — which the Firestore SDK queued for free — are restored with a Room outbox drained by WorkManager.

**Tech Stack:** Kotlin 2.3.20, Hilt 2.59, Room 2.8.4, WorkManager 2.11.0, Coroutines/Flow, supabase-kt (Auth + Postgrest + Realtime) + Ktor client, Supabase CLI (local Docker stack for tests), JUnit5 / MockK / Turbine / Robolectric / Konsist.

## Global Constraints

- One PR per task; each task = one Linear issue (AKA-165 … AKA-173) and is independently shippable.
- **The Firebase seam has no interface.** `SupabaseSharingService` must mirror the full `FirestoreSharingService` surface — member functions **and** the extension functions in the same file — method-name-for-method-name, so cutover is a concrete-type swap at each call site (12 use cases + `PartnerSleepViewModel` + `ManageSharingViewModel`). Do **not** add a repository interface (single implementation; project rule).
- **Firebase exception types leaked** into `usecase/PartnerAccessError.kt`, `usecase/FetchPartnerDataUseCase.kt`, `usecase/SubmitFeedOpUseCase.kt`, `usecase/SubmitSleepOpUseCase.kt`. The "partner access revoked" path keys on `FirebaseFirestoreException.Code.PERMISSION_DENIED`; cutover (Task 8) must re-express this as a Supabase RLS-denied check (Postgrest `RestException` / HTTP 401|403) behind a backend-agnostic predicate.
- No Mapper classes — use extension functions on snapshot/row types (project rule).
- No `sealed class Result<T>` wrappers, no BaseViewModel/BaseFragment, no KAPT (KSP only), no multi-module.
- DateTime: `java.time.Instant`; store epoch millis (`Long`) — same convention as today.
- Firebase deps remain in the build until AKA-173; only the cutover task (AKA-172) swaps the injected service type.
- Secrets (`SUPABASE_URL`, `SUPABASE_ANON_KEY`) come from `local.properties` / CI secrets via `BuildConfig`; never commit them.
- Snapshots are ephemeral — no historical data migration. Existing Firebase share codes are abandoned at cutover.
- Run `./gradlew test --tests "<changed test>"` per task; full `./gradlew build` before each PR. ktlint/detekt run via the pre-commit hook.
- Source of truth for table columns: the `*Snapshot` models in `sharing/domain/model/ShareSnapshot.kt` and `FeedOp.kt`/`SleepOp.kt` (fields enumerated in Task 1). Current Room schema is **v17** — the outbox migration in Task 7 targets **v17 → v18**.

---

### Task 1: Supabase project, schema & RLS — AKA-165

Stand up the backend. **No app/Kotlin code.** Deliverable: a `supabase/` dir whose migrations reproduce the full schema + RLS locally, plus a Cloud project.

**Files:**
- Create: `supabase/config.toml`
- Create: `supabase/migrations/0001_schema.sql`
- Create: `supabase/migrations/0002_rls.sql`
- Create: `supabase/README.md` (how to run local stack; where Cloud keys live)

**Interfaces:**
- Produces: the table/column contract every later task serializes against (column names below) and the RLS predicates the integration tests assert.

**Column contract (1:1 with snapshot models — 15 tables):**
- `shares(code text pk, owner_uid text not null, created_at timestamptz default now(), last_sync_at timestamptz)`
- `baby(code text pk → shares, name text, birth_date_ms bigint, allergies text[])`
- `sessions(code text → shares, id bigint, start_time bigint, end_time bigint, starting_side text, switch_time bigint, paused_duration_ms bigint, notes text, paused_at_ms bigint, pk(code,id))`  ← `paused_at_ms` added (SessionSnapshot.pausedAtMs; null = running)
- `sleep_records(code text → shares, id bigint, start_time bigint, end_time bigint, sleep_type text, notes text, client_id text, started_by text, pk(code,id))`  ← `client_id`/`started_by` added (SPEC-008 partner sleep; default `''`/`'OWNER'`)
- `sleep_prediction(code text pk → shares, state_label text, window_start bigint, window_end bigint, best_estimate bigint, confidence text, reasons text[], feed_prompt text, generated_at bigint)`
- `bottle_feeds(id bigint generated always as identity pk, code text → shares, timestamp bigint, volume_ml int, type text, client_id text, author text, notes text)`  ← surrogate `id` PK (was `pk(code,client_id)`; owner feeds default `client_id` to `''` and would collide). Sync is delete-by-code + insert, so the DB assigns `id` — `client_id` stays a column for the partner's optimistic merge.
- `milk_bags(code text → shares, id bigint, collection_date_ms bigint, volume_ml int, notes text, pk(code,id))`
- `inventory(code text pk → shares, total_ml int, bag_count int, updated_at_ms bigint)`
- `growth(code text → shares, type text, taken_at_ms bigint, value_canonical bigint, notes text, pk(code,type,taken_at_ms))`
- `milestones(code text → shares, title text, date_epoch_day bigint, time_minute_of_day int, note text, pk(code,title,date_epoch_day))`
- `diapers(id bigint generated always as identity pk, code text → shares, timestamp bigint, type text, notes text)`  ← NEW (DiaperSnapshot); surrogate `id` PK (timestamps aren't guaranteed unique; delete-by-code + insert assigns it)
- `doctor_visits(code text → shares, date bigint, provider_name text, pk(code,date))`  ← NEW (DoctorVisitSnapshot; only `date` + `providerName` sync — questions/notes stay local)
- `partners(code text → shares, uid text, connected_at timestamptz default now(), pk(code,uid))`
- `feed_ops(op_id text pk, code text → shares, author_uid text, action text, entry_client_id text, created_at_ms bigint, timestamp_ms bigint, volume_ml int, type text, notes text, consumed_bag_id bigint)`
- `sleep_ops(op_id text pk, code text → shares, author_uid text, action text, entry_client_id text, created_at_ms bigint, start_time_ms bigint, end_time_ms bigint, sleep_type text, notes text)`  ← NEW (SleepOp; action ∈ start|stop|update — SPEC-008)

All child tables `references shares(code) on delete cascade`.

- [ ] **Step 1: Install + init Supabase CLI**

Run: `supabase init` (creates `supabase/config.toml`). In `config.toml` enable anonymous sign-ins:
```toml
[auth]
enable_anonymous_sign_ins = true
```

- [ ] **Step 2: Write `0001_schema.sql`**

Create all 15 tables exactly per the column contract above. Example (repeat the pattern for every table):
```sql
create table shares (
  code         text primary key,
  owner_uid    text not null,
  created_at   timestamptz not null default now(),
  last_sync_at timestamptz
);

create table sessions (
  code              text not null references shares(code) on delete cascade,
  id                bigint not null,
  start_time        bigint not null,
  end_time          bigint,
  starting_side     text not null,
  switch_time       bigint,
  paused_duration_ms bigint not null default 0,
  notes             text,
  primary key (code, id)
);
-- ...baby, sleep_records, sleep_prediction, bottle_feeds, milk_bags, inventory,
--    growth, milestones, diapers, doctor_visits, partners, feed_ops, sleep_ops likewise.
```
Add the op-field validation as CHECK constraints (mirror `isValidFeedOp` / the SleepOp action set):
```sql
alter table feed_ops
  add constraint feed_ops_action_chk check (action in ('create','update','delete')),
  add constraint feed_ops_volume_chk check (volume_ml is null or (volume_ml > 0 and volume_ml <= 5000)),
  add constraint feed_ops_entry_chk  check (char_length(entry_client_id) between 1 and 64),
  add constraint feed_ops_create_fields_chk
    check (action = 'delete' or (timestamp_ms is not null and volume_ml is not null and type is not null));

alter table sleep_ops
  add constraint sleep_ops_action_chk check (action in ('start','stop','update')),
  add constraint sleep_ops_entry_chk  check (char_length(entry_client_id) between 1 and 64),
  -- START carries start_time_ms; STOP carries end_time_ms; UPDATE may set either.
  add constraint sleep_ops_start_fields_chk check (action <> 'start' or start_time_ms is not null),
  add constraint sleep_ops_stop_fields_chk  check (action <> 'stop'  or end_time_ms is not null);
```

- [ ] **Step 3: Write `0002_rls.sql`** — helper functions + policies

```sql
-- helpers run as definer to avoid recursive RLS
create or replace function is_owner(p_code text) returns boolean
  language sql security definer stable as $$
  select exists(select 1 from shares s where s.code = p_code and s.owner_uid = auth.uid()::text);
$$;

create or replace function is_connected_partner(p_code text) returns boolean
  language sql security definer stable as $$
  select exists(select 1 from partners p where p.code = p_code and p.uid = auth.uid()::text);
$$;

alter table shares enable row level security;
create policy shares_read   on shares for select using (auth.uid() is not null);
create policy shares_insert on shares for insert with check (owner_uid = auth.uid()::text);
create policy shares_update on shares for update using (owner_uid = auth.uid()::text);
create policy shares_delete on shares for delete using (owner_uid = auth.uid()::text);

-- snapshot child tables: read to any authed user, write only to share owner.
-- repeat this block for baby, sessions, sleep_records, sleep_prediction, bottle_feeds,
-- milk_bags, inventory, growth, milestones, diapers, doctor_visits:
alter table sessions enable row level security;
create policy sessions_read on sessions for select using (auth.uid() is not null);
create policy sessions_write on sessions for all
  using (is_owner(code)) with check (is_owner(code));

alter table partners enable row level security;
create policy partners_read   on partners for select using (uid = auth.uid()::text or is_owner(code));
create policy partners_insert  on partners for insert with check (uid = auth.uid()::text and exists(select 1 from shares where shares.code = partners.code));
create policy partners_delete  on partners for delete using (uid = auth.uid()::text or is_owner(code));

alter table feed_ops enable row level security;
create policy feed_ops_read   on feed_ops for select using (is_owner(code) or (is_connected_partner(code) and author_uid = auth.uid()::text));
create policy feed_ops_insert on feed_ops for insert with check (is_connected_partner(code) and author_uid = auth.uid()::text);
create policy feed_ops_update on feed_ops for update using (is_connected_partner(code) and author_uid = auth.uid()::text) with check (author_uid = auth.uid()::text);
create policy feed_ops_delete on feed_ops for delete using (is_owner(code) or (is_connected_partner(code) and author_uid = auth.uid()::text));

-- sleep_ops: identical predicate shape to feed_ops (SPEC-008 mirrors SPEC-007).
alter table sleep_ops enable row level security;
create policy sleep_ops_read   on sleep_ops for select using (is_owner(code) or (is_connected_partner(code) and author_uid = auth.uid()::text));
create policy sleep_ops_insert on sleep_ops for insert with check (is_connected_partner(code) and author_uid = auth.uid()::text);
create policy sleep_ops_update on sleep_ops for update using (is_connected_partner(code) and author_uid = auth.uid()::text) with check (author_uid = auth.uid()::text);
create policy sleep_ops_delete on sleep_ops for delete using (is_owner(code) or (is_connected_partner(code) and author_uid = auth.uid()::text));
```
Enable Realtime on the op inboxes **and** on `shares` + `partners` — the partner UI now streams the whole snapshot (`observeSnapshot`) and its own connection (`observePartnerConnected`) live, not just one-shot fetches:
```sql
alter publication supabase_realtime add table feed_ops, sleep_ops, shares, partners;
```

- [ ] **Step 4: Verify locally**

Run: `supabase start` then `supabase db reset`
Expected: migrations apply with no error; `supabase status` shows the stack up.

- [ ] **Step 5: Create the Cloud project**

Via the Supabase dashboard: create project, link with `supabase link`, `supabase db push`. Record URL + anon key in your password manager / CI secrets (do **not** commit). Enable anonymous sign-ins in Auth settings.

- [ ] **Step 6: Commit**

```bash
git add supabase/
git commit -m "feat(sharing): add Supabase schema, RLS, and local stack config [AKA-165]"
```

---

### Task 2: Add supabase-kt + Hilt client provider — AKA-166

Wire the SDK into the build and DI. **No behaviour change** — nothing consumes the client yet.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/babytracker/di/SupabaseModule.kt`
- Create: `app/src/test/java/com/babytracker/di/SupabaseModuleTest.kt`
- Modify: `CLAUDE.md`, `AGENTS.md` (tech-stack table only)

**Interfaces:**
- Produces: an injectable `@Singleton io.github.jan.supabase.SupabaseClient` with `Auth`, `Postgrest`, `Realtime` installed.

- [ ] **Step 1: Add versions + libs**

In `gradle/libs.versions.toml` add (use the current supabase-kt BOM):
```toml
[versions]
supabase = "3.x.x"   # pin to latest stable supabase-kt BOM
ktorClient = "3.x.x" # matching ktor version per supabase-kt docs

[libraries]
supabase-bom       = { group = "io.github.jan-tennert.supabase", name = "bom", version.ref = "supabase" }
supabase-auth      = { group = "io.github.jan-tennert.supabase", name = "auth-kt" }
supabase-postgrest = { group = "io.github.jan-tennert.supabase", name = "postgrest-kt" }
supabase-realtime  = { group = "io.github.jan-tennert.supabase", name = "realtime-kt" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktorClient" }
```

- [ ] **Step 2: Add deps + BuildConfig in `app/build.gradle.kts`**

```kotlin
implementation(platform(libs.supabase.bom))
implementation(libs.supabase.auth)
implementation(libs.supabase.postgrest)
implementation(libs.supabase.realtime)
implementation(libs.ktor.client.okhttp)
```
In `android { defaultConfig { } }`, read keys from `local.properties` and expose:
```kotlin
val props = gradleLocalProperties(rootDir, providers)
buildConfigField("String", "SUPABASE_URL", "\"${props.getProperty("supabase.url", "")}\"")
buildConfigField("String", "SUPABASE_ANON_KEY", "\"${props.getProperty("supabase.anonKey", "")}\"")
```
Ensure `buildFeatures { buildConfig = true }`.

- [ ] **Step 3: Write `SupabaseModule.kt`**

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
}
```

- [ ] **Step 4: Write graph smoke test**

```kotlin
class SupabaseModuleTest {
    @Test
    fun `provides a configured SupabaseClient`() {
        val client = SupabaseModule.provideSupabaseClient()
        assertNotNull(client.pluginManager.getPluginOrNull(Postgrest))
        assertNotNull(client.pluginManager.getPluginOrNull(Auth))
        assertNotNull(client.pluginManager.getPluginOrNull(Realtime))
    }
}
```
(Provide dummy `supabase.url` / `supabase.anonKey` in CI's `local.properties` so `createSupabaseClient` doesn't reject a blank URL.)

- [ ] **Step 5: Run + build**

Run: `./gradlew test --tests "com.babytracker.di.SupabaseModuleTest"` → PASS; then `./gradlew build` → BUILD SUCCESSFUL (Firebase + Supabase coexisting).

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/babytracker/di/SupabaseModule.kt app/src/test/java/com/babytracker/di/SupabaseModuleTest.kt CLAUDE.md AGENTS.md
git commit -m "feat(sharing): add supabase-kt deps and Hilt client provider [AKA-166]"
```

---

### Task 3: Supabase anonymous authentication — AKA-167

Implement anonymous sign-in returning the user id. Identity primitive for every later task.

**Files:**
- Create: `app/src/main/java/com/babytracker/sharing/data/supabase/SupabaseSharingService.kt` (auth method only for now)
- Create: `app/src/test/java/com/babytracker/sharing/data/supabase/SupabaseSharingServiceAuthTest.kt`

**Interfaces:**
- Consumes: `SupabaseClient` (Task 2).
- Produces: `suspend fun signInAnonymously(): String` — returns the Supabase user id, throws on failure. Same contract as `FirestoreSharingService.signInAnonymously()`.

- [ ] **Step 1: Failing test**

```kotlin
class SupabaseSharingServiceAuthTest {
    private val auth = mockk<Auth>(relaxed = true)
    private val client = mockk<SupabaseClient> { every { pluginManager.getPlugin(Auth) } returns auth }
    private val service = SupabaseSharingService(client)

    @Test
    fun `signInAnonymously returns user id`() = runTest {
        coEvery { auth.signInAnonymously() } just Runs
        every { auth.currentUserOrNull() } returns UserInfo(id = "uid-123", aud = "")
        assertEquals("uid-123", service.signInAnonymously())
    }

    @Test
    fun `signInAnonymously throws when no user`() = runTest {
        coEvery { auth.signInAnonymously() } just Runs
        every { auth.currentUserOrNull() } returns null
        assertThrows<IllegalStateException> { service.signInAnonymously() }
    }
}
```

- [ ] **Step 2: Run → FAIL** (`SupabaseSharingService` undefined).

- [ ] **Step 3: Implement**

```kotlin
@Singleton
class SupabaseSharingService @Inject constructor(
    private val client: SupabaseClient,
) {
    private val auth get() = client.pluginManager.getPlugin(Auth)

    suspend fun signInAnonymously(): String {
        auth.signInAnonymously()
        return checkNotNull(auth.currentUserOrNull()?.id) { "Anonymous sign-in returned no user" }
    }
}
```

- [ ] **Step 4: Run → PASS.**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/data/supabase/SupabaseSharingService.kt app/src/test/java/com/babytracker/sharing/data/supabase/SupabaseSharingServiceAuthTest.kt
git commit -m "feat(sharing): add Supabase anonymous sign-in [AKA-167]"
```

---

### Task 4: Primary snapshot sync (write path) — AKA-168

Row models + extension-function mapping + all `sync*` and share-lifecycle writes via Postgrest.

**Files:**
- Create: `app/src/main/java/com/babytracker/sharing/data/supabase/SupabaseRowMapping.kt`
- Modify: `app/src/main/java/com/babytracker/sharing/data/supabase/SupabaseSharingService.kt`
- Create: `app/src/test/java/com/babytracker/sharing/data/supabase/SupabaseRowMappingTest.kt`
- Create: `app/src/test/java/com/babytracker/sharing/data/supabase/SupabaseSharingServiceWriteTest.kt` (integration, local `supabase start`)

**Interfaces:**
- Consumes: `SupabaseClient`, snapshot models.
- Produces: `@Serializable` row classes (`ShareRow`, `BabyRow`, `SessionRow`, `SleepRow`, `SleepPredictionRow`, `BottleFeedRow`, `MilkBagRow`, `InventoryRow`, `GrowthRow`, `MilestoneRow`, `DiaperRow`, `DoctorVisitRow`); `fun <Snapshot>.toRow(code): <Row>` + `fun <Row>.toSnapshot(): <Snapshot>`; service methods mirroring `FirestoreSharingService` member funcs: `createShareDocument`, `isShareCodeValid`, `deleteShareDocument`, `syncFullSnapshot`, `syncSessions`, `syncSleepRecords`, `syncBottleFeeds`, `syncDiapers`, `syncInventory`, `syncBottleFeedsAndInventory` (baby, growth, milestones, and doctor-visits are written inside `syncFullSnapshot`, not as standalone incremental methods — match the current surface).
- **New fields vs the 2026-06-17 model:** `SessionRow.pausedAtMs` (null=running), `SleepRow.clientId`/`SleepRow.startedBy` (SPEC-008). Round-trip-test them too.
- **`BottleFeedRow` / `DiaperRow` have no `id` field** — the surrogate PK is `generated always as identity`, so the row class omits it: Postgres assigns it on insert, and the `id` column on read is ignored (Postgrest decodes with `ignoreUnknownKeys`). These two round-trip on their data columns only.

- [ ] **Step 1: Failing mapping test (representative — session)**

```kotlin
class SupabaseRowMappingTest {
    @Test
    fun `session snapshot round-trips through row`() {
        val s = SessionSnapshot(id = 1, startTime = 100, endTime = 200,
            startingSide = "LEFT", switchTime = 150, pausedDurationMs = 10, notes = "n", pausedAtMs = 180)
        assertEquals(s, s.toRow("CODE1234").toSnapshot())
    }
}
```
Add an equivalent assertion for every snapshot type (baby, sleep [incl. `clientId`/`startedBy`], sleepPrediction, bottleFeed, milkBag, inventory, growth, milestone, diaper, doctorVisit). Source fields verbatim from `sharing/domain/model/ShareSnapshot.kt` (see Task 1 column contract).

- [ ] **Step 2: Run → FAIL.**

- [ ] **Step 3: Implement `SupabaseRowMapping.kt`**

Representative row + conversions (repeat per type, names from Task 1):
```kotlin
@Serializable
data class SessionRow(
    val code: String,
    val id: Long,
    @SerialName("start_time") val startTime: Long,
    @SerialName("end_time") val endTime: Long?,
    @SerialName("starting_side") val startingSide: String,
    @SerialName("switch_time") val switchTime: Long?,
    @SerialName("paused_duration_ms") val pausedDurationMs: Long,
    val notes: String?,
)
fun SessionSnapshot.toRow(code: String) = SessionRow(code, id, startTime, endTime, startingSide, switchTime, pausedDurationMs, notes)
fun SessionRow.toSnapshot() = SessionSnapshot(id, startTime, endTime, startingSide, switchTime, pausedDurationMs, notes)
```

- [ ] **Step 4: Run → PASS.**

- [ ] **Step 5: Implement write methods**

```kotlin
private val db get() = client.pluginManager.getPlugin(Postgrest)

suspend fun createShareDocument(code: String, ownerUid: String) {
    db.from("shares").insert(ShareRow(code = code, ownerUid = ownerUid))
}
suspend fun isShareCodeValid(code: String): Boolean =
    db.from("shares").select { filter { eq("code", code) } }.decodeList<ShareRow>().isNotEmpty()

suspend fun syncSessions(code: String, sessions: List<SessionSnapshot>, prediction: SleepPredictionSnapshot?) {
    db.from("sessions").delete { filter { eq("code", code) } }
    if (sessions.isNotEmpty()) db.from("sessions").insert(sessions.map { it.toRow(code) })
    upsertPrediction(code, prediction)
    touchLastSync(code)
}
// syncSleepRecords / syncBottleFeeds / syncMilkBags / syncDiapers follow the same delete-by-code + insert pattern.
// syncBaby / syncInventory / upsertPrediction use upsert on the share-keyed PK.
// syncBottleFeedsAndInventory writes both in one call (mirror the Firestore method of the same name).
// syncFullSnapshot calls each section writer (incl. growth, milestones, diapers, doctor_visits) for one snapshot.
suspend fun deleteShareDocument(code: String) { db.from("shares").delete { filter { eq("code", code) } } }
```

- [ ] **Step 6: Integration test (write path)**

Against `supabase start`: as an authed anon owner, `createShareDocument` then `syncSessions`/`syncBaby`/etc., then `select` rows back and assert equality. Assert a second anon user (non-owner) is rejected by RLS when writing the same `code`.

Run: `./gradlew test --tests "com.babytracker.sharing.data.supabase.*"` → PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/data/supabase/ app/src/test/java/com/babytracker/sharing/data/supabase/
git commit -m "feat(sharing): add Supabase write path and row mapping [AKA-168]"
```

---

### Task 5: Partner connect & fetch (read path) — AKA-169

Partner registration + snapshot reassembly via Postgrest select.

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/data/supabase/SupabaseSharingService.kt`
- Create: `app/src/test/java/com/babytracker/sharing/data/supabase/SupabaseSharingServiceReadTest.kt` (integration)

**Interfaces:**
- Consumes: row mapping from Task 4 (coordinate ordering; if Task 5 lands first, it introduces `SupabaseRowMapping`).
- Produces: `registerPartner`, `isPartnerConnected`, `getPartners`, `revokePartner`, `fetchSnapshot`.

- [ ] **Step 1: Implement read/partner methods**

```kotlin
suspend fun registerPartner(code: String, partnerUid: String) {
    db.from("partners").upsert(PartnerRow(code = code, uid = partnerUid))
}
suspend fun isPartnerConnected(code: String, partnerUid: String): Boolean =
    db.from("partners").select { filter { eq("code", code); eq("uid", partnerUid) } }.decodeList<PartnerRow>().isNotEmpty()
suspend fun getPartners(code: String): List<PartnerInfo> =
    db.from("partners").select { filter { eq("code", code) } }.decodeList<PartnerRow>().map { it.toPartnerInfo() }
suspend fun revokePartner(code: String, partnerUid: String) {
    db.from("partners").delete { filter { eq("code", code); eq("uid", partnerUid) } }
}
suspend fun fetchSnapshot(code: String): ShareSnapshot {
    // select each section filtered by code, then assemble via *.toSnapshot()
    val baby = db.from("baby").select { filter { eq("code", code) } }.decodeSingle<BabyRow>()
    val sessions = db.from("sessions").select { filter { eq("code", code) } }.decodeList<SessionRow>()
    // ...sleep_records, sleep_prediction, bottle_feeds, milk_bags, inventory, growth,
    //    milestones, diapers, doctor_visits, shares.last_sync_at
    return ShareSnapshot(/* assemble from the above, incl. diapers + doctorVisits */)
}
```

- [ ] **Step 2: Integration test**

Against `supabase start`: owner writes a full snapshot (reuse Task 4 helpers); a second anon user `registerPartner`s, `fetchSnapshot` returns a `ShareSnapshot` equal to what was written; `getPartners` lists the partner for the owner; `revokePartner` removes it. Assert RLS: a partner cannot register a different uid.

Run: `./gradlew test --tests "com.babytracker.sharing.data.supabase.SupabaseSharingServiceReadTest"` → PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/data/supabase/SupabaseSharingService.kt app/src/test/java/com/babytracker/sharing/data/supabase/SupabaseSharingServiceReadTest.kt
git commit -m "feat(sharing): add Supabase partner connect and fetch [AKA-169]"
```

---

### Task 6: Realtime streams — feed ops, sleep ops, live snapshot & connection (online path) — AKA-170

Realtime streams + online write/delete. **Scope grew since 2026-06-17:** besides feed ops, the current service streams sleep ops (SPEC-008) and — as extension functions — the **whole snapshot** (`observeSnapshot`) and the partner's **own connection** (`observePartnerConnected`) live. Mirror all four on Supabase Realtime.

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/data/supabase/SupabaseSharingService.kt`
- Create: `app/src/test/java/com/babytracker/sharing/data/supabase/SupabaseSharingServiceFeedOpsTest.kt` (integration)
- Create: `app/src/test/java/com/babytracker/sharing/data/supabase/SupabaseSharingServiceSleepOpsTest.kt` (integration)
- Create: `app/src/test/java/com/babytracker/sharing/data/supabase/SupabaseSnapshotStreamTest.kt` (integration — `observeSnapshot` / `observePartnerConnected`)

**Interfaces:**
- Produces, matching the current Firestore surface 1:1:
  - Feed ops: `observeFeedOps(code, authorUid? = null): Flow<List<FeedOp>>`, `writeFeedOp(code, op, onFailure)`, `deleteFeedOps(code, opIds)`, `getFeedOps(code)`. Plus `FeedOpRow` + `FeedOp.toRow(code)` / `FeedOpRow.toFeedOp()`.
  - Sleep ops: `observeSleepOps(code, authorUid? = null): Flow<List<SleepOp>>`, `writeSleepOp(code, op, onFailure)`, `deleteSleepOps(code, opIds)`, `getSleepOps(code)`. Plus `SleepOpRow` + `SleepOp.toRow(code)` / `SleepOpRow.toSleepOp()`. (In Firestore these are extension functions; on Supabase they can be plain members or extensions — call-site imports change either way at cutover.)
  - Live observers: `observeSnapshot(code): Flow<SnapshotEmission>` and `observePartnerConnected(code, partnerUid): Flow<ConnectionEmission>`. Reuse the existing `SnapshotEmission(data: ShareSnapshot?, fromCache: Boolean)` / `ConnectionEmission(connected: Boolean, fromCache: Boolean)` types (currently declared in the Firebase file — relocate to a backend-neutral spot at cutover).
- **`fromCache` gap (decide here):** Supabase Realtime has no Firestore-style cache-origin flag. Partner code uses `fromCache` to tell a cold-offline emission from a server-confirmed one (e.g. don't treat a cache-origin missing doc as "share deleted"). Supabase equivalent: emit the seed `select` (online) with `fromCache = false`, and only surface `fromCache = true` when offline/seed-from-outbox. Verify every `fromCache` consumer still behaves; if none rely on `true` post-migration, hardcoding `false` is acceptable — but confirm, don't assume.

- [ ] **Step 1: Add `FeedOpRow` + `SleepOpRow` mapping** (enum ↔ lowercase string for `action`: feed `create/update/delete`, sleep `start/stop/update`; round-trip tests in `SupabaseRowMappingTest`).

- [ ] **Step 2: Implement realtime + writes**

```kotlin
private val realtime get() = client.pluginManager.getPlugin(Realtime)

// Single method with an optional author filter, matching the current Firestore signature.
fun observeFeedOps(code: String, authorUid: String? = null): Flow<List<FeedOp>> = flow {
    val channel = realtime.channel("feed_ops:$code")
    val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "feed_ops"; filter("code", FilterOperator.EQ, code) }
    channel.subscribe()
    // emit initial select, then re-query on each change; filter by author when authorUid != null
    emitAll(changes.map { currentFeedOps(code, authorUid) }.onStart { emit(currentFeedOps(code, authorUid)) })
}.onCompletion { /* channel.unsubscribe() */ }

suspend fun writeFeedOp(code: String, op: FeedOp, onFailure: (Throwable) -> Unit = {}) =
    runCatching { db.from("feed_ops").upsert(op.toRow(code)) }.onFailure(onFailure).getOrThrow()

suspend fun deleteFeedOps(code: String, opIds: List<String>) {
    opIds.chunked(BATCH_LIMIT).forEach { chunk -> db.from("feed_ops").delete { filter { isIn("op_id", chunk) } } }
}
```
**Sleep ops** mirror the block above against `sleep_ops` (`observeSleepOps`/`writeSleepOp`/`deleteSleepOps`/`getSleepOps`). **Live observers** open a channel on `shares` (filter `code`) and re-query → `SnapshotEmission`, and on `partners` (filter `code` + `uid`) → `ConnectionEmission(connected = row exists)`, seeding each with an initial `select` (see `fromCache` note above).

- [ ] **Step 3: Integration tests**

Against `supabase start`: owner + connected partner.
- Feed ops: partner `writeFeedOp`; assert owner's `observeFeedOps` emits it (Turbine). A second partner does NOT receive another partner's op (RLS on subscription). `deleteFeedOps` removes + emits.
- Sleep ops: same shape against `observeSleepOps`/`writeSleepOp`/`deleteSleepOps`.
- Live observers: owner `syncFullSnapshot` → partner's `observeSnapshot` emits the equal `ShareSnapshot`; `revokePartner` → partner's `observePartnerConnected` emits `connected = false`.

Run: `./gradlew test --tests "com.babytracker.sharing.data.supabase.SupabaseSharingService*OpsTest"` and `...SupabaseSnapshotStreamTest` → PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/data/supabase/ app/src/test/java/com/babytracker/sharing/data/supabase/
git commit -m "feat(sharing): add Supabase realtime feed ops, sleep ops, and live streams [AKA-170]"
```

---

### Task 7: Offline write outbox for feed ops **and sleep ops** — AKA-171

Restore offline-first op writes with a Room outbox + WorkManager. The Firestore SDK queued *both* feed-op and sleep-op writes for free; Supabase/Postgrest does not, so the outbox must cover both.

> **Don't confuse with the existing `PartnerOpDrainWorker`.** That worker (in `sharing/work/`) drains the **owner-side inboxes** — it pulls pending partner ops out of Firestore and applies them locally (`ProcessSleepOps/ProcessFeedOps.drainOnce()`), and is **not** part of this migration. This Task 7 outbox is the **partner-side send queue**: it persists the partner's outgoing op locally and pushes it up when connectivity returns. Keep them separate; `PartnerOpDrainWorker` stays as-is (it just calls Supabase reads after cutover).

> **AKACHAN-298 (generic op pipeline):** this plan predates partner sleep ops
> (SPEC-008), which now duplicate the whole feed-op pipeline. Build the outbox
> entity/DAO/worker generic over an *OpKind* (op type + codec + publish type +
> notify accumulation) rather than feed-specific, so sleep ops reuse it as a
> thin adapter instead of a `SleepOp*` clone set. See §7 "Op-pipeline
> genericity" in the design doc for the constraints the seam must absorb.

**Files:**
- Create: `app/src/main/java/com/babytracker/sharing/data/supabase/outbox/OpOutboxEntity.kt`
- Create: `app/src/main/java/com/babytracker/sharing/data/supabase/outbox/OpOutboxDao.kt`
- Create: `app/src/main/java/com/babytracker/sharing/data/supabase/outbox/OpSyncWorker.kt`
- Modify: `BabyTrackerDatabase` (v17 → **v18**) + add `MIGRATION_17_18`; `SupabaseSharingService` (route `writeFeedOp`/`writeSleepOp`, union into `observeFeedOps`/`observeSleepOps` for own ops)
- Create: `app/src/test/java/com/babytracker/sharing/data/supabase/outbox/OpOutboxDaoTest.kt` (Room in-memory) + `OpSyncWorkerTest.kt`

**Interfaces:**
- Consumes: `OpOutboxDao`, `SupabaseSharingService` online writes.
- Produces: `writeFeedOp`/`writeSleepOp` persist locally + enqueue unique work; `OpSyncWorker` drains pending rows (both kinds) and marks synced; the own-op streams union unsynced outbox rows.

- [ ] **Step 1: Entity + DAO + migration**

One discriminated table covers both op kinds (they share `opId`/`code`/`authorUid`/`createdAtMs`/`entryClientId`; the rest are nullable per kind — same flat/nullable shape as `FeedOp`/`SleepOp`):
```kotlin
@Entity(tableName = "op_outbox")
data class OpOutboxEntity(
    @PrimaryKey val opId: String,
    val opKind: String,            // "feed" | "sleep"
    val code: String, val action: String, val entryClientId: String,
    val authorUid: String, val createdAtMs: Long,
    // feed fields
    val timestampMs: Long?, val volumeMl: Int?, val type: String?, val consumedBagId: Long?,
    // sleep fields
    val startTimeMs: Long?, val endTimeMs: Long?, val sleepType: String?,
    val notes: String?,
    val synced: Boolean = false,
)
```
DAO: `upsert`, `pending(): List<…>` (where `synced = 0`), `markSynced(opId)`, `observePending(code, uid, kind): Flow<List<…>>`. Bump DB version to **18** + add `MIGRATION_17_18` creating the table; register it; update the Room schema export (`app/schemas/…/18.json`) and any schema-export test.

- [ ] **Step 2: DAO test** (Room `inMemoryDatabaseBuilder`): insert pending (feed + sleep), observe, markSynced removes from pending, dedupe on `opId`.

- [ ] **Step 3: Worker**

```kotlin
@HiltWorker
class OpSyncWorker @AssistedInject constructor(
    @Assisted ctx: Context, @Assisted params: WorkerParameters,
    private val dao: OpOutboxDao, private val service: SupabaseSharingService,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        dao.pending().forEach { row ->
            when (row.opKind) {
                "feed"  -> service.writeFeedOpDirect(row.toFeedOp(), row.code)
                "sleep" -> service.writeSleepOpDirect(row.toSleepOp(), row.code)
            }
            dao.markSynced(row.opId)
        }
        Result.success()
    } catch (e: Exception) { Result.retry() }
}
```
Enqueue as unique work with exponential backoff from `writeFeedOp`/`writeSleepOp`.

- [ ] **Step 4: Worker test** (faked service): drains pending (both kinds) → marks synced; service failure → `Result.retry()` and rows stay pending.

- [ ] **Step 5: Route writes + union reads** — `writeFeedOp`/`writeSleepOp` insert to outbox + enqueue; the own-op streams (`observeFeedOps`/`observeSleepOps` with `authorUid` set) merge realtime rows with unsynced outbox rows of that kind, de-duped by `opId`. Turbine test for each union.

- [ ] **Step 6: Run** `./gradlew test --tests "com.babytracker.sharing.data.supabase.outbox.*"` → PASS; `./gradlew build`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/data/supabase/outbox/ app/src/test/java/com/babytracker/sharing/data/supabase/outbox/ <db + MIGRATION_17_18 + schema 18.json>
git commit -m "feat(sharing): add offline op outbox (feed + sleep) with WorkManager sync [AKA-171]"
```

---

### Task 8: Cutover — swap the injected service everywhere + abstract leaked errors — AKA-172

**There is no binding to flip** (no `SharingRepository`). Cutover is a concrete-type swap at every injection site, plus re-expressing the leaked Firebase exception handling against Supabase. Firebase code stays physically present for one-revert rollback. This is the largest task — consider splitting the error-abstraction prep into its own commit within the PR.

**Files:**
- Modify (swap `FirestoreSharingService` → `SupabaseSharingService` in the constructor + fix imports of the moved extension functions): the 12 use cases that inject it — `ConnectAsPartnerUseCase`, `FetchPartnerDataUseCase`, `GenerateShareCodeUseCase`, `ObservePartnerDataUseCase`, `ObservePartnerFeedHistoryUseCase`, `ObservePartnerSleepHistoryUseCase`, `ProcessFeedOpsUseCase`, `ProcessSleepOpsUseCase`, `RevokePartnerUseCase`, `SubmitFeedOpUseCase`, `SubmitSleepOpUseCase`, `SyncToFirestoreUseCase` — plus `ui/partner/PartnerSleepViewModel.kt` and `ui/sharing/ManageSharingViewModel.kt`.
- Modify (abstract leaked Firebase exceptions): `usecase/PartnerAccessError.kt` (`isPermissionDenied` keys on `FirebaseFirestoreException.Code.PERMISSION_DENIED`), `usecase/FetchPartnerDataUseCase.kt` (catches `FirebaseException`), `usecase/SubmitFeedOpUseCase.kt`, `usecase/SubmitSleepOpUseCase.kt` (catch `FirebaseFirestoreException`). Replace with a backend-neutral `Throwable.isRlsDeniedError()` checking the Supabase Postgrest error (`RestException` / HTTP 401|403).
- Relocate `SnapshotEmission` / `ConnectionEmission` out of the Firebase file to a backend-neutral location (they're returned by the live observers).
- Modify/replace integration + mapping tests → Supabase equivalents: `FirestoreSharingServiceBottleFeedRoundTripTest`, `FirestoreSharingServiceInventoryRoundTripTest`, `FirestoreSnapshotMappingTest`, `FirestoreSnapshotDoctorVisitTest`, `DiaperFirestoreMappingTest`, and the `SyncToFirestore*` use-case tests (`SyncToFirestoreUseCaseTest`, `SyncToFirestoreUseCaseInventoryTest`, `SyncToFirestoreDiapersTest`, `SyncToFirestoreDoctorVisitTest`).
- Modify: CI workflow + test harness (replace Firebase emulator with `supabase start`).

**Interfaces:**
- Consumes: complete `SupabaseSharingService` (Tasks 3–7) mirroring the Firestore surface.
- Produces: every sharing use case / ViewModel injecting Supabase; the partner-access-revoked path working off a Supabase error; mapping/use-case unit tests pass against the new types.

- [ ] **Step 1: Abstract the leaked errors first** — introduce `Throwable.isRlsDeniedError()` (Supabase) and point `PartnerAccessError.isPermissionDenied`, the two `SubmitOp` catches, and `FetchPartnerDataUseCase` at it. (Doing this before the swap keeps each file compiling.)

- [ ] **Step 2: Swap the injected service** at all 14 sites: change the constructor param type and the imports (the sleep-op / live-observer extension functions move from `com.babytracker.sharing.data.firebase.*` to `...data.supabase.*`). Method names are identical, so call bodies are unchanged.

- [ ] **Step 3: Re-point integration tests** from Firebase emulator (port 8080) to local Supabase (`supabase start`). Update CI to boot Supabase before tests (resolves the `firebase-emulator-jvm-leak` orphaned-java issue — it goes away).

- [ ] **Step 4: End-to-end test** — primary creates share + syncs full snapshot (incl. diapers + doctor visits) → partner connects + `observeSnapshot` streams it → partner logs a feed op AND a sleep op (online + offline-then-online via the outbox) → primary observes both → `revokePartner` surfaces as `isRlsDeniedError` on the partner. All on Supabase.

- [ ] **Step 5: Run full suite** `./gradlew test` → all green; `./gradlew build`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/ app/src/main/java/com/babytracker/ui/ app/src/test/ <ci files>
git commit -m "feat(sharing): cut sharing over to Supabase backend [AKA-172]"
```

---

### Task 9: Remove Firebase & update docs — AKA-173

Delete all Firebase artifacts; flip the guardrails.

**Files:**
- Delete: `app/src/main/java/com/babytracker/sharing/data/firebase/FirestoreSharingService.kt` (incl. the sleep-op + `observeSnapshot`/`observePartnerConnected` extension functions at the bottom), `FirestoreSnapshotMapping.kt`, `app/src/main/java/com/babytracker/di/SharingModule.kt` Firebase providers, any Firebase-only tests
- Delete: `app/google-services.json`, `firebase.json`, `firestore.rules` (which gates both `feedOps` and `sleepOps` subcollections — replaced by Supabase RLS)
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (remove firebase + google-services plugin)
- Modify: `app/src/test/java/com/babytracker/architecture/AntiPatternTest.kt`
- Modify: `specs/SPEC-005-SHARING-FEATURE.md` and `specs/SPEC-007-PARTNER-BOTTLE-FEED-LOGGING.md` (op-inbox = Firestore subcollection → Supabase table), `CLAUDE.md`, `AGENTS.md`, `docs/AI_REPO_MAP.md`. (Partner-sleep / "SPEC-008" has **no committed spec file** under `specs/` — it's the AKA-259 project tracked in `AI_TASK_PROGRESS.md`; nothing to edit there.)

> The leaked-error use-case files (`PartnerAccessError`, `FetchPartnerDataUseCase`, `SubmitFeedOpUseCase`, `SubmitSleepOpUseCase`) were already de-Firebased in Task 8, so no `com.google.firebase` imports should remain outside the deleted `data/firebase/` package by the time this task starts — Step 5's `rg` is the gate.

- [ ] **Step 1: Delete Firebase code + config files** (list above). Keep `SharingModule` only if it still provides non-Firebase bindings; remove its `FirebaseFirestore`/`FirebaseAuth` providers.

- [ ] **Step 2: Remove deps** — `firebase-bom`, `firebase-firestore`, `firebase-auth` from the catalog + `app/build.gradle.kts`; drop `alias(libs.plugins.google.services)` and the `google-services` plugin entry.

- [ ] **Step 3: Flip `AntiPatternTest`**

```kotlin
@Test
fun `no firebase imports anywhere`() {
    Konsist.scopeFromProject().files
        .assertFalse { f -> f.imports.any { it.name.startsWith("com.google.firebase") } }
}
@Test
fun `supabase imports restricted to sharing package and DI provider`() {
    // mirror the old firebase rule: supabase imports only in com.babytracker.sharing.. or SupabaseModule
}
```

- [ ] **Step 4: Update docs** — rewrite SPEC-005 Firebase sections as Supabase; update CLAUDE.md tech-stack table + DI module list + the "What NOT to Do" Firebase line; AGENTS.md; AI_REPO_MAP sharing description.

- [ ] **Step 5: Verify**

Run: `rg "com.google.firebase" app/src` → no hits. Run: `./gradlew build` and `./gradlew test` → green. `AntiPatternTest` fails if a Firebase import is reintroduced.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(sharing): remove Firebase and finalize Supabase migration [AKA-173]"
```

---

## Self-Review

- **2026-06-29 reconciliation:** the plan was realigned to the current `feat/firebase-to-supabase-migration` HEAD. Drift folded in: no `SharingRepository` interface (cutover = concrete-type swap at 14 sites, Task 8); leaked Firebase exception types abstracted (Task 8); new `sleep_ops` inbox + `diapers`/`doctor_visits` tables + `sessions.paused_at_ms` / `sleep_records.client_id,started_by` columns (Tasks 1/4/5); live `observeSnapshot`/`observePartnerConnected` streams with the `fromCache` gap (Task 6); outbox covers feed **and** sleep ops at DB v17→v18, kept distinct from the existing owner-side `PartnerOpDrainWorker` (Task 7).
- **Spec coverage:** §3 decisions → Tasks 1/2/7/8-9; §4 mapping table → Tasks 3–7; §5 schema (incl. growth, milestones, diapers, doctor visits) → Task 1; §6 RLS (incl. sleep_ops) → Task 1; §7 components → Tasks 2–7; §8 build/config → Tasks 2 & 9; §9 testing → every task's test step + Task 8; §10 cutover → Task 8; §11 breakdown → Tasks 1–9; §12 risks (offline, RLS, realtime auth, `fromCache`) → Tasks 7, 1+8, 6. No uncovered section.
- **Placeholder scan:** version pins in Task 2 are marked to resolve against the current supabase-kt BOM at execution time (genuine external lookup, not a hidden TODO); all code steps carry real code. Mapping/CRUD/RLS use one concrete representative + an explicit "repeat per type/table" instruction keyed to the Task 1 column contract — DRY, not a placeholder.
- **Type consistency:** row names (`SessionRow`, `FeedOpRow`, `SleepOpRow`, `DiaperRow`, `DoctorVisitRow`, …) and conversion fns (`toRow`/`toSnapshot`/`toFeedOp`/`toSleepOp`/`toPartnerInfo`) are used consistently across Tasks 4–8; `signInAnonymously(): String`, `fetchSnapshot(code): ShareSnapshot`, and the feed/sleep-op + live-observer stream signatures mirror the current `FirestoreSharingService` surface (members + extension functions) that cutover swaps against.
