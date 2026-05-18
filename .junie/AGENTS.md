# Agent Development Guide — BabyTracker (Akachan)

This document provides technical details for advanced developers (agents) working on the BabyTracker project.

## Build & Configuration

The project is a standard Android application using Gradle (Kotlin DSL).

### Prerequisites
- JDK 17
- Android SDK (Compile SDK 35, Target SDK 35)

### Core Stack
- **Language**: Kotlin 2.3.20
- **UI**: Jetpack Compose (BOM 2026.03.00, Material 3)
- **DI**: Hilt 2.59 with KSP 2.3.6
- **Database**: Room 2.8.4 with KSP (schema v2 — `breastfeeding_sessions` has `paused_at`, `paused_duration_ms` columns)
- **Local Storage**: DataStore 1.1.1
- **Sharing**: Firebase BOM 33.7.0 (Firestore KTX, Auth KTX) — used by the optional partner-sharing feature

> Authoritative versions: `gradle/libs.versions.toml`.

### Build Commands
```bash
# Clean and build the project
./gradlew clean build

# Assemble debug APK
./gradlew assembleDebug

# Run Room schema generation (via KSP)
./gradlew kspDebugKotlin
```

## Testing

The project follows a strict testing strategy using JUnit 5 and MockK.

Create tests for new features, bug fixes, and edge cases.
Doesn't need to follow the TDD pattern. Make sure the feature works as expected.

### Testing Stack
- **Unit Testing**: JUnit 5 (`org.junit.jupiter`)
- **Mocking**: MockK (`io.mockk`)
- **Coroutine Testing**: `kotlinx-coroutines-test`
- **Flow Testing**: Turbine
- **UI Testing**: Compose UI Test

### Running Tests
```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests "com.babytracker.util.DateTimeExtTest"

# Run instrumentation tests (requires emulator/device)
./gradlew connectedAndroidTest
```

### Adding New Tests
- Unit tests go into `app/src/test/java/`.
- Mock dependencies using `mockk()`.
- Use `runTest` for coroutines.
- For `StateFlow` or `Flow`, use Turbine's `test { ... }` block.

#### Example Test
```kotlin
class DemoTest {
    @Test
    fun `formatDuration returns correct string`() {
        val duration = Duration.ofMinutes(5).plusSeconds(30)
        val result = duration.formatDuration()
        assertEquals("5m 30s", result)
    }
}
```

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

## Architecture & Style

### Clean Architecture
- **Domain Layer**: Pure Kotlin, no Android dependencies. Contains Models, Repository Interfaces, and Use Cases.
- **Data Layer**: Repository implementations, Room DAOs/Entities, DataStore.
- **UI Layer**: Compose Screens and ViewModels.

### Key Patterns
- **Use Cases**: Single responsibility, using `operator fun invoke`.
- **Mappers**: No separate mapper classes. Use extension functions (e.g., `Entity.toDomain()`).
- **DateTime**: Always use `java.time.Instant` for timestamps, stored as `Long` (epoch ms) in DB.
- **DI**: Hilt is used for all dependency injection. Modules: `DatabaseModule`, `DataStoreModule`, `RepositoryModule`, `NotificationSchedulerModule`, `SharingModule`. Use `@Binds` for interface-to-implementation binding.
- **Sharing**: The `sharing/` package contains the Firebase partner-sharing feature (`FirestoreSharingService`, `SharingRepository`, use cases). The `AppMode` enum (NONE/PRIMARY/PARTNER) in `sharing/domain/model/` controls which start destination `AppNavGraph` uses.
- **Notifications**: `manager/` holds scheduler interfaces + impls; `BreastfeedingSessionNotificationCoordinator` orchestrates session notification lifecycle. `receiver/` holds `@AndroidEntryPoint` BroadcastReceivers that use `goAsync()` to call suspend use cases.

### Code Style
- Follow standard Kotlin coding conventions.
- Use `StateFlow` in ViewModels to expose UI state.
- Keep Composables stateless where possible by hoisting state to ViewModels.

## Design System

The app follows a custom "Baby" Material 3 design with a soft color palette and high usability.

### Color Palette

| Token | Light Value | Usage |
|-------|-------------|-------|
| `primary` | `#C2185B` | Primary actions, Breastfeeding theme |
| `secondary` | `#1976D2` | Secondary actions, Sleep theme |
| `tertiary` | `#388E3C` | Success states, Active/Overtime indicators |
| `surface` | `#FFFDE7` | Background, Card surfaces |
| `onSurface` | `#1A1A1A` | Main text color |

### Typography

| Style | Specs | Usage |
|-------|-------|-------|
| `displaySmall` | ExtraBold 36sp | Timer displays, Large clocks |
| `headlineLarge`| Bold 32sp | Main screen titles |
| `titleLarge` | SemiBold 22sp | Section headers |
| `bodyLarge` | Normal 16sp | Regular content, description |
| `labelMedium` | Bold 12sp | ALL CAPS headers (Relative dates) |

### Shapes

The app uses rounded shapes for a friendly, child-focused feel.
- **Medium (`16.dp`)**: Main cards, interaction elements.
- **ExtraLarge (`50.dp`)**: Primary buttons, FAB-like elements.
