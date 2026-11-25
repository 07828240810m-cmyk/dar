package com.example.data

import androidx.annotation.Keep
import com.example.model.GradeLevel
import com.example.model.SubjectSpecialty
import com.example.ui.screens.ToolRoute
import com.example.ui.theme.SeasonalThemePreset
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class AnnouncementPriority(val raw: String, val titleAr: String) {
    NORMAL("NORMAL", "عادي"),
    IMPORTANT("IMPORTANT", "مهم"),
    URGENT("URGENT", "عاجل");

    companion object {
        fun fromRaw(value: String?): AnnouncementPriority {
            return entries.firstOrNull { it.raw.equals(value, ignoreCase = true) } ?: NORMAL
        }
    }
}

enum class NotificationTargetType(val raw: String, val titleAr: String) {
    ALL("ALL", "الجميع"),
    USER("USER", "مدرس محدد (اسم المستخدم / UID)"),
    GRADE("GRADE", "حسب المرحلة"),
    SUBJECT("SUBJECT", "حسب المادة / الاختصاص");

    companion object {
        fun fromRaw(value: String?): NotificationTargetType {
            return entries.firstOrNull { it.raw.equals(value, ignoreCase = true) } ?: ALL
        }
    }
}

fun targetMatchesTeacher(
    targetTypeRaw: String,
    targetValue: String,
    username: String,
    teacherName: String,
    uid: String,
    specialtyId: String,
    gradeId: String
): Boolean {
    val type = NotificationTargetType.fromRaw(targetTypeRaw)
    val cleanTarget = targetValue.trim()
    return when (type) {
        NotificationTargetType.ALL -> true
        NotificationTargetType.USER -> {
            if (cleanTarget.isEmpty()) true
            else {
                cleanTarget.equals(uid, ignoreCase = true) ||
                    (username.isNotBlank() && cleanTarget.equals(username.trim(), ignoreCase = true)) ||
                    (teacherName.isNotBlank() && teacherName.trim().contains(cleanTarget, ignoreCase = true))
            }
        }
        NotificationTargetType.GRADE -> {
            if (cleanTarget.isEmpty()) true
            else {
                val g = GradeLevel.entries.firstOrNull { it.id.equals(gradeId, ignoreCase = true) }
                cleanTarget.equals(gradeId, ignoreCase = true) ||
                    (g != null && (cleanTarget.equals(g.shortAr, ignoreCase = true) || cleanTarget.equals(g.titleAr, ignoreCase = true)))
            }
        }
        NotificationTargetType.SUBJECT -> {
            if (cleanTarget.isEmpty()) true
            else {
                val sp = SubjectSpecialty.entries.firstOrNull { it.id.equals(specialtyId, ignoreCase = true) }
                cleanTarget.equals(specialtyId, ignoreCase = true) ||
                    (sp != null && cleanTarget.equals(sp.titleAr, ignoreCase = true))
            }
        }
    }
}

@Keep
@IgnoreExtraProperties
data class AppReleaseInfo(
    @DocumentId val id: String = "",
    @get:PropertyName("latest_version_code") val latestVersionCode: Int = 1,
    @get:PropertyName("latest_version_name") val latestVersionName: String = "1.0",
    @get:PropertyName("release_notes") val releaseNotes: String = "",
    @get:PropertyName("force_update") val forceUpdate: Boolean = false,
    @get:PropertyName("apk_url") val apkUrl: String = "",
    @get:PropertyName("release_image_url") val releaseImageUrl: String = "",
    @get:PropertyName("release_image_base64") val releaseImageBase64: String = "",
    @get:PropertyName("updated_by") val updatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): AppReleaseInfo? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            val code = (data["latest_version_code"] as? Number)?.toInt() ?: 1
            val name = (data["latest_version_name"] as? String)?.trim().orEmpty().ifBlank { "1.0" }
            val notes = (data["release_notes"] as? String).orEmpty()
            val force = (data["force_update"] as? Boolean) ?: false
            val url = (data["apk_url"] as? String)?.trim().orEmpty()
            val relImgUrl = (data["release_image_url"] as? String)?.trim().orEmpty()
            val relImgB64 = (data["release_image_base64"] as? String)?.trim().orEmpty()
            val updatedBy = (data["updated_by"] as? String).orEmpty()
            return AppReleaseInfo(
                id = doc.id,
                latestVersionCode = code,
                latestVersionName = name,
                releaseNotes = notes,
                forceUpdate = force,
                apkUrl = url,
                releaseImageUrl = relImgUrl,
                releaseImageBase64 = relImgB64,
                updatedBy = updatedBy,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class AdminAnnouncement(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val priority: String = AnnouncementPriority.NORMAL.raw,
    val targetType: String = NotificationTargetType.ALL.raw,
    val targetValue: String = "",
    val active: Boolean = true,
    val authorUid: String = "",
    val authorName: String = "",
    val scheduledForMillis: Long = 0L,
    val iconBase64: String = "",
    @ServerTimestamp val sentAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun priorityLevel(): AnnouncementPriority = AnnouncementPriority.fromRaw(priority)

    fun targetTypeEnum(): NotificationTargetType = NotificationTargetType.fromRaw(targetType)

    fun isReadyToDisplay(nowMs: Long): Boolean = scheduledForMillis <= 0L || nowMs >= scheduledForMillis

    fun matchesTeacher(
        username: String,
        teacherName: String,
        uid: String,
        specialtyId: String,
        gradeId: String
    ): Boolean = targetMatchesTeacher(
        targetTypeRaw = targetType,
        targetValue = targetValue,
        username = username,
        teacherName = teacherName,
        uid = uid,
        specialtyId = specialtyId,
        gradeId = gradeId
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): AdminAnnouncement? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            return AdminAnnouncement(
                id = doc.id,
                title = (data["title"] as? String).orEmpty(),
                body = (data["body"] as? String).orEmpty(),
                priority = AnnouncementPriority.fromRaw(data["priority"] as? String).raw,
                targetType = NotificationTargetType.fromRaw(data["targetType"] as? String).raw,
                targetValue = (data["targetValue"] as? String).orEmpty(),
                active = (data["active"] as? Boolean) ?: true,
                authorUid = (data["authorUid"] as? String).orEmpty(),
                authorName = (data["authorName"] as? String).orEmpty(),
                scheduledForMillis = (data["scheduledForMillis"] as? Number)?.toLong() ?: 0L,
                iconBase64 = (data["iconBase64"] as? String).orEmpty(),
                sentAt = doc.getTimestamp("sentAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class UserNotificationStateRecord(
    @DocumentId val id: String = "",
    val notificationId: String = "",
    val read: Boolean = true,
    @ServerTimestamp val readAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UpdateCheckResult {
    data object Idle : UpdateCheckResult
    data object Checking : UpdateCheckResult
    data class UpdateAvailable(
        val release: AppReleaseInfo,
        val currentVersionCode: Int,
        val currentVersionName: String
    ) : UpdateCheckResult

    data class UpToDate(
        val currentVersionCode: Int,
        val currentVersionName: String
    ) : UpdateCheckResult

    data class Error(val message: String) : UpdateCheckResult
}

// ==================== Remote Control, Seasonal Themes & Maintenance ====================

data class RemoteControlConfig(
    val maintenanceEnabled: Boolean = false,
    val maintenanceMessage: String = "نعتذر عن توقف التطبيق مؤقتاً للصيانة والتطوير. نرجو منكم التحلي بالصبر، وسيعود للعمل قريباً.",
    val maintenanceEtaText: String = "",
    val disabledTools: List<String> = emptyList(),
    val pointsRequiredTools: Map<String, Int> = emptyMap(),
    val seasonalTheme: String = SeasonalThemePreset.NONE.id,
    val defaultAccessMode: String = "OPEN",
    val defaultTrialDays: Int = 7,
    val subscriptionsEnabled: Boolean = true,
    val allowWeeklyNameChange: Boolean = false,
    val inactivityReminderMessage: String = "أهلاً بك يا أستاذنا العزيز! غيابك عنا لفترة يشتاق لجهودك. نسأل الله أن يبارك في وقتك وجهدك.",
    val internalUpdateEnabled: Boolean = false,
    val internalUpdateRevision: Long = 0L,
    val internalUpdateTitle: String = "تحديث داخلي للنظام وقوائم الطلبة",
    val internalUpdateText: String = "تم تحديث محتوى المنصة وقوائم الطلبة حسب الصف والشعبة مع الحفاظ الكامل على الدرجات.",
    val internalUpdateDurationSec: Int = 13,
    val internalUpdateIncludeSchoolRoster: Boolean = false,
    val internalUpdateCustomRosterText: String = "",
    val experimentalLayoutEnabled: Boolean = false,
    val experimentalLayoutMode: String = "CLASSIC",
    val passwordChangeIntervalDays: Int = 3,
    val chatExpiryHours: Int = 24,
    val customPromotionTitles: List<String> = listOf("معلم متميز", "سفير رفيق المدرس", "نجم التفاعل"),
    val updatedBy: String = "",
    val updatedAt: Timestamp? = null
) {
    fun seasonalPreset(): SeasonalThemePreset = SeasonalThemePreset.fromId(seasonalTheme)

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): RemoteControlConfig {
            if (!doc.exists()) return RemoteControlConfig()
            val data = doc.data ?: return RemoteControlConfig()
            val disabled = (data["disabledTools"] as? List<*>)
                ?.mapNotNull { it as? String }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val rawCostMap = (data["pointsRequiredTools"] as? Map<*, *>) ?: emptyMap<Any?, Any?>()
            val costMap = mutableMapOf<String, Int>()
            rawCostMap.forEach { (k, v) ->
                val key = k as? String ?: return@forEach
                val cost = (v as? Number)?.toInt() ?: 0
                if (cost > 0) costMap[key] = cost
            }
            val rawTheme = (data["seasonalTheme"] as? String).orEmpty()
            val accessMode = (data["defaultAccessMode"] as? String)?.uppercase()?.let {
                if (it == "TRIAL") "TRIAL" else "OPEN"
            } ?: "OPEN"
            val trialDays = ((data["defaultTrialDays"] as? Number)?.toInt() ?: 7).coerceIn(1, 3650)
            val subEnabled = (data["subscriptionsEnabled"] as? Boolean) ?: true
            val weeklyName = (data["allowWeeklyNameChange"] as? Boolean) ?: false
            val inactivityMsg = (data["inactivityReminderMessage"] as? String).orEmpty().ifBlank {
                "أهلاً بك يا أستاذنا العزيز! غيابك عنا لفترة يشتاق لجهودك. نسأل الله أن يبارك في وقتك وجهدك."
            }
            val customRanks = (data["customPromotionTitles"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim() }
                ?.filter { it.isNotEmpty() }
                ?.take(20)
                ?: listOf("معلم متميز", "سفير رفيق المدرس", "نجم التفاعل")
            val chatHours = ((data["chatExpiryHours"] as? Number)?.toInt() ?: 24).let {
                if (it >= 48) 48 else 24
            }
            val rawLayoutMode = (data["experimentalLayoutMode"] as? String)?.uppercase()?.let { mode ->
                if (mode in setOf("CLASSIC", "MODERN_SPLIT", "COMPACT_CARDS", "FOCUS_STUDIO", "SPECIALIST_PRO")) mode else "CLASSIC"
            } ?: "CLASSIC"
            return RemoteControlConfig(
                maintenanceEnabled = (data["maintenanceEnabled"] as? Boolean) ?: false,
                maintenanceMessage = (data["maintenanceMessage"] as? String).orEmpty().ifBlank {
                    "نعتذر عن توقف التطبيق مؤقتاً للصيانة والتطوير. نرجو منكم التحلي بالصبر، وسيعود للعمل قريباً."
                },
                maintenanceEtaText = (data["maintenanceEtaText"] as? String).orEmpty(),
                disabledTools = disabled,
                pointsRequiredTools = costMap,
                seasonalTheme = SeasonalThemePreset.fromId(rawTheme).id,
                defaultAccessMode = accessMode,
                defaultTrialDays = trialDays,
                subscriptionsEnabled = subEnabled,
                allowWeeklyNameChange = weeklyName,
                inactivityReminderMessage = inactivityMsg,
                internalUpdateEnabled = (data["internalUpdateEnabled"] as? Boolean) ?: false,
                internalUpdateRevision = ((data["internalUpdateRevision"] as? Number)?.toLong() ?: 0L).coerceAtLeast(0L),
                internalUpdateTitle = (data["internalUpdateTitle"] as? String).orEmpty().ifBlank {
                    "تحديث داخلي للنظام وقوائم الطلبة"
                },
                internalUpdateText = (data["internalUpdateText"] as? String).orEmpty().ifBlank {
                    "تم تحديث محتوى المنصة وقوائم الطلبة حسب الصف والشعبة مع الحفاظ الكامل على الدرجات."
                },
                internalUpdateDurationSec = ((data["internalUpdateDurationSec"] as? Number)?.toInt() ?: 13).coerceIn(3, 30),
                internalUpdateIncludeSchoolRoster = (data["internalUpdateIncludeSchoolRoster"] as? Boolean) ?: false,
                internalUpdateCustomRosterText = (data["internalUpdateCustomRosterText"] as? String).orEmpty(),
                experimentalLayoutEnabled = (data["experimentalLayoutEnabled"] as? Boolean) ?: false,
                experimentalLayoutMode = rawLayoutMode,
                passwordChangeIntervalDays = ((data["passwordChangeIntervalDays"] as? Number)?.toInt() ?: 3).coerceIn(1, 90),
                chatExpiryHours = chatHours,
                customPromotionTitles = customRanks,
                updatedBy = (data["updatedBy"] as? String).orEmpty(),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class SharedStudyGuideRecord(
    @DocumentId val id: String = "",
    val title: String = "",
    val subjectTitle: String = "",
    val gradeId: String = "1",
    val teacherName: String = "",
    val description: String = "",
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val mimeType: String = "application/pdf",
    val chunkCount: Int = 1,
    val uploaderUid: String = "",
    val downloadCount: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): SharedStudyGuideRecord? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            return SharedStudyGuideRecord(
                id = doc.id,
                title = (data["title"] as? String).orEmpty(),
                subjectTitle = (data["subjectTitle"] as? String).orEmpty(),
                gradeId = (data["gradeId"] as? String).orEmpty().ifBlank { "1" },
                teacherName = (data["teacherName"] as? String).orEmpty(),
                description = (data["description"] as? String).orEmpty(),
                fileName = (data["fileName"] as? String).orEmpty(),
                fileSizeBytes = (data["fileSizeBytes"] as? Number)?.toLong() ?: 0L,
                mimeType = (data["mimeType"] as? String).orEmpty().ifBlank { "application/pdf" },
                chunkCount = ((data["chunkCount"] as? Number)?.toInt() ?: 1).coerceIn(1, 16),
                uploaderUid = (data["uploaderUid"] as? String).orEmpty(),
                downloadCount = ((data["downloadCount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

// ==================== Promotional Banner / In-App Ad ====================

data class PromoBannerConfig(
    val enabled: Boolean = false,
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val actionUrl: String = "",
    val phoneNumber: String = "",
    val buttonLabel: String = "زيارة الرابط",
    val placement: String = "HOME", // HOME, ALL_SCREENS, SPECIFIC_SECTION
    val targetRoute: String = "",
    val position: String = "TOP", // TOP, BOTTOM
    val updatedBy: String = "",
    val updatedAt: Timestamp? = null
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): PromoBannerConfig {
            if (!doc.exists()) return PromoBannerConfig()
            val data = doc.data ?: return PromoBannerConfig()
            val place = (data["placement"] as? String)?.uppercase()?.let {
                if (it in setOf("HOME", "ALL_SCREENS", "SPECIFIC_SECTION")) it else "HOME"
            } ?: "HOME"
            val pos = (data["position"] as? String)?.uppercase()?.let {
                if (it == "BOTTOM") "BOTTOM" else "TOP"
            } ?: "TOP"
            return PromoBannerConfig(
                enabled = (data["enabled"] as? Boolean) ?: false,
                title = (data["title"] as? String).orEmpty(),
                description = (data["description"] as? String).orEmpty(),
                imageUrl = (data["imageUrl"] as? String).orEmpty(),
                actionUrl = (data["actionUrl"] as? String).orEmpty(),
                phoneNumber = (data["phoneNumber"] as? String).orEmpty(),
                buttonLabel = (data["buttonLabel"] as? String).orEmpty().ifBlank { "زيارة الرابط" },
                placement = place,
                targetRoute = (data["targetRoute"] as? String).orEmpty(),
                position = pos,
                updatedBy = (data["updatedBy"] as? String).orEmpty(),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

// ==================== Live Polls / Voting ====================

data class CloudPoll(
    val id: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val targetType: String = NotificationTargetType.ALL.raw,
    val targetValue: String = "",
    val active: Boolean = true,
    val authorUid: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun matchesTeacher(
        username: String,
        teacherName: String,
        uid: String,
        specialtyId: String,
        gradeId: String
    ): Boolean = targetMatchesTeacher(
        targetTypeRaw = targetType,
        targetValue = targetValue,
        username = username,
        teacherName = teacherName,
        uid = uid,
        specialtyId = specialtyId,
        gradeId = gradeId
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): CloudPoll? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            val opts = (data["options"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            if (opts.size < 2) return null
            return CloudPoll(
                id = doc.id,
                question = (data["question"] as? String).orEmpty(),
                options = opts,
                targetType = NotificationTargetType.fromRaw(data["targetType"] as? String).raw,
                targetValue = (data["targetValue"] as? String).orEmpty(),
                active = (data["active"] as? Boolean) ?: true,
                authorUid = (data["authorUid"] as? String).orEmpty(),
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

data class PollVoteItem(
    val voterUid: String = "",
    val optionIndex: Int = 0,
    val votedAt: Timestamp? = null
)

// ==================== Teacher Cloud Accounts, Analytics, Referrals & Trial ====================

data class TeacherAccountRecord(
    val accountKey: String = "",
    val usernameDisplay: String = "",
    val ownerUid: String = "",
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val teacherName: String = "",
    val specialtyId: String = SubjectSpecialty.PHYSICS.id,
    val gradeId: String = GradeLevel.FIRST.id,
    val referralCode: String = "",
    val referredByCode: String = "",
    val points: Int = 0,
    val referralCount: Int = 0,
    val claimedReferralIds: List<String> = emptyList(),
    val accessMode: String = "OPEN", // OPEN, TRIAL, SUSPENDED
    val trialDays: Int = 7,
    val trialExpiresAtMillis: Long = 0L,
    val todayDateKey: String = "",
    val todayUsageSeconds: Long = 0L,
    val totalUsageSeconds: Long = 0L,
    val toolUsageCounts: Map<String, Long> = emptyMap(),
    val ratingStars: Int = 0,
    val ratingComment: String = "",
    val promotionTitle: String = "",
    val allowWeeklyNameChange: Boolean = false,
    val nameChangeCount: Int = 0,
    val boundDeviceId: String = "",
    val isOnlineVisible: Boolean = true,
    val lastPingAtMillis: Long = 0L,
    val lastActiveAt: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): TeacherAccountRecord? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            val claimed = (data["claimedReferralIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val rawTools = (data["toolUsageCounts"] as? Map<*, *>) ?: emptyMap<Any?, Any?>()
            val toolsMap = mutableMapOf<String, Long>()
            rawTools.forEach { (k, v) ->
                val key = k as? String ?: return@forEach
                val count = (v as? Number)?.toLong() ?: 0L
                if (count > 0L) toolsMap[key] = count
            }
            return TeacherAccountRecord(
                accountKey = (data["accountKey"] as? String).orEmpty().ifBlank { doc.id },
                usernameDisplay = (data["usernameDisplay"] as? String).orEmpty(),
                ownerUid = (data["ownerUid"] as? String).orEmpty(),
                passwordHash = (data["passwordHash"] as? String).orEmpty(),
                passwordSalt = (data["passwordSalt"] as? String).orEmpty(),
                teacherName = (data["teacherName"] as? String).orEmpty(),
                specialtyId = (data["specialtyId"] as? String).orEmpty().ifBlank { SubjectSpecialty.PHYSICS.id },
                gradeId = (data["gradeId"] as? String).orEmpty().ifBlank { GradeLevel.FIRST.id },
                referralCode = (data["referralCode"] as? String).orEmpty(),
                referredByCode = (data["referredByCode"] as? String).orEmpty(),
                points = (data["points"] as? Number)?.toInt() ?: 0,
                referralCount = (data["referralCount"] as? Number)?.toInt() ?: 0,
                claimedReferralIds = claimed,
                accessMode = (data["accessMode"] as? String).orEmpty().ifBlank { "OPEN" },
                trialDays = (data["trialDays"] as? Number)?.toInt() ?: 7,
                trialExpiresAtMillis = (data["trialExpiresAtMillis"] as? Number)?.toLong() ?: 0L,
                todayDateKey = (data["todayDateKey"] as? String).orEmpty(),
                todayUsageSeconds = (data["todayUsageSeconds"] as? Number)?.toLong() ?: 0L,
                totalUsageSeconds = (data["totalUsageSeconds"] as? Number)?.toLong() ?: 0L,
                toolUsageCounts = toolsMap,
                ratingStars = (data["ratingStars"] as? Number)?.toInt() ?: 0,
                ratingComment = (data["ratingComment"] as? String).orEmpty(),
                promotionTitle = (data["promotionTitle"] as? String).orEmpty(),
                allowWeeklyNameChange = (data["allowWeeklyNameChange"] as? Boolean) ?: false,
                nameChangeCount = (data["nameChangeCount"] as? Number)?.toInt() ?: 0,
                boundDeviceId = (data["boundDeviceId"] as? String).orEmpty(),
                isOnlineVisible = (data["isOnlineVisible"] as? Boolean) ?: true,
                lastPingAtMillis = (data["lastPingAtMillis"] as? Number)?.toLong() ?: 0L,
                lastActiveAt = doc.getTimestamp("lastActiveAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

data class TeacherEncryptedBackupRecord(
    val encryptedBlob: String = "",
    val checksumSha256: String = "",
    val ownerUid: String = "",
    val studentCount: Int = 0,
    val appVersionCode: Int = 1,
    val updatedAt: Timestamp? = null
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): TeacherEncryptedBackupRecord? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            val blob = (data["encryptedBlob"] as? String).orEmpty()
            if (blob.isBlank()) return null
            return TeacherEncryptedBackupRecord(
                encryptedBlob = blob,
                checksumSha256 = (data["checksumSha256"] as? String).orEmpty(),
                ownerUid = (data["ownerUid"] as? String).orEmpty(),
                studentCount = (data["studentCount"] as? Number)?.toInt() ?: 0,
                appVersionCode = (data["appVersionCode"] as? Number)?.toInt() ?: 1,
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

data class AdminGiftItem(
    val id: String = "",
    val amount: Int = 0,
    val message: String = "",
    val targetType: String = "ALL",
    val active: Boolean = true,
    val authorUid: String = "",
    val createdAt: Timestamp? = null
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): AdminGiftItem? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            return AdminGiftItem(
                id = doc.id,
                amount = (data["amount"] as? Number)?.toInt() ?: 0,
                message = (data["message"] as? String).orEmpty(),
                targetType = (data["targetType"] as? String).orEmpty().ifBlank { "ALL" },
                active = (data["active"] as? Boolean) ?: true,
                authorUid = (data["authorUid"] as? String).orEmpty(),
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

// ==================== Developer Feedback (Hidden Admin Name) ====================

enum class FeedbackCategory(val raw: String, val titleAr: String) {
    SUGGESTION("SUGGESTION", "اقتراح تطوير"),
    COMPLAINT("COMPLAINT", "شكوى / مشكلة"),
    BUG("BUG", "خطأ برمجي"),
    OTHER("OTHER", "رسالة عامة");

    companion object {
        fun fromRaw(raw: String?): FeedbackCategory =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: SUGGESTION
    }
}

data class DeveloperFeedbackRecord(
    val id: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val accountKey: String = "",
    val category: String = FeedbackCategory.SUGGESTION.raw,
    val message: String = "",
    val wordCount: Int = 0,
    val lockedThreeDays: Boolean = false,
    val lockedUntilMillis: Long = 0L,
    val status: String = "OPEN", // OPEN, REVIEWED
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): DeveloperFeedbackRecord? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            return DeveloperFeedbackRecord(
                id = doc.id,
                senderUid = (data["senderUid"] as? String).orEmpty(),
                senderName = (data["senderName"] as? String).orEmpty(),
                accountKey = (data["accountKey"] as? String).orEmpty(),
                category = FeedbackCategory.fromRaw(data["category"] as? String).raw,
                message = (data["message"] as? String).orEmpty(),
                wordCount = (data["wordCount"] as? Number)?.toInt() ?: 0,
                lockedThreeDays = (data["lockedThreeDays"] as? Boolean) ?: false,
                lockedUntilMillis = (data["lockedUntilMillis"] as? Number)?.toLong() ?: 0L,
                status = (data["status"] as? String).orEmpty().ifBlank { "OPEN" },
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

// ==================== Parent Read-Only Cloud Report ====================

data class ParentReportVisibility(
    val showTerm1: Boolean = true,
    val showMidYear: Boolean = true,
    val showTerm2: Boolean = true,
    val showAnnualEffort: Boolean = true,
    val showFinalGrade: Boolean = true,
    val showDailyPrep: Boolean = true,
    val showBehaviorAndAbsences: Boolean = true,
    val showBadgesAndNotes: Boolean = false
)

data class ParentReportRecord(
    val reportCode: String = "",
    val teacherUid: String = "",
    val teacherName: String = "",
    val subjectName: String = "",
    val studentName: String = "",
    val gradeTitle: String = "",
    val sectionId: String = "",
    val expiresAtMillis: Long = 0L,
    val deletedByAdmin: Boolean = false,
    val visibilityJson: String = "{}",
    val reportDataJson: String = "{}",
    val createdAt: Timestamp? = null
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): ParentReportRecord? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            return ParentReportRecord(
                reportCode = (data["reportCode"] as? String).orEmpty().ifBlank { doc.id },
                teacherUid = (data["teacherUid"] as? String).orEmpty(),
                teacherName = (data["teacherName"] as? String).orEmpty(),
                subjectName = (data["subjectName"] as? String).orEmpty(),
                studentName = (data["studentName"] as? String).orEmpty(),
                gradeTitle = (data["gradeTitle"] as? String).orEmpty(),
                sectionId = (data["sectionId"] as? String).orEmpty(),
                expiresAtMillis = (data["expiresAtMillis"] as? Number)?.toLong() ?: 0L,
                deletedByAdmin = (data["deletedByAdmin"] as? Boolean) ?: false,
                visibilityJson = (data["visibilityJson"] as? String).orEmpty().ifBlank { "{}" },
                reportDataJson = (data["reportDataJson"] as? String).orEmpty().ifBlank { "{}" },
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

// ==================== Trial & Analytics ====================

enum class TrialAccessStatus {
    OPEN,
    TRIAL_ACTIVE,
    TRIAL_EXPIRED,
    SUSPENDED
}

data class CloudAnalyticsSummary(
    val totalUsers: Int = 0,
    val activeTodayUsers: Int = 0,
    val totalTodayMinutes: Long = 0L,
    val totalAllTimeHours: Double = 0.0,
    val averageRating: Double = 0.0,
    val ratingsCount: Int = 0,
    val topTools: List<Pair<ToolRoute, Long>> = emptyList()
)

// ==================== Activation Codes & Sub-Admins & Teacher Chat & Comparisons ====================

data class AdminPermissions(
    val isFullAdmin: Boolean = false,
    val isSubAdmin: Boolean = false,
    val canManageUsers: Boolean = false,
    val canManageAnnouncements: Boolean = false,
    val canManageCodes: Boolean = false,
    val canViewAnalytics: Boolean = false,
    val canManageRemoteControl: Boolean = false,
    val canManageComparisons: Boolean = false
) {
    val hasAnyAdminAccess: Boolean
        get() = isFullAdmin || isSubAdmin
}

data class ActivationCodeRecord(
    @DocumentId val code: String = "",
    val assignedUsername: String = "",
    val assignedPasswordHash: String = "",
    val assignedPasswordSalt: String = "",
    val notes: String = "",
    val isUsed: Boolean = false,
    val usedByUid: String = "",
    val boundDeviceId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): ActivationCodeRecord? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            return ActivationCodeRecord(
                code = doc.id,
                assignedUsername = (data["assignedUsername"] as? String).orEmpty(),
                assignedPasswordHash = (data["assignedPasswordHash"] as? String).orEmpty(),
                assignedPasswordSalt = (data["assignedPasswordSalt"] as? String).orEmpty(),
                notes = (data["notes"] as? String).orEmpty(),
                isUsed = (data["isUsed"] as? Boolean) ?: false,
                usedByUid = (data["usedByUid"] as? String).orEmpty(),
                boundDeviceId = (data["boundDeviceId"] as? String).orEmpty(),
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

data class SubAdminRecord(
    @DocumentId val uid: String = "",
    val email: String = "",
    val name: String = "",
    val canManageUsers: Boolean = true,
    val canManageAnnouncements: Boolean = true,
    val canManageCodes: Boolean = true,
    val canViewAnalytics: Boolean = true,
    val canManageRemoteControl: Boolean = false,
    val canManageComparisons: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toPermissions(): AdminPermissions = AdminPermissions(
        isFullAdmin = false,
        isSubAdmin = true,
        canManageUsers = canManageUsers,
        canManageAnnouncements = canManageAnnouncements,
        canManageCodes = canManageCodes,
        canViewAnalytics = canViewAnalytics,
        canManageRemoteControl = canManageRemoteControl,
        canManageComparisons = canManageComparisons
    )

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): SubAdminRecord? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            return SubAdminRecord(
                uid = doc.id,
                email = (data["email"] as? String).orEmpty(),
                name = (data["name"] as? String).orEmpty(),
                canManageUsers = (data["canManageUsers"] as? Boolean) ?: true,
                canManageAnnouncements = (data["canManageAnnouncements"] as? Boolean) ?: true,
                canManageCodes = (data["canManageCodes"] as? Boolean) ?: true,
                canViewAnalytics = (data["canViewAnalytics"] as? Boolean) ?: true,
                canManageRemoteControl = (data["canManageRemoteControl"] as? Boolean) ?: false,
                canManageComparisons = (data["canManageComparisons"] as? Boolean) ?: true,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

data class TeacherChatMessageRecord(
    @DocumentId val id: String = "",
    val threadId: String = "ROOM",
    val senderUid: String = "",
    val senderName: String = "",
    val senderUsername: String = "",
    val recipientAccountKey: String = "",
    val recipientUid: String = "",
    val recipientName: String = "",
    val specialtyId: String = "",
    val gradeId: String = "",
    val messageText: String = "",
    val imageBase64: String = "",
    val imageExpiresAtMillis: Long = 0L,
    val editedAtMillis: Long = 0L,
    val deletedForAll: Boolean = false,
    val deletedForUids: List<String> = emptyList(),
    val deliveredToUids: List<String> = emptyList(),
    val readByUids: List<String> = emptyList(),
    val expiresAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun hasValidImage(nowMs: Long = System.currentTimeMillis()): Boolean =
        !deletedForAll && imageBase64.isNotBlank() && (imageExpiresAtMillis <= 0L || nowMs <= imageExpiresAtMillis)

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): TeacherChatMessageRecord? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            val expires = (data["expiresAtMillis"] as? Number)?.toLong() ?: 0L
            if (expires > 0L && System.currentTimeMillis() > expires) return null
            val deletedFor = (data["deletedForUids"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val deliveredTo = (data["deliveredToUids"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val readBy = (data["readByUids"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            return TeacherChatMessageRecord(
                id = doc.id,
                threadId = (data["threadId"] as? String).orEmpty().ifBlank { "ROOM" },
                senderUid = (data["senderUid"] as? String).orEmpty(),
                senderName = (data["senderName"] as? String).orEmpty(),
                senderUsername = (data["senderUsername"] as? String).orEmpty(),
                recipientAccountKey = (data["recipientAccountKey"] as? String).orEmpty(),
                recipientUid = (data["recipientUid"] as? String).orEmpty(),
                recipientName = (data["recipientName"] as? String).orEmpty(),
                specialtyId = (data["specialtyId"] as? String).orEmpty(),
                gradeId = (data["gradeId"] as? String).orEmpty(),
                messageText = (data["messageText"] as? String).orEmpty(),
                imageBase64 = (data["imageBase64"] as? String).orEmpty(),
                imageExpiresAtMillis = (data["imageExpiresAtMillis"] as? Number)?.toLong() ?: 0L,
                editedAtMillis = (data["editedAtMillis"] as? Number)?.toLong() ?: 0L,
                deletedForAll = (data["deletedForAll"] as? Boolean) ?: false,
                deletedForUids = deletedFor,
                deliveredToUids = deliveredTo,
                readByUids = readBy,
                expiresAtMillis = expires,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

data class NameChangeRequestRecord(
    @DocumentId val id: String = "",
    val requesterUid: String = "",
    val accountKey: String = "",
    val currentName: String = "",
    val requestedName: String = "",
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): NameChangeRequestRecord? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            return NameChangeRequestRecord(
                id = doc.id,
                requesterUid = (data["requesterUid"] as? String).orEmpty(),
                accountKey = (data["accountKey"] as? String).orEmpty(),
                currentName = (data["currentName"] as? String).orEmpty(),
                requestedName = (data["requestedName"] as? String).orEmpty(),
                status = (data["status"] as? String).orEmpty().ifBlank { "PENDING" },
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

data class GradeComparisonRecord(
    @DocumentId val code: String = "",
    val creatorUid: String = "",
    val creatorName: String = "",
    val creatorSubject: String = "",
    val creatorGradeTitle: String = "",
    val creatorSectionScope: String = "",
    val creatorStatsJson: String = "{}",
    val partnerUid: String = "",
    val partnerName: String = "",
    val partnerSubject: String = "",
    val partnerGradeTitle: String = "",
    val partnerSectionScope: String = "",
    val partnerStatsJson: String = "{}",
    val status: String = "OPEN", // OPEN, PENDING_APPROVAL, APPROVED, REJECTED
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): GradeComparisonRecord? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            return GradeComparisonRecord(
                code = doc.id,
                creatorUid = (data["creatorUid"] as? String).orEmpty(),
                creatorName = (data["creatorName"] as? String).orEmpty(),
                creatorSubject = (data["creatorSubject"] as? String).orEmpty(),
                creatorGradeTitle = (data["creatorGradeTitle"] as? String).orEmpty(),
                creatorSectionScope = (data["creatorSectionScope"] as? String).orEmpty(),
                creatorStatsJson = (data["creatorStatsJson"] as? String).orEmpty().ifBlank { "{}" },
                partnerUid = (data["partnerUid"] as? String).orEmpty(),
                partnerName = (data["partnerName"] as? String).orEmpty(),
                partnerSubject = (data["partnerSubject"] as? String).orEmpty(),
                partnerGradeTitle = (data["partnerGradeTitle"] as? String).orEmpty(),
                partnerSectionScope = (data["partnerSectionScope"] as? String).orEmpty(),
                partnerStatsJson = (data["partnerStatsJson"] as? String).orEmpty().ifBlank { "{}" },
                status = (data["status"] as? String).orEmpty().ifBlank { "OPEN" },
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

