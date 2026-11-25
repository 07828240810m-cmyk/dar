package com.example.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.model.*
import com.example.ui.components.neonCardGlow
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LocalExtendedColors
import com.example.utils.ExamGeneratorHelper
import com.example.utils.ExportManager
import java.io.File

internal data class QbPaletteColors(
    val background: Color,
    val surface: Color,
    val elevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val neonGlow: Boolean
)

@Composable
internal fun qbColors(isDarkMidnight: Boolean): QbPaletteColors {
    val scheme = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current
    val useThemeDirectly = ext.isDark == isDarkMidnight
    return if (useThemeDirectly) {
        QbPaletteColors(
            background = scheme.background,
            surface = scheme.surface,
            elevated = ext.cardElevated,
            border = ext.borderSubtle,
            textPrimary = scheme.onSurface,
            textMuted = ext.textMuted,
            accent = scheme.primary,
            onAccent = scheme.onPrimary,
            neonGlow = ext.neonGlowEnabled
        )
    } else if (isDarkMidnight) {
        QbPaletteColors(
            background = Color(0xFF061124),
            surface = Color(0xFF0C1A32),
            elevated = Color(0xFF122342),
            border = scheme.primary.copy(alpha = 0.35f),
            textPrimary = Color(0xFFF3F7FC),
            textMuted = Color(0xFF9AB0CC),
            accent = scheme.primary,
            onAccent = scheme.onPrimary,
            neonGlow = ext.neonGlowEnabled
        )
    } else {
        QbPaletteColors(
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFF5F8FC),
            elevated = Color(0xFFFFFFFF),
            border = scheme.primary.copy(alpha = 0.25f),
            textPrimary = Color(0xFF0B1422),
            textMuted = Color(0xFF52667E),
            accent = scheme.primary,
            onAccent = scheme.onPrimary,
            neonGlow = ext.neonGlowEnabled
        )
    }
}

internal fun launchBiometricPrompt(
    context: Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    title: String = "فتح بنك الأسئلة السري",
    subtitle: String = "ضع إصبعك على مستشعر البصمة للدخول"
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        onError("البصمة غير مدعومة على هذا الإصدار، استخدم كلمة السر.")
        return
    }
    try {
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButton("استخدام كلمة السر", executor) { _, _ -> }
            .build()

        prompt.authenticate(
            CancellationSignal(),
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                    ) {
                        onError(errString?.toString() ?: "تعذر التحقق بالبصمة")
                    }
                }

                override fun onAuthenticationFailed() {
                    onError("لم تتطابق البصمة، حاول مجدداً.")
                }
            }
        )
    } catch (_: Exception) {
        onError("لا يتوفر مستشعر بصمة على هذا الجهاز.")
    }
}

@Composable
internal fun QuestionBankChaptersTab(
    chapters: List<QuestionChapter>,
    questions: List<BankQuestion>,
    colors: QbPaletteColors,
    onAddChapter: (String, Int) -> Boolean,
    onUpdateChapter: (QuestionChapter) -> Unit,
    onDeleteChapter: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingChapter by remember { mutableStateOf<QuestionChapter?>(null) }
    val totalWeight = remember(chapters) { chapters.sumOf { it.gradeWeight } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.elevated,
                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .neonCardGlow(colors.accent, colors.neonGlow, 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "الفصول الدراسية (${chapters.size} / 10 فصول)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "مجموع توزيع الدرجات: $totalWeight درجة (يُستخدم في التوليد التلقائي للأسئلة)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (totalWeight == 100) colors.accent else colors.textMuted
                        )
                    }
                    Button(
                        onClick = { showAddDialog = true },
                        enabled = chapters.size < 10,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        modifier = Modifier.testTag("qb_add_chapter_btn")
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة فصل")
                    }
                }
            }
        }

        items(chapters, key = { it.id }) { ch ->
            val qCount = questions.count { it.chapterId == ch.id }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .neonCardGlow(colors.accent, colors.neonGlow, 16.dp, alphaFactor = 0.18f)
                    .clickable { editingChapter = ch }
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${ch.chapterNumber}", color = colors.accent, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text(ch.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                Text("$qCount سؤال محفوظ", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.accent.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "حصة: ${ch.gradeWeight} درجة",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.accent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Quick Grade Weight Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("تعديل حصة الفصل:", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = {
                                    onUpdateChapter(ch.copy(gradeWeight = (ch.gradeWeight - 5).coerceAtLeast(0)))
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text("-5", color = colors.textPrimary) }
                            OutlinedButton(
                                onClick = {
                                    onUpdateChapter(ch.copy(gradeWeight = (ch.gradeWeight + 5).coerceAtMost(100)))
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text("+5", color = colors.accent) }
                            IconButton(onClick = { editingChapter = ch }) {
                                Icon(Icons.Filled.Edit, contentDescription = "تعديل", tint = colors.textMuted)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("الفصل ${chapters.size + 1}") }
        var weightTxt by remember { mutableStateOf("20") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("إضافة فصل جديد (بحد أقصى 10 فصول)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("اسم الفصل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = weightTxt,
                        onValueChange = { weightTxt = it.filter(Char::isDigit) },
                        label = { Text("حصة الفصل من الدرجة (مثال: 25)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val w = weightTxt.toIntOrNull()?.coerceIn(0, 100) ?: 20
                    onAddChapter(title, w)
                    showAddDialog = false
                }) { Text("إضافة") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("إلغاء") } }
        )
    }

    editingChapter?.let { ch ->
        var title by remember(ch.id) { mutableStateOf(ch.title) }
        var weightTxt by remember(ch.id) { mutableStateOf(ch.gradeWeight.toString()) }
        AlertDialog(
            onDismissRequest = { editingChapter = null },
            title = { Text("تعديل ${ch.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان الفصل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = weightTxt,
                        onValueChange = { weightTxt = it.filter(Char::isDigit) },
                        label = { Text("الدرجة المخصصة للفصل") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateChapter(
                        ch.copy(
                            title = title.trim().ifBlank { ch.title },
                            gradeWeight = weightTxt.toIntOrNull()?.coerceIn(0, 100) ?: ch.gradeWeight
                        )
                    )
                    editingChapter = null
                }) { Text("حفظ") }
            },
            dismissButton = {
                Row {
                    if (chapters.size > 1) {
                        TextButton(onClick = {
                            onDeleteChapter(ch.id)
                            editingChapter = null
                        }) { Text("حذف الفصل", color = DangerRed) }
                    }
                    TextButton(onClick = { editingChapter = null }) { Text("إلغاء") }
                }
            }
        )
    }
}

@Composable
internal fun QuestionBankExamGeneratorTab(
    state: AppState,
    colors: QbPaletteColors,
    getQuestionImageFile: (String?) -> File?
) {
    val context = LocalContext.current
    var examTitle by remember { mutableStateOf("أسئلة امتحان نصف السنة / نهاية الكورس") }
    var gradeTitle by remember { mutableStateOf(state.profile.preferredGrade.titleAr) }
    var examDuration by remember { mutableStateOf("ساعة ونصف") }
    var examDate by remember { mutableStateOf("2026 / 2027") }
    var noteLine by remember { mutableStateOf("الإجابة عن جميع الأسئلة (أو ترك سؤال واحد)") }

    var selectedChapterIds by remember(state.questionChapters) {
        mutableStateOf(state.questionChapters.map { it.id }.toSet())
    }
    var slots by remember { mutableStateOf(defaultExamSlots()) }
    var includeAnswersInExport by remember { mutableStateOf(false) }
    var generatedPaper by remember { mutableStateOf<GeneratedExamPaper?>(null) }
    var showAddSlotDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("معلومات الورقة الامتحانية", style = MaterialTheme.typography.titleMedium, color = colors.accent, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = examTitle, onValueChange = { examTitle = it }, label = { Text("عنوان الامتحان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = gradeTitle, onValueChange = { gradeTitle = it }, label = { Text("الصف") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = examDuration, onValueChange = { examDuration = it }, label = { Text("الزمن") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = noteLine, onValueChange = { noteLine = it }, label = { Text("ملاحظة الترك") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    Text("الفصول الداخلة في التوليد (بحسب أوزان درجاتها):", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.questionChapters.forEach { ch ->
                            val picked = ch.id in selectedChapterIds
                            FilterChip(
                                selected = picked,
                                onClick = {
                                    selectedChapterIds = if (picked && selectedChapterIds.size > 1) {
                                        selectedChapterIds - ch.id
                                    } else {
                                        selectedChapterIds + ch.id
                                    }
                                },
                                label = { Text("${ch.title} (${ch.gradeWeight})") }
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ترتيب أسئلة النموذج (س1، س2...)",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = { showAddSlotDialog = true }) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إضافة سؤال")
                }
            }
        }

        items(slots, key = { it.id }) { slot ->
            val sec = state.questionSections.find { it.id == slot.sectionId }
            val secColor = sec?.colorHex?.let { Color(it) } ?: colors.accent
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.elevated,
                border = BorderStroke(1.2.dp, secColor.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth().neonCardGlow(secColor, colors.neonGlow, 14.dp, alphaFactor = 0.22f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = CircleShape, color = secColor.copy(alpha = 0.18f)) {
                                Text(
                                    "س:${slot.questionNumber}",
                                    color = secColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = slot.customHeading.ifBlank { sec?.title ?: "قسم" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }
                        if (slots.size > 1) {
                            IconButton(
                                onClick = {
                                    val filtered = slots.filterNot { it.id == slot.id }
                                        .mapIndexed { idx, s -> s.copy(questionNumber = idx + 1) }
                                    slots = filtered
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف", tint = DangerRed)
                            }
                        }
                    }

                    // Section selector chips
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.questionSections.forEach { sOpt ->
                            val optColor = Color(sOpt.colorHex)
                            FilterChip(
                                selected = sOpt.id == slot.sectionId,
                                onClick = {
                                    slots = slots.map {
                                        if (it.id == slot.id) it.copy(sectionId = sOpt.id, customHeading = "") else it
                                    }
                                },
                                label = { Text(sOpt.title, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = optColor.copy(alpha = 0.22f),
                                    selectedLabelColor = optColor
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الفروع: ${slot.branchCount}", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = {
                                    slots = slots.map {
                                        if (it.id == slot.id) it.copy(branchCount = (it.branchCount - 1).coerceAtLeast(1)) else it
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) { Text("-", color = colors.textPrimary) }
                            OutlinedButton(
                                onClick = {
                                    slots = slots.map {
                                        if (it.id == slot.id) it.copy(branchCount = (it.branchCount + 1).coerceAtMost(12)) else it
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) { Text("+", color = colors.accent) }
                        }
                        Text("الدرجة: ${slot.totalMarks}", style = MaterialTheme.typography.labelLarge, color = secColor, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = {
                                    slots = slots.map {
                                        if (it.id == slot.id) it.copy(totalMarks = (it.totalMarks - 5).coerceAtLeast(5)) else it
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) { Text("-5", color = colors.textPrimary) }
                            OutlinedButton(
                                onClick = {
                                    slots = slots.map {
                                        if (it.id == slot.id) it.copy(totalMarks = (it.totalMarks + 5).coerceAtMost(100)) else it
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) { Text("+5", color = secColor) }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    generatedPaper = ExamGeneratorHelper.generateExamPaper(
                        examTitle = examTitle,
                        subjectName = state.profile.subjectName.ifBlank { "المادة الدراسية" },
                        gradeTitle = gradeTitle,
                        examDate = examDate,
                        examDuration = examDuration,
                        teacherName = state.profile.teacherName.ifBlank { "مدرس المادة" },
                        schoolName = state.profile.schoolName,
                        noteLine = noteLine,
                        selectedChapterIds = selectedChapterIds,
                        chapters = state.questionChapters,
                        sections = state.questionSections,
                        allQuestions = state.bankQuestions,
                        slots = slots
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("generate_exam_now_btn")
            ) {
                Icon(Icons.Filled.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("توليد نموذج الأسئلة الآن (${slots.sumOf { it.totalMarks }} درجة)", fontWeight = FontWeight.Bold)
            }
        }

        generatedPaper?.let { paper ->
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surface,
                    border = BorderStroke(1.5.dp, colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .neonCardGlow(colors.accent, colors.neonGlow, 18.dp)
                        .testTag("generated_exam_preview_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("معاينة النموذج المُولَّد", style = MaterialTheme.typography.titleMedium, color = colors.accent, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("مع الأجوبة", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                Switch(checked = includeAnswersInExport, onCheckedChange = { includeAnswersInExport = it })
                            }
                        }

                        Text(
                            "${paper.schoolName} • ${paper.examTitle} (${paper.subjectName} — ${paper.gradeTitle})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            "التاريخ: ${paper.examDate} • الزمن: ${paper.examDuration} • المجموع: ${paper.totalMarks}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )

                        val branchLetters = listOf("أ", "ب", "ج", "د", "هـ", "و", "ز", "ح", "ط", "ي")
                        paper.blocks.forEach { block ->
                            val bColor = Color(block.sectionColorHex)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.elevated,
                                border = BorderStroke(1.dp, bColor.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "س${block.questionNumber} / ${block.sectionTitle} (${block.totalMarks} درجة)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = bColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (block.branches.isEmpty()) {
                                        Text(
                                            "لم تتم إضافة أسئلة في هذا القسم ضمن الفصول المحددة بعد.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textMuted
                                        )
                                    } else {
                                        block.branches.forEachIndexed { idx, q ->
                                            val prefix = branchLetters.getOrElse(idx) { "${idx + 1}" }
                                            Text(
                                                "$prefix- ${q.questionText}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.textPrimary
                                            )
                                            val imgFile = getQuestionImageFile(q.imageFileName)
                                            if (imgFile != null) {
                                                val bmp = remember(imgFile.absolutePath) {
                                                    BitmapFactory.decodeFile(imgFile.absolutePath)
                                                }
                                                if (bmp != null) {
                                                    Image(
                                                        bitmap = bmp.asImageBitmap(),
                                                        contentDescription = "صورة السؤال",
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(max = 140.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                    )
                                                }
                                            }
                                            if (includeAnswersInExport && q.answerText.isNotBlank()) {
                                                Text(
                                                    "الإجابة: ${q.answerText}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.accent
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    val pdf = ExamGeneratorHelper.generateExamPdf(context, paper, includeAnswersInExport)
                                    ExportManager.shareFile(context, pdf, "application/pdf", "مشاركة نموذج الأسئلة PDF")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("تصدير PDF", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    val txt = ExamGeneratorHelper.buildExamShareText(paper, includeAnswersInExport)
                                    ExportManager.shareText(context, paper.examTitle, txt)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("مشاركة نص", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSlotDialog) {
        var pickedSectionId by remember { mutableStateOf(state.questionSections.firstOrNull()?.id ?: "sec_define") }
        var heading by remember { mutableStateOf("") }
        var marksTxt by remember { mutableStateOf("20") }
        var branchesTxt by remember { mutableStateOf("4") }
        AlertDialog(
            onDismissRequest = { showAddSlotDialog = false },
            title = { Text("إضافة سؤال رئيسي رقم ${slots.size + 1}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("نوع السؤال:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.questionSections.forEach { sec ->
                            FilterChip(
                                selected = sec.id == pickedSectionId,
                                onClick = { pickedSectionId = sec.id },
                                label = { Text(sec.title) }
                            )
                        }
                    }
                    OutlinedTextField(value = heading, onValueChange = { heading = it }, label = { Text("عنوان مخصص (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = branchesTxt,
                            onValueChange = { branchesTxt = it.filter(Char::isDigit) },
                            label = { Text("عدد الفروع") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = marksTxt,
                            onValueChange = { marksTxt = it.filter(Char::isDigit) },
                            label = { Text("الدرجة") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newSlot = ExamSlotConfig(
                        id = "slot_${System.currentTimeMillis()}",
                        questionNumber = slots.size + 1,
                        sectionId = pickedSectionId,
                        customHeading = heading.trim(),
                        totalMarks = marksTxt.toIntOrNull()?.coerceIn(1, 100) ?: 20,
                        branchCount = branchesTxt.toIntOrNull()?.coerceIn(1, 15) ?: 4
                    )
                    slots = slots + newSlot
                    showAddSlotDialog = false
                }) { Text("إضافة") }
            },
            dismissButton = { TextButton(onClick = { showAddSlotDialog = false }) { Text("إلغاء") } }
        )
    }
}
