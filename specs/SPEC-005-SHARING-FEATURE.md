# SPEC-005: Partner Sharing Feature

**Status:** Active
**Version:** 1.0
**Date:** 2026-05-07

---

## Overview

An optional feature allowing a primary user (parent) to share a read-only live snapshot of tracking data with a partner device. Uses anonymous Firebase Auth and Firestore.

---

## AppMode

`sharing/domain/model/AppMode.kt`

| Value | Meaning |
|-------|---------|
| `NONE` | Sharing not configured; normal app flow |
| `PRIMARY` | This device owns the share |
| `PARTNER` | This device is the read-only viewer |

`AppNavGraph` uses `AppMode` to select start destination: `PARTNER_DASHBOARD` for `PARTNER` mode, otherwise onboarding/home.

---

## Domain Model

All Firestore-bound data goes through snapshot models — never raw Room entities.

| Domain → Snapshot | Converter |
|-------------------|-----------|
| `BreastfeedingSession` → `SessionSnapshot` | `DomainToSnapshot.toSessionSnapshot()` |
| `SleepRecord` → `SleepSnapshot` | `DomainToSnapshot.toSleepSnapshot()` |
| `Baby` → `BabySnapshot` | `DomainToSnapshot.toBabySnapshot()` |

`ShareSnapshot` is the top-level Firestore document model aggregating all three.

---

## Primary User Flow

1. `GenerateShareCodeUseCase` → `FirestoreSharingService.signInAnonymously()` + `createShareDocument()`
2. Share code: 8-character uppercase alphanumeric stored in `ShareCode`
3. On each tracking action: `SyncToFirestoreUseCase` writes updated `ShareSnapshot` to Firestore

## Partner User Flow

1. Partner enters share code in `ConnectPartnerScreen`
2. `ConnectAsPartnerUseCase` validates code, registers partner UID in `shares/{code}/partners/{uid}`
3. `FetchPartnerDataUseCase` reads `ShareSnapshot` from `shares/{code}`
4. `PartnerDashboardScreen` renders read-only — no session start/stop controls

---

## Firebase Patterns

- **Document path:** `shares/{shareCode}`
- **Partner sub-collection:** `shares/{shareCode}/partners/{uid}`
- `FirestoreSharingService` is `@Singleton` provided by `SharingModule`
- Auth is always anonymous — no email/password or OAuth flows
- `SharingModule` (`@InstallIn(SingletonComponent::class)`) provides both `FirebaseFirestore` and `FirebaseAuth`

---

## Package Layout

All sharing code lives under `sharing/`:
```
sharing/
├── data/
│   ├── firebase/          # FirestoreSharingService
│   └── repository/        # SharingRepositoryImpl
├── domain/
│   ├── model/             # AppMode, ShareSnapshot, BabySnapshot,
│   │                      #   SessionSnapshot, SleepSnapshot,
│   │                      #   PartnerInfo, ShareCode, DomainToSnapshot
│   └── repository/        # SharingRepository (interface)
└── usecase/               # ConnectAsPartner, FetchPartnerData,
                           #   GenerateShareCode, RevokePartner,
                           #   SyncToFirestore
```

---

## Anti-Goals

- Never store `BreastfeedingEntity` / `SleepEntity` directly in Firestore
- Do not add non-anonymous (email/OAuth) auth without an explicit design decision
- Do not add sync logic outside `SyncToFirestoreUseCase` and `FirestoreSharingService`
- Do not add Firebase imports outside the `sharing/` package (enforced by `AntiPatternTest`)
