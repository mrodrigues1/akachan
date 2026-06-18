# Task 1 — Audit, tooling & conventions

> Part of the [i18n implementation plan](../2026-06-17-i18n-overview.md). Implement first, then verify, then commit. See the Global Constraints in the overview.

**Goal:** Lay the foundation for safe extraction: shipped-locale filter, lint detection of hardcoded strings (as warnings + a baseline so CI stays green mid-migration), and a documented key-naming convention. **No user-visible behaviour change.**

**Why first:** Every extraction issue (2–7) relies on the lint baseline existing (so each can *shrink* it) and on `localeFilters` being set. Establishing conventions once prevents drift across six parallel issues.

## Files

- Modify: `app/build.gradle.kts` (add `androidResources` + `lint` blocks)
- Create: `app/lint-baseline.xml` (generated, then committed)
- Modify: `app/src/main/res/values/strings.xml` (section-comment cleanup only — no key changes)
- Create: `docs/i18n-conventions.md` (key-naming reference for contributors)

## Implementation

### Step 1: Add `androidResources` locale filter and `lint` config

In `app/build.gradle.kts`, inside the `android { }` block (after `packaging { }`):

```kotlin
    androidResources {
        // AGP 9 replacement for resConfigs. Only these locales ship.
        localeFilters += listOf("en", "pt-rBR")
    }

    lint {
        baseline = file("lint-baseline.xml")
        // Detect hardcoded UI text and translation gaps. Kept non-fatal until
        // the final enforcement issue flips these to error.
        warning += listOf("HardcodedText", "MissingTranslation")
        checkDependencies = false
        abortOnError = false
    }
```

> If AGP 9.1.0 rejects `localeFilters += listOf(...)`, use
> `localeFilters.addAll(listOf("en", "pt-rBR"))`. Confirm by running Step 4.

- [ ] **Step 1 done when** `app/build.gradle.kts` contains both blocks.

### Step 2: Generate the lint baseline

The baseline records *today's* hardcoded strings so CI does not fail while
extraction is in progress. Each later issue regenerates/shrinks it.

Run:

```bash
./gradlew lintDebug
```

Expected: first run **creates** `app/lint-baseline.xml` and prints
`Created baseline file .../lint-baseline.xml`. The build **passes** (warnings are
baselined). If it instead reports the baseline is empty, confirm `HardcodedText`
appears in the issue list — there are ~188 `Text("...")` literals, so it must be
non-empty.

- [ ] **Step 2 done when** `app/lint-baseline.xml` exists and lists `HardcodedText` entries.

### Step 3: Write the conventions doc

Create `docs/i18n-conventions.md`:

```markdown
# i18n string conventions

## Languages
- Base: `app/src/main/res/values/strings.xml` (English).
- Brazilian Portuguese: `app/src/main/res/values-pt-rBR/strings.xml`.

## Key naming
- Format: `<feature>_<element>` in snake_case. Example: `sleep_schedule_title`.
- Group keys under an XML comment header per feature (`<!-- Sleep schedule -->`).
- Reuse the shared generic keys instead of duplicating: `cancel`, `delete`,
  `edit`, `back`, `try_again`, `loading`, `more_options`, `save`.

## Plurals
- Any count-dependent wording uses `<plurals>` with `quantity="one"`/`"other"`.
- Access with `pluralStringResource(R.plurals.key, count, count)`.

## Formatted values
- Inject runtime values with positional args (`%1$s`, `%2$d`). Never concatenate
  user-facing fragments in Kotlin.
- Access with `stringResource(R.string.key, arg1, arg2)`.

## Accessibility
- Provide `contentDescription` for meaningful icons/images via `stringResource`.
- Decorative-only images keep `contentDescription = null`.

## ViewModels
- Keep ViewModels locale-agnostic. Resolve copy in the composable, or expose a
  `@StringRes Int` (+ format args) in UI state. Inject `@ApplicationContext
  Context` only for non-Compose surfaces (receivers, notification managers).
```

- [ ] **Step 3 done when** `docs/i18n-conventions.md` exists.

### Step 4: Verify the build with the filter and baseline

Run:

```bash
./gradlew assembleDebug lintDebug
```

Expected: BUILD SUCCESSFUL. `assembleDebug` proves `localeFilters` DSL is valid;
`lintDebug` passes because warnings are baselined.

- [ ] **Step 4 done when** both tasks succeed.

## Verify

```
./gradlew ktlintFormat
./gradlew detekt
./gradlew assembleDebug lintDebug
```

Expected: all green; `lint-baseline.xml` present and non-empty.

## Commit

```
chore(i18n): add locale filters, hardcoded-string lint baseline, conventions

Set androidResources.localeFilters to en + pt-rBR, enable HardcodedText /
MissingTranslation lint as warnings with a baseline so CI stays green during
the string-extraction migration, and document key-naming conventions.
```
