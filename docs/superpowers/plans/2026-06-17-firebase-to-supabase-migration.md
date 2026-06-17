# Firebase → Supabase Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Firestore + anonymous Firebase Auth in the partner-sharing feature with Supabase (Postgres + GoTrue + Realtime), with no behaviour regression and zero `com.google.firebase` dependencies at the end.

**Architecture:** Firebase is already isolated behind the `SharingRepository` interface; the only Firebase-touching class is `FirestoreSharingService`. We add a parallel `SupabaseSharingService` implementing identical behaviour against normalized Postgres tables, flip the repository's injected service in a single cutover task, then delete all Firebase code. Offline feed-op writes — which the Firestore SDK queued for free — are restored with a Room outbox drained by WorkManager.

**Tech Stack:** Kotlin 2.3.20, Hilt 2.59, Room 2.8.4, WorkManager 2.10.0, Coroutines/Flow, supabase-kt (Auth + Postgrest + Realtime) + Ktor client, Supabase CLI (local Docker stack for tests), JUnit5 / MockK / Turbine / Robolectric / Konsist.

## Global Constraints

- One PR per task; each task = one Linear issue (AKA-165 … AKA-173) and is independently shippable.
- The `SharingRepository` interface is **frozen** — do not change its signatures. The whole migration hides behind it.
- No Mapper classes — use extension functions on snapshot/row types (project rule).
- No `sealed class Result<T>` wrappers, no BaseViewModel/BaseFragment, no KAPT (KSP only), no multi-module.
- DateTime: `java.time.Instant`; store epoch millis (`Long`) — same convention as today.
- Firebase deps remain in the build until AKA-173; only the cutover task (AKA-172) flips the binding.
- Secrets (`SUPABASE_URL`, `SUPABASE_ANON_KEY`) come from `local.properties` / CI secrets via `BuildConfig`; never commit them.
- Snapshots are ephemeral — no historical data migration. Existing Firebase share codes are abandoned at cutover.
- Run `./gradlew test --tests "<changed test>"` per task; full `./gradlew build` before each PR. ktlint/detekt run via the pre-commit hook.
- Source of truth for table columns: the `*Snapshot` models in `sharing/domain/model/` (fields enumerated in Task 1).

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

**Column contract (1:1 with snapshot models):**
- `shares(code text pk, owner_uid text not null, created_at timestamptz default now(), last_sync_at timestamptz)`
- `baby(code text pk → shares, name text, birth_date_ms bigint, allergies text[])`
- `sessions(code text → shares, id bigint, start_time bigint, end_time bigint, starting_side text, switch_time bigint, paused_duration_ms bigint, notes text, pk(code,id))`
- `sleep_records(code text → shares, id bigint, start_time bigint, end_time bigint, sleep_type text, notes text, pk(code,id))`
- `sleep_prediction(code text pk → shares, state_label text, window_start bigint, window_end bigint, best_estimate bigint, confidence text, reasons text[], feed_prompt text, generated_at bigint)`
- `bottle_feeds(code text → shares, client_id text, timestamp bigint, volume_ml int, type text, author text, notes text, pk(code,client_id))`
- `milk_bags(code text → shares, id bigint, collection_date_ms bigint, volume_ml int, notes text, pk(code,id))`
- `inventory(code text pk → shares, total_ml int, bag_count int, updated_at_ms bigint)`
- `growth(code text → shares, type text, taken_at_ms bigint, value_canonical bigint, notes text, pk(code,type,taken_at_ms))`
- `milestones(code text → shares, title text, date_epoch_day bigint, time_minute_of_day int, note text, pk(code,title,date_epoch_day))`
- `partners(code text → shares, uid text, connected_at timestamptz default now(), pk(code,uid))`
- `feed_ops(op_id text pk, code text → shares, author_uid text, action text, entry_client_id text, created_at_ms bigint, timestamp_ms bigint, volume_ml int, type text, notes text, consumed_bag_id bigint)`

All child tables `references shares(code) on delete cascade`.

- [ ] **Step 1: Install + init Supabase CLI**

Run: `supabase init` (creates `supabase/config.toml`). In `config.toml` enable anonymous sign-ins:
```toml
[auth]
enable_anonymous_sign_ins = true
```

- [ ] **Step 2: Write `0001_schema.sql`**

Create all 12 tables exactly per the column contract above. Example (repeat the pattern for every table):
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
-- ...baby, sleep_records, sleep_prediction, bottle_feeds, milk_bags,
--    inventory, growth, milestones, partners, feed_ops likewise.
```
Add the feed-op field validation as CHECK constraints (mirror `isValidFeedOp`):
```sql
alter table feed_ops
  add constraint feed_ops_action_chk check (action in ('create','update','delete')),
  add constraint feed_ops_volume_chk check (volume_ml is null or (volume_ml > 0 and volume_ml <= 5000)),
  add constraint feed_ops_entry_chk  check (char_length(entry_client_id) between 1 and 64),
  add constraint feed_ops_create_fields_chk
    check (action = 'delete' or (timestamp_ms is not null and volume_ml is not null and type is not null));
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
-- repeat this block for baby, sessions, sleep_records, sleep_prediction,
-- bottle_feeds, milk_bags, inventory, growth, milestones:
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
```
Enable Realtime on `feed_ops`: `alter publication supabase_realtime add table feed_ops;`

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
- Produces: `@Serializable` row classes (`ShareRow`, `BabyRow`, `SessionRow`, `SleepRow`, `SleepPredictionRow`, `BottleFeedRow`, `MilkBagRow`, `InventoryRow`, `GrowthRow`, `MilestoneRow`); `fun <Snapshot>.toRow(code): <Row>` + `fun <Row>.toSnapshot(): <Snapshot>`; service methods `createShareDocument`, `isShareCodeValid`, `deleteShareDocument`, `syncFullSnapshot`, `syncSessions`, `syncSleepRecords`, `syncBottleFeeds`, `syncBaby`, `syncInventory` (and growth/milestones inside `syncFullSnapshot`).

- [ ] **Step 1: Failing mapping test (representative — session)**

```kotlin
class SupabaseRowMappingTest {
    @Test
    fun `session snapshot round-trips through row`() {
        val s = SessionSnapshot(id = 1, startTime = 100, endTime = 200,
            startingSide = "LEFT", switchTime = 150, pausedDurationMs = 10, notes = "n")
        assertEquals(s, s.toRow("CODE1234").toSnapshot())
    }
}
```
Add an equivalent assertion for every snapshot type (baby, sleep, sleepPrediction, bottleFeed, milkBag, inventory, growth, milestone). Source fields verbatim from `sharing/domain/model/` (see Task 1 column contract).

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
// syncSleepRecords / syncBottleFeeds / syncMilkBags follow the same delete-by-code + insert pattern.
// syncBaby / syncInventory / upsertPrediction use upsert on the share-keyed PK.
// syncFullSnapshot calls each section writer (incl. growth + milestones) for one snapshot.
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
    // ...sleep_records, sleep_prediction, bottle_feeds, milk_bags, inventory, growth, milestones, shares.last_sync_at
    return ShareSnapshot(/* assemble from the above */)
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

### Task 6: Feed ops realtime subscriptions (online path) — AKA-170

Realtime streams + online write/delete.

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/data/supabase/SupabaseSharingService.kt`
- Create: `app/src/test/java/com/babytracker/sharing/data/supabase/SupabaseSharingServiceFeedOpsTest.kt` (integration)

**Interfaces:**
- Produces: `observeFeedOps(code): Flow<List<FeedOp>>`, `observeOwnFeedOps(code, uid): Flow<List<FeedOp>>`, `writeFeedOp(code, op, onFailure)`, `deleteFeedOps(code, opIds)`. Plus `FeedOpRow` + `FeedOp.toRow(code)` / `FeedOpRow.toFeedOp()` in `SupabaseRowMapping`.

- [ ] **Step 1: Add `FeedOpRow` mapping** (enum ↔ lowercase string for `action`; round-trip test in `SupabaseRowMappingTest`).

- [ ] **Step 2: Implement realtime + writes**

```kotlin
private val realtime get() = client.pluginManager.getPlugin(Realtime)

fun observeFeedOps(code: String): Flow<List<FeedOp>> = feedOpStream(code, ownerUid = null)
fun observeOwnFeedOps(code: String, uid: String): Flow<List<FeedOp>> = feedOpStream(code, ownerUid = uid)

private fun feedOpStream(code: String, ownerUid: String?): Flow<List<FeedOp>> = flow {
    val channel = realtime.channel("feed_ops:$code")
    val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "feed_ops"; filter("code", FilterOperator.EQ, code) }
    channel.subscribe()
    // emit initial select, then re-query on each change; filter by author when ownerUid != null
    emitAll(changes.map { currentFeedOps(code, ownerUid) }.onStart { emit(currentFeedOps(code, ownerUid)) })
}.onCompletion { /* channel.unsubscribe() */ }

suspend fun writeFeedOp(code: String, op: FeedOp, onFailure: (Throwable) -> Unit = {}) =
    runCatching { db.from("feed_ops").upsert(op.toRow(code)) }.onFailure(onFailure).getOrThrow()

suspend fun deleteFeedOps(code: String, opIds: List<String>) {
    opIds.chunked(BATCH_LIMIT).forEach { chunk -> db.from("feed_ops").delete { filter { isIn("op_id", chunk) } } }
}
```

- [ ] **Step 3: Integration test**

Against `supabase start`: owner + connected partner. Partner `writeFeedOp`; assert owner's `observeFeedOps` emits it (Turbine). Assert a second partner does NOT receive another partner's op (RLS on subscription). `deleteFeedOps` removes + emits.

Run: `./gradlew test --tests "com.babytracker.sharing.data.supabase.SupabaseSharingServiceFeedOpsTest"` → PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/data/supabase/ app/src/test/java/com/babytracker/sharing/data/supabase/SupabaseSharingServiceFeedOpsTest.kt
git commit -m "feat(sharing): add Supabase realtime feed ops [AKA-170]"
```

---

### Task 7: Offline write outbox for feed ops — AKA-171

Restore offline-first feed-op writes with a Room outbox + WorkManager.

**Files:**
- Create: `app/src/main/java/com/babytracker/sharing/data/supabase/outbox/FeedOpOutboxEntity.kt`
- Create: `app/src/main/java/com/babytracker/sharing/data/supabase/outbox/FeedOpOutboxDao.kt`
- Create: `app/src/main/java/com/babytracker/sharing/data/supabase/outbox/FeedOpSyncWorker.kt`
- Modify: the Room database class + add a Room migration; `SupabaseSharingService` (route `writeFeedOp`, union into `observeOwnFeedOps`)
- Create: `app/src/test/java/com/babytracker/sharing/data/supabase/outbox/FeedOpOutboxDaoTest.kt` (Room in-memory) + `FeedOpSyncWorkerTest.kt`

**Interfaces:**
- Consumes: `FeedOpOutboxDao`, `SupabaseSharingService` online write.
- Produces: `writeFeedOp` persists locally + enqueues unique work; `FeedOpSyncWorker` drains pending rows and marks synced; `observeOwnFeedOps` unions unsynced outbox rows.

- [ ] **Step 1: Entity + DAO + migration**

```kotlin
@Entity(tableName = "feed_op_outbox")
data class FeedOpOutboxEntity(
    @PrimaryKey val opId: String,
    val code: String, val action: String, val entryClientId: String,
    val authorUid: String, val createdAtMs: Long,
    val timestampMs: Long?, val volumeMl: Int?, val type: String?,
    val notes: String?, val consumedBagId: Long?,
    val synced: Boolean = false,
)
```
DAO: `upsert`, `pending(): List<…>` (where `synced = 0`), `markSynced(opId)`, `observePending(code, uid): Flow<List<…>>`. Bump DB version + add a `MIGRATION_n_n+1` creating the table; register it; add a Room schema export test if the project has one.

- [ ] **Step 2: DAO test** (Room `inMemoryDatabaseBuilder`): insert pending, observe, markSynced removes from pending, dedupe on `opId`.

- [ ] **Step 3: Worker**

```kotlin
@HiltWorker
class FeedOpSyncWorker @AssistedInject constructor(
    @Assisted ctx: Context, @Assisted params: WorkerParameters,
    private val dao: FeedOpOutboxDao, private val service: SupabaseSharingService,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        dao.pending().forEach { service.writeFeedOpDirect(it.toFeedOp(), it.code); dao.markSynced(it.opId) }
        Result.success()
    } catch (e: Exception) { Result.retry() }
}
```
Enqueue as unique work with exponential backoff from `writeFeedOp`.

- [ ] **Step 4: Worker test** (faked service): drains pending → marks synced; service failure → `Result.retry()` and rows stay pending.

- [ ] **Step 5: Route writes + union reads** — `writeFeedOp` inserts to outbox + enqueues; `observeOwnFeedOps` merges realtime rows with unsynced outbox rows, de-duped by `opId`. Turbine test for the union.

- [ ] **Step 6: Run** `./gradlew test --tests "com.babytracker.sharing.data.supabase.outbox.*"` → PASS; `./gradlew build`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/data/supabase/outbox/ app/src/test/java/com/babytracker/sharing/data/supabase/outbox/ <db + migration files>
git commit -m "feat(sharing): add offline feed-op outbox with WorkManager sync [AKA-171]"
```

---

### Task 8: Cutover — rebind repository + migrate integration tests — AKA-172

The single binding flip. Firebase code still physically present for one-revert rollback.

**Files:**
- Modify: `app/src/main/java/com/babytracker/sharing/data/repository/SharingRepositoryImpl.kt` (inject `SupabaseSharingService`)
- Modify/replace: `FirestoreSharingServiceBottleFeedRoundTripTest`, `FirestoreSharingServiceInventoryRoundTripTest`, `FirestoreSnapshotMappingTest` → Supabase equivalents
- Modify: CI workflow + test harness (replace Firebase emulator with `supabase start`)

**Interfaces:**
- Consumes: complete `SupabaseSharingService` (Tasks 3–7).
- Produces: `SharingRepositoryImpl` delegating to Supabase; the `SharingRepository` interface is unchanged so all use-case/ViewModel tests pass untouched.

- [ ] **Step 1: Swap the injected service**

```kotlin
class SharingRepositoryImpl @Inject constructor(
    private val service: SupabaseSharingService,   // was FirestoreSharingService
) : SharingRepository { /* body unchanged: still delegates method-for-method */ }
```

- [ ] **Step 2: Re-point integration tests** from Firebase emulator (port 8080) to local Supabase (`supabase start`). Update CI to boot Supabase before tests (resolves the `firebase-emulator-jvm-leak` orphaned-java issue — it goes away).

- [ ] **Step 3: End-to-end test** — primary creates share + syncs snapshot → partner connects + fetches → partner logs a feed op (online + offline-then-online) → primary observes it. All on Supabase.

- [ ] **Step 4: Run full suite** `./gradlew test` → all green; `./gradlew build`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babytracker/sharing/data/repository/SharingRepositoryImpl.kt app/src/test/ <ci files>
git commit -m "feat(sharing): cut sharing over to Supabase backend [AKA-172]"
```

---

### Task 9: Remove Firebase & update docs — AKA-173

Delete all Firebase artifacts; flip the guardrails.

**Files:**
- Delete: `app/src/main/java/com/babytracker/sharing/data/firebase/FirestoreSharingService.kt`, `FirestoreSnapshotMapping.kt`, `app/src/main/java/com/babytracker/di/SharingModule.kt` Firebase providers, any Firebase-only tests
- Delete: `app/google-services.json`, `firebase.json`, `firestore.rules`
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (remove firebase + google-services plugin)
- Modify: `app/src/test/java/com/babytracker/architecture/AntiPatternTest.kt`
- Modify: `specs/SPEC-005-SHARING-FEATURE.md`, `CLAUDE.md`, `AGENTS.md`, `docs/AI_REPO_MAP.md`

- [ ] **Step 1: Delete Firebase code + config files** (list above). Keep `SharingModule` only if it still binds the repository; remove its `FirebaseFirestore`/`FirebaseAuth` providers.

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

- **Spec coverage:** §3 decisions → Tasks 1/2/7/8-9; §4 mapping table → Tasks 3–7; §5 schema (incl. growth + milestones) → Task 1; §6 RLS → Task 1; §7 components → Tasks 2–7; §8 build/config → Tasks 2 & 9; §9 testing → every task's test step + Task 8; §10 cutover → Task 8; §11 breakdown → Tasks 1–9; §12 risks (offline, RLS, realtime auth) → Tasks 7, 1+8, 6. No uncovered section.
- **Placeholder scan:** version pins in Task 2 are marked to resolve against the current supabase-kt BOM at execution time (genuine external lookup, not a hidden TODO); all code steps carry real code. Mapping/CRUD/RLS use one concrete representative + an explicit "repeat per type/table" instruction keyed to the Task 1 column contract — DRY, not a placeholder.
- **Type consistency:** row names (`SessionRow`, `FeedOpRow`, …) and conversion fns (`toRow`/`toSnapshot`/`toFeedOp`/`toPartnerInfo`) are used consistently across Tasks 4–8; `signInAnonymously(): String`, `fetchSnapshot(code): ShareSnapshot`, and the feed-op stream signatures match the frozen `SharingRepository` interface.
