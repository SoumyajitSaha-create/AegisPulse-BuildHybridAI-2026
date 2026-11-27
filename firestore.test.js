const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_responder_1";
const BOB_UID = "bob_responder_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validIncidentPayload(uid, overrides = {}) {
  const past = new Date(Date.now() - 2000);
  return {
    userId: uid,
    category: "TRIAGE",
    title: "Severe Bleeding — Trail Laceration",
    promptSummary: "Deep cut on forearm while hiking, steady bleeding.",
    urgencyLevel: "CRITICAL",
    guidanceSummary: "Apply firm direct pressure immediately with sterile gauze.",
    checklistSteps: [
      "Apply direct pressure for 10+ minutes",
      "Elevate limb if no fracture suspected",
      "Call 911 / 112 immediately",
    ],
    doNotDoNotes: "Do not remove soaked dressings; stack fresh layers on top.",
    inferenceSource: "ON_DEVICE",
    latencyMs: 340,
    platform: "ANDROID",
    createdAt: past,
    updatedAt: past,
    ...overrides,
  };
}

function validTelemetryPayload(uid, overrides = {}) {
  const past = new Date(Date.now() - 2000);
  return {
    userId: uid,
    taskType: "Emergency Triage",
    requestedMode: "PREFER_ON_DEVICE",
    actualSource: "IN_CLOUD",
    latencyMs: 890,
    promptChars: 142,
    routingReason: "On-device model unavailable; automatic cloud fallback executed.",
    createdAt: past,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write incidents or telemetry", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("incidents").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("incidents")
      .doc("inc_1")
      .set(validIncidentPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("telemetry")
      .doc("tel_1")
      .set(validTelemetryPayload(ALICE_UID))
  );
});

test("Owner can create, list, partially update, and delete valid incidents", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const incRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("incidents")
    .doc("inc_1");

  await assertSucceeds(incRef.set(validIncidentPayload(ALICE_UID)));

  // Matches Kotlin query: collection("users").document(uid).collection("incidents").orderBy("createdAt", DESCENDING)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("incidents")
      .orderBy("createdAt", "desc")
      .get()
  );

  // Valid partial update (preserving userId, category, createdAt)
  await assertSucceeds(
    incRef.update({
      title: "Severe Bleeding — Controlled with Pressure",
      urgencyLevel: "HIGH",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(incRef.delete());
});

test("Incident security bounds reject shadow fields, @DocumentId id, future timestamps, and immutable mutations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("incidents");

  // Reject @DocumentId 'id' inside payload
  await assertFails(col.doc("inc_bad_id").set(validIncidentPayload(ALICE_UID, { id: "inc_bad_id" })));

  // Reject shadow/ghost field
  await assertFails(col.doc("inc_ghost").set(validIncidentPayload(ALICE_UID, { isAdmin: true })));

  // Reject spoofed userId
  await assertFails(col.doc("inc_spoof").set(validIncidentPayload(BOB_UID)));

  // Reject tampered future timestamp
  await assertFails(
    col.doc("inc_future").set(
      validIncidentPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) })
    )
  );

  // Reject invalid platform value, allow WEB platform for Web Command Center
  await assertSucceeds(col.doc("inc_web").set(validIncidentPayload(ALICE_UID, { platform: "WEB" })));
  await assertFails(col.doc("inc_bad_plat").set(validIncidentPayload(ALICE_UID, { platform: "UNKNOWN" })));

  // Reject immutable createdAt mutation on update
  await assertSucceeds(col.doc("inc_imm").set(validIncidentPayload(ALICE_UID)));
  await assertFails(
    col.doc("inc_imm").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation blocks Bob from accessing Alice's incidents or telemetry", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("incidents")
      .doc("inc_alice")
      .set(validIncidentPayload(ALICE_UID));
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("telemetry")
      .doc("tel_alice")
      .set(validTelemetryPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("incidents").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("incidents").doc("inc_alice").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("incidents").doc("inc_alice").delete()
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("telemetry").get());
});

test("Telemetry logs allow owner create/list/delete and forbid updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const telRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("telemetry")
    .doc("tel_1");

  await assertSucceeds(telRef.set(validTelemetryPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("telemetry")
      .orderBy("createdAt", "desc")
      .limit(50)
      .get()
  );

  // Telemetry entries are immutable
  await assertFails(telRef.update({ latencyMs: 10 }));
  await assertSucceeds(telRef.delete());
});
