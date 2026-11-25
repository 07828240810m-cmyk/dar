package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.model.*
import com.example.ui.components.CalendarDatePickerDialog
import com.example.ui.components.ClassSelectorBar
import com.example.ui.components.SectionHeaderWithInfo
import com.example.ui.components.formatCalendarDateArabic
import com.example.ui.components.formatDateKey
import com.example.ui.components.neonCardGlow
import com.example.ui.components.neonTapFeedback
import com.example.ui.components.rememberNeonTapController
import com.example.ui.components.visualSpec
import com.example.ui.theme.*
import com.example.utils.ComputedStudentResult
import com.example.utils.ExportManager
import com.example.utils.GradeCalculator
import com.example.utils.OcrAndImportHelper
import com.example.utils.ParsedGradeCandidate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val ledgerHeaderColors = listOf(
    0xFFFFD54FL,
    0xFF40D6B4L,
    0xFF54A0FFL,
    0xFFFF9F43L,
    0xFFA55EEAL,
    0xFFFF6B81L,
    0xFF2ED573L
)

enum class GradebookFilterTab(val titleAr: String) {
    ALL("السجل الكامل"),
    EXEMPT("قائمة الإعفاء 90+"),
    MINISTERIAL_QUALIFIED("المؤهلون لدخول الوزاري (50+)"),
    PASSING("الناجحون"),
    BAD_BEHAVIOR("ذوو السلوك السيء"),
    INACTIVE("الخاملون"),
    ABSENTEES("كشف المتغيبين"),
    TRANSFERRED("المنقولون")
}

enum class GradebookWorkMode(val titleAr: String) {
    TABLE("سجل الدرجات"),
    SEQUENTIAL_ENTRY("الإدخال التلقائي بالتسلسل"),
    IMPORT_GRADES("استيراد Excel / Word / OCR")
}

private data class GradeColumnOption(val key: String, val label: String)

private val GradebookToolbarActions: List<Pair<String, String>> = listOf(
    "add_student" to "إضافة طالب",
    "import" to "استيراد Word/Excel",
    "fullscreen" to "تكبير الشاشة",
    "colors" to "الألوان",
    "daily_ledger" to "قائمة اليومي",
    "diagnostics" to "التشخيص",
    "auto_scroll" to "تحريك بطيء",
    "modes" to "أوضاع الإدخال"
)

private fun StudentGrades.scoreForColumn(key: String): Double? = when (key) {
    "t1m1" -> term1Month1
    "t1m2" -> term1Month2
    "t1m3" -> term1Month3
    "t1d" -> term1Daily
    "mid" -> midYearTotal
    "t2m1" -> term2Month1
    "t2m2" -> term2Month2
    "t2m3" -> term2Month3
    "t2d" -> term2Daily
    "final" -> finalExam
    else -> null
}

@Composable
fun GradebookScreen(
    state: AppState,
    selectedGrade: GradeLevel,
    selectedSection: SectionLetter,
    activeLedger: LedgerSubject,
    activeLedgerId: String = state.profile.currentLedgerDef().id,
    safeEditUnlocked: Boolean = true,
    onRequestUnlockSafeEdit: () -> Unit = {},
    todayDateStr: String,
    pendingExtractedText: String,
    onConsumePendingExtractedText: () -> Unit,
    onSelectClass: (GradeLevel, SectionLetter) -> Unit,
    onSelectLedger: (LedgerSubject) -> Unit,
    onSelectLedgerId: (String) -> Unit = { id ->
        onSelectLedger(if (id == "math") LedgerSubject.MATH else LedgerSubject.GENERAL)
    },
    onAddCustomLedger: (String, Long, GradebookHeaders?) -> Unit = { _, _, _ -> },
    onRenameOrStyleLedger: (String, String, Long, GradebookHeaders?) -> Unit = { _, _, _, _ -> },
    onDeleteLedger: (String) -> Unit = {},
    onSetLedgerActivation: (Boolean, Boolean) -> Unit,
    onUpdateStudent: (Student) -> Unit,
    onAddStudent: (String, GradeLevel, SectionLetter, String) -> Unit,
    onAddStudentsBatch: (List<String>, GradeLevel, SectionLetter) -> Int,
    onMoveStudent: (String, GradeLevel, SectionLetter) -> Unit,
    onDeleteStudent: (String) -> Unit,
    onToggleTransferred: (String) -> Unit = {},
    onUpdateBadges: (String, List<String>) -> Unit = { _, _ -> },
    onAddAvailableBadge: (String) -> Unit = {},
    onUpdateProfile: (TeacherProfile) -> Unit = {},
    onBatchSaveGrades: (String, Map<String, Double?>, LedgerSubject, String) -> Unit,
    onToggleAbsenceForDate: (String, String) -> Unit,
    onUpdateSettings: (GradebookSettings) -> Unit,
    onNavigateToQrForStudent: (Student) -> Unit,
    onNavigateToCertForStudent: (Student) -> Unit,
    onOpenDailyLedger: () -> Unit = {},
    onShareParentReport: (Student) -> Unit = {},
    onExportExcelToFolder: (GradeLevel?, SectionLetter?, String) -> ExportManager.FolderExportResult = { _, _, _ -> ExportManager.FolderExportResult(false, "", null, "غير متوفر") },
    onExportWordToFolder: (GradeLevel?, SectionLetter?, String) -> ExportManager.FolderExportResult = { _, _, _ -> ExportManager.FolderExportResult(false, "", null, "غير متوفر") },
    onOpenGradeComparison: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current

    val ledgers = remember(state.profile.customLedgers, state.profile.subjectName, state.profile.subjectSpecialty) {
        state.profile.effectiveLedgers()
    }
    val currentLedgerDef = remember(ledgers, activeLedgerId) {
        ledgers.find { it.id == activeLedgerId } ?: ledgers.first()
    }
    val ledgerAccent = Color(currentLedgerDef.headerColorHex)
    val primaryAccent = MaterialTheme.colorScheme.primary
    val primaryOnColor = MaterialTheme.colorScheme.onPrimary
    val mainSubName = currentLedgerDef.title
    val tableAccent = primaryAccent
    val tableSurfaceBg = if (state.profile.gradebookTableBgColorHex != 0L) {
        Color(state.profile.gradebookTableBgColorHex)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val studentNameDisplayColor = if (state.profile.studentNamesColorHex != 0L) {
        Color(state.profile.studentNamesColorHex)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val tableHeaderBg = ledgerAccent.copy(alpha = 0.22f)
    val tableAltRowBg = ext.cardElevated.copy(alpha = 0.45f)
    val tableBorderColor = ledgerAccent.copy(alpha = 0.50f)

    var workMode by remember {
        mutableStateOf(
            if (pendingExtractedText.isNotBlank()) GradebookWorkMode.IMPORT_GRADES
            else GradebookWorkMode.TABLE
        )
    }
    var activeFilter by remember { mutableStateOf(GradebookFilterTab.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var advSearchFilter by remember { mutableStateOf(com.example.utils.AdvancedStudentSearchFilter()) }
    var showAdvSearchFilters by rememberSaveable { mutableStateOf(false) }
    var searchSecondLedgerId by remember(ledgers) { mutableStateOf<String?>(null) }
    var selectedSearchColumnKeys by rememberSaveable {
        mutableStateOf(listOf("t1avg", "mid", "effort", "final_grade"))
    }
    var isFullscreenTable by remember { mutableStateOf(false) }
    var selectedStudent by remember { mutableStateOf<Student?>(null) }
    var showAddStudentDialog by remember { mutableStateOf(false) }
    var addDialogInitialTab by remember { mutableStateOf(0) } // 0=Single, 1=Batch Type, 2=Word/Excel Import
    var showCustomizeTableDialog by remember { mutableStateOf(false) }
    var showLedgerManagerDialog by remember { mutableStateOf(false) }
    var showAutoScrollColorDialog by remember { mutableStateOf(false) }
    var showTableNamesColorDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var absenceDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showAbsenceCalendar by remember { mutableStateOf(false) }
    var lastFolderExport by remember { mutableStateOf<ExportManager.FolderExportResult?>(null) }

    LaunchedEffect(selectedGrade) {
        if (selectedGrade == GradeLevel.THIRD && activeFilter == GradebookFilterTab.EXEMPT) {
            activeFilter = GradebookFilterTab.MINISTERIAL_QUALIFIED
        } else if (selectedGrade != GradeLevel.THIRD && activeFilter == GradebookFilterTab.MINISTERIAL_QUALIFIED) {
            activeFilter = GradebookFilterTab.ALL
        }
    }
    val selectedAbsenceDateStr = remember(absenceDateMillis, todayDateStr) {
        formatDateKey(absenceDateMillis)
    }

    val sectionStudents = remember(state.students, selectedGrade, selectedSection) {
        state.students
            .filter { it.gradeLevel == selectedGrade && it.section == selectedSection }
            .sortedBy { it.orderNumber }
    }

    val absenceCounts = remember(state.absences) {
        state.absences.groupingBy { it.studentId }.eachCount()
    }
    val selectedDateAbsentIds = remember(state.absences, selectedAbsenceDateStr) {
        state.absences.filter { it.dateStr == selectedAbsenceDateStr }.map { it.studentId }.toSet()
    }

    val filteredStudents = remember(
        state.students,
        sectionStudents,
        selectedGrade,
        selectedSection,
        activeFilter,
        currentLedgerDef.id,
        searchQuery,
        advSearchFilter,
        state.gradebookSettings,
        absenceCounts,
        selectedDateAbsentIds
    ) {
        val scopedBasePool = when (advSearchFilter.sectionScope) {
            com.example.utils.SectionScopeFilter.CURRENT_SECTION -> sectionStudents
            com.example.utils.SectionScopeFilter.ALL_SECTIONS_IN_GRADE -> state.students
                .filter { it.gradeLevel == selectedGrade }
                .sortedWith(compareBy({ it.section.ordinal }, { it.orderNumber }))
            com.example.utils.SectionScopeFilter.ALL_SCHOOL -> state.students
                .sortedWith(compareBy({ it.gradeLevel.ordinal }, { it.section.ordinal }, { it.orderNumber }))
        }
        val visiblePool = if (activeFilter == GradebookFilterTab.TRANSFERRED || advSearchFilter.academicStatus == com.example.utils.AcademicStatusFilter.TRANSFERRED) {
            scopedBasePool.filter { it.isTransferred }
        } else if (state.gradebookSettings.hideTransferredStudents) {
            scopedBasePool.filterNot { it.isTransferred }
        } else {
            scopedBasePool
        }
        val byTab = when (activeFilter) {
            GradebookFilterTab.ALL -> visiblePool
            GradebookFilterTab.EXEMPT -> visiblePool.filter {
                val res = GradeCalculator.computeForLedger(it, state.gradebookSettings, currentLedgerDef.id)
                GradeCalculator.isExemptEligible(it, res, state.gradebookSettings)
            }
            GradebookFilterTab.MINISTERIAL_QUALIFIED -> visiblePool.filter {
                val res = GradeCalculator.computeForLedger(it, state.gradebookSettings, currentLedgerDef.id)
                GradeCalculator.isMinisterialQualified(it, res)
            }
            GradebookFilterTab.PASSING -> visiblePool.filter {
                GradeCalculator.computeForLedger(it, state.gradebookSettings, currentLedgerDef.id).isPassing == true
            }
            GradebookFilterTab.BAD_BEHAVIOR -> visiblePool.filter {
                it.behaviorStatus == BehaviorStatus.BAD || it.behaviorStatus == BehaviorStatus.NEEDS_ATTENTION
            }
            GradebookFilterTab.INACTIVE -> visiblePool.filter {
                it.isInactive || !it.dailyPrepDone
            }
            GradebookFilterTab.ABSENTEES -> visiblePool.filter {
                (absenceCounts[it.id] ?: 0) > 0 || it.id in selectedDateAbsentIds
            }.ifEmpty { visiblePool }
            GradebookFilterTab.TRANSFERRED -> visiblePool
        }
        if (searchQuery.isBlank() && !advSearchFilter.hasActiveStructuredFilters) {
            byTab
        } else {
            com.example.utils.StudentSearchAndVoiceHelper.filterStudentsAdvanced(
                students = byTab,
                filter = advSearchFilter.copy(query = searchQuery),
                settings = state.gradebookSettings,
                ledgerId = currentLedgerDef.id,
                currentGrade = selectedGrade,
                currentSection = selectedSection
            )
        }
    }

    val tableHScroll = rememberScrollState()
    val tableListState = rememberLazyListState()
    val settings = state.gradebookSettings
    val h = currentLedgerDef.customHeaders ?: settings.headers
    val r = settings.enableSmartRounding

    LaunchedEffect(state.profile.autoScrollEnabled, workMode, activeFilter, filteredStudents.size) {
        if (state.profile.autoScrollEnabled && workMode == GradebookWorkMode.TABLE && activeFilter != GradebookFilterTab.ABSENTEES && filteredStudents.size > 1) {
            while (true) {
                delay(45L)
                val scrolled = tableListState.scrollBy(1.6f)
                if (scrolled <= 0.1f) {
                    delay(1200L)
                    tableListState.scrollToItem(0)
                }
            }
        }
    }

    val gradeColumns = remember(h, settings.monthsPerTerm) {
        buildList {
            add(GradeColumnOption("t1m1", h.t1M1Header))
            if (settings.monthsPerTerm >= 2) add(GradeColumnOption("t1m2", h.t1M2Header))
            if (settings.monthsPerTerm >= 3) add(GradeColumnOption("t1m3", h.t1M3Header))
            add(GradeColumnOption("t1d", h.t1DailyHeader))
            add(GradeColumnOption("mid", h.midYearHeader))
            add(GradeColumnOption("t2m1", h.t2M1Header))
            if (settings.monthsPerTerm >= 2) add(GradeColumnOption("t2m2", h.t2M2Header))
            if (settings.monthsPerTerm >= 3) add(GradeColumnOption("t2m3", h.t2M3Header))
            add(GradeColumnOption("t2d", h.t2DailyHeader))
            add(GradeColumnOption("final", h.finalExamHeader))
        }
    }
    var targetColumnKey by remember { mutableStateOf("t1m1") }
    val targetColumn = gradeColumns.find { it.key == targetColumnKey } ?: gradeColumns.first()

    // Import & OCR state
    var rawImportedText by remember { mutableStateOf("") }
    LaunchedEffect(pendingExtractedText) {
        if (pendingExtractedText.isNotBlank()) {
            rawImportedText = pendingExtractedText
            workMode = GradebookWorkMode.IMPORT_GRADES
            onConsumePendingExtractedText()
        }
    }

    val simpleMode = state.profile.gradebookSimpleMode
    val visibleActionsSet = state.profile.gradebookVisibleActions.toSet()
    fun isToolbarActionVisible(key: String): Boolean = !simpleMode || key in visibleActionsSet

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = if (isFullscreenTable) 8.dp else 14.dp)
    ) {
        if (!isFullscreenTable) {
            Spacer(Modifier.height(10.dp))
            SectionHeaderWithInfo(
                title = "سجل: $mainSubName",
                infoTitle = "سجل الدرجات المتعدد واليومي والاستيراد",
                infoDescription = "أنشئ عدة سجلات وغيّر مسمياتها وألوان رؤوسها. فعّل الوضع الكامل لتمديد السجل على كامل الصفحة مع بحث مزدوج بالسجلين، وانقر على حرف (غ) لتسجيل الغياب مباشرة."
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isToolbarActionVisible("colors")) {
                        IconButton(
                            onClick = { showTableNamesColorDialog = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("gradebook_colors_btn")
                        ) {
                            Icon(Icons.Filled.Palette, contentDescription = "لون الجدول والأسماء", tint = tableAccent)
                        }
                    }
                    if (isToolbarActionVisible("fullscreen")) {
                        IconButton(
                            onClick = { isFullscreenTable = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("expand_fullscreen_gradebook_btn")
                        ) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = "تكبير السجل على كامل الصفحة", tint = tableAccent)
                        }
                    }
                    IconButton(
                        onClick = { showCustomizeTableDialog = true },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("customize_gradebook_btn")
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = "تصميم الجدول والوضع المبسط", tint = tableAccent)
                    }
                    if (isToolbarActionVisible("import")) {
                        IconButton(
                            onClick = {
                                addDialogInitialTab = 2
                                showAddStudentDialog = true
                            },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("import_students_word_excel_btn")
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = "استيراد أسماء من Word/Excel", tint = tableAccent)
                        }
                    }
                    if (isToolbarActionVisible("add_student")) {
                        IconButton(
                            onClick = {
                                addDialogInitialTab = 0
                                showAddStudentDialog = true
                            },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("add_student_btn")
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = "إضافة أو كتابة أسماء طلبة", tint = tableAccent)
                        }
                    }
                }
            }

            // Multi-Ledger Switcher Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ledgers.forEach { led ->
                    val isSel = led.id == currentLedgerDef.id
                    val c = Color(led.headerColorHex)
                    FilterChip(
                        selected = isSel,
                        onClick = { onSelectLedgerId(led.id) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(c)
                            )
                        },
                        label = { Text(led.title, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("ledger_chip_${led.id}")
                    )
                }
                AssistChip(
                    onClick = { showLedgerManagerDialog = true },
                    leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null, tint = tableAccent, modifier = Modifier.size(16.dp)) },
                    label = { Text("إدارة/إضافة سجل") },
                    modifier = Modifier.testTag("manage_ledgers_btn")
                )
            }

            Spacer(Modifier.height(4.dp))

            ClassSelectorBar(
                selectedGrade = selectedGrade,
                selectedSection = selectedSection,
                onSelect = onSelectClass
            )

            Spacer(Modifier.height(6.dp))

            // Work Mode Switcher + Diagnostics + AutoScroll + Daily Ledger shortcut
            val showActionStrip = !simpleMode ||
                isToolbarActionVisible("daily_ledger") ||
                isToolbarActionVisible("diagnostics") ||
                isToolbarActionVisible("auto_scroll") ||
                isToolbarActionVisible("modes") ||
                state.profile.safeGradeEditLockEnabled

            if (showActionStrip) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isToolbarActionVisible("daily_ledger")) {
                        AssistChip(
                            onClick = onOpenDailyLedger,
                            label = { Text("قائمة اليومي (/100)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Filled.ViewWeek, null, tint = tableAccent, modifier = Modifier.size(16.dp)) },
                            border = BorderStroke(1.dp, tableAccent),
                            modifier = Modifier.testTag("gradebook_open_daily_ledger_chip")
                        )
                    }
                    if (isToolbarActionVisible("diagnostics")) {
                        val diagTapCtrl = rememberNeonTapController()
                        AssistChip(
                            onClick = {
                                diagTapCtrl.trigger()
                                showDiagnosticsDialog = true
                            },
                            label = { Text("التشخيص", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Filled.Psychology, null, tint = tableAccent, modifier = Modifier.size(16.dp)) },
                            border = BorderStroke(1.dp, tableAccent),
                            modifier = Modifier
                                .neonTapFeedback(diagTapCtrl, tableAccent, 10.dp)
                                .testTag("gradebook_diagnostics_chip")
                        )
                    }
                    if (isToolbarActionVisible("auto_scroll")) {
                        FilterChip(
                            selected = state.profile.autoScrollEnabled,
                            onClick = {
                                onUpdateProfile(state.profile.copy(autoScrollEnabled = !state.profile.autoScrollEnabled))
                            },
                            leadingIcon = { Icon(Icons.Filled.SwipeVertical, null, modifier = Modifier.size(16.dp)) },
                            label = { Text("تحريك بطيء") },
                            modifier = Modifier.testTag("toggle_auto_scroll_btn")
                        )
                        if (state.profile.autoScrollEnabled) {
                            AssistChip(
                                onClick = { showAutoScrollColorDialog = true },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(state.profile.autoScrollHighlightColorHex))
                                    )
                                },
                                label = { Text("لون التمييز") }
                            )
                        }
                    }
                    if (state.profile.safeGradeEditLockEnabled) {
                        AssistChip(
                            onClick = onRequestUnlockSafeEdit,
                            leadingIcon = {
                                Icon(
                                    if (safeEditUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                    null,
                                    tint = if (safeEditUnlocked) SuccessGreen else WarningAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(if (safeEditUnlocked) "الوضع الآمن مفتوح" else "فك قفل الدرجات") }
                        )
                    }
                    if (isToolbarActionVisible("modes")) {
                        GradebookWorkMode.entries.forEach { mode ->
                            val selected = mode == workMode
                            FilterChip(
                                selected = selected,
                                onClick = { workMode = mode },
                                label = {
                                    Text(
                                        mode.titleAr,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = tableAccent,
                                    selectedLabelColor = primaryOnColor,
                                    containerColor = ext.cardElevated,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.testTag("gradebook_mode_${mode.name}")
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
        } else {
            // Compact Fullscreen Top Strip with Grade & Section Switchers
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$mainSubName • ${selectedGrade.shortAr} (${selectedSection.id})",
                        style = MaterialTheme.typography.titleSmall,
                        color = ledgerAccent,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = state.profile.autoScrollEnabled,
                            onClick = { onUpdateProfile(state.profile.copy(autoScrollEnabled = !state.profile.autoScrollEnabled)) },
                            label = { Text("تحريك", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilledTonalButton(
                            onClick = { isFullscreenTable = false },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("collapse_fullscreen_gradebook_btn")
                        ) {
                            Icon(Icons.Filled.FullscreenExit, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("تصغير", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GradeLevel.entries.forEach { g ->
                        val gSel = g == selectedGrade
                        FilterChip(
                            selected = gSel,
                            onClick = { onSelectClass(g, selectedSection) },
                            label = { Text(g.shortAr, style = MaterialTheme.typography.labelSmall, fontWeight = if (gSel) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tableAccent,
                                selectedLabelColor = primaryOnColor
                            ),
                            modifier = Modifier.height(30.dp).testTag("fs_grade_${g.name}")
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(ext.borderSubtle)
                    )
                    SectionLetter.entries.forEach { sec ->
                        val sSel = sec == selectedSection
                        FilterChip(
                            selected = sSel,
                            onClick = { onSelectClass(selectedGrade, sec) },
                            label = { Text("شعبة ${sec.id}", style = MaterialTheme.typography.labelSmall, fontWeight = if (sSel) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ledgerAccent,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.height(30.dp).testTag("fs_section_${sec.id}")
                        )
                    }
                }
            }
        }

        when (workMode) {
            GradebookWorkMode.SEQUENTIAL_ENTRY -> {
                SequentialAutoGradeEntryPanel(
                    sectionStudents = sectionStudents.filterNot { settings.hideTransferredStudents && it.isTransferred },
                    columns = gradeColumns,
                    targetColumn = targetColumn,
                    activeLedgerId = currentLedgerDef.id,
                    activeLedgerTitle = currentLedgerDef.title,
                    accentColor = tableAccent,
                    onSelectColumn = { targetColumnKey = it.key },
                    onSaveSingleScore = { studentId, score ->
                        if (state.profile.safeGradeEditLockEnabled && !safeEditUnlocked) {
                            onRequestUnlockSafeEdit()
                        } else {
                            onBatchSaveGrades(targetColumn.key, mapOf(studentId to score), activeLedger, currentLedgerDef.id)
                        }
                    },
                    onDoneReturn = { workMode = GradebookWorkMode.TABLE }
                )
            }

            GradebookWorkMode.IMPORT_GRADES -> {
                GradeImportPanel(
                    sectionStudents = sectionStudents.filterNot { settings.hideTransferredStudents && it.isTransferred },
                    columns = gradeColumns,
                    targetColumn = targetColumn,
                    activeLedgerTitle = currentLedgerDef.title,
                    accentColor = tableAccent,
                    rawImportedText = rawImportedText,
                    onRawTextChange = { rawImportedText = it },
                    onSelectColumn = { targetColumnKey = it.key },
                    onCommitGrades = { map ->
                        if (state.profile.safeGradeEditLockEnabled && !safeEditUnlocked) {
                            onRequestUnlockSafeEdit()
                        } else {
                            onBatchSaveGrades(targetColumn.key, map, activeLedger, currentLedgerDef.id)
                        }
                    }
                )
            }

            GradebookWorkMode.TABLE -> {
                if (!isFullscreenTable) {
                    DualLedgerSearchSection(
                        searchQuery = searchQuery,
                        onQueryChange = { searchQuery = it },
                        advFilter = advSearchFilter,
                        showAdvFilters = showAdvSearchFilters,
                        onToggleAdvFilters = { showAdvSearchFilters = !showAdvSearchFilters },
                        onUpdateAdvFilter = { advSearchFilter = it },
                        ledgers = ledgers,
                        primaryLedgerDef = currentLedgerDef,
                        secondLedgerId = searchSecondLedgerId,
                        onSelectSecondLedger = { searchSecondLedgerId = it },
                        selectedColumnKeys = selectedSearchColumnKeys,
                        onUpdateColumnKeys = { selectedSearchColumnKeys = it },
                        matchingStudents = filteredStudents,
                        settings = settings,
                        accentColor = tableAccent,
                        primaryAccent = primaryAccent,
                        borderColor = tableBorderColor,
                        studentNameColor = studentNameDisplayColor,
                        onInspectStudent = { selectedStudent = it }
                    )

                    Spacer(Modifier.height(8.dp))

                    // Export Actions Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ExportChip("PDF (${currentLedgerDef.title})", Icons.Filled.PictureAsPdf, tableAccent) {
                            val file = ExportManager.generateSectionPdf(
                                context, selectedGrade, selectedSection, filteredStudents,
                                state.profile, settings, activeLedger,
                                ledgerId = currentLedgerDef.id, ledgerTitle = currentLedgerDef.title
                            )
                            ExportManager.shareFile(context, file, "application/pdf", "مشاركة سجل PDF")
                        }
                        ExportChip("Word", Icons.Filled.Description, tableAccent) {
                            val file = ExportManager.generateSectionWordDoc(
                                context, selectedGrade, selectedSection, filteredStudents,
                                state.profile, settings, activeLedger,
                                ledgerId = currentLedgerDef.id, ledgerTitle = currentLedgerDef.title
                            )
                            ExportManager.shareFile(context, file, "application/msword", "مشاركة سجل Word")
                        }
                        ExportChip("صورة", Icons.Filled.Image, tableAccent) {
                            val file = ExportManager.generateSectionImage(
                                context, selectedGrade, selectedSection, filteredStudents,
                                state.profile, settings, activeLedger,
                                ledgerId = currentLedgerDef.id, ledgerTitle = currentLedgerDef.title
                            )
                            ExportManager.shareFile(context, file, "image/png", "مشاركة صورة السجل")
                        }
                        ExportChip("مشاركة الكل نصاً", Icons.Filled.Share, tableAccent) {
                            val txt = ExportManager.buildSectionShareText(
                                selectedGrade, selectedSection, filteredStudents,
                                state.profile, settings, activeLedger,
                                ledgerId = currentLedgerDef.id, ledgerTitle = currentLedgerDef.title
                            )
                            ExportManager.shareText(
                                context,
                                "درجات ${currentLedgerDef.title} - ${selectedGrade.titleAr} ${selectedSection.titleAr}",
                                txt
                            )
                        }
                        ExportChip("حفظ Excel (رفيق المدرس)", Icons.Filled.TableChart, SuccessGreen) {
                            val res = onExportExcelToFolder(selectedGrade, selectedSection, currentLedgerDef.id)
                            lastFolderExport = res
                            android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_LONG).show()
                        }
                        ExportChip("حفظ Word (رفيق المدرس)", Icons.Filled.Article, InfoBlue) {
                            val res = onExportWordToFolder(selectedGrade, selectedSection, currentLedgerDef.id)
                            lastFolderExport = res
                            android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_LONG).show()
                        }
                        ExportChip("مقارنة بمادة أخرى", Icons.Filled.CompareArrows, WarningAmber) {
                            onOpenGradeComparison()
                        }
                    }

                    lastFolderExport?.let { exp ->
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = (if (exp.success) SuccessGreen else DangerRed).copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, if (exp.success) SuccessGreen else DangerRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = exp.message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    exp.file?.let { f ->
                                        TextButton(onClick = {
                                            val mime = if (f.name.endsWith(".xls")) "application/vnd.ms-excel" else "application/msword"
                                            ExportManager.shareFile(context, f, mime, "مشاركة التقرير")
                                        }) { Text("مشاركة") }
                                    }
                                    IconButton(onClick = { lastFolderExport = null }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Filled.Close, contentDescription = "إغلاق", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Filter List Tabs (hide EXEMPT on 3rd Intermediate and show MINISTERIAL_QUALIFIED)
                    val visibleFilterTabs = remember(selectedGrade) {
                        GradebookFilterTab.entries.filter { tab ->
                            if (selectedGrade == GradeLevel.THIRD) {
                                tab != GradebookFilterTab.EXEMPT
                            } else {
                                tab != GradebookFilterTab.MINISTERIAL_QUALIFIED
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        visibleFilterTabs.forEach { tab ->
                            val selected = tab == activeFilter
                            FilterChip(
                                selected = selected,
                                onClick = { activeFilter = tab },
                                label = { Text(tab.titleAr) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = ext.cardElevated,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.testTag("filter_tab_${tab.name}")
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Prominent Fullscreen Expand Bar directly ABOVE the gradebook table
                    val expandTapCtrl = rememberNeonTapController()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "القائمة: ${selectedGrade.shortAr} (${selectedSection.id}) • ${filteredStudents.size} طالب",
                            style = MaterialTheme.typography.labelLarge,
                            color = ext.textMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                        FilledTonalButton(
                            onClick = {
                                expandTapCtrl.trigger { isFullscreenTable = true }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = tableAccent.copy(alpha = 0.16f),
                                contentColor = tableAccent
                            ),
                            modifier = Modifier
                                .height(36.dp)
                                .neonTapFeedback(expandTapCtrl, tableAccent, 12.dp)
                                .testTag("above_table_fullscreen_btn")
                        ) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("تكبير القائمة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (filteredStudents.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(Icons.Filled.Groups, contentDescription = null, tint = tableAccent, modifier = Modifier.size(52.dp))
                                Text(
                                    "لا توجد أسماء طلبة في ${selectedGrade.shortAr} ${selectedSection.titleAr} حالياً",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "أضف أسماء طلبتك يدوياً (طالب واحد أو قائمة كاملة) أو استوردهم مباشرة من ملف Word أو Excel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textMuted,
                                    textAlign = TextAlign.Center
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            addDialogInitialTab = 1
                                            showAddStudentDialog = true
                                        },
                                        modifier = Modifier.testTag("empty_type_students_btn")
                                    ) {
                                        Icon(Icons.Filled.EditNote, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("كتابة أسماء الطلبة")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            addDialogInitialTab = 2
                                            showAddStudentDialog = true
                                        },
                                        modifier = Modifier.testTag("empty_import_word_excel_btn")
                                    ) {
                                        Icon(Icons.Filled.UploadFile, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("استيراد Word / Excel")
                                    }
                                }
                            }
                        }
                    } else if (activeFilter == GradebookFilterTab.ABSENTEES) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "تاريخ الغياب: ${formatCalendarDateArabic(absenceDateMillis)}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = primaryAccent
                                        )
                                        OutlinedButton(onClick = { showAbsenceCalendar = true }) {
                                            Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("التقويم")
                                        }
                                    }
                                }
                            }
                            items(filteredStudents, key = { it.id }) { st ->
                                val count = absenceCounts[st.id] ?: 0
                                val isAbsentSelectedDate = st.id in selectedDateAbsentIds
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = ext.cardElevated,
                                    border = BorderStroke(1.dp, if (isAbsentSelectedDate) DangerRed else ext.borderSubtle),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${st.orderNumber}. ${st.fullName}",
                                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                                                color = studentNameDisplayColor,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text("مجموع الغيابات: $count يوم", style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                                        }
                                        Button(
                                            onClick = { onToggleAbsenceForDate(st.id, selectedAbsenceDateStr) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isAbsentSelectedDate) DangerRed else MaterialTheme.colorScheme.surface
                                            )
                                        ) {
                                            Text(
                                                text = if (isAbsentSelectedDate) "غائب ✓" else "تسجيل غياب",
                                                color = if (isAbsentSelectedDate) Color.White else primaryAccent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val scrollHlColor = Color(state.profile.autoScrollHighlightColorHex)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = tableSurfaceBg,
                            border = BorderStroke(
                                if (ext.neonGlowEnabled) 1.5.dp else 1.3.dp,
                                if (ext.neonGlowEnabled) ledgerAccent.copy(alpha = 0.65f) else tableBorderColor
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .neonCardGlow(ledgerAccent, ext.neonGlowEnabled, 18.dp, alphaFactor = 0.28f)
                                .testTag("gradebook_table_surface_${currentLedgerDef.id}")
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(tableHeaderBg)
                                        .horizontalScroll(tableHScroll)
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HeaderCell(h.seqHeader, 38.dp, accentColor = tableAccent)
                                    HeaderCell(
                                        text = h.nameHeader,
                                        width = 238.dp,
                                        alignRight = true,
                                        accentColor = tableAccent
                                    )
                                    HeaderCell(h.t1M1Header, 58.dp, accentColor = tableAccent)
                                    if (settings.monthsPerTerm >= 2) HeaderCell(h.t1M2Header, 58.dp, accentColor = tableAccent)
                                    if (settings.monthsPerTerm >= 3) HeaderCell(h.t1M3Header, 58.dp, accentColor = tableAccent)
                                    HeaderCell(h.t1DailyHeader, 58.dp, accentColor = tableAccent)
                                    HeaderCell(h.t1AvgHeader, 64.dp, accentColor = tableAccent)
                                    HeaderCell(h.midYearHeader, 70.dp, accentColor = tableAccent)
                                    HeaderCell(h.t2M1Header, 58.dp, accentColor = tableAccent)
                                    if (settings.monthsPerTerm >= 2) HeaderCell(h.t2M2Header, 58.dp, accentColor = tableAccent)
                                    if (settings.monthsPerTerm >= 3) HeaderCell(h.t2M3Header, 58.dp, accentColor = tableAccent)
                                    HeaderCell(h.t2DailyHeader, 58.dp, accentColor = tableAccent)
                                    HeaderCell(h.t2AvgHeader, 64.dp, accentColor = tableAccent)
                                    HeaderCell(h.annualEffortHeader, 74.dp, accentColor = tableAccent)
                                    HeaderCell(h.finalExamHeader, 74.dp, accentColor = tableAccent)
                                    HeaderCell(h.finalGradeHeader, 76.dp, accentColor = tableAccent)
                                }

                                LazyColumn(
                                    state = tableListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 24.dp)
                                ) {
                                    items(filteredStudents, key = { it.id }) { st ->
                                        val g = st.gradesForLedger(currentLedgerDef.id)
                                        val res = GradeCalculator.computeForLedger(st, settings, currentLedgerDef.id)
                                        val isExempt = GradeCalculator.isExemptEligible(st, res, settings)
                                        val isMinisterial = GradeCalculator.isMinisterialQualified(st, res)
                                        val isAbsentToday = st.id in selectedDateAbsentIds
                                        val tagBg = when {
                                            st.colorTag != StudentColorTag.NONE -> Color(st.colorTag.colorHex).copy(alpha = 0.14f)
                                            state.profile.autoScrollEnabled -> scrollHlColor.copy(alpha = if (st.orderNumber % 2 == 0) 0.14f else 0.05f)
                                            st.orderNumber % 2 == 0 -> tableAltRowBg
                                            else -> Color.Transparent
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(tagBg)
                                                .clickable { selectedStudent = st }
                                                .horizontalScroll(tableHScroll)
                                                .padding(vertical = 10.dp, horizontal = 8.dp)
                                                .testTag("student_row_${st.id}"),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            DataCell(st.orderNumber.toString(), 38.dp, tableAccent, bold = true)
                                            Row(
                                                modifier = Modifier.width(238.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                // Clickable (غ) Absence Toggle Pill right beside student name
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (isAbsentToday) DangerRed else Color.Transparent,
                                                    border = BorderStroke(
                                                        1.2.dp,
                                                        if (isAbsentToday) DangerRed else tableBorderColor
                                                    ),
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .clickable {
                                                            onToggleAbsenceForDate(st.id, selectedAbsenceDateStr)
                                                        }
                                                        .testTag("quick_absent_toggle_${st.id}")
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = "غ",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isAbsentToday) Color.White else ext.textMuted
                                                        )
                                                    }
                                                }

                                                if (st.colorTag != StudentColorTag.NONE) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(st.colorTag.colorHex))
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text(
                                                            text = st.fullName,
                                                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = studentNameDisplayColor,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                        if (isExempt) {
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = SuccessGreen.copy(alpha = 0.18f),
                                                                border = BorderStroke(1.dp, SuccessGreen)
                                                            ) {
                                                                Text(
                                                                    text = "معفو",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    color = SuccessGreen,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        } else if (isMinisterial) {
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = SuccessGreen.copy(alpha = 0.18f),
                                                                border = BorderStroke(1.dp, SuccessGreen)
                                                            ) {
                                                                Text(
                                                                    text = "مؤهل وزاري",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    color = SuccessGreen,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                        if (st.notes.isNotBlank()) {
                                                            Text(
                                                                text = "(${st.notes})",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = WarningAmber,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                    if (st.badges.isNotEmpty() || st.isTransferred) {
                                                        val subtitleParts = buildList {
                                                            if (st.isTransferred) add("منقول")
                                                            addAll(st.badges.take(5))
                                                        }.joinToString(" ")
                                                        Text(
                                                            text = subtitleParts,
                                                            fontSize = 10.sp,
                                                            color = ledgerAccent,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                            DataCell(GradeCalculator.formatScore(res.term1Month1Effective, r), 58.dp)
                                            if (settings.monthsPerTerm >= 2) DataCell(GradeCalculator.formatScore(res.term1Month2Effective, r), 58.dp)
                                            if (settings.monthsPerTerm >= 3) DataCell(GradeCalculator.formatScore(res.term1Month3Effective, r), 58.dp)
                                            DataCell(GradeCalculator.formatScore(g.term1Daily, r), 58.dp, ext.textMuted)
                                            DataCell(GradeCalculator.formatScore(res.term1Average, r), 64.dp, tableAccent, bold = true)
                                            DataCell(GradeCalculator.formatScore(res.midYearEffective, r), 70.dp)
                                            DataCell(GradeCalculator.formatScore(res.term2Month1Effective, r), 58.dp)
                                            if (settings.monthsPerTerm >= 2) DataCell(GradeCalculator.formatScore(res.term2Month2Effective, r), 58.dp)
                                            if (settings.monthsPerTerm >= 3) DataCell(GradeCalculator.formatScore(res.term2Month3Effective, r), 58.dp)
                                            DataCell(GradeCalculator.formatScore(g.term2Daily, r), 58.dp, ext.textMuted)
                                            DataCell(GradeCalculator.formatScore(res.term2Average, r), 64.dp, tableAccent, bold = true)
                                            DataCell(GradeCalculator.formatScore(res.annualEffort, r), 74.dp, InfoBlue, bold = true)
                                            DataCell(GradeCalculator.formatScore(g.finalExam, r), 74.dp)
                                            val finColor = when (res.isPassing) {
                                                true -> tableAccent
                                                false -> DangerRed
                                                null -> MaterialTheme.colorScheme.onSurface
                                            }
                                            DataCell(GradeCalculator.formatScore(res.finalGrade, r), 76.dp, finColor, bold = true)
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                if (isFullscreenTable) {
                    Spacer(Modifier.height(6.dp))
                    DualLedgerSearchSection(
                        searchQuery = searchQuery,
                        onQueryChange = { searchQuery = it },
                        advFilter = advSearchFilter,
                        showAdvFilters = showAdvSearchFilters,
                        onToggleAdvFilters = { showAdvSearchFilters = !showAdvSearchFilters },
                        onUpdateAdvFilter = { advSearchFilter = it },
                        ledgers = ledgers,
                        primaryLedgerDef = currentLedgerDef,
                        secondLedgerId = searchSecondLedgerId,
                        onSelectSecondLedger = { searchSecondLedgerId = it },
                        selectedColumnKeys = selectedSearchColumnKeys,
                        onUpdateColumnKeys = { selectedSearchColumnKeys = it },
                        matchingStudents = filteredStudents,
                        settings = settings,
                        accentColor = tableAccent,
                        primaryAccent = primaryAccent,
                        borderColor = tableBorderColor,
                        studentNameColor = studentNameDisplayColor,
                        onInspectStudent = { selectedStudent = it }
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }

    selectedStudent?.let { st ->
        val latestSt = state.students.find { it.id == st.id } ?: st
        StudentActionDialog(
            student = latestSt,
            profile = state.profile,
            settings = settings,
            absencesCount = absenceCounts[latestSt.id] ?: 0,
            initialLedger = activeLedger,
            activeLedgerId = currentLedgerDef.id,
            safeEditUnlocked = safeEditUnlocked,
            onRequestUnlockSafeEdit = onRequestUnlockSafeEdit,
            onToggleTransferred = onToggleTransferred,
            onUpdateBadges = onUpdateBadges,
            onAddAvailableBadge = onAddAvailableBadge,
            onDismiss = { selectedStudent = null },
            onSave = onUpdateStudent,
            onMoveSection = { g, sec -> onMoveStudent(latestSt.id, g, sec) },
            onDelete = { onDeleteStudent(latestSt.id) },
            onOpenQr = { onNavigateToQrForStudent(latestSt) },
            onOpenCertificate = { onNavigateToCertForStudent(latestSt) },
            onShareParentReport = {
                selectedStudent = null
                onShareParentReport(latestSt)
            }
        )
    }

    if (showAddStudentDialog) {
        AddOrImportStudentsDialog(
            grade = selectedGrade,
            section = selectedSection,
            initialTab = addDialogInitialTab,
            onDismiss = { showAddStudentDialog = false },
            onAddSingle = { name, notes ->
                onAddStudent(name, selectedGrade, selectedSection, notes)
                showAddStudentDialog = false
            },
            onAddBatch = { names ->
                onAddStudentsBatch(names, selectedGrade, selectedSection)
                showAddStudentDialog = false
            }
        )
    }

    if (showCustomizeTableDialog) {
        CustomizeGradebookDialog(
            settings = settings,
            onDismiss = { showCustomizeTableDialog = false },
            onSave = {
                onUpdateSettings(it)
                showCustomizeTableDialog = false
            },
            profile = state.profile,
            onUpdateProfile = onUpdateProfile
        )
    }

    if (showAbsenceCalendar) {
        CalendarDatePickerDialog(
            initialMillis = absenceDateMillis,
            onDismiss = { showAbsenceCalendar = false },
            onDateSelected = {
                absenceDateMillis = it
                showAbsenceCalendar = false
            }
        )
    }

    if (showLedgerManagerDialog) {
        LedgerManagerDialog(
            ledgers = ledgers,
            currentLedger = currentLedgerDef,
            onDismiss = { showLedgerManagerDialog = false },
            onSelectLedger = {
                onSelectLedgerId(it)
                showLedgerManagerDialog = false
            },
            onAddLedger = { title, colorHex, template ->
                onAddCustomLedger(title, colorHex, template)
                showLedgerManagerDialog = false
            },
            onRenameOrStyle = { id, title, colorHex, template ->
                onRenameOrStyleLedger(id, title, colorHex, template)
                showLedgerManagerDialog = false
            },
            onDeleteLedger = {
                onDeleteLedger(it)
                showLedgerManagerDialog = false
            }
        )
    }

    if (showAutoScrollColorDialog) {
        AutoScrollColorPickerDialog(
            selectedHex = state.profile.autoScrollHighlightColorHex,
            onDismiss = { showAutoScrollColorDialog = false },
            onSelectHex = { hex ->
                onUpdateProfile(state.profile.copy(autoScrollHighlightColorHex = hex))
                showAutoScrollColorDialog = false
            }
        )
    }

    if (showTableNamesColorDialog) {
        TableAndNamesColorDialog(
            profile = state.profile,
            onUpdateProfile = onUpdateProfile,
            onDismiss = { showTableNamesColorDialog = false }
        )
    }

    if (showDiagnosticsDialog) {
        val insight = remember(sectionStudents, selectedGrade, selectedSection, settings, absenceCounts, activeLedger, currentLedgerDef.id) {
            val activePool = sectionStudents.filterNot { settings.hideTransferredStudents && it.isTransferred }
            GradeCalculator.analyzeClassroom(
                scopeTitle = "${selectedGrade.shortAr} (${selectedSection.id}) - ${currentLedgerDef.title}",
                students = activePool,
                settings = settings,
                absenceCountMap = absenceCounts,
                ledgerSubject = activeLedger,
                ledgerId = currentLedgerDef.id
            )
        }
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            icon = {
                Icon(Icons.Filled.Psychology, contentDescription = null, tint = tableAccent, modifier = Modifier.size(30.dp))
            },
            title = {
                Text(insight.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(insight.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    HorizontalDivider()
                    insight.recommendations.forEach { rec ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("•", color = tableAccent, fontWeight = FontWeight.Bold)
                            Text(rec, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (insight.topAchieverNames.isNotEmpty()) {
                        Text(
                            "أوائل الشعبة: ${insight.topAchieverNames.joinToString(" ، ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (insight.atRiskNames.isNotEmpty()) {
                        Text(
                            "طلبة يحتاجون متابعة: ${insight.atRiskNames.joinToString(" ، ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = DangerRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDiagnosticsDialog = false }) { Text("إغلاق") }
            }
        )
    }
}

@Composable
private fun SequentialAutoGradeEntryPanel(
    sectionStudents: List<Student>,
    columns: List<GradeColumnOption>,
    targetColumn: GradeColumnOption,
    activeLedgerId: String,
    activeLedgerTitle: String,
    accentColor: Color,
    onSelectColumn: (GradeColumnOption) -> Unit,
    onSaveSingleScore: (String, Double?) -> Unit,
    onDoneReturn: () -> Unit
) {
    val ext = LocalExtendedColors.current
    var currentIndex by remember(sectionStudents.size, targetColumn.key, activeLedgerId) { mutableStateOf(0) }
    val currentStudent = sectionStudents.getOrNull(currentIndex)
    var scoreInput by remember(currentStudent?.id, targetColumn.key, activeLedgerId) {
        val existing = currentStudent?.gradesForLedger(activeLedgerId)?.scoreForColumn(targetColumn.key)
        mutableStateOf(existing?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "")
    }
    var lastSavedBanner by remember { mutableStateOf<String?>(null) }

    fun saveAndAdvance() {
        val st = currentStudent ?: return
        val valNum = scoreInput.trim().toDoubleOrNull()?.coerceIn(0.0, 100.0)
        onSaveSingleScore(st.id, valNum)
        lastSavedBanner = "تم حفظ (${valNum?.toInt() ?: "فارغ"}) في $activeLedgerTitle للطالب: ${st.fullName}"
        if (currentIndex < sectionStudents.lastIndex) {
            currentIndex++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("اختر العمود المراد إدخال درجاته تلقائياً ($activeLedgerTitle):", style = MaterialTheme.typography.labelLarge, color = accentColor)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            columns.forEach { col ->
                FilterChip(
                    selected = col.key == targetColumn.key,
                    onClick = { onSelectColumn(col) },
                    label = { Text(col.label) }
                )
            }
        }

        if (sectionStudents.isEmpty() || currentStudent == null) {
            Text("لا يوجد طلبة في هذه الشعبة.", color = ext.textMuted)
            return@Column
        }

        val progress = (currentIndex + 1).toFloat() / sectionStudents.size.coerceAtLeast(1)
        LinearProgressIndicator(
            progress = { progress },
            color = accentColor,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
        Text(
            "الطالب ${currentIndex + 1} من ${sectionStudents.size} • السجل: $activeLedgerTitle • العمود: ${targetColumn.label}",
            style = MaterialTheme.typography.labelLarge,
            color = ext.textMuted
        )

        lastSavedBanner?.let {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accentColor.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(it, style = MaterialTheme.typography.bodySmall, color = accentColor, modifier = Modifier.padding(8.dp))
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = ext.cardElevated,
            border = BorderStroke(1.8.dp, accentColor.copy(alpha = 0.65f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "تسلسل #${currentStudent.orderNumber} • $activeLedgerTitle",
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Text(
                    text = currentStudent.fullName,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = scoreInput,
                    onValueChange = { txt ->
                        if (txt.isEmpty() || txt.matches(Regex("^\\d{0,3}(\\.\\d{0,2})?$"))) {
                            scoreInput = txt
                        }
                    },
                    label = { Text("درجة ${targetColumn.label} (0 - 100)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { saveAndAdvance() },
                        onDone = { saveAndAdvance() }
                    ),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(0.7f).testTag("sequential_score_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        enabled = currentIndex > 0,
                        onClick = { currentIndex-- },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("السابق")
                    }
                    OutlinedButton(
                        onClick = {
                            if (currentIndex < sectionStudents.lastIndex) currentIndex++
                            lastSavedBanner = "تم تخطي: ${currentStudent.fullName}"
                        },
                        modifier = Modifier.weight(1f).testTag("sequential_skip_btn")
                    ) {
                        Text("تخطي")
                    }
                    Button(
                        onClick = { saveAndAdvance() },
                        modifier = Modifier.weight(1.4f).testTag("sequential_save_next_btn")
                    ) {
                        Text(if (currentIndex < sectionStudents.lastIndex) "حفظ والتالي" else "حفظ")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Quick Student Jump List
        Text("أو انتقل لأي طالب في الشعبة مباشرة:", style = MaterialTheme.typography.labelLarge, color = accentColor)
        sectionStudents.forEachIndexed { idx, st ->
            val savedScore = st.gradesForLedger(activeLedgerId).scoreForColumn(targetColumn.key)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (idx == currentIndex) MaterialTheme.colorScheme.primaryContainer else ext.cardElevated,
                border = BorderStroke(1.dp, if (idx == currentIndex) accentColor else ext.borderSubtle),
                modifier = Modifier.fillMaxWidth().clickable { currentIndex = idx }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${st.orderNumber}. ${st.fullName}",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = savedScore?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        OutlinedButton(onClick = onDoneReturn, modifier = Modifier.fillMaxWidth()) {
            Text("العودة لعرض الجدول الكامل")
        }
    }
}

@Composable
private fun GradeImportPanel(
    sectionStudents: List<Student>,
    columns: List<GradeColumnOption>,
    targetColumn: GradeColumnOption,
    activeLedgerTitle: String,
    accentColor: Color,
    rawImportedText: String,
    onRawTextChange: (String) -> Unit,
    onSelectColumn: (GradeColumnOption) -> Unit,
    onCommitGrades: (Map<String, Double?>) -> Unit
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val scope = rememberCoroutineScope()

    var candidates by remember(rawImportedText, sectionStudents) {
        mutableStateOf(
            if (rawImportedText.isNotBlank()) OcrAndImportHelper.parseGradeCandidates(rawImportedText, sectionStudents)
            else emptyList()
        )
    }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var isOcrBusy by remember { mutableStateOf(false) }

    val importDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "import.txt"
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) fileName = c.getString(idx) ?: fileName
                }
            }
            val extracted = OcrAndImportHelper.extractPlainTextFromDocumentUri(context, uri, fileName)
            onRawTextChange(extracted)
            candidates = OcrAndImportHelper.parseGradeCandidates(extracted, sectionStudents)
            statusBanner = "تم استيراد ${candidates.size} سطراً من ($fileName) ✓"
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isOcrBusy = true
            scope.launch {
                val bmp = OcrAndImportHelper.decodeBitmapFromUri(context, uri)
                if (bmp != null) {
                    OcrAndImportHelper.recognizeTextFromBitmap(bmp)
                        .onSuccess { text ->
                            onRawTextChange(text)
                            candidates = OcrAndImportHelper.parseGradeCandidates(text, sectionStudents)
                            statusBanner = "تم استخراج ${candidates.size} سطر من الصورة ✓"
                        }
                        .onFailure { statusBanner = "تعذر قراءة الصورة، جرّب صورة أوضح." }
                }
                isOcrBusy = false
            }
        }
    }

    val cameraImageUri = remember {
        val file = File(context.cacheDir, "grade_capture_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) {
            isOcrBusy = true
            scope.launch {
                val bmp = OcrAndImportHelper.decodeBitmapFromUri(context, cameraImageUri)
                if (bmp != null) {
                    OcrAndImportHelper.recognizeTextFromBitmap(bmp)
                        .onSuccess { text ->
                            onRawTextChange(text)
                            candidates = OcrAndImportHelper.parseGradeCandidates(text, sectionStudents)
                            statusBanner = "تم تصوير السجل وتفريغ ${candidates.size} سطر ✓"
                        }
                }
                isOcrBusy = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("العمود المستهدف للحفظ ($activeLedgerTitle):", style = MaterialTheme.typography.labelLarge, color = accentColor)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                columns.forEach { col ->
                    FilterChip(
                        selected = col.key == targetColumn.key,
                        onClick = { onSelectColumn(col) },
                        label = { Text(col.label) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        importDocLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/msword",
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "text/*"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.UploadFile, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Excel / Word", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { takePictureLauncher.launch(cameraImageUri) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PhotoCamera, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("كاميرا", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Image, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("صورة", maxLines = 1)
                }
            }
            if (isOcrBusy) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = accentColor)
            }
            statusBanner?.let { msg ->
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = accentColor.copy(alpha = 0.16f), modifier = Modifier.fillMaxWidth()) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = accentColor, modifier = Modifier.padding(8.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = rawImportedText,
                onValueChange = { onRawTextChange(it) },
                label = { Text("الصق النص أو ملف الدرجات هنا (مثال: علي احمد، 85)") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    candidates = OcrAndImportHelper.parseGradeCandidates(rawImportedText, sectionStudents)
                    statusBanner = "تمت مطابقة ${candidates.size} سطر."
                }) {
                    Text("تحليل ومطابقة")
                }
                if (candidates.isNotEmpty()) {
                    Button(onClick = {
                        val updates = candidates
                            .filter { it.matchedStudentId != null && it.extractedScore != null }
                            .associate { it.matchedStudentId!! to it.extractedScore }
                        onCommitGrades(updates)
                        statusBanner = "تم تثبيت ${updates.size} درجة في (${targetColumn.label}) ✓"
                    }) {
                        Text("تثبيت (${candidates.count { it.matchedStudentId != null && it.extractedScore != null }})")
                    }
                }
            }
        }

        items(candidates.indices.toList(), key = { candidates[it].id }) { idx ->
            val cand = candidates[idx]
            var scoreTxt by remember(cand.id) { mutableStateOf(cand.extractedScore?.toInt()?.toString() ?: "") }
            var showPickStudent by remember { mutableStateOf(false) }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ext.cardElevated,
                border = BorderStroke(1.dp, ext.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("النص المستخرج: ${cand.extractedName}", style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showPickStudent = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(cand.matchedStudentName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedTextField(
                            value = scoreTxt,
                            onValueChange = { txt ->
                                scoreTxt = txt
                                val num = txt.toDoubleOrNull()?.coerceIn(0.0, 100.0)
                                candidates = candidates.toMutableList().also { list ->
                                    list[idx] = cand.copy(extractedScore = num)
                                }
                            },
                            label = { Text("الدرجة") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(90.dp)
                        )
                    }
                }
            }

            if (showPickStudent) {
                AlertDialog(
                    onDismissRequest = { showPickStudent = false },
                    title = { Text("اختر الطالب المطابق") },
                    text = {
                        LazyColumn(modifier = Modifier.height(260.dp)) {
                            items(sectionStudents, key = { it.id }) { st ->
                                TextButton(
                                    onClick = {
                                        candidates = candidates.toMutableList().also { list ->
                                            list[idx] = cand.copy(
                                                matchedStudentId = st.id,
                                                matchedStudentName = st.fullName
                                            )
                                        }
                                        showPickStudent = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("${st.orderNumber}. ${st.fullName}", modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showPickStudent = false }) { Text("إغلاق") }
                    }
                )
            }
        }
    }
}

@Composable
private fun ExportChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = accentColor) }
    )
}

@Composable
private fun HeaderCell(
    text: String,
    width: Dp,
    alignRight: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = accentColor,
        fontWeight = FontWeight.Bold,
        textAlign = if (alignRight) TextAlign.Right else TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(horizontal = 2.dp)
    )
}

@Composable
private fun DataCell(
    text: String,
    width: Dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.width(width)
    )
}

@Composable
fun CustomizeGradebookDialog(
    settings: GradebookSettings,
    onDismiss: () -> Unit,
    onSave: (GradebookSettings) -> Unit,
    profile: TeacherProfile? = null,
    onUpdateProfile: (TeacherProfile) -> Unit = {}
) {
    var months by remember { mutableStateOf(settings.monthsPerTerm) }
    var dailyMode by remember { mutableStateOf(settings.dailyCalcMode) }
    var splitMid by remember { mutableStateOf(settings.splitMidYearOralWritten) }
    var rounding by remember { mutableStateOf(settings.enableSmartRounding) }
    var t1m1H by remember { mutableStateOf(settings.headers.t1M1Header) }
    var t1m2H by remember { mutableStateOf(settings.headers.t1M2Header) }
    var t1AvgH by remember { mutableStateOf(settings.headers.t1AvgHeader) }
    var midH by remember { mutableStateOf(settings.headers.midYearHeader) }
    var effortH by remember { mutableStateOf(settings.headers.annualEffortHeader) }
    var finH by remember { mutableStateOf(settings.headers.finalGradeHeader) }
    var simpleMode by remember(profile?.gradebookSimpleMode) { mutableStateOf(profile?.gradebookSimpleMode ?: false) }
    var selectedActions by remember(profile?.gradebookVisibleActions) {
        mutableStateOf(profile?.gradebookVisibleActions?.toSet() ?: emptySet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تصميم السجل وقواعد الاحتساب") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (profile != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("الوضع المبسط للسجل", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "إخفاء الأزرار والأدوات الإضافية فقط مع إبقاء كافة الدرجات والأعمدة كما هي.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalExtendedColors.current.textMuted
                                    )
                                }
                                Switch(
                                    checked = simpleMode,
                                    onCheckedChange = { simpleMode = it },
                                    modifier = Modifier.testTag("gradebook_simple_mode_switch")
                                )
                            }
                            if (simpleMode) {
                                Text("الأزرار المسموح إظهارها في الوضع المبسط:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                GradebookToolbarActions.chunked(2).forEach { pair ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        pair.forEach { (key, title) ->
                                            val checked = key in selectedActions
                                            FilterChip(
                                                selected = checked,
                                                onClick = {
                                                    selectedActions = if (checked) selectedActions - key else selectedActions + key
                                                },
                                                label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                Text("عدد الأشهر في كل فصل:", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "شهر واحد", 2 to "شهران", 3 to "3 أشهر").forEach { (m, lbl) ->
                        FilterChip(selected = months == m, onClick = { months = m }, label = { Text(lbl) })
                    }
                }

                Text("احتساب درجة اليومي والسلوك:", style = MaterialTheme.typography.titleSmall)
                DailyCalcMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dailyMode = mode }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = dailyMode == mode, onClick = { dailyMode = mode })
                        Column {
                            Text(mode.titleAr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(mode.descriptionAr, style = MaterialTheme.typography.bodySmall, color = LocalExtendedColors.current.textMuted)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("التقريب الذكي (55.5→56، 55.3→55)", modifier = Modifier.weight(1f))
                    Switch(checked = rounding, onCheckedChange = { rounding = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("تقسيم نصف السنة (شفوي + تحريري)", modifier = Modifier.weight(1f))
                    Switch(checked = splitMid, onCheckedChange = { splitMid = it })
                }

                HorizontalDivider()
                Text("تعديل أسماء رؤوس الأعمدة:", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = t1m1H, onValueChange = { t1m1H = it }, label = { Text("عمود ش1") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = t1m2H, onValueChange = { t1m2H = it }, label = { Text("عمود ش2") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = t1AvgH, onValueChange = { t1AvgH = it }, label = { Text("معدل ف1") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = midH, onValueChange = { midH = it }, label = { Text("نصف السنة") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = effortH, onValueChange = { effortH = it }, label = { Text("السعي") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = finH, onValueChange = { finH = it }, label = { Text("النهائية") }, modifier = Modifier.weight(1f), singleLine = true)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (profile != null) {
                    onUpdateProfile(
                        profile.copy(
                            gradebookSimpleMode = simpleMode,
                            gradebookVisibleActions = selectedActions.toList()
                        )
                    )
                }
                onSave(
                    settings.copy(
                        monthsPerTerm = months,
                        dailyCalcMode = dailyMode,
                        splitMidYearOralWritten = splitMid,
                        enableSmartRounding = rounding,
                        headers = settings.headers.copy(
                            t1M1Header = t1m1H.ifBlank { "ش1 ف1" },
                            t1M2Header = t1m2H.ifBlank { "ش2 ف1" },
                            t1AvgHeader = t1AvgH.ifBlank { "معدل ف1" },
                            midYearHeader = midH.ifBlank { "نصف السنة" },
                            annualEffortHeader = effortH.ifBlank { "السعي السنوي" },
                            finalGradeHeader = finH.ifBlank { "الدرجة النهائية" }
                        )
                    )
                )
            }) { Text("حفظ التصميم") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun AddOrImportStudentsDialog(
    grade: GradeLevel,
    section: SectionLetter,
    initialTab: Int,
    onDismiss: () -> Unit,
    onAddSingle: (String, String) -> Unit,
    onAddBatch: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab.coerceIn(0, 2)) }

    // Single student fields
    var singleName by remember { mutableStateOf("") }
    var singleNotes by remember { mutableStateOf("") }

    // Batch typed or imported fields
    var batchText by remember { mutableStateOf("") }
    var importedFileLabel by remember { mutableStateOf<String?>(null) }

    val parsedNames = remember(batchText) {
        OcrAndImportHelper.parseStudentNamesList(batchText)
    }

    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "students.txt"
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) fileName = c.getString(idx) ?: fileName
                }
            }
            val extracted = OcrAndImportHelper.extractPlainTextFromDocumentUri(context, uri, fileName)
            val names = OcrAndImportHelper.parseStudentNamesList(extracted)
            batchText = if (names.isNotEmpty()) names.joinToString("\n") else extracted
            importedFileLabel = "$fileName (${names.size} اسم)"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("إضافة طلبة — ${grade.titleAr} (${section.titleAr})")
                Text(
                    "اكتب الأسماء مباشرة أو استوردها من ملف Word أو Excel",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        leadingIcon = { Icon(Icons.Filled.PersonAdd, null, modifier = Modifier.size(16.dp)) },
                        label = { Text("طالب واحد") }
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        leadingIcon = { Icon(Icons.Filled.EditNote, null, modifier = Modifier.size(16.dp)) },
                        label = { Text("كتابة قائمة") }
                    )
                    FilterChip(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        leadingIcon = { Icon(Icons.Filled.UploadFile, null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Word / Excel") }
                    )
                }

                when (selectedTab) {
                    0 -> {
                        OutlinedTextField(
                            value = singleName,
                            onValueChange = { singleName = it },
                            label = { Text("اسم الطالب الثلاثي أو الرباعي") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_student_name_input")
                        )
                        OutlinedTextField(
                            value = singleNotes,
                            onValueChange = { singleNotes = it },
                            label = { Text("ملاحظات (اختياري)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    1 -> {
                        OutlinedTextField(
                            value = batchText,
                            onValueChange = { batchText = it },
                            label = { Text("اكتب أو الصق أسماء الطلبة (كل اسم في سطر)") },
                            placeholder = { Text("علي حسن كاظم\nمريم كريم جاسم\nنور الهدى محمد") },
                            minLines = 6,
                            maxLines = 12,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("batch_student_names_input")
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (parsedNames.isEmpty())
                                    "اكتب اسماً في كل سطر ليتم فرزهم تلقائياً وإضافتهم للشعبة."
                                else
                                    "تم التعرف على ${parsedNames.size} اسم طالب جاهز للإضافة ✓",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    2 -> {
                        Button(
                            onClick = {
                                docLauncher.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "application/msword",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "text/plain",
                                        "text/*"
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_students_doc_btn")
                        ) {
                            Icon(Icons.Filled.UploadFile, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("اختيار ملف Excel (.xlsx) أو Word (.docx)")
                        }

                        importedFileLabel?.let { lbl ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "الملف المستورد: $lbl",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = batchText,
                            onValueChange = { batchText = it },
                            label = { Text("معاينة وتعديل الأسماء المستخرجة (${parsedNames.size})") },
                            minLines = 5,
                            maxLines = 10,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                Button(
                    enabled = singleName.trim().length >= 2,
                    onClick = { onAddSingle(singleName.trim(), singleNotes.trim()) },
                    modifier = Modifier.testTag("confirm_add_student_btn")
                ) {
                    Text("إضافة الطالب")
                }
            } else {
                Button(
                    enabled = parsedNames.isNotEmpty(),
                    onClick = { onAddBatch(parsedNames) },
                    modifier = Modifier.testTag("confirm_add_batch_btn")
                ) {
                    Text("إضافة (${parsedNames.size}) طالب")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DualLedgerSearchSection(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    advFilter: com.example.utils.AdvancedStudentSearchFilter,
    showAdvFilters: Boolean,
    onToggleAdvFilters: () -> Unit,
    onUpdateAdvFilter: (com.example.utils.AdvancedStudentSearchFilter) -> Unit,
    ledgers: List<CustomLedgerDef>,
    primaryLedgerDef: CustomLedgerDef,
    secondLedgerId: String?,
    onSelectSecondLedger: (String?) -> Unit,
    selectedColumnKeys: List<String>,
    onUpdateColumnKeys: (List<String>) -> Unit,
    matchingStudents: List<Student>,
    settings: GradebookSettings,
    accentColor: Color,
    primaryAccent: Color,
    borderColor: Color,
    studentNameColor: Color,
    onInspectStudent: (Student) -> Unit
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val r = settings.enableSmartRounding
    val h = primaryLedgerDef.customHeaders ?: settings.headers
    val secondLedgerDef = secondLedgerId?.let { id -> ledgers.find { it.id == id && it.id != primaryLedgerDef.id } }
    var showColumnSelector by rememberSaveable { mutableStateOf(false) }
    var customMinDraft by remember(advFilter.minScore) {
        mutableStateOf(advFilter.minScore?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty())
    }
    var customMaxDraft by remember(advFilter.maxScore) {
        mutableStateOf(advFilter.maxScore?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty())
    }

    val availableSearchCols = remember(h, settings.monthsPerTerm) {
        buildList {
            add("t1m1" to h.t1M1Header)
            if (settings.monthsPerTerm >= 2) add("t1m2" to h.t1M2Header)
            if (settings.monthsPerTerm >= 3) add("t1m3" to h.t1M3Header)
            add("t1d" to h.t1DailyHeader)
            add("t1avg" to h.t1AvgHeader)
            add("mid" to h.midYearHeader)
            add("t2m1" to h.t2M1Header)
            if (settings.monthsPerTerm >= 2) add("t2m2" to h.t2M2Header)
            if (settings.monthsPerTerm >= 3) add("t2m3" to h.t2M3Header)
            add("t2d" to h.t2DailyHeader)
            add("t2avg" to h.t2AvgHeader)
            add("effort" to h.annualEffortHeader)
            add("final_exam" to h.finalExamHeader)
            add("final_grade" to h.finalGradeHeader)
        }
    }

    val activeSearchCols = remember(availableSearchCols, selectedColumnKeys) {
        availableSearchCols.filter { it.first in selectedColumnKeys }
            .ifEmpty { availableSearchCols.filter { it.first in setOf("t1avg", "effort", "final_grade") } }
    }

    fun formatColValue(g: StudentGrades, res: ComputedStudentResult, colKey: String): String = when (colKey) {
        "t1m1" -> GradeCalculator.formatScore(res.term1Month1Effective, r)
        "t1m2" -> GradeCalculator.formatScore(res.term1Month2Effective, r)
        "t1m3" -> GradeCalculator.formatScore(res.term1Month3Effective, r)
        "t1d" -> GradeCalculator.formatScore(g.term1Daily, r)
        "t1avg" -> GradeCalculator.formatScore(res.term1Average, r)
        "mid" -> GradeCalculator.formatScore(res.midYearEffective, r)
        "t2m1" -> GradeCalculator.formatScore(res.term2Month1Effective, r)
        "t2m2" -> GradeCalculator.formatScore(res.term2Month2Effective, r)
        "t2m3" -> GradeCalculator.formatScore(res.term2Month3Effective, r)
        "t2d" -> GradeCalculator.formatScore(g.term2Daily, r)
        "t2avg" -> GradeCalculator.formatScore(res.term2Average, r)
        "effort" -> GradeCalculator.formatScore(res.annualEffort, r)
        "final_exam" -> GradeCalculator.formatScore(g.finalExam, r)
        "final_grade" -> GradeCalculator.formatScore(res.finalGrade, r)
        else -> "-"
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = showColumnSelector,
                onClick = { showColumnSelector = !showColumnSelector },
                leadingIcon = { Icon(Icons.Filled.ViewColumn, null, modifier = Modifier.size(16.dp)) },
                label = {
                    Text(
                        "أعمدة البحث (${activeSearchCols.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier.testTag("search_columns_toggle_btn")
            )
            if (ledgers.size > 1) {
                FilterChip(
                    selected = secondLedgerDef == null,
                    onClick = { onSelectSecondLedger(null) },
                    label = { Text("سجل واحد (${primaryLedgerDef.title})", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("search_single_ledger_chip")
                )
                ledgers.filter { it.id != primaryLedgerDef.id }.forEach { other ->
                    FilterChip(
                        selected = secondLedgerDef?.id == other.id,
                        onClick = {
                            onSelectSecondLedger(if (secondLedgerDef?.id == other.id) null else other.id)
                        },
                        label = { Text("معاً: ${primaryLedgerDef.title} + ${other.title}", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("search_dual_ledger_chip_${other.id}")
                    )
                }
            }
        }

        if (showColumnSelector) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ext.cardElevated,
                border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "اختر الأعمدة التي تريد إظهارها في نتائج البحث (تبقى محفوظة حتى تغيرها):",
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.textMuted
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val allSelected = availableSearchCols.all { it.first in selectedColumnKeys }
                        FilterChip(
                            selected = allSelected,
                            onClick = {
                                onUpdateColumnKeys(
                                    if (allSelected) listOf("t1avg", "mid", "effort", "final_grade")
                                    else availableSearchCols.map { it.first }
                                )
                            },
                            label = { Text(if (allSelected) "المختصر" else "الكل", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("search_col_chip_ALL")
                        )
                        availableSearchCols.forEach { (colKey, colLabel) ->
                            val isSel = colKey in selectedColumnKeys
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    val next = if (isSel) {
                                        val dropped = selectedColumnKeys - colKey
                                        dropped.ifEmpty { listOf(colKey) }
                                    } else {
                                        selectedColumnKeys + colKey
                                    }
                                    onUpdateColumnKeys(next)
                                },
                                label = { Text(colLabel, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("search_col_chip_$colKey")
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                placeholder = {
                    val label = when {
                        advFilter.nameMatchMode == com.example.utils.StudentNameMatchMode.FIRST_NAME_ONLY ->
                            "بحث باسم الطالب الأول فقط..."
                        secondLedgerDef != null ->
                            "بحث في (${primaryLedgerDef.title}) و (${secondLedgerDef.title})..."
                        else ->
                            "بحث سريع في سجل ${primaryLedgerDef.title}..."
                    }
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = accentColor) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "مسح")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).testTag("search_student_input")
            )

            com.example.ui.components.HoldToSpeakMicButton(
                onRecognizedText = { spoken ->
                    val cleaned = when (val res = com.example.utils.VoiceCommandRouter.resolveVoiceCommand(spoken)) {
                        is com.example.utils.VoiceCommandResolution.SearchStudent -> res.query
                        is com.example.utils.VoiceCommandResolution.Navigate -> spoken
                        com.example.utils.VoiceCommandResolution.Empty -> ""
                    }
                    if (cleaned.isNotBlank()) onQueryChange(cleaned)
                },
                onStatusMessage = { msg ->
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                },
                testTagName = "gradebook_voice_search_btn"
            )

            FilterChip(
                selected = showAdvFilters || advFilter.hasActiveStructuredFilters,
                onClick = onToggleAdvFilters,
                leadingIcon = { Icon(Icons.Filled.FilterList, null, modifier = Modifier.size(16.dp)) },
                label = { Text("فلاتر") },
                modifier = Modifier.testTag("gradebook_adv_filter_toggle")
            )
        }

        if (showAdvFilters) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ext.cardElevated,
                border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth().testTag("gradebook_adv_filters_panel")
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("فلاتر البحث في السجل:", style = MaterialTheme.typography.labelLarge, color = primaryAccent, fontWeight = FontWeight.Bold)
                        if (advFilter.hasActiveStructuredFilters) {
                            TextButton(onClick = {
                                onUpdateAdvFilter(com.example.utils.AdvancedStudentSearchFilter())
                            }) { Text("مسح الفلاتر") }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        com.example.utils.StudentNameMatchMode.entries.forEach { mode ->
                            FilterChip(
                                selected = advFilter.nameMatchMode == mode,
                                onClick = { onUpdateAdvFilter(advFilter.copy(nameMatchMode = mode)) },
                                label = { Text(mode.titleAr, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("gb_filter_name_${mode.name}")
                            )
                        }
                        com.example.utils.SectionScopeFilter.entries.forEach { scope ->
                            FilterChip(
                                selected = advFilter.sectionScope == scope,
                                onClick = { onUpdateAdvFilter(advFilter.copy(sectionScope = scope, specificSection = null)) },
                                label = { Text(scope.titleAr, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("gb_filter_scope_${scope.name}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        com.example.utils.ScoreRangeFilter.entries.forEach { sr ->
                            FilterChip(
                                selected = advFilter.scoreRange == sr,
                                onClick = { onUpdateAdvFilter(advFilter.copy(scoreRange = sr)) },
                                label = { Text(sr.titleAr, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("gb_filter_score_${sr.name}")
                            )
                        }
                    }

                    if (advFilter.scoreRange == com.example.utils.ScoreRangeFilter.CUSTOM) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customMinDraft,
                                onValueChange = { txt ->
                                    val c = txt.filter { it.isDigit() || it == '.' }
                                    customMinDraft = c
                                    onUpdateAdvFilter(advFilter.copy(minScore = c.toDoubleOrNull()))
                                },
                                label = { Text("من درجة") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customMaxDraft,
                                onValueChange = { txt ->
                                    val c = txt.filter { it.isDigit() || it == '.' }
                                    customMaxDraft = c
                                    onUpdateAdvFilter(advFilter.copy(maxScore = c.toDoubleOrNull()))
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
                        com.example.utils.AcademicStatusFilter.entries.forEach { st ->
                            FilterChip(
                                selected = advFilter.academicStatus == st,
                                onClick = { onUpdateAdvFilter(advFilter.copy(academicStatus = st)) },
                                label = { Text(st.titleAr, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        com.example.utils.BehaviorSearchFilter.entries.forEach { bh ->
                            FilterChip(
                                selected = advFilter.behaviorFilter == bh,
                                onClick = { onUpdateAdvFilter(advFilter.copy(behaviorFilter = bh)) },
                                label = { Text(bh.titleAr, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        if ((searchQuery.isNotBlank() || advFilter.hasActiveStructuredFilters) && matchingStudents.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(matchingStudents.take(12), key = { "search_preview_${it.id}" }) { st ->
                    val primGrades = st.gradesForLedger(primaryLedgerDef.id)
                    val primRes = GradeCalculator.computeForLedger(st, settings, primaryLedgerDef.id)
                    val secGrades = secondLedgerDef?.let { st.gradesForLedger(it.id) }
                    val secRes = secondLedgerDef?.let { GradeCalculator.computeForLedger(st, settings, it.id) }
                    val exempt = GradeCalculator.isExemptEligible(st, primRes, settings)
                    val ministerial = GradeCalculator.isMinisterialQualified(st, primRes)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.2.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .neonCardGlow(primaryAccent, ext.neonGlowEnabled, 14.dp, alphaFactor = 0.20f)
                            .clickable { onInspectStudent(st) }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "${st.orderNumber}. ${st.fullName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = studentNameColor,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = primaryAccent.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        "${st.gradeLevel.shortAr} (${st.section.id})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = primaryAccent,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (exempt || ministerial) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = SuccessGreen.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, SuccessGreen)
                                    ) {
                                        Text(
                                            if (ministerial) "مؤهل وزاري" else "معفو",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (secondLedgerDef != null && secGrades != null && secRes != null) {
                                val secColor = Color(secondLedgerDef.headerColorHex)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.45f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                primaryLedgerDef.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = primaryAccent,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                activeSearchCols.forEach { (colKey, colLabel) ->
                                                    Text(
                                                        "$colLabel: ${formatColValue(primGrades, primRes, colKey)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = secColor.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, secColor.copy(alpha = 0.55f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                secondLedgerDef.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = secColor,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                activeSearchCols.forEach { (colKey, colLabel) ->
                                                    Text(
                                                        "$colLabel: ${formatColValue(secGrades, secRes, colKey)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    activeSearchCols.forEach { (colKey, colLabel) ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                            border = BorderStroke(0.8.dp, primaryAccent.copy(alpha = 0.45f))
                                        ) {
                                            Text(
                                                text = "$colLabel: ${formatColValue(primGrades, primRes, colKey)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = primaryAccent,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
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
    }
}

@Composable
private fun LedgerManagerDialog(
    ledgers: List<CustomLedgerDef>,
    currentLedger: CustomLedgerDef,
    onDismiss: () -> Unit,
    onSelectLedger: (String) -> Unit,
    onAddLedger: (String, Long, GradebookHeaders?) -> Unit,
    onRenameOrStyle: (String, String, Long, GradebookHeaders?) -> Unit,
    onDeleteLedger: (String) -> Unit
) {
    var modeAdd by remember { mutableStateOf(false) }
    var titleInput by remember(currentLedger, modeAdd) {
        mutableStateOf(if (modeAdd) "" else currentLedger.title)
    }
    var selectedColorHex by remember(currentLedger, modeAdd) {
        mutableStateOf(if (modeAdd) ledgerHeaderColors.first() else currentLedger.headerColorHex)
    }
    var selectedTemplate by remember { mutableStateOf<SubjectSpecialty?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (modeAdd) "إضافة سجل درجات جديد" else "إدارة السجلات وتخصيص العناوين") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !modeAdd,
                        onClick = { modeAdd = false },
                        label = { Text("تعديل السجل الحالي") }
                    )
                    FilterChip(
                        selected = modeAdd,
                        onClick = { modeAdd = true },
                        label = { Text("+ سجل جديد") },
                        modifier = Modifier.testTag("ledger_manager_new_tab")
                    )
                }

                if (!modeAdd && ledgers.size > 1) {
                    Text("التبديل السريع:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ledgers.forEach { led ->
                            FilterChip(
                                selected = led.id == currentLedger.id,
                                onClick = { onSelectLedger(led.id) },
                                label = { Text(led.title) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("اسم السجل (مثال: الأحياء، التطبيق العملي، القراءة)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("ledger_title_input")
                )

                Text("لون شريط رأس الجدول:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ledgerHeaderColors.forEach { hex ->
                        val isSel = hex == selectedColorHex
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .clickable { selectedColorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSel) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Text("قالب أعمدة جاهز حسب المادة (اختياري):", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedTemplate == null,
                        onClick = { selectedTemplate = null },
                        label = { Text("عادي") }
                    )
                    listOf(SubjectSpecialty.ISLAMIC, SubjectSpecialty.ARABIC, SubjectSpecialty.ENGLISH).forEach { sp ->
                        FilterChip(
                            selected = selectedTemplate == sp,
                            onClick = { selectedTemplate = sp },
                            label = { Text(sp.titleAr) }
                        )
                    }
                }

                if (!modeAdd && currentLedger.id != "general") {
                    OutlinedButton(
                        onClick = { onDeleteLedger(currentLedger.id) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("حذف هذا السجل المخصص")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = titleInput.trim().isNotBlank(),
                onClick = {
                    val tpl = selectedTemplate?.let { presetHeadersForSpecialty(it) }
                    if (modeAdd) {
                        onAddLedger(titleInput.trim(), selectedColorHex, tpl)
                    } else {
                        onRenameOrStyle(currentLedger.id, titleInput.trim(), selectedColorHex, tpl)
                    }
                },
                modifier = Modifier.testTag("confirm_ledger_save_btn")
            ) {
                Text(if (modeAdd) "إنشاء السجل" else "حفظ التغييرات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
private fun AutoScrollColorPickerDialog(
    selectedHex: Long,
    onDismiss: () -> Unit,
    onSelectHex: (Long) -> Unit
) {
    val options = listOf(
        0xFF00E5FFL to "سماوي متوهج",
        0xFFFFD54FL to "ذهبي دافئ",
        0xFF2ED573L to "زمردي هادئ",
        0xFFA55EEAL to "بنفسجي ملكي",
        0xFFFF6B81L to "مرجاني لطيف"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر لون تمييز الصفوف أثناء التحريك البطيء") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (hex, title) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(hex).copy(alpha = 0.16f))
                            .clickable { onSelectHex(hex) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(Color(hex)))
                            Text(title, fontWeight = FontWeight.Medium)
                        }
                        if (hex == selectedHex) Icon(Icons.Filled.CheckCircle, null, tint = Color(hex))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
private fun TableAndNamesColorDialog(
    profile: TeacherProfile,
    onUpdateProfile: (TeacherProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ألوان الجدول وأسماء الطلبة") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("لون أسماء الطلبة في السجل والبحث:", style = MaterialTheme.typography.labelLarge, color = primary)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StudentNameColorPresets.forEach { preset ->
                        val isSel = profile.studentNamesColorHex == preset.hex
                        FilterChip(
                            selected = isSel,
                            onClick = { onUpdateProfile(profile.copy(studentNamesColorHex = preset.hex)) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(if (preset.hex == 0L) MaterialTheme.colorScheme.onSurface else Color(preset.hex))
                                )
                            },
                            label = { Text(preset.titleAr) }
                        )
                    }
                }

                HorizontalDivider()
                Text("لون خلفية جدول الدرجات:", style = MaterialTheme.typography.labelLarge, color = primary)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TimetableBgColorPresets.forEach { preset ->
                        val isSel = profile.gradebookTableBgColorHex == preset.hex
                        FilterChip(
                            selected = isSel,
                            onClick = { onUpdateProfile(profile.copy(gradebookTableBgColorHex = preset.hex)) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(if (preset.hex == 0L) MaterialTheme.colorScheme.surface else Color(preset.hex))
                                )
                            },
                            label = { Text(preset.titleAr) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("حفظ وإغلاق") }
        },
        dismissButton = {
            TextButton(onClick = {
                onUpdateProfile(profile.copy(studentNamesColorHex = 0L, gradebookTableBgColorHex = 0L))
            }) { Text("استعادة الافتراضي") }
        }
    )
}

