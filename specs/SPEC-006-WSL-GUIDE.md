### Linux / WSL

The system image ships only a JRE (`java`) — no `javac`. Hilt's annotation processor (`hiltJavaCompileDebug`) requires a full JDK. Gradle downloads one automatically to `~/.gradle/jdks/`. Additionally, the Hilt toolchain property cannot be serialized by the Gradle configuration cache under this setup, so config cache must be disabled on Linux/WSL.

Both are handled via `~/.gradle/gradle.properties` (GRADLE_USER_HOME has higher precedence than the project `gradle.properties`, so this overrides only the local machine and does not affect Windows-side dev):

```properties
# ~/.gradle/gradle.properties
org.gradle.configuration-cache=false
org.gradle.java.home=/home/matheus/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2
```

With that file in place, Gradle commands run cleanly without manual env exports or `--no-configuration-cache` flags:

```bash
./gradlew :app:testDebugUnitTest
./gradlew ktlintFormat detekt
./gradlew :app:compileDebugAndroidTestKotlin
```

If the JDK path no longer exists (Gradle re-downloaded a newer version), run `ls ~/.gradle/jdks/` to find the current directory and update `org.gradle.java.home` accordingly.

### Linux / WSL — Test Performance

The architecture tests in `app/src/test/java/com/babytracker/architecture/` use [Konsist](https://github.com/LemonAppDev/konsist) to scan every production Kotlin file. On WSL2, the project lives on the Windows NTFS volume (`/mnt/c/...`) and is read through the 9P/DrvFs bridge, which makes file I/O roughly an order of magnitude slower than native ext4. The first `Konsist.scopeFromProduction()` call inside a test JVM takes **~14 minutes** under that setup vs. seconds on a native disk; subsequent calls in the same JVM reuse the cached parse.

Two patches reduce the impact:

1. **Shared, lazy scope** — `app/src/test/java/com/babytracker/architecture/KonsistScopes.kt` exposes one `productionScope` that the three architecture tests all reuse. Without it, every test method paid the scan cost.
2. **`-PfastTests` opt-out** — all architecture test classes carry `@Tag("architecture")`. `tasks.withType<Test>` reads `-PfastTests` (presence flag — any value enables it) and skips that tag, so the inner dev loop is sub-15-seconds while CI (no flag) still runs the full suite.

Day-to-day commands on WSL:

```bash
# Fast loop — skips the architecture tests (~10s warm, ~4min cold)
./gradlew :app:testDebugUnitTest -PfastTests

# Full suite — run before pushing (matches CI behaviour)
./gradlew :app:testDebugUnitTest
```

When adding new architecture tests, annotate them with `@Tag("architecture")` and use the shared `productionScope` instead of calling `Konsist.scopeFromProduction()` directly. Architecture rules only inspect production sources — test code is intentionally excluded.

For an even bigger speedup, clone the repo onto the WSL ext4 filesystem (e.g. `~/akachan`) and work from there — native filesystem I/O eliminates the 9P bottleneck entirely. The trade-off is that Windows-side tools (Android Studio on Windows, Explorer) can no longer reach the working copy.

### Linux / WSL — UI / Instrumentation Tests

`adb` is not installed in WSL — the Android SDK lives on the Windows side. WSL2 can execute Windows `.exe` files directly, so add the Windows SDK paths to `~/.bashrc`:

```bash
# Android SDK (Windows-side — WSL2 runs .exe directly)
export ANDROID_SDK_ROOT="/mnt/c/Users/mathe/AppData/Local/Android/Sdk"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"
```

After `source ~/.bashrc`, `adb` resolves to `adb.exe` on the Windows SDK and connects to the Windows ADB server.

**To run instrumentation tests (`src/androidTest/`) from WSL:**

1. Start the `Pixel_10_Pro` emulator from **Windows** Android Studio (or Windows terminal):
   ```
   # Windows terminal / PowerShell
   %LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -avd Pixel_10_Pro
   ```
2. Wait for boot, then from WSL:
   ```bash
   adb devices          # should list the emulator
   ./gradlew connectedAndroidTest
   ```

**To run Compose UI tests without a device (Robolectric, JVM only):**

Write the test in `src/test/` with `@RunWith(RobolectricTestRunner::class)` and `createComposeRule()`:

```kotlin
@RunWith(RobolectricTestRunner::class)
class MyScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun myTest() {
        composeRule.setContent { MyComposable() }
        composeRule.onNodeWithText("Hello").assertIsDisplayed()
    }
}
```

Run via: `./gradlew :app:testDebugUnitTest`

> `isIncludeAndroidResources = true` is already set in `testOptions` so resources and assets resolve at JVM test time.

### Linux / WSL — Inspecting the Home-Screen Widget on the Emulator

The Glance widget can only be seen once a launcher hosts it — there is no headless render path. To load and inspect it from WSL, start the `Pixel_10_Pro` emulator from **Windows** (see above), then work through `adb.exe`. Four gotchas matter:

**1. `adb` is `adb.exe` (a Windows binary).** It only resolves Windows-visible paths. Anything under the project (`/mnt/c/...`) works; a WSL path like `/tmp/...` does not — `adb push /tmp/foo` fails with `cannot stat`. Stage any file you push from inside the repo tree (e.g. `build/`).

**2. Install signature mismatch.** An APK built in WSL is signed with a different debug keystore than an Android Studio (Windows) install, so `adb install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Uninstall first — this wipes app data (Room DB + DataStore reset):

```bash
ADB="/mnt/c/Users/mathe/AppData/Local/Android/Sdk/platform-tools/adb.exe"
./gradlew :app:assembleDebug
"$ADB" uninstall com.babytracker
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell monkey -p com.babytracker -c android.intent.category.LAUNCHER 1   # first launch creates the Room DB
```

**3. Seeding the DB — no `sqlite3` anywhere.** Neither the emulator image nor WSL ships `sqlite3`, but host `python3` has the `sqlite3` module. `run-as` works because debug builds are debuggable. Force-stop the app first (so no connection is open), pull → edit → push back, staging the file in the repo tree per gotcha 1:

```bash
"$ADB" shell am force-stop com.babytracker
"$ADB" exec-out run-as com.babytracker cat databases/baby_tracker_db > build/seed.db
python3 - build/seed.db <<'PY'
import sqlite3, sys, time
con = sqlite3.connect(sys.argv[1]); cur = con.cursor()
now = int(time.time()*1000)
# end_time NULL => SLEEPING, which paints the widget's active (solid Sleep Blue) sleep surface
cur.execute("INSERT INTO sleep_records (start_time,end_time,sleep_type,notes) VALUES (?,?,?,?)",
            (now - 45*60*1000, None, "NAP", None))
con.commit(); cur.execute("PRAGMA wal_checkpoint(TRUNCATE)"); con.commit(); con.close()
PY
"$ADB" push build/seed.db /data/local/tmp/seed.db
"$ADB" shell run-as com.babytracker cp /data/local/tmp/seed.db databases/baby_tracker_db
"$ADB" shell run-as com.babytracker rm -f databases/baby_tracker_db-wal databases/baby_tracker_db-shm
```

Checkpoint with `wal_checkpoint(TRUNCATE)` and delete the device `-wal`/`-shm` so Room reopens cleanly from the single file. **Only INSERT into the pulled copy — never hand-build a DB from scratch:** Room validates the schema identity hash in `room_master_table` and crashes on mismatch.

**4. Placing and resizing the widget.** No adb command adds a widget (`cmd appwidget` is stubbed), so drive the Pixel Launcher with `input`: long-press an empty cell (`input swipe x y x y 700`) → tap **Widgets** → tap the search field and `input text "Akachan"` → expand the result → drop the thumbnail with `input draganddrop x1 y1 x2 y2 2000`. Resize by long-pressing the placed widget, then dragging the bottom handle (the responsive breakpoint at `BabyWidget.MEDIUM_SIZE` switches the small↔medium layout). Read exact element bounds in device pixels with `uiautomator dump /sdcard/ui.xml` + `exec-out cat`; capture frames with `adb exec-out screencap -p > build/shot.png`.

---