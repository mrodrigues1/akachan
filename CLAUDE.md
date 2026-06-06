# CLAUDE.md — BabyTracker (Akachan)

A native Android baby tracking app for parents of infants (0–12 months). Tracks breastfeeding sessions, sleep patterns, and allergies. Core tracking data is stored locally. An optional partner-sharing feature syncs a read-only snapshot to Firebase Firestore via anonymous Firebase Auth.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.3.20 |
| UI | Jetpack Compose (BOM 2026.03.00), Material 3 |
| Navigation | Compose Navigation 2.9.7 |
| DI | Hilt 2.59 (KSP 2.3.6) |
| Async | Kotlin Coroutines 1.9.0 + Flow |
| Local DB | Room 2.8.4 |
| Preferences | DataStore 1.1.1 |
| Sharing | Firebase BOM 33.7.0 (Firestore KTX, Auth KTX) |
| Widgets | Glance 1.1.1 (appwidget + material3) |
| Background work | WorkManager 2.10.0 |
| Min SDK | 26 | Target SDK 35 | JVM 17 |
| Testing | JUnit 5, MockK 1.13.13, Turbine 1.2.0, Compose UI Test |

> Authoritative versions: `gradle/libs.versions.toml`. Update this table whenever that file changes.

---

## Architecture

**Key principles (from `specs/SPEC-001-APP-STRUCTURE.md`):**
- SOLID: depend on abstractions (repository interfaces), single-responsibility use cases
- KISS: flat packages, no mapper classes, no base classes, no sealed `Result<>` wrappers
- DDD: domain models are pure Kotlin data classes — zero framework imports
- UI: Material 3 with custom "Baby" palette, rounded shapes, and one-handed usability

**Anti-goals:** no multi-module setup, no `Mapper` classes, no `BaseViewModel`, no `BaseFragment`.

---

## Repo Map

Read `docs/AI_REPO_MAP.md` first. Use it to identify the minimum files needed.

---

## Naming Conventions

| Artifact | Convention | Example |
|----------|-----------|---------|
| ViewModel | `*ViewModel` | `OnboardingViewModel` |
| UI State | `*UiState` (data class) | `OnboardingUiState` |
| Screen composable | `*Screen` | `OnboardingScreen` |
| Repository interface | `*Repository` | `BreastfeedingRepository` |
| Repository impl | `*RepositoryImpl` | `BreastfeedingRepositoryImpl` |
| Use case | `*UseCase` | `StartBreastfeedingSessionUseCase` |
| Room DAO | `*Dao` | `BreastfeedingDao` |
| Room entity | `*Entity` | `BreastfeedingEntity` |
| Extension functions | Appended to type | `fun Instant.formatTime()` |

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

---

## Code Patterns

### Use Cases
Single-responsibility classes with `suspend operator fun invoke(...)`:

```kotlin
class StartBreastfeedingSessionUseCase @Inject constructor(
    private val repository: BreastfeedingRepository
) {
    suspend operator fun invoke(side: BreastSide): Long { ... }
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

---

## Domain

Check `domain/model/` for pure Kotlin data classes.

---

## Build & Run

```bash
# Build (assembles debug APK)
./gradlew build

# Unit tests (JUnit 5)
./gradlew test

# Instrumentation tests (requires emulator/device)
./gradlew connectedAndroidTest

# Release bundle
./gradlew bundleRelease
```

Build variants: `debug` (default) and `release` (ProGuard minification enabled via `proguard-android-optimize.txt` + `app/proguard-rules.pro`).

---

## Testing Conventions

Create tests for new features, bug fixes, and edge cases. You do not need to follow strict TDD (test-first); writing tests after the implementation is acceptable. All tests must pass before creating a PR.

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

---

## Dependency Injection

Hilt modules live in `di/`:

- **`DatabaseModule`** (`@InstallIn(SingletonComponent::class)`) — provides `BabyTrackerDatabase`, `BreastfeedingDao`, `SleepDao`
- **`DataStoreModule`** (`@InstallIn(SingletonComponent::class)`) — provides `DataStore<Preferences>`
- **`RepositoryModule`** (`@InstallIn(SingletonComponent::class)`) — binds core repository interfaces to implementations with `@Binds @Singleton`
- **`NotificationSchedulerModule`** (`@InstallIn(SingletonComponent::class)`) — binds `NotificationScheduler` → `BreastfeedingNotificationManager`, `SleepNotificationScheduler` → `SleepNotificationManager`
- **`SharingModule`** (`@InstallIn(SingletonComponent::class)`) — binds `SharingRepository` → `SharingRepositoryImpl`; provides `FirebaseFirestore` and `FirebaseAuth` singletons

All repository and service implementations are `@Singleton` scoped.

---

## Specifications

Detailed feature specs live in `specs/`

Read specs before implementing new features — they define the intended behaviour and non-goals.

---

## What NOT to Do

- Do not add multi-module structure (everything stays in `:app`)
- Do not create Mapper classes — use extension functions on entity/domain types
- Do not create BaseViewModel or BaseFragment
- Do not wrap return values in `sealed class Result<T>` — let exceptions propagate or use nullable types
- Do not add analytics SDKs or new remote API integrations beyond the existing Firebase sharing feature. Extend the partner-sharing feature only within `sharing/` and `di/SharingModule.kt`.
- Do not use KAPT — KSP is configured for all annotation processing (Hilt, Room)
- Do not access warning tokens (`WarningAmber`, `WarningContainerAmber`, `OnWarningContainerAmber` and their `*Dark` pairs) through `MaterialTheme.colorScheme` — they are extended, non-M3 semantics and ship as top-level `val`s in `ui/theme/Color.kt`. Import them by name.

---

## Code Quality

- This project uses **ktlint** (via `ktlint-gradle` plugin v12.1.1) for Kotlin formatting and **detekt** (v1.23.6) for static analysis.
- The detekt config lives at `config/detekt.yml` and is tuned for Jetpack Compose and MVI (Orbit).
- Formatting is delegated entirely to ktlint; detekt does not enforce formatting rules.
- **Before committing**, always run `./gradlew ktlintFormat` to auto-fix formatting, then `./gradlew detekt` to catch code smells.
- A pre-commit hook is available at `scripts/pre-commit`. Install it with: `cp scripts/pre-commit .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit`
- Both tools run automatically on CI (GitHub Actions) on every push to `main` and on every pull request.
- If detekt reports a violation, **always fix the code - never suppress with `@Suppress`**. Each fix should be its own commit using the format `fix(detekt): fix <RuleName> violations`.
- Useful commands:
  - `./gradlew ktlintCheck` - check formatting
  - `./gradlew ktlintFormat` - auto-fix formatting
  - `./gradlew detekt` - run static analysis
  - `./gradlew detektGenerateConfig` - regenerate the base detekt config (do not overwrite `config/detekt.yml` without review)
