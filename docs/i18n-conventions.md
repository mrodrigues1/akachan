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
