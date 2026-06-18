# Task 8 — Add Brazilian Portuguese locale + full translation

> Part of the [i18n implementation plan](../2026-06-17-i18n-overview.md). Depends on Tasks 2–7 (all base keys must exist). See Global Constraints in the overview.

**Goal:** Create `app/src/main/res/values-pt-rBR/strings.xml` with a complete, idiomatic Brazilian Portuguese translation of every key in the base `values/strings.xml`, preserving every format arg and plural quantity.

## Files

- Create: `app/src/main/res/values-pt-rBR/strings.xml`
- (No Kotlin changes.)

## Implementation

### Step 1: Snapshot the full base key set

```bash
rg -n '<string name=|<plurals name=' app/src/main/res/values/strings.xml | wc -l
```

Every `<string>` and `<plurals>` here needs a pt-BR counterpart. There must be
zero `MissingTranslation` after this task.

- [ ] **Step 1 done when** you have the authoritative count of keys to translate.

### Step 2: Author `values-pt-rBR/strings.xml`

Translate each key. **Rules:**

- **Preserve format args verbatim.** `%1$s`, `%2$d`, `%d` stay exactly — only the
  surrounding words change. Reorder with positional indices if pt-BR grammar
  needs a different word order (that is what `%1$`/`%2$` are for).
- **Preserve plural structure.** pt-BR uses `one` / `other` like English for
  these cases. Keep both `<item>` quantities.
- **Escape apostrophes** as `\'` (e.g. `não` has none, but `d\'água`-style needs
  escaping; straight apostrophes in contractions must be `\'`).
- **Brazilian vocabulary**, not European Portuguese: "bebê" (not "bebé"),
  "mamadeira" (not "biberão"), "amamentação" for breastfeeding, "soneca" for nap,
  "geladeira"/"freezer" for storage.
- Keep emoji and the `&#183;` middot separators from the base strings.

Representative translations (author the full file in this style):

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Akachan</string>

    <!-- Notification action labels -->
    <string name="notif_action_switch_now">Trocar agora</string>
    <string name="notif_action_stop_feeding">Parar amamentação</string>
    <string name="notif_action_pause">Pausar</string>
    <string name="notif_action_resume">Retomar</string>

    <!-- Notification body format strings (args preserved) -->
    <string name="notif_body_switch_sides">Seio %1$s &#183; %2$dm &#183; Troque para o %3$s</string>
    <string name="notif_body_feeding_limit">Sessão de %1$d min. Toque em Parar ou Continuar.</string>

    <!-- Side labels -->
    <string name="notif_side_left">Esquerdo</string>
    <string name="notif_side_right">Direito</string>

    <!-- Sleep type labels -->
    <string name="notif_sleep_type_night">Sono noturno</string>
    <string name="notif_sleep_type_nap">Soneca</string>

    <!-- Bottle feed -->
    <string name="bottle_feed_add_title">Registrar mamadeira</string>
    <string name="bottle_feed_volume_label">Volume (mL)</string>
    <string name="bottle_feed_type_breast_milk">Leite materno</string>
    <string name="bottle_feed_type_formula">Fórmula</string>

    <!-- Shared actions -->
    <string name="try_again">Tentar novamente</string>
    <string name="back">Voltar</string>
    <string name="delete">Excluir</string>
    <string name="edit">Editar</string>
    <string name="cancel">Cancelar</string>
    <string name="loading">Carregando...</string>

    <!-- Plurals: keep both quantities, preserve %d -->
    <plurals name="settings_predictive_feeds_remaining">
        <item quantity="one">Precisa de mais %d mamada para ativar</item>
        <item quantity="other">Precisa de mais %d mamadas para ativar</item>
    </plurals>
    <plurals name="notif_body_stash_expiration">
        <item quantity="one">%1$d saco (%2$d mL) vence hoje</item>
        <item quantity="other">%1$d sacos (%2$d mL) vencem hoje</item>
    </plurals>

    <!-- ... translate every remaining key from values/strings.xml ... -->
</resources>
```

- [ ] **Step 2 done when** `values-pt-rBR/strings.xml` has one entry per base key.

### Step 3: Verify translation completeness and format integrity

```bash
./gradlew lintDebug
```

Expected: **zero** `MissingTranslation`, zero `StringFormatInvalid`, zero
`MissingQuantity`, zero `ImpliedQuantity`. If lint flags a format mismatch, the
pt-BR string's args don't match the base — fix the arg.

Confirm parity programmatically:

```bash
# key counts must match between locales
rg -c '<string name=|<plurals name=' app/src/main/res/values/strings.xml
rg -c '<string name=|<plurals name=' app/src/main/res/values-pt-rBR/strings.xml
```

- [ ] **Step 3 done when** counts match and lint shows no translation/format issues.

### Step 4: Build

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL — both locales bundle (`localeFilters` already set in Task 1).

- [ ] **Step 4 done when** the build succeeds.

## Verify

```
./gradlew ktlintFormat
./gradlew assembleDebug lintDebug
```

Expected: green; no `MissingTranslation`/`StringFormatInvalid`/`MissingQuantity`.

## Commit

```
feat(i18n): add Brazilian Portuguese translation (pt-rBR)

Add values-pt-rBR/strings.xml translating every base key, preserving format
args and plural quantities. lint reports zero MissingTranslation.
```
