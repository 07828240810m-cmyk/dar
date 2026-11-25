package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.theme.*
import com.example.utils.ExportManager
import com.example.utils.GradeCalculator

@Composable
fun StudentActionDialog(
    student: Student,
    profile: TeacherProfile,
    settings: GradebookSettings,
    absencesCount: Int,
    initialLedger: LedgerSubject = profile.effectiveActiveLedger(),
    activeLedgerId: String = profile.currentLedgerDef().id,
    safeEditUnlocked: Boolean = true,
    onRequestUnlockSafeEdit: () -> Unit = {},
    onToggleTransferred: (String) -> Unit = {},
    onUpdateBadges: (String, List<String>) -> Unit = { _, _ -> },
    onAddAvailableBadge: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (Student) -> Unit,
    onMoveSection: (GradeLevel, SectionLetter) -> Unit,
    onDelete: () -> Unit,
    onOpenQr: () -> Unit,
    onOpenCertificate: () -> Unit,
    onShareParentReport: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    var name by remember { mutableStateOf(student.fullName) }
    var birth by remember { mutableStateOf(student.birthDate) }
    var reg by remember { mutableStateOf(student.registryNumber) }
    var phone by remember { mutableStateOf(student.phoneNumber) }
    var notes by remember { mutableStateOf(student.notes) }
    var colorTag by remember { mutableStateOf(student.colorTag) }
    var behaviorStatus by remember { mutableStateOf(student.behaviorStatus) }
    var notebookStatus by remember { mutableStateOf(student.notebookStatus) }
    var isInactive by remember { mutableStateOf(student.isInactive) }
    var isTransferred by remember { mutableStateOf(student.isTransferred) }
    var dailyPrepDone by remember { mutableStateOf(student.dailyPrepDone) }
    var selectedBadges by remember { mutableStateOf(student.badges) }
    var customBadgeInput by remember { mutableStateOf("") }

    val currentLedgerDef = profile.effectiveLedgers().find { it.id == activeLedgerId }
        ?: profile.currentLedgerDef()
    val effectiveLedgerId = currentLedgerDef.id
    val h = currentLedgerDef.customHeaders ?: settings.headers
    val initialGrades = remember(student.id, effectiveLedgerId) {
        student.gradesForLedger(effectiveLedgerId)
    }

    var t1m1 by remember(effectiveLedgerId) { mutableStateOf(initialGrades.term1Month1?.toString() ?: "") }
    var t1m2 by remember(effectiveLedgerId) { mutableStateOf(initialGrades.term1Month2?.toString() ?: "") }
    var t1m3 by remember(effectiveLedgerId) { mutableStateOf(initialGrades.term1Month3?.toString() ?: "") }
    var t1d by remember(effectiveLedgerId) { mutableStateOf(initialGrades.term1Daily?.toString() ?: "") }
    var mid by remember(effectiveLedgerId) { mutableStateOf(initialGrades.midYearTotal?.toString() ?: "") }
    var midO by remember(effectiveLedgerId) { mutableStateOf(initialGrades.midYearOral?.toString() ?: "") }
    var midW by remember(effectiveLedgerId) { mutableStateOf(initialGrades.midYearWritten?.toString() ?: "") }
    var t2m1 by remember(effectiveLedgerId) { mutableStateOf(initialGrades.term2Month1?.toString() ?: "") }
    var t2m2 by remember(effectiveLedgerId) { mutableStateOf(initialGrades.term2Month2?.toString() ?: "") }
    var t2m3 by remember(effectiveLedgerId) { mutableStateOf(initialGrades.term2Month3?.toString() ?: "") }
    var t2d by remember(effectiveLedgerId) { mutableStateOf(initialGrades.term2Daily?.toString() ?: "") }
    var fin by remember(effectiveLedgerId) { mutableStateOf(initialGrades.finalExam?.toString() ?: "") }

    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isSafeGradeLocked = profile.safeGradeEditLockEnabled && !safeEditUnlocked

    fun buildUpdatedGrades(): StudentGrades = initialGrades.copy(
        term1Month1 = t1m1.toDoubleOrNull(),
        term1Month2 = t1m2.toDoubleOrNull(),
        term1Month3 = t1m3.toDoubleOrNull(),
        term1Daily = t1d.toDoubleOrNull(),
        midYearTotal = mid.toDoubleOrNull(),
        midYearOral = midO.toDoubleOrNull(),
        midYearWritten = midW.toDoubleOrNull(),
        term2Month1 = t2m1.toDoubleOrNull(),
        term2Month2 = t2m2.toDoubleOrNull(),
        term2Month3 = t2m3.toDoubleOrNull(),
        term2Daily = t2d.toDoubleOrNull(),
        finalExam = fin.toDoubleOrNull()
    )

    fun buildPreviewStudent(): Student {
        val base = student.copy(
            fullName = name.trim().ifBlank { student.fullName },
            birthDate = birth.trim(),
            registryNumber = reg.trim(),
            phoneNumber = phone.trim(),
            notes = notes.trim(),
            colorTag = colorTag,
            behaviorStatus = behaviorStatus,
            notebookStatus = notebookStatus,
            isInactive = isInactive,
            isTransferred = isTransferred,
            badges = selectedBadges.take(5),
            dailyPrepDone = dailyPrepDone
        )
        return if (isSafeGradeLocked) base else base.withGradesForLedger(effectiveLedgerId, buildUpdatedGrades())
    }

    val previewStudent = buildPreviewStudent()
    val genResult = GradeCalculator.computeForLedger(previewStudent, settings, effectiveLedgerId)
    val isExempt = GradeCalculator.isExemptEligible(previewStudent, genResult, settings)
    val r = settings.enableSmartRounding
    val primaryAccent = MaterialTheme.colorScheme.primary
    val mainSubName = currentLedgerDef.title.ifBlank { profile.subjectName.ifBlank { profile.subjectSpecialty.titleAr } }
    val tabAccent = primaryAccent

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(student.fullName, style = MaterialTheme.typography.titleLarge, color = tabAccent, modifier = Modifier.weight(1f, fill = false))
                    if (isExempt) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SuccessGreen.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, SuccessGreen)
                        ) {
                            Text(
                                "معفو",
                                style = MaterialTheme.typography.labelMedium,
                                color = SuccessGreen,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    "${student.gradeLevel.titleAr} - ${student.section.titleAr} • الغيابات: $absencesCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.textMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick Actions Row (Move, Share Text, QR, Certificate, Delete)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { showMoveDialog = true },
                        modifier = Modifier.weight(1f).testTag("move_student_btn"),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.SwapHoriz, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("نقل شعبة", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            val txt = ExportManager.buildStudentShareText(
                                student = previewStudent,
                                profile = profile,
                                settings = settings,
                                ledgerSubject = initialLedger,
                                ledgerId = effectiveLedgerId,
                                ledgerTitle = mainSubName
                            )
                            ExportManager.shareText(context, "درجات ${previewStudent.fullName}", txt)
                        },
                        modifier = Modifier.weight(1f).testTag("share_student_text_btn"),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("مشاركة", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            onSave(previewStudent)
                            onOpenQr()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.QrCode2, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("QR النتائج", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            onSave(previewStudent)
                            onOpenCertificate()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.WorkspacePremium, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("شهادة", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف الطالب", tint = DangerRed)
                    }
                }
                if (onShareParentReport != null) {
                    OutlinedButton(
                        onClick = {
                            onSave(previewStudent)
                            onShareParentReport()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("share_parent_report_btn"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Link, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("رابط ولي الأمر (3 أيام)", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Live Summary Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, Color(currentLedgerDef.headerColorHex).copy(alpha = 0.65f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("سجل $mainSubName:", style = MaterialTheme.typography.labelMedium, color = primaryAccent, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            MiniScore("ف1", GradeCalculator.formatScore(genResult.term1Average, r), primaryAccent)
                            MiniScore("نصف", GradeCalculator.formatScore(genResult.midYearEffective, r), primaryAccent)
                            MiniScore("ف2", GradeCalculator.formatScore(genResult.term2Average, r), primaryAccent)
                            MiniScore("السعي", GradeCalculator.formatScore(genResult.annualEffort, r), primaryAccent)
                            MiniScore("النهائية", GradeCalculator.formatScore(genResult.finalGrade, r), primaryAccent)
                        }
                    }
                }

                if (isSafeGradeLocked) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = WarningAmber.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, WarningAmber),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("الوضع الآمن للدرجات مفعّل", fontWeight = FontWeight.Bold, color = WarningAmber, style = MaterialTheme.typography.labelLarge)
                                Text("افتح القفل لتعديل الدرجات.", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = onRequestUnlockSafeEdit) {
                                Text("فك القفل")
                            }
                        }
                    }
                }

                Text("درجات $mainSubName - الفصل الأول (${settings.dailyCalcMode.titleAr})", style = MaterialTheme.typography.titleSmall, color = primaryAccent)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GradeInput(t1m1, { if (!isSafeGradeLocked) t1m1 = it else onRequestUnlockSafeEdit() }, h.t1M1Header, Modifier.weight(1f), "t1m1_input", enabled = !isSafeGradeLocked)
                    if (settings.monthsPerTerm >= 2) GradeInput(t1m2, { if (!isSafeGradeLocked) t1m2 = it else onRequestUnlockSafeEdit() }, h.t1M2Header, Modifier.weight(1f), "t1m2_input", enabled = !isSafeGradeLocked)
                    if (settings.monthsPerTerm >= 3) GradeInput(t1m3, { if (!isSafeGradeLocked) t1m3 = it else onRequestUnlockSafeEdit() }, h.t1M3Header, Modifier.weight(1f), enabled = !isSafeGradeLocked)
                    GradeInput(t1d, { if (!isSafeGradeLocked) t1d = it else onRequestUnlockSafeEdit() }, h.t1DailyHeader, Modifier.weight(1f), enabled = !isSafeGradeLocked)
                }

                Text("امتحان نصف السنة ($mainSubName)", style = MaterialTheme.typography.titleSmall, color = primaryAccent)
                if (settings.splitMidYearOralWritten) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradeInput(midO, { if (!isSafeGradeLocked) midO = it else onRequestUnlockSafeEdit() }, "الشفوي", Modifier.weight(1f), enabled = !isSafeGradeLocked)
                        GradeInput(midW, { if (!isSafeGradeLocked) midW = it else onRequestUnlockSafeEdit() }, "التحريري", Modifier.weight(1f), enabled = !isSafeGradeLocked)
                    }
                } else {
                    GradeInput(mid, { if (!isSafeGradeLocked) mid = it else onRequestUnlockSafeEdit() }, h.midYearHeader, Modifier.fillMaxWidth(), enabled = !isSafeGradeLocked)
                }

                Text("درجات $mainSubName - الفصل الثاني", style = MaterialTheme.typography.titleSmall, color = primaryAccent)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GradeInput(t2m1, { if (!isSafeGradeLocked) t2m1 = it else onRequestUnlockSafeEdit() }, h.t2M1Header, Modifier.weight(1f), enabled = !isSafeGradeLocked)
                    if (settings.monthsPerTerm >= 2) GradeInput(t2m2, { if (!isSafeGradeLocked) t2m2 = it else onRequestUnlockSafeEdit() }, h.t2M2Header, Modifier.weight(1f), enabled = !isSafeGradeLocked)
                    if (settings.monthsPerTerm >= 3) GradeInput(t2m3, { if (!isSafeGradeLocked) t2m3 = it else onRequestUnlockSafeEdit() }, h.t2M3Header, Modifier.weight(1f), enabled = !isSafeGradeLocked)
                    GradeInput(t2d, { if (!isSafeGradeLocked) t2d = it else onRequestUnlockSafeEdit() }, h.t2DailyHeader, Modifier.weight(1f), enabled = !isSafeGradeLocked)
                }

                GradeInput(fin, { if (!isSafeGradeLocked) fin = it else onRequestUnlockSafeEdit() }, h.finalExamHeader, Modifier.fillMaxWidth(), enabled = !isSafeGradeLocked)

                HorizontalDivider()
                Text("أوسمة ووسوم الطالب المصغرة (${selectedBadges.size}/5):", style = MaterialTheme.typography.titleSmall, color = primaryAccent)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    profile.availableBadges.forEach { badge ->
                        val active = badge in selectedBadges
                        FilterChip(
                            selected = active,
                            onClick = {
                                val next = if (active) {
                                    selectedBadges - badge
                                } else if (selectedBadges.size < 5) {
                                    selectedBadges + badge
                                } else selectedBadges
                                selectedBadges = next
                                onUpdateBadges(student.id, next)
                            },
                            label = { Text(badge, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = customBadgeInput,
                        onValueChange = { customBadgeInput = it },
                        label = { Text("إضافة وسم جديد (#مبدع)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = {
                            val clean = customBadgeInput.trim()
                            if (clean.isNotBlank()) {
                                val norm = if (clean.startsWith("#")) clean else "#$clean"
                                onAddAvailableBadge(norm)
                                if (selectedBadges.size < 5 && norm !in selectedBadges) {
                                    val next = selectedBadges + norm
                                    selectedBadges = next
                                    onUpdateBadges(student.id, next)
                                }
                                customBadgeInput = ""
                            }
                        }
                    ) { Text("إضافة") }
                }

                HorizontalDivider()
                Text("تلوين الطالب في السجل:", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudentColorTag.entries.forEach { tag ->
                        val selected = tag == colorTag
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (tag == StudentColorTag.NONE) MaterialTheme.colorScheme.surface else Color(tag.colorHex))
                                .clickable { colorTag = tag },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Text("السلوك والدفتر والتحضير والنقل:", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BehaviorStatus.entries.forEach { bs ->
                        FilterChip(
                            selected = bs == behaviorStatus,
                            onClick = { behaviorStatus = bs },
                            label = { Text(bs.titleAr, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("تصنيف الطالب كـ (خامل / قليل التفاعل)")
                    Switch(checked = isInactive, onCheckedChange = { isInactive = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("التزام بالتحضير اليومي والدفتر")
                    Switch(checked = dailyPrepDone, onCheckedChange = { dailyPrepDone = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("طالب منقول إلى مدرسة أخرى", fontWeight = FontWeight.SemiBold)
                        Text("يُخفى من القائمة الرئيسية دون حذف درجاته", style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                    }
                    Switch(
                        checked = isTransferred,
                        onCheckedChange = {
                            isTransferred = it
                            onToggleTransferred(student.id)
                        }
                    )
                }

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم الرباعي") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = birth, onValueChange = { birth = it }, label = { Text("المواليد") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = reg, onValueChange = { reg = it }, label = { Text("القيد") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("رقم الهاتف") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات المدرس") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(buildPreviewStudent())
                    onDismiss()
                },
                modifier = Modifier.testTag("save_student_btn")
            ) { Text("حفظ التعديلات") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )

    if (showMoveDialog) {
        var targetGrade by remember { mutableStateOf(student.gradeLevel) }
        var targetSec by remember { mutableStateOf(student.section) }
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("نقل الطالب إلى صف/شعبة أخرى") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختر الصف الجديد:")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GradeLevel.entries.forEach { g ->
                            FilterChip(selected = g == targetGrade, onClick = { targetGrade = g }, label = { Text(g.shortAr) })
                        }
                    }
                    Text("اختر الشعبة الجديدة:")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLetter.entries.forEach { sec ->
                            FilterChip(selected = sec == targetSec, onClick = { targetSec = sec }, label = { Text(sec.id) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onMoveSection(targetGrade, targetSec)
                    showMoveDialog = false
                    onDismiss()
                }) { Text("تأكيد النقل") }
            },
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف الطالب") },
            text = { Text("هل أنت متأكد من حذف الطالب (${student.fullName}) من السجل؟") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun MiniScore(label: String, value: String, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
        Text(value, style = MaterialTheme.typography.titleSmall, color = accentColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GradeInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = { txt ->
            if (txt.isEmpty() || txt.matches(Regex("^\\d{0,3}(\\.\\d{0,2})?$"))) {
                onValueChange(txt)
            }
        },
        readOnly = !enabled,
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = if (testTag != null) modifier.testTag(testTag) else modifier
    )
}
