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
- UI: Material 3 with custom "Baby" palette, rounded shapes, and one-handed usability

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
│       ├── breastfeeding/     # StartSession, StopSession, GetHistory, SwitchSide,
│       │                      #   PauseSession, ResumeSession
│       └── sleep/             # StartRecord, StopRecord, GetHistory, GenerateSchedule
├── data/
│   ├── local/
│   │   ├── BabyTrackerDatabase.kt   # Room DB v2, entities: breastfeeding_sessions, sleep_records
│   │   ├── dao/               # BreastfeedingDao, SleepDao
│   │   ├── entity/            # BreastfeedingEntity, SleepEntity
│   │   └── converter/         # TypeConverter: Instant ↔ Long (epoch ms)
│   └── repository/            # BabyRepositoryImpl, BreastfeedingRepositoryImpl,
│                              #   SleepRepositoryImpl, SettingsRepositoryImpl
├── manager/
│   ├── NotificationScheduler.kt          # Interface: schedule/cancel AlarmManager alarms
│   └── BreastfeedingNotificationManager.kt  # Impl: schedules max-time and per-breast alarms
├── ui/
│   ├── onboarding/            # OnboardingScreen + OnboardingViewModel
│   ├── home/                  # HomeScreen + HomeViewModel
│   ├── breastfeeding/         # BreastfeedingScreen, BreastfeedingHistoryScreen + VMs
│   ├── sleep/                 # SleepTrackingScreen, SleepHistoryScreen, SleepScheduleScreen + VMs
│   ├── settings/              # SettingsScreen + SettingsViewModel
│   ├── component/             # Reusable: TimerDisplay, HistoryCard, SideSelector
│   └── theme/                 # Theme.kt, Color.kt, Shape.kt, Type.kt,
│                              #   DesignSystemPreviewScreen.kt (debug catalog)
└── util/
    ├── DateTimeExt.kt         # Instant.formatTime(), formatDateTime(), Duration.formatDuration(),
    │                          #   Duration.formatElapsedAgo()
    ├── FlowExt.kt             # Flow.catchAndLog()
    ├── NotificationHelper.kt  # Cancel/show notification helpers — builds design-system-themed
    │                          #   notifications (per-type small icon + accent color via setColor())
    └── UpdateChecker.kt       # In-app update check utility
```

---

## Theme & UI Tokens

All design-system source lives in `ui/theme/`. The live catalog is at `ui/theme/DesignSystemPreviewScreen.kt` — reachable from Settings → Developer (debug builds only).

### Palette Scale (`Color.kt`)

Three hue families, each with a 4-stop scale. The scale semantics are shared across families:

| Stop | Role |
|------|------|
| 700  | Primary action color |
| 200  | Container / background tint |
| 900  | On-container text (dark) |
| 100  | Softest tone |

| Family | 100 | 200 | 700 | 900 |
|--------|-----|-----|-----|-----|
| **Pink** (Feeding/Primary) | `#F4C2C2` | `#F8BBD0` | `#C2185B` | `#880E4F` |
| **Blue** (Sleep/Secondary) | `#89CFF0` | `#B3E5FC` | `#1976D2` | `#0D47A1` |
| **Green** (Success/Tertiary) | `#90EE90` | `#C8E6C9` | `#388E3C` | `#1B5E20` |
| **Amber** (Warning) | `#FFCC80` | `#FFE0B2` | `#E65100` | `#7A3600` |

Surface grays and yellows have no scale equivalent:
- `SoftYellow` `#FFF9C4` — soft background hint
- `SurfaceYellow` `#FFFDE7` — light scheme surface / background
- `Amber800` `#7A4800` — off-scale raw used only by the dark-scheme warning container

### Semantic Color Tokens (`Color.kt` → `Theme.kt`)

**Light scheme**

| Token name | Palette constant | Hex |
|------------|-----------------|-----|
| `primary` | `Pink700` | `#C2185B` |
| `onPrimary` | `OnPrimaryWhite` | `#FFFFFF` |
| `primaryContainer` | `Pink200` | `#F8BBD0` |
| `onPrimaryContainer` | `Pink900` | `#880E4F` |
| `secondary` | `Blue700` | `#1976D2` |
| `onSecondary` | `OnSecondaryWhite` | `#FFFFFF` |
| `secondaryContainer` | `Blue200` | `#B3E5FC` |
| `onSecondaryContainer` | `Blue900` | `#0D47A1` |
| `tertiary` | `Green700` | `#388E3C` |
| `onTertiary` | `OnTertiaryWhite` | `#FFFFFF` |
| `tertiaryContainer` | `Green200` | `#C8E6C9` |
| `onTertiaryContainer` | `Green900` | `#1B5E20` |
| `surface` / `background` | `SurfaceYellow` | `#FFFDE7` |
| `onSurface` | `OnSurfaceDark` | `#1A1A1A` |
| `onSurfaceVariant` | `OnSurfaceVariantGrey` | `#757575` |
| `surfaceVariant` | `SurfaceVariantLight` | `#F0EDE0` |
| `outline` | `OutlineLight` | `#CAC4D0` |
| `outlineVariant` | `OutlineVariantLight` | `#CAC4D0` |
| `error` | `ErrorLight` | `#B00020` |
| `errorContainer` | `ErrorContainerLight` | `#FFDAD6` |
| `onErrorContainer` | `OnErrorContainerLight` | `#410002` |

**Dark scheme** — all dark tokens have no palette-scale equivalent (independently chosen for contrast):

| Token name | Hex |
|------------|-----|
| `primary` | `#F48FB1` |
| `primaryContainer` | `#880E4F` (Pink900) |
| `secondary` | `#90CAF9` |
| `secondaryContainer` | `#0D47A1` (Blue900) |
| `tertiary` | `#A5D6A7` |
| `tertiaryContainer` | `#1B5E20` (Green900) |
| `surface` / `background` | `#1C1B1F` |
| `onSurface` | `#E6E1E5` |
| `surfaceVariant` | `#2B2930` |
| `outline` | `#938F99` |
| `outlineVariant` | `#49454F` |
| `error` | `#FFB4AB` |
| `errorContainer` | `#93000A` |

### Warning semantic tokens (extended, non-M3)

Accessed as top-level `val`s from `ui/theme/Color.kt` — **not** wired through `MaterialTheme.colorScheme`. Consumed directly by `NotificationHelper` for the Feeding Limit notification.

| Token | Light | Dark |
|------|-------|------|
| `WarningAmber` | `Amber700` `#E65100` | `Amber100` `#FFCC80` |
| `WarningContainerAmber` | `Amber200` `#FFE0B2` | `Amber800` `#7A4800` |
| `OnWarningContainerAmber` | `Amber900` `#7A3600` | `Amber200` `#FFE0B2` |

### Typography (`Type.kt` → `AkachanTypography`)

All 13 M3 slots are defined. Key custom roles:

| Slot | Weight | Size | Usage |
|------|--------|------|-------|
| `displaySmall` | ExtraBold | 36sp | Timer clock display |
| `headlineLarge` | Bold | 32sp | Screen titles |
| `headlineMedium` | Bold | 28sp | Section titles |
| `headlineSmall` | SemiBold | 24sp | Card titles |
| `titleLarge` | SemiBold | 22sp | TopAppBar title |
| `titleMedium` | SemiBold | 16sp | List item primary text |
| `titleSmall` | Medium | 14sp | List item secondary |
| `bodyLarge` | Normal | 16sp | Body / description |
| `bodyMedium` | Normal | 14sp | Secondary body |
| `bodySmall` | Normal | 12sp | Captions |
| `labelLarge` | SemiBold | 14sp | Button labels |
| `labelMedium` | Bold | 12sp | Section headers (UPPERCASE convention) |
| `labelSmall` | Medium | 11sp | Swatch / chip labels |

### Shapes (`Shape.kt` → `AkachanShapes`)

| Slot | Corner radius | Usage |
|------|--------------|-------|
| `extraSmall` | 4dp | Dense chips |
| `small` | 8dp | Input fields, small cards |
| `medium` | 16dp | Main cards |
| `large` | 24dp | Bottom sheets, dialogs |
| `extraLarge` | 50dp | Primary buttons (FAB-like) |

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

## Workflow

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

**Database:** `baby_tracker_db` (Room v2)

**`breastfeeding_sessions`**
| Column | Type | Notes |
|--------|------|-------|
| `id` | Long PK autoGenerate | |
| `start_time` | Long NOT NULL | epoch ms |
| `end_time` | Long NULLABLE | null = in progress |
| `starting_side` | String NOT NULL | "LEFT" or "RIGHT" |
| `switch_time` | Long NULLABLE | epoch ms when sides switched |
| `notes` | String NULLABLE | |
| `paused_at` | Long NULLABLE | epoch ms when session was paused; null = running |
| `paused_duration_ms` | Long NOT NULL | accumulated paused time in ms (default 0) |

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

Create tests for new features, bug fixes, and edge cases.
Doesn't need to follow the TDD pattern. Make sure the feature works as expected.

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

Read specs before implementing new features — they define the intended behaviour and non-goals.

---

## What NOT to Do

- Do not add multi-module structure (everything stays in `:app`)
- Do not create Mapper classes — use extension functions on entity/domain types
- Do not create BaseViewModel or BaseFragment
- Do not wrap return values in `sealed class Result<T>` — let exceptions propagate or use nullable types
- Do not add cloud sync, analytics, or remote API calls — local-only is by design
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
