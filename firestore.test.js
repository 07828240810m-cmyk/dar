const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const REGULAR_UID = "teacher_user_1";
const OTHER_UID = "teacher_user_2";
const BOOTSTRAP_ADMIN_UID = "bootstrap_admin_uid";
const BOOTSTRAP_ADMIN_EMAIL = "07828240810m@gmail.com";
const DELEGATED_ADMIN_UID = "delegated_admin_uid";

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

test("Unauthenticated user: denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("app_config").doc("latest_release").get());
  await assertFails(unauthDb.collection("admin_notifications").where("active", "==", true).get());
  await assertFails(unauthDb.collection("admins").doc(REGULAR_UID).get());
  await assertFails(unauthDb.collection("users").doc(REGULAR_UID).collection("notification_states").get());
});

test("Email spoofing & privilege escalation rejected on admins and admin_notifications", async () => {
  const spoofedDb = testEnv.authenticatedContext("spoof_uid", {
    email: BOOTSTRAP_ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  const past = new Date(Date.now() - 2000);
  await assertFails(
    spoofedDb.collection("admins").doc("spoof_uid").set({
      userId: "spoof_uid",
      email: BOOTSTRAP_ADMIN_EMAIL,
      isAdmin: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  const regularDb = testEnv.authenticatedContext(REGULAR_UID, {
    email: "user@example.com",
    email_verified: true,
  }).firestore();

  await assertFails(
    regularDb.collection("admins").doc(REGULAR_UID).set({
      userId: REGULAR_UID,
      email: "user@example.com",
      isAdmin: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertFails(
    regularDb.collection("admin_notifications").doc("notif_1").set({
      title: "Fake Admin Notice",
      body: "Should fail",
      priority: "URGENT",
      active: true,
      authorUid: REGULAR_UID,
      sentAt: past,
      updatedAt: past,
    })
  );

  await assertFails(
    regularDb.collection("app_config").doc("latest_release").set({
      latest_version_code: 2,
      latest_version_name: "2.0",
      release_notes: "Hijack",
      force_update: true,
      apk_url: "https://example.com/app.apk",
      updated_by: REGULAR_UID,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Bootstrapped Admin: can manage admins, app_config/latest_release, and admin_notifications", async () => {
  const adminDb = testEnv.authenticatedContext(BOOTSTRAP_ADMIN_UID, {
    email: BOOTSTRAP_ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    adminDb.collection("admins").doc(DELEGATED_ADMIN_UID).set({
      userId: DELEGATED_ADMIN_UID,
      email: "delegate@example.com",
      isAdmin: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    adminDb.collection("app_config").doc("latest_release").set({
      latest_version_code: 2,
      latest_version_name: "2.0",
      release_notes: "تحديث جديد يتضمن تحسينات عامة",
      force_update: false,
      apk_url: "https://example.com/releases/app-v2.apk",
      updated_by: BOOTSTRAP_ADMIN_UID,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    adminDb.collection("admin_notifications").doc("notif_1").set({
      title: "إعلان مهم للفيزياء الثالث متوسط",
      body: "يرجى مراجعة الأسئلة الجديدة",
      priority: "IMPORTANT",
      active: true,
      authorUid: BOOTSTRAP_ADMIN_UID,
      authorName: "إدارة التطبيق",
      sentAt: past,
      updatedAt: past,
    })
  );

  // Partial update on active status preserving sentAt
  await assertSucceeds(
    adminDb.collection("admin_notifications").doc("notif_1").update({
      active: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ghost field & future timestamp rejected
  await assertFails(
    adminDb.collection("admin_notifications").doc("notif_bad").set({
      title: "Bad",
      body: "Body",
      priority: "NORMAL",
      active: true,
      authorUid: BOOTSTRAP_ADMIN_UID,
      ghostField: "hack",
      sentAt: past,
      updatedAt: past,
    })
  );
  await assertFails(
    adminDb.collection("admin_notifications").doc("notif_future").set({
      title: "Future",
      body: "Body",
      priority: "NORMAL",
      active: true,
      authorUid: BOOTSTRAP_ADMIN_UID,
      sentAt: new Date(Date.now() + 60000),
      updatedAt: past,
    })
  );
});

test("Regular signed-in user: filtered active notifications list & app_config get succeed; unfiltered list & inactive read fail", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const past = new Date(Date.now() - 10000);
    await db.collection("app_config").doc("latest_release").set({
      latest_version_code: 2,
      latest_version_name: "2.0",
      release_notes: "ملاحظات",
      force_update: false,
      apk_url: "https://example.com/app.apk",
      updated_by: BOOTSTRAP_ADMIN_UID,
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("admin_notifications").doc("active_1").set({
      title: "نشط",
      body: "محتوى",
      priority: "NORMAL",
      active: true,
      authorUid: BOOTSTRAP_ADMIN_UID,
      sentAt: past,
      updatedAt: past,
    });
    await db.collection("admin_notifications").doc("hidden_1").set({
      title: "معطل",
      body: "محتوى مخفي",
      priority: "NORMAL",
      active: false,
      authorUid: BOOTSTRAP_ADMIN_UID,
      sentAt: past,
      updatedAt: past,
    });
  });

  const userDb = testEnv.authenticatedContext(REGULAR_UID).firestore();
  await assertSucceeds(userDb.collection("app_config").doc("latest_release").get());
  await assertSucceeds(userDb.collection("admin_notifications").where("active", "==", true).get());
  await assertFails(userDb.collection("admin_notifications").get());
  await assertFails(userDb.collection("admin_notifications").doc("hidden_1").get());
});

test("User notification_states: strict per-user read/write isolation", async () => {
  const userDb = testEnv.authenticatedContext(REGULAR_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const past = new Date(Date.now() - 1000);

  await assertSucceeds(
    userDb.collection("users").doc(REGULAR_UID).collection("notification_states").doc("active_1").set({
      notificationId: "active_1",
      read: true,
      readAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    userDb.collection("users").doc(REGULAR_UID).collection("notification_states").get()
  );

  // Cross-user access denied
  await assertFails(
    otherDb.collection("users").doc(REGULAR_UID).collection("notification_states").get()
  );
  await assertFails(
    otherDb.collection("users").doc(REGULAR_UID).collection("notification_states").doc("active_1").set({
      notificationId: "active_1",
      read: true,
      readAt: past,
      updatedAt: past,
    })
  );
});

test("Remote control, promo banner, polls, and gifts: admin writes vs regular user reads & voting", async () => {
  const adminDb = testEnv.authenticatedContext(BOOTSTRAP_ADMIN_UID, {
    email: BOOTSTRAP_ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const userDb = testEnv.authenticatedContext(REGULAR_UID, {
    email: "t1@example.com",
    email_verified: true,
  }).firestore();
  const past = new Date(Date.now() - 2000);

  // Regular user cannot write remote_control or promo_banner
  await assertFails(
    userDb.collection("app_config").doc("remote_control").set({
      maintenanceEnabled: true,
      maintenanceMessage: "Hacked",
      disabledTools: [],
      pointsRequiredTools: {},
      seasonalTheme: "NONE",
      defaultAccessMode: "OPEN",
      defaultTrialDays: 7,
      updatedBy: REGULAR_UID,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Admin sets remote_control and promo_banner
  await assertSucceeds(
    adminDb.collection("app_config").doc("remote_control").set({
      maintenanceEnabled: false,
      maintenanceMessage: "الصيانة جارية، نعتذر منكم",
      disabledTools: ["EXAM_ARCHIVE"],
      pointsRequiredTools: { QUESTION_BANK: 15 },
      seasonalTheme: "RAMADAN",
      defaultAccessMode: "OPEN",
      defaultTrialDays: 7,
      updatedBy: BOOTSTRAP_ADMIN_UID,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    adminDb.collection("app_config").doc("promo_banner").set({
      enabled: true,
      title: "منصة التفوق",
      description: "ملخصات مجانية للفيزياء",
      imageUrl: "https://example.com/banner.png",
      actionUrl: "https://example.com",
      phoneNumber: "07800000000",
      buttonLabel: "زيارة",
      updatedBy: BOOTSTRAP_ADMIN_UID,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(userDb.collection("app_config").doc("remote_control").get());
  await assertSucceeds(userDb.collection("app_config").doc("promo_banner").get());

  // Poll create by admin & vote by user
  await assertSucceeds(
    adminDb.collection("polls").doc("poll_1").set({
      question: "ما رأيكم بالتحديث؟",
      options: ["ممتاز", "جيد"],
      targetType: "ALL",
      targetValue: "",
      active: true,
      authorUid: BOOTSTRAP_ADMIN_UID,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(userDb.collection("polls").where("active", "==", true).get());
  await assertSucceeds(
    userDb.collection("polls").doc("poll_1").collection("votes").doc(REGULAR_UID).set({
      pollId: "poll_1",
      voterUid: REGULAR_UID,
      optionIndex: 0,
      votedAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    userDb.collection("users").doc(REGULAR_UID).collection("poll_votes").doc("poll_1").set({
      pollId: "poll_1",
      optionIndex: 0,
      votedAt: past,
    })
  );
});

test("Teacher accounts, encrypted backups, referrals, feedback, and parent reports isolation", async () => {
  const userDb = testEnv.authenticatedContext(REGULAR_UID, {
    email: "t1@example.com",
    email_verified: true,
  }).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID, {
    email: "t2@example.com",
    email_verified: true,
  }).firestore();
  const adminDb = testEnv.authenticatedContext(BOOTSTRAP_ADMIN_UID, {
    email: BOOTSTRAP_ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const past = new Date(Date.now() - 2000);

  await assertSucceeds(
    userDb.collection("teacher_accounts").doc("acct_1").set({
      accountKey: "acct_1",
      usernameDisplay: "mustafa_teacher",
      ownerUid: REGULAR_UID,
      passwordHash: "hash_abc",
      passwordSalt: "salt_abc",
      teacherName: "أ. مصطفى",
      specialtyId: "physics",
      gradeId: "third",
      referralCode: "RF1234AB",
      referredByCode: "",
      points: 10,
      referralCount: 0,
      claimedReferralIds: [],
      accessMode: "TRIAL",
      trialDays: 7,
      trialExpiresAtMillis: Date.now() + 604800000,
      todayDateKey: "2026-11-23",
      todayUsageSeconds: 120,
      totalUsageSeconds: 120,
      toolUsageCounts: { DAILY_LEDGER: 2 },
      ratingStars: 5,
      ratingComment: "رائع",
      lastActiveAt: past,
      createdAt: past,
      updatedAt: past,
    })
  );

  // User cannot escalate own trial -> OPEN!
  await assertFails(
    userDb.collection("teacher_accounts").doc("acct_1").update({
      accessMode: "OPEN",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // Admin CAN switch user to OPEN
  await assertSucceeds(
    adminDb.collection("teacher_accounts").doc("acct_1").update({
      accessMode: "OPEN",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Non-owner cannot list all accounts; can query own; Admin can list all
  await assertFails(otherDb.collection("teacher_accounts").get());
  await assertSucceeds(userDb.collection("teacher_accounts").where("ownerUid", "==", REGULAR_UID).get());
  await assertSucceeds(adminDb.collection("teacher_accounts").get());

  // Encrypted backup upload + cross-device encrypted fetch
  await assertSucceeds(
    userDb.collection("teacher_accounts").doc("acct_1").collection("backups").doc("latest").set({
      encryptedBlob: "ZW5jcnlwdGVkX2Jsb2I=",
      checksumSha256: "abcdef0123456789abcdef0123456789",
      ownerUid: REGULAR_UID,
      studentCount: 25,
      appVersionCode: 1,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    otherDb.collection("teacher_accounts").doc("acct_1").collection("backups").doc("latest").get()
  );
  // Other UID cannot overwrite acct_1's backup
  await assertFails(
    otherDb.collection("teacher_accounts").doc("acct_1").collection("backups").doc("latest").set({
      encryptedBlob: "tamper",
      checksumSha256: "abcdef0123456789abcdef0123456789",
      ownerUid: OTHER_UID,
      studentCount: 1,
      appVersionCode: 1,
      updatedAt: past,
    })
  );

  // Developer feedback + parent report
  await assertSucceeds(
    userDb.collection("developer_feedback").doc("fb_1").set({
      senderUid: REGULAR_UID,
      senderName: "أ. مصطفى",
      accountKey: "acct_1",
      category: "COMPLAINT",
      message: "اقتراح مفصل جداً للتطوير",
      wordCount: 4,
      lockedThreeDays: false,
      lockedUntilMillis: 0,
      status: "OPEN",
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    userDb.collection("parent_reports").doc("PR9988AA").set({
      reportCode: "PR9988AA",
      teacherUid: REGULAR_UID,
      teacherName: "أ. مصطفى",
      subjectName: "الفيزياء",
      studentName: "أحمد علي",
      gradeTitle: "الثالث المتوسط",
      sectionId: "أ",
      expiresAtMillis: Date.now() + 259200000,
      deletedByAdmin: false,
      visibilityJson: "{\"t1Avg\":true}",
      reportDataJson: "{\"t1Avg\":88}",
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(otherDb.collection("parent_reports").doc("PR9988AA").get());
  await assertSucceeds(adminDb.collection("parent_reports").doc("PR9988AA").delete());
});

test("Shared PDF study guides: strictly PDF, chunked upload, download count, and delete authorization", async () => {
  const userDb = testEnv.authenticatedContext(REGULAR_UID, {
    email: "t1@example.com",
    email_verified: true,
  }).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID, {
    email: "t2@example.com",
    email_verified: true,
  }).firestore();
  const adminDb = testEnv.authenticatedContext(BOOTSTRAP_ADMIN_UID, {
    email: BOOTSTRAP_ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const past = new Date(Date.now() - 2000);

  // Admin can publish internal update + experimental layout in remote_control
  await assertSucceeds(
    adminDb.collection("app_config").doc("remote_control").set({
      maintenanceEnabled: false,
      maintenanceMessage: "",
      disabledTools: [],
      pointsRequiredTools: {},
      seasonalTheme: "NONE",
      defaultAccessMode: "OPEN",
      defaultTrialDays: 7,
      internalUpdateEnabled: true,
      internalUpdateRevision: 101,
      internalUpdateTitle: "تحديث قوائم المدرسة",
      internalUpdateText: "تحديث داخلي جديد",
      internalUpdateDurationSec: 13,
      internalUpdateIncludeSchoolRoster: true,
      internalUpdateCustomRosterText: "",
      experimentalLayoutEnabled: true,
      experimentalLayoutMode: "MODERN_SPLIT",
      updatedBy: BOOTSTRAP_ADMIN_UID,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Non-PDF mimeType must be rejected
  await assertFails(
    userDb.collection("study_guides").doc("guide_bad").set({
      title: "ملف صور",
      subjectTitle: "الفيزياء",
      gradeId: "third",
      uploaderUid: REGULAR_UID,
      teacherName: "أ. مصطفى",
      description: "اختبار",
      fileName: "notes.docx",
      mimeType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      fileSizeBytes: 1024,
      chunkCount: 1,
      downloadCount: 0,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Valid PDF guide + chunk upload succeeds
  await assertSucceeds(
    userDb.collection("study_guides").doc("guide_1").set({
      title: "ملزمة الفيزياء الفصل الأول",
      subjectTitle: "الفيزياء",
      gradeId: "third",
      uploaderUid: REGULAR_UID,
      teacherName: "أ. مصطفى",
      description: "ملخص شامل للثالث المتوسط",
      fileName: "physics_ch1.pdf",
      mimeType: "application/pdf",
      fileSizeBytes: 2048,
      chunkCount: 1,
      downloadCount: 0,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    userDb.collection("study_guides").doc("guide_1").collection("chunks").doc("0").set({
      uploaderUid: REGULAR_UID,
      chunkIndex: 0,
      dataBase64: "JVBERi0xLjQ=",
      createdAt: past,
    })
  );

  // Any signed-in teacher can browse & download and increment downloadCount by 1
  await assertSucceeds(otherDb.collection("study_guides").get());
  await assertSucceeds(otherDb.collection("study_guides").doc("guide_1").collection("chunks").get());
  await assertSucceeds(
    otherDb.collection("study_guides").doc("guide_1").update({
      downloadCount: 1,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Another non-admin teacher cannot delete guide_1
  await assertFails(otherDb.collection("study_guides").doc("guide_1").delete());
  // Uploader can delete guide_1
  await assertSucceeds(userDb.collection("study_guides").doc("guide_1").delete());
});


