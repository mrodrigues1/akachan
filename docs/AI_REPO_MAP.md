# Repo map

## Android app
- `app/src/main/java/com/babytracker/ui`: Compose screens, reusable components, theme, and feature ViewModels.
- `app/src/main/java/com/babytracker/data`: Room database, entities, DAOs, converters, and repository implementations.
- `app/src/main/java/com/babytracker/domain`: Pure Kotlin models, repository interfaces, and business use cases.
- `app/src/main/java/com/babytracker/navigation`: App routes and Compose navigation graph.
- `app/src/main/java/com/babytracker/di`: Hilt modules for database, DataStore, repositories, notifications, and sharing.
- `app/src/main/java/com/babytracker/manager`: Notification scheduler interfaces and implementations.
- `app/src/main/java/com/babytracker/receiver`: Broadcast receivers for notification actions and alarm triggers.
- `app/src/main/java/com/babytracker/sharing`: Partner sharing domain, Firebase service, repository, and use cases.
- `app/src/main/java/com/babytracker/util`: Date/time, Flow, notification, and update-checking helpers.

## Tests and config
- `app/src/test`: Unit tests with JUnit 5, MockK, Turbine, and coroutine test utilities.
- `app/src/androidTest`: Instrumentation, Room in-memory DB, and Compose UI tests.
- `gradle/libs.versions.toml`: Authoritative dependency versions.
- `config/detekt.yml`: Detekt rules tuned for this app.
- `specs/`: Product and architecture specs. Read relevant specs before feature work.

## Common tasks
- Add screen: check `app/src/main/java/com/babytracker/navigation`, the relevant `ui/<feature>` package, and the established ViewModel plus `*UiState` pattern.
- Add DB field: update Entity, DAO queries if needed, Room migration, repository conversion functions, and tests.
- Add use case: put a single-responsibility class under `domain/usecase/<feature>` and inject the repository interface.
- Add repository behavior: update the domain repository interface, implementation in `data/repository`, DI binding if a new implementation is introduced, and tests.
- Add notification behavior: check `manager/`, `receiver/`, `util/NotificationHelper.kt`, manifest receivers, and scheduler DI bindings.
- Add partner sharing behavior: keep Firebase-specific work inside `sharing/` and `di/SharingModule.kt`.
- Update dependencies: edit `gradle/libs.versions.toml`, then update `CLAUDE.md` and `AGENTS.md` tech stack tables if versions changed.

## Do not inspect unless needed
- `build/`
- `.gradle/`
- `generated/`
- `.idea/`
- `*.iml`
- snapshots
- screenshots
- lockfiles unless dependency changes are required
