package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.utils.*
import java.io.File

enum class ToolRoute(
    val titleAr: String,
    val shortTitleAr: String,
    val subtitleAr: String,
    val infoAr: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    TEACHER_PROFILE(
        "الملف الشخصي للمدرس",
        "الملف الشخصي",
        "صورة غلاف، سيرة مختصرة، وتخصيص شاشة الترحيب",
        "صمّم ملفك الشخصي بوضع صورة غلاف علوية وشعار خاص وسيرة ذاتية، وتحكم بشاشة الدخول الافتتاحية.",
        Icons.Filled.AccountCircle,
        TurquoiseBright
    ),
    STUDENT_PAPERS(
        "أوراق إجابات الطلبة",
        "أوراق الطلبة",
        "تصوير عدة صفحات لكل طالب + مشاركة PDF",
        "اجمع صور أوراق إجابة كل طالب (صفحتين أو أكثر) من الكاميرا أو المعرض ببطاقات ملونة وتصفحها بسلاسة مع تصديرها PDF.",
        Icons.Filled.FolderCopy,
        InfoBlue
    ),
    EXAM_ARCHIVE(
        "أرشيف أسئلة الامتحانات",
        "أرشيف الأسئلة",
        "تصنيف ملون: يومي، شهري، نصف السنة، سنوات سابقة",
        "احفظ جميع نماذج أسئلتك من السنوات السابقة أو الشهرية واليومية في أرشيف منظم حسب الصف ونوع الامتحان لتعود إليها في أي وقت أوفلاين.",
        Icons.Filled.Inventory2,
        GoldBright
    ),
    SECTION_STARS(
        "كواكب الشعبة",
        "كواكب الشعبة",
        "خريطة مجرة متوهجة للطلبة المتميزين بصورهم وألقابهم",
        "كرّم الطلبة المتفوقين والقدوة في كل شعبة بعرضهم ككواكب متوهجة في مدار كوني جميل مع صورهم وألقابهم الخاصة.",
        Icons.Filled.AutoAwesome,
        TurquoiseBright
    ),
    QURAN_ATHKAR_PRAYER(
        "القرآن والأذكار ومواقيت الصلاة",
        "القرآن والأذكار",
        "مصحف بدون نت مع بحث وتلوين + أذكار + مواقيت GPS",
        "تلاوة قرآنية برسم عثماني وبحث بدون تشكيل مع تلوين الآيات وحفظ آخر موضع، بالإضافة إلى أذكار الصباح والمساء والتسبيح، ومواقيت الصلاة.",
        Icons.Filled.AutoStories,
        SuccessGreen
    ),
    DAILY_LEDGER(
        "قائمة اليومي والترحيل من 100",
        "سجل اليومي",
        "أعمدة يومية متعددة للشهر الأول والثاني + ترحيل للمعدل",
        "أضف عموداً واحداً أو عدة أعمدة يومية لكل شهر بالفصل الأول والفصل الثاني ولأي سجل درجات، ثم رحّل المجموع من 100 إلى السجل الرئيسي.",
        Icons.Filled.ViewWeek,
        TurquoiseBright
    ),
    QUESTION_BANK(
        "بنك الأسئلة السري ومولّد الاختبارات",
        "بنك الأسئلة",
        "محمي برقم سري وبصمة، فصول وتوزيع درجات وتوليد PDF",
        "قسم منفرد ومشفّر محلياً ببصمة الإصبع أو كلمة سر. أضف حتى 10 فصول ووزع درجاتها، وسجّل أسئلتك وولّد ورقة اختبار PDF تلقائياً.",
        Icons.Filled.Quiz,
        GoldBright
    ),
    EXAM_WRITER(
        "كتابة أسئلة الامتحان وتصديرها",
        "كتابة الأسئلة",
        "محرر اختبارات احترافي + إطارات جاهزة ومخصصة + حفظ وطباعة PDF",
        "صمّم ورقة أسئلة امتحان كاملة بترويسة المدرسة، أفرع وتنسيقات جاهزة، إدخال آيات قرآنية بالرسم العثماني مع رقم الصفحة، وتحكم كامل بالخطوط والألوان وتصدير PDF.",
        Icons.Filled.EditNote,
        GoldBright
    ),
    VAULT_EXTRACTOR(
        "مستخرج النصوص والحافظة السرية",
        "مستخرج النصوص",
        "تفريغ نصوص من الصور والملفات + حافظة محمية برمز PIN",
        "صوّر أي ورقة أو اختر صورة أو ملف Word لاستخراج النص أوفلاين، مع إمكانية نسخه أو إرساله لسجل الدرجات أو حفظه بالحافظة السرية.",
        Icons.Filled.DocumentScanner,
        TurquoiseBright
    ),
    CERTIFICATES_AND_QR(
        "الشهادات التقديرية و QR النتائج",
        "الشهادات و QR",
        "قوالب شهادات فاخرة + تصدير صورة/PDF + رمز QR",
        "صمّم شهادات شكر وتقدير بقوالب احترافية متعددة وأوسمة جاهزة وتاريخ وتوقيع المدرس، وأنشئ رمز QR لنتائج أي طالب.",
        Icons.Filled.WorkspacePremium,
        TurquoiseBright
    ),
    LIBRARY_BOOKS(
        "مكتبة الكتب والملفات",
        "مكتبة الكتب",
        "تصفح الكتب مع حفظ آخر صفحة + تأشير وكتابة",
        "أضف كتب المنهج والملازم (PDF أو صور أو نصوص) واستعرضها بأي وقت أوفلاين مع حفظ رقم آخر صفحة وتأشير الصفحات.",
        Icons.Filled.MenuBook,
        InfoBlue
    ),
    TASKS_AND_CHAPTERS(
        "المهام وفصول المنهج",
        "المهام والمنهج",
        "مهام المدرس اليومية + متابعة إنجاز فصول الكتب",
        "سجّل مهامك اليومية والشهرية وأنجزها، وأضف عناوين فصول الكتاب لكل مرحلة لتؤشر على الفصول المنجزة.",
        Icons.AutoMirrored.Filled.Assignment,
        WarningAmber
    ),
    QUICK_NOTES_TIMER(
        "ملاحظات المدرس الخاصة والمؤقت",
        "الملاحظات والمؤقت",
        "دفتر الملاحظات الخاصة + مؤقت الحصة + أوائل المدرسة",
        "قسم لملاحظات المدرس الخاصة والصفية السريعة المحفوظة، مع مؤقت بسيط للأنشطة والامتحانات اليومية.",
        Icons.Filled.StickyNote2,
        TurquoiseBright
    ),
    STUDY_GUIDES(
        "مكتبة الملازم المشتركة (PDF)",
        "الملازم PDF",
        "رفع ومشاركة ملازم المدرسين بصيغة PDF والبحث الفوري فيها",
        "تصفح وابحث عن ملازم المدرسين المنشورة بصيغة PDF أو ارفع ملزمتك ليصل إليها الجميع.",
        Icons.Filled.PictureAsPdf,
        InfoBlue
    ),
    SETTINGS(
        "الإعدادات والخطوط والمظهر",
        "الإعدادات",
        "الوضع الليلي/النهاري، الخطوط، الإشعارات، الألوان، القفل",
        "خصّص اسمك وتخصصك، وضع الليل والنهار، الخطوط، الإشعارات، ألوان الجداول، قفل التطبيق، وترتيب الأقسام.",
        Icons.Filled.Settings,
        GoldBright
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    state: AppState,
    activeDayPeriod: Pair<SchoolDay?, Int?>,
    nowMillis: Long,
    onOpenTab: (Int) -> Unit,
    onOpenTool: (ToolRoute) -> Unit,
    onUpdateTeacherIdentity: (String, String, String, SubjectSpecialty, GradeLevel) -> Unit,
    onSelectStartupTeacher: (String, String, SubjectSpecialty, GradeLevel) -> Unit,
    onMarkStartupHandled: () -> Unit,
    onSetLedgerActivation: (Boolean, Boolean) -> Unit,
    onInspectStudent: (Student) -> Unit,
    onToggleDarkMode: () -> Unit,
    getToolMediaFile: (String?) -> File? = { null },
    onImportToolImage: (String, Uri) -> Boolean = { _, _ -> false },
    onClearToolImage: (String) -> Unit = {},
    onUpdateToolTileColor: (String, Long) -> Unit = { _, _ -> },
    onUpdateToolWhiteBackdrop: (String, Boolean) -> Unit = { _, _ -> },
    onUpdateToolOrder: (List<String>) -> Unit = {},
    onUpdateProfile: (TeacherProfile) -> Unit = {},
    unreadNotificationsCount: Int = 0,
    onOpenNotificationsInbox: () -> Unit = {},
    remoteControl: RemoteControlConfig = RemoteControlConfig(),
    promoBanner: PromoBannerConfig = PromoBannerConfig(),
    unlockedPointsTools: Set<String> = emptySet(),
    currentPoints: Int = 0,
    trialStatus: TrialAccessStatus = TrialAccessStatus.OPEN,
    remainingTrialDays: Int = 0,
    onOpenPromoBanner: () -> Unit = {},
    onRequestUnlockPointsTool: (ToolRoute, Int) -> Unit = { _, _ -> },
    onOpenAdminPanel: () -> Unit = {},
    isAdminUnlocked: Boolean = false,
    currentRankTitle: String = "",
    onOpenTeacherChat: () -> Unit = {},
    onOpenGradeComparison: () -> Unit = {},
    onOpenActivationCode: () -> Unit = {},
    onRequestNameChange: () -> Unit = {},
    onHideTool: (String) -> Unit = {},
    onUnhideTool: (String) -> Unit = {},
    onToggleFavoriteTool: (String) -> Unit = {},
    onArchiveTool: (String) -> Unit = {},
    onUnarchiveTool: (String) -> Unit = {},
    onSetTileScale: (Float) -> Unit = {},
    onImportSectionBg: (String, Uri) -> Boolean = { _, _ -> false },
    onClearSectionBg: (String) -> Unit = {},
    isCurrentUserAdmin: Boolean = false,
    onSetPreferDefaultDesign: (Boolean) -> Unit = {},
    onVoiceActionMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ext = LocalExtendedColors.current
    val isDark = state.profile.isDarkMode
    var showNameDialog by remember { mutableStateOf(false) }
    var showStartupPicker by remember(state.profile.teacherSelectedOnStartup) {
        mutableStateOf(!state.profile.teacherSelectedOnStartup)
    }
    var showCalendarOverview by remember { mutableStateOf(false) }
    var overviewMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var globalQuery by remember { mutableStateOf("") }
    var advFilter by remember { mutableStateOf(AdvancedStudentSearchFilter()) }
    var showAdvFilters by remember { mutableStateOf(false) }
    var customMinScoreText by remember { mutableStateOf("") }
    var customMaxScoreText by remember { mutableStateOf("") }
    var reorderMode by remember { mutableStateOf(false) }
    var customizingTool by remember { mutableStateOf<ToolRoute?>(null) }
    var showArchiveSection by remember { mutableStateOf(false) }

    val activeLayoutMode = remember(
        remoteControl.experimentalLayoutEnabled,
        remoteControl.experimentalLayoutMode,
        state.profile.preferDefaultDesign,
        state.profile.adminLocalExperimentalMode,
        isCurrentUserAdmin
    ) {
        resolveEffectiveLayoutMode(
            remoteControl = remoteControl,
            profile = state.profile,
            isAdmin = isCurrentUserAdmin
        )
    }

    val availableLedgers = remember(state.profile.customLedgers, state.profile.subjectName, state.profile.subjectSpecialty) {
        state.profile.effectiveLedgers()
    }
    var searchLedger1Id by remember(availableLedgers) {
        mutableStateOf(state.profile.currentLedgerDef().id)
    }
    var searchLedger2Id by remember(availableLedgers) {
        mutableStateOf<String?>(null)
    }

    val orderedTools = remember(state.profile.toolOrder) {
        val all = ToolRoute.entries
        if (state.profile.toolOrder.isEmpty()) all
        else {
            val byName = all.associateBy { it.name }
            val prioritized = state.profile.toolOrder.mapNotNull { byName[it] }
            prioritized + all.filterNot { it in prioritized }
        }
    }

    val toolImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = customizingTool
        if (uri != null && target != null) {
            onImportToolImage(target.name, uri)
        }
    }

    val sectionBgPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = customizingTool
        if (uri != null && target != null) {
            onImportSectionBg(target.name, uri)
        }
    }

    var bannerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { bannerVisible = true }

    val pulse = rememberInfiniteTransition(label = "live_pulse")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val ambientDrift by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient"
    )

    val primaryAccent = MaterialTheme.colorScheme.primary
    val secondaryAccent = MaterialTheme.colorScheme.secondary
    val studentNameDisplayColor = if (state.profile.studentNamesColorHex != 0L) {
        Color(state.profile.studentNamesColorHex)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val activeTeacherKey = state.profile.timetableTeacherKey.ifBlank { state.profile.teacherName }
    val (todayDay, currentPeriod) = activeDayPeriod

    val myWeeklyLessons = remember(state.timetable, activeTeacherKey) {
        if (activeTeacherKey.isBlank()) state.timetable
        else {
            val matching = state.timetable.filter { it.teacherName.contains(activeTeacherKey, ignoreCase = true) }
            matching.ifEmpty { state.timetable }
        }
    }
    val hasLessonToday = remember(myWeeklyLessons, todayDay) {
        todayDay != null && myWeeklyLessons.any { it.day == todayDay }
    }
    val hasLessonNow = remember(myWeeklyLessons, todayDay, currentPeriod) {
        todayDay != null && currentPeriod != null &&
            myWeeklyLessons.any { it.day == todayDay && it.periodNumber == currentPeriod }
    }

    val overallStats = remember(state.students, state.gradebookSettings, state.profile.activeLedgerId) {
        GradeCalculator.computeSectionStats(
            students = state.students,
            settings = state.gradebookSettings,
            ledgerId = state.profile.currentLedgerDef().id
        )
    }

    val bannerInfo = remember(state.timetable, state.dayPeriodTimes, activeTeacherKey, nowMillis) {
        com.example.utils.LessonAlarmScheduler.computeLiveBannerInfo(
            timetable = state.timetable,
            dayPeriodTimes = state.dayPeriodTimes,
            teacherKey = activeTeacherKey
        )
    }

    val searchMatches = remember(
        globalQuery,
        advFilter,
        state.students,
        state.gradebookSettings,
        searchLedger1Id,
        state.profile.preferredGrade
    ) {
        val q = OcrAndImportHelper.normalizeArabicForSearch(globalQuery)
        if (q.length < 2 && !advFilter.hasActiveStructuredFilters) emptyList()
        else {
            val effectiveFilter = advFilter.copy(
                query = globalQuery,
                sectionScope = if (advFilter.sectionScope == SectionScopeFilter.CURRENT_SECTION && q.isNotBlank() && !advFilter.hasActiveStructuredFilters) {
                    SectionScopeFilter.ALL_SCHOOL
                } else {
                    advFilter.sectionScope
                }
            )
            StudentSearchAndVoiceHelper.filterStudentsAdvanced(
                students = state.students,
                filter = effectiveFilter,
                settings = state.gradebookSettings,
                ledgerId = searchLedger1Id,
                currentGrade = state.profile.preferredGrade,
                currentSection = SectionLetter.A
            ).take(15)
        }
    }

    val neonEnabled = state.profile.neonGlowEnabled
    val isDesign9 = ext.paletteId == ThemePalette.DESIGN_9_ROYAL_PURPLE_PEARL.id
    val cyanPillGradient = listOf(Color(0xFF06B6D4), Color(0xFF2DD4BF))
    val goldPillGradient = listOf(Color(0xFFFFB300), Color(0xFFFFD54F))

    val effectiveUnlockedTools = remember(unlockedPointsTools, state.profile.unlockedPointsTools) {
        unlockedPointsTools + state.profile.unlockedPointsTools
    }

    val enabledBaseTools = remember(
        orderedTools,
        remoteControl.disabledTools,
        state.profile.hiddenTools,
        state.profile.archivedTools
    ) {
        orderedTools.filterNot {
            it.name in remoteControl.disabledTools ||
                it.name in state.profile.hiddenTools ||
                it.name in state.profile.archivedTools
        }
    }

    val archivedToolsList = remember(orderedTools, remoteControl.disabledTools, state.profile.archivedTools) {
        orderedTools.filter {
            it.name in state.profile.archivedTools && it.name !in remoteControl.disabledTools
        }
    }

    val favoriteToolsList = remember(enabledBaseTools, state.profile.favoriteTools) {
        state.profile.favoriteTools.mapNotNull { favName ->
            enabledBaseTools.find { it.name == favName }
        }
    }

    val filteredTools = remember(globalQuery, enabledBaseTools) {
        val q = globalQuery.trim()
        if (q.isEmpty()) enabledBaseTools
        else enabledBaseTools.filter {
            it.shortTitleAr.contains(q, ignoreCase = true) || it.titleAr.contains(q, ignoreCase = true)
        }.ifEmpty { enabledBaseTools }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Wavy Specialty Header Card (Teacher Name by Specialty + Gear & Day/Night + Top Next Period Slot)
        item {
            AnimatedVisibility(
                visible = bannerVisible,
                enter = fadeIn(tween(360)) + scaleIn(
                    initialScale = 0.96f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                )
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    border = BorderStroke(
                        width = if (hasLessonNow || neonEnabled) 1.6.dp else 1.dp,
                        color = if (hasLessonNow) primaryAccent.copy(alpha = glowAlpha)
                        else ext.headerWaveAccent.copy(alpha = if (neonEnabled) 0.55f else 0.25f)
                    ),
                    shadowElevation = if (neonEnabled) 10.dp else 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .neonCardGlow(ext.headerWaveAccent, neonEnabled, 24.dp, alphaFactor = 0.30f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(ext.headerWaveStart, ext.headerWaveEnd)
                                )
                            )
                            .drawBehind {
                                val w = size.width
                                val h = size.height
                                // Wavy curved accent swoosh matching screenshots 7, 9, 5, 6
                                val wavePath = Path().apply {
                                    moveTo(0f, h * 0.28f)
                                    cubicTo(
                                        w * 0.25f, 0f,
                                        w * 0.72f, h * 0.48f,
                                        w, h * 0.14f
                                    )
                                    lineTo(w, 0f)
                                    lineTo(0f, 0f)
                                    close()
                                }
                                drawPath(
                                    wavePath,
                                    color = ext.headerWaveAccent.copy(alpha = 0.14f)
                                )
                                drawCircle(
                                    color = secondaryAccent.copy(alpha = 0.12f * ambientDrift),
                                    radius = size.maxDimension * 0.22f,
                                    center = Offset(w * 0.12f, h * 0.85f)
                                )
                                listOf(0.24f, 0.38f).forEach { factor ->
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.06f),
                                        radius = size.maxDimension * factor,
                                        center = Offset(w * 0.90f, h * 0.16f),
                                        style = Stroke(width = 1.2.dp.toPx())
                                    )
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Top Bar Row: Specialty Emblem (Start/Right) - Teacher Name & Specialty (Center) - Gear & Theme Toggle (End/Left)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Specialty Circular Emblem Badge
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF07152B).copy(alpha = 0.65f),
                                    border = BorderStroke(1.6.dp, ext.headerWaveAccent.copy(alpha = 0.85f)),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable { showNameDialog = true }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = state.profile.subjectSpecialty.visualSpec().primaryIcon,
                                            contentDescription = state.profile.subjectSpecialty.titleAr,
                                            tint = ext.headerWaveAccent,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                // Centered Teacher Name By Specialty
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { showNameDialog = true }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val specialtyLabel = state.profile.subjectName.ifBlank { state.profile.subjectSpecialty.titleAr }
                                    val isBlackMintTheme = ext.paletteId == ThemePalette.BLACK_NEON_MINT.id && isDark
                                    Text(
                                        text = if (state.profile.teacherName.isNotBlank()) {
                                            "أ. ${state.profile.teacherName}"
                                        } else {
                                            "أدوات مدرس $specialtyLabel"
                                        },
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isBlackMintTheme) Color(0xFF02C39A) else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(16.dp)
                                                .height(1.5.dp)
                                                .background(ext.headerWaveAccent.copy(alpha = 0.7f))
                                        )
                                        Text(
                                            text = "مدرس $specialtyLabel • ${state.profile.preferredGrade.shortAr}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ext.headerWaveAccent,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(16.dp)
                                                .height(1.5.dp)
                                                .background(ext.headerWaveAccent.copy(alpha = 0.7f))
                                        )
                                    }
                                    if (currentRankTitle.isNotBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = GoldBright.copy(alpha = 0.22f),
                                            border = BorderStroke(1.dp, GoldBright.copy(alpha = 0.85f))
                                        ) {
                                            Text(
                                                text = "★ $currentRankTitle",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = GoldBright,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // Settings Gear + Day/Night + Edit Icon Cluster
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        IconButton(
                                            onClick = onToggleDarkMode,
                                            modifier = Modifier.fillMaxSize().testTag("dashboard_theme_toggle")
                                        ) {
                                            Icon(
                                                imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                                contentDescription = "تبديل الوضع الليلي والنهاري",
                                                tint = ext.headerWaveAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        IconButton(
                                            onClick = { showNameDialog = true },
                                            modifier = Modifier.fillMaxSize().testTag("edit_teacher_name_btn")
                                        ) {
                                            Icon(
                                                Icons.Filled.Edit,
                                                contentDescription = "تعديل الهوية والاختصاص",
                                                tint = Color.White,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White.copy(alpha = 0.14f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (unreadNotificationsCount > 0) ext.headerWaveAccent else Color.White.copy(alpha = 0.30f)
                                        ),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            IconButton(
                                                onClick = onOpenNotificationsInbox,
                                                modifier = Modifier.fillMaxSize().testTag("dashboard_notifications_btn")
                                            ) {
                                                Icon(
                                                    imageVector = if (unreadNotificationsCount > 0) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                                                    contentDescription = "صندوق الإشعارات",
                                                    tint = if (unreadNotificationsCount > 0) ext.headerWaveAccent else Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            if (unreadNotificationsCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(5.dp)
                                                        .size(9.dp)
                                                        .clip(CircleShape)
                                                        .background(DangerRed)
                                                )
                                            }
                                        }
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        IconButton(
                                            onClick = { onOpenTool(ToolRoute.SETTINGS) },
                                            modifier = Modifier.fillMaxSize().testTag("header_settings_btn")
                                        ) {
                                            Icon(
                                                Icons.Filled.Settings,
                                                contentDescription = "الإعدادات",
                                                tint = Color.White,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Top Dedicated Next Period & Live Lesson Slot (Current = Yellow, Next = Light Green, Past = Light Red)
                            val lessonStatusCurrentYellow = Color(0xFFFACC15)
                            val lessonStatusNextGreen = Color(0xFF86EFAC)
                            val lessonStatusPastRed = Color(0xFFFCA5A5)
                            val nextBannerTap = rememberNeonTapController()
                            val slotAccent = when {
                                hasLessonNow -> lessonStatusCurrentYellow
                                bannerInfo.hasNextPeriod -> lessonStatusNextGreen
                                myWeeklyLessons.isNotEmpty() -> lessonStatusPastRed
                                else -> ext.headerWaveAccent
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF061329).copy(alpha = 0.78f),
                                border = BorderStroke(
                                    width = if (hasLessonNow || neonEnabled) 1.5.dp else 1.dp,
                                    color = slotAccent.copy(alpha = if (hasLessonNow) glowAlpha else 0.72f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .neonTapFeedback(nextBannerTap, slotAccent, 16.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { nextBannerTap.trigger { onOpenTab(1) } }
                                    .testTag("live_lesson_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = slotAccent.copy(alpha = 0.20f),
                                        border = BorderStroke(1.2.dp, slotAccent),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (hasLessonNow) Icons.Filled.PlayCircleFilled else Icons.Filled.Schedule,
                                                contentDescription = "الحصة القادمة",
                                                tint = slotAccent,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = slotAccent
                                            ) {
                                                Text(
                                                    text = when {
                                                        hasLessonNow -> "الحالي • الحصة $currentPeriod"
                                                        bannerInfo.hasNextPeriod -> "الدرس القادم"
                                                        myWeeklyLessons.isNotEmpty() -> "درس فائت / منتهٍ"
                                                        else -> "الحصة القادمة"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF061124),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                            if (bannerInfo.nextPeriodTimeRange.isNotBlank()) {
                                                Text(
                                                    text = bannerInfo.nextPeriodTimeRange,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = slotAccent,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (hasLessonNow && bannerInfo.hasNextPeriod) {
                                                "القادم: ${bannerInfo.nextPeriodTitle}"
                                            } else {
                                                bannerInfo.nextPeriodTitle
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (hasLessonNow && bannerInfo.hasNextPeriod) lessonStatusNextGreen else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (bannerInfo.nextPeriodSubtitle.isNotBlank()) {
                                            Text(
                                                text = bannerInfo.nextPeriodSubtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFCBD5E1),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = slotAccent
                                    ) {
                                        Text(
                                            text = if (myWeeklyLessons.isEmpty()) "جدول" else "انطلق",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF061124),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Seasonal Theme Badge & Trial Countdown Strip (when active)
        val activeSeasonal = remoteControl.seasonalPreset()
        if (activeSeasonal != SeasonalThemePreset.NONE || trialStatus == TrialAccessStatus.TRIAL_ACTIVE) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activeSeasonal != SeasonalThemePreset.NONE) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = "${activeSeasonal.emoji} ${activeSeasonal.titleAr}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    if (trialStatus == TrialAccessStatus.TRIAL_ACTIVE) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = WarningAmber.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, WarningAmber)
                        ) {
                            Text(
                                text = "فترة تجريبية • متبقي $remainingTrialDays يوم",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = WarningAmber,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Promotional In-App Banner Strip (Admin Managed - TOP on Home)
        val showHomePromo = promoBanner.enabled &&
            promoBanner.title.isNotBlank() &&
            (promoBanner.placement == "HOME" || promoBanner.placement == "ALL_SCREENS" || (promoBanner.placement == "SPECIFIC_SECTION" && promoBanner.targetRoute == "HOME"))
        if (showHomePromo && promoBanner.position != "BOTTOM") {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                    border = BorderStroke(1.3.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onOpenPromoBanner() }
                        .testTag("dashboard_promo_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Campaign,
                            contentDescription = "إعلان مميز",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(26.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = promoBanner.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (promoBanner.description.isNotBlank()) {
                                Text(
                                    text = promoBanner.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                text = "عرض",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Permanent Home Admin Section (unlocked via 10 silent taps on version)
        if (isAdminUnlocked) {
            item {
                val adminTapCtrl = rememberNeonTapController()
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.6.dp, MaterialTheme.colorScheme.primary),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .neonCardGlow(MaterialTheme.colorScheme.primary, neonEnabled, 18.dp, alphaFactor = 0.35f)
                        .neonTapFeedback(adminTapCtrl, MaterialTheme.colorScheme.primary, 18.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { adminTapCtrl.trigger { onOpenAdminPanel() } }
                        .testTag("dashboard_admin_section_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Filled.AdminPanelSettings,
                                contentDescription = "قسم الإدارة",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "قسم الإدارة والتحكم الشامل",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "محفوظ دائماً بالرئيسية • محمي برمز المطور • إدارة المستخدمين والتحديثات",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // 2. Two Stat Hero Cards side-by-side (إجمالي الطلبة & نسبة النجاح) — Matching Design 7 & 9
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val isBlackMint = ext.paletteId == ThemePalette.BLACK_NEON_MINT.id && isDark
                val customWheelActive = state.profile.customColorsEnabled ||
                    state.profile.customBgColorHex != 0L ||
                    state.profile.customButtonColorHex != 0L ||
                    state.profile.customCardAccentColorHex != 0L
                val statCardBg = when {
                    customWheelActive || isBlackMint -> Brush.linearGradient(listOf(ext.cardElevated, ext.cardSecondary))
                    isDark -> Brush.linearGradient(listOf(Color(0xFF0B254B), Color(0xFF071A36)))
                    isDesign9 -> Brush.linearGradient(listOf(Color(0xFFF5F2FF), Color(0xFFFFFFFF)))
                    else -> Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF3F8FF)))
                }

                val studentsAccent = if (customWheelActive) MaterialTheme.colorScheme.primary else if (isBlackMint) primaryAccent else if (isDark) Color(0xFFFFC83D) else if (isDesign9) Color(0xFF6D28D9) else Color(0xFF0A3D86)
                val passAccent = if (customWheelActive) MaterialTheme.colorScheme.secondary else if (isBlackMint) primaryAccent else if (isDark) Color(0xFF22D3EE) else Color(0xFF0284C7)

                val studentsTapCtrl = rememberNeonTapController()
                val passTapCtrl = rememberNeonTapController()
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    border = BorderStroke(
                        width = if (neonEnabled) 1.4.dp else 1.dp,
                        color = studentsAccent.copy(alpha = if (neonEnabled) 0.55f else 0.25f)
                    ),
                    shadowElevation = if (neonEnabled) 7.dp else 3.dp,
                    modifier = Modifier
                        .weight(1f)
                        .neonCardGlow(studentsAccent, neonEnabled, 20.dp, alphaFactor = 0.34f)
                        .neonTapFeedback(studentsTapCtrl, studentsAccent, 20.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { studentsTapCtrl.trigger { onOpenTab(2) } }
                ) {
                    Row(
                        modifier = Modifier
                            .background(statCardBg)
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Groups,
                            contentDescription = "إجمالي الطلبة",
                            tint = studentsAccent,
                            modifier = Modifier.size(34.dp)
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${state.students.size}",
                                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF0B1324)
                            )
                            Text(
                                text = "إجمالي الطلبة",
                                style = MaterialTheme.typography.labelMedium,
                                color = ext.textMuted,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    border = BorderStroke(
                        width = if (neonEnabled) 1.4.dp else 1.dp,
                        color = passAccent.copy(alpha = if (neonEnabled) 0.55f else 0.25f)
                    ),
                    shadowElevation = if (neonEnabled) 7.dp else 3.dp,
                    modifier = Modifier
                        .weight(1f)
                        .neonCardGlow(passAccent, neonEnabled, 20.dp, alphaFactor = 0.34f)
                        .neonTapFeedback(passTapCtrl, passAccent, 20.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { passTapCtrl.trigger { onOpenTab(3) } }
                ) {
                    Row(
                        modifier = Modifier
                            .background(statCardBg)
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = if (overallStats.gradedStudents > 0) "${overallStats.passRatePercent.toInt()}%" else "جاهز",
                                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF0B1324)
                            )
                            Text(
                                text = "نسبة النجاح",
                                style = MaterialTheme.typography.labelMedium,
                                color = ext.textMuted,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.TrendingUp,
                            contentDescription = "نسبة النجاح",
                            tint = passAccent,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
        }

        // 3. Two Glossy Pill Action Buttons (قائمة اليومي & بنك الأسئلة السري) — Matching Design 7 & 9
        val dailyDisabled = ToolRoute.DAILY_LEDGER.name in remoteControl.disabledTools
        val qbDisabled = ToolRoute.QUESTION_BANK.name in remoteControl.disabledTools
        if (!dailyDisabled || !qbDisabled) {
            item {
                val dailyPillTap = rememberNeonTapController()
                val qbPillTap = rememberNeonTapController()
                val dailyCost = (remoteControl.pointsRequiredTools[ToolRoute.DAILY_LEDGER.name] ?: 0)
                    .takeIf { it > 0 && ToolRoute.DAILY_LEDGER.name !in effectiveUnlockedTools }
                val qbCost = (remoteControl.pointsRequiredTools[ToolRoute.QUESTION_BANK.name] ?: 0)
                    .takeIf { it > 0 && ToolRoute.QUESTION_BANK.name !in effectiveUnlockedTools }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!dailyDisabled) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Transparent,
                            shadowElevation = if (neonEnabled) 8.dp else 4.dp,
                            border = BorderStroke(1.2.dp, Color(0xFF67E8F9).copy(alpha = if (neonEnabled) 0.8f else 0.35f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .neonCardGlow(Color(0xFF22D3EE), neonEnabled, 24.dp, alphaFactor = 0.35f)
                                .neonTapFeedback(dailyPillTap, Color(0xFF22D3EE), 24.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    dailyPillTap.trigger {
                                        if (dailyCost != null) {
                                            onRequestUnlockPointsTool(ToolRoute.DAILY_LEDGER, dailyCost)
                                        } else {
                                            onOpenTool(ToolRoute.DAILY_LEDGER)
                                        }
                                    }
                                }
                                .testTag("quick_open_daily_ledger")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.horizontalGradient(cyanPillGradient))
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.ViewWeek, contentDescription = null, tint = Color(0xFF05202E), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (dailyCost != null) "قائمة اليومي ($dailyCost★)" else "قائمة اليومي",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF05202E),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (!qbDisabled) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Transparent,
                            shadowElevation = if (neonEnabled) 8.dp else 4.dp,
                            border = BorderStroke(1.2.dp, Color(0xFFFDE68A).copy(alpha = if (neonEnabled) 0.85f else 0.35f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .neonCardGlow(Color(0xFFFFC83D), neonEnabled, 24.dp, alphaFactor = 0.35f)
                                .neonTapFeedback(qbPillTap, Color(0xFFFFC83D), 24.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    qbPillTap.trigger {
                                        if (qbCost != null) {
                                            onRequestUnlockPointsTool(ToolRoute.QUESTION_BANK, qbCost)
                                        } else {
                                            onOpenTool(ToolRoute.QUESTION_BANK)
                                        }
                                    }
                                }
                                .testTag("quick_open_question_bank")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.horizontalGradient(goldPillGradient))
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF1F1400), modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (qbCost != null) "بنك الأسئلة ($qbCost★)" else "بنك الأسئلة السري",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF1F1400),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3B. Specialty Teacher Chat (50/day), Cross-Subject Grade Comparison & Unlocked Stealth Admin Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chatTapCtrl = rememberNeonTapController()
                    val cmpTapCtrl = rememberNeonTapController()
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.2.dp, InfoBlue.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .neonTapFeedback(chatTapCtrl, InfoBlue, 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { chatTapCtrl.trigger { onOpenTeacherChat() } }
                            .testTag("dashboard_teacher_chat_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.Forum, contentDescription = null, tint = InfoBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "دردشة التخصص (50/يوم)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.2.dp, SuccessGreen.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .neonTapFeedback(cmpTapCtrl, SuccessGreen, 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { cmpTapCtrl.trigger { onOpenGradeComparison() } }
                            .testTag("dashboard_grade_comparison_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.CompareArrows, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "مقارنة الدرجات بكود",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Experimental System Restructuring Active Banner + Revert to Default
        val adminOrRemoteExpAvailable = remoteControl.experimentalLayoutEnabled ||
            (isCurrentUserAdmin && state.profile.adminLocalExperimentalMode != "CLASSIC")
        if (activeLayoutMode != ExperimentalLayoutMode.CLASSIC || (state.profile.preferDefaultDesign && adminOrRemoteExpAvailable)) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (activeLayoutMode != ExperimentalLayoutMode.CLASSIC) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                    } else {
                        ext.cardElevated
                    },
                    border = BorderStroke(
                        1.dp,
                        if (activeLayoutMode != ExperimentalLayoutMode.CLASSIC) MaterialTheme.colorScheme.secondary else ext.borderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("experimental_layout_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoFixHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (activeLayoutMode != ExperimentalLayoutMode.CLASSIC) {
                                "النظام التجريبي: ${activeLayoutMode.titleAr}"
                            } else {
                                "أنت تستخدم التصميم الافتراضي حالياً"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (activeLayoutMode != ExperimentalLayoutMode.CLASSIC) {
                            OutlinedButton(
                                onClick = { onSetPreferDefaultDesign(true) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("revert_default_design_btn")
                            ) {
                                Text("العودة للافتراضي", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onSetPreferDefaultDesign(false) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("enable_experimental_design_btn")
                            ) {
                                Text("تفعيل التجريبي", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 4. Search Bar + Voice Search + Advanced Filters + Reorder/Customize Pill Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = globalQuery,
                        onValueChange = { globalQuery = it },
                        placeholder = {
                            Text(
                                if (advFilter.nameMatchMode == StudentNameMatchMode.FIRST_NAME_ONLY) {
                                    "بحث باسم الطالب الأول فقط..."
                                } else {
                                    "ابحث عن طالب أو أداة..."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = primaryAccent, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (globalQuery.isNotEmpty()) {
                                IconButton(onClick = { globalQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "مسح", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ext.cardElevated,
                            unfocusedContainerColor = ext.cardElevated,
                            focusedBorderColor = primaryAccent,
                            unfocusedBorderColor = ext.borderSubtle
                        ),
                        modifier = Modifier.weight(1f).testTag("global_student_search")
                    )

                    HoldToSpeakMicButton(
                        onRecognizedText = { spoken ->
                            when (val res = VoiceCommandRouter.resolveVoiceCommand(spoken)) {
                                is VoiceCommandResolution.Navigate -> {
                                    when (res.target) {
                                        VoiceNavTarget.HOME -> Unit
                                        VoiceNavTarget.TIMETABLE -> onOpenTab(1)
                                        VoiceNavTarget.GRADEBOOK -> onOpenTab(2)
                                        VoiceNavTarget.ANALYTICS -> onOpenTab(3)
                                        VoiceNavTarget.SETTINGS -> onOpenTool(ToolRoute.SETTINGS)
                                        VoiceNavTarget.STUDY_GUIDES -> onOpenTool(ToolRoute.STUDY_GUIDES)
                                        VoiceNavTarget.DAILY_LEDGER -> onOpenTool(ToolRoute.DAILY_LEDGER)
                                        VoiceNavTarget.QUESTION_BANK -> onOpenTool(ToolRoute.QUESTION_BANK)
                                        VoiceNavTarget.CERTIFICATES_AND_QR -> onOpenTool(ToolRoute.CERTIFICATES_AND_QR)
                                        VoiceNavTarget.LIBRARY_BOOKS -> onOpenTool(ToolRoute.LIBRARY_BOOKS)
                                        VoiceNavTarget.TASKS_AND_CHAPTERS -> onOpenTool(ToolRoute.TASKS_AND_CHAPTERS)
                                        VoiceNavTarget.QUICK_NOTES_TIMER -> onOpenTool(ToolRoute.QUICK_NOTES_TIMER)
                                        VoiceNavTarget.QURAN_ATHKAR_PRAYER -> onOpenTool(ToolRoute.QURAN_ATHKAR_PRAYER)
                                        VoiceNavTarget.STUDENT_PAPERS -> onOpenTool(ToolRoute.STUDENT_PAPERS)
                                        VoiceNavTarget.EXAM_ARCHIVE -> onOpenTool(ToolRoute.EXAM_ARCHIVE)
                                        VoiceNavTarget.SECTION_STARS -> onOpenTool(ToolRoute.SECTION_STARS)
                                        VoiceNavTarget.VAULT_EXTRACTOR -> onOpenTool(ToolRoute.VAULT_EXTRACTOR)
                                        VoiceNavTarget.TEACHER_PROFILE -> onOpenTool(ToolRoute.TEACHER_PROFILE)
                                        VoiceNavTarget.EXAM_WRITER -> onOpenTool(ToolRoute.EXAM_WRITER)
                                    }
                                    onVoiceActionMessage("تم الفتح الصوتي: ${res.matchedPhrase}")
                                }
                                is VoiceCommandResolution.SearchStudent -> {
                                    globalQuery = res.query
                                    onVoiceActionMessage("بحث صوتي عن: ${res.query}")
                                }
                                VoiceCommandResolution.Empty -> Unit
                            }
                        },
                        onStatusMessage = onVoiceActionMessage,
                        testTagName = "dashboard_voice_search_btn"
                    )

                    Surface(
                        shape = CircleShape,
                        color = if (showAdvFilters || advFilter.hasActiveStructuredFilters) primaryAccent else ext.cardElevated,
                        border = BorderStroke(1.2.dp, if (showAdvFilters || advFilter.hasActiveStructuredFilters) primaryAccent else ext.borderSubtle),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { showAdvFilters = !showAdvFilters }
                            .testTag("dashboard_adv_filter_toggle")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = "فلاتر البحث المتقدمة",
                                tint = if (showAdvFilters || advFilter.hasActiveStructuredFilters) MaterialTheme.colorScheme.onPrimary else primaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (reorderMode) primaryAccent else ext.cardElevated,
                        border = BorderStroke(
                            1.2.dp,
                            if (reorderMode) primaryAccent else ext.borderSubtle
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { reorderMode = !reorderMode }
                            .testTag("toggle_tile_customize_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (reorderMode) Icons.Filled.Check else Icons.Filled.Tune,
                                contentDescription = null,
                                tint = if (reorderMode) MaterialTheme.colorScheme.onPrimary else primaryAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (reorderMode) "إنهاء" else "ترتيب",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (reorderMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showAdvFilters) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth().testTag("dashboard_adv_filters_panel")
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("فلاتر البحث المتقدمة:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = primaryAccent)
                                if (advFilter.hasActiveStructuredFilters) {
                                    TextButton(onClick = {
                                        advFilter = AdvancedStudentSearchFilter()
                                        customMinScoreText = ""
                                        customMaxScoreText = ""
                                    }) {
                                        Text("إعادة ضبط")
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StudentNameMatchMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = advFilter.nameMatchMode == mode,
                                        onClick = { advFilter = advFilter.copy(nameMatchMode = mode) },
                                        label = { Text(mode.titleAr, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.testTag("dash_filter_name_mode_${mode.name}")
                                    )
                                }
                                SectionScopeFilter.entries.forEach { scope ->
                                    FilterChip(
                                        selected = advFilter.sectionScope == scope,
                                        onClick = { advFilter = advFilter.copy(sectionScope = scope, specificSection = null) },
                                        label = { Text(scope.titleAr, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.testTag("dash_filter_scope_${scope.name}")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ScoreRangeFilter.entries.forEach { sr ->
                                    FilterChip(
                                        selected = advFilter.scoreRange == sr,
                                        onClick = { advFilter = advFilter.copy(scoreRange = sr) },
                                        label = { Text(sr.titleAr, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.testTag("dash_filter_score_${sr.name}")
                                    )
                                }
                            }

                            if (advFilter.scoreRange == ScoreRangeFilter.CUSTOM) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customMinScoreText,
                                        onValueChange = { txt ->
                                            customMinScoreText = txt.filter { it.isDigit() || it == '.' }
                                            advFilter = advFilter.copy(minScore = customMinScoreText.toDoubleOrNull())
                                        },
                                        label = { Text("من درجة") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = customMaxScoreText,
                                        onValueChange = { txt ->
                                            customMaxScoreText = txt.filter { it.isDigit() || it == '.' }
                                            advFilter = advFilter.copy(maxScore = customMaxScoreText.toDoubleOrNull())
                                        },
                                        label = { Text("إلى درجة") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AcademicStatusFilter.entries.forEach { st ->
                                    FilterChip(
                                        selected = advFilter.academicStatus == st,
                                        onClick = { advFilter = advFilter.copy(academicStatus = st) },
                                        label = { Text(st.titleAr, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.testTag("dash_filter_status_${st.name}")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BehaviorSearchFilter.entries.forEach { bh ->
                                    FilterChip(
                                        selected = advFilter.behaviorFilter == bh,
                                        onClick = { advFilter = advFilter.copy(behaviorFilter = bh) },
                                        label = { Text(bh.titleAr, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.testTag("dash_filter_behavior_${bh.name}")
                                    )
                                }
                                SectionLetter.entries.forEach { sec ->
                                    FilterChip(
                                        selected = advFilter.specificSection == sec,
                                        onClick = {
                                            advFilter = advFilter.copy(
                                                specificSection = if (advFilter.specificSection == sec) null else sec
                                            )
                                        },
                                        label = { Text("شعبة ${sec.id}", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (availableLedgers.size > 1 && globalQuery.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("نطاق البحث:", style = MaterialTheme.typography.labelSmall, color = ext.textMuted)
                        availableLedgers.forEach { led ->
                            val isSelected = led.id == searchLedger1Id || led.id == searchLedger2Id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    when {
                                        led.id == searchLedger1Id && searchLedger2Id != null -> {
                                            searchLedger1Id = searchLedger2Id!!
                                            searchLedger2Id = null
                                        }
                                        led.id == searchLedger2Id -> {
                                            searchLedger2Id = null
                                        }
                                        led.id != searchLedger1Id -> {
                                            searchLedger2Id = led.id
                                        }
                                    }
                                },
                                label = { Text(led.title, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                if (searchMatches.isNotEmpty()) {
                    val r = state.gradebookSettings.enableSmartRounding
                    val ledger1 = availableLedgers.find { it.id == searchLedger1Id } ?: availableLedgers.first()
                    val ledger2 = searchLedger2Id?.let { id2 -> availableLedgers.find { it.id == id2 } }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.3.dp, primaryAccent.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            searchMatches.forEach { st ->
                                val res1 = GradeCalculator.computeForLedger(st, state.gradebookSettings, ledger1.id)
                                val res2 = ledger2?.let { GradeCalculator.computeForLedger(st, state.gradebookSettings, it.id) }
                                val exempt1 = GradeCalculator.isExemptEligible(st, res1, state.gradebookSettings)
                                val ministerial1 = st.gradeLevel == GradeLevel.THIRD && (res1.annualEffort ?: 0.0) >= 50.0
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            globalQuery = ""
                                            onInspectStudent(st)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${st.orderNumber}. ${st.fullName}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                                            fontWeight = FontWeight.SemiBold,
                                            color = studentNameDisplayColor,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (exempt1 || ministerial1) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = SuccessGreen.copy(alpha = 0.2f),
                                                modifier = Modifier.padding(end = 6.dp)
                                            ) {
                                                Text(
                                                    if (ministerial1) "مؤهل وزاري" else "معفو",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SuccessGreen,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = primaryAccent.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = "${st.gradeLevel.shortAr} (${st.section.id})",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = primaryAccent,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    val statusLabel = when {
                                        st.isTransferred -> "منقول" to ext.textMuted
                                        res1.isPassing == true -> "ناجح ✓" to SuccessGreen
                                        res1.isPassing == false -> "راسب / مكمل" to DangerRed
                                        else -> "قيد الرصد" to ext.textMuted
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = statusLabel.second.copy(alpha = 0.14f),
                                            border = BorderStroke(0.8.dp, statusLabel.second.copy(alpha = 0.55f))
                                        ) {
                                            Text(
                                                text = statusLabel.first,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = statusLabel.second,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                        val pills = listOf(
                                            "ف1" to GradeCalculator.formatScore(res1.term1Average, r),
                                            "نصف السنة" to GradeCalculator.formatScore(res1.midYearEffective, r),
                                            "السعي" to GradeCalculator.formatScore(res1.annualEffort, r),
                                            "النهائي" to GradeCalculator.formatScore(res1.finalGrade, r)
                                        )
                                        pills.forEach { (lbl, valTxt) ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                                border = BorderStroke(0.8.dp, Color(ledger1.headerColorHex).copy(alpha = 0.55f))
                                            ) {
                                                Text(
                                                    text = "$lbl: $valTxt",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = primaryAccent,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (ledger2 != null && res2 != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(ledger2.headerColorHex).copy(alpha = 0.14f),
                                            border = BorderStroke(1.dp, Color(ledger2.headerColorHex).copy(alpha = 0.7f))
                                        ) {
                                            Text(
                                                text = "${ledger2.title}: ف1 ${GradeCalculator.formatScore(res2.term1Average, r)} | سعي ${GradeCalculator.formatScore(res2.annualEffort, r)} | نهائي ${GradeCalculator.formatScore(res2.finalGrade, r)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        // Quick tile reset/white pills, tile size slider & hidden tool restore shown while reorderMode is active
        if (reorderMode) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = ext.cardElevated,
                            border = BorderStroke(1.dp, ext.borderSubtle),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    val allWhite = orderedTools.associate { it.name to 0xFFFFFFFFL }
                                    onUpdateProfile(state.profile.copy(toolCustomColors = allWhite))
                                }
                        ) {
                            Text(
                                text = "جعل كل المربعات بيضاء",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = ext.cardElevated,
                            border = BorderStroke(1.dp, ext.borderSubtle),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    onUpdateProfile(
                                        state.profile.copy(
                                            toolOrder = emptyList(),
                                            toolCustomColors = emptyMap(),
                                            toolWhiteBackdrop = emptyMap(),
                                            hiddenTools = emptyList(),
                                            toolTileScale = 1f
                                        )
                                    )
                                }
                        ) {
                            Text(
                                text = "استعادة الترتيب والألوان الافتراضية",
                                style = MaterialTheme.typography.labelSmall,
                                color = primaryAccent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.dp, ext.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("حجم مربعات الأقسام:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text("${(state.profile.toolTileScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = primaryAccent, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = state.profile.toolTileScale.coerceIn(0.85f, 1.20f),
                                onValueChange = { onSetTileScale(it) },
                                valueRange = 0.85f..1.20f
                            )
                        }
                    }

                    if (state.profile.hiddenTools.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("المخفية (اضغط للاستعادة):", style = MaterialTheme.typography.labelSmall, color = ext.textMuted)
                            state.profile.hiddenTools.forEach { hiddenName ->
                                val tr = ToolRoute.entries.firstOrNull { it.name == hiddenName }
                                if (tr != null) {
                                    AssistChip(
                                        onClick = { onUnhideTool(tr.name) },
                                        leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        label = { Text(tr.shortTitleAr) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Weekly Schedule Quick Summary Strip (Current = Yellow, Next = Light Green, Past = Light Red)
        if (myWeeklyLessons.isNotEmpty()) {
            item {
                val activeDays = state.profile.workingDays.sortedBy { it.ordinal }.ifEmpty { SchoolDay.entries }
                val calNow = remember(nowMillis) { java.util.Calendar.getInstance().apply { timeInMillis = nowMillis } }
                val nowMinutes = calNow.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calNow.get(java.util.Calendar.MINUTE)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(activeDays) { day ->
                        val dayCells = myWeeklyLessons.filter { it.day == day }.sortedBy { it.periodNumber }
                        val isDayActive = day == todayDay
                        val dayPeriods = state.periodTimesForDay(day)
                        val dayTapCtrl = rememberNeonTapController()
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ext.cardElevated,
                            border = BorderStroke(
                                if (isDayActive) 1.6.dp else 1.dp,
                                if (isDayActive) Color(0xFFFACC15) else ext.borderSubtle
                            ),
                            modifier = Modifier
                                .neonTapFeedback(dayTapCtrl, primaryAccent, 14.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { dayTapCtrl.trigger { onOpenTab(1) } }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${day.titleAr} (${dayCells.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = primaryAccent,
                                    fontWeight = FontWeight.Bold
                                )
                                if (dayCells.isEmpty()) {
                                    Text("بدون حصص", style = MaterialTheme.typography.labelSmall, color = ext.textMuted)
                                } else {
                                    dayCells.take(4).forEach { cell ->
                                        val pt = dayPeriods.find { it.periodNumber == cell.periodNumber }
                                        val startMin = pt?.let { it.startHour * 60 + it.startMinute }
                                        val endMin = pt?.let { it.endHour * 60 + it.endMinute }
                                        val isPast = when {
                                            todayDay != null && day.ordinal < todayDay.ordinal -> true
                                            isDayActive && endMin != null && nowMinutes > endMin -> true
                                            else -> false
                                        }
                                        val isCurrent = isDayActive && (currentPeriod == cell.periodNumber || (startMin != null && endMin != null && nowMinutes in startMin..endMin))
                                        val isUpcoming = !isCurrent && !isPast
                                        val statusColor = when {
                                            isCurrent -> Color(0xFFFACC15)   // أصفر: الدرس الحالي
                                            isUpcoming -> Color(0xFF86EFAC)  // أخضر فاتح: الدرس القادم
                                            else -> Color(0xFFFCA5A5)        // أحمر فاتح: الدرس الفائت
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = statusColor.copy(alpha = if (isDark) 0.16f else 0.25f),
                                            border = BorderStroke(0.8.dp, statusColor.copy(alpha = 0.7f))
                                        ) {
                                            Text(
                                                text = "ح${cell.periodNumber}: ${cell.gradeLevel.shortAr} (${cell.section.id})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isDark) statusColor else Color(0xFF0B1324),
                                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Tools Grid + Pinned Favorites Strip + SPECIALIST_PRO + Collapsible Archive Section
        val openToolWithLockCheck: (ToolRoute) -> Unit = { tool ->
            val lockedCost = (remoteControl.pointsRequiredTools[tool.name] ?: 0)
                .takeIf { it > 0 && tool.name !in effectiveUnlockedTools }
            if (reorderMode) {
                customizingTool = tool
            } else if (lockedCost != null) {
                onRequestUnlockPointsTool(tool, lockedCost)
            } else {
                onOpenTool(tool)
            }
        }

        if (favoriteToolsList.isNotEmpty() && !reorderMode && globalQuery.isBlank()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = GoldBright, modifier = Modifier.size(18.dp))
                            Text(
                                text = "المفضلة المثبتة (${favoriteToolsList.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryAccent
                            )
                        }
                        Text(
                            text = "ضغط مطوّل للتخصيص",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.textMuted
                        )
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("dashboard_favorites_strip")
                    ) {
                        items(favoriteToolsList, key = { "fav_${it.name}" }) { favTool ->
                            val lockedCost = (remoteControl.pointsRequiredTools[favTool.name] ?: 0)
                                .takeIf { it > 0 && favTool.name !in effectiveUnlockedTools }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ext.cardElevated,
                                border = BorderStroke(1.2.dp, GoldBright.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .width(126.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .combinedClickable(
                                        onClick = { openToolWithLockCheck(favTool) },
                                        onLongClick = { customizingTool = favTool }
                                    )
                                    .testTag("fav_tool_${favTool.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        favTool.icon,
                                        contentDescription = favTool.shortTitleAr,
                                        tint = if (lockedCost != null) GoldBright else primaryAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = favTool.shortTitleAr,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activeLayoutMode == ExperimentalLayoutMode.MODERN_SPLIT && !reorderMode) {
            val splitRows = filteredTools.chunked(2)
            items(splitRows.size) { rowIndex ->
                val pair = splitRows[rowIndex]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEachIndexed { cIdx, tool ->
                        val num = rowIndex * 2 + cIdx + 1
                        val ribbon = tileRibbonColor(num, ext.paletteId)
                        val lockedCost = (remoteControl.pointsRequiredTools[tool.name] ?: 0)
                            .takeIf { it > 0 && tool.name !in effectiveUnlockedTools }
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = ext.cardElevated,
                            border = BorderStroke(1.3.dp, ribbon.copy(alpha = 0.65f)),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 86.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .combinedClickable(
                                    onClick = { openToolWithLockCheck(tool) },
                                    onLongClick = { customizingTool = tool }
                                )
                                .testTag("tool_card_${tool.name}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ribbon.copy(alpha = 0.16f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(tool.icon, contentDescription = tool.shortTitleAr, tint = ribbon, modifier = Modifier.size(24.dp))
                                    }
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(tool.shortTitleAr, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = if (lockedCost != null) "يفتح بـ $lockedCost ★" else tool.subtitleAr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (lockedCost != null) GoldBright else ext.textMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else if (activeLayoutMode == ExperimentalLayoutMode.SPECIALIST_PRO && !reorderMode && globalQuery.isBlank()) {
            val specialistGroups = listOf(
                "التقييم والدرجات والتكريم" to listOf(
                    ToolRoute.DAILY_LEDGER,
                    ToolRoute.STUDENT_PAPERS,
                    ToolRoute.CERTIFICATES_AND_QR,
                    ToolRoute.SECTION_STARS
                ),
                "الاختبارات وبنك الأسئلة" to listOf(
                    ToolRoute.EXAM_WRITER,
                    ToolRoute.QUESTION_BANK,
                    ToolRoute.EXAM_ARCHIVE
                ),
                "المنهج والمكتبة والملاحظات" to listOf(
                    ToolRoute.STUDY_GUIDES,
                    ToolRoute.LIBRARY_BOOKS,
                    ToolRoute.TASKS_AND_CHAPTERS,
                    ToolRoute.VAULT_EXTRACTOR,
                    ToolRoute.QUICK_NOTES_TIMER
                ),
                "القرآن والملف والإعدادات" to listOf(
                    ToolRoute.QURAN_ATHKAR_PRAYER,
                    ToolRoute.TEACHER_PROFILE,
                    ToolRoute.SETTINGS
                )
            )
            val groupedSeen = specialistGroups.flatMap { it.second }.toSet()
            val leftover = filteredTools.filterNot { it in groupedSeen }
            val allGroups = if (leftover.isNotEmpty()) {
                specialistGroups + ("أدوات إضافية" to leftover)
            } else {
                specialistGroups
            }
            var runningNum = 1
            allGroups.forEach { (categoryTitle, routes) ->
                val activeInCategory = routes.filter { it in filteredTools }
                if (activeInCategory.isNotEmpty()) {
                    item(key = "spec_hdr_$categoryTitle") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(primaryAccent)
                            )
                            Text(
                                text = categoryTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryAccent
                            )
                        }
                    }
                    val catRows = activeInCategory.chunked(3)
                    items(catRows.size, key = { "spec_${categoryTitle}_$it" }) { rIdx ->
                        val rowItems = catRows[rIdx]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { tool ->
                                val num = runningNum++
                                val customHex = state.profile.toolCustomColors[tool.name] ?: 0L
                                val whiteBackdrop = state.profile.toolWhiteBackdrop[tool.name] ?: false
                                val customFile = getToolMediaFile(state.profile.toolCustomImages[tool.name])
                                val lockedCost = (remoteControl.pointsRequiredTools[tool.name] ?: 0)
                                    .takeIf { it > 0 && tool.name !in effectiveUnlockedTools }
                                SectionSquareTile(
                                    tool = tool,
                                    priorityNumber = num,
                                    totalCount = orderedTools.size,
                                    tileScale = state.profile.toolTileScale,
                                    isDarkMode = isDark,
                                    neonGlowEnabled = neonEnabled,
                                    reorderMode = false,
                                    customColorHex = customHex,
                                    whiteIconBackdrop = whiteBackdrop,
                                    customImageFile = customFile,
                                    lockedPointsCost = lockedCost,
                                    onClick = { openToolWithLockCheck(tool) },
                                    onLongClick = { customizingTool = tool },
                                    onOpenCustomizer = { customizingTool = tool },
                                    onMoveEarlier = {},
                                    onMoveLater = {},
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        } else {
            val cols = when {
                reorderMode -> 3
                activeLayoutMode == ExperimentalLayoutMode.COMPACT_CARDS -> 4
                activeLayoutMode == ExperimentalLayoutMode.FOCUS_STUDIO -> 3
                else -> 3
            }
            val displayTools = if (activeLayoutMode == ExperimentalLayoutMode.FOCUS_STUDIO && !reorderMode && globalQuery.isBlank()) {
                val priority = listOf(ToolRoute.STUDY_GUIDES, ToolRoute.DAILY_LEDGER, ToolRoute.EXAM_WRITER, ToolRoute.QUESTION_BANK)
                val top = priority.filter { it in filteredTools }
                top + filteredTools.filterNot { it in top }
            } else {
                filteredTools
            }
            val rows = displayTools.chunked(cols)
            items(rows.size) { rowIndex ->
                val rowItems = rows[rowIndex]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (cols == 4) 6.dp else 10.dp)
                ) {
                    rowItems.forEachIndexed { colIndex, tool ->
                        val globalIndex = rowIndex * cols + colIndex
                        val customHex = state.profile.toolCustomColors[tool.name] ?: 0L
                        val whiteBackdrop = state.profile.toolWhiteBackdrop[tool.name] ?: false
                        val customFile = getToolMediaFile(state.profile.toolCustomImages[tool.name])
                        val lockedCost = (remoteControl.pointsRequiredTools[tool.name] ?: 0)
                            .takeIf { it > 0 && tool.name !in effectiveUnlockedTools }

                        SectionSquareTile(
                            tool = tool,
                            priorityNumber = globalIndex + 1,
                            totalCount = orderedTools.size,
                            tileScale = if (cols == 4) (state.profile.toolTileScale * 0.9f) else state.profile.toolTileScale,
                            isDarkMode = isDark,
                            neonGlowEnabled = neonEnabled,
                            reorderMode = reorderMode,
                            customColorHex = customHex,
                            whiteIconBackdrop = whiteBackdrop,
                            customImageFile = customFile,
                            lockedPointsCost = lockedCost,
                            onClick = { openToolWithLockCheck(tool) },
                            onLongClick = { customizingTool = tool },
                            onOpenCustomizer = { customizingTool = tool },
                            onMoveEarlier = {
                                if (globalIndex > 0) {
                                    val mutable = orderedTools.toMutableList()
                                    val prev = mutable[globalIndex - 1]
                                    mutable[globalIndex - 1] = mutable[globalIndex]
                                    mutable[globalIndex] = prev
                                    onUpdateToolOrder(mutable.map { it.name })
                                }
                            },
                            onMoveLater = {
                                if (globalIndex < orderedTools.lastIndex) {
                                    val mutable = orderedTools.toMutableList()
                                    val next = mutable[globalIndex + 1]
                                    mutable[globalIndex + 1] = mutable[globalIndex]
                                    mutable[globalIndex] = next
                                    onUpdateToolOrder(mutable.map { it.name })
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(cols - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (archivedToolsList.isNotEmpty()) {
            item(key = "dashboard_archive_section") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.dp, ext.borderSubtle),
                    modifier = Modifier.fillMaxWidth().testTag("dashboard_archive_section")
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showArchiveSection = !showArchiveSection }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Archive, contentDescription = null, tint = ext.textMuted, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "قسم الأرشفة (${archivedToolsList.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = if (showArchiveSection) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (showArchiveSection) "طي" else "توسيع",
                                tint = ext.textMuted
                            )
                        }
                        AnimatedVisibility(visible = showArchiveSection) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                archivedToolsList.forEach { archived ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = ext.cardSecondary,
                                        border = BorderStroke(1.dp, ext.borderSubtle),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { openToolWithLockCheck(archived) }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(archived.icon, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(20.dp))
                                                Text(archived.shortTitleAr, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton(onClick = { openToolWithLockCheck(archived) }) { Text("فتح") }
                                                TextButton(onClick = { onUnarchiveTool(archived.name) }) { Text("استعادة") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showHomePromo && promoBanner.position == "BOTTOM") {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                    border = BorderStroke(1.3.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onOpenPromoBanner() }
                        .testTag("dashboard_promo_banner_bottom")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Campaign, contentDescription = "إعلان مميز", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(26.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(promoBanner.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (promoBanner.description.isNotBlank()) {
                                Text(promoBanner.description, style = MaterialTheme.typography.bodySmall, color = ext.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondary) {
                            Text("عرض", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        }
    }

    // Tile PNG Image, Color & Priority Customizer Dialog
    customizingTool?.let { tool ->
        val customHex = state.profile.toolCustomColors[tool.name] ?: 0L
        val whiteBackdrop = state.profile.toolWhiteBackdrop[tool.name] ?: true
        val hasCustomImg = !state.profile.toolCustomImages[tool.name].isNullOrBlank()
        val toolIndex = orderedTools.indexOf(tool)

        AlertDialog(
            onDismissRequest = { customizingTool = null },
            icon = {
                Icon(tool.icon, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(28.dp))
            },
            title = { Text("تخصيص قسم: ${tool.shortTitleAr}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(tool.infoAr, style = MaterialTheme.typography.bodySmall, color = ext.textMuted)

                    // PNG / Gallery Image
                    OutlinedButton(
                        onClick = {
                            toolImagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("pick_tool_png_btn")
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("اختيار صورة للقسم (يدعم PNG)")
                    }
                    if (hasCustomImg) {
                        TextButton(
                            onClick = { onClearToolImage(tool.name) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Filled.DeleteOutline, null, modifier = Modifier.size(16.dp), tint = DangerRed)
                            Spacer(Modifier.width(4.dp))
                            Text("حذف الصورة واستعادة الأيقونة", color = DangerRed)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("خلفية بيضاء داخل إطار الصورة/الأيقونة", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(
                            checked = whiteBackdrop,
                            onCheckedChange = { onUpdateToolWhiteBackdrop(tool.name, it) }
                        )
                    }

                    HorizontalDivider()
                    Text("لون المربع بالكامل:", style = MaterialTheme.typography.labelLarge, color = primaryAccent)
                    TileColorPresets.chunked(2).forEach { pair ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { preset ->
                                val isSel = customHex == preset.hex
                                FilterChip(
                                    selected = isSel,
                                    onClick = { onUpdateToolTileColor(tool.name, preset.hex) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (preset.hex == 0L) ext.cardElevated else Color(preset.hex)
                                                )
                                        )
                                    },
                                    label = { Text(preset.titleAr, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }

                    HorizontalDivider()
                    Text("خلفية القسم وإخفاؤه:", style = MaterialTheme.typography.labelLarge, color = primaryAccent)
                    val hasSectionBg = !state.profile.sectionBgImageFiles[tool.name].isNullOrBlank()
                    OutlinedButton(
                        onClick = {
                            sectionBgPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Wallpaper, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("تعيين خلفية مخصصة لهذا القسم (PNG/JPG)")
                    }
                    if (hasSectionBg) {
                        TextButton(
                            onClick = { onClearSectionBg(tool.name) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("حذف خلفية هذا القسم", color = DangerRed)
                        }
                    }
                    val isFavorite = tool.name in state.profile.favoriteTools
                    val isArchived = tool.name in state.profile.archivedTools
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onToggleFavoriteTool(tool.name) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isFavorite) GoldBright else primaryAccent
                            ),
                            modifier = Modifier.weight(1f).testTag("customizer_toggle_favorite_btn")
                        ) {
                            Icon(if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isFavorite) "إزالة من المفضلة" else "إضافة للمفضلة", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = {
                                if (isArchived) onUnarchiveTool(tool.name) else onArchiveTool(tool.name)
                                customizingTool = null
                            },
                            modifier = Modifier.weight(1f).testTag("customizer_toggle_archive_btn")
                        ) {
                            Icon(if (isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArchived) "استعادة" else "أرشفة", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    val isHidden = tool.name in state.profile.hiddenTools
                    OutlinedButton(
                        onClick = {
                            if (isHidden) onUnhideTool(tool.name) else onHideTool(tool.name)
                            customizingTool = null
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isHidden) SuccessGreen else WarningAmber),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(if (isHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isHidden) "إظهار القسم في الرئيسية" else "إخفاء القسم من الرئيسية")
                    }

                    HorizontalDivider()
                    Text("أولوية القسم (الترتيب الحالي: #${toolIndex + 1}):", style = MaterialTheme.typography.labelLarge, color = primaryAccent)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            enabled = toolIndex > 0,
                            onClick = {
                                val mutable = orderedTools.toMutableList()
                                mutable.removeAt(toolIndex)
                                mutable.add(0, tool)
                                onUpdateToolOrder(mutable.map { it.name })
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("الأول") }
                        OutlinedButton(
                            enabled = toolIndex > 0,
                            onClick = {
                                val mutable = orderedTools.toMutableList()
                                val prev = mutable[toolIndex - 1]
                                mutable[toolIndex - 1] = mutable[toolIndex]
                                mutable[toolIndex] = prev
                                onUpdateToolOrder(mutable.map { it.name })
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("تقديم ▲") }
                        OutlinedButton(
                            enabled = toolIndex < orderedTools.lastIndex,
                            onClick = {
                                val mutable = orderedTools.toMutableList()
                                val next = mutable[toolIndex + 1]
                                mutable[toolIndex + 1] = mutable[toolIndex]
                                mutable[toolIndex] = next
                                onUpdateToolOrder(mutable.map { it.name })
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("تأخير ▼") }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        customizingTool = null
                        onOpenTool(tool)
                    }) { Text("فتح القسم") }
                    Button(onClick = { customizingTool = null }) { Text("حفظ") }
                }
            }
        )
    }

    // One-Time Initial Teacher Onboarding Dialog (Clean Neutral School Icon — no biology symbol)
    if (showStartupPicker) {
        var customName by remember { mutableStateOf(state.profile.teacherName) }
        var selectedSpec by remember { mutableStateOf(state.profile.subjectSpecialty) }
        var selectedPrefGrade by remember { mutableStateOf(state.profile.preferredGrade) }

        val previewColors = selectedSpec.paletteSpec()

        AlertDialog(
            onDismissRequest = {
                onMarkStartupHandled()
                showStartupPicker = false
            },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = previewColors.darkPrimary.copy(alpha = 0.2f),
                    border = BorderStroke(1.5.dp, previewColors.darkPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        tint = previewColors.darkPrimary,
                        modifier = Modifier.padding(12.dp).size(30.dp)
                    )
                }
            },
            title = { Text("مرحباً بك — حدّد اسمك واختصاصك") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "أدخل اسمك واختر اختصاصك الدراسي والمرحلة المفضلة لإنشاء جدولك وسجلك الخاص:",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textMuted
                    )
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("اسم المدرس") },
                        placeholder = { Text("مثال: أ. علي أو أ. سارة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("startup_teacher_name_input")
                    )

                    Text("اختر اختصاصك الدراسي:", style = MaterialTheme.typography.labelLarge, color = primaryAccent)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubjectSpecialty.entries.chunked(2).forEach { pair ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pair.forEach { sp ->
                                    val spColors = sp.paletteSpec()
                                    val spVis = sp.visualSpec()
                                    val isSel = sp == selectedSpec
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSel) spColors.darkPrimary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            width = if (isSel) 1.8.dp else 1.dp,
                                            color = if (isSel) spColors.darkPrimary else ext.borderSubtle
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { selectedSpec = sp }
                                            .testTag("startup_spec_${sp.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(spColors.darkPrimary)
                                            )
                                            Icon(spVis.primaryIcon, null, tint = spColors.darkPrimary, modifier = Modifier.size(18.dp))
                                            Text(
                                                text = sp.titleAr,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    Text("المرحلة المفضلة عند الفتح:", style = MaterialTheme.typography.labelLarge, color = primaryAccent)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GradeLevel.entries.forEach { g ->
                            FilterChip(selected = g == selectedPrefGrade, onClick = { selectedPrefGrade = g }, label = { Text(g.shortAr) })
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = {
                        val finalDisplay = customName.trim()
                        onSelectStartupTeacher(finalDisplay, finalDisplay, selectedSpec, selectedPrefGrade)
                        showStartupPicker = false
                        onOpenTab(1)
                    }) {
                        Text("حفظ وبناء جدولي")
                    }
                    Button(
                        onClick = {
                            val finalDisplay = customName.trim()
                            onSelectStartupTeacher(finalDisplay, finalDisplay, selectedSpec, selectedPrefGrade)
                            showStartupPicker = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = previewColors.darkPrimary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("startup_save_btn")
                    ) { Text("حفظ ودخول") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onMarkStartupHandled()
                    showStartupPicker = false
                }) { Text("تخطي") }
            }
        )
    }

    if (showNameDialog) {
        var nameInput by remember { mutableStateOf(state.profile.teacherName) }
        var schoolInput by remember { mutableStateOf(state.profile.schoolName) }
        var subjectInput by remember { mutableStateOf(state.profile.subjectName) }
        var specInput by remember { mutableStateOf(state.profile.subjectSpecialty) }
        var prefGradeInput by remember { mutableStateOf(state.profile.preferredGrade) }
        val nameLocked = state.profile.initialNameChosen

        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("هوية المدرس وتخصصه") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { if (!nameLocked) nameInput = it },
                        enabled = !nameLocked,
                        label = { Text("اسم المدرس") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("teacher_name_input")
                    )
                    if (nameLocked) {
                        OutlinedButton(
                            onClick = {
                                showNameDialog = false
                                onRequestNameChange()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("طلب تغيير اسم المدرس")
                        }
                    }
                    OutlinedTextField(value = subjectInput, onValueChange = { subjectInput = it }, label = { Text("المادة الدراسية") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = schoolInput, onValueChange = { schoolInput = it }, label = { Text("اسم المدرسة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("الاختصاص:", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubjectSpecialty.entries.forEach { sp ->
                            FilterChip(
                                selected = sp == specInput,
                                onClick = { specInput = sp; subjectInput = sp.titleAr },
                                leadingIcon = { Icon(sp.visualSpec().primaryIcon, null, modifier = Modifier.size(16.dp)) },
                                label = { Text(sp.titleAr) }
                            )
                        }
                    }
                    Text("المرحلة المفضلة:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GradeLevel.entries.forEach { g ->
                            FilterChip(selected = g == prefGradeInput, onClick = { prefGradeInput = g }, label = { Text(g.shortAr) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = if (nameLocked) state.profile.teacherName else nameInput.trim()
                        onUpdateTeacherIdentity(finalName, subjectInput.trim(), schoolInput.trim(), specInput, prefGradeInput)
                        showNameDialog = false
                    },
                    modifier = Modifier.testTag("save_teacher_name_btn")
                ) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text("إلغاء") } }
        )
    }

    if (showCalendarOverview) {
        CalendarDatePickerDialog(
            initialMillis = overviewMillis,
            onDismiss = { showCalendarOverview = false },
            onDateSelected = {
                overviewMillis = it
                showCalendarOverview = false
            }
        )
    }
}

private fun tileRibbonColor(priorityNumber: Int, paletteId: String): Color {
    if (paletteId == ThemePalette.DESIGN_6_PURE_SKY_BLUE.id) {
        return if (priorityNumber == 1) Color(0xFF00B87C) else Color(0xFF0284C7)
    }
    val cycle = listOf(
        Color(0xFFF59E0B), // 1 Amber Gold
        Color(0xFF10B981), // 2 Emerald Green
        Color(0xFF0EA5E9), // 3 Sky Blue
        Color(0xFF22C55E), // 4 Bright Green
        Color(0xFF06B6D4), // 5 Neon Cyan
        Color(0xFF8B5CF6), // 6 Royal Violet
        Color(0xFFA855F7), // 7 Magenta Purple
        Color(0xFF2563EB), // 8 Cobalt Blue
        Color(0xFFF59E0B), // 9 Gold
        Color(0xFF14B8A6), // 10 Teal
        Color(0xFFF43F5E), // 11 Coral Rose
        Color(0xFF3B82F6), // 12 Blue
        Color(0xFFEAB308)  // 13 Gold
    )
    return cycle[(priorityNumber - 1).coerceAtLeast(0) % cycle.size]
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionSquareTile(
    tool: ToolRoute,
    priorityNumber: Int,
    totalCount: Int,
    tileScale: Float = 1f,
    isDarkMode: Boolean,
    neonGlowEnabled: Boolean,
    reorderMode: Boolean,
    customColorHex: Long,
    whiteIconBackdrop: Boolean,
    customImageFile: File?,
    lockedPointsCost: Int? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenCustomizer: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalExtendedColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tile_press"
    )

    val ribbonColor = remember(priorityNumber, ext.paletteId) {
        tileRibbonColor(priorityNumber, ext.paletteId)
    }
    val isBlackMint = ext.paletteId == ThemePalette.BLACK_NEON_MINT.id && isDarkMode
    val defaultBg = when {
        isBlackMint -> ext.cardElevated
        isDarkMode -> Color(0xFF092144)
        else -> Color(0xFFFFFFFF)
    }
    val targetBg = if (customColorHex != 0L) Color(customColorHex) else defaultBg
    val animCardBg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(250),
        label = "tile_bg"
    )

    val bgLuminance = 0.299f * animCardBg.red + 0.587f * animCardBg.green + 0.114f * animCardBg.blue
    val isLightCard = bgLuminance > 0.58f
    val labelColor = when {
        isLightCard -> Color(0xFF08142B)
        isBlackMint -> Color(0xFF02C39A)
        else -> Color.White
    }
    val borderCol = when {
        reorderMode -> MaterialTheme.colorScheme.primary
        isBlackMint -> Color(0xFF02C39A).copy(alpha = if (neonGlowEnabled) 0.78f else 0.45f)
        neonGlowEnabled -> ribbonColor.copy(alpha = if (isLightCard) 0.65f else 0.80f)
        isLightCard -> Color(0xFF000000).copy(alpha = 0.16f)
        else -> ext.borderSubtle
    }

    val customBmp = remember(customImageFile?.absolutePath, customImageFile?.lastModified()) {
        customImageFile?.takeIf { it.exists() }?.let { file ->
            runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        }
    }
    val tileTapCtrl = rememberNeonTapController()
    val safeTileScale = tileScale.coerceIn(0.85f, 1.20f)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = if (reorderMode) 1.8.dp else if (neonGlowEnabled) 1.4.dp else 1.dp,
            color = borderCol
        ),
        shadowElevation = if (neonGlowEnabled) 8.dp else if (isLightCard) 3.dp else 4.dp,
        modifier = modifier
            .aspectRatio((0.86f / safeTileScale).coerceIn(0.68f, 1.05f))
            .scale(scale)
            .neonCardGlow(if (isBlackMint) Color(0xFF02C39A) else ribbonColor, neonGlowEnabled, 20.dp, alphaFactor = if (isLightCard) 0.25f else 0.42f)
            .neonTapFeedback(tileTapCtrl, if (isBlackMint) Color(0xFF02C39A) else ribbonColor, 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(color = ribbonColor),
                onClick = { tileTapCtrl.trigger { onClick() } },
                onLongClick = { tileTapCtrl.trigger { onLongClick() } }
            )
            .testTag("tool_card_${tool.name}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (customColorHex == 0L && isDarkMode && !isBlackMint) {
                        Brush.verticalGradient(listOf(Color(0xFF0B274F), Color(0xFF071A36)))
                    } else {
                        Brush.verticalGradient(listOf(animCardBg, animCardBg))
                    }
                )
        ) {
            // Corner Folded Numbered Ribbon (Top-Start in RTL = Top-Right corner visually, matching Image 7 & 9)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(30.dp)
                    .clip(RoundedCornerShape(bottomEnd = 16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(ribbonColor, ribbonColor.copy(alpha = 0.80f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$priorityNumber",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Opposite Corner Palette Customizer Icon OR Gold Points Cost Badge (Top-End)
            if (lockedPointsCost != null) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 12.dp),
                    color = GoldBright,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "$lockedPointsCost ★",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1F1400),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isLightCard) Color(0xFF000000).copy(alpha = 0.06f) else Color.White.copy(alpha = 0.10f))
                        .clickable { onOpenCustomizer() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = "تخصيص ${tool.shortTitleAr}",
                        tint = if (reorderMode) MaterialTheme.colorScheme.primary else ribbonColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Center Content: Icon / PNG + Title + Bottom Circular Arrow Pill
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp, bottom = 8.dp, start = 6.dp, end = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Center Icon or PNG Image Frame
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .then(
                            if (whiteIconBackdrop || customBmp != null) {
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (whiteIconBackdrop) Color.White else Color.Transparent)
                                    .padding(if (customBmp != null) 3.dp else 6.dp)
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (customBmp != null) {
                        Image(
                            bitmap = customBmp.asImageBitmap(),
                            contentDescription = tool.shortTitleAr,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = if (whiteIconBackdrop) Color(0xFF0B2347)
                            else if (isLightCard) ribbonColor else Color(0xFF93C5FD).takeIf { priorityNumber % 3 == 0 } ?: ribbonColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Text(
                    text = tool.shortTitleAr,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )

                if (reorderMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (priorityNumber > 1) 0.22f else 0.06f),
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = priorityNumber > 1, onClick = onMoveEarlier)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.KeyboardArrowRight,
                                    contentDescription = "تقديم",
                                    tint = if (priorityNumber > 1) labelColor else labelColor.copy(alpha = 0.25f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (priorityNumber < totalCount) 0.22f else 0.06f),
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = priorityNumber < totalCount, onClick = onMoveLater)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.KeyboardArrowLeft,
                                    contentDescription = "تأخير",
                                    tint = if (priorityNumber < totalCount) labelColor else labelColor.copy(alpha = 0.25f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Bottom Circular Colored Arrow Badge (Signature detail from Images 7, 9, 5, 6)
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(ribbonColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowLeft,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
