# akachan

Akachan is a native Android baby tracker app designed to help parents track breastfeeding sessions, sleep records, and baby's growth and health.

Built with **Jetpack Compose** and following modern Android development practices.

## 🚀 Stack

- **Language:** [Kotlin](https://kotlinlang.org/)
- **UI Framework:** [Jetpack Compose](https://developer.android.com/compose)
- **Dependency Injection:** [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- **Database:** [Room](https://developer.android.com/training/data-storage/room)
- **Preferences:** [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- **Concurrency:** [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & [Flow](https://kotlinlang.org/docs/flow.html)
- **Architecture:** MVVM with Clean Architecture principles (Use Cases, Repositories)
- **Package Manager:** [Gradle (Kotlin DSL)](https://docs.gradle.org/current/userguide/kotlin_dsl.html) with Version Catalogs

## 🛠️ Requirements

- **JDK:** 17
- **Android SDK:**
    - `compileSdk`: 35
    - `minSdk`: 26
    - `targetSdk`: 35
- **Android Studio:** Ladybug or newer recommended.

## 🏃 Setup & Run

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/matheusr/akachan.git
    cd akachan
    ```

2.  **Open in Android Studio:**
    Import the project as a Gradle project.

3.  **Run the app:**
    Select the `app` configuration and your device/emulator, then click **Run**.

    Or via CLI:
    ```powershell
    ./gradlew installDebug
    ```

## 📜 Scripts & Tasks

Common Gradle tasks:
- `./gradlew assembleDebug`: Build debug APK.
- `./gradlew bundleRelease`: Build release App Bundle.
- `./gradlew lint`: Run Android Lint.
- `./gradlew ktlintCheck`: (If configured) Check Kotlin style. TODO: Verify if ktlint is applied.

## 🔐 Environment Variables

For release builds, the following environment variables are required:
- `SIGNING_STORE_PASSWORD`: Keystore password.
- `SIGNING_KEY_ALIAS`: Key alias.
- `SIGNING_KEY_PASSWORD`: Key password.

The project expects a `release.keystore` file in the `app/` directory for release builds.

## 🧪 Tests

### Unit Tests
Located in `app/src/test/`. Uses JUnit 5, MockK, and Turbine.
```powershell
./gradlew test
```

### Instrumentation Tests
Located in `app/src/androidTest/`. Uses JUnit 4 and Compose UI Test.
```powershell
./gradlew connectedAndroidTest
```

## 📁 Project Structure

```text
app/
├── src/
│   ├── main/
│   │   ├── java/com/babytracker/
│   │   │   ├── data/          # Local storage, Repositories implementations
│   │   │   ├── di/            # Hilt modules
│   │   │   ├── domain/        # Models, Repository interfaces, Use Cases
│   │   │   ├── navigation/    # App navigation graph
│   │   │   ├── ui/            # Compose screens, ViewModels, Themes
│   │   │   └── util/          # Extensions and utility classes
│   │   └── res/               # Android resources
│   ├── test/                  # Unit tests
│   └── androidTest/           # Instrumentation tests
gradle/                        # Gradle wrapper and Version Catalogs
specs/                         # Project specifications and design docs
```

## 📄 License

This project is licensed under the [MIT License](LICENSE).
