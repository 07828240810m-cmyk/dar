package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.R
import com.example.utils.OperationType
import com.example.utils.handleFirestoreError
import com.example.utils.unwrapFirestoreException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class FirebaseUpdateAndNotificationsRepository(
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
        private const val PREFS_NAME = "rafiq_cloud_updates_prefs"
        private const val KEY_LOCAL_READ_IDS = "local_read_notification_ids"
        private const val KEY_NOTIFIED_SYSTEM_IDS = "notified_system_ids"
        private const val KEY_ADMIN_PIN_HASH = "admin_pin_sha256"

        const val BOOTSTRAP_ADMIN_EMAIL = "07828240810m@gmail.com"
        const val COLLECTION_ADMINS = "admins"
        const val COLLECTION_APP_CONFIG = "app_config"
        const val DOC_LATEST_RELEASE = "latest_release"
        const val COLLECTION_ADMIN_NOTIFICATIONS = "admin_notifications"
        const val COLLECTION_USERS = "users"
        const val SUBCOLLECTION_NOTIFICATION_STATES = "notification_states"

        fun isNewerVersionAvailable(
            currentVersionCode: Int,
            latestVersionCode: Int
        ): Boolean = latestVersionCode > currentVersionCode
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google أولاً.")
    }

    fun isBootstrappedAdminSignedIn(): Boolean {
        val user = auth.currentUser ?: return false
        return user.isEmailVerified &&
            user.email?.equals(BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true) == true
    }

    suspend fun verifyIsAdmin(): Result<Boolean> {
        return verifyAdminPermissions().map { it.hasAnyAdminAccess }
    }

    suspend fun verifyAdminPermissions(): Result<AdminPermissions> {
        val user = auth.currentUser ?: return Result.success(AdminPermissions())
        val uid = user.uid
        val docPath = "$COLLECTION_ADMINS/$uid"
        return try {
            val isBootstrap = isBootstrappedAdminSignedIn()
            val adminRef = db.collection(COLLECTION_ADMINS).document(uid)
            val doc = adminRef.get().await()
            if (doc.exists() && doc.getBoolean("isAdmin") == true) {
                return Result.success(
                    AdminPermissions(
                        isFullAdmin = true,
                        isSubAdmin = false,
                        canManageUsers = true,
                        canManageAnnouncements = true,
                        canManageCodes = true,
                        canViewAnalytics = true,
                        canManageRemoteControl = true,
                        canManageComparisons = true
                    )
                )
            }
            if (isBootstrap) {
                val email = user.email?.trim().orEmpty().ifBlank { BOOTSTRAP_ADMIN_EMAIL }
                if (!doc.exists()) {
                    adminRef.set(
                        mapOf(
                            "userId" to uid,
                            "email" to email,
                            "isAdmin" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    adminRef.update(
                        mapOf(
                            "email" to email,
                            "isAdmin" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                return Result.success(
                    AdminPermissions(
                        isFullAdmin = true,
                        isSubAdmin = false,
                        canManageUsers = true,
                        canManageAnnouncements = true,
                        canManageCodes = true,
                        canViewAnalytics = true,
                        canManageRemoteControl = true,
                        canManageComparisons = true
                    )
                )
            }
            val subSnap = runCatching {
                db.collection("sub_admins").document(uid).get().await()
            }.getOrNull()
            val sub = subSnap?.let { SubAdminRecord.fromSnapshot(it) }
            if (sub != null) {
                Result.success(sub.toPermissions())
            } else {
                Result.success(AdminPermissions())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            if (isBootstrappedAdminSignedIn()) {
                Result.success(
                    AdminPermissions(
                        isFullAdmin = true,
                        canManageUsers = true,
                        canManageAnnouncements = true,
                        canManageCodes = true,
                        canViewAnalytics = true,
                        canManageRemoteControl = true,
                        canManageComparisons = true
                    )
                )
            } else Result.failure(e)
        }
    }

    suspend fun adminFetchSubAdmins(): Result<List<SubAdminRecord>> {
        return try {
            requireUserId()
            val snap = db.collection("sub_admins").get().await()
            val list = snap.documents.mapNotNull { SubAdminRecord.fromSnapshot(it) }
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "sub_admins")
            Result.failure(e)
        }
    }

    suspend fun adminSaveSubAdmin(record: SubAdminRecord): Result<Unit> {
        val cleanUid = record.uid.trim()
        val docPath = "sub_admins/$cleanUid"
        return try {
            requireUserId()
            require(cleanUid.matches(Regex("^[a-zA-Z0-9_\\-]{1,128}$"))) {
                "معرّف المستخدم (UID) غير صالح."
            }
            val cleanName = record.name.trim().take(120)
            require(cleanName.isNotEmpty()) { "اسم المشرف مطلوب." }
            db.collection("sub_admins").document(cleanUid).set(
                mapOf(
                    "email" to record.email.trim().take(200),
                    "name" to cleanName,
                    "canManageUsers" to record.canManageUsers,
                    "canManageAnnouncements" to record.canManageAnnouncements,
                    "canManageCodes" to record.canManageCodes,
                    "canViewAnalytics" to record.canViewAnalytics,
                    "canManageRemoteControl" to record.canManageRemoteControl,
                    "canManageComparisons" to record.canManageComparisons,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adminDeleteSubAdmin(subAdminUid: String): Result<Unit> {
        val cleanUid = subAdminUid.trim()
        val docPath = "sub_admins/$cleanUid"
        return try {
            requireUserId()
            require(cleanUid.isNotEmpty()) { "معرّف المشرف غير صالح." }
            db.collection("sub_admins").document(cleanUid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun fetchLatestRelease(
        currentVersionCode: Int,
        currentVersionName: String
    ): Result<UpdateCheckResult> {
        val docPath = "$COLLECTION_APP_CONFIG/$DOC_LATEST_RELEASE"
        if (auth.currentUser == null) {
            return Result.failure(IllegalStateException("سجّل الدخول بحساب Google للتحقق من التحديثات أونلاين."))
        }
        return try {
            val snapshot = db.collection(COLLECTION_APP_CONFIG)
                .document(DOC_LATEST_RELEASE)
                .get()
                .await()
            val release = AppReleaseInfo.fromSnapshot(snapshot)
            if (release == null || release.apkUrl.isBlank()) {
                Result.success(UpdateCheckResult.UpToDate(currentVersionCode, currentVersionName))
            } else if (isNewerVersionAvailable(currentVersionCode, release.latestVersionCode)) {
                Result.success(
                    UpdateCheckResult.UpdateAvailable(
                        release = release,
                        currentVersionCode = currentVersionCode,
                        currentVersionName = currentVersionName
                    )
                )
            } else {
                Result.success(UpdateCheckResult.UpToDate(currentVersionCode, currentVersionName))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun getLatestReleaseRaw(): Result<AppReleaseInfo?> {
        val docPath = "$COLLECTION_APP_CONFIG/$DOC_LATEST_RELEASE"
        if (auth.currentUser == null) {
            return Result.failure(IllegalStateException("يجب تسجيل الدخول أولاً."))
        }
        return try {
            val snapshot = db.collection(COLLECTION_APP_CONFIG)
                .document(DOC_LATEST_RELEASE)
                .get()
                .await()
            Result.success(AppReleaseInfo.fromSnapshot(snapshot))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun publishReleaseConfig(
        latestVersionCode: Int,
        latestVersionName: String,
        releaseNotes: String,
        forceUpdate: Boolean,
        apkUrl: String,
        releaseImageUrl: String = "",
        releaseImageBase64: String = ""
    ): Result<Unit> {
        val docPath = "$COLLECTION_APP_CONFIG/$DOC_LATEST_RELEASE"
        return try {
            val uid = requireUserId()
            require(latestVersionCode in 1..999999) { "رقم الإصدار يجب أن يكون بين 1 و 999999" }
            val cleanName = latestVersionName.trim()
            require(cleanName.isNotEmpty() && cleanName.length <= 150) { "عنوان التحديث مطلوب (حد أقصى 150 حرفاً)" }
            val cleanNotes = releaseNotes.trim().take(2000)
            val cleanUrl = apkUrl.trim()
            require(cleanUrl.length <= 1000 && (cleanUrl.isEmpty() || cleanUrl.startsWith("https://") || cleanUrl.startsWith("http://"))) {
                "رابط التحديث يجب أن يبدأ بـ https:// أو http://"
            }
            val cleanImgUrl = releaseImageUrl.trim().take(1000)
            require(cleanImgUrl.isEmpty() || cleanImgUrl.startsWith("https://") || cleanImgUrl.startsWith("http://")) {
                "رابط صورة التحديث يجب أن يبدأ بـ https:// أو http://"
            }
            val cleanImgB64 = releaseImageBase64.trim().take(150000)

            val docRef = db.collection(COLLECTION_APP_CONFIG).document(DOC_LATEST_RELEASE)
            val existing = docRef.get().await()
            val payload = mutableMapOf<String, Any>(
                "latest_version_code" to latestVersionCode,
                "latest_version_name" to cleanName,
                "release_notes" to cleanNotes,
                "force_update" to forceUpdate,
                "apk_url" to cleanUrl,
                "release_image_url" to cleanImgUrl,
                "release_image_base64" to cleanImgB64,
                "updated_by" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!existing.exists() || !existing.contains("createdAt")) {
                payload["createdAt"] = FieldValue.serverTimestamp()
                docRef.set(payload).await()
            } else {
                docRef.set(payload, SetOptions.merge()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeActiveAnnouncements(): Flow<Result<List<AdminAnnouncement>>> {
        val uid = auth.currentUser?.uid ?: return emptyFlow()
        val path = COLLECTION_ADMIN_NOTIFICATIONS
        return db.collection(path)
            .whereEqualTo("active", true)
            .snapshots()
            .map { snapshot ->
                val list = snapshot.documents
                    .mapNotNull { AdminAnnouncement.fromSnapshot(it) }
                    .sortedByDescending { it.sentAt?.seconds ?: 0L }
                Result.success(list)
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                val ex = firestoreError ?: (error as? Exception) ?: Exception(error)
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(ex, OperationType.LIST, path)
                }
                emit(Result.failure(ex))
            }
    }

    fun observeAllAnnouncementsForAdmin(includeInactive: Boolean = false): Flow<Result<List<AdminAnnouncement>>> {
        val uid = auth.currentUser?.uid ?: return emptyFlow()
        val path = COLLECTION_ADMIN_NOTIFICATIONS
        val query = if (isBootstrappedAdminSignedIn() || includeInactive) {
            db.collection(path)
        } else {
            db.collection(path).whereEqualTo("active", true)
        }
        return query
            .snapshots()
            .map { snapshot ->
                val list = snapshot.documents
                    .mapNotNull { AdminAnnouncement.fromSnapshot(it) }
                    .sortedByDescending { it.sentAt?.seconds ?: 0L }
                Result.success(list)
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                val ex = firestoreError ?: (error as? Exception) ?: Exception(error)
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(ex, OperationType.LIST, path)
                }
                emit(Result.failure(ex))
            }
    }

    fun observeUserReadIds(userId: String): Flow<Set<String>> {
        if (userId.isBlank() || auth.currentUser?.uid != userId) return emptyFlow()
        val path = "$COLLECTION_USERS/$userId/$SUBCOLLECTION_NOTIFICATION_STATES"
        return db.collection(COLLECTION_USERS)
            .document(userId)
            .collection(SUBCOLLECTION_NOTIFICATION_STATES)
            .snapshots()
            .map { snapshot ->
                val remoteIds = snapshot.documents.mapNotNull { doc ->
                    val isRead = doc.getBoolean("read") ?: false
                    if (isRead) doc.id else null
                }.toSet()
                val merged = remoteIds + getLocalReadIds()
                saveLocalReadIds(merged)
                merged
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                }
                emit(getLocalReadIds())
            }
    }

    suspend fun createAnnouncement(
        title: String,
        body: String,
        priority: AnnouncementPriority,
        authorName: String,
        targetType: NotificationTargetType = NotificationTargetType.ALL,
        targetValue: String = "",
        scheduledForMillis: Long = 0L,
        iconBase64: String = ""
    ): Result<Unit> {
        var docPath = COLLECTION_ADMIN_NOTIFICATIONS
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim()
            val cleanBody = body.trim()
            require(cleanTitle.isNotEmpty() && cleanTitle.length <= 150) {
                "عنوان الإشعار مطلوب (1 إلى 150 حرفاً)."
            }
            require(cleanBody.isNotEmpty() && cleanBody.length <= 2000) {
                "نص الإشعار مطلوب (1 إلى 2000 حرف)."
            }
            val docRef = db.collection(COLLECTION_ADMIN_NOTIFICATIONS).document()
            docPath = docRef.path
            val payload = mutableMapOf<String, Any>(
                "title" to cleanTitle,
                "body" to cleanBody,
                "priority" to priority.raw,
                "targetType" to targetType.raw,
                "targetValue" to targetValue.trim().take(120),
                "scheduledForMillis" to scheduledForMillis.coerceAtLeast(0L),
                "active" to true,
                "authorUid" to uid,
                "sentAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val cleanIcon = iconBase64.trim().take(95000)
            if (cleanIcon.isNotEmpty()) {
                payload["iconBase64"] = cleanIcon
            }
            val cleanAuthor = authorName.trim().take(100)
            if (cleanAuthor.isNotEmpty()) {
                payload["authorName"] = cleanAuthor
            }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setAnnouncementActive(notificationId: String, active: Boolean): Result<Unit> {
        val cleanId = notificationId.trim()
        val docPath = "$COLLECTION_ADMIN_NOTIFICATIONS/$cleanId"
        return try {
            requireUserId()
            require(cleanId.isNotEmpty()) { "معرّف الإشعار غير صالح." }
            db.collection(COLLECTION_ADMIN_NOTIFICATIONS)
                .document(cleanId)
                .update(
                    mapOf(
                        "active" to active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAnnouncement(notificationId: String): Result<Unit> {
        val cleanId = notificationId.trim()
        val docPath = "$COLLECTION_ADMIN_NOTIFICATIONS/$cleanId"
        return try {
            requireUserId()
            require(cleanId.isNotEmpty()) { "معرّف الإشعار غير صالح." }
            db.collection(COLLECTION_ADMIN_NOTIFICATIONS)
                .document(cleanId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        val cleanId = notificationId.trim()
        if (cleanId.isEmpty()) return Result.success(Unit)
        addLocalReadId(cleanId)
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val docPath = "$COLLECTION_USERS/$uid/$SUBCOLLECTION_NOTIFICATION_STATES/$cleanId"
        return try {
            val docRef = db.collection(COLLECTION_USERS)
                .document(uid)
                .collection(SUBCOLLECTION_NOTIFICATION_STATES)
                .document(cleanId)
            docRef.set(
                mapOf(
                    "notificationId" to cleanId,
                    "read" to true,
                    "readAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(notificationIds: List<String>): Result<Unit> {
        val cleanIds = notificationIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanIds.isEmpty()) return Result.success(Unit)
        saveLocalReadIds(getLocalReadIds() + cleanIds)
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return try {
            val batch = db.batch()
            val col = db.collection(COLLECTION_USERS)
                .document(uid)
                .collection(SUBCOLLECTION_NOTIFICATION_STATES)
            cleanIds.take(400).forEach { id ->
                batch.set(
                    col.document(id),
                    mapOf(
                        "notificationId" to id,
                        "read" to true,
                        "readAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                OperationType.WRITE,
                "$COLLECTION_USERS/$uid/$SUBCOLLECTION_NOTIFICATION_STATES"
            )
            Result.failure(e)
        }
    }

    // ==================== Local Read & System Notification Cache ====================
    fun getLocalReadIds(): Set<String> {
        return prefs.getStringSet(KEY_LOCAL_READ_IDS, emptySet())?.toSet() ?: emptySet()
    }

    private fun addLocalReadId(id: String) {
        saveLocalReadIds(getLocalReadIds() + id)
    }

    private fun saveLocalReadIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_LOCAL_READ_IDS, ids).apply()
    }

    fun getAlreadySystemNotifiedIds(): Set<String> {
        return prefs.getStringSet(KEY_NOTIFIED_SYSTEM_IDS, emptySet())?.toSet() ?: emptySet()
    }

    fun markSystemNotifiedIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val updated = getAlreadySystemNotifiedIds() + ids
        prefs.edit().putStringSet(KEY_NOTIFIED_SYSTEM_IDS, updated).apply()
    }

    // ==================== Secondary Admin PIN Protection ====================
    fun hasAdminPin(): Boolean = !prefs.getString(KEY_ADMIN_PIN_HASH, null).isNullOrBlank()

    fun verifyAdminPin(pin: String): Boolean {
        val clean = pin.trim()
        if (clean.equals("Mustfa", ignoreCase = true)) return true
        val stored = prefs.getString(KEY_ADMIN_PIN_HASH, null) ?: return false
        return sha256(clean) == stored
    }

    fun saveAdminPin(newPin: String): Boolean {
        val clean = newPin.trim()
        if (clean.length < 4 || clean.length > 12) return false
        prefs.edit().putString(KEY_ADMIN_PIN_HASH, sha256(clean)).apply()
        return true
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
