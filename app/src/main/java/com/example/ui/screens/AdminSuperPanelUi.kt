package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.data.ActivationCodeRecord
import com.example.data.AdminAnnouncement
import com.example.data.AdminPermissions
import com.example.data.AnnouncementPriority
import com.example.data.AppReleaseInfo
import com.example.data.CloudAnalyticsSummary
import com.example.data.CloudPoll
import com.example.data.DeveloperFeedbackRecord
import com.example.data.FeedbackCategory
import com.example.data.GradeComparisonRecord
import com.example.data.NameChangeRequestRecord
import com.example.data.NotificationTargetType
import com.example.data.ParentReportRecord
import com.example.data.PromoBannerConfig
import com.example.data.RemoteControlConfig
import com.example.data.SubAdminRecord
import com.example.data.TeacherAccountRecord
import com.example.model.GradeLevel
import com.example.model.SubjectSpecialty
import com.example.ui.theme.DangerRed
import com.example.ui.theme.GoldBright
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.theme.SeasonalThemePreset
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.utils.GoogleSignInArabicButton
import com.google.firebase.auth.FirebaseUser
import java.io.ByteArrayOutputStream

private fun copyAdminText(context: Context, label: String, text: String) {
    if (text.isBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "تم نسخ $label ✓", Toast.LENGTH_SHORT).show()
}

private fun encodeUriToBase64(context: Context, uri: Uri, asPng: Boolean, maxDim: Int, maxChars: Int): String? {
    return runCatching {
        val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val scale = minOf(1f, maxDim.toFloat() / maxOf(raw.width, raw.height).coerceAtLeast(1))
        val targetW = (raw.width * scale).toInt().coerceAtLeast(1)
        val targetH = (raw.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(raw, targetW, targetH, true) else raw
        if (asPng) {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (b64.length <= maxChars) return b64
            // Fallback smaller PNG if needed
            val smaller = Bitmap.createScaledBitmap(scaled, (targetW * 0.65f).toInt().coerceAtLeast(32), (targetH * 0.65f).toInt().coerceAtLeast(32), true)
            val out2 = ByteArrayOutputStream()
            smaller.compress(Bitmap.CompressFormat.PNG, 100, out2)
            val b64Small = Base64.encodeToString(out2.toByteArray(), Base64.NO_WRAP)
            return b64Small.takeIf { it.length <= maxChars }
        } else {
            for (q in listOf(80, 62, 46, 30)) {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
                val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                if (b64.length <= maxChars) return b64
            }
            null
        }
    }.getOrNull()
}

private enum class AdminTabKind(val titleAr: String) {
    NOTIFICATIONS("الإشعارات"),
    POLLS("التصويت"),
    REMOTE("الأدوات والصيانة"),
    PROMO_RELEASE("الإعلان والتحديث"),
    USERS("المستخدمون"),
    ACTIVATION_CODES("أكواد التفعيل"),
    REQUESTS_COMPARISONS("الطلبات والمقارنات"),
    SUB_ADMINS("المشرفون"),
    ANALYTICS_LOGS("التحليل والرسائل")
}

@Composable
fun AdminSuperPanelDialog(
    currentUser: FirebaseUser?,
    isAdmin: Boolean,
    adminPermissions: AdminPermissions = AdminPermissions(isFullAdmin = true),
    adminCheckLoading: Boolean,
    hasAdminPin: Boolean,
    adminPinUnlocked: Boolean,
    adminAnnouncements: List<AdminAnnouncement>,
    remoteRelease: AppReleaseInfo?,
    remoteControl: RemoteControlConfig,
    promoBanner: PromoBannerConfig,
    polls: List<CloudPoll>,
    pollTallies: Map<String, List<Int>>,
    allUsers: List<TeacherAccountRecord>,
    allFeedback: List<DeveloperFeedbackRecord>,
    allParentReports: List<ParentReportRecord>,
    subAdmins: List<SubAdminRecord> = emptyList(),
    activationCodes: List<ActivationCodeRecord> = emptyList(),
    nameChangeRequests: List<NameChangeRequestRecord> = emptyList(),
    gradeComparisons: List<GradeComparisonRecord> = emptyList(),
    analytics: CloudAnalyticsSummary,
    actionMessage: String?,
    defaultAuthorName: String,
    onRetryAdminCheck: () -> Unit,
    onRefreshDashboardData: () -> Unit,
    onSignOut: () -> Unit,
    onSetupAdminPin: (String) -> Boolean,
    onUnlockWithPin: (String) -> Boolean,
    onPublishAnnouncement: (String, String, AnnouncementPriority, String, NotificationTargetType, String, Long, String) -> Unit,
    onToggleAnnouncementActive: (String, Boolean) -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
    onPublishPoll: (String, List<String>, NotificationTargetType, String) -> Unit,
    onTogglePollActive: (String, Boolean) -> Unit,
    onDeletePoll: (String) -> Unit,
    onLoadPollTallies: (CloudPoll) -> Unit,
    onSaveRemoteControl: (RemoteControlConfig) -> Unit,
    onPublishInternalUpdateNow: (RemoteControlConfig) -> Unit = {},
    onPublishExperimentalLayoutNow: (RemoteControlConfig) -> Unit = {},
    adminLocalExperimentalMode: String = "CLASSIC",
    onSetAdminLocalExperimentalMode: (String) -> Unit = {},
    onSavePromoBanner: (PromoBannerConfig) -> Unit,
    onPublishRelease: (Int, String, String, Boolean, String, String, String) -> Unit,
    onUpdateUserExtended: (accountKey: String, points: Int, accessMode: String, trialDays: Int, promotionTitle: String, allowWeeklyNameChange: Boolean) -> Unit,
    onDeleteUserAccount: (String) -> Unit,
    onUnbindUserDevice: (String) -> Unit,
    onSendPrivateNotification: (targetAccountKeyOrUid: String, title: String, body: String) -> Unit,
    onSendGroupGift: (amount: Int, message: String) -> Unit,
    onSaveSubAdmin: (SubAdminRecord) -> Unit,
    onDeleteSubAdmin: (String) -> Unit,
    onCreateActivationCode: (customCode: String, assignedUsername: String, assignedPasswordPlain: String, notes: String) -> Unit,
    onResetActivationCodeDevice: (String) -> Unit,
    onDeleteActivationCode: (String) -> Unit,
    onDecideNameRequest: (NameChangeRequestRecord, Boolean) -> Unit,
    onDecideComparison: (String, Boolean) -> Unit,
    onDeleteComparison: (String) -> Unit,
    onAutoPromoteTopActive: (String) -> Unit,
    onPurgeExpiredChats: () -> Unit,
    onToggleFeedbackReviewed: (String, Boolean) -> Unit,
    onDeleteParentReport: (String) -> Unit,
    onClearActionMessage: () -> Unit,
    onDismiss: () -> Unit
) {
    val ext = LocalExtendedColors.current
    var authError by remember { mutableStateOf<String?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            shadowElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 10.dp)
                .widthIn(max = 640.dp)
                .testTag("admin_super_panel_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                "لوحة تحكم المطور الشاملة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "إدارة الإشعارات، التصويت، الأدوات، الصيانة، المستخدمين والتحليلات",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (adminPinUnlocked && isAdmin) {
                        IconButton(onClick = onRefreshDashboardData) {
                            Icon(Icons.Filled.Refresh, contentDescription = "تحديث البيانات")
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                when {
                    currentUser == null -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("مطلوب التحقق من حساب المطور / الأدمن", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(14.dp))
                            GoogleSignInArabicButton(
                                label = "دخول عبر Google",
                                onAuthSuccess = {
                                    authError = null
                                    onRetryAdminCheck()
                                },
                                onAuthError = { authError = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                            authError?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    adminCheckLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator()
                                Text("جاري التحقق من الصلاحية في Firebase...")
                            }
                        }
                    }

                    !isAdmin -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.GppBad, contentDescription = null, tint = DangerRed, modifier = Modifier.size(54.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("صلاحية الأدمن غير متوفرة لهذا الحساب", color = DangerRed, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(currentUser.email ?: currentUser.uid, style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = onSignOut, modifier = Modifier.weight(1f)) { Text("تبديل الحساب") }
                                Button(onClick = onRetryAdminCheck, modifier = Modifier.weight(1f)) { Text("إعادة التحقق") }
                            }
                        }
                    }

                    !adminPinUnlocked -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (hasAdminPin) "أدخل رمز الحماية الخاص بلوحة التحكم" else "أنشئ رمز حماية لحماية لوحة التحكم",
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = {
                                    pinInput = it.take(24)
                                    pinError = null
                                },
                                label = { Text("رمز الحماية / PIN") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth().testTag("admin_pin_input")
                            )
                            pinError?.let { Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (!hasAdminPin) {
                                        if (!onSetupAdminPin(pinInput)) pinError = "أدخل 4 أحرف أو أرقام على الأقل."
                                    } else if (!onUnlockWithPin(pinInput)) {
                                        pinError = "رمز الحماية غير صحيح."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("admin_pin_unlock_btn")
                            ) {
                                Text(if (hasAdminPin) "فتح اللوحة" else "حفظ الرمز وفتح اللوحة", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    else -> {
                        val visibleTabs = remember(adminPermissions) {
                            buildList {
                                if (adminPermissions.isFullAdmin || adminPermissions.canManageAnnouncements) {
                                    add(AdminTabKind.NOTIFICATIONS)
                                    add(AdminTabKind.POLLS)
                                }
                                if (adminPermissions.isFullAdmin || adminPermissions.canManageRemoteControl) {
                                    add(AdminTabKind.REMOTE)
                                    add(AdminTabKind.PROMO_RELEASE)
                                }
                                if (adminPermissions.isFullAdmin || adminPermissions.canManageUsers) {
                                    add(AdminTabKind.USERS)
                                }
                                if (adminPermissions.isFullAdmin || adminPermissions.canManageCodes) {
                                    add(AdminTabKind.ACTIVATION_CODES)
                                }
                                if (adminPermissions.isFullAdmin || adminPermissions.canManageUsers || adminPermissions.canManageComparisons) {
                                    add(AdminTabKind.REQUESTS_COMPARISONS)
                                }
                                if (adminPermissions.isFullAdmin) {
                                    add(AdminTabKind.SUB_ADMINS)
                                }
                                if (adminPermissions.isFullAdmin || adminPermissions.canViewAnalytics) {
                                    add(AdminTabKind.ANALYTICS_LOGS)
                                }
                                if (isEmpty()) add(AdminTabKind.NOTIFICATIONS)
                            }
                        }
                        val safeTabIdx = selectedTab.coerceIn(0, visibleTabs.lastIndex)
                        ScrollableTabRow(selectedTabIndex = safeTabIdx, edgePadding = 2.dp) {
                            visibleTabs.forEachIndexed { idx, kind ->
                                Tab(
                                    selected = safeTabIdx == idx,
                                    onClick = { selectedTab = idx },
                                    text = { Text(kind.titleAr, maxLines = 1) }
                                )
                            }
                        }

                        actionMessage?.let { msg ->
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    IconButton(onClick = onClearActionMessage, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        when (visibleTabs[safeTabIdx]) {
                            AdminTabKind.NOTIFICATIONS -> AdminNotificationsTab(
                                currentUser = currentUser,
                                defaultAuthorName = defaultAuthorName,
                                adminAnnouncements = adminAnnouncements,
                                onPublish = onPublishAnnouncement,
                                onToggleActive = onToggleAnnouncementActive,
                                onDelete = onDeleteAnnouncement
                            )
                            AdminTabKind.POLLS -> AdminPollsTab(
                                polls = polls,
                                pollTallies = pollTallies,
                                onPublishPoll = onPublishPoll,
                                onTogglePollActive = onTogglePollActive,
                                onDeletePoll = onDeletePoll,
                                onLoadTallies = onLoadPollTallies
                            )
                            AdminTabKind.REMOTE -> AdminRemoteToolsAndMaintenanceTab(
                                remoteControl = remoteControl,
                                adminLocalExperimentalMode = adminLocalExperimentalMode,
                                onSaveRemoteControl = onSaveRemoteControl,
                                onPublishInternalUpdateNow = onPublishInternalUpdateNow,
                                onPublishExperimentalLayoutNow = onPublishExperimentalLayoutNow,
                                onSetAdminLocalExperimentalMode = onSetAdminLocalExperimentalMode
                            )
                            AdminTabKind.PROMO_RELEASE -> AdminPromoAndReleaseTab(
                                promoBanner = promoBanner,
                                remoteRelease = remoteRelease,
                                onSavePromoBanner = onSavePromoBanner,
                                onPublishRelease = onPublishRelease
                            )
                            AdminTabKind.USERS -> AdminUsersAndGiftsTab(
                                users = allUsers,
                                availableRanks = remoteControl.customPromotionTitles,
                                onUpdateUser = onUpdateUserExtended,
                                onDeleteUser = onDeleteUserAccount,
                                onUnbindUserDevice = onUnbindUserDevice,
                                onSendPrivateNotification = onSendPrivateNotification,
                                onSendGroupGift = onSendGroupGift
                            )
                            AdminTabKind.ACTIVATION_CODES -> AdminActivationCodesTab(
                                codes = activationCodes,
                                onCreateCode = onCreateActivationCode,
                                onResetDevice = onResetActivationCodeDevice,
                                onDeleteCode = onDeleteActivationCode
                            )
                            AdminTabKind.REQUESTS_COMPARISONS -> AdminRequestsAndComparisonsTab(
                                nameRequests = nameChangeRequests,
                                comparisons = gradeComparisons,
                                onDecideNameRequest = onDecideNameRequest,
                                onDecideComparison = onDecideComparison,
                                onDeleteComparison = onDeleteComparison
                            )
                            AdminTabKind.SUB_ADMINS -> AdminSubAdminsTab(
                                subAdmins = subAdmins,
                                onSaveSubAdmin = onSaveSubAdmin,
                                onDeleteSubAdmin = onDeleteSubAdmin
                            )
                            AdminTabKind.ANALYTICS_LOGS -> AdminAnalyticsAndLogsTab(
                                analytics = analytics,
                                users = allUsers,
                                feedbackList = allFeedback,
                                parentReports = allParentReports,
                                availableRanks = remoteControl.customPromotionTitles,
                                onAutoPromoteTopActive = onAutoPromoteTopActive,
                                onPurgeExpiredChats = onPurgeExpiredChats,
                                onToggleFeedbackReviewed = onToggleFeedbackReviewed,
                                onDeleteParentReport = onDeleteParentReport
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetSelectorBlock(
    targetType: NotificationTargetType,
    targetValue: String,
    onTypeChange: (NotificationTargetType) -> Unit,
    onValueChange: (String) -> Unit
) {
    Text("الجمهور المستهدف:", style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        NotificationTargetType.entries.forEach { t ->
            FilterChip(
                selected = targetType == t,
                onClick = { onTypeChange(t) },
                label = { Text(t.titleAr) }
            )
        }
    }
    when (targetType) {
        NotificationTargetType.ALL -> Unit
        NotificationTargetType.USER -> {
            OutlinedTextField(
                value = targetValue,
                onValueChange = { onValueChange(it.take(120)) },
                label = { Text("اسم المستخدم أو اسم المدرس أو UID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        NotificationTargetType.GRADE -> {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GradeLevel.entries.forEach { g ->
                    FilterChip(
                        selected = targetValue.equals(g.id, ignoreCase = true),
                        onClick = { onValueChange(g.id) },
                        label = { Text(g.shortAr) }
                    )
                }
            }
        }
        NotificationTargetType.SUBJECT -> {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SubjectSpecialty.entries.forEach { sp ->
                    FilterChip(
                        selected = targetValue.equals(sp.id, ignoreCase = true),
                        onClick = { onValueChange(sp.id) },
                        label = { Text(sp.titleAr) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminNotificationsTab(
    currentUser: FirebaseUser,
    defaultAuthorName: String,
    adminAnnouncements: List<AdminAnnouncement>,
    onPublish: (String, String, AnnouncementPriority, String, NotificationTargetType, String, Long, String) -> Unit,
    onToggleActive: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    val ext = LocalExtendedColors.current
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(AnnouncementPriority.IMPORTANT) }
    var targetType by remember { mutableStateOf(NotificationTargetType.ALL) }
    var targetValue by remember { mutableStateOf("") }
    var scheduleMinutesOffset by remember { mutableStateOf(0L) }
    var customAmountText by remember { mutableStateOf("") }
    var customUnit by remember { mutableStateOf("SEC") } // SEC, MIN, HOUR, DAY
    var iconBase64 by remember { mutableStateOf("") }

    val iconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = encodeUriToBase64(context, uri, asPng = true, maxDim = 160, maxChars = 95_000)
            if (encoded != null) {
                iconBase64 = encoded
            } else {
                Toast.makeText(context, "حجم الأيقونة كبير جداً، اختر صورة PNG أصغر.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val customAmount = customAmountText.toLongOrNull() ?: 0L
    val computedScheduleMs = remember(scheduleMinutesOffset, customAmount, customUnit) {
        if (customAmount > 0L) {
            val unitMs = when (customUnit) {
                "SEC" -> 1_000L
                "MIN" -> 60_000L
                "HOUR" -> 3_600_000L
                else -> 86_400_000L
            }
            System.currentTimeMillis() + maxOf(1_000L, customAmount * unitMs)
        } else if (scheduleMinutesOffset > 0L) {
            System.currentTimeMillis() + scheduleMinutesOffset * 60_000L
        } else 0L
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ext.cardElevated,
                border = BorderStroke(1.dp, ext.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إرسال إشعار موجّه أو مجدول", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(150) },
                        label = { Text("عنوان الإشعار") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_notif_title_input")
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it.take(2000) },
                        label = { Text("نص الإشعار") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("admin_notif_body_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AnnouncementPriority.entries.forEach { p ->
                            FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.titleAr) })
                        }
                    }
                    TargetSelectorBlock(
                        targetType = targetType,
                        targetValue = targetValue,
                        onTypeChange = { targetType = it; targetValue = "" },
                        onValueChange = { targetValue = it }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                iconPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f).testTag("admin_notif_pick_icon_btn")
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (iconBase64.isNotBlank()) "تغيير أيقونة PNG" else "إرفاق أيقونة PNG")
                        }
                        if (iconBase64.isNotBlank()) {
                            val bmp = remember(iconBase64) {
                                runCatching {
                                    val b = Base64.decode(iconBase64, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(b, 0, b.size)
                                }.getOrNull()
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "أيقونة الإشعار",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                                )
                            }
                            IconButton(onClick = { iconBase64 = "" }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف الأيقونة", tint = DangerRed)
                            }
                        }
                    }

                    Text("توقيت النشر (الجدولة التلقائية):", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            0L to "فوراً",
                            30L to "بعد 30 د",
                            60L to "بعد ساعة",
                            360L to "بعد 6 ساعات",
                            1440L to "بعد يوم",
                            4320L to "بعد 3 أيام"
                        ).forEach { (mins, lbl) ->
                            FilterChip(
                                selected = customAmount == 0L && scheduleMinutesOffset == mins,
                                onClick = {
                                    scheduleMinutesOffset = mins
                                    customAmountText = ""
                                },
                                label = { Text(lbl) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customAmountText,
                            onValueChange = { customAmountText = it.filter(Char::isDigit).take(4) },
                            label = { Text("توقيت مخصص") },
                            placeholder = { Text("مثال: 10") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(115.dp).testTag("admin_notif_custom_sched_input")
                        )
                        listOf(
                            "SEC" to "ثانية",
                            "MIN" to "دقيقة",
                            "HOUR" to "ساعة",
                            "DAY" to "يوم"
                        ).forEach { (unitId, unitTitle) ->
                            FilterChip(
                                selected = customUnit == unitId,
                                onClick = { customUnit = unitId },
                                label = { Text(unitTitle) }
                            )
                        }
                    }

                    Button(
                        enabled = title.isNotBlank() && body.isNotBlank(),
                        onClick = {
                            onPublish(
                                title,
                                body,
                                priority,
                                defaultAuthorName.ifBlank { currentUser.displayName ?: "الإدارة" },
                                targetType,
                                targetValue,
                                computedScheduleMs,
                                iconBase64
                            )
                            title = ""
                            body = ""
                            scheduleMinutesOffset = 0L
                            customAmountText = ""
                            iconBase64 = ""
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("admin_send_notif_btn")
                    ) {
                        Icon(
                            imageVector = if (computedScheduleMs > 0L) Icons.Filled.ScheduleSend else Icons.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (computedScheduleMs > 0L) "جدولة الإشعار وإرساله بالموعد" else "نشر الإشعار الآن",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        items(adminAnnouncements, key = { it.id }) { item ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ext.cardElevated,
                border = BorderStroke(1.dp, ext.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Bold)
                        val schedTag = if (item.scheduledForMillis > System.currentTimeMillis()) " • مجدول" else ""
                        Text("${item.priorityLevel().titleAr} • ${item.targetTypeEnum().titleAr} ${item.targetValue}$schedTag", style = MaterialTheme.typography.labelSmall, color = ext.textMuted)
                        Text(item.body, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Switch(checked = item.active, onCheckedChange = { onToggleActive(item.id, it) })
                    IconButton(onClick = { onDelete(item.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = DangerRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminPollsTab(
    polls: List<CloudPoll>,
    pollTallies: Map<String, List<Int>>,
    onPublishPoll: (String, List<String>, NotificationTargetType, String) -> Unit,
    onTogglePollActive: (String, Boolean) -> Unit,
    onDeletePoll: (String) -> Unit,
    onLoadTallies: (CloudPoll) -> Unit
) {
    val ext = LocalExtendedColors.current
    var question by remember { mutableStateOf("") }
    var opt1 by remember { mutableStateOf("") }
    var opt2 by remember { mutableStateOf("") }
    var opt3 by remember { mutableStateOf("") }
    var opt4 by remember { mutableStateOf("") }
    var targetType by remember { mutableStateOf(NotificationTargetType.ALL) }
    var targetValue by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ext.cardElevated,
                border = BorderStroke(1.dp, ext.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إنشاء استطلاع رأي وتصويت حي", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = question, onValueChange = { question = it.take(300) }, label = { Text("سؤال التصويت") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = opt1, onValueChange = { opt1 = it.take(80) }, label = { Text("الخيار الأول (مطلوب)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = opt2, onValueChange = { opt2 = it.take(80) }, label = { Text("الخيار الثاني (مطلوب)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = opt3, onValueChange = { opt3 = it.take(80) }, label = { Text("الخيار الثالث (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = opt4, onValueChange = { opt4 = it.take(80) }, label = { Text("الخيار الرابع (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TargetSelectorBlock(targetType, targetValue, { targetType = it; targetValue = "" }, { targetValue = it })
                    Button(
                        enabled = question.trim().length >= 2 && opt1.isNotBlank() && opt2.isNotBlank(),
                        onClick = {
                            onPublishPoll(question, listOf(opt1, opt2, opt3, opt4).filter { it.isNotBlank() }, targetType, targetValue)
                            question = ""; opt1 = ""; opt2 = ""; opt3 = ""; opt4 = ""
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("نشر التصويت للجميع", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        items(polls, key = { it.id }) { poll ->
            val counts = pollTallies[poll.id]
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ext.cardElevated,
                border = BorderStroke(1.dp, ext.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(poll.question, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(checked = poll.active, onCheckedChange = { onTogglePollActive(poll.id, it) })
                        IconButton(onClick = { onDeletePoll(poll.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = DangerRed)
                        }
                    }
                    poll.options.forEachIndexed { idx, opt ->
                        val c = counts?.getOrNull(idx)
                        Text("• $opt ${if (c != null) "($c صوت)" else ""}", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { onLoadTallies(poll) }) {
                        Text("عرض النتائج الحية")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminRemoteToolsAndMaintenanceTab(
    remoteControl: RemoteControlConfig,
    adminLocalExperimentalMode: String,
    onSaveRemoteControl: (RemoteControlConfig) -> Unit,
    onPublishInternalUpdateNow: (RemoteControlConfig) -> Unit,
    onPublishExperimentalLayoutNow: (RemoteControlConfig) -> Unit,
    onSetAdminLocalExperimentalMode: (String) -> Unit
) {
    val ext = LocalExtendedColors.current
    var maintenanceEnabled by remember(remoteControl) { mutableStateOf(remoteControl.maintenanceEnabled) }
    var maintenanceMessage by remember(remoteControl) { mutableStateOf(remoteControl.maintenanceMessage) }
    var maintenanceEta by remember(remoteControl) { mutableStateOf(remoteControl.maintenanceEtaText) }
    var disabledTools by remember(remoteControl) { mutableStateOf(remoteControl.disabledTools.toSet()) }
    var pointsTools by remember(remoteControl) { mutableStateOf(remoteControl.pointsRequiredTools) }
    var seasonalTheme by remember(remoteControl) { mutableStateOf(remoteControl.seasonalTheme) }
    var accessMode by remember(remoteControl) { mutableStateOf(remoteControl.defaultAccessMode) }
    var trialDaysText by remember(remoteControl) { mutableStateOf(remoteControl.defaultTrialDays.toString()) }
    var subscriptionsEnabled by remember(remoteControl) { mutableStateOf(remoteControl.subscriptionsEnabled) }
    var allowWeeklyNameChange by remember(remoteControl) { mutableStateOf(remoteControl.allowWeeklyNameChange) }
    var passwordIntervalText by remember(remoteControl) { mutableStateOf(remoteControl.passwordChangeIntervalDays.toString()) }
    var chatExpiryHours by remember(remoteControl) { mutableStateOf(remoteControl.chatExpiryHours) }
    var inactivityMsg by remember(remoteControl) { mutableStateOf(remoteControl.inactivityReminderMessage) }
    var internalUpdateEnabled by remember(remoteControl) { mutableStateOf(remoteControl.internalUpdateEnabled) }
    var internalUpdateTitle by remember(remoteControl) { mutableStateOf(remoteControl.internalUpdateTitle) }
    var internalUpdateText by remember(remoteControl) { mutableStateOf(remoteControl.internalUpdateText) }
    var internalUpdateDurationText by remember(remoteControl) { mutableStateOf(remoteControl.internalUpdateDurationSec.toString()) }
    var internalIncludeSchoolRoster by remember(remoteControl) { mutableStateOf(remoteControl.internalUpdateIncludeSchoolRoster) }
    var internalCustomRosterText by remember(remoteControl) { mutableStateOf(remoteControl.internalUpdateCustomRosterText) }
    var experimentalLayoutEnabled by remember(remoteControl) { mutableStateOf(remoteControl.experimentalLayoutEnabled) }
    var experimentalLayoutMode by remember(remoteControl) { mutableStateOf(remoteControl.experimentalLayoutMode) }
    var customRanksText by remember(remoteControl) { mutableStateOf(remoteControl.customPromotionTitles.joinToString("، ")) }

    fun buildUpdatedConfig(): RemoteControlConfig {
        val parsedRanks = customRanksText
            .split("،", ",", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("معلم متميز") }
        return RemoteControlConfig(
            maintenanceEnabled = maintenanceEnabled,
            maintenanceMessage = maintenanceMessage,
            maintenanceEtaText = maintenanceEta,
            disabledTools = disabledTools.toList(),
            pointsRequiredTools = pointsTools,
            seasonalTheme = seasonalTheme,
            defaultAccessMode = accessMode,
            defaultTrialDays = (trialDaysText.toIntOrNull() ?: 7).coerceIn(1, 3650),
            subscriptionsEnabled = subscriptionsEnabled,
            allowWeeklyNameChange = allowWeeklyNameChange,
            inactivityReminderMessage = inactivityMsg,
            internalUpdateEnabled = internalUpdateEnabled,
            internalUpdateRevision = remoteControl.internalUpdateRevision,
            internalUpdateTitle = internalUpdateTitle,
            internalUpdateText = internalUpdateText,
            internalUpdateDurationSec = (internalUpdateDurationText.toIntOrNull() ?: 13).coerceIn(3, 30),
            internalUpdateIncludeSchoolRoster = internalIncludeSchoolRoster,
            internalUpdateCustomRosterText = internalCustomRosterText,
            experimentalLayoutEnabled = experimentalLayoutEnabled,
            experimentalLayoutMode = experimentalLayoutMode,
            passwordChangeIntervalDays = (passwordIntervalText.toIntOrNull() ?: 3).coerceIn(1, 90),
            chatExpiryHours = chatExpiryHours,
            customPromotionTitles = parsedRanks
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Maintenance Mode Lock
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = DangerRed.copy(alpha = 0.08f),
            border = BorderStroke(1.2.dp, DangerRed.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("إغلاق التطبيق كاملاً (وضع الصيانة)", fontWeight = FontWeight.Bold, color = DangerRed)
                    Switch(checked = maintenanceEnabled, onCheckedChange = { maintenanceEnabled = it })
                }
                OutlinedTextField(
                    value = maintenanceMessage,
                    onValueChange = { maintenanceMessage = it.take(1000) },
                    label = { Text("رسالة الاعتذار والتحلي بالصبر") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = maintenanceEta,
                    onValueChange = { maintenanceEta = it.take(120) },
                    label = { Text("الوقت المتوقع للعودة (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Subscriptions + Password Cooldown + Weekly Name Change + Chat Expiry
        Surface(shape = RoundedCornerShape(14.dp), color = ext.cardElevated, border = BorderStroke(1.dp, ext.borderSubtle)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("تفعيل نظام الاشتراكات والتجربة", fontWeight = FontWeight.Bold)
                    Switch(checked = subscriptionsEnabled, onCheckedChange = { subscriptionsEnabled = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("السماح بتغيير الاسم أسبوعياً للجميع", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = allowWeeklyNameChange, onCheckedChange = { allowWeeklyNameChange = it })
                }
                if (subscriptionsEnabled) {
                    Text("سياسة الوصول الافتراضية للحسابات الجديدة:", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = accessMode == "OPEN", onClick = { accessMode = "OPEN" }, label = { Text("مفتوح دائماً") })
                        FilterChip(selected = accessMode == "TRIAL", onClick = { accessMode = "TRIAL" }, label = { Text("فترة تجريبية محددة") })
                    }
                    if (accessMode == "TRIAL") {
                        OutlinedTextField(
                            value = trialDaysText,
                            onValueChange = { trialDaysText = it.filter(Char::isDigit).take(4) },
                            label = { Text("عدد أيام الفترة التجريبية") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = passwordIntervalText,
                        onValueChange = { passwordIntervalText = it.filter(Char::isDigit).take(2) },
                        label = { Text("فترة تغيير المرور (أيام)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(selected = chatExpiryHours == 24, onClick = { chatExpiryHours = 24 }, label = { Text("دردشة 24س") })
                        FilterChip(selected = chatExpiryHours == 48, onClick = { chatExpiryHours = 48 }, label = { Text("48س") })
                    }
                }
                OutlinedTextField(
                    value = inactivityMsg,
                    onValueChange = { inactivityMsg = it.take(500) },
                    label = { Text("رسالة/دعاء الترحيب بعد غياب 7 أيام") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customRanksText,
                    onValueChange = { customRanksText = it.take(300) },
                    label = { Text("ألقاب الترقية المخصصة (افصل بينها بفاصلة)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Internal 13-Second Update + Non-Destructive Student Roster Push
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("التحديث الداخلي المباشر وشريط الـ 13 ثانية", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "ينشر نافذة (تحديث) لجميع المدرسين مع شريط تقدم جميل يدمج قوائم الطلبة دون مسح الدرجات",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.textMuted
                        )
                    }
                    Switch(checked = internalUpdateEnabled, onCheckedChange = { internalUpdateEnabled = it })
                }
                OutlinedTextField(
                    value = internalUpdateTitle,
                    onValueChange = { internalUpdateTitle = it.take(180) },
                    label = { Text("عنوان نافذة التحديث الداخلي") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = internalUpdateText,
                    onValueChange = { internalUpdateText = it.take(400) },
                    label = { Text("وصف التحديث ونص الشريط المتحرك") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = internalUpdateDurationText,
                    onValueChange = { internalUpdateDurationText = it.filter(Char::isDigit).take(2) },
                    label = { Text("مدة التحديث بالثواني (الافتراضي 13 ثانية)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("إرفاق قوائم طلبة المدرسة (516 طالب - 2026/2027)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "تُضاف الأسماء الجديدة حسب الصف والشعبة فقط، وتُحفظ الدرجات السابقة بالكامل",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen
                        )
                    }
                    Switch(
                        checked = internalIncludeSchoolRoster,
                        onCheckedChange = { internalIncludeSchoolRoster = it },
                        modifier = Modifier.testTag("admin_include_school_roster_switch")
                    )
                }
                OutlinedTextField(
                    value = internalCustomRosterText,
                    onValueChange = { internalCustomRosterText = it.take(50000) },
                    label = { Text("أسماء إضافية اختيارية (صف\\tاسم\\tشعبة في كل سطر)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            internalUpdateEnabled = true
                            onPublishInternalUpdateNow(buildUpdatedConfig())
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("admin_publish_internal_update_now_btn")
                    ) {
                        Icon(Icons.Filled.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("نشر التحديث الداخلي الآن", fontWeight = FontWeight.Bold)
                    }
                    if (internalUpdateEnabled) {
                        OutlinedButton(
                            onClick = {
                                internalUpdateEnabled = false
                                onSaveRemoteControl(buildUpdatedConfig())
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("إيقاف")
                        }
                    }
                }
            }
        }

        // Experimental System Restructuring (Local Admin Preview or Publish to All Teachers)
        Surface(shape = RoundedCornerShape(14.dp), color = ext.cardElevated, border = BorderStroke(1.dp, ext.borderSubtle)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("إعادة هيكلة النظام والتصميم التجريبي", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "جرّب تصميماً جديداً للواجهة على جهازك أولاً أو انشره للجميع (ويمكن لكل مدرس العودة للافتراضي بأي وقت).",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    com.example.utils.ExperimentalLayoutMode.entries.forEach { mode ->
                        FilterChip(
                            selected = experimentalLayoutMode == mode.id,
                            onClick = { experimentalLayoutMode = mode.id },
                            label = { Text(mode.titleAr) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نشر التصميم التجريبي لجميع المدرسين:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Switch(
                        checked = experimentalLayoutEnabled,
                        onCheckedChange = { experimentalLayoutEnabled = it },
                        modifier = Modifier.testTag("admin_publish_experimental_layout_switch")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { onSetAdminLocalExperimentalMode(experimentalLayoutMode) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("admin_try_local_layout_btn")
                    ) {
                        Text(
                            if (adminLocalExperimentalMode == experimentalLayoutMode && experimentalLayoutMode != "CLASSIC") {
                                "قيد التجربة المحلية ✓"
                            } else {
                                "تجربة محلية للمدير فقط"
                            },
                            maxLines = 1
                        )
                    }
                    if (adminLocalExperimentalMode != "CLASSIC") {
                        TextButton(
                            onClick = { onSetAdminLocalExperimentalMode("CLASSIC") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text("إلغاء المحلي") }
                    }
                }
                Button(
                    onClick = {
                        experimentalLayoutEnabled = true
                        onPublishExperimentalLayoutNow(buildUpdatedConfig().copy(experimentalLayoutEnabled = true))
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("admin_publish_experimental_layout_now_btn")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("نشر التصميم التجريبي للجميع الآن", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Seasonal Theme Switcher
        Text("تفعيل ثيم موسمي مؤقت للجميع:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SeasonalThemePreset.entries.forEach { preset ->
                FilterChip(
                    selected = seasonalTheme == preset.id,
                    onClick = { seasonalTheme = preset.id },
                    label = { Text("${preset.emoji} ${preset.titleAr}".trim()) }
                )
            }
        }

        // Per-Tool Disable & Points Cost Switcher
        Text("تشغيل/إطفاء الأدوات عن بُعد وتحديد تكلفة النقاط:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        ToolRoute.entries.filter { it != ToolRoute.SETTINGS }.forEach { tool ->
            val isDisabled = tool.name in disabledTools
            val currentCost = pointsTools[tool.name] ?: 0
            Surface(shape = RoundedCornerShape(12.dp), color = ext.cardElevated, border = BorderStroke(1.dp, ext.borderSubtle)) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(tool.icon, contentDescription = null, tint = tool.accentColor, modifier = Modifier.size(18.dp))
                            Text(tool.shortTitleAr, fontWeight = FontWeight.SemiBold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (isDisabled) "مطفأة" else "تعمل", style = MaterialTheme.typography.labelSmall, color = if (isDisabled) DangerRed else SuccessGreen)
                            Switch(
                                checked = !isDisabled,
                                onCheckedChange = { on ->
                                    disabledTools = if (on) disabledTools - tool.name else disabledTools + tool.name
                                }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("نقاط:", style = MaterialTheme.typography.labelSmall)
                        listOf(0, 25, 50, 100).forEach { c ->
                            FilterChip(
                                selected = currentCost == c,
                                onClick = {
                                    pointsTools = if (c == 0) pointsTools - tool.name else pointsTools + (tool.name to c)
                                },
                                label = { Text(if (c == 0) "مجاني" else "$c") }
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = { onSaveRemoteControl(buildUpdatedConfig()) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("admin_save_remote_control_btn")
        ) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("حفظ وتطبيق التحكم عن بُعد", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AdminPromoAndReleaseTab(
    promoBanner: PromoBannerConfig,
    remoteRelease: AppReleaseInfo?,
    onSavePromoBanner: (PromoBannerConfig) -> Unit,
    onPublishRelease: (Int, String, String, Boolean, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var promoEnabled by remember(promoBanner) { mutableStateOf(promoBanner.enabled) }
    var promoTitle by remember(promoBanner) { mutableStateOf(promoBanner.title) }
    var promoDesc by remember(promoBanner) { mutableStateOf(promoBanner.description) }
    var promoImg by remember(promoBanner) { mutableStateOf(promoBanner.imageUrl) }
    var promoUrl by remember(promoBanner) { mutableStateOf(promoBanner.actionUrl) }
    var promoPhone by remember(promoBanner) { mutableStateOf(promoBanner.phoneNumber) }
    var promoBtn by remember(promoBanner) { mutableStateOf(promoBanner.buttonLabel) }
    var promoPlacement by remember(promoBanner) { mutableStateOf(promoBanner.placement) }
    var promoTargetRoute by remember(promoBanner) { mutableStateOf(promoBanner.targetRoute) }
    var promoPosition by remember(promoBanner) { mutableStateOf(promoBanner.position) }

    var updateTitle by remember(remoteRelease) {
        mutableStateOf(
            remoteRelease?.latestVersionName?.takeIf {
                it.isNotBlank() && it != "1.0" && it != "1.1" && it != "تم الإيقاف"
            }.orEmpty()
        )
    }
    var updateUrl by remember(remoteRelease) { mutableStateOf(remoteRelease?.apkUrl.orEmpty()) }
    var notes by remember(remoteRelease) { mutableStateOf(remoteRelease?.releaseNotes.orEmpty()) }
    var releaseImageUrl by remember(remoteRelease) { mutableStateOf(remoteRelease?.releaseImageUrl.orEmpty()) }
    var releaseImageBase64 by remember(remoteRelease) { mutableStateOf(remoteRelease?.releaseImageBase64.orEmpty()) }

    val releaseImgPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = encodeUriToBase64(context, uri, asPng = false, maxDim = 800, maxChars = 150_000)
            if (encoded != null) {
                releaseImageBase64 = encoded
            } else {
                Toast.makeText(context, "حجم الصورة كبير جداً، اختر صورة أصغر.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("إعداد الإعلان الترويجي داخل التطبيق:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("تفعيل ظهور الإعلان للمستخدمين")
            Switch(checked = promoEnabled, onCheckedChange = { promoEnabled = it })
        }
        OutlinedTextField(value = promoTitle, onValueChange = { promoTitle = it.take(150) }, label = { Text("عنوان الإعلان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = promoDesc, onValueChange = { promoDesc = it.take(2000) }, label = { Text("وصف الإعلان") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = promoImg, onValueChange = { promoImg = it.take(1000) }, label = { Text("رابط الصورة (HTTPS اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = promoUrl, onValueChange = { promoUrl = it.take(1000) }, label = { Text("رابط التحويل عند النقر") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = promoPhone, onValueChange = { promoPhone = it.take(40) }, label = { Text("رقم هاتف للتواصل") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = promoBtn, onValueChange = { promoBtn = it.take(60) }, label = { Text("نص الزر") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Text("مكان الظهور:", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("HOME" to "الرئيسية", "ALL_SCREENS" to "كل الأقسام", "SPECIFIC_SECTION" to "قسم محدد").forEach { (id, label) ->
                FilterChip(selected = promoPlacement == id, onClick = { promoPlacement = id }, label = { Text(label) })
            }
        }
        if (promoPlacement == "SPECIFIC_SECTION") {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolRoute.entries.forEach { r ->
                    FilterChip(selected = promoTargetRoute == r.name, onClick = { promoTargetRoute = r.name }, label = { Text(r.shortTitleAr) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = promoPosition == "TOP", onClick = { promoPosition = "TOP" }, label = { Text("أعلى الشاشة") })
            FilterChip(selected = promoPosition == "BOTTOM", onClick = { promoPosition = "BOTTOM" }, label = { Text("أسفل الشاشة") })
        }
        Button(
            onClick = {
                onSavePromoBanner(
                    PromoBannerConfig(
                        enabled = promoEnabled,
                        title = promoTitle,
                        description = promoDesc,
                        imageUrl = promoImg,
                        actionUrl = promoUrl,
                        phoneNumber = promoPhone,
                        buttonLabel = promoBtn,
                        placement = promoPlacement,
                        targetRoute = promoTargetRoute,
                        position = promoPosition
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text("حفظ ونشر الإعلان", fontWeight = FontWeight.Bold)
        }

        HorizontalDivider()
        Text("إرسال رابط التحديث للجميع (بدون أرقام إصدار):", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = updateTitle,
            onValueChange = { updateTitle = it.take(150) },
            label = { Text("عنوان التحديث (مثال: تحديث جديد ومزايا مهمة)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("admin_release_title_input")
        )
        OutlinedTextField(
            value = updateUrl,
            onValueChange = { updateUrl = it.take(1000) },
            label = { Text("رابط التحديث (HTTPS / HTTP)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("admin_release_url_input")
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it.take(2000) },
            label = { Text("وصف مختصر للتحديث (اختياري)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = releaseImageUrl,
            onValueChange = { releaseImageUrl = it.take(1000) },
            label = { Text("رابط صورة للتحديث (HTTPS اختياري)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    releaseImgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.weight(1f).testTag("admin_release_pick_image_btn")
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (releaseImageBase64.isNotBlank()) "تغيير صورة التحديث" else "اختيار صورة من المعرض")
            }
            if (releaseImageBase64.isNotBlank()) {
                val rBmp = remember(releaseImageBase64) {
                    runCatching {
                        val b = Base64.decode(releaseImageBase64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(b, 0, b.size)
                    }.getOrNull()
                }
                if (rBmp != null) {
                    Image(
                        bitmap = rBmp.asImageBitmap(),
                        contentDescription = "صورة التحديث",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                    )
                }
                IconButton(onClick = { releaseImageBase64 = "" }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف الصورة", tint = DangerRed)
                }
            }
        }
        val canPublish = updateTitle.isNotBlank() &&
            (updateUrl.trim().startsWith("https://") || updateUrl.trim().startsWith("http://"))
        Button(
            enabled = canPublish,
            onClick = {
                val nextCode = maxOf(remoteRelease?.latestVersionCode ?: 1, BuildConfig.VERSION_CODE) + 1
                onPublishRelease(
                    nextCode,
                    updateTitle.trim(),
                    notes.trim(),
                    false,
                    updateUrl.trim(),
                    releaseImageUrl.trim(),
                    releaseImageBase64
                )
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("admin_publish_release_btn")
        ) {
            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("نشر التحديث للجميع", fontWeight = FontWeight.Bold)
        }
        if (!remoteRelease?.apkUrl.isNullOrBlank()) {
            OutlinedButton(
                onClick = { onPublishRelease(1, "تم الإيقاف", "", false, "", "", "") },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("إخفاء / إيقاف التحديث الحالي")
            }
        }
    }
}

@Composable
private fun AdminUsersAndGiftsTab(
    users: List<TeacherAccountRecord>,
    availableRanks: List<String>,
    onUpdateUser: (String, Int, String, Int, String, Boolean) -> Unit,
    onDeleteUser: (String) -> Unit,
    onUnbindUserDevice: (String) -> Unit,
    onSendPrivateNotification: (String, String, String) -> Unit,
    onSendGroupGift: (Int, String) -> Unit
) {
    val ext = LocalExtendedColors.current
    val context = LocalContext.current
    var userSearchQuery by remember { mutableStateOf("") }
    var giftAmount by remember { mutableStateOf("50") }
    var giftMessage by remember { mutableStateOf("هدية نقاط مجانية للجميع!") }
    var editingUser by remember { mutableStateOf<TeacherAccountRecord?>(null) }
    var messagingUser by remember { mutableStateOf<TeacherAccountRecord?>(null) }
    var deletingUser by remember { mutableStateOf<TeacherAccountRecord?>(null) }

    val filteredUsers = remember(users, userSearchQuery) {
        val q = userSearchQuery.trim()
        if (q.isBlank()) users
        else users.filter {
            it.teacherName.contains(q, ignoreCase = true) ||
                it.usernameDisplay.contains(q, ignoreCase = true) ||
                it.accountKey.contains(q, ignoreCase = true) ||
                it.ownerUid.contains(q, ignoreCase = true)
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(
                value = userSearchQuery,
                onValueChange = { userSearchQuery = it },
                label = { Text("ابحث عن مستخدم (الاسم، اليوزر، المعرّف)") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (userSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { userSearchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "مسح")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admin_users_search_input")
            )
        }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = GoldBright.copy(alpha = 0.12f), border = BorderStroke(1.dp, GoldBright)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إرسال هدية نقاط جماعية للجميع (منفصلة عن الإحالة)", fontWeight = FontWeight.Bold, color = GoldBright)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = giftAmount,
                            onValueChange = { giftAmount = it.filter(Char::isDigit).take(5) },
                            label = { Text("النقاط") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(100.dp)
                        )
                        OutlinedTextField(
                            value = giftMessage,
                            onValueChange = { giftMessage = it.take(300) },
                            label = { Text("رسالة الهدية") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        enabled = (giftAmount.toIntOrNull() ?: 0) >= 1,
                        onClick = { onSendGroupGift(giftAmount.toIntOrNull() ?: 50, giftMessage) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إرسال هدية للجميع الآن", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(filteredUsers, key = { it.accountKey }) { u ->
            Surface(shape = RoundedCornerShape(14.dp), color = ext.cardElevated, border = BorderStroke(1.dp, ext.borderSubtle)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${u.teacherName.ifBlank { u.usernameDisplay.ifBlank { u.accountKey } }} ${if (u.promotionTitle.isNotBlank()) "• [${u.promotionTitle}]" else ""}",
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val userHandle = u.usernameDisplay.ifBlank { u.accountKey }
                        Text(
                            "المستخدم: $userHandle • النقاط: ${u.points} • الحالة: ${u.accessMode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.textMuted,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { copyAdminText(context, "اسم المستخدم", userHandle) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "نسخ اليوزر", modifier = Modifier.size(15.dp))
                        }
                    }
                    if (u.referralCode.isNotBlank() || u.ownerUid.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (u.referralCode.isNotBlank()) {
                                AssistChip(
                                    onClick = { copyAdminText(context, "كود الإحالة", u.referralCode) },
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(14.dp)) },
                                    label = { Text("إحالة: ${u.referralCode}", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            if (u.ownerUid.isNotBlank()) {
                                AssistChip(
                                    onClick = { copyAdminText(context, "UID", u.ownerUid) },
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(14.dp)) },
                                    label = { Text("UID: ${u.ownerUid.take(8)}...", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                    Text(
                        "اليوم: ${u.todayUsageSeconds / 60} د | الإجمالي: ${u.totalUsageSeconds / 60} د | تقييم: ${u.ratingStars}★",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(onClick = { editingUser = u }) { Text("هدية/رتبة/صلاحية") }
                        OutlinedButton(onClick = { messagingUser = u }) { Text("إشعار خاص") }
                        OutlinedButton(
                            onClick = {
                                val nextMode = if (u.accessMode == "SUSPENDED") "OPEN" else "SUSPENDED"
                                onUpdateUser(u.accountKey, u.points, nextMode, u.trialDays, u.promotionTitle, u.allowWeeklyNameChange)
                            }
                        ) { Text(if (u.accessMode == "SUSPENDED") "تفعيل" else "تعطيل مؤقت") }
                        if (u.boundDeviceId.isNotBlank()) {
                            OutlinedButton(onClick = { onUnbindUserDevice(u.accountKey) }) { Text("فك الجهاز") }
                        }
                        OutlinedButton(onClick = { deletingUser = u }) { Text("حذف", color = DangerRed) }
                    }
                }
            }
        }
    }

    editingUser?.let { u ->
        var ptsText by remember(u) { mutableStateOf(u.points.toString()) }
        var mode by remember(u) { mutableStateOf(u.accessMode) }
        var daysText by remember(u) { mutableStateOf(u.trialDays.toString()) }
        var rankText by remember(u) { mutableStateOf(u.promotionTitle) }
        var weeklyGift by remember(u) { mutableStateOf(u.allowWeeklyNameChange) }
        AlertDialog(
            onDismissRequest = { editingUser = null },
            title = { Text("تحكم بالحساب: ${u.teacherName.ifBlank { u.usernameDisplay }}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = ptsText,
                        onValueChange = { ptsText = it.filter(Char::isDigit).take(6) },
                        label = { Text("رصيد النقاط (يمكن الزيادة كهدية)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rankText,
                        onValueChange = { rankText = it.take(50) },
                        label = { Text("اللقب / الترقية (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableRanks.forEach { presetRank ->
                            FilterChip(selected = rankText == presetRank, onClick = { rankText = presetRank }, label = { Text(presetRank) })
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("هدية تغيير الاسم أسبوعياً", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = weeklyGift, onCheckedChange = { weeklyGift = it })
                    }
                    Text("صلاحية الحساب:")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("OPEN" to "مفتوح", "TRIAL" to "تجريبي", "SUSPENDED" to "معطل").forEach { (id, label) ->
                            FilterChip(selected = mode == id, onClick = { mode = id }, label = { Text(label) })
                        }
                    }
                    if (mode == "TRIAL") {
                        OutlinedTextField(
                            value = daysText,
                            onValueChange = { daysText = it.filter(Char::isDigit).take(4) },
                            label = { Text("أيام التجربة من الآن") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateUser(u.accountKey, ptsText.toIntOrNull() ?: u.points, mode, daysText.toIntOrNull() ?: 7, rankText, weeklyGift)
                    editingUser = null
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { editingUser = null }) { Text("إلغاء") } }
        )
    }

    messagingUser?.let { u ->
        var msgTitle by remember(u) { mutableStateOf("رسالة خاصة من الإدارة") }
        var msgBody by remember(u) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { messagingUser = null },
            title = { Text("إشعار خاص إلى: ${u.teacherName.ifBlank { u.usernameDisplay }}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = msgTitle, onValueChange = { msgTitle = it.take(120) }, label = { Text("العنوان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = msgBody, onValueChange = { msgBody = it.take(1000) }, label = { Text("نص الرسالة") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    enabled = msgBody.isNotBlank(),
                    onClick = {
                        val target = u.ownerUid.ifBlank { u.usernameDisplay.ifBlank { u.accountKey } }
                        onSendPrivateNotification(target, msgTitle.ifBlank { "إشعار خاص" }, msgBody)
                        messagingUser = null
                    }
                ) { Text("إرسال") }
            },
            dismissButton = { TextButton(onClick = { messagingUser = null }) { Text("إلغاء") } }
        )
    }

    deletingUser?.let { u ->
        AlertDialog(
            onDismissRequest = { deletingUser = null },
            title = { Text("تأكيد حذف الحساب", color = DangerRed) },
            text = { Text("سيتم حذف سجل (${u.teacherName.ifBlank { u.usernameDisplay }}) من قاعدة البيانات السحابية نهائياً.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUser(u.accountKey)
                        deletingUser = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) { Text("حذف نهائي", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { deletingUser = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun AdminAnalyticsFeedbackReportsTab(
    analytics: CloudAnalyticsSummary,
    users: List<TeacherAccountRecord>,
    feedbackList: List<DeveloperFeedbackRecord>,
    parentReports: List<ParentReportRecord>,
    onToggleFeedbackReviewed: (String, Boolean) -> Unit,
    onDeleteParentReport: (String) -> Unit
) {
    val ext = LocalExtendedColors.current
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = ext.cardElevated, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("إحصائيات الاستخدام الحي والتقييمات:", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Text("إجمالي الحسابات المسجلة: ${analytics.totalUsers} • النشطون اليوم: ${analytics.activeTodayUsers}")
                    Text("مجموع استخدام اليوم: ${analytics.totalTodayMinutes} دقيقة • التراكمي: ${String.format("%.1f", analytics.totalAllTimeHours)} ساعة")
                    Text("متوسط التقييم: ${String.format("%.1f", analytics.averageRating)} ★ (${analytics.ratingsCount} تقييم)")
                    if (analytics.topTools.isNotEmpty()) {
                        HorizontalDivider()
                        Text("أكثر الأدوات استخداماً:", fontWeight = FontWeight.Bold)
                        analytics.topTools.take(5).forEachIndexed { idx, (tool, count) ->
                            Text("${idx + 1}. ${tool.shortTitleAr}: $count مرة", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val reviews = users.filter { it.ratingStars > 0 && it.ratingComment.isNotBlank() }
                    if (reviews.isNotEmpty()) {
                        HorizontalDivider()
                        Text("تعليقات التقييم:", fontWeight = FontWeight.Bold)
                        reviews.take(4).forEach { rev ->
                            Text("• ${rev.teacherName.ifBlank { rev.usernameDisplay }} (${rev.ratingStars}★): ${rev.ratingComment}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            Text("رسائل واقتراحات المطور (${feedbackList.size}):", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        items(feedbackList, key = { it.id }) { fb ->
            Surface(shape = RoundedCornerShape(14.dp), color = ext.cardElevated, border = BorderStroke(1.dp, if (fb.lockedThreeDays) WarningAmber else ext.borderSubtle)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${fb.senderName.ifBlank { "مستخدم" }} • ${FeedbackCategory.fromRaw(fb.category).titleAr} (${fb.wordCount} كلمة)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        FilterChip(
                            selected = fb.status == "REVIEWED",
                            onClick = { onToggleFeedbackReviewed(fb.id, fb.status != "REVIEWED") },
                            label = { Text(if (fb.status == "REVIEWED") "تمت المراجعة" else "قيد الانتظار") }
                        )
                    }
                    Text(fb.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Text("تقارير أولياء الأمور النشطة (${parentReports.size}):", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        items(parentReports, key = { it.reportCode }) { rep ->
            Surface(shape = RoundedCornerShape(12.dp), color = ext.cardElevated, border = BorderStroke(1.dp, ext.borderSubtle)) {
                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${rep.studentName} • ${rep.reportCode}", fontWeight = FontWeight.Bold)
                        Text("${rep.teacherName} | ${rep.subjectName} (${rep.gradeTitle})", style = MaterialTheme.typography.labelSmall, color = ext.textMuted)
                    }
                    IconButton(onClick = { onDeleteParentReport(rep.reportCode) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف التقرير", tint = DangerRed)
                    }
                }
            }
        }
    }
}
