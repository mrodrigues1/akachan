# Center Launcher Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Center the adaptive launcher foreground artwork's visible bounds on both axes and provide an emulator launcher screenshot.

**Architecture:** Keep the density-specific raster artwork unchanged and correct its placement in the shared adaptive-icon `InsetDrawable`. Equal horizontal insets preserve the source's horizontal center; asymmetric vertical insets compensate for the source alpha bounds being 4.5 mdpi pixels above its canvas center.

**Tech Stack:** Android resource XML, Android Gradle Plugin, Android emulator/CLI

---

### Task 1: Correct the adaptive foreground placement

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml:3-7`

- [ ] **Step 1: Run the bounds check and confirm the current resource fails**

Run a PowerShell bounds calculation against `mipmap-mdpi/ic_launcher_foreground.png` and the current inset values. The transformed visible center must be compared with the 54 dp adaptive-icon center.

Expected before the fix: horizontal and vertical centers do not equal `54` because the current resource uses left/top/right/bottom insets of `17/11/11/17` dp.

- [ ] **Step 2: Apply the minimal XML correction**

Replace the four inset values with:

```xml
android:insetLeft="14dp"
android:insetTop="17.333333dp"
android:insetRight="14dp"
android:insetBottom="10.666667dp"
```

The horizontal insets are equal. The vertical inset difference is `6.666666dp`, which offsets the source's `-4.5dp` alpha-bounds center after the 108 dp source is scaled into the 80 dp inset area.

- [ ] **Step 3: Re-run the bounds check**

Run the same PowerShell calculation.

Expected after the fix: transformed visible bounds center rounds to `(54.000, 54.000)` dp.

- [ ] **Step 4: Compile Android resources**

Run:

```powershell
.\gradlew.bat :app:processDebugResources
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the resource fix**

```powershell
git add app/src/main/res/drawable/ic_launcher_foreground.xml AI_TASK_PROGRESS.md
git commit -m "fix(ui): center adaptive launcher artwork"
```

### Task 2: Verify the installed icon on an emulator

**Files:**
- Create: `.artifacts/center-launcher-icon-emulator.png`

- [ ] **Step 1: Build the debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 2: Start an available emulator and install a fresh APK**

Use `android emulator list`, start an available AVD when none is running, uninstall `com.babytracker`, and install the new debug APK with `android run` or `adb install`.

Expected: the app appears in the emulator launcher with newly rendered adaptive-icon resources.

- [ ] **Step 3: Capture the launcher screenshot**

Return to the emulator launcher, navigate to the page or app drawer containing Akachan, then run:

```powershell
android screen capture -o .artifacts/center-launcher-icon-emulator.png
```

Expected: the screenshot contains the Akachan launcher icon and label.

- [ ] **Step 4: Inspect the screenshot and repository state**

Open `.artifacts/center-launcher-icon-emulator.png`, verify the artwork is centered within the icon mask, then run:

```powershell
git status --short --branch
git log -2 --oneline
```

Expected: the resource fix and design commit are present; no unintended tracked changes remain.
