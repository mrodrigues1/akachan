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

describe("shares document rules", () => {
  it("any authenticated user can read a share doc; unauthenticated cannot", async () => {
    await assertSucceeds(env.authenticatedContext(PARTNER).firestore().doc(`shares/${CODE}`).get());
    await assertSucceeds(env.authenticatedContext(STRANGER).firestore().doc(`shares/${CODE}`).get());
    await assertFails(env.unauthenticatedContext().firestore().doc(`shares/${CODE}`).get());
  });

  it("create requires owner.uid == auth.uid", async () => {
    const db = env.authenticatedContext(OWNER).firestore();
    await assertSucceeds(db.doc("shares/NEW1").set({ owner: { uid: OWNER, createdAt: new Date() } }));
    await assertFails(db.doc("shares/NEW2").set({ owner: { uid: STRANGER, createdAt: new Date() } }));
  });

  it("only the owner can update the snapshot; owner.uid is immutable", async () => {
    const sync = { data: { lastSyncAt: new Date() } };
    await assertSucceeds(
      env.authenticatedContext(OWNER).firestore().doc(`shares/${CODE}`).set(sync, { merge: true }),
    );
    await assertFails(
      env.authenticatedContext(PARTNER).firestore().doc(`shares/${CODE}`).set(sync, { merge: true }),
    );
    await assertFails(
      env.authenticatedContext(STRANGER).firestore().doc(`shares/${CODE}`).set(
        { owner: { uid: STRANGER } },
        { merge: true },
      ),
    );
    await assertFails(
      env.authenticatedContext(OWNER).firestore().doc(`shares/${CODE}`).set(
        { owner: { uid: STRANGER } },
        { merge: true },
      ),
    );
  });

  it("only the owner can delete a share doc", async () => {
    await assertFails(env.authenticatedContext(PARTNER).firestore().doc(`shares/${CODE}`).delete());
    await assertSucceeds(env.authenticatedContext(OWNER).firestore().doc(`shares/${CODE}`).delete());
  });
});

describe("partners registry rules", () => {
  it("a user can register only their own partner doc", async () => {
    const NEW_UID = "new-partner-uid";
    await assertSucceeds(
      env.authenticatedContext(NEW_UID).firestore()
        .doc(`shares/${CODE}/partners/${NEW_UID}`).set({ connectedAt: new Date() }),
    );
    await assertFails(
      env.authenticatedContext(STRANGER).firestore()
        .doc(`shares/${CODE}/partners/victim-uid`).set({ connectedAt: new Date() }),
    );
    await assertFails(
      env.unauthenticatedContext().firestore()
        .doc(`shares/${CODE}/partners/${NEW_UID}`).set({ connectedAt: new Date() }),
    );
  });

  it("owner and the partner themselves can delete a partner doc; others cannot", async () => {
    await assertFails(
      env.authenticatedContext(STRANGER).firestore().doc(`shares/${CODE}/partners/${PARTNER}`).delete(),
    );
    await assertSucceeds(
      env.authenticatedContext(OWNER).firestore().doc(`shares/${CODE}/partners/${PARTNER}`).delete(),
    );
  });

  it("partner doc cannot be created under a nonexistent share code", async () => {
    await assertFails(
      env.authenticatedContext(STRANGER).firestore()
        .doc(`shares/NOSUCH/partners/${STRANGER}`).set({ connectedAt: new Date() }),
    );
  });

  it("an orphan partner doc under a missing share does not authorize feedOps", async () => {
    const ORPHAN = "orphan-uid";
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`shares/GHOST/partners/${ORPHAN}`).set({ connectedAt: new Date() });
    });
    const db = env.authenticatedContext(ORPHAN).firestore();
    await assertFails(db.doc("shares/GHOST/feedOps/op1").set(op(ORPHAN)));
  });

  it("owner can list partners; a stranger cannot", async () => {
    await assertSucceeds(
      env.authenticatedContext(OWNER).firestore().collection(`shares/${CODE}/partners`).get(),
    );
    await assertFails(
      env.authenticatedContext(STRANGER).firestore().collection(`shares/${CODE}/partners`).get(),
    );
  });
});
