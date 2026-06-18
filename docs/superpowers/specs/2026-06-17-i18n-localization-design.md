# Internationalization (i18n) — Design Spec

**Status:** Approved (design phase) — pending implementation
**Linear project:** [Internationalization (i18n)](https://linear.app/akachan/project/internationalization-i18n-bd75c873c822)
**Date:** 2026-06-17

---

## 1. Purpose

Make Akachan fully localizable and ship two languages: **English** (base) and
**Brazilian Portuguese** (`pt-rBR`). Today ~600 user-facing string literals are
hardcoded across ~50 UI files; only `SettingsScreen.kt` uses `stringResource`.
This project extracts every user-facing string into resources, adds a full
Brazilian Portuguese translation, gives users an in-app language picker, and
adds lint enforcement so hardcoded strings cannot regress.

No architectural blocker exists — Android i18n is built in. The cost is **volume
+ discipline**, not complexity.

## 2. Decisions (one-line summary)

| # | Decision | Choice |
|---|----------|--------|
| 1 | Languages | English (base) + Brazilian Portuguese (`pt-rBR`) |
| 2 | Language selection | **In-app picker** in Settings **and** system-locale fallback |
| 3 | Per-app locale mechanism | `AppCompatDelegate.setApplicationLocales` (AppCompat backport to minSdk 26) + `android:localeConfig` for Android 13+ system UI |
| 4 | Translation ownership | Translations authored in-repo (this project), reviewed by the team (native pt-BR speaker) |
| 5 | String key naming | `feature_element` snake_case, grouped by XML comment block (matches existing) |
| 6 | Plurals | Android `<plurals>` — never count-based string concatenation |
| 7 | Formatted values | Positional format args (`%1$s`, `%2$d`) — never `+` concatenation |
| 8 | `contentDescription` | Extract all meaningful a11y strings; decorative images stay `null` |
| 9 | Shipped locales | `androidResources.localeFilters += listOf("en", "pt-rBR")` |
| 10 | Lint guard | `HardcodedText`, `MissingTranslation`, `MissingQuantity`, `StringFormatInvalid` — warning/baseline first, `error` (fatal) once both locales complete |
| 11 | Issue granularity | One PR per issue, ~9 issues, extraction grouped by feature area |

## 3. Architecture

No multi-module change. No new domain layer. The work is almost entirely in
`app/src/main/res/values*/strings.xml`, `ui/**` composables, `app/build.gradle.kts`,
`AndroidManifest.xml`, and one new Settings code path for the picker.

### 3.1 String resource conventions

- **Base file:** `app/src/main/res/values/strings.xml` (English). The existing 83
  keys stay; new keys append to their feature's comment block.
- **Translation file:** `app/src/main/res/values-pt-rBR/strings.xml` — every key
  the base file has, fully translated.
- **Naming:** `<feature>_<element>` snake_case. Examples already in tree:
  `bottle_feed_volume_label`, `settings_predictive_toggle_title`,
  `feeding_history_empty`. New keys follow the same pattern. Screen-scoped keys
  use the screen as the feature prefix (e.g. `sleep_schedule_*`, `onboarding_*`).
- **Reuse:** generic actions already exist (`cancel`, `delete`, `edit`, `back`,
  `try_again`, `loading`, `more_options`). Reuse these; do not create per-screen
  duplicates of common verbs.
- **Grouping:** each feature's keys sit under an XML comment header
  (`<!-- Sleep schedule -->`), matching the current file's layout.

### 3.2 Plurals and formatting

- Any string whose wording changes with a count uses `<plurals>` with
  `quantity="one"` / `quantity="other"` (pt-BR plural rules match English's
  one/other split for these cases). Existing examples:
  `settings_predictive_feeds_remaining`, `notif_body_stash_expiration`.
- Durations, volumes, dates, names, and counts are injected via positional args
  (`%1$s`, `%2$d`). **No Kotlin string concatenation of user-facing fragments.**
  Where current code builds a string by concatenation (e.g. `"$count feeds"`),
  it is rewritten to `stringResource(R.string.x, count)` or
  `pluralStringResource(...)`.
- Compose access: `stringResource(R.string.key)`,
  `stringResource(R.string.key, arg1, arg2)`,
  `pluralStringResource(R.plurals.key, count, count)`.
- Non-Compose access (ViewModels, receivers, notification managers that need a
  `Context`): use `context.getString(...)` / `resources.getQuantityString(...)`.
  ViewModels that currently embed user-facing copy (e.g. `PredictionCopy.kt`)
  resolve strings through an injected `@ApplicationContext Context` or expose
  string *resource IDs* in UI state and let the composable resolve them. Prefer
  resolving in the composable where practical to keep ViewModels locale-agnostic
  and unit-testable.

### 3.3 Per-app locale (in-app picker)

Android 13+ (API 33) has framework per-app language. To support minSdk 26 we use
the AndroidX AppCompat backport:

- Add dependency `androidx.appcompat:appcompat` (UI library, not a remote API —
  consistent with the "no new remote integrations" rule).
- Switch language at runtime via
  `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("pt-BR"))`.
  The backport persists the selection and re-applies it on next start; on API 33+
  it delegates to the framework `LocaleManager`.
- Enable auto-storage so the choice survives process death without a manual
  DataStore key: add to `AndroidManifest.xml`
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
- Add `res/xml/locales_config.xml` listing `en` + `pt-BR`, and reference it from
  `<application android:localeConfig="@xml/locales_config">` so Android 13+ shows
  Akachan in the system per-app-language settings.
- The Settings picker shows three options: **System default**, **English**,
  **Português (Brasil)**. "System default" clears app locales
  (`LocaleListCompat.getEmptyLocaleList()`).

### 3.4 Build config

```kotlin
android {
    androidResources {
        localeFilters += listOf("en", "pt-rBR")
        generateLocaleConfig = false // we author locales_config.xml by hand for the named langs
    }
    lint {
        // Warning + baseline in issue 1; promoted to fatal in issue 9.
        warning += listOf("MissingTranslation")
        // HardcodedText, MissingQuantity, StringFormatInvalid promoted to error
        // in the final enforcement issue.
    }
}
```

> Note: `localeFilters` is the AGP 9 replacement for the deprecated `resConfigs`.
> Confirm exact DSL against AGP 9.1.0 during issue 1; fall back to
> `androidResources { localeFilters.addAll(...) }` if the `+=` form is rejected.

## 4. Issue breakdown

Nine issues, each one PR. Extraction issues (2–7) are independent of each other —
they touch disjoint file sets — so they can proceed in parallel after issue 1.
Issues 8–9 depend on extraction being complete.

| # | Issue | Depends on | Scope |
|---|-------|-----------|-------|
| 1 | Audit, tooling & conventions | — | Add `localeFilters`, enable lint (warning + baseline), document key-naming conventions, restructure `strings.xml` comment sections. No visible behaviour change. |
| 2 | Extract: Home + Breastfeeding + shared components | 1 | `Home*`, `Breastfeeding*`, `FeedSettings*`, `EditBreastfeedingSessionSheet`, `PredictionCopy`, shared `HistoryCard`/`SideSelector`/`TimerDisplay`/`CueQuickTapRow`/`DateTimeFieldRow` |
| 3 | Extract: Sleep + Trends | 1 | `Sleep*` (tracking, history, schedule, settings, prediction, recommendation), `Trends*`, `RhythmStrip` |
| 4 | Extract: Onboarding + Growth + Milestones | 1 | `Onboarding*` + step components, `Growth*`, `AddMeasurementSheet`, `Milestone*` |
| 5 | Extract: Settings + Inventory | 1 | residual `Settings*`, `DataSection`, `DataExport*`, `SettingsReminderComponents`, `WarningSurface`, `Inventory*` |
| 6 | Extract: Feeding + Bottle + Pumping | 1 | `BottleFeed*`, `UnifiedFeedingHistory*`, `FeedingHistory*`, `Pumping*`, `AddBagPromptSheet` |
| 7 | Extract: Sharing/Partner + widget/tile residue | 1 | `ConnectPartner*`, `ManageSharing*`, `PartnerDashboard*`, `PartnerFeedHistory*`, any residual widget/tile/notification literals |
| 8 | Add pt-BR locale + full translation | 2–7 | `values-pt-rBR/strings.xml` translating every key authored by issues 1–7 |
| 9 | In-app language picker + per-app locale + enforcement/QA | 8 | AppCompat dep, `locales_config.xml`, `android:localeConfig`, Settings picker UI + ViewModel wiring, flip lint to `error` (fatal), manual QA both locales |

Each extraction issue (2–7) is self-contained: it extracts its files' literals,
converts that area's counts to `<plurals>` and concatenations to format args,
adds keys to the base `strings.xml`, and updates the lint baseline. There is no
separate "plurals pass" issue — plurals are handled where the strings live.

## 5. Per-issue testing strategy

- **Extraction issues (2–7):** the change is mechanical and behaviour-preserving.
  Validation is `./gradlew assembleDebug` (resources compile, no missing keys) +
  `./gradlew lintDebug` (no *new* `HardcodedText` in the touched files) +
  existing UI/unit tests still green. Where a ViewModel's string handling
  changes (e.g. resource IDs in UI state), add/adjust unit tests for that mapping.
- **Translation issue (8):** `lintDebug` must report zero `MissingTranslation`
  for the shipped locales; `assembleDebug` confirms `StringFormatInvalid` /
  `MissingQuantity` are clean (positional args + plural quantities match base).
- **Picker issue (9):** unit-test the locale-selection ViewModel logic
  (selected tag → `LocaleListCompat`, "system default" → empty list); manual QA
  switches language live and after process restart in both locales; lint runs
  `fatal` and the full build passes.

## 6. Non-goals

- No languages beyond English and Brazilian Portuguese in this project.
- No RTL-specific layout work (both languages are LTR; `supportsRtl` already true).
- No translation of developer-facing logs, analytics, or Firestore field names.
- No automated translation-management service (Crowdin/Lokalise) integration.
- No date/number locale formatting overhaul beyond what `stringResource` args and
  existing `DateTimeExt` already provide (revisit only if QA surfaces a defect).

## 7. Risks

- **AppCompat dependency on a pure-Compose app (issue 9):** adding `appcompat`
  pulls in `AppCompatActivity` infrastructure. We do *not* need to convert
  `MainActivity` to `AppCompatActivity` for `setApplicationLocales` to work, but
  this must be verified on a device early in issue 9; the picker plan calls it
  out as the first validation step.
- **ViewModel-embedded copy:** strings currently produced in ViewModels
  (`PredictionCopy`, recommendation/prediction cards) need a context or a
  resource-ID-in-state refactor; this is the only non-mechanical extraction work
  and is concentrated in issues 2 and 3.
- **Lint baseline drift:** issue 1 records a baseline so existing hardcoded
  strings don't fail CI mid-migration; each extraction issue must *shrink* the
  baseline, and issue 9 deletes it entirely when the count reaches zero.
