# QWEN.md — Akachan (BabyTracker)

## Project Overview

**Akachan** is a native Android baby tracker app that helps parents track breastfeeding sessions, sleep records, and baby's growth and health. All data is stored locally — no cloud sync, no remote APIs.

Built with **Jetpack Compose**, **Hilt** for dependency injection, **Room** for local database, and **DataStore** for preferences. Follows MVVM architecture with Clean Architecture principles (Use Cases, Repositories).

### Key Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.3.20 |
| UI | Jetpack Compose (BOM 2026.03.00), Material 3 |
| Navigation | Compose Navigation 2.9.7 |
| DI | Hilt 2.59 (KSP 2.3.6) |
| Async | Kotlin Coroutines 1.9.0 + Flow |
| Local DB | Room 2.8.4 |
| Preferences | DataStore 1.1.1 |
| Min SDK | 26 | Target SDK 35 | JVM 17 |
| Testing | JUnit 5, MockK 1.13.13, Turbine 1.2.0, Compose UI Test |

## Project Structure

```
app/src/main/java/com/babytracker/
├── BabyTrackerApp.kt          # @HiltAndroidApp Application class
├── MainActivity.kt            # Single activity, edge-to-edge Compose host
├── navigation/                # App navigation graph
├── di/                        # Hilt modules (Database, DataStore, Repository)
├── domain/
│   ├── model/                 # Pure Kotlin data classes
│   ├── repository/            # Repository interfaces
│   └── usecase/               # Single-responsibility use cases
├── data/
│   ├── local/                 # Room DB, DAOs, Entities, TypeConverters
│   └── repository/            # Repository implementations
├── ui/                        # Compose screens + ViewModels
│   ├── onboarding/
│   ├── home/
│   ├── breastfeeding/
│   ├── sleep/
│   ├── settings/
│   ├── component/             # Reusable Compose components
│   └── theme/                 # Theme.kt, Color.kt, Shape.kt, Type.kt
└── util/                      # Extension functions (DateTime, Flow)
```

## Building and Running

### Prerequisites
- JDK 17
- Android Studio Ladybug or newer

### Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release bundle (requires signing env vars)
./gradlew bundleRelease

# Run unit tests
./gradlew test

# Run instrumentation tests (requires emulator/device)
./gradlew connectedAndroidTest

# Run lint checks
./gradlew lint

# Install debug APK
./gradlew installDebug
```

### Release Signing
For release builds, set these environment variables:
- `SIGNING_KEYSTORE` (optional, defaults to `release.keystore`)
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

## Architecture

Three-layer clean architecture with unidirectional data flow:

```
UI Layer       Compose Screens → ViewModels → StateFlow<*UiState>
                    ↑                               |
Domain Layer   Use Cases ← ViewModels              |
                    ↓                               |
Data Layer     Repository Impls → Room DAOs / DataStore
                                        ↓
                                  Flow<T> bubbles up
```

### Key Principles
- **SOLID**: Depend on abstractions (repository interfaces), single-responsibility use cases
- **KISS**: Flat packages, no mapper classes, no base classes, no sealed `Result<>` wrappers
- **DDD**: Domain models are pure Kotlin data classes — zero framework imports
- **UI**: Material 3 with custom "Baby" palette, rounded shapes, one-handed usability

### What NOT to Do
- Do not add multi-module structure (everything stays in `:app`)
- Do not create Mapper classes — use extension functions on entity/domain types
- Do not create BaseViewModel or BaseFragment
- Do not wrap return values in `sealed class Result<T>` — let exceptions propagate or use nullable types
- Do not add cloud sync, analytics, or remote API calls — local-only is by design
- Do not use KAPT — KSP is configured for all annotation processing

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

## Testing

Create tests for new features, bug fixes, and edge cases.

### Unit Tests (`src/test/`)
- Framework: JUnit 5 (`@Test`, `@BeforeEach`, `runTest`)
- Mocking: MockK — use `mockk()`, `coEvery`, `coVerify`, `slot<T>` for argument capture
- Flow testing: Turbine — `flow.test { ... }`
- Test class mirrors production class path

### Instrumentation Tests (`src/androidTest/`)
- Framework: AndroidJUnit4 (JUnit 4 via Compose Test runner)
- Room: use `Room.inMemoryDatabaseBuilder()` — never test against real disk DB
- Compose: use `createComposeRule()` for UI tests

## Conventional Commits

All commits follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>[optional scope]: <description>
```

### Types
`feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`, `ci`, `revert`

### Scopes
`breastfeeding`, `sleep`, `onboarding`, `settings`, `ui`, `db`, `repository`, `usecase`, `navigation`, `di`

### Branching Model

When working on a new feature, pull `main` branch latest changes and create a new branch from `main` and name it after the feature:
- `feat/breastfeeding-history`
- `fix/sleep-schedule-bug`
- `refactor/settings-screen-refactor`
- `chore/update-room-version`
- `ci/add-android-test-coverage`

After the feature is complete, run all tests,
if all tests are passing: create a PR to `main` with a descriptive title and description,
if there is any broken test: fix it, re-run tests and do it until all tests pass.

### Branch Naming
- `feat/breastfeeding-history`
- `fix/sleep-schedule-bug`
- `refactor/settings-screen-refactor`
- `chore/update-room-version`

---

## Theme Tokens

| Token | Light Value | Usage |
|-------|-------------|-------|
| `primary` | `#C2185B` | Primary actions, Feeding theme |
| `secondary` | `#1976D2` | Secondary actions, Sleep theme |
| `tertiary` | `#388E3C` | Success states, Warning/Overtime in timers |
| `surface` | `#FFFDE7` | Background, Cards |

## Specifications

Feature specs live in `specs/`:
- `SPEC-001-APP-STRUCTURE.md` — Architecture, package layout, design principles, DB schema
- `SPEC-002-Onboarding.md` — Onboarding flow spec
- `SPEC-003-ThemeSelection.md` — Theme selection spec

Read specs before implementing new features.
