package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AnnouncementPriority
import com.example.data.FirebaseCloudPlatformRepository
import com.example.data.FirebaseUpdateAndNotificationsRepository
import com.example.data.NotificationTargetType
import com.example.data.TrialAccessStatus
import com.example.model.GradeLevel
import com.example.model.Student
import com.example.model.SubjectSpecialty
import com.example.ui.components.neonTapFeedback
import com.example.ui.components.rememberNeonTapController
import com.example.ui.screens.*
import com.example.ui.theme.GoldBright
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.theme.TeacherLedgerTheme
import com.example.ui.viewmodel.CloudUpdatesViewModel
import com.example.ui.viewmodel.TeacherViewModel
import com.example.utils.BackupCryptoHelper
import com.example.utils.NotificationHelper
import com.example.utils.OfflineSyncOutbox
import com.example.utils.attemptAutoSignIn
import kotlinx.coroutines.delay

enum class MainNavTab(
    val titleAr: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("الرئيسية", Icons.Filled.Home, Icons.Outlined.Home),
    TIMETABLE("الجدول", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    GRADEBOOK("الدرجات", Icons.Filled.FactCheck, Icons.Outlined.FactCheck),
    ANALYTICS("التحليل", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    TOOLS("الإعدادات", Icons.Filled.Tune, Icons.Outlined.Tune)
}

private fun extractParentReportCode(data: Uri?): String? {
    if (data == null) return null
    if (data.scheme == "rafiqteacher" && data.host == "report") {
        return data.path?.trim('/')?.takeIf { it.isNotBlank() }?.uppercase()
    }
    return null
}

class MainActivity : ComponentActivity() {
    private var incomingReportCode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingReportCode = extractParentReportCode(intent?.data)
        setContent {
            val viewModel: TeacherViewModel = viewModel()
            TeacherLedgerApp(
                viewModel = viewModel,
                initialDeepLinkReportCode = incomingReportCode,
                onDeepLinkConsumed = { incomingReportCode = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractParentReportCode(intent.data)?.let { code ->
            incomingReportCode = code
        }
    }
}

@Composable
fun TeacherLedgerApp(
    viewModel: TeacherViewModel,
    initialDeepLinkReportCode: String? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeDayPeriod by viewModel.activeDayPeriod.collectAsStateWithLifecycle()
    val nowMillis by viewModel.nowMillis.collectAsStateWithLifecycle()
    val selectedGrade by viewModel.selectedGrade.collectAsStateWithLifecycle()
    val selectedSection by viewModel.selectedSection.collectAsStateWithLifecycle()
    val activeLedger by viewModel.activeLedger.collectAsStateWithLifecycle()
    val activeLedgerId by viewModel.activeLedgerId.collectAsStateWithLifecycle()
    val inAppAlert by viewModel.inAppAlert.collectAsStateWithLifecycle()
    val pendingExtractedText by viewModel.pendingExtractedTextForGrades.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val cloudViewModel: CloudUpdatesViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                CloudUpdatesViewModel(
                    appContext = app,
                    repository = FirebaseUpdateAndNotificationsRepository(app),
                    platformRepo = FirebaseCloudPlatformRepository(app),
                    outbox = OfflineSyncOutbox(app),
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    currentVersionName = BuildConfig.VERSION_NAME
                )
            }
        }
    )

    val cloudUser by cloudViewModel.currentUser.collectAsStateWithLifecycle()
    val cloudIsOnline by cloudViewModel.isOnline.collectAsStateWithLifecycle()
    val cloudPendingOutbox by cloudViewModel.pendingOutboxCount.collectAsStateWithLifecycle()
    val cloudIsAdmin by cloudViewModel.isAdmin.collectAsStateWithLifecycle()
    val cloudAdminCheckLoading by cloudViewModel.adminCheckLoading.collectAsStateWithLifecycle()
    val cloudHasAdminPin by cloudViewModel.hasAdminPin.collectAsStateWithLifecycle()
    val cloudAdminPinUnlocked by cloudViewModel.adminPinUnlocked.collectAsStateWithLifecycle()
    val cloudUpdateCheckResult by cloudViewModel.updateCheckResult.collectAsStateWithLifecycle()
    val cloudShowUpdateDialog by cloudViewModel.showUpdateDialog.collectAsStateWithLifecycle()
    val cloudDownloadState by cloudViewModel.apkDownloadState.collectAsStateWithLifecycle()
    val cloudActiveAnnouncements by cloudViewModel.activeAnnouncements.collectAsStateWithLifecycle()
    val cloudAdminAnnouncements by cloudViewModel.adminAnnouncements.collectAsStateWithLifecycle()
    val cloudReadIds by cloudViewModel.readNotificationIds.collectAsStateWithLifecycle()
    val cloudUnreadCount by cloudViewModel.unreadCount.collectAsStateWithLifecycle()
    val cloudRemoteRelease by cloudViewModel.remoteReleaseDraft.collectAsStateWithLifecycle()
    val cloudAdminActionMessage by cloudViewModel.adminActionMessage.collectAsStateWithLifecycle()
    val cloudRemoteControl by cloudViewModel.remoteControl.collectAsStateWithLifecycle()
    val cloudPromoBanner by cloudViewModel.promoBanner.collectAsStateWithLifecycle()
    val cloudUnvotedPoll by cloudViewModel.unvotedPoll.collectAsStateWithLifecycle()
    val cloudTeacherAccount by cloudViewModel.teacherAccount.collectAsStateWithLifecycle()
    val cloudVerifiedNowMillis by cloudViewModel.verifiedNowMillis.collectAsStateWithLifecycle()
    val cloudTrialStatus by cloudViewModel.trialAccessStatus.collectAsStateWithLifecycle()
    val cloudRemainingTrialDays by cloudViewModel.remainingTrialDays.collectAsStateWithLifecycle()
    val cloudUnlockedPointsTools by cloudViewModel.unlockedPointsTools.collectAsStateWithLifecycle()
    val cloudUserActionMessage by cloudViewModel.userActionMessage.collectAsStateWithLifecycle()
    val cloudBusy by cloudViewModel.busyOperation.collectAsStateWithLifecycle()
    val cloudAdminPolls by cloudViewModel.adminAllPolls.collectAsStateWithLifecycle()
    val cloudAdminPollTallies by cloudViewModel.adminPollTallies.collectAsStateWithLifecycle()
    val cloudAdminUsers by cloudViewModel.adminAllUsers.collectAsStateWithLifecycle()
    val cloudAdminFeedback by cloudViewModel.adminAllFeedback.collectAsStateWithLifecycle()
    val cloudAdminParentReports by cloudViewModel.adminAllParentReports.collectAsStateWithLifecycle()
    val cloudAnalyticsSummary by cloudViewModel.analyticsSummary.collectAsStateWithLifecycle()
    val cloudMyParentReports by cloudViewModel.myParentReports.collectAsStateWithLifecycle()
    val cloudAdminPermissions by cloudViewModel.adminPermissions.collectAsStateWithLifecycle()
    val cloudSubAdmins by cloudViewModel.adminSubAdmins.collectAsStateWithLifecycle()
    val cloudActivationCodes by cloudViewModel.adminActivationCodes.collectAsStateWithLifecycle()
    val cloudNameChangeRequests by cloudViewModel.adminNameChangeRequests.collectAsStateWithLifecycle()
    val cloudAllComparisons by cloudViewModel.adminAllComparisons.collectAsStateWithLifecycle()
    val cloudInternalUpdateVisible by cloudViewModel.internalUpdateBannerVisible.collectAsStateWithLifecycle()
    val cloudShowInactivityDua by cloudViewModel.showInactivityDuaDialog.collectAsStateWithLifecycle()
    val cloudChatMessages by cloudViewModel.teacherChatMessages.collectAsStateWithLifecycle()
    val cloudDirectChatMessages by cloudViewModel.directChatMessages.collectAsStateWithLifecycle()
    val cloudChatRemaining by cloudViewModel.chatRemainingToday.collectAsStateWithLifecycle()
    val cloudMyComparisons by cloudViewModel.myGradeComparisons.collectAsStateWithLifecycle()
    val cloudJoinedComparisons by cloudViewModel.joinedGradeComparisons.collectAsStateWithLifecycle()
    val cloudSharedStudyGuides by cloudViewModel.sharedStudyGuides.collectAsStateWithLifecycle()
    val cloudStudyGuideBusy by cloudViewModel.studyGuideBusy.collectAsStateWithLifecycle()

    val uiPrefs = remember { context.getSharedPreferences("rafiq_ui_prefs", Context.MODE_PRIVATE) }
    var isAdminUnlocked by remember(state.profile.adminSectionUnlocked) {
        mutableStateOf(
            uiPrefs.getBoolean("admin_section_unlocked", false) || state.profile.adminSectionUnlocked
        )
    }
    LaunchedEffect(state.profile.adminSectionUnlocked) {
        if (state.profile.adminSectionUnlocked && !uiPrefs.getBoolean("admin_section_unlocked", false)) {
            uiPrefs.edit().putBoolean("admin_section_unlocked", true).apply()
            isAdminUnlocked = true
        }
    }

    var currentTab by remember { mutableStateOf(MainNavTab.HOME) }
    var activeTool by remember { mutableStateOf<ToolRoute?>(null) }

    fun unlockAdminSection() {
        isAdminUnlocked = true
        uiPrefs.edit().putBoolean("admin_section_unlocked", true).apply()
        if (!state.profile.adminSectionUnlocked) {
            viewModel.updateProfile(state.profile.copy(adminSectionUnlocked = true))
        }
        activeTool = null
        currentTab = MainNavTab.HOME
    }
    var toolStudentContext by remember { mutableStateOf<Student?>(null) }
    var certInitialTab by remember { mutableStateOf(0) }
    var inspectedStudentFromSearch by remember { mutableStateOf<Student?>(null) }
    var showNotificationsInbox by remember { mutableStateOf(false) }
    var showAdminNotificationsPanel by remember { mutableStateOf(false) }
    var showCloudBackupDialog by remember { mutableStateOf(false) }
    var showReferralRatingDialog by remember { mutableStateOf(false) }
    var showDeveloperFeedbackDialog by remember { mutableStateOf(false) }
    var showPromoBannerDialog by remember { mutableStateOf(false) }
    var showTeacherChatDialog by remember { mutableStateOf(false) }
    var showGradeComparisonDialog by remember { mutableStateOf(false) }
    var showActivationCodeDialog by remember { mutableStateOf(false) }
    var showNameChangeDialog by remember { mutableStateOf(false) }
    var parentReportTargetStudent by remember { mutableStateOf<Student?>(null) }
    var showParentReportViewer by remember { mutableStateOf(false) }
    var parentReportViewerInitialCode by remember { mutableStateOf("") }
    var dismissedPollIdForSession by remember { mutableStateOf<String?>(null) }
    var dismissedInternalRevision by remember { mutableStateOf(0L) }
    var unlockToolPrompt by remember { mutableStateOf<Pair<ToolRoute, Int>?>(null) }

    fun openToolTracked(route: ToolRoute) {
        cloudViewModel.recordToolOpened(route)
        activeTool = route
    }

    LaunchedEffect(Unit) {
        val updated = viewModel.ensureAutoGeneratedAccountCredentials()
        cloudViewModel.checkSevenDayInactivity(updated.lastAppActiveMillis) { nowMs ->
            viewModel.markLastActiveNow(nowMs)
        }
    }

    LaunchedEffect(initialDeepLinkReportCode) {
        if (!initialDeepLinkReportCode.isNullOrBlank()) {
            parentReportViewerInitialCode = initialDeepLinkReportCode
            showParentReportViewer = true
            onDeepLinkConsumed()
        }
    }

    var splashDismissed by remember { mutableStateOf(false) }
    var sessionUnlocked by remember { mutableStateOf(false) }
    var safeEditUnlocked by remember { mutableStateOf(false) }
    var showSafeEditUnlockDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.gradeSaveFeedback.collect { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    LaunchedEffect(cloudUserActionMessage) {
        val msg = cloudUserActionMessage
        if (!msg.isNullOrBlank() &&
            !showCloudBackupDialog &&
            !showReferralRatingDialog &&
            !showDeveloperFeedbackDialog &&
            !showGradeComparisonDialog &&
            !showActivationCodeDialog
        ) {
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            cloudViewModel.clearUserActionMessage()
        }
    }

    LaunchedEffect(
        state.profile.accountUsername,
        state.profile.teacherName,
        state.profile.subjectSpecialty,
        selectedGrade,
        state.profile.unlockedPointsTools,
        state.profile.dismissedNotificationIds,
        state.profile.deviceInstallId,
        state.profile.chatActiveOnlineVisible,
        cloudUser?.uid
    ) {
        cloudViewModel.updateTeacherContext(
            username = state.profile.accountUsername,
            teacherName = state.profile.teacherName,
            specialtyId = state.profile.subjectSpecialty.id,
            gradeId = selectedGrade.id,
            localUnlockedTools = state.profile.unlockedPointsTools,
            dismissedNotificationIds = state.profile.dismissedNotificationIds,
            deviceInstallId = state.profile.deviceInstallId,
            isOnlineVisible = state.profile.chatActiveOnlineVisible
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            cloudViewModel.recordUsageTick(15L)
        }
    }

    LaunchedEffect(Unit) {
        val credManager = CredentialManager.create(context)
        attemptAutoSignIn(
            context = context,
            credentialManager = credManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = this
        )
    }

    LaunchedEffect(Unit) {
        cloudViewModel.newSystemAnnouncementEvent.collect { announcement ->
            if (state.profile.notificationsMasterEnabled) {
                NotificationHelper.showNotification(
                    context = context,
                    id = announcement.id.hashCode(),
                    title = announcement.title.ifBlank { "إشعار جديد من الإدارة" },
                    body = announcement.body
                )
            }
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            state.profile.notificationsMasterEnabled &&
            state.profile.lessonNotificationsEnabled
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    TeacherLedgerTheme(
        palette = state.profile.themePalette,
        specialty = state.profile.subjectSpecialty,
        useSpecialtyTheme = state.profile.useSpecialtyTheme,
        textTone = state.profile.textTone,
        darkTheme = state.profile.isDarkMode,
        neonGlowEnabled = state.profile.neonGlowEnabled,
        fontOption = state.profile.fontOption,
        fontScaleMultiplier = state.profile.fontScaleMultiplier,
        seasonalThemeId = cloudRemoteControl.seasonalTheme,
        customColorsEnabled = state.profile.customColorsEnabled,
        customTextColorHex = state.profile.customTextColorHex,
        customButtonColorHex = state.profile.customButtonColorHex,
        customBgColorHex = state.profile.customBgColorHex,
        customCardAccentColorHex = state.profile.customCardAccentColorHex
    ) {
        val ext = LocalExtendedColors.current
        val hasWeeklyNameGift = cloudRemoteControl.allowWeeklyNameChange ||
            (cloudTeacherAccount?.allowWeeklyNameChange == true)
        val activeBgKey = activeTool?.name ?: currentTab.name
        val resolvedBgFileName = state.profile.sectionBgImageFiles[activeBgKey]
            ?: state.profile.appBgImageFileName
        val bgFile = viewModel.getCustomBgFile(resolvedBgFileName)
        val bgBmp = remember(bgFile?.absolutePath, bgFile?.lastModified()) {
            bgFile?.takeIf { it.exists() }?.let { f ->
                runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            if (bgBmp != null) {
                Image(
                    bitmap = bgBmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.background.copy(
                                alpha = if (state.profile.isDarkMode) 0.72f else 0.78f
                            )
                        )
                )
            }
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = if (bgBmp != null) Color.Transparent else MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (activeTool == null) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                            color = ext.bottomNavBg,
                            tonalElevation = 6.dp,
                            shadowElevation = 14.dp,
                            border = BorderStroke(
                                width = if (ext.neonGlowEnabled) 1.3.dp else 1.dp,
                                color = if (ext.neonGlowEnabled) {
                                    ext.bottomNavActivePill.copy(alpha = 0.45f)
                                } else {
                                    Color.White.copy(alpha = 0.14f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MainNavTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    val pillShape = RoundedCornerShape(16.dp)
                                    val tabTapCtrl = rememberNeonTapController()
                                    val tabGlowColor = if (selected) ext.bottomNavActivePill else MaterialTheme.colorScheme.primary
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .padding(horizontal = 2.dp)
                                            .neonTapFeedback(tabTapCtrl, tabGlowColor, 16.dp)
                                            .clip(pillShape)
                                            .then(
                                                if (selected) {
                                                    Modifier.background(
                                                        Brush.verticalGradient(
                                                            listOf(
                                                                ext.bottomNavActivePill,
                                                                ext.bottomNavActivePill.copy(alpha = 0.88f)
                                                            )
                                                        )
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            .clickable {
                                                tabTapCtrl.trigger {
                                                    if (tab == MainNavTab.TOOLS) {
                                                        openToolTracked(ToolRoute.SETTINGS)
                                                    } else {
                                                        currentTab = tab
                                                    }
                                                }
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp)
                                            .testTag("nav_tab_${tab.name}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.titleAr,
                                                tint = if (selected) ext.bottomNavActiveContent else ext.bottomNavInactiveContent,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Text(
                                                text = tab.titleAr,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                color = if (selected) ext.bottomNavActiveContent else ext.bottomNavInactiveContent,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    InternalUpdateMarqueeStrip(
                        visible = cloudInternalUpdateVisible && cloudRemoteControl.internalUpdateEnabled,
                        text = cloudRemoteControl.internalUpdateText,
                        onDismiss = cloudViewModel::dismissInternalUpdateBanner
                    )

                    val currentRouteKey = activeTool?.name ?: currentTab.name
                    val showOuterPromo = cloudPromoBanner.enabled &&
                        cloudPromoBanner.title.isNotBlank() &&
                        !(activeTool == null && currentTab == MainNavTab.HOME) &&
                        (cloudPromoBanner.placement == "ALL_SCREENS" ||
                            (cloudPromoBanner.placement == "SPECIFIC_SECTION" && cloudPromoBanner.targetRoute == currentRouteKey))

                    if (showOuterPromo && cloudPromoBanner.position != "BOTTOM") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showPromoBannerDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                Text(cloudPromoBanner.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("عرض", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }

                    // Live In-App Lesson Alert Banner (1 minute before lesson start / end + 6s chime)
                    AnimatedVisibility(
                        visible = inAppAlert != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        inAppAlert?.let { alert ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("in_app_lesson_alert_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = alert.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = alert.body,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    TextButton(
                                        onClick = viewModel::dismissInAppAlert,
                                        modifier = Modifier.testTag("dismiss_inapp_alert_btn")
                                    ) {
                                        Text("إيقاف", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Persistent Live & Next Period Banner (shown smoothly on non-Home tabs; Home has dedicated top header + Next Period card)
                    if (state.profile.liveScheduleBannerEnabled && activeTool == null && currentTab != MainNavTab.HOME) {
                        val teacherKey = state.profile.timetableTeacherKey.ifBlank { state.profile.teacherName }
                        val banner = remember(state.timetable, state.dayPeriodTimes, teacherKey, nowMillis) {
                            com.example.utils.LessonAlarmScheduler.computeLiveBannerInfo(
                                timetable = state.timetable,
                                dayPeriodTimes = state.dayPeriodTimes,
                                teacherKey = teacherKey
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (banner.hasLessonNow) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                ext.cardElevated
                            },
                            border = BorderStroke(
                                if (ext.neonGlowEnabled) 1.3.dp else 1.dp,
                                if (banner.hasLessonNow) MaterialTheme.colorScheme.primary else ext.borderSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clickable { currentTab = MainNavTab.TIMETABLE }
                                .testTag("live_schedule_top_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (banner.hasLessonNow) Icons.Filled.PlayCircleFilled else Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = banner.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (banner.hasLessonNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (banner.hasLessonNow && banner.hasNextPeriod) {
                                            "${banner.subtitle} | القادمة: ${banner.nextPeriodTitle}"
                                        } else banner.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                if (banner.timeRange.isNotBlank()) {
                                    Text(
                                        text = banner.timeRange,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(
                            targetState = activeTool to currentTab,
                            transitionSpec = {
                                (fadeIn(tween(240)) + slideInHorizontally(tween(260)) { it / 12 }) togetherWith
                                    (fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { -it / 12 })
                            },
                            label = "screen_transition"
                        ) { (tool, tab) ->
                            when (tool) {
                                ToolRoute.TEACHER_PROFILE -> {
                                    TeacherProfileScreen(
                                        state = state,
                                        getMediaFile = viewModel::getProfileMediaFile,
                                        onImportCover = viewModel::importProfileCover,
                                        onImportSplashLogo = viewModel::importSplashLogo,
                                        onUpdateProfile = viewModel::updateProfile,
                                        currentRankTitle = cloudTeacherAccount?.promotionTitle.orEmpty(),
                                        hasWeeklyNameGift = hasWeeklyNameGift,
                                        onRequestNameChange = { showNameChangeDialog = true },
                                        onBack = { activeTool = null }
                                    )
                                }

                                ToolRoute.STUDENT_PAPERS -> {
                                    StudentPapersScreen(
                                        state = state,
                                        selectedGrade = selectedGrade,
                                        selectedSection = selectedSection,
                                        getPaperFile = viewModel::getStudentPaperFile,
                                        onAddPaperGroup = viewModel::addStudentPaperGroup,
                                        onAddMorePages = viewModel::addPagesToStudentPaperGroup,
                                        onDeletePaperGroup = viewModel::deleteStudentPaperGroup,
                                        onBack = { activeTool = null }
                                    )
                                }

                                ToolRoute.EXAM_ARCHIVE -> {
                                    ExamArchiveScreen(
                                        state = state,
                                        selectedGrade = selectedGrade,
                                        getExamFile = viewModel::getExamArchiveFile,
                                        onAddExamItem = viewModel::addExamArchiveItem,
                                        onDeleteExamItem = viewModel::deleteExamArchiveItem,
                                        onBack = { activeTool = null }
                                    )
                                }

                                ToolRoute.SECTION_STARS -> {
                                    SectionStarsScreen(
                                        state = state,
                                        selectedGrade = selectedGrade,
                                        selectedSection = selectedSection,
                                        getStarPhoto = viewModel::getStarPhotoFile,
                                        onSelectClass = viewModel::selectClass,
                                        onSaveStar = viewModel::saveSectionStar,
                                        onDeleteStar = viewModel::deleteSectionStar,
                                        onBack = { activeTool = null }
                                    )
                                }

                                ToolRoute.QURAN_ATHKAR_PRAYER -> {
                                    IslamicCompanionScreen(
                                        state = state,
                                        onUpdateProfile = viewModel::updateProfile,
                                        onUpdateQuranPosition = viewModel::updateQuranPosition,
                                        onToggleAyahColor = viewModel::toggleQuranAyahColor,
                                        onUpdatePrayerSettings = viewModel::updatePrayerSettings,
                                        onDetectGpsLocation = viewModel::detectAndSaveGpsPrayerLocation,
                                        onBack = { activeTool = null }
                                    )
                                }

                                ToolRoute.DAILY_LEDGER -> {
                                    DailyLedgerScreen(
                                        state = state,
                                        selectedGrade = selectedGrade,
                                        selectedSection = selectedSection,
                                        activeLedger = activeLedger,
                                        activeLedgerId = activeLedgerId,
                                        onSelectClass = viewModel::selectClass,
                                        onSelectLedger = viewModel::setActiveLedger,
                                        onSelectLedgerId = viewModel::setActiveLedgerId,
                                        onAddColumn = viewModel::addDailyColumn,
                                        onUpdateColumn = viewModel::updateDailyColumn,
                                        onDeleteColumn = viewModel::deleteDailyColumn,
                                        onSetDailyScore = viewModel::setDailyScore,
                                        onTransferTotalToMain = viewModel::transferDailyTotalToMainGradebook,
                                        onUpdateDailyCalcMode = { mode ->
                                            viewModel.updateGradebookSettings(
                                                state.gradebookSettings.copy(dailyCalcMode = mode)
                                            )
                                        },
                                        onBack = { activeTool = null }
                                    )
                                }

                                ToolRoute.QUESTION_BANK -> {
                                    QuestionBankScreen(
                                        state = state,
                                        onAddChapter = viewModel::addQuestionChapter,
                                        onUpdateChapter = viewModel::updateQuestionChapter,
                                        onDeleteChapter = viewModel::deleteQuestionChapter,
                                        onAddCustomSection = viewModel::addCustomQuestionSection,
                                        onDeleteCustomSection = viewModel::deleteCustomQuestionSection,
                                        onSaveQuestion = viewModel::saveBankQuestion,
                                        onDeleteQuestion = viewModel::deleteBankQuestion,
                                        onImportQuestionImage = viewModel::importQuestionImageFromUri,
                                        getQuestionImageFile = viewModel::getQuestionImageFile,
                                        onUpdateSecurity = viewModel::updateQuestionBankSecurity,
                                        onToggleTheme = viewModel::toggleQuestionBankTheme,
                                        onBack = { activeTool = null }
                                    )
                                }

                                ToolRoute.EXAM_WRITER -> {
                                    ExamWriterScreen(
                                        state = state,
                                        onBack = { activeTool = null }
                                    )
                                }

                                ToolRoute.VAULT_EXTRACTOR -> {
                                    VaultExtractorAndExtrasScreen(
                                        state = state,
                                        initialTab = 0,
                                        onAddVaultNote = viewModel::addVaultNote,
                                        onDeleteVaultNote = viewModel::deleteVaultNote,
                                        onUpdateVaultPin = viewModel::updateVaultPin,
                                        onSendTextToGradeImport = { extracted ->
                                            viewModel.sendExtractedTextToGradeImport(extracted)
                                            activeTool = null
                                            currentTab = MainNavTab.GRADEBOOK
                                        },
                                        onBack = { activeTool = null }
                                    )
                                }

                                ToolRoute.CERTIFICATES_AND_QR -> {
                                    CertificatesAndQrScreen(
                                        state = state,
                                        selectedGrade = selectedGrade,
                                        selectedSection = selectedSection,
                                        initialStudent = toolStudentContext,
                                        initialTab = certInitialTab,
                                        onSelectClass = viewModel::selectClass,
                                        onBack = {
                                            toolStudentContext = null
                                            activeTool = null
                                        }
                                    )
                                }

                                ToolRoute.LIBRARY_BOOKS -> {
                                    LibraryBooksScreen(
                                        state = state,
                                        getLibraryFile = viewModel::getLibraryFile,
                                        onImportFile = viewModel::importLibraryFile,
                                        onDeleteFile = viewModel::deleteLibraryFile,
                                        onUpdateFilePage = viewModel::updateLibraryFilePage,
                                        loadPageAnnotations = viewModel::loadLibraryPageAnnotations,
                                        savePageAnnotations = viewModel::saveLibraryPageAnnotations,
                                        onBack = { activeTool = null }
                                    )
                                }

                                ToolRoute.TASKS_AND_CHAPTERS -> {
                                    TasksAndChaptersScreen(
                                        state = state,
                                        onAddTask = viewModel::addTask,
                                        onToggleTask = viewModel::toggleTask,
                                        onDeleteTask = viewModel::deleteTask,
                                        onAddChapter = viewModel::addChapter,
                                        onToggleChapter = viewModel::toggleChapter,
                                        onDeleteChapter = viewModel::deleteChapter,
                                        onBack = { activeTool = null }
                                    )
                                }

                                ToolRoute.QUICK_NOTES_TIMER -> {
                                    QuickNotesAndTimerScreen(
                                        state = state,
                                        onAddQuickNote = viewModel::addQuickNote,
                                        onDeleteQuickNote = viewModel::deleteQuickNote,
                                        onBack = { activeTool = null }
                                    )
                                }

                                ToolRoute.STUDY_GUIDES -> {
                                    SharedStudyGuidesScreen(
                                        guides = cloudSharedStudyGuides,
                                        currentUser = cloudUser,
                                        isAdmin = cloudIsAdmin || cloudAdminPermissions.canManageRemoteControl,
                                        defaultTeacherName = state.profile.teacherName,
                                        defaultSubjectName = state.profile.subjectName,
                                        busy = cloudStudyGuideBusy,
                                        onUploadPdfGuide = cloudViewModel::uploadSharedPdfGuide,
                                        onDownloadAndPrepareFile = cloudViewModel::downloadSharedPdfGuide,
                                        onDeleteGuide = cloudViewModel::deleteSharedPdfGuide,
                                        onBack = { activeTool = null }
                                    )
                                }

                                ToolRoute.SETTINGS -> {
                                    SettingsScreen(
                                        state = state,
                                        onUpdateProfile = viewModel::updateProfile,
                                        onUpdateGradebookSettings = viewModel::updateGradebookSettings,
                                        onSetLedgerActivation = viewModel::setLedgerActivation,
                                        onResetDefaultSchedule = viewModel::resetSchoolDefaultBellSchedule,
                                        onTriggerInAppTestAlert = viewModel::triggerTestInAppAlert,
                                        onUpdateToolOrder = viewModel::updateToolOrder,
                                        onWipeAllGrades = viewModel::wipeAllGrades,
                                        onWipeAllStudents = viewModel::wipeAllStudents,
                                        onOpenTeacherProfile = { openToolTracked(ToolRoute.TEACHER_PROFILE) },
                                        selectedGrade = selectedGrade,
                                        onSelectPreferredGrade = { g -> viewModel.selectClass(g, selectedSection) },
                                        currentUser = cloudUser,
                                        unreadNotificationsCount = cloudUnreadCount,
                                        updateCheckResult = cloudUpdateCheckResult,
                                        currentPoints = cloudTeacherAccount?.points ?: 0,
                                        isOnline = cloudIsOnline,
                                        pendingOutboxCount = cloudPendingOutbox,
                                        onCheckForUpdates = { cloudViewModel.checkForUpdates(openDialogEvenIfUpToDate = true) },
                                        onOpenNotificationsInbox = { showNotificationsInbox = true },
                                        onOpenAdminNotifications = {
                                            cloudViewModel.refreshAdminStatus()
                                            showAdminNotificationsPanel = true
                                        },
                                        onOpenCloudBackup = { showCloudBackupDialog = true },
                                        onOpenReferralAndRating = { showReferralRatingDialog = true },
                                        onOpenDeveloperFeedback = { showDeveloperFeedbackDialog = true },
                                        onOpenParentReportViewer = {
                                            parentReportViewerInitialCode = ""
                                            showParentReportViewer = true
                                        },
                                        onSignOutCloud = { cloudViewModel.signOut(context) },
                                        onUnlockAdminSection = ::unlockAdminSection,
                                        onOpenActivationCode = { showActivationCodeDialog = true },
                                        onRequestNameChange = { showNameChangeDialog = true },
                                        hasWeeklyNameGift = hasWeeklyNameGift,
                                        onImportAppBg = viewModel::importAppBackgroundImage,
                                        onClearAppBg = viewModel::clearAppBackgroundImage,
                                        onImportSectionBg = viewModel::importSectionBackgroundImage,
                                        onClearSectionBg = viewModel::clearSectionBackgroundImage,
                                        remoteControl = cloudRemoteControl,
                                        isAdmin = cloudIsAdmin || isAdminUnlocked,
                                        onSetPreferDefaultDesign = viewModel::setPreferDefaultDesign,
                                        onBack = { activeTool = null }
                                    )
                                }

                                null -> {
                                    when (tab) {
                                        MainNavTab.HOME -> DashboardScreen(
                                            state = state,
                                            activeDayPeriod = activeDayPeriod,
                                            nowMillis = nowMillis,
                                            onOpenTab = { idx ->
                                                currentTab = MainNavTab.entries[idx.coerceIn(0, 3)]
                                            },
                                            onOpenTool = { route ->
                                                toolStudentContext = null
                                                certInitialTab = 0
                                                openToolTracked(route)
                                            },
                                            onUpdateTeacherIdentity = { newName, newSubject, newSchool, specialty, preferredGrade ->
                                                viewModel.selectClass(preferredGrade, selectedSection)
                                                val chosenNow = state.profile.initialNameChosen || newName.trim().length >= 2
                                                viewModel.updateProfile(
                                                    state.profile.copy(
                                                        teacherName = newName,
                                                        timetableTeacherKey = newName,
                                                        subjectName = newSubject.ifBlank { specialty.titleAr },
                                                        schoolName = newSchool.ifBlank { state.profile.schoolName },
                                                        subjectSpecialty = specialty,
                                                        preferredGrade = preferredGrade,
                                                        initialNameChosen = chosenNow,
                                                        lastNameChangeMillis = if (!state.profile.initialNameChosen && chosenNow) System.currentTimeMillis() else state.profile.lastNameChangeMillis
                                                    )
                                                )
                                            },
                                            onSelectStartupTeacher = { key, displayName, specialty, preferredGrade ->
                                                viewModel.selectTeacherOnStartup(key, displayName, specialty, preferredGrade)
                                            },
                                            onMarkStartupHandled = viewModel::markStartupPromptHandled,
                                            onSetLedgerActivation = viewModel::setLedgerActivation,
                                            onInspectStudent = { st ->
                                                inspectedStudentFromSearch = st
                                            },
                                            onToggleDarkMode = viewModel::toggleDarkMode,
                                            getToolMediaFile = viewModel::getToolMediaFile,
                                            onImportToolImage = viewModel::importToolImage,
                                            onClearToolImage = viewModel::clearToolImage,
                                            onUpdateToolTileColor = viewModel::updateToolTileColor,
                                            onUpdateToolWhiteBackdrop = viewModel::updateToolWhiteBackdrop,
                                            onUpdateToolOrder = viewModel::updateToolOrder,
                                            onUpdateProfile = viewModel::updateProfile,
                                            unreadNotificationsCount = cloudUnreadCount,
                                            onOpenNotificationsInbox = { showNotificationsInbox = true },
                                            remoteControl = cloudRemoteControl,
                                            promoBanner = cloudPromoBanner,
                                            unlockedPointsTools = cloudUnlockedPointsTools,
                                            currentPoints = cloudTeacherAccount?.points ?: 0,
                                            trialStatus = cloudTrialStatus,
                                            remainingTrialDays = cloudRemainingTrialDays,
                                            onOpenPromoBanner = { showPromoBannerDialog = true },
                                            onRequestUnlockPointsTool = { route, cost ->
                                                unlockToolPrompt = route to cost
                                            },
                                            onOpenAdminPanel = {
                                                cloudViewModel.refreshAdminStatus()
                                                showAdminNotificationsPanel = true
                                            },
                                            isAdminUnlocked = isAdminUnlocked,
                                            currentRankTitle = cloudTeacherAccount?.promotionTitle.orEmpty(),
                                            onOpenTeacherChat = { showTeacherChatDialog = true },
                                            onOpenGradeComparison = { showGradeComparisonDialog = true },
                                            onOpenActivationCode = { showActivationCodeDialog = true },
                                            onRequestNameChange = { showNameChangeDialog = true },
                                            onHideTool = viewModel::hideTool,
                                            onUnhideTool = viewModel::unhideTool,
                                            onToggleFavoriteTool = viewModel::toggleFavoriteTool,
                                            onArchiveTool = viewModel::archiveTool,
                                            onUnarchiveTool = viewModel::unarchiveTool,
                                            onSetTileScale = viewModel::setToolTileScale,
                                            onImportSectionBg = viewModel::importSectionBackgroundImage,
                                            onClearSectionBg = viewModel::clearSectionBackgroundImage,
                                            isCurrentUserAdmin = cloudIsAdmin || isAdminUnlocked,
                                            onSetPreferDefaultDesign = viewModel::setPreferDefaultDesign,
                                            onVoiceActionMessage = { msg ->
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        )

                                        MainNavTab.TIMETABLE -> TimetableScreen(
                                            state = state,
                                            activeDayPeriod = activeDayPeriod,
                                            onToggleShowOnlyMine = viewModel::toggleShowOnlyMyLessons,
                                            onUpdateTeacherName = { newName ->
                                                val clean = newName.trim().ifBlank { state.profile.teacherName }
                                                viewModel.updateProfile(
                                                    state.profile.copy(
                                                        teacherName = clean,
                                                        timetableTeacherKey = clean
                                                    )
                                                )
                                            },
                                            onUpdateWorkingDays = viewModel::setWorkingDays,
                                            onAddBatchLessons = viewModel::addBatchTimetableCells,
                                            onClearTimetable = viewModel::clearTimetable,
                                            onUpdateCell = viewModel::updateTimetableCell,
                                            onDeleteCell = viewModel::deleteTimetableCell,
                                            onMoveOrSwapCell = viewModel::moveOrSwapLessonCell,
                                            onSwapDayPeriods = viewModel::swapPeriodsOnDay,
                                            onUpdateDayPeriodTime = viewModel::updateDayPeriodTime,
                                            onApplyDayPeriodPreset = viewModel::applyPeriodPresetForDay,
                                            onResetDefaultSchedule = viewModel::resetSchoolDefaultBellSchedule,
                                            onTriggerInAppTestAlert = viewModel::triggerTestInAppAlert,
                                            onUpdateProfile = viewModel::updateProfile
                                        )

                                        MainNavTab.GRADEBOOK -> GradebookScreen(
                                            state = state,
                                            selectedGrade = selectedGrade,
                                            selectedSection = selectedSection,
                                            activeLedger = activeLedger,
                                            activeLedgerId = activeLedgerId,
                                            safeEditUnlocked = safeEditUnlocked,
                                            onRequestUnlockSafeEdit = { showSafeEditUnlockDialog = true },
                                            todayDateStr = viewModel.todayDateStr(),
                                            pendingExtractedText = pendingExtractedText,
                                            onConsumePendingExtractedText = viewModel::clearPendingExtractedTextForGrades,
                                            onSelectClass = viewModel::selectClass,
                                            onSelectLedger = viewModel::setActiveLedger,
                                            onSelectLedgerId = viewModel::setActiveLedgerId,
                                            onAddCustomLedger = { title, color, tpl ->
                                                viewModel.addCustomLedger(title, color, tpl)
                                            },
                                            onRenameOrStyleLedger = viewModel::renameOrStyleCustomLedger,
                                            onDeleteLedger = viewModel::deleteCustomLedger,
                                            onSetLedgerActivation = viewModel::setLedgerActivation,
                                            onUpdateStudent = viewModel::updateStudent,
                                            onAddStudent = viewModel::addStudent,
                                            onAddStudentsBatch = viewModel::addStudentsBatch,
                                            onMoveStudent = viewModel::moveStudent,
                                            onDeleteStudent = viewModel::deleteStudent,
                                            onToggleTransferred = viewModel::toggleTransferred,
                                            onUpdateBadges = viewModel::setStudentBadges,
                                            onAddAvailableBadge = viewModel::addAvailableBadge,
                                            onUpdateProfile = viewModel::updateProfile,
                                            onBatchSaveGrades = viewModel::batchUpdateStudentGrades,
                                            onToggleAbsenceForDate = { id, dateStr -> viewModel.toggleAbsenceForDate(id, dateStr) },
                                            onUpdateSettings = viewModel::updateGradebookSettings,
                                            onNavigateToQrForStudent = { st ->
                                                toolStudentContext = st
                                                certInitialTab = 1
                                                openToolTracked(ToolRoute.CERTIFICATES_AND_QR)
                                            },
                                            onNavigateToCertForStudent = { st ->
                                                toolStudentContext = st
                                                certInitialTab = 0
                                                openToolTracked(ToolRoute.CERTIFICATES_AND_QR)
                                            },
                                            onOpenDailyLedger = {
                                                openToolTracked(ToolRoute.DAILY_LEDGER)
                                            },
                                            onShareParentReport = { st ->
                                                parentReportTargetStudent = st
                                            },
                                            onExportExcelToFolder = viewModel::exportScopeToExcelInRafiqFolder,
                                            onExportWordToFolder = viewModel::exportScopeToWordInRafiqFolder,
                                            onOpenGradeComparison = { showGradeComparisonDialog = true }
                                        )

                                        MainNavTab.ANALYTICS -> AnalyticsScreen(
                                            state = state,
                                            selectedGrade = selectedGrade,
                                            selectedSection = selectedSection,
                                            activeLedger = activeLedger,
                                            activeLedgerId = activeLedgerId,
                                            onSelectLedger = viewModel::setActiveLedger,
                                            onSelectLedgerId = viewModel::setActiveLedgerId,
                                            onSelectClass = viewModel::selectClass,
                                            onUpdateProfile = viewModel::updateProfile,
                                            onExportExcelToFolder = viewModel::exportScopeToExcelInRafiqFolder,
                                            onExportWordToFolder = viewModel::exportScopeToWordInRafiqFolder,
                                            onOpenGradeComparison = { showGradeComparisonDialog = true }
                                        )

                                        MainNavTab.TOOLS -> Unit
                                    }
                                }
                            }
                        }
                    }

                    if (showOuterPromo && cloudPromoBanner.position == "BOTTOM") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showPromoBannerDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                Text(cloudPromoBanner.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("عرض", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }

            if (state.profile.appLockEnabled && !sessionUnlocked && (splashDismissed || !state.profile.splashEnabled)) {
                AppLockGateScreen(
                    profile = state.profile,
                    onUnlocked = { sessionUnlocked = true }
                )
            }

            if (cloudRemoteControl.maintenanceEnabled && !showAdminNotificationsPanel && (splashDismissed || !state.profile.splashEnabled)) {
                val maintenanceWithEta = if (cloudRemoteControl.maintenanceEtaText.isNotBlank()) {
                    "${cloudRemoteControl.maintenanceMessage}\n\nالوقت المتوقع للعودة: ${cloudRemoteControl.maintenanceEtaText}"
                } else {
                    cloudRemoteControl.maintenanceMessage
                }
                MaintenanceLockScreen(
                    maintenanceMessage = maintenanceWithEta,
                    onSevenTapAdminBypass = {
                        cloudViewModel.refreshAdminStatus()
                        showAdminNotificationsPanel = true
                    }
                )
            } else if (!cloudIsAdmin &&
                (cloudTrialStatus == TrialAccessStatus.TRIAL_EXPIRED || cloudTrialStatus == TrialAccessStatus.SUSPENDED) &&
                !showAdminNotificationsPanel &&
                (splashDismissed || !state.profile.splashEnabled)
            ) {
                TrialAccessGateScreen(
                    status = cloudTrialStatus,
                    currentUser = cloudUser,
                    onRefreshAccess = cloudViewModel::refreshTrialClock,
                    onSevenTapAdminBypass = {
                        cloudViewModel.refreshAdminStatus()
                        showAdminNotificationsPanel = true
                    }
                )
            }

            if (state.profile.splashEnabled && !splashDismissed) {
                CustomSplashOverlay(
                    profile = state.profile,
                    logoFile = viewModel.getProfileMediaFile(state.profile.splashLogoFileName),
                    onFinished = { splashDismissed = true }
                )
            }
        }

        if (showSafeEditUnlockDialog) {
            SafeGradeEditUnlockDialog(
                profile = state.profile,
                onUnlocked = {
                    safeEditUnlocked = true
                    showSafeEditUnlockDialog = false
                },
                onDismiss = { showSafeEditUnlockDialog = false }
            )
        }

        inspectedStudentFromSearch?.let { st ->
            val latestSt = state.students.find { it.id == st.id } ?: st
            val absCount = state.absences.count { it.studentId == latestSt.id }
            StudentActionDialog(
                student = latestSt,
                profile = state.profile,
                settings = state.gradebookSettings,
                absencesCount = absCount,
                initialLedger = activeLedger,
                activeLedgerId = activeLedgerId,
                safeEditUnlocked = safeEditUnlocked,
                onRequestUnlockSafeEdit = { showSafeEditUnlockDialog = true },
                onToggleTransferred = viewModel::toggleTransferred,
                onUpdateBadges = viewModel::setStudentBadges,
                onAddAvailableBadge = viewModel::addAvailableBadge,
                onDismiss = { inspectedStudentFromSearch = null },
                onSave = viewModel::updateStudent,
                onMoveSection = { g, sec -> viewModel.moveStudent(latestSt.id, g, sec) },
                onDelete = { viewModel.deleteStudent(latestSt.id) },
                onOpenQr = {
                    inspectedStudentFromSearch = null
                    toolStudentContext = latestSt
                    certInitialTab = 1
                    openToolTracked(ToolRoute.CERTIFICATES_AND_QR)
                },
                onOpenCertificate = {
                    inspectedStudentFromSearch = null
                    toolStudentContext = latestSt
                    certInitialTab = 0
                    openToolTracked(ToolRoute.CERTIFICATES_AND_QR)
                },
                onShareParentReport = {
                    inspectedStudentFromSearch = null
                    parentReportTargetStudent = latestSt
                }
            )
        }

        if (cloudShowUpdateDialog) {
            AppUpdateDialog(
                updateState = cloudUpdateCheckResult,
                downloadState = cloudDownloadState,
                currentUser = cloudUser,
                onStartDownload = { url -> cloudViewModel.startApkDownload(context, url) },
                onRetryCheck = { cloudViewModel.checkForUpdates(openDialogEvenIfUpToDate = true) },
                onResetDownload = cloudViewModel::resetApkDownloadState,
                onDismiss = cloudViewModel::dismissUpdateDialogIfAllowed
            )
        }

        if (showNotificationsInbox) {
            NotificationsInboxDialog(
                currentUser = cloudUser,
                announcements = cloudActiveAnnouncements,
                readIds = cloudReadIds,
                onMarkRead = cloudViewModel::markNotificationRead,
                onMarkAllRead = cloudViewModel::markAllRead,
                onDeleteNotifications = { ids ->
                    viewModel.dismissNotificationIds(ids)
                    cloudViewModel.markNotificationsDismissed(ids)
                },
                onDismiss = { showNotificationsInbox = false }
            )
        }

        val activePollToDisplay = cloudUnvotedPoll
        if (!cloudRemoteControl.maintenanceEnabled &&
            cloudTrialStatus != TrialAccessStatus.TRIAL_EXPIRED &&
            cloudTrialStatus != TrialAccessStatus.SUSPENDED &&
            !showAdminNotificationsPanel &&
            activePollToDisplay != null &&
            activePollToDisplay.id != dismissedPollIdForSession
        ) {
            PollVotingDialog(
                poll = activePollToDisplay,
                onVote = { pollId, optionIdx ->
                    cloudViewModel.voteOnPoll(pollId, optionIdx)
                },
                onDismissTemporarily = {
                    dismissedPollIdForSession = activePollToDisplay.id
                }
            )
        }

        if (showPromoBannerDialog) {
            PromoBannerDialog(
                promo = cloudPromoBanner,
                onDismiss = { showPromoBannerDialog = false }
            )
        }

        if (showCloudBackupDialog) {
            TeacherAccountBackupDialog(
                currentUser = cloudUser,
                initialUsername = state.profile.accountUsername,
                autoUsername = state.profile.autoGeneratedUsername,
                passwordChangeIntervalDays = cloudRemoteControl.passwordChangeIntervalDays,
                teacherAccount = cloudTeacherAccount,
                trialStatus = cloudTrialStatus,
                remainingTrialDays = cloudRemainingTrialDays,
                studentCount = state.students.size,
                isOnline = cloudIsOnline,
                pendingOutboxCount = cloudPendingOutbox,
                busy = cloudBusy,
                actionMessage = cloudUserActionMessage,
                onClearMessage = cloudViewModel::clearUserActionMessage,
                onBackupEncrypted = { username, password ->
                    cloudViewModel.backupAllDataEncrypted(
                        username = username,
                        password = password,
                        plainStateJson = viewModel.exportStateJsonString(),
                        studentCount = state.students.size
                    ) { savedUsername ->
                        viewModel.updateProfile(state.profile.copy(accountUsername = savedUsername))
                    }
                },
                onRestoreEncrypted = { username, password ->
                    cloudViewModel.restoreAllDataEncrypted(username, password) { restoredUsername, plainJson ->
                        viewModel.parseStateJsonString(plainJson)?.let { parsed ->
                            viewModel.replaceFullState(
                                parsed.copy(profile = parsed.profile.copy(accountUsername = restoredUsername))
                            )
                        }
                    }
                },
                onChangePassword = { username, newPass ->
                    cloudViewModel.changePasswordWithCooldown(
                        username = username,
                        newPassword = newPass,
                        lastPasswordChangeMillis = state.profile.lastPasswordChangeMillis
                    ) { changedAtMs ->
                        viewModel.recordPasswordChanged(changedAtMs)
                    }
                },
                onOpenActivationCode = {
                    showCloudBackupDialog = false
                    showActivationCodeDialog = true
                },
                onDismiss = { showCloudBackupDialog = false }
            )
        }

        if (showReferralRatingDialog) {
            ReferralAndRatingDialog(
                currentUser = cloudUser,
                teacherAccount = cloudTeacherAccount,
                busy = cloudBusy,
                actionMessage = cloudUserActionMessage,
                onClearMessage = cloudViewModel::clearUserActionMessage,
                onRedeemCode = cloudViewModel::redeemReferralCode,
                onSubmitRating = cloudViewModel::submitAppRating,
                onDismiss = { showReferralRatingDialog = false }
            )
        }

        if (showDeveloperFeedbackDialog) {
            DeveloperFeedbackDialog(
                currentUser = cloudUser,
                lockedUntilMillis = state.profile.feedbackLockedUntilMillis,
                verifiedNowMillis = cloudVerifiedNowMillis,
                lastComplaintText = state.profile.lastComplaintText,
                busy = cloudBusy,
                actionMessage = cloudUserActionMessage,
                onClearMessage = cloudViewModel::clearUserActionMessage,
                onSubmitFeedback = { category, msg, onSaved ->
                    cloudViewModel.submitFeedbackToDeveloper(category, msg) { _ ->
                        val wc = BackupCryptoHelper.countWords(msg)
                        if (wc > 100) {
                            val lockUntil = cloudVerifiedNowMillis + 3L * 86_400_000L
                            viewModel.updateProfile(
                                state.profile.copy(
                                    feedbackLockedUntilMillis = lockUntil,
                                    lastComplaintText = msg
                                )
                            )
                        }
                        onSaved(com.example.data.DeveloperFeedbackRecord())
                    }
                },
                onDismiss = { showDeveloperFeedbackDialog = false }
            )
        }

        parentReportTargetStudent?.let { st ->
            ShareParentReportDialog(
                student = st,
                state = state,
                activeLedgerId = activeLedgerId,
                myReports = cloudMyParentReports,
                busy = cloudBusy,
                onPublishReport = cloudViewModel::publishParentReport,
                onDismiss = { parentReportTargetStudent = null }
            )
        }

        if (showParentReportViewer) {
            ParentReportViewerDialog(
                initialCode = parentReportViewerInitialCode,
                currentUser = cloudUser,
                busy = cloudBusy,
                onLookupCode = cloudViewModel::lookupParentReport,
                onDismiss = { showParentReportViewer = false }
            )
        }

        unlockToolPrompt?.let { (route, cost) ->
            val currentPoints = cloudTeacherAccount?.points ?: 0
            AlertDialog(
                onDismissRequest = { unlockToolPrompt = null },
                icon = {
                    Icon(route.icon, contentDescription = null, tint = GoldBright)
                },
                title = { Text("فتح قسم (${route.shortTitleAr}) بالنقاط") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("يتطلب فتح هذا القسم استبدال $cost نقطة من رصيدك السحابي.")
                        Text(
                            text = "رصيدك الحالي: $currentPoints نقطة ★",
                            color = GoldBright,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                confirmButton = {
                    if (currentPoints >= cost) {
                        Button(
                            enabled = !cloudBusy,
                            onClick = {
                                cloudViewModel.unlockToolWithPoints(route, cost) {
                                    viewModel.unlockPointsTool(route.name)
                                    unlockToolPrompt = null
                                    openToolTracked(route)
                                }
                            }
                        ) { Text("فتح القسم الآن ($cost ★)") }
                    } else {
                        Button(
                            onClick = {
                                unlockToolPrompt = null
                                showReferralRatingDialog = true
                            }
                        ) { Text("اكسب نقاطاً الآن") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { unlockToolPrompt = null }) { Text("إلغاء") }
                }
            )
        }

        if (cloudShowInactivityDua && (splashDismissed || !state.profile.splashEnabled)) {
            InactivityDuaDialog(
                message = cloudRemoteControl.inactivityReminderMessage,
                teacherName = state.profile.teacherName,
                onDismiss = cloudViewModel::dismissInactivityDuaDialog
            )
        }

        if (showTeacherChatDialog) {
            TeacherChatDialog(
                currentUser = cloudUser,
                specialtyId = state.profile.subjectSpecialty.id,
                specialtyTitleAr = state.profile.subjectName.ifBlank { state.profile.subjectSpecialty.titleAr },
                gradeId = selectedGrade.id,
                messages = cloudChatMessages,
                remainingToday = cloudChatRemaining,
                expiryHours = cloudRemoteControl.chatExpiryHours,
                onSendMessage = cloudViewModel::sendChatMessage,
                onDismiss = { showTeacherChatDialog = false },
                directMessages = cloudDirectChatMessages,
                isOnlineVisible = state.profile.chatActiveOnlineVisible,
                onToggleOnlineVisible = { visible ->
                    viewModel.setChatActiveOnlineVisible(visible)
                    cloudViewModel.toggleOnlinePresence(visible)
                },
                dmThreadIdFor = cloudViewModel::dmThreadIdFor,
                onFindUserByUsername = cloudViewModel::findChatUserByUsername,
                onSendMessageV2 = cloudViewModel::sendChatMessageV2,
                onEditMessage = cloudViewModel::editChatMessage,
                onDeleteForMe = cloudViewModel::deleteChatMessageForMe,
                onDeleteForEveryone = cloudViewModel::deleteChatMessageForEveryone,
                onMarkDeliveredAndRead = cloudViewModel::markMessagesDeliveredAndRead
            )
        }

        if (showGradeComparisonDialog) {
            CrossSubjectComparisonDialog(
                currentUser = cloudUser,
                state = state,
                selectedGrade = selectedGrade,
                selectedSection = selectedSection,
                activeLedgerId = activeLedgerId,
                myComparisons = cloudMyComparisons,
                joinedComparisons = cloudJoinedComparisons,
                busy = cloudBusy,
                actionMessage = cloudUserActionMessage,
                onClearMessage = cloudViewModel::clearUserActionMessage,
                onCreateComparison = cloudViewModel::publishGradeComparison,
                onJoinComparison = cloudViewModel::joinGradeComparison,
                onDismiss = { showGradeComparisonDialog = false }
            )
        }

        if (showActivationCodeDialog) {
            ActivationCodeClaimDialog(
                currentUser = cloudUser,
                busy = cloudBusy,
                actionMessage = cloudUserActionMessage,
                onClaimCode = { code ->
                    val ensured = viewModel.ensureAutoGeneratedAccountCredentials()
                    cloudViewModel.claimActivationCode(code, ensured.deviceInstallId) { activatedRec ->
                        if (activatedRec.assignedUsername.isNotBlank()) {
                            viewModel.updateProfile(
                                ensured.copy(accountUsername = activatedRec.assignedUsername)
                            )
                        }
                    }
                },
                onDismiss = { showActivationCodeDialog = false }
            )
        }

        if (showNameChangeDialog) {
            TeacherNameChangeDialog(
                currentName = state.profile.teacherName,
                initialChosen = state.profile.initialNameChosen,
                hasWeeklyGift = hasWeeklyNameGift,
                onSubmit = { requested ->
                    cloudViewModel.requestTeacherNameChange(
                        currentName = state.profile.teacherName,
                        requestedName = requested,
                        initialChosen = state.profile.initialNameChosen,
                        lastNameChangeMillis = state.profile.lastNameChangeMillis
                    ) { applied ->
                        viewModel.updateProfile(
                            state.profile.copy(
                                teacherName = applied,
                                timetableTeacherKey = applied,
                                initialNameChosen = true,
                                lastNameChangeMillis = System.currentTimeMillis()
                            )
                        )
                    }
                },
                onDismiss = { showNameChangeDialog = false }
            )
        }

        if (!cloudRemoteControl.maintenanceEnabled &&
            cloudRemoteControl.internalUpdateEnabled &&
            cloudRemoteControl.internalUpdateRevision > 0L &&
            cloudRemoteControl.internalUpdateRevision > state.profile.appliedInternalUpdateRevision &&
            dismissedInternalRevision != cloudRemoteControl.internalUpdateRevision &&
            !showAdminNotificationsPanel &&
            (splashDismissed || !state.profile.splashEnabled)
        ) {
            InternalUpdateProgressDialog(
                title = cloudRemoteControl.internalUpdateTitle,
                description = cloudRemoteControl.internalUpdateText,
                durationSec = cloudRemoteControl.internalUpdateDurationSec,
                includesSchoolRoster = cloudRemoteControl.internalUpdateIncludeSchoolRoster ||
                    cloudRemoteControl.internalUpdateCustomRosterText.isNotBlank(),
                onApplyInternalMerge = {
                    viewModel.applyInternalUpdateRoster(
                        revision = cloudRemoteControl.internalUpdateRevision,
                        includeSchoolRoster = cloudRemoteControl.internalUpdateIncludeSchoolRoster,
                        customRosterText = cloudRemoteControl.internalUpdateCustomRosterText
                    )
                },
                onDismiss = {
                    dismissedInternalRevision = cloudRemoteControl.internalUpdateRevision
                }
            )
        }

        if (showAdminNotificationsPanel) {
            AdminSuperPanelDialog(
                currentUser = cloudUser,
                isAdmin = cloudIsAdmin,
                adminPermissions = cloudAdminPermissions,
                adminCheckLoading = cloudAdminCheckLoading,
                hasAdminPin = cloudHasAdminPin,
                adminPinUnlocked = cloudAdminPinUnlocked,
                adminAnnouncements = cloudAdminAnnouncements,
                remoteRelease = cloudRemoteRelease,
                remoteControl = cloudRemoteControl,
                promoBanner = cloudPromoBanner,
                polls = cloudAdminPolls,
                pollTallies = cloudAdminPollTallies,
                allUsers = cloudAdminUsers,
                allFeedback = cloudAdminFeedback,
                allParentReports = cloudAdminParentReports,
                subAdmins = cloudSubAdmins,
                activationCodes = cloudActivationCodes,
                nameChangeRequests = cloudNameChangeRequests,
                gradeComparisons = cloudAllComparisons,
                analytics = cloudAnalyticsSummary,
                actionMessage = cloudAdminActionMessage,
                defaultAuthorName = state.profile.teacherName,
                onRetryAdminCheck = cloudViewModel::refreshAdminStatus,
                onRefreshDashboardData = cloudViewModel::loadAdminDashboardData,
                onSignOut = { cloudViewModel.signOut(context) },
                onSetupAdminPin = cloudViewModel::setupAdminPin,
                onUnlockWithPin = cloudViewModel::unlockWithAdminPin,
                onPublishAnnouncement = cloudViewModel::publishAnnouncement,
                onToggleAnnouncementActive = cloudViewModel::toggleAnnouncementActive,
                onDeleteAnnouncement = cloudViewModel::deleteAnnouncement,
                onPublishPoll = cloudViewModel::publishPoll,
                onTogglePollActive = cloudViewModel::togglePollActive,
                onDeletePoll = cloudViewModel::deletePoll,
                onLoadPollTallies = cloudViewModel::loadPollTallies,
                onSaveRemoteControl = cloudViewModel::saveRemoteControl,
                onPublishInternalUpdateNow = cloudViewModel::publishInternalUpdateNow,
                onPublishExperimentalLayoutNow = cloudViewModel::publishExperimentalLayoutNow,
                adminLocalExperimentalMode = state.profile.adminLocalExperimentalMode,
                onSetAdminLocalExperimentalMode = viewModel::setAdminLocalExperimentalMode,
                onSavePromoBanner = cloudViewModel::savePromoBanner,
                onPublishRelease = cloudViewModel::publishAppRelease,
                onUpdateUserExtended = cloudViewModel::updateUserExtendedControl,
                onDeleteUserAccount = cloudViewModel::adminDeleteUserAccount,
                onUnbindUserDevice = cloudViewModel::adminUnbindUserDevice,
                onSendPrivateNotification = { target, title, body ->
                    cloudViewModel.publishAnnouncement(
                        title = title,
                        body = body,
                        priority = AnnouncementPriority.IMPORTANT,
                        authorName = state.profile.teacherName.ifBlank { "الإدارة" },
                        targetType = NotificationTargetType.USER,
                        targetValue = target,
                        scheduledForMillis = 0L
                    )
                },
                onSendGroupGift = cloudViewModel::sendGroupGift,
                onSaveSubAdmin = cloudViewModel::saveSubAdmin,
                onDeleteSubAdmin = cloudViewModel::deleteSubAdmin,
                onCreateActivationCode = cloudViewModel::createActivationCode,
                onResetActivationCodeDevice = cloudViewModel::resetActivationCodeDevice,
                onDeleteActivationCode = cloudViewModel::deleteActivationCode,
                onDecideNameRequest = cloudViewModel::decideNameChangeRequest,
                onDecideComparison = cloudViewModel::decideGradeComparison,
                onDeleteComparison = cloudViewModel::deleteGradeComparison,
                onAutoPromoteTopActive = cloudViewModel::autoPromoteTopActiveTeachers,
                onPurgeExpiredChats = cloudViewModel::adminPurgeExpiredChats,
                onToggleFeedbackReviewed = cloudViewModel::toggleFeedbackReviewed,
                onDeleteParentReport = cloudViewModel::adminDeleteParentReport,
                onClearActionMessage = cloudViewModel::clearAdminActionMessage,
                onDismiss = { showAdminNotificationsPanel = false }
            )
        }
    }
}
