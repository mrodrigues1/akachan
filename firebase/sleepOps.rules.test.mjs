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

const startOp = (authorUid) => ({
  action: "start",
  entryClientId: "sleep-1",
  authorUid,
  createdAtMs: Date.now(),
  startTimeMs: Date.now(),
  sleepType: "nap",
});

const stopOp = (authorUid) => ({
  action: "stop",
  entryClientId: "sleep-1",
  authorUid,
  createdAtMs: Date.now(),
  endTimeMs: Date.now(),
});

const updateOp = (authorUid) => ({
  action: "update",
  entryClientId: "sleep-1",
  authorUid,
  createdAtMs: Date.now(),
  startTimeMs: Date.now(),
  sleepType: "night_sleep",
  notes: "moved earlier",
});

describe("sleepOps rules", () => {
  it("connected partner can create start / stop / update ops with authorUid == auth.uid", async () => {
    const db = env.authenticatedContext(PARTNER).firestore();
    await assertSucceeds(db.doc(`shares/${CODE}/sleepOps/s1`).set(startOp(PARTNER)));
    await assertSucceeds(db.doc(`shares/${CODE}/sleepOps/s2`).set(stopOp(PARTNER)));
    await assertSucceeds(db.doc(`shares/${CODE}/sleepOps/s3`).set(updateOp(PARTNER)));
  });

  it("non-partner (not in partners/) is denied", async () => {
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(db.doc(`shares/${CODE}/sleepOps/s1`).set(startOp(STRANGER)));
  });

  it("authorUid spoofing is denied", async () => {
    const db = env.authenticatedContext(PARTNER).firestore();
    await assertFails(db.doc(`shares/${CODE}/sleepOps/s1`).set(startOp(OWNER)));
  });

  it("sleep ops are write-once: a prior op cannot be mutated", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`shares/${CODE}/sleepOps/s1`).set(startOp(PARTNER));
    });
    const db = env.authenticatedContext(PARTNER).firestore();
    await assertFails(db.doc(`shares/${CODE}/sleepOps/s1`).update({ sleepType: "night_sleep" }));
  });

  it("owner and author partner can delete; stranger cannot", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`shares/${CODE}/sleepOps/s1`).set(startOp(PARTNER));
    });
    await assertFails(env.authenticatedContext(STRANGER).firestore().doc(`shares/${CODE}/sleepOps/s1`).delete());
    await assertSucceeds(env.authenticatedContext(PARTNER).firestore().doc(`shares/${CODE}/sleepOps/s1`).delete());
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`shares/${CODE}/sleepOps/s2`).set(startOp(PARTNER));
    });
    await assertSucceeds(env.authenticatedContext(OWNER).firestore().doc(`shares/${CODE}/sleepOps/s2`).delete());
  });

  it("partner reads own op, owner reads all, stranger denied", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`shares/${CODE}/sleepOps/s1`).set(startOp(PARTNER));
    });
    await assertSucceeds(env.authenticatedContext(PARTNER).firestore().doc(`shares/${CODE}/sleepOps/s1`).get());
    await assertSucceeds(env.authenticatedContext(OWNER).firestore().doc(`shares/${CODE}/sleepOps/s1`).get());
    await assertFails(env.authenticatedContext(STRANGER).firestore().doc(`shares/${CODE}/sleepOps/s1`).get());
  });

  it("revoked partner loses create, read, and delete — even on own ops", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await db.doc(`shares/${CODE}/sleepOps/s1`).set(startOp(PARTNER));
      await db.doc(`shares/${CODE}/partners/${PARTNER}`).delete();
    });
    const db = env.authenticatedContext(PARTNER).firestore();
    await assertFails(db.doc(`shares/${CODE}/sleepOps/s2`).set(startOp(PARTNER)));
    await assertFails(db.doc(`shares/${CODE}/sleepOps/s1`).get());
    await assertFails(db.doc(`shares/${CODE}/sleepOps/s1`).delete());
  });

  it("malformed payloads are denied even from a connected partner", async () => {
    const db = env.authenticatedContext(PARTNER).firestore();
    // unknown extra field
    await assertFails(db.doc(`shares/${CODE}/sleepOps/x1`).set({ ...startOp(PARTNER), evil: true }));
    // invalid action
    await assertFails(db.doc(`shares/${CODE}/sleepOps/x2`).set({ ...startOp(PARTNER), action: "delete" }));
    // start missing sleepType
    const { sleepType, ...startNoType } = startOp(PARTNER);
    await assertFails(db.doc(`shares/${CODE}/sleepOps/x3`).set(startNoType));
    // start missing startTimeMs
    const { startTimeMs, ...startNoTime } = startOp(PARTNER);
    await assertFails(db.doc(`shares/${CODE}/sleepOps/x4`).set(startNoTime));
    // stop missing endTimeMs
    const { endTimeMs, ...stopNoEnd } = stopOp(PARTNER);
    await assertFails(db.doc(`shares/${CODE}/sleepOps/x5`).set(stopNoEnd));
    // update missing sleepType
    const { sleepType: _t, ...updateNoType } = updateOp(PARTNER);
    await assertFails(db.doc(`shares/${CODE}/sleepOps/x6`).set(updateNoType));
    // wrong sleepType value
    await assertFails(db.doc(`shares/${CODE}/sleepOps/x7`).set({ ...startOp(PARTNER), sleepType: "siesta" }));
    // oversized entryClientId (> 64 chars)
    await assertFails(db.doc(`shares/${CODE}/sleepOps/x8`).set({ ...startOp(PARTNER), entryClientId: "a".repeat(65) }));
    // empty entryClientId
    await assertFails(db.doc(`shares/${CODE}/sleepOps/x9`).set({ ...startOp(PARTNER), entryClientId: "" }));
  });
});
