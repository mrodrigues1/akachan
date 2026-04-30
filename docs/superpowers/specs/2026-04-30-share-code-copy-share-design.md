# Share Code — Copy & Share UX

**Date:** 2026-04-30
**Status:** Approved

---

## Overview

Add Copy and Share affordances to the share code card in `ManageSharingScreen` so the primary user can send their 8-character pairing code to a partner without manual transcription.

---

## Goals

- User can copy the share code to the clipboard with one tap
- User can open the Android share sheet with pre-filled text with one tap
- Copy action gives visible confirmation via a Snackbar

## Non-Goals

- QR code generation
- Deep-link / dynamic link wrapping of the share code
- ViewModel or domain layer changes

---

## Design

### Affected file

`app/src/main/java/com/babytracker/ui/sharing/ManageSharingScreen.kt`

### UI changes

**`ManageSharingScreen` (top-level composable)**

- Create a `SnackbarHostState` with `remember`.
- Create a `CoroutineScope` with `rememberCoroutineScope`.
- Wire `snackbarHost = { SnackbarHost(snackbarHostState) }` into the existing `Scaffold`.
- Pass `snackbarHostState` and `scope` down to `PrimaryContent`.

**`PrimaryContent` composable**

Below the existing share code `Card`, add a `Row` with `horizontalArrangement = Arrangement.spacedBy(8.dp)`:

| Button | Type | Icon | Label | Action |
|--------|------|------|-------|--------|
| Copy | `OutlinedButton` | `Icons.Default.ContentCopy` | "Copy" | Copy code to clipboard; show Snackbar |
| Share | `OutlinedButton` | `Icons.Default.Share` | "Share" | Open Android share chooser |

**Copy action (inline)**
```kotlin
val clipboard = LocalClipboardManager.current
clipboard.setText(AnnotatedString(shareCode))
scope.launch { snackbarHostState.showSnackbar("Code copied to clipboard") }
```

**Share action (inline)**
```kotlin
val context = LocalContext.current
val sendIntent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(
        Intent.EXTRA_TEXT,
        "Use this code to connect to my baby tracker: $shareCode"
    )
}
context.startActivity(Intent.createChooser(sendIntent, null))
```

### Snackbar placement

The `SnackbarHost` is added to the `Scaffold` already wrapping `ManageSharingScreen`. No second `Scaffold` is needed. The Snackbar appears at the bottom of the screen above any system bars.

---

## Error handling

- Clipboard write is infallible on Android — no error handling needed.
- `startActivity` for `ACTION_SEND` is always resolvable (`text/plain` has universal support) — no fallback needed.

---

## Testing

- No new unit tests required (no ViewModel or domain logic changed).
- Manual test: tap Copy → verify Snackbar appears and clipboard contains the code.
- Manual test: tap Share → verify Android chooser opens with the pre-filled sentence.
