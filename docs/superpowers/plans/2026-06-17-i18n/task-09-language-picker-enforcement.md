# Task 9 — In-app language picker + per-app locale + enforcement/QA

> Part of the [i18n implementation plan](../2026-06-17-i18n-overview.md). Depends on Task 8 (translation must exist). See Global Constraints in the overview.

**Goal:** Let users pick **System default / English / Português (Brasil)** from Settings; persist and apply the choice on minSdk 26 via the AppCompat per-app-locale backport; expose Akachan in Android 13+ system per-app-language settings; and promote lint to fatal so hardcoded strings can never regress.

**Tech note / first risk to retire:** the app is pure Compose (`MainActivity : ComponentActivity`). `AppCompatDelegate.setApplicationLocales` works **without** converting to `AppCompatActivity`, but verify this on a device in Step 4 before building the UI on top of it.

## Files

- Modify: `gradle/libs.versions.toml` (add `appcompat` version + library alias)
- Modify: `app/build.gradle.kts` (add `appcompat` dependency; promote lint to fatal; delete baseline reference)
- Create: `app/src/main/res/xml/locales_config.xml`
- Modify: `app/src/main/AndroidManifest.xml` (add `localeConfig` + `AppLocalesMetadataHolderService`)
- Create: `app/src/main/java/com/babytracker/ui/settings/AppLocale.kt` (enum + apply/read helpers)
- Modify: `app/src/main/java/com/babytracker/ui/settings/SettingsScreen.kt` (add Language section)
- Create: `app/src/test/java/com/babytracker/ui/settings/AppLocaleTest.kt`
- Add base + pt-BR keys for the picker UI in both `strings.xml` files
- Delete: `app/lint-baseline.xml` (once count is zero)

## Implementation

### Step 1: Add the AppCompat dependency

In `gradle/libs.versions.toml`, under `[versions]`:

```toml
appcompat = "1.7.0"
```

under `[libraries]`:

```toml
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
```

In `app/build.gradle.kts` dependencies:

```kotlin
    implementation(libs.androidx.appcompat)
```

- [ ] **Step 1 done when** the project syncs with the new dependency.

### Step 2: Locale config XML + manifest wiring

Create `app/src/main/res/xml/locales_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="pt-BR" />
</locale-config>
```

In `AndroidManifest.xml`, add to `<application>`:

```xml
android:localeConfig="@xml/locales_config"
```

and inside `<application>`, register the auto-store service:

```xml
        <service
            android:name="androidx.appcompat.app.AppLocalesMetadataHolderService"
            android:enabled="false"
            android:exported="false">
            <meta-data
                android:name="autoStoreLocales"
                android:value="true" />
        </service>
```

- [ ] **Step 2 done when** the manifest references the locale config and the service.

### Step 3: `AppLocale` helper (write the test first)

Create `app/src/test/java/com/babytracker/ui/settings/AppLocaleTest.kt`:

```kotlin
package com.babytracker.ui.settings

import androidx.core.os.LocaleListCompat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppLocaleTest {
    @Test
    fun `SYSTEM maps to empty locale list`() {
        assertTrue(AppLocale.SYSTEM.toLocaleList().isEmpty)
    }

    @Test
    fun `ENGLISH maps to en`() {
        assertEquals("en", AppLocale.ENGLISH.toLocaleList().toLanguageTags())
    }

    @Test
    fun `PORTUGUESE_BR maps to pt-BR`() {
        assertEquals("pt-BR", AppLocale.PORTUGUESE_BR.toLocaleList().toLanguageTags())
    }

    @Test
    fun `fromLocaleList round-trips a selected tag`() {
        val list = LocaleListCompat.forLanguageTags("pt-BR")
        assertEquals(AppLocale.PORTUGUESE_BR, AppLocale.fromLocaleList(list))
    }

    @Test
    fun `fromLocaleList empty maps to SYSTEM`() {
        assertEquals(AppLocale.SYSTEM, AppLocale.fromLocaleList(LocaleListCompat.getEmptyLocaleList()))
    }
}
```

Run it — it fails (class missing):

```bash
./gradlew test --tests "com.babytracker.ui.settings.AppLocaleTest"
```

Expected: FAIL (unresolved `AppLocale`).

Create `app/src/main/java/com/babytracker/ui/settings/AppLocale.kt`:

```kotlin
package com.babytracker.ui.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.babytracker.R

enum class AppLocale(@StringRes val labelRes: Int, val tag: String?) {
    SYSTEM(R.string.settings_language_system, null),
    ENGLISH(R.string.settings_language_english, "en"),
    PORTUGUESE_BR(R.string.settings_language_pt_br, "pt-BR"),
    ;

    fun toLocaleList(): LocaleListCompat =
        if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)

    companion object {
        fun fromLocaleList(list: LocaleListCompat): AppLocale {
            if (list.isEmpty) return SYSTEM
            val tag = list.toLanguageTags()
            return entries.firstOrNull { it.tag != null && tag.startsWith(it.tag.substringBefore('-')) && tag.equals(it.tag, ignoreCase = true) }
                ?: entries.firstOrNull { it.tag != null && tag.startsWith(it.tag, ignoreCase = true) }
                ?: SYSTEM
        }

        fun current(): AppLocale = fromLocaleList(AppCompatDelegate.getApplicationLocales())

        fun apply(locale: AppLocale) {
            AppCompatDelegate.setApplicationLocales(locale.toLocaleList())
        }
    }
}
```

Re-run the test:

```bash
./gradlew test --tests "com.babytracker.ui.settings.AppLocaleTest"
```

Expected: PASS.

- [ ] **Step 3 done when** `AppLocaleTest` passes.

### Step 4: Verify the backport works on a device (retire the risk)

Temporarily call `AppLocale.apply(AppLocale.PORTUGUESE_BR)` from a debug entry
point (or the picker once built) and confirm the UI switches to pt-BR and
survives a cold restart on a device/emulator (API 26 and API 33+ if available).
If `ComponentActivity` does not pick up the locale, the fallback is to make
`MainActivity` extend `AppCompatActivity` (it already uses `setContent`, so only
the superclass changes). Record the outcome in the PR description.

- [ ] **Step 4 done when** live switch + restart works on a device.

### Step 5: Add picker strings (both locales)

`values/strings.xml`:

```xml
    <!-- Settings — language -->
    <string name="settings_section_language">Language</string>
    <string name="settings_language_system">System default</string>
    <string name="settings_language_english">English</string>
    <string name="settings_language_pt_br">Português (Brasil)</string>
```

`values-pt-rBR/strings.xml`:

```xml
    <!-- Settings — language -->
    <string name="settings_section_language">Idioma</string>
    <string name="settings_language_system">Padrão do sistema</string>
    <string name="settings_language_english">English</string>
    <string name="settings_language_pt_br">Português (Brasil)</string>
```

- [ ] **Step 5 done when** both files have the four language keys.

### Step 6: Add the Language section to Settings

In `SettingsScreen.kt`, add a section that lists the three `AppLocale` options as
single-choice rows, following the screen's existing section/row composables:

```kotlin
@Composable
private fun LanguageSection() {
    val selected = remember { mutableStateOf(AppLocale.current()) }
    Text(
        text = stringResource(R.string.settings_section_language),
        style = MaterialTheme.typography.titleSmall,
    )
    AppLocale.entries.forEach { locale ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected.value == locale,
                    onClick = {
                        selected.value = locale
                        AppLocale.apply(locale)
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected.value == locale, onClick = null)
            Text(stringResource(locale.labelRes))
        }
    }
}
```

Wire `LanguageSection()` into the existing Settings list. Match the surrounding
section composables' spacing/padding rather than the skeleton above.

- [ ] **Step 6 done when** the Settings screen shows the Language section and tapping a row switches language live.

### Step 7: Promote lint to fatal and remove the baseline

Confirm the baseline is empty (Task 7 drove `HardcodedText` to zero):

```bash
rm -f app/lint-baseline.xml
./gradlew lintDebug
```

If zero issues, update `app/build.gradle.kts` `lint { }`:

```kotlin
    lint {
        // Migration complete — these now fail the build.
        error += listOf("HardcodedText", "MissingTranslation", "MissingQuantity", "StringFormatInvalid")
        abortOnError = true
        // baseline reference removed
    }
```

Remove the `baseline = file("lint-baseline.xml")` line and the
`warning += listOf("HardcodedText", "MissingTranslation")` line from Task 1.

```bash
./gradlew lintDebug
```

Expected: BUILD SUCCESSFUL with lint as `error`/`fatal`.

- [ ] **Step 7 done when** lint runs fatal and passes with no baseline.

### Step 8: Cross-locale QA

Manually verify in both languages: launch in English, switch to Português
(Brasil) from Settings, walk the primary flows (home, breastfeeding, sleep,
bottle feed, onboarding via fresh install, settings), confirm no clipped/garbled
text and no leftover English. Switch back; restart the app and confirm the choice
persisted. Note results in the PR.

- [ ] **Step 8 done when** both locales pass manual QA.

## Verify

```
./gradlew ktlintFormat
./gradlew detekt
./gradlew build
./gradlew test --tests "com.babytracker.ui.settings.AppLocaleTest"
```

Expected: full build green with fatal lint; `AppLocaleTest` passes; no
`lint-baseline.xml` in the tree.

## Commit

```
feat(i18n): add in-app language picker and enforce no hardcoded strings

Add a Settings language picker (System default / English / Português (Brasil))
backed by AppCompatDelegate.setApplicationLocales with autoStoreLocales and a
locales_config for Android 13+. Promote HardcodedText/MissingTranslation lint to
fatal and remove the now-empty baseline.
```
