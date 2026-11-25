package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.UpdateCheckResult
import com.example.model.AppState
import com.example.model.GradeLevel
import com.example.model.GradebookSettings
import com.example.model.SubjectSpecialty
import com.example.model.TeacherProfile
import com.example.ui.components.ColorWheelCustomizerSection
import com.example.ui.components.ColorWheelPickerDialog
import com.example.ui.components.SectionHeaderWithInfo
import com.example.ui.components.formatColorHex
import com.example.ui.components.neonCardGlow
import com.example.ui.components.visualSpec
import com.example.ui.theme.AppFontOption
import com.example.ui.theme.AppTextTone
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.ThemePalette
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.paletteSpec
import com.example.utils.GoogleSignInArabicButton
import com.example.utils.LessonAlarmScheduler
import com.example.utils.LessonBoundaryType
import com.example.utils.NotificationHelper
import com.google.firebase.auth.FirebaseUser

@Composable
fun SettingsScreen(
    state: AppState,
    onUpdateProfile: (TeacherProfile) -> Unit,
    onUpdateGradebookSettings: (GradebookSettings) -> Unit,
    onSetLedgerActivation: (Boolean, Boolean) -> Unit,
    onResetDefaultSchedule: () -> Unit,
    onTriggerInAppTestAlert: (LessonBoundaryType) -> Unit,
    onUpdateToolOrder: (List<String>) -> Unit = {},
    onWipeAllGrades: (String) -> Boolean = { false },
    onWipeAllStudents: (String) -> Boolean = { false },
    onOpenTeacherProfile: () -> Unit = {},
    selectedGrade: GradeLevel = state.profile.preferredGrade,
    onSelectPreferredGrade: (GradeLevel) -> Unit = {},
    currentUser: FirebaseUser? = null,
    unreadNotificationsCount: Int = 0,
    updateCheckResult: UpdateCheckResult = UpdateCheckResult.Idle,
    currentPoints: Int = 0,
    isOnline: Boolean = true,
    pendingOutboxCount: Int = 0,
    onCheckForUpdates: () -> Unit = {},
    onOpenNotificationsInbox: () -> Unit = {},
    onOpenAdminNotifications: () -> Unit = {},
    onOpenCloudBackup: () -> Unit = {},
    onOpenReferralAndRating: () -> Unit = {},
    onOpenDeveloperFeedback: () -> Unit = {},
    onOpenParentReportViewer: () -> Unit = {},
    onSignOutCloud: () -> Unit = {},
    onUnlockAdminSection: () -> Unit = {},
    onOpenActivationCode: () -> Unit = {},
    onRequestNameChange: () -> Unit = {},
    hasWeeklyNameGift: Boolean = false,
    onImportAppBg: (Uri) -> Boolean = { false },
    onClearAppBg: () -> Unit = {},
    onImportSectionBg: (String, Uri) -> Boolean = { _, _ -> false },
    onClearSectionBg: (String) -> Unit = {},
    remoteControl: com.example.data.RemoteControlConfig = com.example.data.RemoteControlConfig(),
    isAdmin: Boolean = false,
    onSetPreferDefaultDesign: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val ext = LocalExtendedColors.current

    var teacherName by remember(state.profile) { mutableStateOf(state.profile.teacherName) }
    var timetableKey by remember(state.profile) { mutableStateOf(state.profile.timetableTeacherKey) }
    var schoolName by remember(state.profile) { mutableStateOf(state.profile.schoolName) }
    var subjectName by remember(state.profile) { mutableStateOf(state.profile.subjectName) }
    var academicYear by remember(state.profile) { mutableStateOf(state.profile.academicYear) }
    var lockPasswordDraft by remember(state.profile.appLockPassword) { mutableStateOf(state.profile.appLockPassword) }
    var lockPassVisible by remember { mutableStateOf(false) }
    var showCustomizeGradebook by remember { mutableStateOf(false) }
    var testNotifFeedback by remember { mutableStateOf<String?>(null) }
    var wipeTarget by remember { mutableStateOf<String?>(null) }
    var wipePasswordInput by remember { mutableStateOf("") }
    var wipeError by remember { mutableStateOf<String?>(null) }
    var selectedBgSectionKey by remember { mutableStateOf("HOME") }
    val canExactAlarms = remember(state.profile) { LessonAlarmScheduler.canScheduleExactAlarms(context) }

    val appBgPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onImportAppBg(uri) }

    val sectionBgPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onImportSectionBg(selectedBgSectionKey, uri) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotificationHelper.showNotification(
                context = context,
                id = 900,
                title = "تم تفعيل إشعارات التطبيق",
                body = "سيصلك تنبيه برنة خفيفة قبل بداية ونهاية كل حصة بـ ${state.profile.notifyMinutesBefore} دقيقة."
            )
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.primary)
            }
            SectionHeaderWithInfo(
                title = "الإعدادات والخطوط والمظهر",
                infoTitle = "تخصيص التطبيق والخط والإشعارات",
                infoDescription = "اضبط اسم المدرس وتخصصه والمرحلة المفضلة، وتحكم بنوع وحجم الخط في كامل التطبيق لراحة النظر، وفعّل تنبيهات بداية ونهاية الحصة بدقيقة داخل وخارج التطبيق."
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Identity, Specialty & Preferred Grade
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(if (ext.neonGlowEnabled) 1.2.dp else 1.dp, if (ext.neonGlowEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.42f) else ext.borderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .neonCardGlow(MaterialTheme.colorScheme.primary, ext.neonGlowEnabled, 18.dp, alphaFactor = 0.22f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("هوية المدرس والتخصص والمرحلة", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        val activeUserDisplay = state.profile.accountUsername.ifBlank { state.profile.autoGeneratedUsername }
                        if (activeUserDisplay.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "اسم المستخدم: $activeUserDisplay",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("اسم المستخدم", activeUserDisplay))
                                        Toast.makeText(context, "تم نسخ اسم المستخدم ✓", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_copy_username_btn")
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "نسخ اسم المستخدم", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        OutlinedTextField(
                            value = teacherName,
                            onValueChange = { if (!state.profile.initialNameChosen) teacherName = it },
                            enabled = !state.profile.initialNameChosen,
                            label = { Text("اسم المدرس في التطبيق والرسائل") },
                            trailingIcon = {
                                val targetName = if (state.profile.initialNameChosen) state.profile.teacherName else teacherName
                                if (targetName.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("اسم المدرس", targetName))
                                            Toast.makeText(context, "تم نسخ الاسم ✓", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.testTag("settings_copy_teacher_name_btn")
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = "نسخ اسم المدرس", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (state.profile.initialNameChosen) {
                            OutlinedButton(
                                onClick = onRequestNameChange,
                                modifier = Modifier.fillMaxWidth().testTag("settings_request_name_change_btn")
                            ) {
                                Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (hasWeeklyNameGift) "تعديل الاسم (متاح أسبوعياً)"
                                    else "طلب تعديل اسم المدرس"
                                )
                            }
                        }
                        OutlinedTextField(
                            value = timetableKey,
                            onValueChange = { timetableKey = it },
                            label = { Text("الاسم المطابق لجدول الدروس") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = schoolName,
                            onValueChange = { schoolName = it },
                            label = { Text("اسم المدرسة") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = subjectName,
                                onValueChange = { subjectName = it },
                                label = { Text("المادة") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = academicYear,
                                onValueChange = { academicYear = it },
                                label = { Text("العام الدراسي") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text("الاختصاص (تظهر رموز المادة واسمها في السجل):", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SubjectSpecialty.entries.forEach { sp ->
                                val spec = sp.visualSpec()
                                val palSpec = sp.paletteSpec()
                                val selected = sp == state.profile.subjectSpecialty
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        subjectName = sp.titleAr
                                        onUpdateProfile(
                                            state.profile.copy(
                                                subjectSpecialty = sp,
                                                subjectName = sp.titleAr
                                            )
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = spec.primaryIcon,
                                            contentDescription = null,
                                            tint = if (selected) MaterialTheme.colorScheme.onPrimary else palSpec.darkPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(sp.titleAr) }
                                )
                            }
                        }

                        Text("المرحلة المفضلة عند الفتح:", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GradeLevel.entries.forEach { g ->
                                FilterChip(
                                    selected = g == state.profile.preferredGrade,
                                    onClick = {
                                        onSelectPreferredGrade(g)
                                        onUpdateProfile(state.profile.copy(preferredGrade = g))
                                    },
                                    label = { Text(g.shortAr) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val finalName = if (state.profile.initialNameChosen) {
                                    state.profile.teacherName
                                } else {
                                    teacherName.trim()
                                }
                                val chosenNow = state.profile.initialNameChosen || finalName.length >= 2
                                onUpdateProfile(
                                    state.profile.copy(
                                        teacherName = finalName,
                                        timetableTeacherKey = timetableKey.trim(),
                                        schoolName = schoolName.trim(),
                                        subjectName = subjectName.trim(),
                                        academicYear = academicYear.trim(),
                                        initialNameChosen = chosenNow,
                                        lastNameChangeMillis = if (!state.profile.initialNameChosen && chosenNow) System.currentTimeMillis() else state.profile.lastNameChangeMillis
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("save_profile_settings_btn")
                        ) { Text("حفظ الهوية") }
                    }
                }
            }

            // Font Family & Global Text Size Slider
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.dp, ext.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("نوع الخط وحجم النص (لراحة العين وضعاف النظر)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "حجم النص الحالي: ${(state.profile.fontScaleMultiplier * 100).toInt()}% — هذا نص تجريبي واضح للقراءة.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Slider(
                            value = state.profile.fontScaleMultiplier,
                            onValueChange = { scale ->
                                onUpdateProfile(state.profile.copy(fontScaleMultiplier = scale))
                            },
                            valueRange = 0.85f..1.45f,
                            steps = 5,
                            modifier = Modifier.testTag("font_scale_slider")
                        )
                        HorizontalDivider()
                        Text("اختر نوع الخط:", style = MaterialTheme.typography.labelLarge)
                        AppFontOption.entries.forEach { fOpt ->
                            val selected = fOpt == state.profile.fontOption
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onUpdateProfile(state.profile.copy(fontOption = fOpt)) }
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = fOpt.titleAr,
                                    fontFamily = fOpt.family,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (selected) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // Master Notifications & 1-min Lesson Alerts + Bell Defaults
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.dp, ext.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("التنبيهات الداخلية والخارجية وأوقات الحصص", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "يرن التنبيه الخفيف (~6 ثوانٍ) قبل بداية ونهاية كل درس بدقيقة، داخل التطبيق وخارجه.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textMuted
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("تفعيل جميع إشعارات التطبيق", fontWeight = FontWeight.Bold)
                            Switch(
                                checked = state.profile.notificationsMasterEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    onUpdateProfile(state.profile.copy(notificationsMasterEnabled = enabled))
                                },
                                modifier = Modifier.testTag("master_notifications_switch")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("تنبيهات بداية ونهاية الدروس")
                            Switch(
                                enabled = state.profile.notificationsMasterEnabled,
                                checked = state.profile.lessonNotificationsEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateProfile(state.profile.copy(lessonNotificationsEnabled = enabled))
                                }
                            )
                        }

                        Text("نوع التنبيه المطلوب تفعيله:", style = MaterialTheme.typography.labelLarge)
                        val pNotif = state.profile
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = pNotif.notifyBeforeLessonStart && pNotif.notifyBeforeLessonEnd,
                                onClick = {
                                    onUpdateProfile(pNotif.copy(notifyBeforeLessonStart = true, notifyBeforeLessonEnd = true))
                                },
                                label = { Text("البداية والنهاية معاً") },
                                modifier = Modifier.testTag("notif_mode_both")
                            )
                            FilterChip(
                                selected = pNotif.notifyBeforeLessonStart && !pNotif.notifyBeforeLessonEnd,
                                onClick = {
                                    onUpdateProfile(pNotif.copy(notifyBeforeLessonStart = true, notifyBeforeLessonEnd = false))
                                },
                                label = { Text("بداية الدرس فقط") },
                                modifier = Modifier.testTag("notif_mode_start_only")
                            )
                            FilterChip(
                                selected = !pNotif.notifyBeforeLessonStart && pNotif.notifyBeforeLessonEnd,
                                onClick = {
                                    onUpdateProfile(pNotif.copy(notifyBeforeLessonStart = false, notifyBeforeLessonEnd = true))
                                },
                                label = { Text("نهاية الدرس فقط") },
                                modifier = Modifier.testTag("notif_mode_end_only")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("النطق الصوتي الذكوري الهادئ", fontWeight = FontWeight.Bold)
                                Text(
                                    "ينطق مثال: (بداية الدرس الثاني) أو (متبقي دقيقة لانتهاء الدرس)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textMuted
                                )
                            }
                            Switch(
                                enabled = state.profile.notificationsMasterEnabled,
                                checked = state.profile.voiceAnnouncementEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateProfile(state.profile.copy(voiceAnnouncementEnabled = enabled))
                                },
                                modifier = Modifier.testTag("voice_announcement_switch")
                            )
                        }

                        Text("مستوى صوت النغمة والنطق (${state.profile.alertVolumePercent}%):", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = state.profile.alertVolumePercent.toFloat(),
                            onValueChange = { v ->
                                onUpdateProfile(state.profile.copy(alertVolumePercent = v.toInt().coerceIn(25, 100)))
                            },
                            valueRange = 25f..100f,
                            steps = 5,
                            modifier = Modifier.testTag("alert_volume_slider")
                        )

                        Text("مدة التنبيه المسبق (دقائق):", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3, 5).forEach { mins ->
                                FilterChip(
                                    selected = state.profile.notifyMinutesBefore == mins,
                                    onClick = { onUpdateProfile(state.profile.copy(notifyMinutesBefore = mins)) },
                                    label = { Text("$mins د") }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("تنبيه إضافي قبل بدء الحصة بـ 30 ثانية", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "إشعار سريع قبل دخول الحصة بنصف دقيقة للاستعداد",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textMuted
                                )
                            }
                            Switch(
                                enabled = state.profile.notificationsMasterEnabled && state.profile.lessonNotificationsEnabled,
                                checked = state.profile.halfMinuteAlertEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateProfile(state.profile.copy(halfMinuteAlertEnabled = enabled))
                                },
                                modifier = Modifier.testTag("settings_half_minute_alert_switch")
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    LessonAlarmScheduler.scheduleTest30SecondNotification(
                                        context = context,
                                        teacherName = state.profile.teacherName,
                                        boundaryType = LessonBoundaryType.BEFORE_START,
                                        voiceEnabled = state.profile.voiceAnnouncementEnabled,
                                        volumePercent = state.profile.alertVolumePercent
                                    )
                                    testNotifFeedback = "تمت جدولة إشعار خارجي — سيصلك بنغمة ونطق بعد 30 ثانية ✓"
                                },
                                modifier = Modifier.weight(1f).testTag("settings_test_30s_notif_btn")
                            ) {
                                Icon(Icons.Filled.Timer, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("تجربة خارجية 30ث", maxLines = 1)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    onTriggerInAppTestAlert(LessonBoundaryType.BEFORE_START)
                                    testNotifFeedback = "تم تشغيل نغمة البداية + النطق الصوتي الهادئ ✓"
                                },
                                modifier = Modifier.weight(1f).testTag("settings_test_start_alert_btn")
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("تجربة نغمة البداية", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    onTriggerInAppTestAlert(LessonBoundaryType.BEFORE_END)
                                    testNotifFeedback = "تم تشغيل نغمة النهاية + النطق الصوتي الهادئ ✓"
                                },
                                modifier = Modifier.weight(1f).testTag("settings_test_end_alert_btn")
                            ) {
                                Icon(Icons.Filled.NotificationsActive, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("تجربة نغمة النهاية", maxLines = 1)
                            }
                        }

                        testNotifFeedback?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }

                        HorizontalDivider()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canExactAlarms) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = WarningAmber.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, WarningAmber)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "إذن المنبه الدقيق غير مفعّل لإشعارات الدقيقة الواحدة",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WarningAmber,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        runCatching { context.startActivity(intent) }
                                    }) {
                                        Text("تفعيل الإذن")
                                    }
                                }
                            }
                        } else {
                            Text(
                                "أذونات المنبهات الدقيقة والتوقيت: مفعّلة ✓",
                                style = MaterialTheme.typography.bodySmall,
                                color = SuccessGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                onResetDefaultSchedule()
                                testNotifFeedback = "تمت استعادة أوقات الصباحي والظهري المعتمدة ✓"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Restore, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("استعادة أوقات الصباحي والظهري الافتراضية")
                        }

                        Button(
                            onClick = { showCustomizeGradebook = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Tune, null)
                            Spacer(Modifier.width(8.dp))
                            Text("ضبط حساب اليومي والتقريب والأشهر")
                        }
                    }
                }
            }

            // Security: App Lock, Safe Grade Edit Lock & Profile Splash shortcut
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.dp, ext.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("الأمان وقفل التطبيق والوضع الآمن للدرجات", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(
                            onClick = onOpenTeacherProfile,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.AccountCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("فتح الملف الشخصي وتخصيص شاشة الدخول (1.5 ثانية)")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("قفل التطبيق عند الدخول", fontWeight = FontWeight.Bold)
                                Text("يطلب البصمة أو كلمة المرور قبل عرض السجلات", style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                            }
                            Switch(
                                checked = state.profile.appLockEnabled,
                                onCheckedChange = { onUpdateProfile(state.profile.copy(appLockEnabled = it)) },
                                modifier = Modifier.testTag("app_lock_switch")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("استخدام بصمة الإصبع للفتح")
                            Switch(
                                enabled = state.profile.appLockEnabled,
                                checked = state.profile.appLockFingerprintEnabled,
                                onCheckedChange = { onUpdateProfile(state.profile.copy(appLockFingerprintEnabled = it)) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = lockPasswordDraft,
                                onValueChange = { lockPasswordDraft = it },
                                label = { Text("كلمة مرور التطبيق والحذف") },
                                singleLine = true,
                                visualTransformation = if (lockPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { lockPassVisible = !lockPassVisible }) {
                                        Icon(if (lockPassVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                enabled = lockPasswordDraft.isNotBlank(),
                                onClick = {
                                    onUpdateProfile(state.profile.copy(appLockPassword = lockPasswordDraft))
                                    testNotifFeedback = "تم تحديث كلمة مرور الأمان ✓"
                                }
                            ) { Text("حفظ") }
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("الوضع الآمن لتعديل الدرجات", fontWeight = FontWeight.Bold)
                                Text("يمنع تغيير أي درجة بدون تأكيد بالبصمة أو كلمة المرور", style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                            }
                            Switch(
                                checked = state.profile.safeGradeEditLockEnabled,
                                onCheckedChange = { onUpdateProfile(state.profile.copy(safeGradeEditLockEnabled = it)) },
                                modifier = Modifier.testTag("safe_grade_lock_switch")
                            )
                        }
                    }
                }
            }

            // Display & Live Schedule Banner & Gradebook Rules
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.dp, ext.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("خيارات العرض والشريط الحي والتقريب", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("تفعيل الجدول الحي (شريط الحصة الحالية والقادمة)")
                            Switch(
                                checked = state.profile.liveScheduleBannerEnabled,
                                onCheckedChange = { onUpdateProfile(state.profile.copy(liveScheduleBannerEnabled = it)) },
                                modifier = Modifier.testTag("settings_live_schedule_switch")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("جبر الكسور وتقريب الدرجات (55.5 → 56)")
                            Switch(
                                checked = state.gradebookSettings.enableSmartRounding,
                                onCheckedChange = { onUpdateGradebookSettings(state.gradebookSettings.copy(enableSmartRounding = it)) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("إخفاء الطلبة المنقولين من السجل")
                            Switch(
                                checked = state.gradebookSettings.hideTransferredStudents,
                                onCheckedChange = { onUpdateGradebookSettings(state.gradebookSettings.copy(hideTransferredStudents = it)) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("احتساب المنقولين في الإحصائيات")
                            Switch(
                                checked = state.gradebookSettings.includeTransferredInStats,
                                onCheckedChange = { onUpdateGradebookSettings(state.gradebookSettings.copy(includeTransferredInStats = it)) }
                            )
                        }
                        HorizontalDivider()
                        val effectiveLayout = com.example.utils.resolveEffectiveLayoutMode(
                            remoteControl = remoteControl,
                            profile = state.profile,
                            isAdmin = isAdmin
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("العودة للتصميم الافتراضي", fontWeight = FontWeight.Bold)
                                Text(
                                    text = "الوضع الحالي: ${effectiveLayout.titleAr}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textMuted
                                )
                            }
                            Switch(
                                checked = state.profile.preferDefaultDesign,
                                onCheckedChange = { onSetPreferDefaultDesign(it) },
                                modifier = Modifier.testTag("settings_prefer_default_design_switch")
                            )
                        }
                    }
                }
            }

            // Theme & Interactive Color Wheel (Text, Buttons, Background, Cards & Data)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(
                        if (ext.neonGlowEnabled) 1.3.dp else 1.dp,
                        if (ext.neonGlowEnabled) MaterialTheme.colorScheme.secondary.copy(alpha = 0.50f) else ext.borderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .neonCardGlow(MaterialTheme.colorScheme.secondary, ext.neonGlowEnabled, 18.dp, alphaFactor = 0.26f)
                        .testTag("settings_color_wheel_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "تخصيص الألوان من عجلة الألوان",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("الوضع الليلي / النهاري", fontWeight = FontWeight.Bold)
                                Text(
                                    if (state.profile.isDarkMode) "ليلي مريح للعين" else "نهاري أبيض ناصع",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textMuted
                                )
                            }
                            Switch(
                                checked = state.profile.isDarkMode,
                                onCheckedChange = {
                                    onUpdateProfile(
                                        state.profile.copy(
                                            isDarkMode = it,
                                            themePalette = ThemePalette.DESIGN_7_MIDNIGHT_NEON,
                                            textTone = AppTextTone.AUTO
                                        )
                                    )
                                },
                                modifier = Modifier.testTag("settings_dark_mode_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("إضاءة النيون حول إطارات الأقسام", fontWeight = FontWeight.Bold)
                                Text(
                                    if (state.profile.neonGlowEnabled) "مفعّلة — إطارات مضيئة ملونة حول البطاقات والأقسام" else "متوقفة — إطارات كلاسيكية هادئة",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textMuted
                                )
                            }
                            Switch(
                                checked = state.profile.neonGlowEnabled,
                                onCheckedChange = {
                                    onUpdateProfile(state.profile.copy(neonGlowEnabled = it))
                                },
                                modifier = Modifier.testTag("settings_neon_glow_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("تلوين تلقائي حسب الاختصاص (${state.profile.subjectSpecialty.titleAr})", fontWeight = FontWeight.Medium)
                                Text(
                                    "يعطي طابعاً خاصاً لمادتك عند إيقاف الألوان اليدوية",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textMuted
                                )
                            }
                            Switch(
                                checked = state.profile.useSpecialtyTheme,
                                onCheckedChange = {
                                    onUpdateProfile(state.profile.copy(useSpecialtyTheme = it))
                                },
                                modifier = Modifier.testTag("use_specialty_theme_switch")
                            )
                        }

                        HorizontalDivider()

                        ColorWheelCustomizerSection(
                            customColorsEnabled = state.profile.customColorsEnabled,
                            customTextColorHex = state.profile.customTextColorHex,
                            customButtonColorHex = state.profile.customButtonColorHex,
                            customBgColorHex = state.profile.customBgColorHex,
                            customCardAccentColorHex = state.profile.customCardAccentColorHex,
                            onApplyColors = { enabled, textHex, btnHex, bgHex, cardHex ->
                                onUpdateProfile(
                                    state.profile.copy(
                                        customColorsEnabled = enabled,
                                        customTextColorHex = textHex,
                                        customButtonColorHex = btnHex,
                                        customBgColorHex = bgHex,
                                        customCardAccentColorHex = cardHex,
                                        useSpecialtyTheme = if (enabled) false else state.profile.useSpecialtyTheme
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // Timetable & Student Names Color Customization via Color Wheel
            item {
                var activeTableWheelTarget by remember { mutableStateOf<String?>(null) }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.dp, ext.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ألوان الجداول وأسماء الطلبة", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            TextButton(
                                onClick = {
                                    onUpdateProfile(
                                        state.profile.copy(
                                            studentNamesColorHex = 0L,
                                            timetableBgColorHex = 0L,
                                            timetableAccentColorHex = 0L,
                                            gradebookTableBgColorHex = 0L
                                        )
                                    )
                                }
                            ) { Text("الكل افتراضي") }
                        }

                        val tableItems = listOf(
                            Triple("student_names", "لون أسماء الطلبة في السجل والبحث", state.profile.studentNamesColorHex),
                            Triple("timetable_bg", "لون خلفية جدول الدروس الأسبوعي", state.profile.timetableBgColorHex),
                            Triple("timetable_accent", "لون عناوين وحدود جدول الدروس", state.profile.timetableAccentColorHex),
                            Triple("gradebook_bg", "لون خلفية جدول الدرجات", state.profile.gradebookTableBgColorHex)
                        )
                        tableItems.forEach { (key, title, hex) ->
                            val swatch = when {
                                hex != 0L -> androidx.compose.ui.graphics.Color(hex)
                                key == "student_names" -> MaterialTheme.colorScheme.onSurface
                                key == "timetable_accent" -> MaterialTheme.colorScheme.primary
                                else -> ext.cardSecondary
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ext.cardSecondary,
                                border = BorderStroke(1.dp, ext.borderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { activeTableWheelTarget = key }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(swatch)
                                        )
                                        Column {
                                            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text(formatColorHex(hex), style = MaterialTheme.typography.labelSmall, color = ext.textMuted)
                                        }
                                    }
                                    Text("عجلة الألوان", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                activeTableWheelTarget?.let { targetKey ->
                    val (dialogTitle, currentHex) = when (targetKey) {
                        "student_names" -> "لون أسماء الطلبة" to state.profile.studentNamesColorHex
                        "timetable_bg" -> "خلفية جدول الدروس" to state.profile.timetableBgColorHex
                        "timetable_accent" -> "عناوين وحدود جدول الدروس" to state.profile.timetableAccentColorHex
                        else -> "خلفية جدول الدرجات" to state.profile.gradebookTableBgColorHex
                    }
                    ColorWheelPickerDialog(
                        title = dialogTitle,
                        initialHex = currentHex,
                        onSelectHex = { newHex ->
                            onUpdateProfile(
                                when (targetKey) {
                                    "student_names" -> state.profile.copy(studentNamesColorHex = newHex)
                                    "timetable_bg" -> state.profile.copy(timetableBgColorHex = newHex)
                                    "timetable_accent" -> state.profile.copy(timetableAccentColorHex = newHex)
                                    else -> state.profile.copy(gradebookTableBgColorHex = newHex)
                                }
                            )
                        },
                        onDismiss = { activeTableWheelTarget = null }
                    )
                }
            }

            // Custom App-Wide & Per-Section Backgrounds (PNG / JPG via Photo Picker)
            item {
                val bgTargets = remember {
                    listOf(
                        "HOME" to "الرئيسية",
                        "TIMETABLE" to "الجدول",
                        "GRADEBOOK" to "الدرجات",
                        "ANALYTICS" to "التحليل"
                    ) + ToolRoute.entries.map { it.name to it.shortTitleAr }
                }
                val hasAppBg = !state.profile.appBgImageFileName.isNullOrBlank()
                val hasSectionBg = !state.profile.sectionBgImageFiles[selectedBgSectionKey].isNullOrBlank()

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.dp, ext.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("خلفيات التطبيق والأقسام (PNG / JPG)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "أضف صورة خلفية كاملة للتطبيق أو خصص خلفية مستقلة لكل قسم من الاستوديو.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textMuted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    appBgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("settings_pick_app_bg_btn")
                            ) {
                                Icon(Icons.Filled.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("خلفية التطبيق", maxLines = 1)
                            }
                            if (hasAppBg) {
                                OutlinedButton(
                                    onClick = onClearAppBg,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) { Text("حذف") }
                            }
                        }

                        HorizontalDivider()
                        Text("خلفية قسم محدد:", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            bgTargets.forEach { (key, title) ->
                                val hasBg = !state.profile.sectionBgImageFiles[key].isNullOrBlank()
                                FilterChip(
                                    selected = selectedBgSectionKey == key,
                                    onClick = { selectedBgSectionKey = key },
                                    label = { Text(if (hasBg) "$title ✓" else title) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    sectionBgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("settings_pick_section_bg_btn")
                            ) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("اختيار خلفية القسم", maxLines = 1)
                            }
                            if (hasSectionBg) {
                                OutlinedButton(
                                    onClick = { onClearSectionBg(selectedBgSectionKey) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) { Text("حذف") }
                            }
                        }
                    }
                }
            }

            // Tool Priority Order
            item {
                val orderedTools = remember(state.profile.toolOrder) {
                    val order = state.profile.toolOrder
                    if (order.isEmpty()) ToolRoute.entries
                    else {
                        val map = ToolRoute.entries.associateBy { it.name }
                        val prioritized = order.mapNotNull { map[it] }
                        prioritized + ToolRoute.entries.filterNot { it in prioritized }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.dp, ext.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ترتيب أولوية الأقسام بالرئيسية", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { onUpdateToolOrder(emptyList()) }) {
                                Text("افتراضي")
                            }
                        }
                        orderedTools.forEachIndexed { idx, route ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                    Icon(route.icon, null, tint = route.accentColor, modifier = Modifier.size(18.dp))
                                    Text(route.titleAr, style = MaterialTheme.typography.bodyMedium)
                                }
                                Row {
                                    IconButton(
                                        enabled = idx > 0,
                                        onClick = {
                                            val list = orderedTools.toMutableList()
                                            val tmp = list[idx - 1]
                                            list[idx - 1] = list[idx]
                                            list[idx] = tmp
                                            onUpdateToolOrder(list.map { it.name })
                                        }
                                    ) {
                                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "أعلى")
                                    }
                                    IconButton(
                                        enabled = idx < orderedTools.lastIndex,
                                        onClick = {
                                            val list = orderedTools.toMutableList()
                                            val tmp = list[idx + 1]
                                            list[idx + 1] = list[idx]
                                            list[idx] = tmp
                                            onUpdateToolOrder(list.map { it.name })
                                        }
                                    ) {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "أسفل")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Cloud Accounts, Backups, Referrals, Developer Feedback, Parent Portal & Silent 10-Tap Version Panel
            item {
                var cloudAuthError by remember { mutableStateOf<String?>(null) }
                var versionTapCount by remember { mutableStateOf(0) }
                var lastVersionTapMs by remember { mutableStateOf(0L) }
                var showCloudGuide by remember { mutableStateOf(false) }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(
                        if (ext.neonGlowEnabled) 1.3.dp else 1.dp,
                        if (ext.neonGlowEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else ext.borderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .neonCardGlow(MaterialTheme.colorScheme.primary, ext.neonGlowEnabled, 18.dp, alphaFactor = 0.22f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "الخدمات السحابية والتحديثات",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isOnline) "متصل ✓" else if (pendingOutboxCount > 0) "أوفلاين ($pendingOutboxCount)" else "أوفلاين",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isOnline) SuccessGreen else WarningAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Collapsible Cloud Guide so nothing feels vague
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showCloudGuide = !showCloudGuide }
                                .testTag("settings_cloud_guide_toggle")
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Filled.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "كيف أستخدم الخدمات السحابية؟ (دليل سريع)",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Icon(
                                        imageVector = if (showCloudGuide) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (showCloudGuide) {
                                    Text(
                                        text = "• أولاً: سجل الدخول بحساب Google أدناه لتفعيل جميع الخدمات.\n" +
                                            "• النسخ الاحتياطي: احفظ درجاتك وألوانك وإعداداتك باسم مستخدم وكلمة مرور واسترجعها بأي هاتف.\n" +
                                            "• بوابة ولي الأمر: من (سجل الدرجات ← اسم الطالب ← مشاركة كود ولي الأمر) تحصل على كود يبدأ بـ PR ليعرض ولي الأمر درجات ابنه.\n" +
                                            "• التحديثات: يظهر لك عنوان التحديث ورابط مباشر عبر زر (عرض الرابط).\n" +
                                            "• أكواد الإدارة: إذا أعطاك المشرف كوداً يبدأ بـ RF أدخله لتفعيل الحساب أو الحصول على نقاط.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ext.textMuted
                                    )
                                }
                            }
                        }

                        if (currentUser == null) {
                            GoogleSignInArabicButton(
                                label = "1. تسجيل الدخول بحساب Google للمزامنة",
                                onAuthSuccess = { cloudAuthError = null },
                                onAuthError = { cloudAuthError = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                            cloudAuthError?.let {
                                Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "الحساب المتصل: ${currentUser.email ?: currentUser.displayName ?: currentUser.uid}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onSignOutCloud) {
                                    Text("تسجيل الخروج")
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = onCheckForUpdates,
                                enabled = updateCheckResult !is UpdateCheckResult.Checking,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_check_updates_btn")
                            ) {
                                Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (updateCheckResult is UpdateCheckResult.Checking) "جاري الفحص..." else "التحقق من التحديثات",
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = onOpenNotificationsInbox,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_notifications_inbox_btn")
                            ) {
                                Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (unreadNotificationsCount > 0) "الإشعارات ($unreadNotificationsCount)" else "الإشعارات",
                                    maxLines = 1
                                )
                            }
                        }

                        HorizontalDivider()

                        @Composable
                        fun CloudServiceTile(
                            icon: androidx.compose.ui.graphics.vector.ImageVector,
                            title: String,
                            subtitle: String,
                            tag: String,
                            highlight: Boolean = false,
                            onClick: () -> Unit
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f) else ext.cardSecondary,
                                border = BorderStroke(
                                    1.dp,
                                    if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else ext.borderSubtle
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onClick() }
                                    .testTag(tag)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ext.textMuted
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Filled.ChevronLeft,
                                        contentDescription = null,
                                        tint = ext.textMuted
                                    )
                                }
                            }
                        }

                        CloudServiceTile(
                            icon = Icons.Filled.CloudSync,
                            title = if (state.profile.accountUsername.isNotBlank()) {
                                "الحساب والنسخ الاحتياطي (${state.profile.accountUsername})"
                            } else {
                                "الحساب والنسخ الاحتياطي السحابي"
                            },
                            subtitle = "احفظ جميع طلابك ودرجاتك وألوانك باسم مستخدم وكلمة مرور واسترجعها من أي هاتف.",
                            tag = "settings_cloud_backup_btn",
                            highlight = true,
                            onClick = onOpenCloudBackup
                        )

                        CloudServiceTile(
                            icon = Icons.Filled.Visibility,
                            title = "بوابة ولي الأمر (عرض تقرير بكود)",
                            subtitle = "أدخل كود التقرير (يبدأ بـ PR) الذي أنشأته من سجل الطالب لعرض درجاته بأمان.",
                            tag = "settings_parent_report_viewer_btn",
                            onClick = onOpenParentReportViewer
                        )

                        CloudServiceTile(
                            icon = Icons.Filled.Key,
                            title = "تفعيل كود من الإدارة",
                            subtitle = "أدخل كود التفعيل المرسل من المشرف (يبدأ بـ RF-) لفتح المزايا أو إضافة نقاط.",
                            tag = "settings_activation_code_btn",
                            onClick = onOpenActivationCode
                        )

                        CloudServiceTile(
                            icon = Icons.Filled.CardGiftcard,
                            title = "النقاط والإحالات والتقييم ($currentPoints نقطة)",
                            subtitle = "شارك كود الإحالة مع زملائك لربح نقاط مجانية وقيّم التطبيق.",
                            tag = "settings_referrals_rating_btn",
                            onClick = onOpenReferralAndRating
                        )

                        CloudServiceTile(
                            icon = Icons.Filled.SupportAgent,
                            title = "مراسلة المطور (اقتراحات / شكاوى)",
                            subtitle = "أرسل ملاحظاتك أو اقتراحاتك مباشرةً لإدارة التطبيق.",
                            tag = "settings_developer_feedback_btn",
                            onClick = onOpenDeveloperFeedback
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ظهور «نشط الآن» في دردشة المدرسين", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    "إظهار حالتك كمتصل الآن للزملاء عند فتح المحادثات",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textMuted
                                )
                            }
                            Switch(
                                checked = state.profile.chatActiveOnlineVisible,
                                onCheckedChange = { onUpdateProfile(state.profile.copy(chatActiveOnlineVisible = it)) },
                                modifier = Modifier.testTag("settings_chat_online_switch")
                            )
                        }

                        HorizontalDivider()

                        Text(
                            text = "إصدار التطبيق الحالي: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                ) {
                                    val now = System.currentTimeMillis()
                                    versionTapCount = if (now - lastVersionTapMs <= 1500L) versionTapCount + 1 else 1
                                    lastVersionTapMs = now
                                    if (versionTapCount >= 10) {
                                        versionTapCount = 0
                                        onUnlockAdminSection()
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .testTag("settings_version_tap_target")
                        )
                    }
                }
            }

            if (state.profile.hiddenTools.isNotEmpty() || state.profile.archivedTools.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.dp, ext.borderSubtle),
                        modifier = Modifier.fillMaxWidth().testTag("settings_hidden_archived_tools_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("الأقسام المخفية والمؤرشفة", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            if (state.profile.hiddenTools.isNotEmpty()) {
                                Text("أقسام مخفية (اضغط لإظهارها في الرئيسية):", style = MaterialTheme.typography.labelMedium, color = ext.textMuted)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    state.profile.hiddenTools.forEach { hiddenKey ->
                                        val tr = ToolRoute.entries.firstOrNull { it.name == hiddenKey }
                                        AssistChip(
                                            onClick = {
                                                onUpdateProfile(
                                                    state.profile.copy(hiddenTools = state.profile.hiddenTools.filterNot { it == hiddenKey })
                                                )
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Visibility, null, modifier = Modifier.size(16.dp)) },
                                            label = { Text(tr?.shortTitleAr ?: hiddenKey) }
                                        )
                                    }
                                }
                            }
                            if (state.profile.archivedTools.isNotEmpty()) {
                                Text("أقسام مؤرشفة (اضغط لاستعادتها للواجهة):", style = MaterialTheme.typography.labelMedium, color = ext.textMuted)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    state.profile.archivedTools.forEach { archKey ->
                                        val tr = ToolRoute.entries.firstOrNull { it.name == archKey }
                                        AssistChip(
                                            onClick = {
                                                onUpdateProfile(
                                                    state.profile.copy(archivedTools = state.profile.archivedTools.filterNot { it == archKey })
                                                )
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Unarchive, null, modifier = Modifier.size(16.dp)) },
                                            label = { Text(tr?.shortTitleAr ?: archKey) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Danger Zone (Password Protected)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = DangerRed.copy(alpha = 0.08f),
                    border = BorderStroke(1.5.dp, DangerRed.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("منطقة الخطر — مسح البيانات بكلمة المرور", style = MaterialTheme.typography.titleMedium, color = DangerRed)
                        Text(
                            "لحماية سجلاتك من الحذف غير المقصود، يتطلب المسح إدخال كلمة مرور التطبيق أو رمز الحافظة السرية.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textMuted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    wipeTarget = "grades"
                                    wipePasswordInput = ""
                                    wipeError = null
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                                modifier = Modifier.weight(1f).testTag("wipe_grades_btn")
                            ) {
                                Text("مسح جميع الدرجات")
                            }
                            Button(
                                onClick = {
                                    wipeTarget = "students"
                                    wipePasswordInput = ""
                                    wipeError = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                                modifier = Modifier.weight(1f).testTag("wipe_students_btn")
                            ) {
                                Text("مسح جميع الطلبة", color = androidx.compose.ui.graphics.Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomizeGradebook) {
        CustomizeGradebookDialog(
            settings = state.gradebookSettings,
            onDismiss = { showCustomizeGradebook = false },
            onSave = {
                onUpdateGradebookSettings(it)
                showCustomizeGradebook = false
            },
            profile = state.profile,
            onUpdateProfile = onUpdateProfile
        )
    }

    wipeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { wipeTarget = null },
            title = {
                Text(
                    if (target == "grades") "تأكيد مسح جميع الدرجات" else "تأكيد مسح جميع الطلبة",
                    color = DangerRed
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل كلمة مرور التطبيق أو رمز الحافظة السرية للمتابعة (الإجراء لا يمكن التراجع عنه):")
                    OutlinedTextField(
                        value = wipePasswordInput,
                        onValueChange = {
                            wipePasswordInput = it
                            wipeError = null
                        },
                        label = { Text("كلمة المرور") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    wipeError?.let {
                        Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ok = if (target == "grades") {
                            onWipeAllGrades(wipePasswordInput)
                        } else {
                            onWipeAllStudents(wipePasswordInput)
                        }
                        if (ok) {
                            wipeTarget = null
                        } else {
                            wipeError = "كلمة المرور غير صحيحة"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) { Text("تنفيذ المسح", color = androidx.compose.ui.graphics.Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { wipeTarget = null }) { Text("إلغاء") }
            }
        )
    }
}
