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
- Both actions are disabled when the share code is blank

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
- Define `onCopyCode` and `onShareCode` lambdas at this level (see below) and pass them into `PrimaryContent` as callbacks — do **not** pass `snackbarHostState` or `scope` into the child composable.

**`PrimaryContent` composable signature addition**

```kotlin
onCopyCode: () -> Unit,
onShareCode: () -> Unit,
```

Below the existing share code `Card`, add a `Row`:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    OutlinedButton(
        onClick = onCopyCode,
        enabled = shareCode.isNotBlank(),
        modifier = Modifier.weight(1f),
    ) {
        Icon(Icons.Default.ContentCopy, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Copy")
    }
    OutlinedButton(
        onClick = onShareCode,
        enabled = shareCode.isNotBlank(),
        modifier = Modifier.weight(1f),
    ) {
        Icon(Icons.Default.Share, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Share")
    }
}
```

Both buttons are **disabled** when `shareCode.isBlank()` — this covers the transient window during `generateNewCode()` where `AppMode.PRIMARY` is active but no code exists yet.

**`onCopyCode` lambda (defined in `ManageSharingScreen`)**

```kotlin
val clipboard = LocalClipboardManager.current
val onCopyCode: () -> Unit = {
    clipboard.setText(AnnotatedString(shareCode))
    scope.launch { snackbarHostState.showSnackbar("Code copied to clipboard") }
}
```

**`onShareCode` lambda (defined in `ManageSharingScreen`)**

```kotlin
val context = LocalContext.current
val onShareCode: () -> Unit = {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "Use this code to connect to my baby tracker: $shareCode"
        )
    }
    try {
        context.startActivity(Intent.createChooser(sendIntent, null))
    } catch (e: ActivityNotFoundException) {
        scope.launch { snackbarHostState.showSnackbar("No app available to share this code.") }
    }
}
```

### Snackbar placement

The `SnackbarHost` is added to the `Scaffold` already wrapping `ManageSharingScreen`. No second `Scaffold` is needed. The Snackbar appears at the bottom of the screen above any system bars.

---

## Error handling

- Clipboard write is infallible on Android — no error handling needed.
- `startActivity` for `ACTION_SEND` **can** throw `ActivityNotFoundException` on restricted or managed devices. Wrap in `try/catch` and show a Snackbar: `"No app available to share this code."`.

---

## Testing

- No new unit tests required (no ViewModel or domain logic changed).
- Manual test: tap Copy → verify Snackbar appears and clipboard contains the code.
- Manual test: tap Share → verify Android chooser opens with the pre-filled sentence.
- Manual test: with a blank share code (during code regeneration) → verify both buttons are disabled.
