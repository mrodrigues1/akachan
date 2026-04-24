# Partner Sharing — Design Spec

**Date:** 2026-04-24  
**Branch:** `feat/partner-sharing`  
**Status:** Approved

---

## Overview

Allow the primary parent to share their baby tracking data (breastfeeding sessions, sleep records, baby profile) with a partner in read-only mode. The partner installs the same app, enters an 8-character pairing code, and sees a read-only dashboard. Data flows from the primary device to Firebase Firestore on meaningful events; the partner fetches a snapshot on demand.

The app remains **offline-first** for all tracking functionality. Firebase is used exclusively as a relay for the sharing feature and is never accessed by users who do not enable sharing.

---

## Goals

- Partner can see baby profile, last 20 breastfeeding sessions, and last 20 sleep records
- Partner view is read-only — no logging or editing
- Connection established via an 8-character alphanumeric code (no account required)
- Primary user can revoke individual partner access from Settings
- Zero Firebase calls for users who never enable sharing
- App remains functional offline; sharing is a gracefully-degraded enhancement

## Non-Goals

- Real-time live sync (partner does not see a ticking timer)
- Partner logging sessions or editing data
- More than one primary per device
- Web viewer (partner must install the app)
- Push notifications to the partner when a session starts

---

## Architecture

### App Modes

A new `AppMode` enum stored in DataStore drives routing:

| Mode | Meaning |
|---|---|
| `NONE` | Default for all existing installs. No Firebase calls. |
| `PRIMARY` | Normal app + sharing features. Syncs to Firestore on events. |
| `PARTNER` | Read-only mode. Normal screens hidden. Only partner dashboard shown. |

Mode is set once during setup. Switching roles clears the previous mode.

### Data Flow

```
Primary Device                     Firestore
──────────────                     ─────────
Room DB ──► SyncService ──────────► /shares/{code}/data
                │
           (on session events
            + app open)

                                   Partner Device
                                   ──────────────
                                   FetchService ◄── partner taps "Refresh"
                                        │
                                        ▼
                                   PartnerDashboardScreen (read-only)
```

Room remains the single source of truth for all local data. Firestore is a write-through cache for partner consumption only.

---

## Firebase Data Model

One Firestore document per share, keyed by the share code:

```
/shares/{shareCode}/
  owner:
    uid: String              # anonymous Firebase UID of primary device
    createdAt: Timestamp

  data:                      # written by primary on sync events
    lastSyncAt: Timestamp
    baby:
      name: String
      birthDate: Long        # epoch ms
      allergies: [String]    # allergy type names only (e.g. ["DAIRY", "PEANUTS"])
    sessions: [              # last 20 breastfeeding sessions, descending by startTime
      {
        id: Long
        startTime: Long      # epoch ms
        endTime: Long?
        startingSide: String # "LEFT" | "RIGHT"
        switchTime: Long?
        pausedDurationMs: Long
        notes: String?
      }
    ]
    sleepRecords: [          # last 20 sleep records, descending by startTime
      {
        id: Long
        startTime: Long
        endTime: Long?
        sleepType: String    # "NAP" | "NIGHT_SLEEP"
        notes: String?
      }
    ]

  partners/ (sub-collection)
    {partnerUid}:
      connectedAt: Timestamp
```

**Constraints:**
- Last 20 sessions and 20 sleep records only — keeps document well within Firestore's 1 MB limit
- Notes are synced for sessions and sleep records but not surfaced prominently in the partner UI
- Share code format: 8 uppercase alphanumeric characters (e.g. `ABCD1234`) — ~2.8 trillion combinations, collision handled by silent regeneration

---

## Pairing Flow

### Primary sets up sharing

1. Settings → Sharing → "Start Sharing"
2. App signs into Firebase anonymously (silent, no UI shown). If an anonymous session already exists on the device (e.g. from a previous sharing setup), Firebase Auth reuses it automatically — no new UID is created.
3. Generates an 8-char alphanumeric code
4. Writes `/shares/{code}` to Firestore with `owner.uid` and `owner.createdAt`
5. Pushes first full data snapshot to `data`
6. Sets `AppMode.PRIMARY` in DataStore
7. Displays the code as large text in the Manage Sharing screen

### Partner connects

1. Home → "Connect as Partner" → enters 8-char code
2. App signs into Firebase anonymously (silent)
3. Looks up `/shares/{code}` — returns error if document does not exist
4. Records partner's anonymous UID in `partners/{partnerUid}` sub-collection
5. Sets `AppMode.PARTNER` in DataStore
6. Navigates to Partner Dashboard

### Revocation

- Primary taps "Remove" next to a partner in Manage Sharing
- Partner's UID is deleted from the `partners/` sub-collection
- On the partner's next refresh, the app checks if its UID still exists in the sub-collection; if not, shows "You've been disconnected" and resets `AppMode` to `NONE`
- Primary can also tap "Generate New Code" — deletes the old `/shares/{code}` document and creates a new one, disconnecting all current partners

### Disabling sharing entirely

- Primary taps "Stop Sharing" in Manage Sharing
- The `/shares/{code}` document is deleted from Firestore (disconnects all partners)
- `AppMode` is reset to `NONE` on the primary device
- The share code is cleared from DataStore
- No local Room data is affected — only the Firestore document is removed

---

## Sync Triggers (Primary → Firestore)

`SyncService` hooks into existing use cases. No new local data paths are introduced.

| Trigger | Payload synced |
|---|---|
| App open (foreground) | Full snapshot: baby + last 20 sessions + last 20 sleep records |
| `StartBreastfeedingSessionUseCase` | Sessions list only |
| `StopBreastfeedingSessionUseCase` | Sessions list only |
| `PauseBreastfeedingSessionUseCase` | Sessions list only |
| `ResumeBreastfeedingSessionUseCase` | Sessions list only |
| `SwitchBreastfeedingSideUseCase` | Sessions list only |
| `StartSleepRecordUseCase` | Sleep records list only |
| `StopSleepRecordUseCase` | Sleep records list only |
| `SaveBabyProfileUseCase` | Baby sub-document only |

**Rules:**
- Sync only runs when `AppMode == PRIMARY` and a share code exists
- Each sync uses Firestore `set` with merge — partial updates do not wipe other fields
- Sync failures are fire-and-forget: logged, not surfaced to the user; next trigger retries
- `lastSyncAt` is always updated so the partner can see data freshness

---

## Partner Dashboard

The partner dashboard is the root screen when `AppMode == PARTNER`. All normal screens (Home, Breastfeeding, Sleep, Settings) are unreachable.

**Content shown:**
- Baby name and computed age
- Active breastfeeding session indicator (if `endTime == null` on the latest session), with side and elapsed time since `startTime` — static, not a live ticker
- Most recent completed breastfeeding session: time elapsed since end, sides used, total duration
- Mini history list: last 3 sessions (side badge, duration, time ago)
- Last sleep record: type, duration, time elapsed since end
- Allergy pills using existing Amber warning tokens

**Controls:**
- "↻ Refresh" button in a top bar — calls `FetchPartnerDataUseCase` and updates the UI
- "Last updated X min ago" timestamp derived from `data.lastSyncAt`
- No edit, no logging, no navigation to other screens

### Connect as Partner Screen

Entry point: Settings screen → new "Partner Access" section → "Connect as Partner" button, visible only when `AppMode == NONE`. For first-time users who haven't completed onboarding, the option also appears at the end of the onboarding welcome step as a secondary action ("Setting up for a partner? Connect here").

Screen content:
- Code input field: uppercase, 8 characters, auto-formats as the user types
- "Connect" button → calls `ConnectAsPartnerUseCase`
- "I'm the primary parent →" ghost button → navigates back to normal home/settings

---

## Error Handling

### Partner device

| Situation | Behaviour |
|---|---|
| Invalid/unknown code | "This code doesn't exist. Check with your partner." |
| Access revoked | "You've been disconnected. Ask your partner for a new code." Clears `AppMode` back to `NONE`. |
| No internet on refresh | "Couldn't refresh. Check your connection." Toast — last fetched data stays visible |
| Primary has never synced | "No data yet. Ask your partner to open their app." |
| Firestore unavailable | Same as no internet — silent fallback, last data stays visible |

### Primary device

| Situation | Behaviour |
|---|---|
| Sync fails on session event | Logged, not shown. Next trigger retries automatically. |
| Firebase auth fails on setup | "Couldn't enable sharing. Check your connection." in Manage Sharing screen |
| Code collision | Regenerate silently — astronomically rare with 8-char space |

---

## New Package Structure

```
com/babytracker/
├── sharing/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── AppMode.kt              # enum: NONE, PRIMARY, PARTNER
│   │   │   ├── ShareCode.kt            # value class wrapping String + validation
│   │   │   └── PartnerInfo.kt          # data class: uid, connectedAt (Instant)
│   │   └── repository/
│   │       └── SharingRepository.kt    # interface
│   ├── data/
│   │   ├── repository/
│   │   │   └── SharingRepositoryImpl.kt
│   │   └── firebase/
│   │       └── FirestoreSharingService.kt  # raw Firestore + Auth calls
│   └── usecase/
│       ├── GenerateShareCodeUseCase.kt
│       ├── ConnectAsPartnerUseCase.kt
│       ├── SyncToFirestoreUseCase.kt
│       ├── FetchPartnerDataUseCase.kt
│       └── RevokePartnerUseCase.kt
├── ui/
│   ├── partner/
│   │   ├── PartnerDashboardScreen.kt
│   │   └── PartnerDashboardViewModel.kt
│   └── sharing/
│       ├── ManageSharingScreen.kt
│       ├── ManageSharingViewModel.kt
│       ├── ConnectPartnerScreen.kt
│       └── ConnectPartnerViewModel.kt
└── di/
    └── SharingModule.kt               # binds SharingRepository, provides FirebaseFirestore
```

### Navigation changes (`AppNavGraph.kt`)

| Route | Condition |
|---|---|
| `connect_partner` | Accessible when `AppMode == NONE` from the home entry point |
| `partner_dashboard` | Root destination when `AppMode == PARTNER` |
| `manage_sharing` | Reachable from Settings when `AppMode == PRIMARY` |

---

## New Dependencies

```kotlin
// app/build.gradle.kts — use the latest stable Firebase BOM at implementation time
// Check https://firebase.google.com/support/release-notes/android for the current version
implementation(platform("com.google.firebase:firebase-bom:<latest-stable>"))
implementation("com.google.firebase:firebase-firestore-ktx")
implementation("com.google.firebase:firebase-auth-ktx")
```

`google-services.json` must be added manually from the Firebase console — one-time setup step outside the code.

---

## Testing Plan

### Unit tests

| Class | Scenarios |
|---|---|
| `GenerateShareCodeUseCase` | Code is 8 chars, uppercase alphanumeric; different codes on repeated calls |
| `ConnectAsPartnerUseCase` | Invalid code → error result; valid code → `AppMode.PARTNER` set in DataStore |
| `SyncToFirestoreUseCase` | No-op when `AppMode != PRIMARY`; calls Firestore service when PRIMARY |
| `RevokePartnerUseCase` | Deletes correct UID from sub-collection; no-op for unknown UID |
| `FetchPartnerDataUseCase` | Returns data on success; returns error when UID not in partners sub-collection |

`FirestoreSharingService` is injected and mocked with MockK in all unit tests. No real Firebase calls in unit tests.

### Manual verification checklist

- [ ] Sharing disabled by default — no Firebase calls on fresh install
- [ ] Code generation produces valid 8-char uppercase alphanumeric strings
- [ ] Partner connects successfully with valid code
- [ ] Partner dashboard shows correct baby name, last session, last sleep, allergies
- [ ] "Refresh" fetches updated data from Firestore
- [ ] "Last updated" timestamp reflects `lastSyncAt` correctly
- [ ] Revoking partner shows disconnection screen on partner's next refresh
- [ ] Generating new code disconnects existing partners
- [ ] Sync fires after session start/stop on primary device
- [ ] No Firebase calls when `AppMode == NONE`
