# Plan 03: Version-controlled Firestore security rules + emulator tests

LINEAR_ISSUE: [AKA-119](https://linear.app/akachan/issue/AKA-119/spec-007-plan-03-version-controlled-firestore-security-rules-emulator)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring Firestore security rules into the repo (blocking requirement of SPEC-007: console-only rules are unacceptable once a partner→primary write path exists) and gate the new `feedOps` subcollection, with emulator tests proving the gates.

**Architecture:** Pure infra change, zero Android code. `firestore.rules` + `firebase.json` + `.firebaserc` at repo root; a small Node toolchain under `firebase/` runs `@firebase/rules-unit-testing` against the Firestore emulator. This sits **outside** the Android app, so the "no new remote integrations" rule is untouched.

**Tech Stack:** Firebase CLI, Firestore security rules language, Node 20+, `@firebase/rules-unit-testing` v3, vitest.

**Spec:** `specs/SPEC-007-PARTNER-BOTTLE-FEED-LOGGING.md` — "Security rules (version-controlled — blocking requirement)".

**Dependencies:** None (can merge before any Android plan). Plans 04/05 rely on these rules being **deployed** at runtime, not at compile time.

**Suggested branch:** `chore/firestore-security-rules`

---

## File Structure

- Create: `firestore.rules` — full ruleset (existing console rules + `feedOps` block).
- Create: `firebase.json` — points deploy + emulator at the rules file.
- Create: `.firebaserc` — default project `akachan-baby-tracker` (matches `app/google-services.json` `project_id`).
- Create: `firebase/package.json`, `firebase/feedOps.rules.test.mjs` — emulator test suite.
- Create: `firebase/README.md` — deploy-as-release-step documentation.
- Modify: `.gitignore` — `firebase/node_modules/`, emulator debug logs.

---

### Task 1: Export existing rules from console — ⚠️ REQUIRES HUMAN

The currently deployed rules exist only in the Firebase console. They MUST be copied verbatim first — do not reconstruct them from memory or invent them.

- [ ] **Step 1 (human):** Firebase console → Firestore Database → Rules → copy the entire active ruleset → paste into a new `firestore.rules` at the repo root. Commit nothing yet.
- [ ] **Step 2: Sanity-check assumptions.** The data model the new rules depend on (verify these paths appear in the exported rules / match `FirestoreSharingService`):
  - share doc: `shares/{code}` with `owner.uid` (string)
  - partner registry: `shares/{code}/partners/{partnerUid}`

  If the exported rules use different helper names or structures, adapt Task 2 to the exported reality — the spec's block is the contract, not the literal text.

### Task 2: Add `feedOps` rules

**Files:**
- Modify: `firestore.rules`

- [ ] **Step 1: Ensure/add helper functions** (inside `match /databases/{database}/documents`), reusing exported equivalents if present:

```
function isOwner(code) {
  return request.auth != null
    && request.auth.uid == get(/databases/$(database)/documents/shares/$(code)).data.owner.uid;
}

function isConnectedPartner(code) {
  return request.auth != null
    && exists(/databases/$(database)/documents/shares/$(code)/partners/$(request.auth.uid));
}
```

- [ ] **Step 2: Add the `feedOps` block** nested inside the existing `match /shares/{code}` block:

```
function isValidFeedOp(op) {
  return op.keys().hasOnly(['action', 'entryClientId', 'authorUid', 'createdAtMs',
                            'timestampMs', 'volumeMl', 'type', 'notes', 'consumedBagId'])
    && op.keys().hasAll(['action', 'entryClientId', 'authorUid', 'createdAtMs'])
    && op.action in ['create', 'update', 'delete']
    && op.entryClientId is string
    && op.entryClientId.size() > 0 && op.entryClientId.size() <= 64
    && op.authorUid is string
    && op.createdAtMs is int
    && (op.action == 'delete'
        || (op.keys().hasAll(['timestampMs', 'volumeMl', 'type'])
            && op.timestampMs is int
            && op.volumeMl is int && op.volumeMl > 0 && op.volumeMl <= 5000
            && op.type is string
            && (!('notes' in op) || op.notes is string)))
    && (op.action == 'create' || !('consumedBagId' in op))
    && (!('consumedBagId' in op) || op.consumedBagId is int);
}

match /shares/{code} {
  // existing read/owner-write rules unchanged
  match /feedOps/{opId} {
    allow read: if isOwner(code)
                || (isConnectedPartner(code) && request.auth.uid == resource.data.authorUid);
    allow create: if isConnectedPartner(code)
                  && request.resource.data.authorUid == request.auth.uid
                  && isValidFeedOp(request.resource.data);
    allow update: if isConnectedPartner(code)
                  && resource.data.authorUid == request.auth.uid
                  && request.resource.data.authorUid == resource.data.authorUid
                  && isValidFeedOp(request.resource.data);
    allow delete: if isOwner(code)
                  || (isConnectedPartner(code) && request.auth.uid == resource.data.authorUid);
  }
}
```

**Deliberate hardening over the spec's literal block** (the spec's rules are the contract's floor, not its ceiling):

1. **Split `create`/`update`:** the spec's combined `allow create, update` only constrains the *post*-update `authorUid`, so any connected client could take over another author's op by rewriting `authorUid` to itself (and then delete it as "author"). Update now requires the caller to be the original author and makes `authorUid` immutable. Single-partner is today's product scope, but `partners/` is structurally multi-entry — rules must not rely on product scope.
2. **Revocation severs everything:** author read/delete additionally require `isConnectedPartner(code)`. Without it, a revoked (or reinstalled) partner's anonymous uid would keep read/delete over its old ops forever. Revoking the partner doc now cuts read, create, update, and delete in one move.
3. **Schema validation (`isValidFeedOp`):** structural gate on keys, types, action enum, `volumeMl` range (`0 < v <= 5000`), and create-only `consumedBagId` — a connected-but-buggy/hostile client cannot park arbitrary documents in the inbox. This complements (never replaces) the primary's apply-time re-validation in Plan 04, which stays authoritative: rules can't check cross-document facts (entry ownership, future timestamps vs device clock, bag state).

Rationale recap (from spec): rules verify **who** wrote an op but cannot see Room state — entry-level ownership (op targets an OWNER entry?) is enforced on the primary at apply time (Plan 04). Both layers are blocking requirements.

### Task 3: Firebase project config

**Files:**
- Create: `firebase.json`, `.firebaserc`
- Modify: `.gitignore`

- [ ] **Step 1: `firebase.json`** (repo root):

```json
{
  "firestore": {
    "rules": "firestore.rules"
  },
  "emulators": {
    "firestore": {
      "port": 8080
    },
    "ui": {
      "enabled": false
    }
  }
}
```

- [ ] **Step 2: `.firebaserc`** (repo root):

```json
{
  "projects": {
    "default": "akachan-baby-tracker"
  }
}
```

- [ ] **Step 3: `.gitignore`** — append:

```
firebase/node_modules/
firebase-debug.log
firestore-debug.log
```

### Task 4: Emulator test suite

**Files:**
- Create: `firebase/package.json`
- Create: `firebase/feedOps.rules.test.mjs`

- [ ] **Step 1: `firebase/package.json`**

```json
{
  "name": "akachan-firestore-rules-tests",
  "private": true,
  "type": "module",
  "scripts": {
    "test": "firebase emulators:exec --only firestore \"vitest run\""
  },
  "devDependencies": {
    "@firebase/rules-unit-testing": "^3.0.4",
    "firebase-admin": "^12.0.0",
    "firebase-tools": "^13.0.0",
    "vitest": "^2.0.0"
  }
}
```

(`firebase emulators:exec` reads `../firebase.json` when run from `firebase/`? No — run from repo root or pass `--config`. Use the script as: run `npm test` from `firebase/` with `--config ../firebase.json`; if the installed CLI version rejects relative config, run from repo root: `npx firebase emulators:exec --only firestore "npm --prefix firebase run vitest"`. Pin whichever works during implementation and record the canonical invocation in `firebase/README.md`.)

- [ ] **Step 2: Test file `firebase/feedOps.rules.test.mjs`** — covers the four spec-mandated cases plus revoked-partner denial:

```javascript
import { readFileSync } from "node:fs";
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from "@firebase/rules-unit-testing";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

const CODE = "SHARE1";
const OWNER = "owner-uid";
const PARTNER = "partner-uid";
const STRANGER = "stranger-uid";

let env;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "akachan-baby-tracker",
    firestore: { rules: readFileSync("../firestore.rules", "utf8") },
  });
});

afterAll(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc(`shares/${CODE}`).set({ owner: { uid: OWNER } });
    await db.doc(`shares/${CODE}/partners/${PARTNER}`).set({ connectedAt: new Date() });
  });
});

const op = (authorUid) => ({
  action: "create",
  entryClientId: "entry-1",
  authorUid,
  createdAtMs: Date.now(),
  timestampMs: Date.now(),
  volumeMl: 120,
  type: "FORMULA",
});

describe("feedOps rules", () => {
  it("connected partner can create an op with authorUid == auth.uid", async () => {
    const db = env.authenticatedContext(PARTNER).firestore();
    await assertSucceeds(db.doc(`shares/${CODE}/feedOps/op1`).set(op(PARTNER)));
  });

  it("non-partner (not in partners/) is denied", async () => {
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(db.doc(`shares/${CODE}/feedOps/op1`).set(op(STRANGER)));
  });

  it("authorUid spoofing is denied", async () => {
    const db = env.authenticatedContext(PARTNER).firestore();
    await assertFails(db.doc(`shares/${CODE}/feedOps/op1`).set(op(OWNER)));
  });

  it("owner can delete an op", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`shares/${CODE}/feedOps/op1`).set(op(PARTNER));
    });
    const db = env.authenticatedContext(OWNER).firestore();
    await assertSucceeds(db.doc(`shares/${CODE}/feedOps/op1`).delete());
  });

  it("op author can delete own op", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`shares/${CODE}/feedOps/op1`).set(op(PARTNER));
    });
    const db = env.authenticatedContext(PARTNER).firestore();
    await assertSucceeds(db.doc(`shares/${CODE}/feedOps/op1`).delete());
  });

  it("other uid cannot delete someone else's op", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`shares/${CODE}/feedOps/op1`).set(op(PARTNER));
    });
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(db.doc(`shares/${CODE}/feedOps/op1`).delete());
  });

  it("author can update own op but cannot change authorUid", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`shares/${CODE}/feedOps/op1`).set(op(PARTNER));
    });
    const db = env.authenticatedContext(PARTNER).firestore();
    await assertSucceeds(db.doc(`shares/${CODE}/feedOps/op1`).update({ volumeMl: 90 }));
    await assertFails(db.doc(`shares/${CODE}/feedOps/op1`).update({ authorUid: STRANGER }));
  });

  it("a different connected partner cannot take over another author's op", async () => {
    const SECOND_PARTNER = "second-partner-uid";
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await db.doc(`shares/${CODE}/partners/${SECOND_PARTNER}`).set({ connectedAt: new Date() });
      await db.doc(`shares/${CODE}/feedOps/op1`).set(op(PARTNER));
    });
    const db = env.authenticatedContext(SECOND_PARTNER).firestore();
    await assertFails(db.doc(`shares/${CODE}/feedOps/op1`).update({ authorUid: SECOND_PARTNER }));
    await assertFails(db.doc(`shares/${CODE}/feedOps/op1`).update({ volumeMl: 60 }));
  });

  it("revoked partner loses create, read, update, and delete — even on own ops", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await db.doc(`shares/${CODE}/feedOps/op1`).set(op(PARTNER));
      await db.doc(`shares/${CODE}/partners/${PARTNER}`).delete();
    });
    const db = env.authenticatedContext(PARTNER).firestore();
    await assertFails(db.doc(`shares/${CODE}/feedOps/op2`).set(op(PARTNER)));
    await assertFails(db.doc(`shares/${CODE}/feedOps/op1`).get());
    await assertFails(db.doc(`shares/${CODE}/feedOps/op1`).update({ volumeMl: 60 }));
    await assertFails(db.doc(`shares/${CODE}/feedOps/op1`).delete());
  });

  it("malformed payloads are denied even from a connected partner", async () => {
    const db = env.authenticatedContext(PARTNER).firestore();
    // unknown extra field
    await assertFails(db.doc(`shares/${CODE}/feedOps/x1`).set({ ...op(PARTNER), evil: true }));
    // invalid action
    await assertFails(db.doc(`shares/${CODE}/feedOps/x2`).set({ ...op(PARTNER), action: "drop" }));
    // non-positive volume
    await assertFails(db.doc(`shares/${CODE}/feedOps/x3`).set({ ...op(PARTNER), volumeMl: 0 }));
    // missing required payload field on create
    const { volumeMl, ...noVolume } = op(PARTNER);
    await assertFails(db.doc(`shares/${CODE}/feedOps/x4`).set(noVolume));
    // consumedBagId is create-only
    await assertFails(db.doc(`shares/${CODE}/feedOps/x5`).set({
      action: "update", entryClientId: "entry-1", authorUid: PARTNER,
      createdAtMs: Date.now(), timestampMs: Date.now(), volumeMl: 100,
      type: "BREAST_MILK", consumedBagId: 7,
    }));
    // delete op carries no payload beyond the envelope
    await assertSucceeds(db.doc(`shares/${CODE}/feedOps/x6`).set({
      action: "delete", entryClientId: "entry-1", authorUid: PARTNER, createdAtMs: Date.now(),
    }));
  });

  it("partner can read own op, not others; owner reads all", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`shares/${CODE}/feedOps/op1`).set(op(PARTNER));
    });
    await assertSucceeds(env.authenticatedContext(PARTNER).firestore().doc(`shares/${CODE}/feedOps/op1`).get());
    await assertSucceeds(env.authenticatedContext(OWNER).firestore().doc(`shares/${CODE}/feedOps/op1`).get());
    await assertFails(env.authenticatedContext(STRANGER).firestore().doc(`shares/${CODE}/feedOps/op1`).get());
  });
});
```

- [ ] **Step 3: Regression guard for existing rules.** Add one test asserting the pre-existing contract still holds after the edit (partner can read `shares/{code}`, stranger cannot) — exact assertions depend on the exported ruleset from Task 1; write them against what was exported.

- [ ] **Step 4: Run the suite**

Run (from `firebase/`): `npm install && npm test`
Expected: all tests PASS against the emulator. Requires Java (emulator) + Node 20+.

### Task 5: Release-step documentation

**Files:**
- Create: `firebase/README.md`

- [ ] **Step 1:** Document:

```markdown
# Firestore rules

`firestore.rules` (repo root) is the single source of truth — the console is a deploy target, not an editor.

## Test
cd firebase && npm install && npm test

## Deploy (release step — required whenever firestore.rules changes)
firebase deploy --only firestore:rules

Deploy from repo root with the Firebase CLI authenticated against the
`akachan-baby-tracker` project. Run the tests first; never deploy a red ruleset.
```

### Task 6: Commit

- [ ] **Step 1: Commit** (no Gradle validation needed — nothing in `:app` changed; pre-commit ktlint/detekt will pass trivially):

```bash
git add firestore.rules firebase.json .firebaserc firebase/ .gitignore
git commit -m "chore(sharing): version-control Firestore rules with feedOps gates and emulator tests"
```

---

## Acceptance Criteria

- [ ] `firestore.rules` contains the **exported console rules verbatim** plus the `feedOps` block; no invented/reconstructed legacy rules.
- [ ] `feedOps` rules enforce: create only by connected partner with `authorUid == auth.uid`; update only by the op's original author with `authorUid` immutable; read/delete by owner, or by op author **only while still connected**.
- [ ] `isValidFeedOp` schema gate: closed key set, required fields per action, action enum, `volumeMl` range, create-only `consumedBagId`.
- [ ] Emulator tests prove: cross-author update takeover denied; `authorUid` immutable; revoked partner loses create/read/update/delete including own ops; malformed payloads (extra field, bad action, zero volume, missing field, update with `consumedBagId`) denied; well-formed delete op accepted.
- [ ] `firebase.json` + `.firebaserc` allow `firebase deploy --only firestore:rules` from repo root.
- [ ] Emulator tests cover the four spec-mandated cases (partner create, non-partner denied, uid spoof denied, delete authorization) + revoked partner + read scoping, all green via `npm test`.
- [ ] Deploy documented as a release step in `firebase/README.md`.
- [ ] No Android code touched.
