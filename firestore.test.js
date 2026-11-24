const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const CHARLIE_UID = "charlie_uid_789";

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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("public_profiles").where("discoverable", "==", true).get());
  await assertFails(unauthDb.collection("posts").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("conversations").where("participantIds", "array-contains", ALICE_UID).get());
  await assertFails(
    unauthDb.collection("posts").doc("post_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "@alice",
      authorAvatarIndex: 0,
      content: "Hello",
      category: "Discussion",
      likesCount: 0,
      commentsCount: 0,
      visibility: "public",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Private user PII isolation: owner can create/read/update, others denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      handle: "@alice",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("users").get());
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Public profiles: discoverable query succeeds, unfiltered query fails, cross-user update fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "@alice",
      bio: "Building communities on Nirax",
      statusNote: "Available to chat",
      avatarIndex: 1,
      online: true,
      discoverable: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered discovery query matches Kotlin repository
  await assertSucceeds(
    bobDb.collection("public_profiles").where("discoverable", "==", true).get()
  );

  // Unfiltered query must fail
  await assertFails(bobDb.collection("public_profiles").get());

  // Cross-user write or ghost field rejected
  await assertFails(
    bobDb.collection("public_profiles").doc(ALICE_UID).update({
      bio: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("public_profiles").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Posts, likes batch atomicity, comments, and query alignment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Reject post with extra @DocumentId 'id' field
  await assertFails(
    aliceDb.collection("posts").doc("post_bad").set({
      id: "post_bad",
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "@alice",
      authorAvatarIndex: 0,
      content: "Bad payload",
      category: "Discussion",
      likesCount: 0,
      commentsCount: 0,
      visibility: "public",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid Kotlin create payload
  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "@alice",
      authorAvatarIndex: 0,
      content: "Welcome to Nirax!",
      category: "Discussion",
      likesCount: 0,
      commentsCount: 0,
      visibility: "public",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Query alignment
  await assertSucceeds(bobDb.collection("posts").where("visibility", "==", "public").get());
  await assertFails(bobDb.collection("posts").get());

  // Atomic like by Bob (batch: liked_posts create + likesCount increment)
  const likeBatch = bobDb.batch();
  likeBatch.set(
    bobDb.collection("users").doc(BOB_UID).collection("liked_posts").doc("post_1"),
    { userId: BOB_UID, postId: "post_1", createdAt: new Date(Date.now() - 1000) }
  );
  likeBatch.update(bobDb.collection("posts").doc("post_1"), {
    likesCount: 1,
    updatedAt: new Date(Date.now() - 1000),
  });
  await assertSucceeds(likeBatch.commit());

  // Non-atomic likesCount tamper without liked_posts doc must fail
  await assertFails(
    bobDb.collection("posts").doc("post_1").update({
      likesCount: 99,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Atomic comment by Bob
  const commentBatch = bobDb.batch();
  commentBatch.set(
    bobDb.collection("posts").doc("post_1").collection("comments").doc("c_1"),
    {
      postId: "post_1",
      authorId: BOB_UID,
      authorName: "Bob",
      authorHandle: "@bob",
      authorAvatarIndex: 2,
      text: "Great to be here!",
      createdAt: new Date(Date.now() - 800),
    }
  );
  commentBatch.update(bobDb.collection("posts").doc("post_1"), {
    commentsCount: 1,
    updatedAt: new Date(Date.now() - 800),
  });
  await assertSucceeds(commentBatch.commit());

  // Filtered comment query succeeds; unfiltered fails
  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").collection("comments").where("postId", "==", "post_1").get()
  );
  await assertFails(
    aliceDb.collection("posts").doc("post_1").collection("comments").get()
  );

  // Bob cannot delete Alice's post
  await assertFails(bobDb.collection("posts").doc("post_1").delete());
  // Alice can delete her own post
  await assertSucceeds(aliceDb.collection("posts").doc("post_1").delete());
});

test("Direct messaging: participants can chat, outsiders denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const convId = `${ALICE_UID}_${BOB_UID}`;
  const participants = [ALICE_UID, BOB_UID];
  const now = new Date(Date.now() - 3000);

  // Atomic conversation creation + first message
  const startBatch = aliceDb.batch();
  startBatch.set(aliceDb.collection("conversations").doc(convId), {
    participantIds: participants,
    participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
    participantHandles: { [ALICE_UID]: "@alice", [BOB_UID]: "@bob" },
    participantAvatars: { [ALICE_UID]: 0, [BOB_UID]: 2 },
    lastMessageText: "Hey Bob!",
    lastMessageSenderId: ALICE_UID,
    lastMessageAt: now,
    createdAt: now,
    updatedAt: now,
  });
  startBatch.set(aliceDb.collection("conversations").doc(convId).collection("messages").doc("m_1"), {
    conversationId: convId,
    senderId: ALICE_UID,
    senderName: "Alice",
    participantIds: participants,
    text: "Hey Bob!",
    createdAt: now,
  });
  await assertSucceeds(startBatch.commit());

  // Bob queries conversations & messages using participantIds filter
  await assertSucceeds(
    bobDb.collection("conversations").where("participantIds", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages")
      .where("participantIds", "array-contains", BOB_UID).get()
  );

  // Charlie (outsider) cannot read conversation, direct message, unfiltered list, or inject message
  await assertFails(charlieDb.collection("conversations").doc(convId).get());
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages").doc("m_1").get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages").get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages").doc("m_hack").set({
      conversationId: convId,
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      participantIds: [ALICE_UID, CHARLIE_UID],
      text: "Intrusion",
      createdAt: new Date(Date.now() - 500),
    })
  );
});

test("Following subcollection: owner can follow/unfollow, others denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("following").doc(BOB_UID).set({
      targetUid: BOB_UID,
      targetName: "Bob",
      targetHandle: "@bob",
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("following").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("following").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("following").doc(BOB_UID).delete()
  );
});
