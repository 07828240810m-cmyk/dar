package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import com.example.BuildConfig
import com.example.R
import com.example.utils.BackupCryptoHelper
import com.example.utils.OperationType
import com.example.utils.PdfValidationHelper
import com.example.utils.handleFirestoreError
import com.example.utils.unwrapFirestoreException
import java.io.ByteArrayOutputStream
import java.io.File
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class FirebaseCloudPlatformRepository(
    private val db: FirebaseFirestore,
    private val prefs: SharedPreferences,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        auth = Firebase.auth
    )

    companion object {
        private const val PREFS_NAME = "rafiq_cloud_platform_prefs"
        private const val KEY_HIGHEST_VERIFIED_NOW = "highest_verified_now_ms"
        private const val KEY_ANCHOR_SERVER_MS = "anchor_server_ms"
        private const val KEY_ANCHOR_ELAPSED_MS = "anchor_elapsed_ms"
        private const val KEY_LAST_ACCOUNT_KEY = "last_account_key"
        private const val KEY_CACHED_REMOTE_JSON_UNUSED = "unused"
        private const val KEY_DISMISSED_PROMO_TITLE = "dismissed_promo_title"
        private const val KEY_LOCAL_TODAY_DATE = "local_usage_date_key"
        private const val KEY_LOCAL_TODAY_SECONDS = "local_usage_today_seconds"
        private const val KEY_LOCAL_TOTAL_SECONDS = "local_usage_total_seconds"
        private const val KEY_LOCAL_TOOL_COUNTS = "local_tool_counts_json"
        private const val KEY_UNLOCKED_TOOLS_CSV = "local_unlocked_tools_csv"

        const val COL_APP_CONFIG = "app_config"
        const val DOC_REMOTE_CONTROL = "remote_control"
        const val DOC_PROMO_BANNER = "promo_banner"
        const val COL_POLLS = "polls"
        const val SUBCOL_VOTES = "votes"
        const val COL_TEACHER_ACCOUNTS = "teacher_accounts"
        const val SUBCOL_BACKUPS = "backups"
        const val DOC_LATEST_BACKUP = "latest"
        const val COL_REFERRAL_CODES = "referral_codes"
        const val COL_REFERRAL_CLAIMS = "referral_claims"
        const val COL_ADMIN_GIFTS = "admin_gifts"
        const val COL_FEEDBACK = "developer_feedback"
        const val COL_PARENT_REPORTS = "parent_reports"
        const val COL_USERS = "users"
        const val SUBCOL_POLL_VOTES = "poll_votes"
        const val SUBCOL_CLAIMED_GIFTS = "claimed_gifts"
        const val SUBCOL_TIME_SYNC = "time_sync"
        const val DOC_TIME_PROBE = "probe"
        const val COL_STUDY_GUIDES = "study_guides"
        const val SUBCOL_CHUNKS = "chunks"

        const val THREE_DAYS_MILLIS = 3L * 24L * 60L * 60L * 1000L
        const val REFERRAL_BONUS_POINTS = 50
    }

    private fun requireUid(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يرجى تسجيل الدخول بحساب Google أولاً.")
    }

    fun isBootstrappedAdminSignedIn(): Boolean {
        val u = auth.currentUser ?: return false
        return u.isEmailVerified &&
            u.email?.equals(FirebaseUpdateAndNotificationsRepository.BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true) == true
    }

    fun todayDateKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(getVerifiedNowMillis()))
    }

    // ==================== Tamper-Proof Server Clock Sync ====================

    fun getVerifiedNowMillis(): Long {
        val anchorServer = prefs.getLong(KEY_ANCHOR_SERVER_MS, 0L)
        val anchorElapsed = prefs.getLong(KEY_ANCHOR_ELAPSED_MS, 0L)
        val highestSeen = prefs.getLong(KEY_HIGHEST_VERIFIED_NOW, 0L)
        val currentElapsed = SystemClock.elapsedRealtime()
        val estimate = if (anchorServer > 0L && currentElapsed >= anchorElapsed) {
            anchorServer + (currentElapsed - anchorElapsed)
        } else {
            System.currentTimeMillis()
        }
        val safeNow = max(estimate, max(highestSeen, System.currentTimeMillis()))
        if (safeNow > highestSeen) {
            prefs.edit().putLong(KEY_HIGHEST_VERIFIED_NOW, safeNow).apply()
        }
        return safeNow
    }

    suspend fun syncServerTime(): Result<Long> {
        val uid = auth.currentUser?.uid ?: return Result.success(getVerifiedNowMillis())
        val path = "$COL_USERS/$uid/$SUBCOL_TIME_SYNC/$DOC_TIME_PROBE"
        return try {
            val probeRef = db.collection(COL_USERS)
                .document(uid)
                .collection(SUBCOL_TIME_SYNC)
                .document(DOC_TIME_PROBE)
            probeRef.set(
                mapOf(
                    "ownerUid" to uid,
                    "ts" to FieldValue.serverTimestamp()
                )
            ).await()
            val snap = probeRef.get().await()
            val ts = snap.getTimestamp("ts", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val serverMs = ts?.toDate()?.time ?: System.currentTimeMillis()
            val prevHigh = prefs.getLong(KEY_HIGHEST_VERIFIED_NOW, 0L)
            val monotonic = max(serverMs, prevHigh)
            prefs.edit()
                .putLong(KEY_ANCHOR_SERVER_MS, serverMs)
                .putLong(KEY_ANCHOR_ELAPSED_MS, SystemClock.elapsedRealtime())
                .putLong(KEY_HIGHEST_VERIFIED_NOW, monotonic)
                .apply()
            Result.success(monotonic)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.success(getVerifiedNowMillis())
        }
    }

    // ==================== Remote Control & Promo Banner ====================

    fun observeRemoteControl(): Flow<RemoteControlConfig> {
        val uid = auth.currentUser?.uid ?: return emptyFlow()
        val path = "$COL_APP_CONFIG/$DOC_REMOTE_CONTROL"
        return db.collection(COL_APP_CONFIG)
            .document(DOC_REMOTE_CONTROL)
            .snapshots()
            .map { RemoteControlConfig.fromSnapshot(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                }
                emit(RemoteControlConfig())
            }
    }

    fun observePromoBanner(): Flow<PromoBannerConfig> {
        val uid = auth.currentUser?.uid ?: return emptyFlow()
        val path = "$COL_APP_CONFIG/$DOC_PROMO_BANNER"
        return db.collection(COL_APP_CONFIG)
            .document(DOC_PROMO_BANNER)
            .snapshots()
            .map { PromoBannerConfig.fromSnapshot(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path)
                }
                emit(PromoBannerConfig())
            }
    }

    suspend fun updateRemoteControl(config: RemoteControlConfig): Result<Unit> {
        val path = "$COL_APP_CONFIG/$DOC_REMOTE_CONTROL"
        return try {
            val uid = requireUid()
            val ref = db.collection(COL_APP_CONFIG).document(DOC_REMOTE_CONTROL)
            val existing = ref.get().await()
            val cleanMsg = config.maintenanceMessage.trim().take(1000)
            val cleanDisabled = config.disabledTools.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(30)
            val cleanCosts = config.pointsRequiredTools.filterValues { it > 0 }.mapValues { it.value.coerceIn(1, 50000) }
            val accessMode = if (config.defaultAccessMode == "TRIAL") "TRIAL" else "OPEN"
            val trialDays = config.defaultTrialDays.coerceIn(1, 3650)

            val payload = mutableMapOf<String, Any>(
                "maintenanceEnabled" to config.maintenanceEnabled,
                "maintenanceMessage" to cleanMsg,
                "maintenanceEtaText" to config.maintenanceEtaText.trim().take(200),
                "disabledTools" to cleanDisabled,
                "pointsRequiredTools" to cleanCosts,
                "seasonalTheme" to config.seasonalPreset().id,
                "defaultAccessMode" to accessMode,
                "defaultTrialDays" to trialDays,
                "subscriptionsEnabled" to config.subscriptionsEnabled,
                "allowWeeklyNameChange" to config.allowWeeklyNameChange,
                "inactivityReminderMessage" to config.inactivityReminderMessage.trim().take(1000),
                "internalUpdateEnabled" to config.internalUpdateEnabled,
                "internalUpdateRevision" to config.internalUpdateRevision.coerceAtLeast(0L),
                "internalUpdateTitle" to config.internalUpdateTitle.trim().take(200),
                "internalUpdateText" to config.internalUpdateText.trim().take(500),
                "internalUpdateDurationSec" to config.internalUpdateDurationSec.coerceIn(3, 30),
                "internalUpdateIncludeSchoolRoster" to config.internalUpdateIncludeSchoolRoster,
                "internalUpdateCustomRosterText" to config.internalUpdateCustomRosterText.take(120000),
                "experimentalLayoutEnabled" to config.experimentalLayoutEnabled,
                "experimentalLayoutMode" to (if (config.experimentalLayoutMode in setOf("CLASSIC", "MODERN_SPLIT", "COMPACT_CARDS", "FOCUS_STUDIO", "SPECIALIST_PRO")) config.experimentalLayoutMode else "CLASSIC"),
                "passwordChangeIntervalDays" to config.passwordChangeIntervalDays.coerceIn(1, 90),
                "chatExpiryHours" to (if (config.chatExpiryHours >= 48) 48 else 24),
                "customPromotionTitles" to config.customPromotionTitles.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(20),
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!existing.exists() || !existing.contains("createdAt")) {
                payload["createdAt"] = FieldValue.serverTimestamp()
                ref.set(payload).await()
            } else {
                payload["createdAt"] = existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
                ref.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePromoBanner(banner: PromoBannerConfig): Result<Unit> {
        val path = "$COL_APP_CONFIG/$DOC_PROMO_BANNER"
        return try {
            val uid = requireUid()
            val cleanImg = banner.imageUrl.trim().take(1000)
            val cleanAction = banner.actionUrl.trim().take(1000)
            require(cleanImg.isEmpty() || cleanImg.startsWith("https://") || cleanImg.startsWith("http://")) {
                "رابط الصورة يجب أن يبدأ بـ https:// أو http://"
            }
            require(cleanAction.isEmpty() || cleanAction.startsWith("https://") || cleanAction.startsWith("http://")) {
                "رابط الإعلان يجب أن يبدأ بـ https:// أو http://"
            }
            val ref = db.collection(COL_APP_CONFIG).document(DOC_PROMO_BANNER)
            val existing = ref.get().await()
            val cleanPlacement = if (banner.placement in setOf("HOME", "ALL_SCREENS", "SPECIFIC_SECTION")) banner.placement else "HOME"
            val cleanPos = if (banner.position == "BOTTOM") "BOTTOM" else "TOP"
            val payload = mutableMapOf<String, Any>(
                "enabled" to banner.enabled,
                "title" to banner.title.trim().take(150),
                "description" to banner.description.trim().take(2000),
                "imageUrl" to cleanImg,
                "actionUrl" to cleanAction,
                "phoneNumber" to banner.phoneNumber.trim().take(40),
                "buttonLabel" to banner.buttonLabel.trim().take(60).ifBlank { "زيارة الرابط" },
                "placement" to cleanPlacement,
                "targetRoute" to banner.targetRoute.trim().take(60),
                "position" to cleanPos,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!existing.exists() || !existing.contains("createdAt")) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            } else {
                payload["createdAt"] = existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
            }
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // ==================== Live Polls & Voting ====================

    fun observeActivePolls(): Flow<List<CloudPoll>> {
        val uid = auth.currentUser?.uid ?: return emptyFlow()
        return db.collection(COL_POLLS)
            .whereEqualTo("active", true)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { CloudPoll.fromSnapshot(it) }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, COL_POLLS)
                }
                emit(emptyList())
            }
    }

    fun observeAllPollsForAdmin(): Flow<List<CloudPoll>> {
        val uid = auth.currentUser?.uid ?: return emptyFlow()
        val query = if (isBootstrappedAdminSignedIn()) {
            db.collection(COL_POLLS)
        } else {
            db.collection(COL_POLLS).whereEqualTo("active", true)
        }
        return query.snapshots()
            .map { snap ->
                snap.documents.mapNotNull { CloudPoll.fromSnapshot(it) }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, COL_POLLS)
                }
                emit(emptyList())
            }
    }

    fun observeMyVotedPollIds(userId: String): Flow<Set<String>> {
        if (userId.isBlank() || auth.currentUser?.uid != userId) return emptyFlow()
        val path = "$COL_USERS/$userId/$SUBCOL_POLL_VOTES"
        return db.collection(COL_USERS)
            .document(userId)
            .collection(SUBCOL_POLL_VOTES)
            .snapshots()
            .map { snap -> snap.documents.map { it.id }.toSet() }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                emit(emptySet())
            }
    }

    suspend fun createPoll(
        question: String,
        options: List<String>,
        targetType: NotificationTargetType,
        targetValue: String
    ): Result<Unit> {
        var docPath = COL_POLLS
        return try {
            val uid = requireUid()
            val cleanQ = question.trim()
            val cleanOpts = options.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(6)
            require(cleanQ.length in 2..300) { "نص السؤال مطلوب (2 إلى 300 حرفاً)" }
            require(cleanOpts.size in 2..6) { "أدخل خيارين على الأقل للتصويت (بحد أقصى 6 خيارات)" }

            val docRef = db.collection(COL_POLLS).document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "question" to cleanQ,
                    "options" to cleanOpts,
                    "targetType" to targetType.raw,
                    "targetValue" to targetValue.trim().take(120),
                    "active" to true,
                    "authorUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setPollActive(pollId: String, active: Boolean): Result<Unit> {
        val cleanId = pollId.trim()
        val path = "$COL_POLLS/$cleanId"
        return try {
            requireUid()
            db.collection(COL_POLLS).document(cleanId).update(
                mapOf(
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePoll(pollId: String): Result<Unit> {
        val cleanId = pollId.trim()
        val path = "$COL_POLLS/$cleanId"
        return try {
            requireUid()
            db.collection(COL_POLLS).document(cleanId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun submitPollVote(pollId: String, optionIndex: Int): Result<Unit> {
        val cleanPollId = pollId.trim()
        val path = "$COL_POLLS/$cleanPollId/$SUBCOL_VOTES"
        return try {
            val uid = requireUid()
            require(cleanPollId.isNotEmpty() && optionIndex in 0..5) { "بيانات التصويت غير صالحة" }
            val batch = db.batch()
            val voteRef = db.collection(COL_POLLS).document(cleanPollId)
                .collection(SUBCOL_VOTES).document(uid)
            batch.set(
                voteRef,
                mapOf(
                    "pollId" to cleanPollId,
                    "voterUid" to uid,
                    "optionIndex" to optionIndex,
                    "votedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val receiptRef = db.collection(COL_USERS).document(uid)
                .collection(SUBCOL_POLL_VOTES).document(cleanPollId)
            batch.set(
                receiptRef,
                mapOf(
                    "pollId" to cleanPollId,
                    "optionIndex" to optionIndex,
                    "votedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun fetchPollVoteTallies(pollId: String, optionCount: Int): Result<List<Int>> {
        val cleanPollId = pollId.trim()
        val path = "$COL_POLLS/$cleanPollId/$SUBCOL_VOTES"
        return try {
            requireUid()
            val snap = db.collection(COL_POLLS).document(cleanPollId)
                .collection(SUBCOL_VOTES)
                .get()
                .await()
            val counts = MutableList(optionCount.coerceAtLeast(2)) { 0 }
            snap.documents.forEach { doc ->
                val idx = (doc.getLong("optionIndex") ?: -1L).toInt()
                if (idx in counts.indices) counts[idx] = counts[idx] + 1
            }
            Result.success(counts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    // ==================== Teacher Account, Encrypted Backups & Telemetry ====================

    fun defaultAccountKeyForUid(uid: String): String = "u_${uid.take(28)}"

    fun resolveAccountKey(username: String, uid: String): String {
        val clean = username.trim()
        return if (clean.length >= 2) {
            BackupCryptoHelper.normalizeUsernameKey(clean)
        } else {
            prefs.getString(KEY_LAST_ACCOUNT_KEY, null)?.takeIf { it.isNotBlank() }
                ?: defaultAccountKeyForUid(uid)
        }
    }

    fun rememberAccountKey(key: String) {
        prefs.edit().putString(KEY_LAST_ACCOUNT_KEY, key).apply()
    }

    suspend fun ensureOrSyncTeacherAccount(
        username: String,
        teacherName: String,
        specialtyId: String,
        gradeId: String,
        remoteControl: RemoteControlConfig,
        passwordOptional: String = "",
        deviceInstallId: String = "",
        isOnlineVisible: Boolean = true
    ): Result<TeacherAccountRecord> {
        val path = COL_TEACHER_ACCOUNTS
        return try {
            val uid = requireUid()
            val targetKey = resolveAccountKey(username, uid)
            val docRef = db.collection(COL_TEACHER_ACCOUNTS).document(targetKey)
            val snap = docRef.get().await()
            val refCode = BackupCryptoHelper.deriveDeterministicReferralCode(uid)
            val nowMs = getVerifiedNowMillis()
            val todayKey = todayDateKey()

            runCatching {
                db.collection(COL_REFERRAL_CODES).document(refCode).set(
                    mapOf(
                        "code" to refCode,
                        "ownerUid" to uid,
                        "accountKey" to targetKey,
                        "teacherName" to teacherName.trim().take(120),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            }

            if (!snap.exists()) {
                val salt = if (passwordOptional.isNotEmpty()) BackupCryptoHelper.generateSaltHex() else ""
                val hash = if (passwordOptional.isNotEmpty()) BackupCryptoHelper.hashPassword(passwordOptional, salt) else ""
                val defaultTrialDays = remoteControl.defaultTrialDays.coerceIn(1, 3650)
                val initialMode = if (remoteControl.defaultAccessMode == "TRIAL") "TRIAL" else "OPEN"
                val trialExp = if (initialMode == "TRIAL") {
                    nowMs + defaultTrialDays.toLong() * 86_400_000L
                } else 0L

                val payload = mapOf(
                    "accountKey" to targetKey,
                    "usernameDisplay" to username.trim().take(60),
                    "ownerUid" to uid,
                    "passwordHash" to hash,
                    "passwordSalt" to salt,
                    "teacherName" to teacherName.trim().take(120),
                    "specialtyId" to specialtyId.take(40),
                    "gradeId" to gradeId.take(40),
                    "referralCode" to refCode,
                    "referredByCode" to "",
                    "points" to 0,
                    "referralCount" to 0,
                    "claimedReferralIds" to emptyList<String>(),
                    "accessMode" to initialMode,
                    "trialDays" to defaultTrialDays,
                    "trialExpiresAtMillis" to trialExp,
                    "todayDateKey" to todayKey,
                    "todayUsageSeconds" to getLocalTodayUsageSeconds(todayKey),
                    "totalUsageSeconds" to getLocalTotalUsageSeconds(),
                    "toolUsageCounts" to getLocalToolCounts(),
                    "ratingStars" to 0,
                    "ratingComment" to "",
                    "promotionTitle" to "",
                    "allowWeeklyNameChange" to false,
                    "nameChangeCount" to 0,
                    "boundDeviceId" to deviceInstallId.trim().take(128),
                    "isOnlineVisible" to isOnlineVisible,
                    "lastPingAtMillis" to nowMs,
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                rememberAccountKey(targetKey)
                val createdSnap = docRef.get().await()
                return Result.success(
                    TeacherAccountRecord.fromSnapshot(createdSnap) ?: TeacherAccountRecord(
                        accountKey = targetKey,
                        usernameDisplay = username.trim(),
                        ownerUid = uid,
                        teacherName = teacherName,
                        referralCode = refCode,
                        accessMode = initialMode,
                        trialDays = defaultTrialDays,
                        trialExpiresAtMillis = trialExp,
                        boundDeviceId = deviceInstallId.trim(),
                        isOnlineVisible = isOnlineVisible,
                        lastPingAtMillis = nowMs
                    )
                )
            } else {
                val existing = TeacherAccountRecord.fromSnapshot(snap)!!
                if (existing.ownerUid.isNotBlank() && existing.ownerUid != uid) {
                    return Result.failure(IllegalStateException("اسم المستخدم هذا مستخدم بالفعل من حساب آخر، اختر اسماً مختلفاً."))
                }
                val newSalt = if (passwordOptional.isNotEmpty()) {
                    existing.passwordSalt.ifBlank { BackupCryptoHelper.generateSaltHex() }
                } else existing.passwordSalt
                val newHash = if (passwordOptional.isNotEmpty()) {
                    BackupCryptoHelper.hashPassword(passwordOptional, newSalt)
                } else existing.passwordHash

                val resolvedDevice = deviceInstallId.trim().take(128).ifBlank { existing.boundDeviceId }
                val updates = mutableMapOf<String, Any>(
                    "usernameDisplay" to username.trim().take(60).ifBlank { existing.usernameDisplay },
                    "passwordHash" to newHash,
                    "passwordSalt" to newSalt,
                    "teacherName" to teacherName.trim().take(120).ifBlank { existing.teacherName },
                    "specialtyId" to specialtyId.take(40),
                    "gradeId" to gradeId.take(40),
                    "referralCode" to existing.referralCode.ifBlank { refCode },
                    "boundDeviceId" to resolvedDevice,
                    "isOnlineVisible" to isOnlineVisible,
                    "lastPingAtMillis" to nowMs,
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
                rememberAccountKey(targetKey)
                val fresh = docRef.get().await()
                return Result.success(TeacherAccountRecord.fromSnapshot(fresh) ?: existing)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun uploadEncryptedBackup(
        username: String,
        password: String,
        plainStateJson: String,
        studentCount: Int,
        teacherName: String,
        specialtyId: String,
        gradeId: String,
        remoteControl: RemoteControlConfig
    ): Result<TeacherAccountRecord> {
        val cleanUser = username.trim()
        require(cleanUser.length >= 2) { "أدخل اسم مستخدم لا يقل عن حرفين لحفظ النسخة السحابية." }
        require(password.length >= 4) { "أدخل كلمة مرور من 4 أحرف/أرقام على الأقل لحماية بياناتك." }

        val accResult = ensureOrSyncTeacherAccount(
            username = cleanUser,
            teacherName = teacherName,
            specialtyId = specialtyId,
            gradeId = gradeId,
            remoteControl = remoteControl,
            passwordOptional = password
        )
        val account = accResult.getOrElse { return Result.failure(it) }
        val uid = requireUid()
        val path = "$COL_TEACHER_ACCOUNTS/${account.accountKey}/$SUBCOL_BACKUPS/$DOC_LATEST_BACKUP"
        return try {
            val (encryptedBlob, checksum) = BackupCryptoHelper.encryptAndCompress(
                plainJson = plainStateJson,
                password = password,
                saltHex = account.passwordSalt
            )
            require(encryptedBlob.length <= 890_000) {
                "حجم البيانات كبير جداً على سعة المستند الواحد؛ يرجى تقليل المرفقات."
            }
            db.collection(COL_TEACHER_ACCOUNTS)
                .document(account.accountKey)
                .collection(SUBCOL_BACKUPS)
                .document(DOC_LATEST_BACKUP)
                .set(
                    mapOf(
                        "encryptedBlob" to encryptedBlob,
                        "checksumSha256" to checksum,
                        "ownerUid" to uid,
                        "studentCount" to studentCount.coerceAtLeast(0),
                        "appVersionCode" to BuildConfig.VERSION_CODE,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun restoreEncryptedBackupByCredentials(
        username: String,
        password: String
    ): Result<String> {
        val cleanUser = username.trim()
        if (cleanUser.length < 2 || password.length < 4) {
            return Result.failure(IllegalArgumentException("أدخل اسم المستخدم وكلمة المرور الصحيحة."))
        }
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("يرجى تسجيل الدخول بحساب Google أولاً للاتصال بقاعدة البيانات."))
        val key = BackupCryptoHelper.normalizeUsernameKey(cleanUser)
        val path = "$COL_TEACHER_ACCOUNTS/$key/$SUBCOL_BACKUPS/$DOC_LATEST_BACKUP"
        return try {
            val accSnap = db.collection(COL_TEACHER_ACCOUNTS).document(key).get().await()
            val account = TeacherAccountRecord.fromSnapshot(accSnap)
                ?: return Result.failure(IllegalStateException("لا يوجد حساب محفوظ باسم المستخدم ($cleanUser)."))
            if (account.passwordHash.isBlank() || account.passwordSalt.isBlank()) {
                return Result.failure(IllegalStateException("لم يتم ضبط كلمة مرور لهذا الحساب بعد."))
            }
            if (!BackupCryptoHelper.verifyPassword(password, account.passwordSalt, account.passwordHash)) {
                return Result.failure(IllegalStateException("كلمة المرور غير صحيحة."))
            }
            val backupSnap = db.collection(COL_TEACHER_ACCOUNTS)
                .document(key)
                .collection(SUBCOL_BACKUPS)
                .document(DOC_LATEST_BACKUP)
                .get()
                .await()
            val backup = TeacherEncryptedBackupRecord.fromSnapshot(backupSnap)
                ?: return Result.failure(IllegalStateException("لا توجد نسخة احتياطية مرفوعة لهذا الحساب بعد."))

            val plainJson = BackupCryptoHelper.decryptAndDecompress(
                encryptedBase64 = backup.encryptedBlob,
                password = password,
                saltHex = account.passwordSalt,
                expectedChecksum = backup.checksumSha256
            ) ?: return Result.failure(IllegalStateException("فشل فك تشفير النسخة الاحتياطية؛ تأكد من كلمة المرور."))

            if (account.ownerUid == uid) {
                rememberAccountKey(key)
            }
            Result.success(plainJson)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun syncLocalUsageAndTelemetry(
        accountKey: String,
        todayDateKey: String,
        todayUsageSeconds: Long,
        totalUsageSeconds: Long,
        toolUsageCounts: Map<String, Long>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "$COL_TEACHER_ACCOUNTS/$accountKey"
        return try {
            val docRef = db.collection(COL_TEACHER_ACCOUNTS).document(accountKey)
            val snap = docRef.get().await()
            val existing = TeacherAccountRecord.fromSnapshot(snap) ?: return Result.success(Unit)
            if (existing.ownerUid != uid) return Result.success(Unit)
            val mergedTools = existing.toolUsageCounts.toMutableMap()
            toolUsageCounts.forEach { (k, v) ->
                mergedTools[k] = max(mergedTools[k] ?: 0L, v)
            }
            val mergedToday = if (existing.todayDateKey == todayDateKey) {
                max(existing.todayUsageSeconds, todayUsageSeconds)
            } else todayUsageSeconds
            val mergedTotal = max(existing.totalUsageSeconds, totalUsageSeconds)

            docRef.update(
                mapOf(
                    "todayDateKey" to todayDateKey,
                    "todayUsageSeconds" to mergedToday.coerceIn(0L, 199_999L),
                    "totalUsageSeconds" to mergedTotal.coerceIn(0L, 99_999_999L),
                    "toolUsageCounts" to mergedTools,
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitRating(accountKey: String, stars: Int, comment: String): Result<Unit> {
        val path = "$COL_TEACHER_ACCOUNTS/$accountKey"
        return try {
            val uid = requireUid()
            val cleanStars = stars.coerceIn(1, 5)
            val cleanComment = comment.trim().take(500)
            val docRef = db.collection(COL_TEACHER_ACCOUNTS).document(accountKey)
            val snap = docRef.get().await()
            val acc = TeacherAccountRecord.fromSnapshot(snap)
                ?: return Result.failure(IllegalStateException("الحساب غير متزامن بعد."))
            if (acc.ownerUid != uid) {
                return Result.failure(IllegalStateException("لا يمكن تقييم حساب لا تملكه."))
            }
            docRef.update(
                mapOf(
                    "ratingStars" to cleanStars,
                    "ratingComment" to cleanComment,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // ==================== Referrals, Group Gifts & Points ====================

    suspend fun redeemFriendReferralCode(myAccountKey: String, friendCodeRaw: String): Result<Int> {
        val cleanCode = friendCodeRaw.trim().uppercase(Locale.US)
        if (cleanCode.length < 4) {
            return Result.failure(IllegalArgumentException("أدخل رمز إحالة صحيح."))
        }
        val uid = requireUid()
        val path = "$COL_REFERRAL_CODES/$cleanCode"
        return try {
            val refCodeSnap = db.collection(COL_REFERRAL_CODES).document(cleanCode).get().await()
            if (!refCodeSnap.exists()) {
                return Result.failure(IllegalStateException("رمز الإحالة غير موجود."))
            }
            val referrerUid = (refCodeSnap.getString("ownerUid")).orEmpty()
            if (referrerUid == uid || referrerUid.isBlank()) {
                return Result.failure(IllegalStateException("لا يمكنك استخدام رمز الإحالة الخاص بك!"))
            }
            val myRef = db.collection(COL_TEACHER_ACCOUNTS).document(myAccountKey)
            val mySnap = myRef.get().await()
            val myAcc = TeacherAccountRecord.fromSnapshot(mySnap)
                ?: return Result.failure(IllegalStateException("احفظ حسابك السحابي أولاً قبل استرداد الإحالة."))
            if (myAcc.referredByCode.isNotBlank()) {
                return Result.failure(IllegalStateException("لقد استخدمت رمز إحالة مسبقاً (${myAcc.referredByCode})."))
            }

            val claimId = "rc_${uid.take(20)}_$cleanCode"
            val claimRef = db.collection(COL_REFERRAL_CLAIMS).document(claimId)
            claimRef.set(
                mapOf(
                    "claimerUid" to uid,
                    "claimerAccountKey" to myAccountKey,
                    "referrerCode" to cleanCode,
                    "referrerUid" to referrerUid,
                    "pointsAwarded" to REFERRAL_BONUS_POINTS,
                    "claimedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val newPoints = myAcc.points + REFERRAL_BONUS_POINTS
            myRef.update(
                mapOf(
                    "referredByCode" to cleanCode,
                    "points" to newPoints,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(REFERRAL_BONUS_POINTS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun collectPendingReferralRewards(account: TeacherAccountRecord): Result<Int> {
        val uid = auth.currentUser?.uid ?: return Result.success(0)
        if (account.ownerUid != uid) return Result.success(0)
        return try {
            val claimsSnap = db.collection(COL_REFERRAL_CLAIMS)
                .whereEqualTo("referrerUid", uid)
                .get()
                .await()
            val uncollected = claimsSnap.documents.filter { it.id !in account.claimedReferralIds }
            if (uncollected.isEmpty()) return Result.success(0)

            var bonus = 0
            val newlyClaimedIds = mutableListOf<String>()
            uncollected.forEach { doc ->
                bonus += (doc.getLong("pointsAwarded") ?: REFERRAL_BONUS_POINTS.toLong()).toInt()
                newlyClaimedIds += doc.id
            }
            val updatedIds = (account.claimedReferralIds + newlyClaimedIds).distinct().take(200)
            db.collection(COL_TEACHER_ACCOUNTS).document(account.accountKey).update(
                mapOf(
                    "points" to (account.points + bonus).coerceAtMost(1_000_000),
                    "referralCount" to (account.referralCount + uncollected.size),
                    "claimedReferralIds" to updatedIds,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(bonus)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, COL_REFERRAL_CLAIMS)
            Result.failure(e)
        }
    }

    suspend fun claimUnclaimedAdminGifts(account: TeacherAccountRecord): Result<Int> {
        val uid = auth.currentUser?.uid ?: return Result.success(0)
        if (account.ownerUid != uid) return Result.success(0)
        return try {
            val activeGifts = db.collection(COL_ADMIN_GIFTS)
                .whereEqualTo("active", true)
                .get()
                .await()
                .documents
                .mapNotNull { AdminGiftItem.fromSnapshot(it) }
            if (activeGifts.isEmpty()) return Result.success(0)

            val claimedSnap = db.collection(COL_USERS)
                .document(uid)
                .collection(SUBCOL_CLAIMED_GIFTS)
                .get()
                .await()
            val alreadyClaimedIds = claimedSnap.documents.map { it.id }.toSet()
            val toClaim = activeGifts.filter { it.id !in alreadyClaimedIds && it.amount > 0 }
            if (toClaim.isEmpty()) return Result.success(0)

            var totalAdded = 0
            val batch = db.batch()
            val claimedCol = db.collection(COL_USERS).document(uid).collection(SUBCOL_CLAIMED_GIFTS)
            toClaim.forEach { gift ->
                totalAdded += gift.amount
                batch.set(
                    claimedCol.document(gift.id),
                    mapOf(
                        "giftId" to gift.id,
                        "amount" to gift.amount,
                        "claimedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()

            db.collection(COL_TEACHER_ACCOUNTS).document(account.accountKey).update(
                mapOf(
                    "points" to (account.points + totalAdded).coerceAtMost(1_000_000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(totalAdded)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, COL_ADMIN_GIFTS)
            Result.failure(e)
        }
    }

    suspend fun spendPointsForToolUnlock(account: TeacherAccountRecord, cost: Int): Result<Int> {
        val uid = requireUid()
        if (account.ownerUid != uid) return Result.failure(IllegalStateException("الحساب غير متطابق."))
        if (account.points < cost) return Result.failure(IllegalStateException("نقاطك الحالية (${account.points}) غير كافية (المطلوب $cost نقطة)."))
        val newPoints = (account.points - cost).coerceAtLeast(0)
        return try {
            db.collection(COL_TEACHER_ACCOUNTS).document(account.accountKey).update(
                mapOf(
                    "points" to newPoints,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(newPoints)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "$COL_TEACHER_ACCOUNTS/${account.accountKey}")
            Result.failure(e)
        }
    }

    // ==================== Developer Feedback (Hidden Admin Name) ====================

    suspend fun submitDeveloperFeedback(
        senderName: String,
        accountKey: String,
        category: FeedbackCategory,
        message: String
    ): Result<DeveloperFeedbackRecord> {
        var docPath = COL_FEEDBACK
        return try {
            val uid = requireUid()
            val cleanMsg = message.trim()
            require(cleanMsg.length >= 2 && cleanMsg.length <= 5000) { "يرجى كتابة رسالة لا تقل عن حرفين." }
            val wc = BackupCryptoHelper.countWords(cleanMsg).coerceIn(1, 2000)
            val isLongComplaint = wc > 100
            val nowMs = getVerifiedNowMillis()
            val lockUntil = if (isLongComplaint) nowMs + THREE_DAYS_MILLIS else 0L

            val ref = db.collection(COL_FEEDBACK).document()
            docPath = ref.path
            ref.set(
                mapOf(
                    "senderUid" to uid,
                    "senderName" to senderName.trim().take(120),
                    "accountKey" to accountKey.take(128),
                    "category" to category.raw,
                    "message" to cleanMsg,
                    "wordCount" to wc,
                    "lockedThreeDays" to isLongComplaint,
                    "lockedUntilMillis" to lockUntil,
                    "status" to "OPEN",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val saved = DeveloperFeedbackRecord(
                id = ref.id,
                senderUid = uid,
                senderName = senderName.trim(),
                accountKey = accountKey,
                category = category.raw,
                message = cleanMsg,
                wordCount = wc,
                lockedThreeDays = isLongComplaint,
                lockedUntilMillis = lockUntil,
                status = "OPEN"
            )
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    // ==================== Parent Read-Only Cloud Reports ====================

    suspend fun publishParentReport(
        teacherName: String,
        subjectName: String,
        studentName: String,
        gradeTitle: String,
        sectionId: String,
        visibilityJson: String,
        reportDataJson: String
    ): Result<ParentReportRecord> {
        val uid = requireUid()
        val code = BackupCryptoHelper.generateReportCode(uid)
        val path = "$COL_PARENT_REPORTS/$code"
        return try {
            val nowMs = getVerifiedNowMillis()
            val expiresMs = nowMs + THREE_DAYS_MILLIS
            db.collection(COL_PARENT_REPORTS).document(code).set(
                mapOf(
                    "reportCode" to code,
                    "teacherUid" to uid,
                    "teacherName" to teacherName.trim().take(120),
                    "subjectName" to subjectName.trim().take(80),
                    "studentName" to studentName.trim().take(120),
                    "gradeTitle" to gradeTitle.trim().take(60),
                    "sectionId" to sectionId.trim().take(20),
                    "expiresAtMillis" to expiresMs,
                    "deletedByAdmin" to false,
                    "visibilityJson" to visibilityJson.take(2000),
                    "reportDataJson" to reportDataJson.take(8000),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(
                ParentReportRecord(
                    reportCode = code,
                    teacherUid = uid,
                    teacherName = teacherName,
                    subjectName = subjectName,
                    studentName = studentName,
                    gradeTitle = gradeTitle,
                    sectionId = sectionId,
                    expiresAtMillis = expiresMs,
                    deletedByAdmin = false,
                    visibilityJson = visibilityJson,
                    reportDataJson = reportDataJson
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun fetchParentReportByCode(codeRaw: String): Result<ParentReportRecord> {
        val code = codeRaw.trim().uppercase(Locale.US)
        if (!code.matches(Regex("^[A-Z0-9]{4,24}$"))) {
            return Result.failure(IllegalArgumentException("صيغة كود التقرير غير صالحة."))
        }
        if (auth.currentUser == null) {
            return Result.failure(IllegalStateException("يرجى تسجيل الدخول بحساب Google لعرض التقرير السحابي."))
        }
        val path = "$COL_PARENT_REPORTS/$code"
        return try {
            val snap = db.collection(COL_PARENT_REPORTS).document(code).get().await()
            val rec = ParentReportRecord.fromSnapshot(snap)
                ?: return Result.failure(IllegalStateException("لم يتم العثور على تقرير بهذا الكود أو تم حذفه."))
            if (rec.deletedByAdmin) {
                return Result.failure(IllegalStateException("تم حذف هذا التقرير وإيقاف صلاحيته."))
            }
            val nowMs = getVerifiedNowMillis()
            if (rec.expiresAtMillis in 1..nowMs) {
                return Result.failure(IllegalStateException("انتهت صلاحية هذا الرابط بعد مرور 3 أيام من إنشائه."))
            }
            Result.success(rec)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    fun observeMyParentReports(uid: String): Flow<List<ParentReportRecord>> {
        if (uid.isBlank() || auth.currentUser?.uid != uid) return emptyFlow()
        return db.collection(COL_PARENT_REPORTS)
            .whereEqualTo("teacherUid", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { ParentReportRecord.fromSnapshot(it) }
                    .filter { !it.deletedByAdmin }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, COL_PARENT_REPORTS)
                }
                emit(emptyList())
            }
    }

    // ==================== Admin Directory, Gifts, Feedback & Parent Reports ====================

    suspend fun adminFetchAllAccounts(): Result<List<TeacherAccountRecord>> {
        return try {
            requireUid()
            val snap = db.collection(COL_TEACHER_ACCOUNTS).get().await()
            val list = snap.documents.mapNotNull { TeacherAccountRecord.fromSnapshot(it) }
                .sortedByDescending { it.lastActiveAt?.seconds ?: 0L }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, COL_TEACHER_ACCOUNTS)
            Result.failure(e)
        }
    }

    suspend fun adminUpdateAccountControl(
        accountKey: String,
        points: Int,
        accessMode: String,
        trialDays: Int
    ): Result<Unit> {
        val path = "$COL_TEACHER_ACCOUNTS/$accountKey"
        return try {
            requireUid()
            val cleanMode = when (accessMode.uppercase(Locale.US)) {
                "TRIAL" -> "TRIAL"
                "SUSPENDED" -> "SUSPENDED"
                else -> "OPEN"
            }
            val cleanDays = trialDays.coerceIn(0, 3650)
            val expMs = if (cleanMode == "TRIAL" && cleanDays > 0) {
                getVerifiedNowMillis() + cleanDays.toLong() * 86_400_000L
            } else 0L
            db.collection(COL_TEACHER_ACCOUNTS).document(accountKey).update(
                mapOf(
                    "points" to points.coerceIn(0, 1_000_000),
                    "accessMode" to cleanMode,
                    "trialDays" to cleanDays,
                    "trialExpiresAtMillis" to expMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminCreateGroupGift(amount: Int, message: String): Result<Unit> {
        var docPath = COL_ADMIN_GIFTS
        return try {
            val uid = requireUid()
            val cleanAmount = amount.coerceIn(1, 10_000)
            val cleanMsg = message.trim().take(300).ifBlank { "هدية نقاط من فريق التطوير" }
            val ref = db.collection(COL_ADMIN_GIFTS).document()
            docPath = ref.path
            ref.set(
                mapOf(
                    "amount" to cleanAmount,
                    "message" to cleanMsg,
                    "targetType" to "ALL",
                    "active" to true,
                    "authorUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adminFetchAllFeedback(): Result<List<DeveloperFeedbackRecord>> {
        return try {
            requireUid()
            val snap = db.collection(COL_FEEDBACK).get().await()
            val list = snap.documents.mapNotNull { DeveloperFeedbackRecord.fromSnapshot(it) }
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, COL_FEEDBACK)
            Result.failure(e)
        }
    }

    suspend fun adminMarkFeedbackReviewed(feedbackId: String, reviewed: Boolean): Result<Unit> {
        val path = "$COL_FEEDBACK/$feedbackId"
        return try {
            requireUid()
            db.collection(COL_FEEDBACK).document(feedbackId).update(
                mapOf(
                    "status" to if (reviewed) "REVIEWED" else "OPEN",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminFetchAllParentReports(): Result<List<ParentReportRecord>> {
        return try {
            requireUid()
            val snap = db.collection(COL_PARENT_REPORTS).get().await()
            val list = snap.documents.mapNotNull { ParentReportRecord.fromSnapshot(it) }
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, COL_PARENT_REPORTS)
            Result.failure(e)
        }
    }

    suspend fun adminDeleteParentReport(reportCode: String): Result<Unit> {
        val path = "$COL_PARENT_REPORTS/$reportCode"
        return try {
            requireUid()
            db.collection(COL_PARENT_REPORTS).document(reportCode).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // ==================== Local Usage & Promo Prefs ====================

    fun addLocalUsageSeconds(deltaSec: Long, toolKey: String? = null) {
        val today = todayDateKey()
        val storedDate = prefs.getString(KEY_LOCAL_TODAY_DATE, "")
        val baseToday = if (storedDate == today) prefs.getLong(KEY_LOCAL_TODAY_SECONDS, 0L) else 0L
        val baseTotal = prefs.getLong(KEY_LOCAL_TOTAL_SECONDS, 0L)
        val editor = prefs.edit()
            .putString(KEY_LOCAL_TODAY_DATE, today)
            .putLong(KEY_LOCAL_TODAY_SECONDS, baseToday + deltaSec.coerceAtLeast(0L))
            .putLong(KEY_LOCAL_TOTAL_SECONDS, baseTotal + deltaSec.coerceAtLeast(0L))
        if (!toolKey.isNullOrBlank()) {
            val map = getLocalToolCounts().toMutableMap()
            map[toolKey] = (map[toolKey] ?: 0L) + 1L
            val json = org.json.JSONObject()
            map.forEach { (k, v) -> json.put(k, v) }
            editor.putString(KEY_LOCAL_TOOL_COUNTS, json.toString())
        }
        editor.apply()
    }

    fun getLocalTodayUsageSeconds(todayKey: String = todayDateKey()): Long {
        val storedDate = prefs.getString(KEY_LOCAL_TODAY_DATE, "")
        return if (storedDate == todayKey) prefs.getLong(KEY_LOCAL_TODAY_SECONDS, 0L) else 0L
    }

    fun getLocalTotalUsageSeconds(): Long = prefs.getLong(KEY_LOCAL_TOTAL_SECONDS, 0L)

    fun getLocalToolCounts(): Map<String, Long> {
        val raw = prefs.getString(KEY_LOCAL_TOOL_COUNTS, null) ?: return emptyMap()
        return runCatching {
            val obj = org.json.JSONObject(raw)
            val map = mutableMapOf<String, Long>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optLong(k, 0L)
            }
            map
        }.getOrDefault(emptyMap())
    }

    fun getUnlockedToolNamesLocal(): Set<String> {
        val raw = prefs.getString(KEY_UNLOCKED_TOOLS_CSV, "").orEmpty()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun addUnlockedToolNameLocal(toolName: String) {
        val next = getUnlockedToolNamesLocal() + toolName.trim()
        prefs.edit().putString(KEY_UNLOCKED_TOOLS_CSV, next.joinToString(",")).apply()
    }

    // ==================== Extended Admin User Actions ====================

    suspend fun adminUpdateUserExtendedControl(
        accountKey: String,
        points: Int,
        accessMode: String,
        trialDays: Int,
        promotionTitle: String,
        allowWeeklyNameChange: Boolean
    ): Result<Unit> {
        val path = "$COL_TEACHER_ACCOUNTS/$accountKey"
        return try {
            requireUid()
            val cleanMode = when (accessMode.uppercase(Locale.US)) {
                "TRIAL" -> "TRIAL"
                "SUSPENDED" -> "SUSPENDED"
                else -> "OPEN"
            }
            val cleanDays = trialDays.coerceIn(0, 3650)
            val expMs = if (cleanMode == "TRIAL" && cleanDays > 0) {
                getVerifiedNowMillis() + cleanDays.toLong() * 86_400_000L
            } else 0L
            db.collection(COL_TEACHER_ACCOUNTS).document(accountKey).update(
                mapOf(
                    "points" to points.coerceIn(0, 1_000_000),
                    "accessMode" to cleanMode,
                    "trialDays" to cleanDays,
                    "trialExpiresAtMillis" to expMs,
                    "promotionTitle" to promotionTitle.trim().take(80),
                    "allowWeeklyNameChange" to allowWeeklyNameChange,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminDeleteTeacherAccount(accountKey: String): Result<Unit> {
        val path = "$COL_TEACHER_ACCOUNTS/$accountKey"
        return try {
            requireUid()
            db.collection(COL_TEACHER_ACCOUNTS).document(accountKey).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun adminUnbindAccountDevice(accountKey: String): Result<Unit> {
        val path = "$COL_TEACHER_ACCOUNTS/$accountKey"
        return try {
            requireUid()
            db.collection(COL_TEACHER_ACCOUNTS).document(accountKey).update(
                mapOf(
                    "boundDeviceId" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun unbindCurrentDeviceFromAccount(accountKey: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "$COL_TEACHER_ACCOUNTS/$accountKey"
        return try {
            val ref = db.collection(COL_TEACHER_ACCOUNTS).document(accountKey)
            val snap = ref.get().await()
            val acc = TeacherAccountRecord.fromSnapshot(snap) ?: return Result.success(Unit)
            if (acc.ownerUid == uid) {
                ref.update(
                    mapOf(
                        "boundDeviceId" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (_: Exception) {
            Result.success(Unit)
        }
    }

    // ==================== Activation Codes & Single-Device Lock ====================

    suspend fun adminFetchActivationCodes(): Result<List<ActivationCodeRecord>> {
        return try {
            requireUid()
            val snap = db.collection("activation_codes").get().await()
            val list = snap.documents.mapNotNull { ActivationCodeRecord.fromSnapshot(it) }
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "activation_codes")
            Result.failure(e)
        }
    }

    suspend fun adminCreateActivationCode(
        customCode: String,
        assignedUsername: String,
        assignedPasswordPlain: String,
        notes: String = ""
    ): Result<ActivationCodeRecord> {
        val code = customCode.trim().uppercase(Locale.US)
            .ifBlank { BackupCryptoHelper.generateActivationCode("RF") }
        val cleanUser = assignedUsername.trim().uppercase(Locale.US)
            .ifBlank { BackupCryptoHelper.generateAutoFourLetterUsername() }
        val cleanPass = assignedPasswordPlain.trim().ifBlank { "1234" }
        val path = "activation_codes/$code"
        return try {
            requireUid()
            require(code.matches(Regex("^[A-Z0-9_\\-]{4,32}$"))) { "صيغة كود التفعيل غير صالحة." }
            val salt = BackupCryptoHelper.generateSaltHex()
            val hash = BackupCryptoHelper.hashPassword(cleanPass, salt)
            db.collection("activation_codes").document(code).set(
                mapOf(
                    "assignedUsername" to cleanUser.take(60),
                    "assignedPasswordHash" to hash,
                    "assignedPasswordSalt" to salt,
                    "notes" to notes.trim().take(200),
                    "isUsed" to false,
                    "usedByUid" to "",
                    "boundDeviceId" to "",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(
                ActivationCodeRecord(
                    code = code,
                    assignedUsername = cleanUser,
                    assignedPasswordHash = hash,
                    assignedPasswordSalt = salt,
                    notes = notes.trim(),
                    isUsed = false
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminResetActivationCodeDevice(code: String): Result<Unit> {
        val cleanCode = code.trim().uppercase(Locale.US)
        return try {
            requireUid()
            db.collection("activation_codes").document(cleanCode).update(
                mapOf(
                    "boundDeviceId" to "",
                    "isUsed" to false,
                    "usedByUid" to ""
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "activation_codes/$cleanCode")
            Result.failure(e)
        }
    }

    suspend fun adminDeleteActivationCode(code: String): Result<Unit> {
        val cleanCode = code.trim().uppercase(Locale.US)
        return try {
            requireUid()
            db.collection("activation_codes").document(cleanCode).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "activation_codes/$cleanCode")
            Result.failure(e)
        }
    }

    suspend fun claimActivationCodeWithDeviceLock(
        codeRaw: String,
        deviceInstallId: String
    ): Result<ActivationCodeRecord> {
        val cleanCode = codeRaw.trim().uppercase(Locale.US)
        if (!cleanCode.matches(Regex("^[A-Z0-9_\\-]{4,32}$"))) {
            return Result.failure(IllegalArgumentException("صيغة كود التفعيل غير صحيحة."))
        }
        val uid = requireUid()
        val path = "activation_codes/$cleanCode"
        return try {
            val docRef = db.collection("activation_codes").document(cleanCode)
            val snap = docRef.get().await()
            val rec = ActivationCodeRecord.fromSnapshot(snap)
                ?: return Result.failure(IllegalStateException("كود التفعيل غير موجود."))
            if (rec.isUsed && rec.boundDeviceId.isNotBlank() && rec.boundDeviceId != deviceInstallId && rec.usedByUid != uid) {
                return Result.failure(IllegalStateException("هذا الكود مرتبط بجهاز آخر بالفعل. يرجى تسجيل الخروج من الجهاز السابق أو مراسلة المطور لفك الربط."))
            }
            docRef.update(
                mapOf(
                    "isUsed" to true,
                    "usedByUid" to uid,
                    "boundDeviceId" to deviceInstallId.take(128)
                )
            ).await()
            Result.success(rec.copy(isUsed = true, usedByUid = uid, boundDeviceId = deviceInstallId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun releaseActivationCodeOnSignOut(deviceInstallId: String) {
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            val snap = db.collection("activation_codes")
                .whereEqualTo("usedByUid", uid)
                .get()
                .await()
            snap.documents.forEach { doc ->
                val bound = doc.getString("boundDeviceId").orEmpty()
                if (bound.isBlank() || bound == deviceInstallId) {
                    doc.reference.update(
                        mapOf(
                            "boundDeviceId" to "",
                            "isUsed" to false,
                            "usedByUid" to ""
                        )
                    ).await()
                }
            }
        }
    }

    // ==================== Teacher-to-Teacher Chat v2 (DMs + Specialty Room, 50 Cap / 25 Each, 8h Images, Edit/Delete, Ticks) ====================

    fun dmThreadIdFor(uidA: String, uidB: String): String {
        val a = uidA.trim()
        val b = uidB.trim()
        return if (a <= b) "DM_${a}_${b}" else "DM_${b}_${a}"
    }

    suspend fun findTeacherByUsername(usernameRaw: String): Result<TeacherAccountRecord> {
        val clean = usernameRaw.trim()
        if (clean.length < 2) {
            return Result.failure(IllegalArgumentException("أدخل اسم مستخدم صالح للبحث عنه."))
        }
        return try {
            requireUid()
            val key = BackupCryptoHelper.normalizeUsernameKey(clean)
            val snap = db.collection(COL_TEACHER_ACCOUNTS).document(key).get().await()
            val acc = TeacherAccountRecord.fromSnapshot(snap)
                ?: return Result.failure(IllegalStateException("لا يوجد مستخدم مسجّل باسم ($clean). تأكد من مطابقة اسم المستخدم."))
            Result.success(acc)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, COL_TEACHER_ACCOUNTS)
            Result.failure(e)
        }
    }

    suspend fun updateOnlinePresence(accountKey: String, isOnlineVisible: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (accountKey.isBlank()) return Result.success(Unit)
        return try {
            val nowMs = getVerifiedNowMillis()
            val ref = db.collection(COL_TEACHER_ACCOUNTS).document(accountKey)
            val snap = ref.get().await()
            val acc = TeacherAccountRecord.fromSnapshot(snap) ?: return Result.success(Unit)
            if (acc.ownerUid == uid) {
                ref.update(
                    mapOf(
                        "isOnlineVisible" to isOnlineVisible,
                        "lastPingAtMillis" to nowMs,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.success(Unit)
        }
    }

    fun observeSpecialtyChatMessages(specialtyId: String): Flow<List<TeacherChatMessageRecord>> {
        val uid = auth.currentUser?.uid ?: return emptyFlow()
        return db.collection("teacher_chat_messages")
            .whereEqualTo("specialtyId", specialtyId)
            .snapshots()
            .map { snap ->
                val now = getVerifiedNowMillis()
                snap.documents.mapNotNull { doc ->
                    val rec = TeacherChatMessageRecord.fromSnapshot(doc)
                    if (rec == null || (rec.expiresAtMillis > 0L && now > rec.expiresAtMillis)) {
                        runCatching { doc.reference.delete() }
                        null
                    } else if (rec.threadId != "ROOM" || uid in rec.deletedForUids) {
                        null
                    } else if (rec.imageBase64.isNotBlank() && rec.imageExpiresAtMillis in 1L..now) {
                        rec.copy(imageBase64 = "")
                    } else rec
                }.sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "teacher_chat_messages")
                }
                emit(emptyList())
            }
    }

    fun observeDirectChatMessagesForUser(): Flow<List<TeacherChatMessageRecord>> {
        val uid = auth.currentUser?.uid ?: return emptyFlow()
        val sentFlow = db.collection("teacher_chat_messages")
            .whereEqualTo("senderUid", uid)
            .snapshots()
            .map { it.documents }
            .catch { emit(emptyList()) }
        val recvFlow = db.collection("teacher_chat_messages")
            .whereEqualTo("recipientUid", uid)
            .snapshots()
            .map { it.documents }
            .catch { emit(emptyList()) }
        return kotlinx.coroutines.flow.combine(sentFlow, recvFlow) { sDocs, rDocs ->
            val now = getVerifiedNowMillis()
            val allDocs = (sDocs + rDocs).distinctBy { it.id }

            allDocs.mapNotNull { doc ->
                val rec = TeacherChatMessageRecord.fromSnapshot(doc)
                if (rec == null || (rec.expiresAtMillis > 0L && now > rec.expiresAtMillis)) {
                    runCatching { doc.reference.delete() }
                    null
                } else if (!rec.threadId.startsWith("DM_") || uid in rec.deletedForUids) {
                    null
                } else if (rec.imageBase64.isNotBlank() && rec.imageExpiresAtMillis in 1L..now) {
                    rec.copy(imageBase64 = "")
                } else rec
            }.sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
        }
    }

    suspend fun getTodayChatCount(): Int {
        val uid = auth.currentUser?.uid ?: return 0
        val today = todayDateKey()
        return runCatching {
            val snap = db.collection(COL_USERS).document(uid)
                .collection("chat_quota").document(today).get().await()
            (snap.getLong("count") ?: 0L).toInt()
        }.getOrDefault(0)
    }

    suspend fun sendSpecialtyChatMessage(
        senderName: String,
        specialtyId: String,
        gradeId: String,
        messageText: String,
        expiryHours: Int = 24,
        senderUsername: String = "",
        threadId: String = "ROOM",
        recipientAccountKey: String = "",
        recipientUid: String = "",
        recipientName: String = "",
        imageBase64: String = ""
    ): Result<Int> {
        val cleanText = messageText.trim()
        val cleanImg = imageBase64.trim().take(180000)
        if (cleanText.isEmpty() && cleanImg.isEmpty()) {
            return Result.failure(IllegalArgumentException("أدخل رسالة نصية أو اختر صورة."))
        }
        if (cleanText.length > 1000) {
            return Result.failure(IllegalArgumentException("النص يجب أن لا يتجاوز 1000 حرف."))
        }
        val uid = requireUid()
        val today = todayDateKey()
        val quotaRef = db.collection(COL_USERS).document(uid).collection("chat_quota").document(today)
        return try {
            val quotaSnap = quotaRef.get().await()
            val currentCount = (quotaSnap.getLong("count") ?: 0L).toInt()
            if (currentCount >= 50) {
                return Result.failure(IllegalStateException("وصلت للحد اليومي (50 رسالة يومياً). يمكنك الإرسال مجدداً غداً."))
            }
            val nextCount = currentCount + 1
            val nowMs = getVerifiedNowMillis()
            val validHours = if (expiryHours >= 48) 48L else 24L
            val expiresAt = nowMs + validHours * 3_600_000L
            val imgExpiresAt = if (cleanImg.isNotEmpty()) nowMs + 8L * 3_600_000L else 0L
            val cleanThreadId = threadId.trim().ifBlank { "ROOM" }.take(140)

            // Rolling 50-message cap (max 25 per party) in this conversation
            runCatching {
                val threadSnap = if (cleanThreadId == "ROOM") {
                    db.collection("teacher_chat_messages")
                        .whereEqualTo("specialtyId", specialtyId)
                        .get()
                        .await()
                        .documents
                        .filter { (it.getString("threadId") ?: "ROOM") == "ROOM" }
                } else {
                    db.collection("teacher_chat_messages")
                        .whereEqualTo("threadId", cleanThreadId)
                        .get()
                        .await()
                        .documents
                }
                val sorted = threadSnap.sortedBy { it.getTimestamp("createdAt")?.seconds ?: 0L }.toMutableList()
                val mine = sorted.filter { it.getString("senderUid") == uid }.toMutableList()
                while (mine.size >= 25) {
                    val oldestMine = mine.removeAt(0)
                    sorted.removeAll { it.id == oldestMine.id }
                    runCatching { oldestMine.reference.delete().await() }
                }
                while (sorted.size >= 50) {
                    val oldestAny = sorted.removeAt(0)
                    runCatching { oldestAny.reference.delete().await() }
                }
            }

            val msgRef = db.collection("teacher_chat_messages").document()
            val batch = db.batch()
            batch.set(
                quotaRef,
                mapOf(
                    "dateKey" to today,
                    "count" to nextCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val payload = mutableMapOf<String, Any>(
                "threadId" to cleanThreadId,
                "senderUid" to uid,
                "senderName" to senderName.trim().ifBlank { "أستاذ" }.take(120),
                "senderUsername" to senderUsername.trim().take(60),
                "recipientAccountKey" to recipientAccountKey.trim().take(128),
                "recipientUid" to recipientUid.trim().take(128),
                "recipientName" to recipientName.trim().take(120),
                "specialtyId" to specialtyId.take(40),
                "gradeId" to gradeId.take(40),
                "messageText" to cleanText,
                "imageBase64" to cleanImg,
                "imageExpiresAtMillis" to imgExpiresAt,
                "editedAtMillis" to 0L,
                "deletedForAll" to false,
                "deletedForUids" to emptyList<String>(),
                "deliveredToUids" to listOf(uid),
                "readByUids" to listOf(uid),
                "expiresAtMillis" to expiresAt,
                "createdAt" to FieldValue.serverTimestamp()
            )
            batch.set(msgRef, payload)
            batch.commit().await()
            Result.success(50 - nextCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "teacher_chat_messages")
            Result.failure(e)
        }
    }

    suspend fun editChatMessage(msgId: String, newText: String): Result<Unit> {
        val clean = newText.trim()
        if (clean.isEmpty() || clean.length > 1000) {
            return Result.failure(IllegalArgumentException("أدخل نصاً لا يتجاوز 1000 حرف."))
        }
        val uid = requireUid()
        return try {
            val ref = db.collection("teacher_chat_messages").document(msgId)
            val snap = ref.get().await()
            val rec = TeacherChatMessageRecord.fromSnapshot(snap)
                ?: return Result.failure(IllegalStateException("الرسالة غير موجودة."))
            if (rec.senderUid != uid) {
                return Result.failure(IllegalStateException("لا يمكنك تعديل رسالة مستخدم آخر."))
            }
            ref.update(
                mapOf(
                    "messageText" to clean,
                    "editedAtMillis" to getVerifiedNowMillis()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "teacher_chat_messages/$msgId")
            Result.failure(e)
        }
    }

    suspend fun deleteChatMessageForMe(msgId: String): Result<Unit> {
        val uid = requireUid()
        return try {
            db.collection("teacher_chat_messages").document(msgId).update(
                "deletedForUids", FieldValue.arrayUnion(uid)
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "teacher_chat_messages/$msgId")
            Result.failure(e)
        }
    }

    suspend fun deleteChatMessageForEveryone(msg: TeacherChatMessageRecord): Result<Boolean> {
        val uid = requireUid()
        if (msg.senderUid != uid) {
            return Result.failure(IllegalStateException("فقط مرسل الرسالة يمكنه حذفها لدى الجميع."))
        }
        val isReadByPeer = msg.readByUids.any { it.isNotBlank() && it != uid }
        return try {
            val ref = db.collection("teacher_chat_messages").document(msg.id)
            if (isReadByPeer) {
                // Recipient already read it -> show deleted notice ("تم حذف رسالة")
                ref.update(
                    mapOf(
                        "deletedForAll" to true,
                        "messageText" to "تم حذف رسالة",
                        "imageBase64" to "",
                        "editedAtMillis" to getVerifiedNowMillis()
                    )
                ).await()
                Result.success(true)
            } else {
                // Recipient has NOT read it -> delete completely so it vanishes cleanly
                ref.delete().await()
                Result.success(false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "teacher_chat_messages/${msg.id}")
            Result.failure(e)
        }
    }

    suspend fun markMessagesDeliveredAndRead(messages: List<TeacherChatMessageRecord>, markRead: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val targets = messages.filter { msg ->
            msg.senderUid != uid && !msg.deletedForAll && (uid !in msg.deliveredToUids || (markRead && uid !in msg.readByUids))
        }.take(25)
        if (targets.isEmpty()) return
        runCatching {
            val batch = db.batch()
            targets.forEach { msg ->
                val ref = db.collection("teacher_chat_messages").document(msg.id)
                val map = mutableMapOf<String, Any>("deliveredToUids" to FieldValue.arrayUnion(uid))
                if (markRead) {
                    map["readByUids"] = FieldValue.arrayUnion(uid)
                }
                batch.update(ref, map)
            }
            batch.commit().await()
        }
    }

    suspend fun adminPurgeExpiredChatMessages(): Result<Int> {
        return try {
            requireUid()
            val nowMs = getVerifiedNowMillis()
            val snap = db.collection("teacher_chat_messages").get().await()
            var deleted = 0
            snap.documents.forEach { doc ->
                val exp = doc.getLong("expiresAtMillis") ?: 0L
                val imgExp = doc.getLong("imageExpiresAtMillis") ?: 0L
                if (exp in 1L..nowMs) {
                    doc.reference.delete().await()
                    deleted++
                } else if (imgExp in 1L..nowMs && !doc.getString("imageBase64").isNullOrBlank()) {
                    doc.reference.update("imageBase64", "").await()
                    deleted++
                }
            }
            Result.success(deleted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "teacher_chat_messages")
            Result.failure(e)
        }
    }

    // ==================== Name Change Requests & Approvals ====================

    suspend fun submitNameChangeRequest(
        accountKey: String,
        currentName: String,
        requestedName: String
    ): Result<Unit> {
        val cleanRequested = requestedName.trim().take(120)
        if (cleanRequested.length < 2) {
            return Result.failure(IllegalArgumentException("أدخل اسماً لا يقل عن حرفين."))
        }
        val uid = requireUid()
        val path = "name_change_requests"
        return try {
            db.collection(path).document().set(
                mapOf(
                    "requesterUid" to uid,
                    "accountKey" to accountKey.take(128),
                    "currentName" to currentName.trim().take(120),
                    "requestedName" to cleanRequested,
                    "status" to "PENDING",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminFetchNameChangeRequests(): Result<List<NameChangeRequestRecord>> {
        return try {
            requireUid()
            val snap = db.collection("name_change_requests").get().await()
            val list = snap.documents.mapNotNull { NameChangeRequestRecord.fromSnapshot(it) }
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "name_change_requests")
            Result.failure(e)
        }
    }

    suspend fun adminDecideNameChangeRequest(
        request: NameChangeRequestRecord,
        approve: Boolean
    ): Result<Unit> {
        val path = "name_change_requests/${request.id}"
        return try {
            requireUid()
            val nextStatus = if (approve) "APPROVED" else "REJECTED"
            db.collection("name_change_requests").document(request.id).update("status", nextStatus).await()
            if (approve && request.accountKey.isNotBlank()) {
                runCatching {
                    val accRef = db.collection(COL_TEACHER_ACCOUNTS).document(request.accountKey)
                    val accSnap = accRef.get().await()
                    val count = (accSnap.getLong("nameChangeCount") ?: 0L).toInt()
                    accRef.update(
                        mapOf(
                            "teacherName" to request.requestedName.trim().take(120),
                            "nameChangeCount" to count + 1,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // ==================== Cross-Subject Grade Comparison ====================

    suspend fun publishGradeComparison(
        creatorName: String,
        creatorSubject: String,
        creatorGradeTitle: String,
        creatorSectionScope: String,
        creatorStatsJson: String
    ): Result<GradeComparisonRecord> {
        val uid = requireUid()
        val code = BackupCryptoHelper.generateComparisonCode()
        val path = "grade_comparisons/$code"
        return try {
            db.collection("grade_comparisons").document(code).set(
                mapOf(
                    "creatorUid" to uid,
                    "creatorName" to creatorName.trim().take(120),
                    "creatorSubject" to creatorSubject.trim().take(80),
                    "creatorGradeTitle" to creatorGradeTitle.trim().take(60),
                    "creatorSectionScope" to creatorSectionScope.trim().take(60),
                    "creatorStatsJson" to creatorStatsJson.take(19500),
                    "partnerUid" to "",
                    "partnerName" to "",
                    "partnerSubject" to "",
                    "partnerGradeTitle" to "",
                    "partnerSectionScope" to "",
                    "partnerStatsJson" to "{}",
                    "status" to "OPEN",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(
                GradeComparisonRecord(
                    code = code,
                    creatorUid = uid,
                    creatorName = creatorName,
                    creatorSubject = creatorSubject,
                    creatorGradeTitle = creatorGradeTitle,
                    creatorSectionScope = creatorSectionScope,
                    creatorStatsJson = creatorStatsJson,
                    status = "OPEN"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinGradeComparisonByCode(
        codeRaw: String,
        partnerName: String,
        partnerSubject: String,
        partnerGradeTitle: String,
        partnerSectionScope: String,
        partnerStatsJson: String
    ): Result<GradeComparisonRecord> {
        val code = codeRaw.trim().uppercase(Locale.US)
        if (!code.matches(Regex("^[A-Z0-9_\\-]{4,32}$"))) {
            return Result.failure(IllegalArgumentException("صيغة كود المقارنة غير صالحة."))
        }
        val uid = requireUid()
        val path = "grade_comparisons/$code"
        return try {
            val ref = db.collection("grade_comparisons").document(code)
            val snap = ref.get().await()
            val existing = GradeComparisonRecord.fromSnapshot(snap)
                ?: return Result.failure(IllegalStateException("لا توجد مقارنة مسجلة بهذا الكود."))
            if (existing.creatorUid == uid) {
                return Result.failure(IllegalStateException("لا يمكنك إقران نفسك بنفس الكود؛ أرسل الكود إلى مدرس مادة أخرى."))
            }
            if (existing.status != "OPEN") {
                return Result.failure(IllegalStateException("هذا الكود مرتبط بمقارنة أخرى أو قيد المراجعة بالفعل."))
            }
            ref.update(
                mapOf(
                    "partnerUid" to uid,
                    "partnerName" to partnerName.trim().take(120),
                    "partnerSubject" to partnerSubject.trim().take(80),
                    "partnerGradeTitle" to partnerGradeTitle.trim().take(60),
                    "partnerSectionScope" to partnerSectionScope.trim().take(60),
                    "partnerStatsJson" to partnerStatsJson.take(19500),
                    "status" to "PENDING_APPROVAL",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val fresh = ref.get().await()
            Result.success(GradeComparisonRecord.fromSnapshot(fresh) ?: existing.copy(status = "PENDING_APPROVAL"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun fetchGradeComparisonByCode(codeRaw: String): Result<GradeComparisonRecord> {
        val code = codeRaw.trim().uppercase(Locale.US)
        return try {
            requireUid()
            val snap = db.collection("grade_comparisons").document(code).get().await()
            val rec = GradeComparisonRecord.fromSnapshot(snap)
                ?: return Result.failure(IllegalStateException("كود المقارنة غير موجود."))
            Result.success(rec)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "grade_comparisons/$code")
            Result.failure(e)
        }
    }

    fun observeMyGradeComparisons(uid: String): Flow<List<GradeComparisonRecord>> {
        if (uid.isBlank() || auth.currentUser?.uid != uid) return emptyFlow()
        return db.collection("grade_comparisons")
            .whereEqualTo("creatorUid", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { GradeComparisonRecord.fromSnapshot(it) }
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                emit(emptyList())
            }
    }

    fun observeJoinedGradeComparisons(uid: String): Flow<List<GradeComparisonRecord>> {
        if (uid.isBlank() || auth.currentUser?.uid != uid) return emptyFlow()
        return db.collection("grade_comparisons")
            .whereEqualTo("partnerUid", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { GradeComparisonRecord.fromSnapshot(it) }
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                emit(emptyList())
            }
    }

    suspend fun adminFetchAllGradeComparisons(): Result<List<GradeComparisonRecord>> {
        return try {
            requireUid()
            val snap = db.collection("grade_comparisons").get().await()
            val list = snap.documents.mapNotNull { GradeComparisonRecord.fromSnapshot(it) }
                .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "grade_comparisons")
            Result.failure(e)
        }
    }

    suspend fun adminDecideGradeComparison(code: String, approve: Boolean): Result<Unit> {
        val cleanCode = code.trim().uppercase(Locale.US)
        return try {
            requireUid()
            db.collection("grade_comparisons").document(cleanCode).update(
                mapOf(
                    "status" to if (approve) "APPROVED" else "REJECTED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "grade_comparisons/$cleanCode")
            Result.failure(e)
        }
    }

    suspend fun deleteGradeComparison(code: String): Result<Unit> {
        val cleanCode = code.trim().uppercase(Locale.US)
        return try {
            requireUid()
            db.collection("grade_comparisons").document(cleanCode).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "grade_comparisons/$cleanCode")
            Result.failure(e)
        }
    }

    // ==================== Shared Teacher PDF Study Guides ====================

    fun observeSharedStudyGuides(): Flow<List<SharedStudyGuideRecord>> {
        val uid = auth.currentUser?.uid ?: return emptyFlow()
        val path = COL_STUDY_GUIDES
        return db.collection(COL_STUDY_GUIDES)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { SharedStudyGuideRecord.fromSnapshot(it) }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                emit(emptyList())
            }
    }

    suspend fun uploadSharedPdfStudyGuide(
        title: String,
        subjectTitle: String,
        gradeId: String,
        teacherName: String,
        description: String,
        fileName: String,
        pdfBytes: ByteArray
    ): Result<SharedStudyGuideRecord> {
        val cleanTitle = title.trim().take(160)
        if (cleanTitle.length < 2) {
            return Result.failure(IllegalArgumentException("أدخل عنواناً واضحاً للملزمة (حرفين على الأقل)."))
        }
        if (!PdfValidationHelper.isValidPdfBytes(pdfBytes)) {
            return Result.failure(IllegalArgumentException("يُسمح فقط برفع ملفات PDF صالحة (تبدأ بتوقيع PDF)."))
        }
        if (!PdfValidationHelper.isAllowedFileSize(pdfBytes.size.toLong())) {
            return Result.failure(IllegalArgumentException("حجم ملف PDF يتجاوز الحد الأقصى المسموح (5 ميجابايت)."))
        }
        val uid = requireUid()
        val guideRef = db.collection(COL_STUDY_GUIDES).document()
        val guideId = guideRef.id
        val path = "$COL_STUDY_GUIDES/$guideId"

        val chunkSize = 480_000
        val chunkByteSlices = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < pdfBytes.size) {
            val end = minOf(offset + chunkSize, pdfBytes.size)
            chunkByteSlices.add(pdfBytes.copyOfRange(offset, end))
            offset = end
        }
        if (chunkByteSlices.size !in 1..15) {
            return Result.failure(IllegalArgumentException("تعذر تقسيم ملف PDF ضمن الحدود المسموحة."))
        }

        val safeFileName = fileName.trim().let { raw ->
            val withExt = if (raw.lowercase(Locale.US).endsWith(".pdf")) raw else "$raw.pdf"
            withExt.take(180).let { if (it.length < 5) "study_guide.pdf" else it }
        }
        val cleanSubject = subjectTitle.trim().ifBlank { "مادة عامة" }.take(80)
        val cleanTeacher = teacherName.trim().ifBlank { "أستاذ" }.take(120)
        val cleanDesc = description.trim().take(600)
        val cleanGrade = gradeId.trim().ifBlank { "ALL" }.take(40)

        return try {
            guideRef.set(
                mapOf(
                    "title" to cleanTitle,
                    "subjectTitle" to cleanSubject,
                    "gradeId" to cleanGrade,
                    "uploaderUid" to uid,
                    "teacherName" to cleanTeacher,
                    "description" to cleanDesc,
                    "fileName" to safeFileName,
                    "mimeType" to "application/pdf",
                    "fileSizeBytes" to pdfBytes.size,
                    "chunkCount" to chunkByteSlices.size,
                    "downloadCount" to 0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            chunkByteSlices.forEachIndexed { idx, slice ->
                val encoded = Base64.encodeToString(slice, Base64.NO_WRAP)
                guideRef.collection(SUBCOL_CHUNKS).document(idx.toString()).set(
                    mapOf(
                        "uploaderUid" to uid,
                        "chunkIndex" to idx,
                        "dataBase64" to encoded,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(
                SharedStudyGuideRecord(
                    id = guideId,
                    title = cleanTitle,
                    subjectTitle = cleanSubject,
                    gradeId = cleanGrade,
                    teacherName = cleanTeacher,
                    description = cleanDesc,
                    fileName = safeFileName,
                    fileSizeBytes = pdfBytes.size.toLong(),
                    mimeType = "application/pdf",
                    chunkCount = chunkByteSlices.size,
                    uploaderUid = uid,
                    downloadCount = 0
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun downloadSharedPdfToFile(
        guide: SharedStudyGuideRecord,
        destFile: File
    ): Result<File> {
        if (guide.id.isBlank()) {
            return Result.failure(IllegalArgumentException("معرف الملزمة غير صالح."))
        }
        val path = "$COL_STUDY_GUIDES/${guide.id}/$SUBCOL_CHUNKS"
        return try {
            requireUid()
            val snap = db.collection(COL_STUDY_GUIDES)
                .document(guide.id)
                .collection(SUBCOL_CHUNKS)
                .get()
                .await()
            val sortedDocs = snap.documents.sortedBy { doc ->
                (doc.getLong("chunkIndex") ?: doc.id.toLongOrNull() ?: 0L)
            }
            if (sortedDocs.isEmpty()) {
                return Result.failure(IllegalStateException("ملف الملزمة غير متوفر أو محذوف."))
            }
            val out = ByteArrayOutputStream()
            sortedDocs.forEach { doc ->
                val b64 = doc.getString("dataBase64").orEmpty()
                if (b64.isNotBlank()) {
                    out.write(Base64.decode(b64, Base64.NO_WRAP))
                }
            }
            val assembled = out.toByteArray()
            if (!PdfValidationHelper.isValidPdfBytes(assembled)) {
                return Result.failure(IllegalStateException("الملف المحمّل ليس بصيغة PDF صالحة."))
            }
            destFile.parentFile?.mkdirs()
            destFile.writeBytes(assembled)

            runCatching {
                db.collection(COL_STUDY_GUIDES).document(guide.id).update(
                    mapOf(
                        "downloadCount" to guide.downloadCount + 1,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(destFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSharedStudyGuide(guideId: String): Result<Unit> {
        val cleanId = guideId.trim()
        val path = "$COL_STUDY_GUIDES/$cleanId"
        return try {
            requireUid()
            val guideRef = db.collection(COL_STUDY_GUIDES).document(cleanId)
            val chunks = guideRef.collection(SUBCOL_CHUNKS).get().await()
            chunks.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            guideRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
