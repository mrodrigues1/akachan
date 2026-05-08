# Test Coverage — Missing Critical/High/Medium Paths

**Date:** 2026-05-07  
**Scope:** Add missing tests for 11 untested paths + migrate existing JUnit 4 instrumentation tests to JUnit 5

---

## Context

Analysis revealed 13 untested critical paths (42% of total). All ViewModels and notification managers are covered; gaps are in the data layer (DAOs, repositories) and several use cases.

---

## Section 1: Organization & Gradle

### Gradle changes

1. **`libs.versions.toml`** — add mannodermaus plugin version + library aliases for `android-test-core` and `android-test-runner`
2. **`build.gradle.kts` (root)** — declare `de.mannodermaus.android-junit5` plugin `apply false`
3. **`app/build.gradle.kts`** — apply plugin; add `androidTestImplementation(junit5-api)`, `androidTestRuntimeOnly(junit5-engine)`, `androidTestImplementation(mannodermaus-core)`, `androidTestRuntimeOnly(mannodermaus-runner)`

### Existing androidTest files

| File | Action | Reason |
|------|--------|--------|
| `BreastfeedingDaoTest` | Migrate JUnit 4 → 5 | Needs Android (Room SQLite); now can use JUnit 5 |
| `BabyRepositoryImplTest` | Migrate JUnit 4 → 5 | Needs Android (DataStore context); now can use JUnit 5 |
| `PartnerDashboardScreenTest` | Keep JUnit 4 | Compose `createAndroidComposeRule()` uses `@Rule` — no JUnit 5 equivalent |
| `SettingsScreenTest` | Keep JUnit 4 | Same — android-junit5 runs both side by side |

### New test files

| File | Location | Framework |
|------|----------|-----------|
| `SleepDaoTest` | `androidTest/` | JUnit 5, Room in-memory |
| `StartSleepRecordUseCaseTest` | `test/` | JUnit 5, MockK |
| `StopSleepRecordUseCaseTest` | `test/` | JUnit 5, MockK |
| `SleepRepositoryImplTest` | `test/` | JUnit 5, MockK |
| `BreastfeedingRepositoryImplTest` | `test/` | JUnit 5, MockK |
| `BreastfeedingNotificationReceiverTest` | `test/` | JUnit 5, MockK + spyk |
| `StopBreastfeedingSessionUseCaseTest` | `test/` | JUnit 5, MockK |
| `GetBreastfeedingHistoryUseCaseTest` | `test/` | JUnit 5, MockK + Turbine |
| `GetSleepHistoryUseCaseTest` | `test/` | JUnit 5, MockK + Turbine |
| `GetBabyProfileUseCaseTest` | `test/` | JUnit 5, MockK + Turbine |
| `SharingRepositoryImplTest` | `test/` | JUnit 5, MockK |

---

## Section 2: CRITICAL tier

### `SleepDaoTest` — `androidTest/`, JUnit 5, Room in-memory

| Test | Verifies |
|------|---------|
| `insertRecord_returnsGeneratedId` | returned id > 0 |
| `insertRecord_andGetAll_returnsRecord` | record in `getAllRecords()` flow |
| `getAllRecords_orderedByStartTimeDesc` | most recent first |
| `getAllRecords_emptyDb_returnsEmptyList` | empty state |
| `updateRecord_updatesEndTime` | `endTime` stored after update |
| `updateRecord_keepingNullEndTime_remainsNull` | update without endTime keeps null |
| `getCompletedRecordsSince_excludesInProgress` | null `endTime` rows excluded |
| `getCompletedRecordsSince_excludesBeforeCutoff` | rows before `sinceMillis` excluded |
| `getCompletedRecordsSince_includesExactCutoffMs` | row at exact cutoff included (boundary) |
| `getCompletedRecordsSince_emptyDb_returnsEmpty` | empty state |
| `getRecentRecords_respectsLimit` | only `limit` rows returned |
| `getRecentRecords_limitExceedsCount_returnsAll` | no crash when limit > count |
| `getRecentRecords_orderedByStartTimeDesc` | most recent first within limit |

### `StartSleepRecordUseCaseTest` — `test/`, JUnit 5, MockK

| Test | Verifies |
|------|---------|
| `invokeWithNap_createsRecordWithNapType` | `sleepType = NAP` |
| `invokeWithNightSleep_createsRecordWithNightSleepType` | `sleepType = NIGHT_SLEEP` |
| `invoke_returnedRecord_hasIdFromRepository` | id from `insertRecord()` propagated |
| `invoke_returnedRecord_hasNonNullStartTime` | `startTime` set |
| `invoke_returnedRecord_hasNullEndTime` | record starts in-progress |
| `invoke_callsRepositoryInsertRecordExactlyOnce` | no double-insert |

### `StopSleepRecordUseCaseTest` — `test/`, JUnit 5, MockK

| Test | Verifies |
|------|---------|
| `invoke_matchingInProgress_updatesWithNonNullEndTime` | happy path |
| `invoke_matchingInProgress_preservesOtherFields` | `sleepType`/`startTime`/`notes` unchanged |
| `invoke_wrongId_doesNotCallUpdate` | id mismatch → early return |
| `invoke_recordAlreadyStopped_doesNotCallUpdate` | `endTime != null` → skipped |
| `invoke_emptyRecordList_doesNotCallUpdate` | empty flow → no crash |
| `invoke_multipleRecords_updatesOnlyMatchingOne` | exactly 1 `updateRecord` call |

---

## Section 3: HIGH tier

### `SleepRepositoryImplTest` — `test/`, JUnit 5, MockK

| Test | Verifies |
|------|---------|
| `getAllRecords_mapsEntitiesToDomain` | `SleepEntity` → `SleepRecord` conversion |
| `getAllRecords_emptyList_emitsEmpty` | empty flow passes through |
| `getCompletedRecordsSince_passesEpochMsToDao` | `Instant.toEpochMilli()` forwarded |
| `getCompletedRecordsSince_mapsResultsToDomain` | entity list → domain list |
| `getCompletedRecordsSince_emptyResult_returnsEmpty` | empty DAO result |
| `getRecentRecords_passesLimitToDao` | limit arg forwarded |
| `getRecentRecords_mapsResultsToDomain` | entity list → domain list |
| `insertRecord_convertsToEntityBeforeInsert` | `SleepRecord.toEntity()` fields correct |
| `insertRecord_returnsIdFromDao` | DAO-returned id propagated |
| `updateRecord_convertsToEntityBeforeUpdate` | `endTime` epoch-ms conversion verified |

### `BreastfeedingRepositoryImplTest` — `test/`, JUnit 5, MockK

| Test | Verifies |
|------|---------|
| `getAllSessions_mapsEntitiesToDomain` | entity → domain conversion |
| `getAllSessions_emptyList_emitsEmpty` | empty flow |
| `getActiveSession_nullEntity_returnsNull` | null DAO result → null domain |
| `getActiveSession_entity_mapsToDomain` | active session mapped |
| `getLastSession_nullEntity_returnsNull` | null passthrough |
| `getLastSession_entity_mapsToDomain` | last session mapped |
| `getRecentSessions_mapsEntities` | list mapping |
| `insertSession_convertsToEntityAndDelegates` | `BreastSide` enum stored as string |
| `insertSession_returnsIdFromDao` | id propagated |
| `updateSession_convertsToEntityAndDelegates` | nullable fields (`switchTime`, `pausedAt`) converted correctly |

### `StopBreastfeedingSessionUseCaseTest` — `test/`, JUnit 5, MockK

| Test | Verifies |
|------|---------|
| `invoke_callsUpdateSessionExactlyOnce` | no double-update |
| `invoke_setsNonNullEndTime` | `endTime != null` |
| `invoke_preservesSessionId` | id unchanged |
| `invoke_preservesStartingSide` | side unchanged |
| `invoke_preservesPausedDurationMs` | accumulated pause time kept |
| `invoke_alreadyStoppedSession_stillUpdates` | no guard — always updates (documents intended behavior) |

### `BreastfeedingNotificationReceiverTest` — `test/`, JUnit 5, MockK + spyk

**Strategy:** `spyk(receiver)` stubs `goAsync()` → relaxed mock `PendingResult`. All cases (positive and negative) synchronize via `CountDownLatch(1)` on `result.finish()` — `finish()` is called in the receiver's `finally{}` block unconditionally, making it a reliable coroutine-completion signal. After latch await, verify `NotificationHelper` call expectations. No fixed-time delays.

**Rationale for keeping in `test/` (not `androidTest/`):** All other `@AndroidEntryPoint` receivers in this project (`BreastfeedingActionReceiver`, `SleepActionReceiver`) are JVM unit tests with manual dependency injection. Hilt injection correctness is compile-time verified by KSP. Moving only this receiver to androidTest creates inconsistency without eliminating the gap shared by all existing receiver tests.

| Test | Verifies |
|------|---------|
| `switchSideType_richDisabled_showsSwitchSideNotification` | `showSwitchSide()` called |
| `switchSideType_richEnabled_passesRichFlagTrue` | `richEnabled=true` forwarded |
| `switchSideType_passesSessionIdFromIntent` | `session_id` extra propagated |
| `switchSideType_passesElapsedMinutesFromIntent` | `elapsed_minutes` extra propagated |
| `switchSideType_missingCurrentSide_defaultsToLeft` | null `current_side` → `"LEFT"` |
| `maxTotalType_richDisabled_showsFeedingLimitNotification` | `showFeedingLimit()` called |
| `maxTotalType_richEnabled_passesRichFlagTrue` | richEnabled forwarded |
| `maxTotalType_passesMaxTotalMinutesFromIntent` | `max_total_minutes` extra propagated |
| `nullNotificationType_doesNotCallAnyNotification` | null type → no show call |
| `unknownNotificationType_doesNotCallAnyNotification` | unknown string → no show call |
| `finishCalledOnPendingResult` | `result.finish()` always called in finally |

---

## Section 4: MEDIUM tier

### `GetBreastfeedingHistoryUseCaseTest` — `test/`, JUnit 5, MockK + Turbine

| Test | Verifies |
|------|---------|
| `invoke_delegatesToRepositoryGetAllSessions` | repository called exactly once |
| `invoke_emitsSessionsFromRepository` | flow items pass through unchanged |
| `invoke_emptyFlow_emitsEmpty` | empty state |
| `invoke_multipleEmissions_allPropagated` | 2+ emissions |

### `GetSleepHistoryUseCaseTest` — `test/`, JUnit 5, MockK + Turbine

| Test | Verifies |
|------|---------|
| `invoke_delegatesToRepositoryGetAllRecords` | repository called exactly once |
| `invoke_emitsRecordsFromRepository` | flow items pass through |
| `invoke_emptyFlow_emitsEmpty` | empty state |
| `invoke_multipleEmissions_allPropagated` | 2+ emissions |

### `GetBabyProfileUseCaseTest` — `test/`, JUnit 5, MockK + Turbine

| Test | Verifies |
|------|---------|
| `invoke_delegatesToRepositoryGetBabyProfile` | repository called exactly once |
| `invoke_emitsBabyFromRepository` | non-null baby passes through |
| `invoke_emitsNullWhenRepositoryReturnsNull` | null profile state |
| `invoke_multipleEmissions_allPropagated` | null → baby profile transition |

### `SharingRepositoryImplTest` — `test/`, JUnit 5, MockK

Each test verifies: (1) delegates to `FirestoreSharingService`, (2) unwraps `ShareCode.value` correctly.

| Test | Verifies |
|------|---------|
| `signInAnonymously_delegatesToService_returnsUid` | uid propagated |
| `createShareDocument_passesCodeValueAndOwnerUid` | `code.value` not `code` object |
| `isShareCodeValid_valid_returnsTrue` | true propagated |
| `isShareCodeValid_invalid_returnsFalse` | false propagated |
| `syncFullSnapshot_passesCodeValueAndSnapshot` | snapshot forwarded |
| `syncSessions_passesCodeValueAndList` | list forwarded |
| `syncSleepRecords_passesCodeValueAndList` | list forwarded |
| `syncBaby_passesCodeValueAndSnapshot` | baby snapshot forwarded |
| `registerPartner_passesCodeValueAndUid` | uid forwarded |
| `fetchSnapshot_passesCodeValue_returnsSnapshot` | snapshot returned |
| `isPartnerConnected_passesCodeValueAndUid` | result propagated |
| `getPartners_passesCodeValue_returnsList` | list returned |
| `revokePartner_passesCodeValueAndUid` | delegation verified |
| `deleteShareDocument_passesCodeValue` | delegation verified |

---

## Constraints

- No `@Suppress` annotations — fix violations directly
- No Mapper classes — entity/domain conversion via extension functions
- Commit per logical unit following Conventional Commits (`test(sleep):`, `test(breastfeeding):`, `chore(deps):`)
- Run `./gradlew ktlintFormat && ./gradlew detekt` before each commit
