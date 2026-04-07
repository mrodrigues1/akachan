# CLAUDE.md — BabyTracker (Akachan)

A native Android baby tracking app for parents of infants (0–12 months). Tracks breastfeeding sessions, sleep patterns, and allergies. All data is stored locally — no cloud sync, no remote APIs.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose (BOM 2024.12.01), Material 3 |
| Navigation | Compose Navigation 2.8.5 |
| DI | Hilt 2.52 (KSP 2.0.21-1.0.28) |
| Async | Kotlin Coroutines 1.9.0 + Flow |
| Local DB | Room 2.6.1 |
| Preferences | DataStore 1.1.1 |
| Min SDK | 26 | Target SDK 35 | JVM 17 |
| Testing | JUnit 5, MockK 1.13.13, Turbine 1.2.0, Compose UI Test |

---

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

**Key principles (from `specs/SPEC-001-APP-STRUCTURE.md`):**
- SOLID: depend on abstractions (repository interfaces), single-responsibility use cases
- KISS: flat packages, no mapper classes, no base classes, no sealed `Result<>` wrappers
- DDD: domain models are pure Kotlin data classes — zero framework imports

**Anti-goals:** no multi-module setup, no `Mapper` classes, no `BaseViewModel`, no `BaseFragment`.

---

## Package Structure

```
app/src/main/java/com/babytracker/
├── BabyTrackerApp.kt          # @HiltAndroidApp Application class
├── MainActivity.kt            # Single activity, edge-to-edge Compose host
├── navigation/
│   └── AppNavGraph.kt         # 8 routes: onboarding, home, breastfeeding,
│                              #   breastfeeding_history, sleep, sleep_history,
│                              #   sleep_schedule, settings
├── di/
│   ├── DatabaseModule.kt      # Provides Room DB + DAOs
│   ├── DataStoreModule.kt     # Provides DataStore<Preferences>
│   └── RepositoryModule.kt    # Binds interfaces → implementations
├── domain/
│   ├── model/                 # Pure data classes: Baby, BreastfeedingSession,
│   │                          #   SleepRecord, SleepSchedule
│   │                          # Enums: AllergyType, BreastSide, SleepType
│   ├── repository/            # Interfaces: BabyRepository, BreastfeedingRepository,
│   │                          #   SleepRepository, SettingsRepository
│   └── usecase/
│       ├── baby/              # GetBabyProfile, SaveBabyProfile
│       ├── breastfeeding/     # StartSession, StopSession, GetHistory
│       └── sleep/             # StartRecord, StopRecord, GetHistory, GenerateSchedule
├── data/
│   ├── local/
│   │   ├── BabyTrackerDatabase.kt   # Room DB v1, entities: breastfeeding_sessions, sleep_records
│   │   ├── dao/               # BreastfeedingDao, SleepDao
│   │   ├── entity/            # BreastfeedingEntity, SleepEntity
│   │   └── converter/         # TypeConverter: Instant ↔ Long (epoch ms)
│   └── repository/            # BabyRepositoryImpl, BreastfeedingRepositoryImpl,
│                              #   SleepRepositoryImpl, SettingsRepositoryImpl
├── ui/
│   ├── onboarding/            # OnboardingScreen + OnboardingViewModel
│   ├── home/                  # HomeScreen + HomeViewModel
│   ├── breastfeeding/         # BreastfeedingScreen, BreastfeedingHistoryScreen + VMs
│   ├── sleep/                 # SleepScreen, SleepHistoryScreen, SleepScheduleScreen + VMs
│   ├── settings/              # SettingsScreen + SettingsViewModel
│   ├── component/             # Reusable: TimerDisplay, HistoryCard, SideSelector
│   └── theme/                 # BabyTrackerTheme (MD3, dynamic colors on API 31+)
└── util/
    ├── DateTimeExt.kt         # Instant.formatTime(), formatDateTime(), Duration.formatDuration()
    └── FlowExt.kt             # Flow.catchAndLog()
```

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

### Examples

✅ **Good commits:**
```
feat(breastfeeding): add session duration calculator

Calculates total duration and elapsed time for active sessions.
Handles side switches and provides formatted output.

Closes #42
```

```
fix(sleep): prevent negative duration in sleep records
```

```
test(breastfeeding): add unit tests for StartBreastfeedingSessionUseCase
```

```
docs: add testing conventions to CLAUDE.md
```

❌ **Bad commits:**
```
updated code
```

```
Fix bug in breastfeeding
```

```
wip: work on new feature
```

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

## Database Schema

**Database:** `baby_tracker_db` (Room v1)

**`breastfeeding_sessions`**
| Column | Type | Notes |
|--------|------|-------|
| `id` | Long PK autoGenerate | |
| `start_time` | Long NOT NULL | epoch ms |
| `end_time` | Long NULLABLE | null = in progress |
| `starting_side` | String NOT NULL | "LEFT" or "RIGHT" |
| `switch_time` | Long NULLABLE | epoch ms when sides switched |
| `notes` | String NULLABLE | |

**`sleep_records`**
| Column | Type | Notes |
|--------|------|-------|
| `id` | Long PK autoGenerate | |
| `start_time` | Long NOT NULL | epoch ms |
| `end_time` | Long NULLABLE | null = in progress |
| `sleep_type` | String NOT NULL | "NAP" or "NIGHT_SLEEP" |
| `notes` | String NULLABLE | |

**DataStore (`baby_tracker_prefs`):** baby name, birth date, allergies, onboarding flag, feed timing limits.

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
- **`RepositoryModule`** (`@InstallIn(SingletonComponent::class)`) — binds repository interfaces to implementations with `@Binds @Singleton`

All repository implementations are `@Singleton` scoped.

---

## Specifications

Detailed feature specs live in `specs/`:

- `specs/SPEC-001-APP-STRUCTURE.md` — architecture, package layout, design principles, DB schema
- `SPEC-002-Onboarding.md` — onboarding flow (3 steps: baby name → DOB → allergies), validation rules, persistence strategy

Read specs before implementing new features — they define the intended behaviour and non-goals.

---

## What NOT to Do

- Do not add multi-module structure (everything stays in `:app`)
- Do not create Mapper classes — use extension functions on entity/domain types
- Do not create BaseViewModel or BaseFragment
- Do not wrap return values in `sealed class Result<T>` — let exceptions propagate or use nullable types
- Do not add cloud sync, analytics, or remote API calls — local-only is by design
- Do not use KAPT — KSP is configured for all annotation processing (Hilt, Room)
