# Share Code Copy & Share Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Copy and Share buttons below the share code card in `ManageSharingScreen` so users can send their 8-character pairing code to a partner without manual transcription.

**Architecture:** Pure UI change in a single file — no ViewModel or domain layer involved. Clipboard access and share intent are triggered via `LocalClipboardManager` and `LocalContext` (Compose Composition-local providers), keeping platform APIs inside `ManageSharingScreen` and passing only `() -> Unit` lambdas down to `PrimaryContent`. Snackbar feedback is wired through `SnackbarHostState` hoisted into `ManageSharingScreen`'s `Scaffold`.

**Tech Stack:** Jetpack Compose, Material 3 (`OutlinedButton`, `SnackbarHost`, `Icon`), `LocalClipboardManager`, `LocalContext`, Android `Intent.ACTION_SEND`.

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `app/src/main/java/com/babytracker/ui/sharing/ManageSharingScreen.kt` | Modify | All changes live here — snackbar wiring, lambdas, Row with buttons |

No new files are created. No other files are touched.

---

### Task 1: Wire SnackbarHost into the Scaffold

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/sharing/ManageSharingScreen.kt:57-146`

- [ ] **Step 1: Add imports for Snackbar, SnackbarHostState, and rememberCoroutineScope**

At the top of the file, inside the existing import block, add:

```kotlin
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Create SnackbarHostState and CoroutineScope in ManageSharingScreen**

Inside `ManageSharingScreen`, directly after the `var showNewCodeDialog by remember { mutableStateOf(false) }` line, add:

```kotlin
val snackbarHostState = remember { SnackbarHostState() }
val scope = rememberCoroutineScope()
```

- [ ] **Step 3: Wire snackbarHost into the Scaffold**

In `ManageSharingScreen`'s `Scaffold(...)` call, add the `snackbarHost` parameter after the `topBar` parameter:

```kotlin
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("Partner Sharing") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
) { padding ->
```

- [ ] **Step 4: Verify compilation**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Spec review**

Check:
- `SnackbarHostState` is created with `remember` ✓
- `rememberCoroutineScope` is used for the scope ✓
- `snackbarHost = { SnackbarHost(snackbarHostState) }` is on the `Scaffold` in `ManageSharingScreen` (not a nested composable) ✓
- No second `Scaffold` was introduced ✓

- [ ] **Step 6: Code review**

Check:
- No `snackbarHostState` or `scope` are passed into child composables yet (they will only be used via lambdas in Task 2) ✓
- Imports are clean and only what is needed ✓

- [ ] **Step 7: Commit**

```bash
./gradlew ktlintFormat
git add app/src/main/java/com/babytracker/ui/sharing/ManageSharingScreen.kt
git commit -m "feat(sharing): wire SnackbarHost into ManageSharingScreen Scaffold"
```

---

### Task 2: Define callbacks and render Copy / Share buttons in PrimaryContent

This task defines the lambdas in `ManageSharingScreen`, updates `PrimaryContent`'s signature, adds the button `Row`, and wires the call site — all in one commit to avoid an intermediate state with unused local variables.

**Files:**
- Modify: `app/src/main/java/com/babytracker/ui/sharing/ManageSharingScreen.kt:57-304`

- [ ] **Step 1: Add all remaining imports**

Add to the import block:

```kotlin
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
```

- [ ] **Step 2: Define shareCode local val and the onCopyCode lambda**

Directly after `val scope = rememberCoroutineScope()`, add:

```kotlin
val shareCode = uiState.shareCode.orEmpty()
val clipboard = LocalClipboardManager.current
val onCopyCode: () -> Unit = {
    clipboard.setText(AnnotatedString(shareCode))
    scope.launch { snackbarHostState.showSnackbar("Code copied to clipboard") }
}
```

- [ ] **Step 3: Define the onShareCode lambda**

Directly after `onCopyCode`, add:

```kotlin
val context = LocalContext.current
val onShareCode: () -> Unit = {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "Use this code to connect to my baby tracker: $shareCode",
        )
    }
    try {
        context.startActivity(Intent.createChooser(sendIntent, null))
    } catch (e: ActivityNotFoundException) {
        scope.launch { snackbarHostState.showSnackbar("No app available to share this code.") }
    }
}
```

- [ ] **Step 4: Add onCopyCode and onShareCode parameters to PrimaryContent signature**

Replace:

```kotlin
@Composable
private fun PrimaryContent(
    shareCode: String,
    partners: List<PartnerInfo>,
    isLoading: Boolean,
    error: String?,
    onStopSharing: () -> Unit,
    onGenerateNewCode: () -> Unit,
    onRevokePartner: (String) -> Unit,
    onClearError: () -> Unit,
)
```

With:

```kotlin
@Composable
private fun PrimaryContent(
    shareCode: String,
    partners: List<PartnerInfo>,
    isLoading: Boolean,
    error: String?,
    onCopyCode: () -> Unit,
    onShareCode: () -> Unit,
    onStopSharing: () -> Unit,
    onGenerateNewCode: () -> Unit,
    onRevokePartner: (String) -> Unit,
    onClearError: () -> Unit,
)
```

- [ ] **Step 5: Add the Copy / Share Row inside PrimaryContent, below the share code Card**

In `PrimaryContent`, find the `Spacer(modifier = Modifier.height(24.dp))` that follows the share code `Card { ... }` block (line ~246). Insert the `Row` between the `Card` closing brace and that `Spacer`:

```kotlin
        } // end of Card
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCopyCode,
                enabled = !isLoading && shareCode.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Copy")
            }
            OutlinedButton(
                onClick = onShareCode,
                enabled = !isLoading && shareCode.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Share")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
```

- [ ] **Step 6: Pass the new callbacks from ManageSharingScreen into PrimaryContent**

In `ManageSharingScreen`, find the `PrimaryContent(...)` call site and add the two new parameters:

```kotlin
AppMode.PRIMARY -> PrimaryContent(
    shareCode = shareCode,
    partners = uiState.partners,
    isLoading = uiState.isLoading,
    error = uiState.error,
    onCopyCode = onCopyCode,
    onShareCode = onShareCode,
    onStopSharing = { showStopDialog = true },
    onGenerateNewCode = { showNewCodeDialog = true },
    onRevokePartner = viewModel::revokePartner,
    onClearError = viewModel::clearError,
)
```

Note: `shareCode = shareCode` uses the local `val shareCode` defined in Step 2 — no `.orEmpty()` needed at the call site.

- [ ] **Step 7: Verify compilation**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Spec review**

Check:
- `onCopyCode` and `onShareCode` are defined in `ManageSharingScreen`, not inside `PrimaryContent` ✓
- `Intent.EXTRA_TEXT` value matches the spec: `"Use this code to connect to my baby tracker: $shareCode"` ✓
- `ActivityNotFoundException` is caught and shows `"No app available to share this code."` via Snackbar ✓
- Neither `snackbarHostState` nor `scope` are passed as parameters to `PrimaryContent` ✓
- `Row` uses `Arrangement.spacedBy(8.dp)` ✓
- Both buttons have `Modifier.weight(1f)` ✓
- Both buttons are `OutlinedButton` ✓
- Both buttons are `enabled = !isLoading && shareCode.isNotBlank()` — disabled when loading OR when code is blank ✓
- Copy button uses `Icons.Default.ContentCopy` ✓
- Share button uses `Icons.Default.Share` ✓

- [ ] **Step 9: Code review**

Check:
- Lambdas close over `shareCode` — a `val` derived from `uiState.shareCode.orEmpty()`, recreated each recomposition with the latest code value. Correct Compose behavior. ✓
- `Intent.createChooser(sendIntent, null)` — `null` title lets the system provide one. ✓
- `scope` is a `rememberCoroutineScope()` tied to the composable lifecycle — no scope leaks ✓
- `contentDescription = null` on decorative icons is correct — the button label already describes the action ✓
- `Spacer(Modifier.width(8.dp))` between icon and text matches spec exactly ✓
- `Row` is placed between the share code `Card` and the "CONNECTED PARTNERS" section ✓

- [ ] **Step 10: Run all unit tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL` with all tests passing. These are ViewModel tests — the new callbacks live purely in the Compose layer and require no unit test changes.

- [ ] **Step 11: Run ktlintFormat and detekt**

```bash
./gradlew ktlintFormat
./gradlew detekt
```

Expected: `BUILD SUCCESSFUL` with no violations.

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/babytracker/ui/sharing/ManageSharingScreen.kt
git commit -m "feat(sharing): add Copy and Share buttons to share code card"
```

---

## Manual Test Checklist

After all tasks are complete, perform these manual checks on a device or emulator:

- [ ] Navigate to Settings → Partner Sharing → Start Sharing (or already in PRIMARY mode)
- [ ] Tap **Copy** → verify Snackbar shows "Code copied to clipboard" and clipboard contains the 8-character code
- [ ] Tap **Share** → verify Android system chooser opens with pre-filled text "Use this code to connect to my baby tracker: XXXXXXXX"
- [ ] Tap **Generate New Code** → while regenerating (isLoading = true or code is blank), verify both buttons are disabled
- [ ] On a restricted emulator with no share targets: trigger Share → verify Snackbar shows "No app available to share this code." (simulate by temporarily removing share targets or using an API-level test)
