const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1001";
const BOB_UID = "bob_uid_2002";
const CHARLIE_UID = "charlie_uid_3003";
const CONV_ID = `${ALICE_UID}_${BOB_UID}`;
const SAMPLE_PUB_KEY_A = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEAlicePublicKeyMaterialForTesting1234567890==";
const SAMPLE_PUB_KEY_B = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEBobPublicKeyMaterialForTesting123456789012==";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

// --- 1. DIRECTORY & PII ISOLATION ---

test("Directory: unauthenticated access rejected; owner can create & update status; other user can list discoverable", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  await assertFails(unauthDb.collection("directory").where("discoverable", "==", true).get());
  await assertFails(unauthDb.collection("directory").doc(ALICE_UID).get());

  // Valid Kotlin create payload
  await assertSucceeds(
    aliceDb.collection("directory").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice Vance",
      handle: "@alice_vance",
      publicKey: SAMPLE_PUB_KEY_A,
      keyFingerprint: "A1B2 C3D4 E5F6 0718",
      presence: "ONLINE",
      statusMessage: "Ready for encrypted chat",
      statusMood: "Available",
      discoverable: true,
      statusUpdatedAt: now,
      lastSeenAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject ghost field (shadow update test)
  await assertFails(
    aliceDb.collection("directory").doc(ALICE_UID).update({
      presence: "AWAY",
      isAdmin: true,
      lastSeenAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Valid partial status update
  await assertSucceeds(
    aliceDb.collection("directory").doc(ALICE_UID).update({
      presence: "BUSY",
      statusMessage: "In an encrypted call",
      statusMood: "In a Call",
      statusUpdatedAt: pastDate(500),
      lastSeenAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Bob cannot overwrite Alice's directory entry
  await assertFails(
    bobDb.collection("directory").doc(ALICE_UID).update({
      presence: "OFFLINE",
      lastSeenAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Bob can query discoverable users, but unfiltered list fails
  await assertSucceeds(bobDb.collection("directory").where("discoverable", "==", true).get());
  await assertFails(bobDb.collection("directory").get());
});

test("PrivateAccount: PII isolated strictly to owner", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("account").set({
      userId: ALICE_UID,
      email: "alice@example.com",
      verifiedPeerIds: [BOB_UID],
      readReceiptsEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("private").doc("account").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("private").get());
});

// --- 2. STATUS UPDATES FEED ---

test("StatusUpdates: author can publish and edit; peers can query PUBLIC statuses", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("status_updates").doc("status_1").set({
      authorId: ALICE_UID,
      authorName: "Alice Vance",
      mood: "In Focus",
      text: "Shipping zero-knowledge builds today",
      visibility: "PUBLIC",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered PUBLIC query succeeds; unfiltered query fails
  await assertSucceeds(bobDb.collection("status_updates").where("visibility", "==", "PUBLIC").get());
  await assertFails(bobDb.collection("status_updates").get());

  // Bob cannot edit or delete Alice's status
  await assertFails(
    bobDb.collection("status_updates").doc("status_1").update({
      text: "Tampered",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(bobDb.collection("status_updates").doc("status_1").delete());

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("status_updates").doc("status_future").set({
      authorId: ALICE_UID,
      authorName: "Alice Vance",
      mood: "Focus",
      text: "Future clock spoof",
      visibility: "PUBLIC",
      createdAt: new Date(Date.now() + 120000),
      updatedAt: new Date(Date.now() + 120000),
    })
  );
});

// --- 3. E2EE CONVERSATIONS, MESSAGES & TYPING ---

test("Conversations & EncryptedMessages: participant isolation, ciphertext immutability, and typing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate();

  // Create conversation
  await assertSucceeds(
    aliceDb.collection("conversations").doc(CONV_ID).set({
      participantIds: [ALICE_UID, BOB_UID],
      peerAId: ALICE_UID,
      peerAName: "Alice Vance",
      peerBId: BOB_UID,
      peerBName: "Bob Stone",
      lastMessageCiphertext: "c2FtcGxlX2NpcGhlcnRleHQ=",
      lastMessageIv: "c2FtcGxlX2l2",
      lastMessageSenderId: ALICE_UID,
      lastMessageSenderPublicKey: SAMPLE_PUB_KEY_A,
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered participantIds query succeeds; unfiltered query fails; Charlie denied
  await assertSucceeds(
    bobDb.collection("conversations").where("participantIds", "array-contains", BOB_UID).get()
  );
  await assertFails(bobDb.collection("conversations").get());
  await assertFails(charlieDb.collection("conversations").doc(CONV_ID).get());

  // Send E2EE message
  await assertSucceeds(
    aliceDb.collection("conversations").doc(CONV_ID).collection("messages").doc("msg_1").set({
      conversationId: CONV_ID,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderPublicKey: SAMPLE_PUB_KEY_A,
      ciphertext: "dGVzdF9lbmNyeXB0ZWRfcGF5bG9hZA==",
      iv: "MTIzNDU2Nzg5MDEy",
      encryptionAlg: "ECDH-P256-HKDF-AES256GCM",
      messageType: "TEXT",
      readBy: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can read and mark readBy, but CANNOT alter ciphertext
  await assertSucceeds(
    bobDb.collection("conversations").doc(CONV_ID).collection("messages")
      .where("participantIds", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("conversations").doc(CONV_ID).collection("messages").doc("msg_1").update({
      readBy: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    bobDb.collection("conversations").doc(CONV_ID).collection("messages").doc("msg_1").update({
      ciphertext: "dGFtcGVyZWQ=",
      updatedAt: pastDate(500),
    })
  );

  // Charlie cannot read, run unfiltered queries, or inject messages into Alice-Bob conversation
  await assertFails(
    charlieDb.collection("conversations").doc(CONV_ID).collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc(CONV_ID).collection("messages").get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc(CONV_ID).collection("messages").doc("msg_2").set({
      conversationId: CONV_ID,
      participantIds: [CHARLIE_UID, BOB_UID],
      senderId: CHARLIE_UID,
      senderPublicKey: SAMPLE_PUB_KEY_A,
      ciphertext: "aW5qZWN0ZWQ=",
      iv: "MTIzNDU2Nzg5MDEy",
      encryptionAlg: "ECDH-P256-HKDF-AES256GCM",
      messageType: "TEXT",
      readBy: [CHARLIE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Typing state: Alice can write her own doc, Bob cannot spoof Alice's typing doc
  await assertSucceeds(
    aliceDb.collection("conversations").doc(CONV_ID).collection("typing").doc(ALICE_UID).set({
      userId: ALICE_UID,
      participantIds: [ALICE_UID, BOB_UID],
      typing: true,
      updatedAt: now,
    })
  );
  await assertFails(
    bobDb.collection("conversations").doc(CONV_ID).collection("typing").doc(ALICE_UID).set({
      userId: ALICE_UID,
      participantIds: [ALICE_UID, BOB_UID],
      typing: false,
      updatedAt: now,
    })
  );
});

// --- 4. LIVE AUDIO/VIDEO CALL SIGNALING & TERMINAL STATE LOCK ---

test("Calls & ICE Candidates: WebRTC signaling flow and terminal state locking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate();

  // Alice initiates VIDEO call
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callerId: ALICE_UID,
      callerName: "Alice Vance",
      calleeId: BOB_UID,
      calleeName: "Bob Stone",
      participantIds: [ALICE_UID, BOB_UID],
      callType: "VIDEO",
      status: "RINGING",
      offerSdp: "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\n",
      e2eeFingerprint: "9F2A-81C0-44B7",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob queries his calls
  await assertSucceeds(
    bobDb.collection("calls").where("participantIds", "array-contains", BOB_UID).get()
  );

  // Charlie cannot read or join the call
  await assertFails(charlieDb.collection("calls").doc("call_1").get());

  // Bob accepts with answerSdp
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "ACCEPTED",
      answerSdp: "v=0\r\no=- 456 2 IN IP4 127.0.0.1\r\n",
      updatedAt: pastDate(500),
    })
  );

  // ICE candidates exchanged
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").collection("candidates").doc("cand_1").set({
      senderId: ALICE_UID,
      participantIds: [ALICE_UID, BOB_UID],
      sdpMid: "0",
      sdpMLineIndex: 0,
      candidate: "candidate:842163049 1 udp 1677729535 192.168.1.2 54321 typ srflx",
      createdAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").collection("candidates")
      .where("participantIds", "array-contains", BOB_UID).get()
  );

  // End call -> terminal state lock prevents any subsequent update
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").update({
      status: "ENDED",
      updatedAt: pastDate(200),
    })
  );
  await assertFails(
    bobDb.collection("calls").doc("call_1").update({
      status: "CONNECTED",
      updatedAt: pastDate(100),
    })
  );
});

// --- 5. WHATSAPP-STYLE LIVE STORIES ---

test("Stories: author creates & deletes; peers view PUBLIC stories and mark viewedBy without altering content", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").set({
      authorId: ALICE_UID,
      authorName: "Alice Vance",
      storyType: "TEXT",
      caption: "Live encrypted story from CipherTalk!",
      gradientId: "EMERALD",
      visibility: "PUBLIC",
      viewedBy: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered PUBLIC query succeeds; unfiltered query fails
  await assertSucceeds(bobDb.collection("stories").where("visibility", "==", "PUBLIC").get());
  await assertFails(bobDb.collection("stories").get());

  // Bob can mark himself in viewedBy
  await assertSucceeds(
    bobDb.collection("stories").doc("story_1").update({
      viewedBy: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(400),
    })
  );

  // Bob cannot tamper with caption or delete Alice's story
  await assertFails(
    bobDb.collection("stories").doc("story_1").update({
      caption: "Tampered story",
      updatedAt: pastDate(300),
    })
  );
  await assertFails(bobDb.collection("stories").doc("story_1").delete());

  // Author Alice can delete her own story
  await assertSucceeds(aliceDb.collection("stories").doc("story_1").delete());
});

// --- 6. GROUPS & PROFILE DP/PRIVACY CONTROLS ---

test("Profile controls & Groups: avatarBase64, discoverable, group member isolation, and ciphertext immutability", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate();

  // Alice updates DP (avatarBase64) and discoverable flag on her profile
  await assertSucceeds(
    aliceDb.collection("directory").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice Vance",
      handle: "@alice_v",
      avatarBase64: "ZmFrZV9qcGVnX2Jhc2U2NA==",
      publicKey: SAMPLE_PUB_KEY_A,
      keyFingerprint: "A1B2 C3D4 E5F6 0718",
      presence: "ONLINE",
      statusMessage: "Controlled profile",
      statusMood: "Available",
      discoverable: false,
      statusUpdatedAt: now,
      lastSeenAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice creates an encrypted group with Bob
  const GROUP_ID = "grp_team_1";
  await assertSucceeds(
    aliceDb.collection("groups").doc(GROUP_ID).set({
      name: "Core Security Team",
      description: "Encrypted group",
      avatarBase64: "Z3JvdXBfaWNvbl9iNjQ=",
      createdBy: ALICE_UID,
      adminIds: [ALICE_UID],
      memberIds: [ALICE_UID, BOB_UID],
      lastMessageCiphertext: "",
      lastMessageIv: "",
      lastMessageSenderId: ALICE_UID,
      lastMessageSenderName: "Alice Vance",
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice writes wrapped group key for Bob
  await assertSucceeds(
    aliceDb.collection("groups").doc(GROUP_ID).collection("memberKeys").doc(BOB_UID).set({
      memberId: BOB_UID,
      participantIds: [ALICE_UID, BOB_UID],
      granterId: ALICE_UID,
      granterPublicKey: SAMPLE_PUB_KEY_A,
      wrappedKeyCiphertext: "d3JhcHBlZF9rZXk=",
      wrappedKeyIv: "MTIzNDU2Nzg5MDEy",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can query his groups & get his own memberKey; Charlie cannot access group or memberKey
  await assertSucceeds(bobDb.collection("groups").where("memberIds", "array-contains", BOB_UID).get());
  await assertSucceeds(bobDb.collection("groups").doc(GROUP_ID).collection("memberKeys").doc(BOB_UID).get());
  await assertFails(charlieDb.collection("groups").doc(GROUP_ID).get());
  await assertFails(charlieDb.collection("groups").doc(GROUP_ID).collection("memberKeys").doc(BOB_UID).get());

  // Alice sends encrypted group message
  await assertSucceeds(
    aliceDb.collection("groups").doc(GROUP_ID).collection("messages").doc("gmsg_1").set({
      groupId: GROUP_ID,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alice Vance",
      ciphertext: "Z3JvdXBfY2lwaGVy",
      iv: "MTIzNDU2Nzg5MDEy",
      encryptionAlg: "GROUP-AES256GCM",
      readBy: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can read & mark readBy, cannot tamper ciphertext; Charlie cannot read or inject
  await assertSucceeds(
    bobDb.collection("groups").doc(GROUP_ID).collection("messages")
      .where("participantIds", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("groups").doc(GROUP_ID).collection("messages").doc("gmsg_1").update({
      readBy: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    bobDb.collection("groups").doc(GROUP_ID).collection("messages").doc("gmsg_1").update({
      ciphertext: "dGFtcGVyZWQ=",
      updatedAt: pastDate(400),
    })
  );
  await assertFails(
    charlieDb.collection("groups").doc(GROUP_ID).collection("messages").doc("gmsg_1").get()
  );
});

// --- 7. RICH MESSAGING (POLL, IMAGE, VIDEO, LOCATION), REACTIONS & VIDEO/MENTION STORIES ---

test("Rich Messages & Stories: poll voting isolation, emoji reactions, VIDEO story, and @mentions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  // Setup conversation
  await assertSucceeds(
    aliceDb.collection("conversations").doc(CONV_ID).set({
      participantIds: [ALICE_UID, BOB_UID],
      peerAId: ALICE_UID,
      peerAName: "Alice Vance",
      peerBId: BOB_UID,
      peerBName: "Bob Stone",
      lastMessageCiphertext: "cHJldmlldw==",
      lastMessageIv: "MTIzNDU2Nzg5MDEy",
      lastMessageSenderId: ALICE_UID,
      lastMessageSenderPublicKey: SAMPLE_PUB_KEY_A,
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice sends a POLL encrypted message
  const msgRefAlice = aliceDb.collection("conversations").doc(CONV_ID).collection("messages").doc("msg_poll_1");
  await assertSucceeds(
    msgRefAlice.set({
      conversationId: CONV_ID,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderPublicKey: SAMPLE_PUB_KEY_A,
      ciphertext: "ZW5jcnlwdGVkX3BvbGw=",
      iv: "MTIzNDU2Nzg5MDEy",
      encryptionAlg: "ECDH-P256-HKDF-AES256GCM",
      messageType: "POLL",
      readBy: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob casts his own vote & emoji reaction
  const msgRefBob = bobDb.collection("conversations").doc(CONV_ID).collection("messages").doc("msg_poll_1");
  await assertSucceeds(
    msgRefBob.update({
      [`pollVotes.${BOB_UID}`]: 1,
      [`reactions.${BOB_UID}`]: "🔥",
      updatedAt: pastDate(500),
    })
  );

  // Alice votes for herself too
  await assertSucceeds(
    msgRefAlice.update({
      [`pollVotes.${ALICE_UID}`]: 0,
      updatedAt: pastDate(400),
    })
  );

  // Bob CANNOT tamper with Alice's vote or reaction
  await assertFails(
    msgRefBob.update({
      [`pollVotes.${ALICE_UID}`]: 1,
      updatedAt: pastDate(300),
    })
  );
  await assertFails(
    msgRefBob.update({
      [`reactions.${ALICE_UID}`]: "😈",
      updatedAt: pastDate(200),
    })
  );

  // Alice publishes a VIDEO story with @mention of Bob
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_video_1").set({
      authorId: ALICE_UID,
      authorName: "Alice Vance",
      storyType: "VIDEO",
      caption: "Check this out @bob!",
      gradientId: "OCEAN",
      imageBase64: "dGh1bWJfYmpwZw==",
      videoBase64: "ZmFrZV9tcDRfY2xpcA==",
      mentionedUserIds: [BOB_UID],
      mentionedHandles: ["@bob"],
      visibility: "PUBLIC",
      viewedBy: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob marks viewedBy; cannot alter videoBase64 or mentionedUserIds
  await assertSucceeds(
    bobDb.collection("stories").doc("story_video_1").update({
      viewedBy: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(300),
    })
  );
  await assertFails(
    bobDb.collection("stories").doc("story_video_1").update({
      mentionedHandles: ["@tampered"],
      updatedAt: pastDate(200),
    })
  );
});

// --- 8. EDIT & DELETE MESSAGES, CHATS & GROUPS ---

test("Edit & Delete Chat/Messages: sender edit, nickname isolation, conversation delete, and group delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate();

  // Setup conversation & 2 messages (one from Alice, one from Bob)
  await assertSucceeds(
    aliceDb.collection("conversations").doc(CONV_ID).set({
      participantIds: [ALICE_UID, BOB_UID],
      peerAId: ALICE_UID,
      peerAName: "Alice Vance",
      peerBId: BOB_UID,
      peerBName: "Bob Stone",
      lastMessageCiphertext: "cHJldmlldw==",
      lastMessageIv: "MTIzNDU2Nzg5MDEy",
      lastMessageSenderId: ALICE_UID,
      lastMessageSenderPublicKey: SAMPLE_PUB_KEY_A,
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  const aliceMsgRef = aliceDb.collection("conversations").doc(CONV_ID).collection("messages").doc("msg_alice_1");
  await assertSucceeds(
    aliceMsgRef.set({
      conversationId: CONV_ID,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderPublicKey: SAMPLE_PUB_KEY_A,
      ciphertext: "b3JpZ2luYWw=",
      iv: "MTIzNDU2Nzg5MDEy",
      encryptionAlg: "ECDH-P256-HKDF-AES256GCM",
      messageType: "TEXT",
      readBy: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    bobDb.collection("conversations").doc(CONV_ID).collection("messages").doc("msg_bob_1").set({
      conversationId: CONV_ID,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderPublicKey: SAMPLE_PUB_KEY_B,
      ciphertext: "Ym9iX21zZw==",
      iv: "MTIzNDU2Nzg5MDEy",
      encryptionAlg: "ECDH-P256-HKDF-AES256GCM",
      messageType: "TEXT",
      readBy: [BOB_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice can edit her own message
  await assertSucceeds(
    aliceMsgRef.update({
      ciphertext: "ZWRpdGVkX2NpcGhlcg==",
      iv: "OTg3NjU0MzIxMDEy",
      messageType: "TEXT",
      edited: true,
      updatedAt: pastDate(400),
    })
  );

  // Bob CANNOT edit Alice's message even with edited: true
  await assertFails(
    bobDb.collection("conversations").doc(CONV_ID).collection("messages").doc("msg_alice_1").update({
      ciphertext: "dGFtcGVyZWRfYnlfYm9i",
      edited: true,
      updatedAt: pastDate(350),
    })
  );

  // Bob CANNOT delete Alice's message while conversation exists
  await assertFails(
    bobDb.collection("conversations").doc(CONV_ID).collection("messages").doc("msg_alice_1").delete()
  );

  // Alice can delete her own message
  await assertSucceeds(aliceMsgRef.delete());

  // Alice can set her own nickname on conversation, but NOT Bob's key
  await assertSucceeds(
    aliceDb.collection("conversations").doc(CONV_ID).update({
      [`nicknames.${ALICE_UID}`]: "Project Partner",
      updatedAt: pastDate(300),
    })
  );
  await assertFails(
    aliceDb.collection("conversations").doc(CONV_ID).update({
      [`nicknames.${BOB_UID}`]: "Spoofed Nickname",
      updatedAt: pastDate(250),
    })
  );

  // Charlie cannot delete Alice-Bob conversation; Alice CAN delete conversation & cascade remaining message
  await assertFails(charlieDb.collection("conversations").doc(CONV_ID).delete());
  await assertSucceeds(aliceDb.collection("conversations").doc(CONV_ID).delete());
  await assertSucceeds(
    aliceDb.collection("conversations").doc(CONV_ID).collection("messages").doc("msg_bob_1").delete()
  );

  // Group edit & delete checks
  const GROUP_ID = "grp_edit_del_1";
  await assertSucceeds(
    aliceDb.collection("groups").doc(GROUP_ID).set({
      name: "Original Group",
      description: "Desc",
      createdBy: ALICE_UID,
      adminIds: [ALICE_UID],
      memberIds: [ALICE_UID, BOB_UID],
      lastMessageCiphertext: "",
      lastMessageIv: "",
      lastMessageSenderId: ALICE_UID,
      lastMessageSenderName: "Alice Vance",
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("groups").doc(GROUP_ID).collection("messages").doc("gmsg_a1").set({
      groupId: GROUP_ID,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alice Vance",
      ciphertext: "Z3JvdXBfb3JpZw==",
      iv: "MTIzNDU2Nzg5MDEy",
      encryptionAlg: "GROUP-AES256GCM",
      messageType: "TEXT",
      readBy: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice edits own group message; Bob cannot edit Alice's group message
  await assertSucceeds(
    aliceDb.collection("groups").doc(GROUP_ID).collection("messages").doc("gmsg_a1").update({
      ciphertext: "Z3JvdXBfZWRpdGVk",
      iv: "OTg3NjU0MzIxMDEy",
      messageType: "TEXT",
      edited: true,
      updatedAt: pastDate(200),
    })
  );
  await assertFails(
    bobDb.collection("groups").doc(GROUP_ID).collection("messages").doc("gmsg_a1").update({
      ciphertext: "dGFtcGVyZWQ=",
      edited: true,
      updatedAt: pastDate(150),
    })
  );

  // Bob cannot delete group; creator Alice CAN delete group & cascade leftover messages
  await assertFails(bobDb.collection("groups").doc(GROUP_ID).delete());
  await assertSucceeds(aliceDb.collection("groups").doc(GROUP_ID).delete());
  await assertSucceeds(
    aliceDb.collection("groups").doc(GROUP_ID).collection("messages").doc("gmsg_a1").delete()
  );
});

// --- 9. TOKER EXTENSIONS: DOCUMENT SHARING, PIN MESSAGE, PRIVACY/CONTACTS, CLOUD BACKUP, CALL DELETE ---

test("ToKer extensions: DOCUMENT messages, pinned messages, privacy/contacts, cloud backup isolation, and call deletion", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate();

  // Directory & PrivateAccount with privacy audience + saved/blocked contacts
  await assertSucceeds(
    aliceDb.collection("directory").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice Vance",
      handle: "@alice_toker",
      publicKey: SAMPLE_PUB_KEY_A,
      keyFingerprint: "A1B2 C3D4 E5F6 0718",
      presence: "ONLINE",
      statusMessage: "On ToKer",
      statusMood: "Available",
      discoverable: true,
      lastSeenVisibility: "CONTACTS",
      photoVisibility: "EVERYONE",
      statusVisibility: "CONTACTS",
      statusUpdatedAt: now,
      lastSeenAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("account").set({
      userId: ALICE_UID,
      email: "alice@example.com",
      verifiedPeerIds: [BOB_UID],
      savedContactIds: [BOB_UID],
      blockedUserIds: [CHARLIE_UID],
      readReceiptsEnabled: false,
      lastSeenVisibility: "CONTACTS",
      photoVisibility: "EVERYONE",
      statusVisibility: "CONTACTS",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cloud backup strictly owner-only
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("backup").set({
      userId: ALICE_UID,
      backupJson: "{\"app\":\"ToKer\",\"version\":1}",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private").doc("backup").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private").doc("backup").set({
      userId: ALICE_UID,
      backupJson: "{\"tampered\":true}",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Conversation with DOCUMENT message & pinnedMessageId
  await assertSucceeds(
    aliceDb.collection("conversations").doc(CONV_ID).set({
      participantIds: [ALICE_UID, BOB_UID],
      peerAId: ALICE_UID,
      peerAName: "Alice Vance",
      peerBId: BOB_UID,
      peerBName: "Bob Stone",
      lastMessageCiphertext: "cHJldmlldw==",
      lastMessageIv: "MTIzNDU2Nzg5MDEy",
      lastMessageSenderId: ALICE_UID,
      lastMessageSenderPublicKey: SAMPLE_PUB_KEY_A,
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc(CONV_ID).collection("messages").doc("msg_doc_1").set({
      conversationId: CONV_ID,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderPublicKey: SAMPLE_PUB_KEY_A,
      ciphertext: "ZW5jcnlwdGVkX2RvY3VtZW50",
      iv: "MTIzNDU2Nzg5MDEy",
      encryptionAlg: "ECDH-P256-HKDF-AES256GCM",
      messageType: "DOCUMENT",
      readBy: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    bobDb.collection("conversations").doc(CONV_ID).update({
      pinnedMessageId: "msg_doc_1",
      pinnedBy: BOB_UID,
      updatedAt: pastDate(300),
    })
  );

  await assertFails(
    charlieDb.collection("conversations").doc(CONV_ID).update({
      pinnedMessageId: "msg_doc_1",
      pinnedBy: CHARLIE_UID,
      updatedAt: pastDate(200),
    })
  );

  // Call history deletion: participants can delete call & candidate; outsider cannot
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_del_1").set({
      callerId: ALICE_UID,
      callerName: "Alice Vance",
      calleeId: BOB_UID,
      calleeName: "Bob Stone",
      participantIds: [ALICE_UID, BOB_UID],
      callType: "AUDIO",
      status: "RINGING",
      offerSdp: "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\n",
      e2eeFingerprint: "9F2A-81C0-44B7",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_del_1").collection("candidates").doc("c_1").set({
      senderId: ALICE_UID,
      participantIds: [ALICE_UID, BOB_UID],
      sdpMid: "0",
      sdpMLineIndex: 0,
      candidate: "candidate:1 1 udp 2122260223 127.0.0.1 50000 typ host",
      createdAt: now,
    })
  );
  await assertFails(charlieDb.collection("calls").doc("call_del_1").delete());
  await assertSucceeds(bobDb.collection("calls").doc("call_del_1").collection("candidates").doc("c_1").delete());
  await assertSucceeds(bobDb.collection("calls").doc("call_del_1").delete());
});

// --- 10. NON-EXISTENT DOCUMENT GET CHECKS (PRE-CREATION CHAT/GROUP OPENING) ---

test("Opening chat or group before creation: get() on non-existent docs succeeds without PERMISSION_DENIED while outsiders remain blocked on existing docs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate();

  // Unauthenticated get must still fail even if doc does not exist
  await assertFails(unauthDb.collection("conversations").doc(CONV_ID).get());
  await assertFails(unauthDb.collection("groups").doc("grp_missing_1").get());

  // Signed-in user checking existence before getOrCreateConversation must succeed
  const missingConvSnap = await assertSucceeds(aliceDb.collection("conversations").doc(CONV_ID).get());
  if (missingConvSnap.exists) {
    throw new Error("Expected conversation to not exist yet");
  }

  // Empty messages and typing queries must succeed for participants
  await assertSucceeds(
    aliceDb.collection("conversations").doc(CONV_ID).collection("messages")
      .where("participantIds", "array-contains", ALICE_UID).get()
  );
  await assertSucceeds(
    aliceDb.collection("conversations").doc(CONV_ID).collection("typing")
      .where("participantIds", "array-contains", ALICE_UID).get()
  );

  // Non-existent group and memberKey check must succeed for Bob before key wrapping completes
  await assertSucceeds(bobDb.collection("groups").doc("grp_pending_1").get());
  await assertSucceeds(
    bobDb.collection("groups").doc("grp_pending_1").collection("memberKeys").doc(BOB_UID).get()
  );

  // Once conversation exists, outsider Charlie is strictly denied get()
  await assertSucceeds(
    aliceDb.collection("conversations").doc(CONV_ID).set({
      participantIds: [ALICE_UID, BOB_UID],
      peerAId: ALICE_UID,
      peerAName: "Alice Vance",
      peerBId: BOB_UID,
      peerBName: "Bob Stone",
      lastMessageCiphertext: "",
      lastMessageIv: "",
      lastMessageSenderId: ALICE_UID,
      lastMessageSenderPublicKey: SAMPLE_PUB_KEY_A,
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(bobDb.collection("conversations").doc(CONV_ID).get());
  await assertFails(charlieDb.collection("conversations").doc(CONV_ID).get());
});

// --- 11. VOICE MESSAGES, MEMBER JOIN TIMESTAMPS, AND MEMBER REMOVAL ---

test("Voice messages, group memberJoinedAt tracking, and group member removal by creator", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate();

  // Direct VOICE message succeeds for participants
  await assertSucceeds(
    aliceDb.collection("conversations").doc(CONV_ID).set({
      participantIds: [ALICE_UID, BOB_UID],
      peerAId: ALICE_UID,
      peerAName: "Alice Vance",
      peerBId: BOB_UID,
      peerBName: "Bob Stone",
      lastMessageCiphertext: "dm9pY2U=",
      lastMessageIv: "MTIzNDU2Nzg5MDEy",
      lastMessageSenderId: ALICE_UID,
      lastMessageSenderPublicKey: SAMPLE_PUB_KEY_A,
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("conversations").doc(CONV_ID).collection("messages").doc("msg_voice_1").set({
      conversationId: CONV_ID,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderPublicKey: SAMPLE_PUB_KEY_A,
      ciphertext: "ZW5jcnlwdGVkX3ZvaWNl",
      iv: "MTIzNDU2Nzg5MDEy",
      encryptionAlg: "ECDH-P256-HKDF-AES256GCM",
      messageType: "VOICE",
      readBy: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Group with memberJoinedAt & VOICE message, then creator removes Charlie and deletes Charlie's key
  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_voice_1").set({
      name: "Voice Group",
      description: "Team chat",
      createdBy: ALICE_UID,
      adminIds: [ALICE_UID],
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      memberJoinedAt: { [ALICE_UID]: 1000, [BOB_UID]: 1000, [CHARLIE_UID]: 2000 },
      lastMessageCiphertext: "Z3JvdXBfdm9pY2U=",
      lastMessageIv: "MTIzNDU2Nzg5MDEy",
      lastMessageSenderId: ALICE_UID,
      lastMessageSenderName: "Alice Vance",
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_voice_1").collection("memberKeys").doc(CHARLIE_UID).set({
      memberId: CHARLIE_UID,
      participantIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      granterId: ALICE_UID,
      granterPublicKey: SAMPLE_PUB_KEY_A,
      wrappedKeyCiphertext: "d3JhcHBlZF9rZXk=",
      wrappedKeyIv: "MTIzNDU2Nzg5MDEy",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_voice_1").collection("messages").doc("gmsg_voice_1").set({
      groupId: "grp_voice_1",
      participantIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      senderId: ALICE_UID,
      senderName: "Alice Vance",
      ciphertext: "ZW5jcnlwdGVkX2dyb3VwX3ZvaWNl",
      iv: "MTIzNDU2Nzg5MDEy",
      encryptionAlg: "GROUP-AES256GCM",
      messageType: "VOICE",
      readBy: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Creator Alice removes Charlie from memberIds & memberJoinedAt and deletes Charlie's memberKey
  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_voice_1").update({
      memberIds: [ALICE_UID, BOB_UID],
      adminIds: [ALICE_UID],
      memberJoinedAt: { [ALICE_UID]: 1000, [BOB_UID]: 1000 },
      updatedAt: pastDate(300),
    })
  );
  await assertFails(
    bobDb.collection("groups").doc("grp_voice_1").collection("memberKeys").doc(CHARLIE_UID).delete()
  );
  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_voice_1").collection("memberKeys").doc(CHARLIE_UID).delete()
  );
  await assertFails(
    charlieDb.collection("groups").doc("grp_voice_1").get()
  );
});

// --- 12. GROUP ADMIN CONTROLS (PROMOTE/DEMOTE, SETTINGS EDIT, MESSAGE MODERATION, MEMBER REMOVAL) ---

test("Group Admin Controls: non-admin denied admin mutations; promoted admin can edit group, moderate messages & remove members", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_admin_1").set({
      name: "Admin Test Group",
      description: "Initial",
      createdBy: ALICE_UID,
      adminIds: [ALICE_UID],
      memberIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      memberJoinedAt: { [ALICE_UID]: 1000, [BOB_UID]: 1000, [CHARLIE_UID]: 1000 },
      lastMessageCiphertext: "",
      lastMessageIv: "",
      lastMessageSenderId: ALICE_UID,
      lastMessageSenderName: "Alice Vance",
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_admin_1").collection("memberKeys").doc(CHARLIE_UID).set({
      memberId: CHARLIE_UID,
      participantIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      granterId: ALICE_UID,
      granterPublicKey: SAMPLE_PUB_KEY_A,
      wrappedKeyCiphertext: "d3JhcHBlZF9rZXk=",
      wrappedKeyIv: "MTIzNDU2Nzg5MDEy",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    charlieDb.collection("groups").doc("grp_admin_1").collection("messages").doc("gmsg_c1").set({
      groupId: "grp_admin_1",
      participantIds: [ALICE_UID, BOB_UID, CHARLIE_UID],
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      ciphertext: "Y2hhcmxpZV9tc2c=",
      iv: "MTIzNDU2Nzg5MDEy",
      encryptionAlg: "GROUP-AES256GCM",
      messageType: "TEXT",
      readBy: [CHARLIE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Regular member Bob cannot promote himself, rename group, or delete Charlie's message
  await assertFails(
    bobDb.collection("groups").doc("grp_admin_1").update({
      adminIds: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    bobDb.collection("groups").doc("grp_admin_1").update({
      name: "Hijacked Group",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    bobDb.collection("groups").doc("grp_admin_1").collection("messages").doc("gmsg_c1").delete()
  );

  // Creator Alice promotes Bob to Admin
  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_admin_1").update({
      adminIds: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(400),
    })
  );

  // Now Admin Bob CAN update group name, moderate Charlie's message, and remove Charlie + Charlie's key
  await assertSucceeds(
    bobDb.collection("groups").doc("grp_admin_1").update({
      name: "Updated By Admin Bob",
      description: "Moderated group",
      updatedAt: pastDate(300),
    })
  );
  await assertSucceeds(
    bobDb.collection("groups").doc("grp_admin_1").collection("messages").doc("gmsg_c1").delete()
  );
  await assertSucceeds(
    bobDb.collection("groups").doc("grp_admin_1").update({
      memberIds: [ALICE_UID, BOB_UID],
      adminIds: [ALICE_UID, BOB_UID],
      memberJoinedAt: { [ALICE_UID]: 1000, [BOB_UID]: 1000 },
      updatedAt: pastDate(200),
    })
  );
  await assertSucceeds(
    bobDb.collection("groups").doc("grp_admin_1").collection("memberKeys").doc(CHARLIE_UID).delete()
  );
});

// --- 13. ADMIN-ONLY GROUP MESSAGING, STICKER/GIF TYPES & DISAPPEARING MESSAGES ---

test("Admin-only group permission blocks regular members, allows admins, and validates disappearingSeconds + STICKER/GIF", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("conversations").doc(CONV_ID).set({
      participantIds: [ALICE_UID, BOB_UID],
      peerAId: ALICE_UID,
      peerAName: "Alice Vance",
      peerBId: BOB_UID,
      peerBName: "Bob",
      disappearingSeconds: 3600,
      lastMessageCiphertext: "",
      lastMessageIv: "",
      lastMessageSenderId: ALICE_UID,
      lastMessageSenderPublicKey: SAMPLE_PUB_KEY_A,
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc(CONV_ID).collection("messages").doc("msg_sticker_1").set({
      conversationId: CONV_ID,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderPublicKey: SAMPLE_PUB_KEY_A,
      ciphertext: "c3RpY2tlcl9wYXlsb2Fk",
      iv: "MTIzNDU2Nzg5MDEy",
      encryptionAlg: "ECDH-P256-HKDF-AES256GCM",
      messageType: "STICKER",
      readBy: [ALICE_UID],
      expiresAtMillis: Date.now() + 3600000,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_perm_1").set({
      name: "Locked Broadcast Group",
      description: "Admins announce only",
      createdBy: ALICE_UID,
      adminIds: [ALICE_UID],
      memberIds: [ALICE_UID, BOB_UID],
      onlyAdminsCanMessage: true,
      disappearingSeconds: 86400,
      lastMessageCiphertext: "",
      lastMessageIv: "",
      lastMessageSenderId: ALICE_UID,
      lastMessageSenderName: "Alice Vance",
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Regular member Bob cannot toggle onlyAdminsCanMessage or post a message while enabled
  await assertFails(
    bobDb.collection("groups").doc("grp_perm_1").update({
      onlyAdminsCanMessage: false,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    bobDb.collection("groups").doc("grp_perm_1").collection("messages").doc("gmsg_bob_blocked").set({
      groupId: "grp_perm_1",
      participantIds: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Bob",
      ciphertext: "YmxvY2tlZF9tc2c=",
      iv: "MTIzNDU2Nzg5MDEy",
      encryptionAlg: "GROUP-AES256GCM",
      messageType: "TEXT",
      readBy: [BOB_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin Alice CAN send GIF message with expiresAtMillis
  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_perm_1").collection("messages").doc("gmsg_admin_gif").set({
      groupId: "grp_perm_1",
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alice Vance",
      ciphertext: "Z2lmX21lc3NhZ2U=",
      iv: "MTIzNDU2Nzg5MDEy",
      encryptionAlg: "GROUP-AES256GCM",
      messageType: "GIF",
      readBy: [ALICE_UID],
      expiresAtMillis: Date.now() + 86400000,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin Alice unlocks group so Bob can message
  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_perm_1").update({
      onlyAdminsCanMessage: false,
      updatedAt: pastDate(300),
    })
  );
  await assertSucceeds(
    bobDb.collection("groups").doc("grp_perm_1").collection("messages").doc("gmsg_bob_ok").set({
      groupId: "grp_perm_1",
      participantIds: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Bob",
      ciphertext: "dW5sb2NrZWRfbXNn",
      iv: "MTIzNDU2Nzg5MDEy",
      encryptionAlg: "GROUP-AES256GCM",
      messageType: "TEXT",
      readBy: [BOB_UID],
      createdAt: now,
      updatedAt: now,
    })
  );
});





