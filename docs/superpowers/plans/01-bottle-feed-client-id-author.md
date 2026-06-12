# Plan 01: BottleFeed clientId + author (Room migration 8→9)

LINEAR_ISSUE: [AKA-117](https://linear.app/akachan/issue/AKA-117/spec-007-plan-01-bottlefeed-clientid-author-room-migration-89)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every bottle feed a stable cross-device identity (`clientId`) and an author (`OWNER`/`PARTNER`) so partner-created entries can be synced, attributed, and ownership-gated (SPEC-007).

**Architecture:** Pure data-layer change, zero behavior change. Domain `BottleFeed` gains two fields; `bottle_feeds` table gains two columns via Room migration 8→9 with backfill; all construction sites updated explicitly. Everything defaults to `OWNER`, so the app behaves identically after this PR.

**Tech Stack:** Kotlin, Room 2.8.4, JUnit 5 (unit), AndroidJUnit4 + framework SQLite helper (migration test).

**Spec:** `specs/SPEC-007-PARTNER-BOTTLE-FEED-LOGGING.md` — "Data model" section.

**Dependencies:** None. First plan in the SPEC-007 sequence; plans 02 and 04 depend on it.

**Suggested branch:** `feat/bottle-feed-client-id-author`

---

## File Structure

- Create: `app/src/main/java/com/babytracker/domain/model/FeedAuthor.kt` — new enum.
- Modify: `app/src/main/java/com/babytracker/domain/model/BottleFeed.kt` — add `clientId`, `author`.
- Modify: `app/src/main/java/com/babytracker/data/local/entity/BottleFeedEntity.kt` — add columns + unique index + conversions.
- Modify: `app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt` — version 9, `MIGRATION_8_9`.
- Modify: `app/src/main/java/com/babytracker/di/DatabaseModule.kt` — register `MIGRATION_8_9`.
- Modify: `app/src/main/java/com/babytracker/domain/usecase/bottlefeed/LogBottleFeedUseCase.kt` — generate UUID.
- Modify: `app/src/main/java/com/babytracker/export/data/BackupConverters.kt` and `app/src/main/java/com/babytracker/export/data/BackupImporterImpl.kt` — entity construction sites.
- Modify: `app/src/main/java/com/babytracker/debug/DebugDataSeeder.kt` — if it constructs `BottleFeed`/`BottleFeedEntity` (check at implementation time).
- Test: `app/src/androidTest/java/com/babytracker/data/local/MigrationTest.kt` — add 8→9 test.
- Test: existing unit tests constructing `BottleFeed` — add `clientId` argument.

### Critical Room constraint (read before coding)

After a migration runs, Room validates the live table schema against the schema generated from the entity — **including column default values**. SQLite requires `ALTER TABLE ... ADD COLUMN ... NOT NULL` to carry a `DEFAULT`. Therefore both new entity columns MUST declare a matching `@ColumnInfo(defaultValue = ...)`, or the app crashes post-migration with "Migration didn't properly handle: bottle_feeds". The values must match the migration SQL exactly: `''` for `client_id`, `'OWNER'` for `author`.

---

### Task 1: FeedAuthor enum + domain model

**Files:**
- Create: `app/src/main/java/com/babytracker/domain/model/FeedAuthor.kt`
- Modify: `app/src/main/java/com/babytracker/domain/model/BottleFeed.kt`

- [ ] **Step 1: Create the enum** (pure Kotlin, zero framework imports — DDD rule)

```kotlin
package com.babytracker.domain.model

enum class FeedAuthor { OWNER, PARTNER }
```

- [ ] **Step 2: Extend `BottleFeed`**

`clientId` has **no default** on purpose: every construction site must decide identity explicitly (a silent random default would mask identity bugs in sync code). `author` defaults to `OWNER` because all local writes are owner writes.

```kotlin
package com.babytracker.domain.model

import java.time.Instant

data class BottleFeed(
    val id: Long = 0,
    val clientId: String,
    val timestamp: Instant,
    val volumeMl: Int,
    val type: FeedType,
    val linkedMilkBagId: Long? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val author: FeedAuthor = FeedAuthor.OWNER,
)
```

Note: `clientId` is placed second (matching the spec), which intentionally breaks positional constructor calls — the compiler will enumerate every call site that needs updating (Task 4).

### Task 2: Entity, conversions, unique index

**Files:**
- Modify: `app/src/main/java/com/babytracker/data/local/entity/BottleFeedEntity.kt`

- [ ] **Step 1: Add columns and unique index to the entity**

```kotlin
@Entity(
    tableName = "bottle_feeds",
    foreignKeys = [
        ForeignKey(
            entity = MilkBagEntity::class,
            parentColumns = ["id"],
            childColumns = ["linked_milk_bag_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("timestamp"),
        Index("linked_milk_bag_id"),
        Index(value = ["client_id"], unique = true),
    ],
)
data class BottleFeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "client_id", defaultValue = "''") val clientId: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "volume_ml") val volumeMl: Int,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "linked_milk_bag_id") val linkedMilkBagId: Long? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "author", defaultValue = "'OWNER'") val author: String = FeedAuthor.OWNER.name,
)
```

- [ ] **Step 2: Update both conversion extension functions in the same file**

```kotlin
fun BottleFeedEntity.toDomain(): BottleFeed = BottleFeed(
    id = id,
    clientId = clientId,
    timestamp = Instant.ofEpochMilli(timestamp),
    volumeMl = volumeMl,
    type = FeedType.valueOf(type),
    linkedMilkBagId = linkedMilkBagId,
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
    author = FeedAuthor.valueOf(author),
)

fun BottleFeed.toEntity(): BottleFeedEntity = BottleFeedEntity(
    id = id,
    clientId = clientId,
    timestamp = timestamp.toEpochMilli(),
    volumeMl = volumeMl,
    type = type.name,
    linkedMilkBagId = linkedMilkBagId,
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
    author = author.name,
)
```

Add imports for `FeedAuthor` as needed.

### Task 3: Migration 8→9 + DI wiring

**Files:**
- Modify: `app/src/main/java/com/babytracker/data/local/BabyTrackerDatabase.kt`
- Modify: `app/src/main/java/com/babytracker/di/DatabaseModule.kt`

- [ ] **Step 1: Bump `version = 8` to `version = 9`** in the `@Database` annotation.

- [ ] **Step 2: Add `MIGRATION_8_9`** after `MIGRATION_7_8` (follow the existing object-val pattern):

```kotlin
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE bottle_feeds ADD COLUMN client_id TEXT NOT NULL DEFAULT ''"
        )
        database.execSQL(
            "UPDATE bottle_feeds SET client_id = lower(hex(randomblob(16)))"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_bottle_feeds_client_id ON bottle_feeds(client_id)"
        )
        database.execSQL(
            "ALTER TABLE bottle_feeds ADD COLUMN author TEXT NOT NULL DEFAULT 'OWNER'"
        )
    }
}
```

Backfill note: `lower(hex(randomblob(16)))` produces a 32-hex-char random id per existing row (spec-mandated). It is not RFC-4122 formatted, and that is fine — `clientId` is an opaque string everywhere; only uniqueness matters. Collision probability across a household's feed history is negligible, and the unique index would fail the migration loudly rather than corrupt silently.

- [ ] **Step 3: Register the migration in `DatabaseModule`** — add `MIGRATION_8_9` to the existing `.addMigrations(...)` list and import it.

- [ ] **Step 4: Compile check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAILS — Task 4 call sites don't pass `clientId` yet. The error list is your worklist for Task 4.

### Task 4: Update all construction sites

**Files (compiler output is authoritative; known sites below):**
- Modify: `app/src/main/java/com/babytracker/domain/usecase/bottlefeed/LogBottleFeedUseCase.kt`
- Modify: `app/src/main/java/com/babytracker/export/data/BackupConverters.kt` (`BottleFeedBackup.toEntity()`)
- Modify: `app/src/main/java/com/babytracker/export/data/BackupImporterImpl.kt` (`mergeBottleFeeds` entity construction)
- Modify: `app/src/main/java/com/babytracker/debug/DebugDataSeeder.kt` (if applicable)
- Modify: unit/androidTest files that construct `BottleFeed` (≈10 files; see `rg -n "BottleFeed\(" app/src -g "*.kt"`)

- [ ] **Step 1: `LogBottleFeedUseCase` generates the identity** at creation time:

```kotlin
val id = repository.insert(
    BottleFeed(
        clientId = UUID.randomUUID().toString(),
        timestamp = timestamp,
        volumeMl = volumeMl,
        type = type,
        linkedMilkBagId = linkedBag?.id,
        notes = notes,
        createdAt = now(),
    ),
)
```

Add `import java.util.UUID`.

- [ ] **Step 2: Backup import/converters generate fresh ids** — backups predate `clientId` and the backup JSON format is NOT extended in this plan (YAGNI; revisit only if partner pending-ops-across-restore ever matters):

```kotlin
// BackupConverters.kt — BottleFeedBackup.toEntity()
fun BottleFeedBackup.toEntity() = BottleFeedEntity(
    clientId = UUID.randomUUID().toString(),
    // ...existing args unchanged
)
```

Same pattern in `BackupImporterImpl.mergeBottleFeeds`. Keep the importer's `identity()` dedupe function unchanged — it dedupes on content, and `clientId` must stay out of it.

- [ ] **Step 3: Fix test construction sites.** In tests use deterministic ids, e.g. `clientId = "client-1"`. Tests asserting equality on copied/round-tripped objects must carry the same `clientId` through. Do not add `author` args anywhere — the `OWNER` default is the point.

- [ ] **Step 4: Compile check**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL

### Task 5: Migration test

**Files:**
- Modify: `app/src/androidTest/java/com/babytracker/data/local/MigrationTest.kt`

Two complementary tests are REQUIRED. The behavioral test (Step 2) checks data; only the `MigrationTestHelper` test (Step 1) catches the release-blocking failure mode from the "Critical Room constraint" section — entity `defaultValue` drift vs. migration SQL. Schemas are exported to `app/schemas/` and already wired into androidTest assets (`app/build.gradle.kts:100`), so `MigrationTestHelper` works out of the box.

- [ ] **Step 1: Schema-validation test via `MigrationTestHelper`** (build once first so `9.json` is generated: `./gradlew :app:compileDebugKotlin`):

```kotlin
@get:Rule
val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    BabyTrackerDatabase::class.java,
)

@Test
fun migrate8To9_roomSchemaValidationPasses() {
    helper.createDatabase(TEST_DB, 8).use { db ->
        db.execSQL(
            "INSERT INTO bottle_feeds (timestamp, volume_ml, type, created_at) VALUES (1000, 120, 'FORMULA', 1000)"
        )
    }
    // throws if the migrated schema (incl. column defaults) differs from entity-generated schema 9
    helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9).close()
}
```

with `private const val TEST_DB = "migration-test"` and imports `androidx.room.testing.MigrationTestHelper`, `androidx.test.platform.app.InstrumentationRegistry`. If `androidx.room:room-testing` is not yet an androidTest dependency, add it to `gradle/libs.versions.toml` + `app/build.gradle.kts` (`androidTestImplementation`), same version as Room (2.8.4).

- [ ] **Step 2: Behavioral test** following the existing pattern in the same file (`FrameworkSQLiteOpenHelperFactory`-based; mirror `migrate7To8_...`):

```kotlin
@Test
fun migrate8To9_backfillsUniqueClientId_andDefaultsAuthorToOwner() {
    // create db at version 8 via existing helper pattern, then:
    db.execSQL(
        "INSERT INTO bottle_feeds (timestamp, volume_ml, type, created_at) VALUES (1000, 120, 'FORMULA', 1000)"
    )
    db.execSQL(
        "INSERT INTO bottle_feeds (timestamp, volume_ml, type, created_at) VALUES (2000, 90, 'BREAST_MILK', 2000)"
    )

    MIGRATION_8_9.migrate(db)

    // every row backfilled with a non-empty client_id, author defaults to OWNER
    db.query("SELECT client_id, author FROM bottle_feeds").use { cursor ->
        val seen = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            val clientId = cursor.getString(0)
            assertTrue(clientId.isNotEmpty())
            assertTrue(seen.add(clientId)) // uniqueness across rows
            assertEquals("OWNER", cursor.getString(1))
        }
        assertEquals(2, seen.size)
    }

    // unique index enforced
    db.execSQL(
        "INSERT INTO bottle_feeds (client_id, timestamp, volume_ml, type, created_at, author) VALUES ('dup', 3000, 50, 'FORMULA', 3000, 'OWNER')"
    )
    assertThrows(SQLiteConstraintException::class.java) {
        db.execSQL(
            "INSERT INTO bottle_feeds (client_id, timestamp, volume_ml, type, created_at, author) VALUES ('dup', 4000, 60, 'FORMULA', 4000, 'OWNER')"
        )
    }
}
```

Adapt setup/teardown boilerplate from the neighboring tests in the same file — the version-8 schema must be created the same way `migrate7To8` creates version 7.

- [ ] **Step 3: Run migration tests** (needs emulator/device)

Run: `./gradlew connectedAndroidTest --tests "com.babytracker.data.local.MigrationTest"` (or run the class from Android Studio)
Expected: PASS

### Task 6: Full validation + commit

- [ ] **Step 1: Targeted unit tests**

Run: `./gradlew test --tests "com.babytracker.domain.usecase.bottlefeed.*" --tests "com.babytracker.data.repository.BottleFeedRepositoryImplTest"`
Expected: PASS

- [ ] **Step 2: Full suite before commit**

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat(db): add clientId and author to bottle feeds (migration 8->9)"
```

---

## Acceptance Criteria

- [ ] `BottleFeed` has `clientId: String` (no default) and `author: FeedAuthor = OWNER`; `FeedAuthor` is a pure Kotlin enum.
- [ ] DB version 9; migration 8→9 backfills `client_id` with `lower(hex(randomblob(16)))`, adds unique index, adds `author TEXT NOT NULL DEFAULT 'OWNER'`.
- [ ] Entity columns declare `defaultValue` matching the migration SQL (`''` / `'OWNER'`) so Room post-migration validation passes.
- [ ] `LogBottleFeedUseCase` assigns `UUID.randomUUID().toString()`; backup import generates fresh ids; importer dedupe excludes `clientId`.
- [ ] `MigrationTestHelper.runMigrationsAndValidate(..., 9, true, MIGRATION_8_9)` passes — Room schema validation (incl. column defaults) is mandatory, not implied.
- [ ] Behavioral migration test proves: backfill non-empty + unique, author defaults `OWNER`, unique index rejects duplicates.
- [ ] Full unit test suite green; no behavior change anywhere (everything is `OWNER`).
