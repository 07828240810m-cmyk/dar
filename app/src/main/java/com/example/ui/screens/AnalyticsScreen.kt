package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.utils.ExportManager
import com.example.utils.GradeCalculator

@Composable
fun AnalyticsScreen(
    state: AppState,
    selectedGrade: GradeLevel,
    selectedSection: SectionLetter,
    activeLedger: LedgerSubject,
    activeLedgerId: String = state.profile.currentLedgerDef().id,
    onSelectLedger: (LedgerSubject) -> Unit,
    onSelectLedgerId: (String) -> Unit = { id ->
        onSelectLedger(if (id == "math") LedgerSubject.MATH else LedgerSubject.GENERAL)
    },
    onSelectClass: (GradeLevel, SectionLetter) -> Unit,
    onUpdateProfile: (TeacherProfile) -> Unit = {},
    onExportExcelToFolder: (GradeLevel?, SectionLetter?, String) -> ExportManager.FolderExportResult = { _, _, _ -> ExportManager.FolderExportResult(false, "", null, "غير متوفر") },
    onExportWordToFolder: (GradeLevel?, SectionLetter?, String) -> ExportManager.FolderExportResult = { _, _, _ -> ExportManager.FolderExportResult(false, "", null, "غير متوفر") },
    onOpenGradeComparison: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ext = LocalExtendedColors.current
    var wholeSchoolScope by remember { mutableStateOf(false) }
    var lastExportFeedback by remember { mutableStateOf<ExportManager.FolderExportResult?>(null) }

    val ledgers = remember(state.profile.customLedgers, state.profile.subjectName, state.profile.subjectSpecialty) {
        state.profile.effectiveLedgers()
    }
    val currentLedgerDef = remember(ledgers, activeLedgerId) {
        ledgers.find { it.id == activeLedgerId } ?: ledgers.first()
    }
    val ledgerId = currentLedgerDef.id

    // Comparison State
    var compareGradeB by remember { mutableStateOf(selectedGrade) }
    var compareSectionB by remember { mutableStateOf(if (selectedSection == SectionLetter.A) SectionLetter.B else SectionLetter.A) }
    var studentA by remember { mutableStateOf<Student?>(null) }
    var studentB by remember { mutableStateOf<Student?>(null) }

    // Interactive Timeline State
    var timelineWholeSection by remember { mutableStateOf(true) }
    var timelineSelectedStudent by remember { mutableStateOf<Student?>(null) }
    var timelineSourceMode by remember { mutableStateOf(com.example.utils.TimelineSourceMode.BOTH) }
    var pickTimelineStudentOpen by remember { mutableStateOf(false) }

    val activeStudents = remember(state.students, selectedGrade, selectedSection, wholeSchoolScope) {
        if (wholeSchoolScope) state.students
        else state.students.filter { it.gradeLevel == selectedGrade && it.section == selectedSection }
    }

    val stats = remember(activeStudents, state.gradebookSettings, activeLedger, ledgerId) {
        GradeCalculator.computeSectionStats(activeStudents, state.gradebookSettings, activeLedger, ledgerId)
    }

    val absenceCounts = remember(state.absences) {
        state.absences.groupingBy { it.studentId }.eachCount()
    }

    val comprehensive = remember(activeStudents, state.gradebookSettings, absenceCounts, ledgerId) {
        GradeCalculator.computeComprehensiveAnalytics(activeStudents, state.gradebookSettings, absenceCounts, ledgerId)
    }
    val grade3Overall = remember(state.students, state.gradebookSettings, absenceCounts, ledgerId) {
        GradeCalculator.computeComprehensiveAnalytics(
            state.students.filter { it.gradeLevel == GradeLevel.THIRD },
            state.gradebookSettings,
            absenceCounts,
            ledgerId
        )
    }

    val scopeTitle = if (wholeSchoolScope) "جميع الصفوف والشعب" else "${selectedGrade.titleAr} - ${selectedSection.titleAr}"
    val isGrade3Scope = !wholeSchoolScope && selectedGrade == GradeLevel.THIRD

    // Section Comparison Bars for current Grade
    val primaryAccent = MaterialTheme.colorScheme.primary
    val gradeSectionBars = remember(state.students, selectedGrade, state.gradebookSettings, activeLedger, ledgerId, primaryAccent) {
        SectionLetter.entries.take(4).mapIndexed { idx, sec ->
            val list = state.students.filter { it.gradeLevel == selectedGrade && it.section == sec }
            val st = GradeCalculator.computeSectionStats(list, state.gradebookSettings, activeLedger, ledgerId)
            val barColor = when (idx) {
                0 -> primaryAccent
                1 -> InfoBlue
                2 -> SuccessGreen
                else -> WarningAmber
            }
            BarChartEntry("شعبة ${sec.id}", st.passRatePercent, barColor)
        }
    }

    // Section A vs Section B stats
    val sectionAStats = remember(state.students, selectedGrade, selectedSection, state.gradebookSettings, activeLedger, ledgerId) {
        val list = state.students.filter { it.gradeLevel == selectedGrade && it.section == selectedSection }
        GradeCalculator.computeSectionStats(list, state.gradebookSettings, activeLedger, ledgerId)
    }
    val sectionBStats = remember(state.students, compareGradeB, compareSectionB, state.gradebookSettings, activeLedger, ledgerId) {
        val list = state.students.filter { it.gradeLevel == compareGradeB && it.section == compareSectionB }
        GradeCalculator.computeSectionStats(list, state.gradebookSettings, activeLedger, ledgerId)
    }

    // Interactive Timeline Points (Daily / Monthly / Both, per Section or per Student)
    val timelineStudents = remember(timelineWholeSection, timelineSelectedStudent, activeStudents) {
        if (timelineWholeSection || timelineSelectedStudent == null) activeStudents else listOf(timelineSelectedStudent!!)
    }
    val timelinePoints = remember(timelineStudents, state.gradebookSettings, ledgerId, timelineSourceMode) {
        GradeCalculator.buildProgressTimeline(timelineStudents, state.gradebookSettings, ledgerId, timelineSourceMode)
            .map { it.label to (it.score?.toFloat() ?: 0f) }
    }

    val rnd = state.gradebookSettings.enableSmartRounding

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeaderWithInfo(
                title = "التحليل البياني والإنفوجرافيك",
                infoTitle = "الرسوم البيانية ومحلل الصف الشامل",
                infoDescription = "يعرض نسب النجاح، وقائمة طلبة الإعفاء (90+)، وتحليل جاهزية الثالث المتوسط، والتتبع الزمني لدرجات اليومي والشهري، والحالات الحرجة والمنقولين، مع أزرار إظهار وإخفاء لكل قسم."
            )
            Spacer(Modifier.height(8.dp))

            if (ledgers.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ledgers.forEach { led ->
                        FilterChip(
                            selected = led.id == ledgerId,
                            onClick = { onSelectLedgerId(led.id) },
                            label = { Text(led.title) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !wholeSchoolScope,
                    onClick = { wholeSchoolScope = false },
                    label = { Text("شعبة محددة") }
                )
                FilterChip(
                    selected = wholeSchoolScope,
                    onClick = { wholeSchoolScope = true },
                    label = { Text("جميع الصفوف كاملة") }
                )
            }
            if (!wholeSchoolScope) {
                Spacer(Modifier.height(8.dp))
                ClassSelectorBar(selectedGrade, selectedSection, onSelectClass)
            }

            Spacer(Modifier.height(8.dp))
            Text("الأقسام المعروضة في التحليل:", style = MaterialTheme.typography.labelMedium, color = ext.textMuted)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = state.profile.analyticsShowExemptions,
                    onClick = { onUpdateProfile(state.profile.copy(analyticsShowExemptions = !state.profile.analyticsShowExemptions)) },
                    label = { Text(if (isGrade3Scope) "المؤهلون للوزاري (50+)" else "الإعفاء 90+") }
                )
                FilterChip(
                    selected = state.profile.analyticsShowGrade3,
                    onClick = { onUpdateProfile(state.profile.copy(analyticsShowGrade3 = !state.profile.analyticsShowGrade3)) },
                    label = { Text("الثالث المتوسط") }
                )
                FilterChip(
                    selected = state.profile.analyticsShowPassFail,
                    onClick = { onUpdateProfile(state.profile.copy(analyticsShowPassFail = !state.profile.analyticsShowPassFail)) },
                    label = { Text("نسب النجاح والمسار") }
                )
                FilterChip(
                    selected = state.profile.analyticsShowCriticalCases,
                    onClick = { onUpdateProfile(state.profile.copy(analyticsShowCriticalCases = !state.profile.analyticsShowCriticalCases)) },
                    label = { Text("الحالات الحرجة") }
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        val res = onExportExcelToFolder(
                            if (wholeSchoolScope) null else selectedGrade,
                            if (wholeSchoolScope) null else selectedSection,
                            ledgerId
                        )
                        lastExportFeedback = res
                        android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("حفظ تحليل Excel (رفيق المدرس)")
                }
                FilledTonalButton(
                    onClick = {
                        val res = onExportWordToFolder(
                            if (wholeSchoolScope) null else selectedGrade,
                            if (wholeSchoolScope) null else selectedSection,
                            ledgerId
                        )
                        lastExportFeedback = res
                        android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Filled.Article, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("حفظ تحليل Word (رفيق المدرس)")
                }
                OutlinedButton(
                    onClick = onOpenGradeComparison,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("مقارنة مع مادة أخرى بكود")
                }
            }

            lastExportFeedback?.let { exp ->
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
                        Text(exp.message, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { lastExportFeedback = null }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "إغلاق", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            // 2 Gradient KPI Summary Cards inspired by Design 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Transparent,
                    shadowElevation = if (ext.neonGlowEnabled) 8.dp else 4.dp,
                    modifier = Modifier
                        .weight(1f)
                        .neonCardGlow(primaryAccent, ext.neonGlowEnabled, 18.dp, alphaFactor = 0.35f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(primaryAccent, primaryAccent.copy(alpha = 0.72f))
                                )
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${stats.passRatePercent.toInt()}%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "نسبة النجاح (${stats.passingCount}/${stats.gradedStudents})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Transparent,
                    shadowElevation = if (ext.neonGlowEnabled) 8.dp else 4.dp,
                    modifier = Modifier
                        .weight(1f)
                        .neonCardGlow(MaterialTheme.colorScheme.secondary, ext.neonGlowEnabled, 18.dp, alphaFactor = 0.35f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f))
                                )
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isGrade3Scope) "${comprehensive.ministerialQualifiedStudents.size}" else "${comprehensive.exemptStudents.size}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                        Text(
                            text = if (isGrade3Scope) "المؤهلون للوزاري (50+)" else "المرشحون للإعفاء (${state.gradebookSettings.exemptionThreshold}+)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        // Interactive Timeline Card (Always accessible for student/section progression)
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ext.cardElevated,
                border = BorderStroke(
                    if (ext.neonGlowEnabled) 1.3.dp else 1.dp,
                    if (ext.neonGlowEnabled) primaryAccent.copy(alpha = 0.45f) else ext.borderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .neonCardGlow(primaryAccent, ext.neonGlowEnabled, 18.dp, alphaFactor = 0.25f)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "مخطط التتبع الزمني (اليومي والشهري)",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryAccent
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = timelineWholeSection,
                            onClick = { timelineWholeSection = true },
                            label = { Text(if (wholeSchoolScope) "كامل الصفوف" else "شعبة كاملة") }
                        )
                        FilterChip(
                            selected = !timelineWholeSection,
                            onClick = {
                                timelineWholeSection = false
                                if (timelineSelectedStudent == null && activeStudents.isNotEmpty()) {
                                    timelineSelectedStudent = activeStudents.first()
                                }
                                pickTimelineStudentOpen = true
                            },
                            label = {
                                Text(
                                    timelineSelectedStudent?.fullName?.substringBefore(" ")
                                        ?.let { "طالب: $it" } ?: "اختر طالباً"
                                )
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        com.example.utils.TimelineSourceMode.entries.forEach { mode ->
                            FilterChip(
                                selected = timelineSourceMode == mode,
                                onClick = { timelineSourceMode = mode },
                                label = { Text(mode.titleAr) }
                            )
                        }
                    }
                    if (timelinePoints.isEmpty()) {
                        Text("لا توجد درجات كافية لعرض التتبع الزمني حالياً.", style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                    } else {
                        ProgressionSparklineChart(
                            points = timelinePoints,
                            lineColor = primaryAccent
                        )
                    }
                }
            }
            if (pickTimelineStudentOpen) {
                StudentPickDialog(activeStudents, onDismiss = { pickTimelineStudentOpen = false }) {
                    timelineSelectedStudent = it
                    timelineWholeSection = false
                    pickTimelineStudentOpen = false
                }
            }
        }

        // Exemptions Card (1st & 2nd Intermediate >= 90) OR Ministerial Qualification Card (3rd Intermediate >= 50)
        if (state.profile.analyticsShowExemptions) {
            if (isGrade3Scope) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.3.dp, SuccessGreen.copy(alpha = if (ext.neonGlowEnabled) 0.72f else 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .neonCardGlow(SuccessGreen, ext.neonGlowEnabled, 18.dp, alphaFactor = 0.28f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Verified, null, tint = SuccessGreen)
                                    Text(
                                        "المؤهلون لدخول الوزاري (السعي السنوي 50+)",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SuccessGreen
                                    )
                                }
                                Surface(shape = RoundedCornerShape(8.dp), color = SuccessGreen.copy(alpha = 0.16f)) {
                                    Text(
                                        "${comprehensive.ministerialQualifiedStudents.size} مؤهل",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = SuccessGreen,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (comprehensive.ministerialQualifiedStudents.isEmpty()) {
                                Text(
                                    "لا يوجد طلبة مؤهلون لدخول الامتحان الوزاري (سعي 50 فأعلى) في هذا النطاق بعد.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textMuted
                                )
                            } else {
                                comprehensive.ministerialQualifiedStudents.take(20).forEach { (st, effort) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${st.fullName} (${st.section.id})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(shape = RoundedCornerShape(6.dp), color = SuccessGreen.copy(alpha = 0.16f)) {
                                            Text(
                                                "مؤهل وزاري • سعي ${GradeCalculator.formatScore(effort, rnd)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = SuccessGreen,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.3.dp, SuccessGreen.copy(alpha = if (ext.neonGlowEnabled) 0.72f else 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .neonCardGlow(SuccessGreen, ext.neonGlowEnabled, 18.dp, alphaFactor = 0.28f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Verified, null, tint = SuccessGreen)
                                    Text(
                                        "قائمة الإعفاء (${state.gradebookSettings.exemptionThreshold}+ للأول والثاني)",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SuccessGreen
                                    )
                                }
                                Surface(shape = RoundedCornerShape(8.dp), color = SuccessGreen.copy(alpha = 0.16f)) {
                                    Text(
                                        "${comprehensive.exemptStudents.size} معفو",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = SuccessGreen,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (comprehensive.exemptStudents.isEmpty()) {
                                Text(
                                    "لا يوجد طلبة مستحقون للإعفاء في النطاق الحالي حتى الآن.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textMuted
                                )
                            } else {
                                comprehensive.exemptStudents.take(15).forEach { (st, score) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${st.fullName} (${st.gradeLevel.shortAr} ${st.section.id})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(shape = RoundedCornerShape(6.dp), color = SuccessGreen.copy(alpha = 0.16f)) {
                                            Text(
                                                "معفو • ${GradeCalculator.formatScore(score, rnd)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = SuccessGreen,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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

        // Grade 3 Ministerial Readiness Card
        if (state.profile.analyticsShowGrade3) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.3.dp, InfoBlue.copy(alpha = if (ext.neonGlowEnabled) 0.72f else 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .neonCardGlow(InfoBlue, ext.neonGlowEnabled, 18.dp, alphaFactor = 0.28f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.School, null, tint = InfoBlue)
                            Text(
                                "تحليل الثالث المتوسط (جاهزية الامتحان الوزاري)",
                                style = MaterialTheme.typography.titleMedium,
                                color = InfoBlue
                            )
                        }
                        if (grade3Overall.grade3Total == 0) {
                            Text("لا يوجد طلبة مسجلون في الصف الثالث المتوسط بعد.", style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                InfoMetric("العدد الكلي", "${grade3Overall.grade3Total}")
                                InfoMetric("المؤهلون/الناجحون", "${grade3Overall.grade3Passing}")
                                InfoMetric("نسبة النجاح", "${grade3Overall.grade3PassRate.toInt()}%")
                                InfoMetric("المعدل", "%.1f".format(grade3Overall.grade3Average))
                            }
                            LinearProgressIndicator(
                                progress = { (grade3Overall.grade3PassRate / 100f).coerceIn(0f, 1f) },
                                color = InfoBlue,
                                modifier = Modifier.fillMaxWidth().height(8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Critical Cases & Transferred Students Card
        if (state.profile.analyticsShowCriticalCases) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.2.dp, DangerRed.copy(alpha = if (ext.neonGlowEnabled) 0.72f else 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .neonCardGlow(DangerRed, ext.neonGlowEnabled, 18.dp, alphaFactor = 0.25f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.WarningAmber, null, tint = DangerRed)
                                Text("الحالات الحرجة والمنقولون", style = MaterialTheme.typography.titleMedium, color = DangerRed)
                            }
                            Text(
                                "المنقولون: ${comprehensive.transferredCount}",
                                style = MaterialTheme.typography.labelLarge,
                                color = ext.textMuted
                            )
                        }
                        if (comprehensive.criticalCases.isEmpty()) {
                            Text("لا توجد حالات حرجة في هذا النطاق 🎉", style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
                        } else {
                            comprehensive.criticalCases.take(12).forEach { (st, reason) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        st.fullName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        reason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DangerRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.profile.analyticsShowPassFail) {
            // Infographic Pass Rate Card
            item {
                GradientBannerCard {
                    Text(
                        text = "إنفوجرافيك الأداء (${currentLedgerDef.title}): $scopeTitle",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    DonutPassRateChart(
                        passRate = stats.passRatePercent,
                        passingCount = stats.passingCount,
                        failingCount = stats.failingCount
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        InfoMetric("المعدل العام", "%.1f".format(stats.averageScore))
                        InfoMetric("ذوو السلوك السيء", "${stats.badBehaviorCount}")
                        InfoMetric("الخاملون", "${stats.inactiveCount}")
                    }
                }
            }

            // Section Pass Rates Bar Chart
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.dp, ext.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "مخطط نسب النجاح لشعب ${selectedGrade.titleAr}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        VerticalBarComparisonChart(entries = gradeSectionBars)
                    }
                }
            }

            // Trajectory
            item {
                TrendTrajectoryChart(
                    improved = stats.improvedCount,
                    stable = stats.stableCount,
                    declined = stats.declinedCount
                )
            }

            // Section vs Section Comparison
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.dp, ext.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "مقارنة شعبة مع أخرى",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("قارن (${selectedGrade.shortAr} ${selectedSection.id}) مع:", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionLetter.entries.take(4).forEach { sec ->
                                FilterChip(
                                    selected = compareSectionB == sec && compareGradeB == selectedGrade,
                                    onClick = {
                                        compareGradeB = selectedGrade
                                        compareSectionB = sec
                                    },
                                    label = { Text("شعبة ${sec.id}") }
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            CompareBox(
                                title = "${selectedGrade.shortAr} (${selectedSection.id})",
                                passRate = sectionAStats.passRatePercent,
                                avg = sectionAStats.averageScore,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(10.dp))
                            CompareBox(
                                title = "${compareGradeB.shortAr} (${compareSectionB.id})",
                                passRate = sectionBStats.passRatePercent,
                                avg = sectionBStats.averageScore,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Student vs Student Comparison
            item {
                val pool = remember(state.students, selectedGrade, selectedSection) {
                    state.students.filter { it.gradeLevel == selectedGrade && it.section == selectedSection }
                }
                var pickAOpen by remember { mutableStateOf(false) }
                var pickBOpen by remember { mutableStateOf(false) }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.dp, ext.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("مقارنة طالب بطالب (ش1، ش2، ف1، ف2)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { pickAOpen = true }, modifier = Modifier.weight(1f)) {
                                Text(studentA?.fullName?.substringBefore(" ") ?: "اختر الطالب الأول", maxLines = 1)
                            }
                            OutlinedButton(onClick = { pickBOpen = true }, modifier = Modifier.weight(1f)) {
                                Text(studentB?.fullName?.substringBefore(" ") ?: "اختر الطالب الثاني", maxLines = 1)
                            }
                        }
                        if (studentA != null && studentB != null) {
                            val rA = GradeCalculator.computeForLedger(studentA!!, state.gradebookSettings, ledgerId)
                            val rB = GradeCalculator.computeForLedger(studentB!!, state.gradebookSettings, ledgerId)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(studentA!!.fullName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("ش1: ${GradeCalculator.formatScore(rA.term1Month1Effective, rnd)} | ش2: ${GradeCalculator.formatScore(rA.term1Month2Effective, rnd)}")
                                    Text("ف1: ${GradeCalculator.formatScore(rA.term1Average, rnd)} | ف2: ${GradeCalculator.formatScore(rA.term2Average, rnd)}")
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(studentB!!.fullName, fontWeight = FontWeight.Bold, color = InfoBlue)
                                    Text("ش1: ${GradeCalculator.formatScore(rB.term1Month1Effective, rnd)} | ش2: ${GradeCalculator.formatScore(rB.term1Month2Effective, rnd)}")
                                    Text("ف1: ${GradeCalculator.formatScore(rB.term1Average, rnd)} | ف2: ${GradeCalculator.formatScore(rB.term2Average, rnd)}")
                                }
                            }
                        }
                    }
                }

                if (pickAOpen) {
                    StudentPickDialog(pool, onDismiss = { pickAOpen = false }) { studentA = it; pickAOpen = false }
                }
                if (pickBOpen) {
                    StudentPickDialog(pool, onDismiss = { pickBOpen = false }) { studentB = it; pickBOpen = false }
                }
            }
        }
    }
}

@Composable
private fun InfoMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalExtendedColors.current.textMuted)
    }
}

@Composable
private fun CompareBox(title: String, passRate: Float, avg: Double, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text("النجاح: ${passRate.toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text("المعدل: ${"%.1f".format(avg)}", style = MaterialTheme.typography.bodySmall, color = LocalExtendedColors.current.textMuted)
        }
    }
}

@Composable
private fun StudentPickDialog(
    students: List<Student>,
    onDismiss: () -> Unit,
    onPick: (Student) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر طالباً") },
        text = {
            LazyColumn(modifier = Modifier.height(260.dp)) {
                items(students.size) { idx ->
                    val st = students[idx]
                    TextButton(
                        onClick = { onPick(st) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${st.orderNumber}. ${st.fullName}", modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}
