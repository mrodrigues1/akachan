# AGENTS.md — BabyTracker (Akachan)

A native Android baby tracking app for parents of infants (0–12 months). Tracks feeding (breast, bottle, pumping), sleep, diapers, growth, milestones, vaccines, doctor visits, and allergies. Core tracking data is stored locally. An optional partner-sharing feature syncs a read-only snapshot to Firebase Firestore via anonymous Firebase Auth.

---

## Tech Stack

Single `:app` module. Kotlin + Jetpack Compose (Material 3), Hilt for DI (KSP — never KAPT), Room (schema JSONs exported to `app/schemas/`), DataStore Preferences, Compose Navigation, Coroutines + Flow, WorkManager, Glance home-screen widgets, Quick Settings tiles, Firebase Firestore + anonymous Auth (partner sharing only), Vico charts (compose-m3), sh.calvin.reorderable (drag-and-drop), Kotlinx Serialization JSON.

- SDK: min 26, compile 36, target 35, JVM 17.
- Tests: JUnit 5 + MockK + Turbine + Robolectric + Konsist (unit); JUnit 4 + Compose Test + Espresso (instrumented); Compose Preview Screenshot Testing.
- Quality: ktlint + detekt (with Compose rules), Kover coverage gate.
- Versions: `gradle/libs.versions.toml` is authoritative — versions are deliberately not duplicated here. Known pins: DataStore stays 1.1.1 and Kover stays 0.9.1 (newer versions break DataStore-rename tests and coverage reporting; only a full build catches it).

---

## Architecture

**Key principles (from `specs/SPEC-001-APP-STRUCTURE.md`):**
- SOLID: depend on abstractions (repository interfaces), single-responsibility use cases
- KISS: flat packages, no mapper classes, no base classes, no sealed `Result<>` wrappers
- DDD: domain models are pure Kotlin data classes — zero framework imports
- UI: Material 3 with custom "Baby" palette, rounded shapes, and one-handed usability

**Anti-goals:** no multi-module setup, no `Mapper` classes, no `BaseViewModel`, no `BaseFragment`.

**Product & design context:** `PRODUCT.md` (target users, design principles, accessibility baselines — WCAG AA, 48dp targets, dark-first) and `DESIGN.md` (the design system: palettes incl. per-feature domain colors, typography, elevation, component catalog). Read `DESIGN.md` before any UI work.

---

## Repo Map

Read `docs/AI_REPO_MAP.md` first. Use it to identify the minimum files needed.

- Feature work: `docs/AI_FEATURE_MAP.md` maps every feature to its screens, ViewModels, repositories, entities, use cases, and routes.
- Persisted-data changes: follow `docs/AI_DATA_CHANGE_CHECKLIST.md` — the ripple goes far beyond the Room migration (backup, CSV, partner sync, firestore rules).

---

## Search Efficiency

Minimize token usage. Search first, read second. Never scan the whole repo.

### Preferred commands

Find content:

```bash
rg "SymbolName"
rg "functionName\(" -g "*.kt"
rg -l "SymbolName"

findstr /S /N /I "SymbolName" *.kt
```

Find files:

```bash
rg --files | rg "Dashboard|Partner"

dir /S /B *Dashboard*
where /R . *Dashboard*
```

Read only relevant sections (never whole large files):

```bash
powershell -Command "Get-Content path\to\file.kt | Select-Object -Skip 120 -First 100"

sed -n '120,220p' path/to/file
```

### Rules

- Prefer "rg"; fallback to native Windows commands ("findstr", "dir", "where", PowerShell).
- Read files in small chunks only (±50–100 lines around matches).
- Narrow searches to relevant folders/file types.
- Avoid scanning unrelated modules, generated files, "build/", ".gradle/", lockfiles.
- Make the smallest reasonable change.

---

## Validation Commands

Run commands from the repo root. On Windows PowerShell, use `.\gradlew.bat` if `./gradlew` does not execute.

### Gradle rules — read before running any build

1. **Run Gradle with the Bash sandbox disabled — the daemon is then safe and fast.** The sandboxed Bash tool kills the daemon the moment the test JVM forks — the build dies with `Gradle build daemon has been stopped: stop command received`. That is a harness kill, **not** a test failure. Unsandboxed, the daemon survives and reuse roughly halves iteration time (measured: single-class test 5 s warm daemon vs 11 s `--no-daemon`). Use `--no-daemon` only if forced to run sandboxed, or for a one-off build that must leave nothing running. One idle daemon left behind is normal — Gradle reuses it and it expires after 3 h idle.
2. **Never run two Gradle builds concurrently.** A second invocation stops the first one's daemon (same "stop command received" error). Before starting a build, be sure no earlier one is still running. `./gradlew --status` lists daemons; `./gradlew --stop` cleans up stale ones.
3. **Set the tool timeout to 10 minutes (600000 ms) up front and wait for the result.** Never kill a running build to retry it with a bigger timeout — the first run was likely fine, and the retry collides with it (rule 2). If a run can exceed 10 min (full connected suite, `build`), run it in the background and wait for the completion notification.
4. **Don't trust exit codes through pipes.** `./gradlew ... | tail` reports tail's exit code (always 0) even when the build failed. Append `; echo "EXIT:${PIPESTATUS[0]}"` in bash, or read the `BUILD SUCCESSFUL` / `BUILD FAILED` line.

### Running tests — one command per test type

| Test type | Source | Command |
|-----------|--------|---------|
| Unit — full suite, dev loop | `app/src/test/` | `./gradlew :app:testDebugUnitTest -PfastTests` |
| Unit — full suite, pre-commit | `app/src/test/` | `./gradlew :app:testDebugUnitTest` |
| Unit — single class | | `./gradlew :app:testDebugUnitTest --tests "com.babytracker.util.VolumeExtTest"` |
| Instrumented — full suite | `app/src/androidTest/` | `./gradlew :app:connectedDebugAndroidTest` (emulator required — see below) |
| Instrumented — single class | | add `-Pandroid.testInstrumentationRunnerArguments.class=com.babytracker.ui.vaccine.VaccineSheetTest` |
| Instrumented — single method | | same property with `#methodName` appended to the class FQN |
| Screenshot — validate | `app/src/screenshotTest/` | `./gradlew :app:validateDebugScreenshotTest` |
| Screenshot — record new goldens | | `./gradlew :app:updateDebugScreenshotTest` |
| Firestore rules | `firebase/` | `cd firebase && npm test` — see `firebase/README.md` (incl. the leaked-JVM-on-port-8080 fix) |
| Coverage gate | | `./gradlew :app:koverVerify` (CI enforces ≥60% line coverage) |

- `-PfastTests` skips the Konsist architecture tests (tagged `architecture`) — the slowest part of the unit suite. Use it in dev loops; drop it for the pre-commit run. CI runs the full suite (`./gradlew test :app:koverVerify`).
- Measured durations (warm daemon + cache): single unit class ~5 s; full unit suite with `-PfastTests` ~1 m 20 s; screenshot suite ~35 s; single instrumented class ~1–3 min (includes install). The full connected suite is long — CI shards it in two; locally run only the classes you touched.
- Do not add `maxParallelForks` to the unit test task — measured slower (1 m 47 s with 4 forks vs 1 m 17 s single fork; per-fork Robolectric/MockK-agent init outweighs the parallelism at this suite size).

### Emulator (instrumented tests)

An emulator is usually already running — **never claim "no device available" without checking first**:

```bash
adb devices
```

`adb` is on the Windows user PATH and in `~/.bashrc`. If a stale shell still can't find it, use the full path: `C:/Users/mathe/AppData/Local/Android/Sdk/platform-tools/adb.exe` (from `sdk.dir` in `local.properties`). When passing device paths like `/sdcard/...` in git bash, prefix the command with `MSYS_NO_PATHCONV=1`.

### Known flakes — verify in isolation before chasing

- **Full unit suite:** a *different, unrelated* class fails each run with `Dispatchers.Main was accessed when the test dispatcher was unset` or `UncaughtExceptionsBeforeTest` — main-dispatcher pollution bleeding across classes in the shared JVM fork. Re-run the named class alone with `--tests`; if green, it is pre-existing pollution, not your change.
- **Instrumented:** `Activity never becomes requested state "[DESTROYED]"` — emulator teardown flake. Re-run the single method in isolation; if green, it was a flake.

### Build validation

```bash
# Build validation when production code, resources, manifests, DI, Room, or Gradle files changed
./gradlew build
```

### Before commit or PR

```bash
# Full local validation — also the only local check that catches missing
# translations/plurals and release (R8/lint) issues
./gradlew build
```

Formatting and static analysis run automatically in the pre-commit hook (`ktlintFormat` + `detekt`) — do not run them manually unless diagnosing a specific failure (see Code Quality).

### Targeted checks

```bash
# Formatting check only (only lints .kts — use ktlintFormat to validate .kt files)
./gradlew ktlintCheck

# Release bundle validation
./gradlew bundleRelease
```

### CI — what blocks a PR

- `lint.yml`: `./gradlew ktlintCheck detekt`
- `pr-checks.yml`: `./gradlew test :app:koverVerify` (≥60% line coverage) plus the full connected suite, sharded ×2 on an API 29 emulator
- Android `lintDebug` is **not** run by CI — lint/resource/translation errors will not block a PR. Catch them locally with `./gradlew build`.

If a validation command fails, read the failing output and fix the cause before rerunning the same command — rerunning without a fix wastes a full build. Do not guess validation commands; every test command needed is listed above.

---

## Naming Conventions

| Artifact | Convention | Example |
|----------|-----------|---------|
| ViewModel | `*ViewModel` | `OnboardingViewModel` |
| UI State | `*UiState` (data class) | `OnboardingUiState` |
| Screen composable | `*Screen` | `OnboardingScreen` |
| Repository interface | `*Repository` | `BreastfeedingRepository` |
| Repository impl | `*RepositoryImpl` | `BreastfeedingRepositoryImpl` |
| Use case | `*UseCase` | `PredictNextFeedUseCase` |
| Room DAO | `*Dao` | `BreastfeedingDao` |
| Room entity | `*Entity` | `BreastfeedingEntity` |
| BroadcastReceiver | `*Receiver` | `SleepActionReceiver` |
| WorkManager worker | `*Worker` | `PartnerOpDrainWorker` |
| Quick Settings tile | `*TileService` | `SleepTileService` |
| Glance widget | `*Widget` + `*WidgetReceiver` | `MilkStashWidget` |
| Extension functions | Appended to type | `fun Instant.formatTime()` |

`*Impl` is a repository-only convention. Scheduler/service interfaces in `manager/` are implemented by `*Manager` or technology-prefixed classes (`NotificationScheduler` → `BreastfeedingNotificationManager`, `WidgetRefreshScheduler` → `WorkManagerWidgetRefreshScheduler`); other roles use `*Coordinator` / `*Controller`.

---

## Conventional Commits

All commits follow the [Conventional Commits](https://www.conventionalcommits.org/) specification.

### Format

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Types

| Type | Purpose | Example |
|------|---------|---------|
| `feat` | New feature | `feat(breastfeeding): add session pause functionality` |
| `fix` | Bug fix | `fix(sleep): correct duration calculation for night sleep` |
| `docs` | Documentation changes | `docs: update README with setup instructions` |
| `style` | Code style (formatting, missing semicolons, etc.) | `style: reformat code to match kotlin conventions` |
| `refactor` | Code refactoring without feature change | `refactor(repository): extract common db query logic` |
| `perf` | Performance improvements | `perf(ui): optimize recomposition in HomeScreen` |
| `test` | Adding or updating tests | `test(breastfeeding): add unit tests for session duration` |
| `chore` | Build, deps, CI, tooling | `chore: upgrade Room dependency to 2.6.2` |
| `ci` | CI/CD pipeline changes | `ci: add lint check to GitHub Actions` |
| `revert` | Revert previous commit | `revert: revert "feat(sleep): add nap prediction"` |

### Scope (Optional)

Scopes identify the area of the codebase:
- `breastfeeding` — Breastfeeding feature (sessions, history, etc.)
- `sleep` — Sleep tracking feature (records, schedule, etc.)
- `onboarding` — Onboarding flow
- `settings` — Settings screen and preferences
- `ui` — UI components and theme
- `db` — Database schema and migrations
- `repository` — Data layer and repositories
- `usecase` — Use cases and business logic
- `navigation` — Navigation graph and routing
- `di` — Dependency injection and modules
- `sharing` — Partner sharing feature, Firebase sync
- `partner` — Partner dashboard UI
- `notification` — Notification managers, schedulers, coordinators
- `receiver` — BroadcastReceiver classes

---

## Workflow

### Branching Model

When working on a new feature, pull `main` branch latest changes and create a new branch from `main` and name it after the feature:
- `feat/breastfeeding-history`
- `fix/sleep-schedule-bug`
- `refactor/settings-screen-refactor`
- `chore/update-room-version`
- `ci/add-android-test-coverage`

### Pull Requests

- Target `main`. PR titles follow the same Conventional Commits format as commit messages.
- Merge strategy: rebase-merge, after CI is green.

---

## Task Progress File

At the start of any work, read `AI_TASK_PROGRESS.md` (repo root). Keep it updated throughout the task so work can be resumed in a different session.

- If empty, create it as a minimal todo list for the current task — no extra prose.
- If not empty, check whether it matches the current task/context. Resume from it if it does; overwrite it if it doesn't.
- Update it as steps complete, not just at the end.
- Keep it terse: short heading, todo list, only the context needed to resume. No history log, no analysis.

---

## Code Patterns

### Use Cases
Optional tier (ADR-0001): write a use case only when it contains behaviour — a branch, computation, time math, validation, or orchestration across repositories/services. For plain CRUD, ViewModels call the repository interface (`domain/repository/*`) directly; never scaffold pass-through `Get*/Observe*/Delete*UseCase` relays.

Single-responsibility classes with `suspend operator fun invoke(...)`:

```kotlin
class UpdateBreastfeedingSessionUseCase @Inject constructor(
    private val repository: BreastfeedingRepository
) {
    suspend operator fun invoke(session: BreastfeedingSession, newStart: Instant, newEnd: Instant?) { ... }
}
```

No interface abstraction unless multiple implementations exist.

### ViewModels
Expose a single `StateFlow<*UiState>` and event handler functions:

```kotlin
@HiltViewModel
class OnboardingViewModel @Inject constructor(...) : ViewModel() {
    val uiState: StateFlow<OnboardingUiState> = ...
    fun onNameChanged(name: String) { ... }
}
```

### Entity ↔ Domain Conversion
Extension functions directly on entity/domain classes — no Mapper classes:

```kotlin
fun BreastfeedingEntity.toDomain(): BreastfeedingSession = ...
fun BreastfeedingSession.toEntity(): BreastfeedingEntity = ...
```

### DateTime
- Always use `java.time.Instant` for timestamps
- Store as epoch milliseconds (Long) in Room and DataStore
- Format via extension functions in `util/DateTimeExt.kt`

### Room Migrations
- The schema version and every migration live in `data/local/BabyTrackerDatabase.kt` — top-level `MIGRATION_X_Y` vals, registered via `.addMigrations(...)` in `di/DatabaseModule.kt`.
- To change the schema: update the entity, bump `version` in the `@Database` annotation, add `MIGRATION_<old>_<new>`, register it in `DatabaseModule`, then build — the schema JSON is exported to `app/schemas/` automatically. Commit that JSON with the change.
- That is only the Room part. A persisted-data change also ripples into backup/export, CSV, and partner sync — follow `docs/AI_DATA_CHANGE_CHECKLIST.md` end to end.

---

## Domain

Check `domain/model/` for pure Kotlin data classes.

---

## Localization (i18n)

The app ships in English (`res/values/strings.xml`) and Brazilian Portuguese (`res/values-pt-rBR/strings.xml`). Full conventions: `docs/i18n-conventions.md`.

- Never hardcode user-facing strings in composables — every new string goes into **both** files.
- Portuguese plurals need the `many` quantity in addition to `one`/`other`.
- A missing translation or missing `many` plural fails only `./gradlew build` (lint) — unit tests and the pre-commit hook will not catch it.

---

## Testing Conventions

Use `Validation Commands` for all test, build, formatting, and static-analysis commands.

Create tests for new features, bug fixes, and edge cases. You do not need to follow strict TDD (test-first); writing tests after the implementation is acceptable. All tests must pass before creating a PR.

**Do not run the full test suite after every task.** After each task, run only the tests related to changed code (e.g., `./gradlew :app:testDebugUnitTest --tests "com.babytracker.foo.BarTest"`). Run the full suite (`./gradlew :app:testDebugUnitTest`) only before committing.

After the feature is complete, run all tests. If there are any broken tests, fix them and re-run until all pass.

### Unit Tests (`src/test/`)
- Framework: JUnit 5 (`@Test`, `@BeforeEach`, `runTest`)
- Mocking: MockK — use `mockk()`, `coEvery`, `coVerify`, `slot<T>()` for argument capture
- Flow testing: Turbine — `flow.test { ... }`
- Test class mirrors production class path

```kotlin
class StartBreastfeedingSessionUseCaseTest {
    private lateinit var repository: BreastfeedingRepository

    @BeforeEach
    fun setup() {
        repository = mockk()
    }

    @Test
    fun `invoke saves session and returns id`() = runTest { ... }
}
```

### Instrumentation Tests (`src/androidTest/`)
- Framework: AndroidJUnit4 (JUnit 4 via Compose Test runner)
- Room: use `Room.inMemoryDatabaseBuilder()` — never test against real disk DB
- Compose: use `createComposeRule()` for UI tests
- Screen tests define anonymous `object : XxxRepository { … }` fakes inline per file — adding a method to a repository interface breaks each of them; update the fakes together with the interface

---

## Dependency Injection

All modules are `@InstallIn(SingletonComponent::class)`. Most live in `di/`; feature-local ones exist too (e.g. `export/di/ExportModule.kt`). The load-bearing ones:

- **`DatabaseModule`** — provides `BabyTrackerDatabase` and a `() -> Instant` now-provider. The 13 DAOs come from **`DaoModule`**.
- **`DataStoreModule`** — provides `DataStore<Preferences>`
- **`RepositoryModule`** — binds ~20 repository interfaces to `*Impl` with `@Binds @Singleton` (plus `WhoReferenceData` and `MilestonePhotoCleaner`)
- **`NotificationSchedulerModule`** — binds scheduler interfaces to `*Manager` implementations; the same file holds sibling modules for permission checking and nap/vaccine/doctor-visit reminders
- **`SharingModule`** — provides the `FirebaseFirestore` and `FirebaseAuth` singletons only. Sharing has **no repository interface** — use cases inject the concrete `FirestoreSharingService`.
- Smaller ones: per-feature settings modules (`FeedSettingsModule`, `SleepSettingsModule`, `InventorySettingsModule`), `TimeModule` (`Clock`, deliberately unscoped `ZoneId`), `ApplicationScopeModule` (`@ApplicationScope CoroutineScope`), `WidgetModule` (widget updaters/refresh scheduler), `ExportModule` (backup source/importer, PDF renderer, `Json`)
- Glance widgets and tiles can't constructor-inject — they use `@EntryPoint` interfaces (`widget/*EntryPoint`, `tile/TileEntryPoint`)

All repository and service implementations are `@Singleton` scoped.

---

## Specifications

Two homes — read the relevant spec before feature work; they define intended behaviour and non-goals:

- `specs/SPEC-00x` — the early core specs: app structure (SPEC-001, as amended by ADR-0001), onboarding, theme, notification architecture, partner sharing, partner bottle feeds.
- `docs/superpowers/specs/` — the de-facto specs for everything shipped since ~May 2026 (vaccines, doctor visits, diaper, growth/milestones, pumping/inventory, widgets, tiles, export, trends, sleep prediction, i18n, …). Dated filenames — search by feature keyword.

---

## What NOT to Do

- Do not add multi-module structure (everything stays in `:app`)
- Do not create Mapper classes — use extension functions on entity/domain types
- Do not create BaseViewModel or BaseFragment
- Do not wrap return values in `sealed class Result<T>` — let exceptions propagate or use nullable types
- Do not add analytics SDKs or new remote API integrations beyond the existing Firebase sharing feature. Extend the partner-sharing feature only within `sharing/` and `di/SharingModule.kt`.
- Do not assume `firestore.rules` deploys itself — CI does not deploy it. After editing rules: `cd firebase && npm test`, then `firebase deploy --only firestore:rules` from the repo root. Skipping the deploy silently breaks rule-gated features in production with `PERMISSION_DENIED`.
- Do not use KAPT — KSP is configured for all annotation processing (Hilt, Room)
- Do not access warning tokens (`WarningAmber`, `WarningContainerAmber`, `OnWarningContainerAmber` and their `*Dark` pairs) through `MaterialTheme.colorScheme` — they are extended, non-M3 semantics and ship as top-level `val`s in `ui/theme/Color.kt`. Import them by name.

---

## Code Quality

- This project uses **ktlint** for Kotlin formatting and **detekt** for static analysis (versions in `gradle/libs.versions.toml`).
- The detekt config lives at `config/detekt.yml` and is tuned for Jetpack Compose and MVVM with StateFlow.
- Formatting is delegated entirely to ktlint; detekt does not enforce formatting rules.
- Use `Validation Commands` for the required before-commit formatting and static-analysis commands.
- A pre-commit hook is available at `scripts/pre-commit`. Install it with: `cp scripts/pre-commit .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit`
- Both tools run automatically on CI (GitHub Actions) on every push to `main` and on every pull request.
- If detekt reports a violation, **always fix the code - never suppress with `@Suppress`**. Each fix should be its own commit using the format `fix(detekt): fix <RuleName> violations`.
- **Do not run `ktlintFormat` or `detekt` manually after finishing a task.** The pre-commit hook runs both automatically on every commit. Only run them manually if you need to diagnose a specific failure.

---

## Agent skills

### Issue tracker

Issues live as Plane work items in the `aka-enterprise` workspace, via the `plane` MCP server. External PRs are not a triage surface. See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical label names used as-is: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
