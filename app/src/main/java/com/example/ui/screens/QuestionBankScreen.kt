package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.model.*
import com.example.ui.components.neonCardGlow
import com.example.ui.theme.DangerRed
import java.io.File

private val sectionColorChoices = listOf(
    0xFF00D2D3L, // Cyan
    0xFFFF9F43L, // Amber
    0xFFFF6B81L, // Coral Rose
    0xFF2ED573L, // Emerald
    0xFF54A0FFL, // Sky Blue
    0xFFA55EEAL, // Violet
    0xFF1ABC9CL, // Teal
    0xFFFFD54FL  // Gold
)

@Composable
fun QuestionBankScreen(
    state: AppState,
    onAddChapter: (String, Int) -> Boolean,
    onUpdateChapter: (QuestionChapter) -> Unit,
    onDeleteChapter: (String) -> Unit,
    onAddCustomSection: (String, Long) -> Unit,
    onDeleteCustomSection: (String) -> Unit,
    onSaveQuestion: (BankQuestion) -> Unit,
    onDeleteQuestion: (String) -> Unit,
    onImportQuestionImage: (Uri) -> String?,
    getQuestionImageFile: (String?) -> File?,
    onUpdateSecurity: (Boolean, String, Boolean) -> Unit,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val p = state.profile
    val colors = qbColors(p.questionBankThemeDark)

    var isUnlocked by remember(p.questionBankLockEnabled) {
        mutableStateOf(!p.questionBankLockEnabled)
    }
    var lockError by remember { mutableStateOf<String?>(null) }
    var pinEntry by remember { mutableStateOf("") }
    var showSecurityDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("qb_back_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = colors.accent)
                }
                Column {
                    Text(
                        text = "بنك الأسئلة السري",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (p.questionBankThemeDark) "الوضع الليلي حسب الثيم" else "خلفية بيضاء ناصعة",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("qb_toggle_theme_btn")
                ) {
                    Icon(
                        imageVector = if (p.questionBankThemeDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = "تبديل خلفية بنك الأسئلة (داكن / أبيض)",
                        tint = colors.accent
                    )
                }
                if (isUnlocked) {
                    IconButton(
                        onClick = { showSecurityDialog = true },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("qb_security_settings_btn")
                    ) {
                        Icon(
                            imageVector = if (p.questionBankLockEnabled) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = "إعدادات القفل والبصمة",
                            tint = if (p.questionBankLockEnabled) colors.accent else colors.textMuted
                        )
                    }
                }
            }
        }

        if (!isUnlocked) {
            // Security Lock Gate (PIN / Password + Fingerprint Biometric + Hint)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = colors.surface,
                    border = BorderStroke(1.5.dp, colors.accent.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth().neonCardGlow(colors.accent, colors.neonGlow, 22.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
                        }
                        Text(
                            "قسم بنك الأسئلة محمي وسري",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            "أدخل كلمة السر / الرقم السري أو افتح بالبصمة (الافتراضي: 1234)",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )

                        OutlinedTextField(
                            value = pinEntry,
                            onValueChange = { pinEntry = it; lockError = null },
                            label = { Text("كلمة السر أو الرقم السري") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().testTag("qb_pin_input")
                        )

                        lockError?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = DangerRed)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    if (pinEntry == p.questionBankPin) {
                                        isUnlocked = true
                                        pinEntry = ""
                                    } else {
                                        lockError = "كلمة السر غير صحيحة"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("qb_unlock_pin_btn")
                            ) {
                                Icon(Icons.Filled.Key, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("دخول", fontWeight = FontWeight.Bold)
                            }
                            if (p.questionBankBiometricEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                OutlinedButton(
                                    onClick = {
                                        launchBiometricPrompt(
                                            context = context,
                                            onSuccess = { isUnlocked = true },
                                            onError = { msg -> lockError = msg }
                                        )
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("qb_unlock_biometric_btn")
                                ) {
                                    Icon(Icons.Filled.Fingerprint, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("البصمة", color = colors.accent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            return@Column
        }

        // Unlocked Content: 3 Sub-Tabs
        var selectedSubTab by remember { mutableStateOf(0) }
        val subTabs = listOf("بنك الأسئلة والأقسام", "الفصول (10) والأوزان", "توليد نموذج أسئلة")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            subTabs.forEachIndexed { idx, label ->
                val active = selectedSubTab == idx
                FilterChip(
                    selected = active,
                    onClick = { selectedSubTab = idx },
                    label = { Text(label, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accent,
                        selectedLabelColor = colors.onAccent,
                        containerColor = colors.surface,
                        labelColor = colors.textPrimary
                    ),
                    modifier = Modifier.testTag("qb_tab_$idx")
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (selectedSubTab) {
            0 -> QuestionBankQuestionsTab(
                state = state,
                colors = colors,
                onAddCustomSection = onAddCustomSection,
                onDeleteCustomSection = onDeleteCustomSection,
                onSaveQuestion = onSaveQuestion,
                onDeleteQuestion = onDeleteQuestion,
                onImportQuestionImage = onImportQuestionImage,
                getQuestionImageFile = getQuestionImageFile
            )

            1 -> QuestionBankChaptersTab(
                chapters = state.questionChapters,
                questions = state.bankQuestions,
                colors = colors,
                onAddChapter = onAddChapter,
                onUpdateChapter = onUpdateChapter,
                onDeleteChapter = onDeleteChapter
            )

            2 -> QuestionBankExamGeneratorTab(
                state = state,
                colors = colors,
                getQuestionImageFile = getQuestionImageFile
            )
        }
    }

    if (showSecurityDialog) {
        var lockEnabled by remember { mutableStateOf(p.questionBankLockEnabled) }
        var newPin by remember { mutableStateOf(p.questionBankPin) }
        var bioEnabled by remember { mutableStateOf(p.questionBankBiometricEnabled) }
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            title = { Text("أمان وقفل بنك الأسئلة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تفعيل قفل كلمة السر / البصمة")
                        Switch(checked = lockEnabled, onCheckedChange = { lockEnabled = it })
                    }
                    if (lockEnabled) {
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { newPin = it },
                            label = { Text("كلمة السر أو الرقم السري الجديد") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("السماح بالدخول بالبصمة")
                            Switch(checked = bioEnabled, onCheckedChange = { bioEnabled = it })
                        }
                    } else {
                        Text(
                            "تم تعطيل كلمة السر: سيفتح بنك الأسئلة مباشرة دون طلب رمز حتى تقوم بتفعيله مجدداً.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.accent
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateSecurity(lockEnabled, newPin, bioEnabled)
                    showSecurityDialog = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showSecurityDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun QuestionBankQuestionsTab(
    state: AppState,
    colors: QbPaletteColors,
    onAddCustomSection: (String, Long) -> Unit,
    onDeleteCustomSection: (String) -> Unit,
    onSaveQuestion: (BankQuestion) -> Unit,
    onDeleteQuestion: (String) -> Unit,
    onImportQuestionImage: (Uri) -> String?,
    getQuestionImageFile: (String?) -> File?
) {
    var selectedChapterId by remember(state.questionChapters) {
        mutableStateOf(state.questionChapters.firstOrNull()?.id ?: "qch_1")
    }
    var selectedSectionId by remember { mutableStateOf<String?>(null) } // null = All sections
    var editingQuestion by remember { mutableStateOf<BankQuestion?>(null) }
    var showAddQuestionDialog by remember { mutableStateOf(false) }
    var showAddSectionDialog by remember { mutableStateOf(false) }

    val filteredQuestions = remember(state.bankQuestions, selectedChapterId, selectedSectionId) {
        state.bankQuestions.filter { q ->
            q.chapterId == selectedChapterId && (selectedSectionId == null || q.sectionId == selectedSectionId)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("اختر الفصل:", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.questionChapters.forEach { ch ->
                        val active = ch.id == selectedChapterId
                        FilterChip(
                            selected = active,
                            onClick = { selectedChapterId = ch.id },
                            label = { Text("${ch.title} (${state.bankQuestions.count { it.chapterId == ch.id }})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accent,
                                selectedLabelColor = colors.onAccent,
                                containerColor = colors.surface,
                                labelColor = colors.textPrimary
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("أقسام الأسئلة (كل نوع بلون):", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
                    TextButton(onClick = { showAddSectionDialog = true }) {
                        Icon(Icons.Filled.AddCircleOutline, null, modifier = Modifier.size(16.dp), tint = colors.accent)
                        Spacer(Modifier.width(4.dp))
                        Text("قسم جديد", color = colors.accent)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedSectionId == null,
                        onClick = { selectedSectionId = null },
                        label = { Text("الكل") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accent,
                            selectedLabelColor = colors.onAccent,
                            containerColor = colors.surface,
                            labelColor = colors.textPrimary
                        )
                    )
                    state.questionSections.forEach { sec ->
                        val secColor = Color(sec.colorHex)
                        val active = sec.id == selectedSectionId
                        FilterChip(
                            selected = active,
                            onClick = { selectedSectionId = sec.id },
                            label = { Text(sec.title) },
                            leadingIcon = {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(secColor))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = secColor,
                                selectedLabelColor = Color(0xFF061018),
                                containerColor = colors.surface,
                                labelColor = secColor
                            ),
                            modifier = Modifier.testTag("qb_section_chip_${sec.id}")
                        )
                    }
                }

                Button(
                    onClick = { showAddQuestionDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("qb_add_question_btn")
                ) {
                    Icon(Icons.Filled.EditNote, null)
                    Spacer(Modifier.width(8.dp))
                    Text("كتابة سؤال جديد يدوياً (+ صورة)", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (filteredQuestions.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.LibraryBooks, null, tint = colors.textMuted, modifier = Modifier.size(44.dp))
                        Text(
                            "لا توجد أسئلة مضافة في هذا الفصل / القسم بعد",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                        Text(
                            "اضغط (كتابة سؤال جديد يدوياً) لإضافة أسئلتك في التعاريف أو الفراغات أو التعاليل أو الصح والخطأ أو الشرح أو التعداد مع إمكانية إرفاق صورة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                }
            }
        } else {
            items(filteredQuestions, key = { it.id }) { q ->
                val sec = state.questionSections.find { it.id == q.sectionId }
                val secColor = sec?.colorHex?.let { Color(it) } ?: colors.accent
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    border = BorderStroke(1.5.dp, secColor.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .neonCardGlow(secColor, colors.neonGlow, 16.dp, alphaFactor = 0.22f)
                        .clickable { editingQuestion = q }
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = secColor.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, secColor.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = sec?.title ?: "سؤال",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = secColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "${q.marks} درجة",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMuted
                                )
                                IconButton(onClick = { onDeleteQuestion(q.id) }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف", tint = DangerRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Text(
                            text = q.questionText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )

                        val imgFile = getQuestionImageFile(q.imageFileName)
                        if (imgFile != null) {
                            val bmp = remember(imgFile.absolutePath) {
                                BitmapFactory.decodeFile(imgFile.absolutePath)
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "صورة مرفقة مع السؤال",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            }
                        }

                        if (q.answerText.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colors.elevated,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "الإجابة النموذجية: ${q.answerText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secColor,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Question Dialog with Image Picker
    val activeModalQuestion = editingQuestion ?: if (showAddQuestionDialog) {
        BankQuestion(
            id = "",
            chapterId = selectedChapterId,
            sectionId = selectedSectionId ?: state.questionSections.firstOrNull()?.id ?: "sec_define",
            questionText = ""
        )
    } else null

    activeModalQuestion?.let { initialQ ->
        QuestionEditorDialog(
            initialQuestion = initialQ,
            chapters = state.questionChapters,
            sections = state.questionSections,
            onImportQuestionImage = onImportQuestionImage,
            getQuestionImageFile = getQuestionImageFile,
            onDismiss = {
                showAddQuestionDialog = false
                editingQuestion = null
            },
            onSave = { saved ->
                onSaveQuestion(saved)
                showAddQuestionDialog = false
                editingQuestion = null
            }
        )
    }

    // Add Custom Question Section Dialog
    if (showAddSectionDialog) {
        var title by remember { mutableStateOf("") }
        var pickedColorHex by remember { mutableStateOf(sectionColorChoices.first()) }
        AlertDialog(
            onDismissRequest = { showAddSectionDialog = false },
            title = { Text("إضافة قسم أسئلة جديد حسب الطلب") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("اسم القسم (مثال: مسائل، مقارنات، خرائط، معادلات)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("اختر لون القسم:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sectionColorChoices.forEach { hex ->
                            val selected = hex == pickedColorHex
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .clickable { pickedColorHex = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    // Allow deleting existing custom sections if needed
                    val customSecs = state.questionSections.filter { !it.isBuiltIn }
                    if (customSecs.isNotEmpty()) {
                        HorizontalDivider()
                        Text("الأقسام المضافة سابقاً:", style = MaterialTheme.typography.labelSmall)
                        customSecs.forEach { cs ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cs.title, color = Color(cs.colorHex))
                                TextButton(onClick = { onDeleteCustomSection(cs.id) }) { Text("حذف", color = DangerRed) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddCustomSection(title, pickedColorHex)
                    showAddSectionDialog = false
                }) { Text("إضافة") }
            },
            dismissButton = { TextButton(onClick = { showAddSectionDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun QuestionEditorDialog(
    initialQuestion: BankQuestion,
    chapters: List<QuestionChapter>,
    sections: List<QuestionSectionDef>,
    onImportQuestionImage: (Uri) -> String?,
    getQuestionImageFile: (String?) -> File?,
    onDismiss: () -> Unit,
    onSave: (BankQuestion) -> Unit
) {
    var chapterId by remember { mutableStateOf(initialQuestion.chapterId) }
    var sectionId by remember { mutableStateOf(initialQuestion.sectionId) }
    var qText by remember { mutableStateOf(initialQuestion.questionText) }
    var aText by remember { mutableStateOf(initialQuestion.answerText) }
    var marksTxt by remember { mutableStateOf(initialQuestion.marks.toString()) }
    var imageFileName by remember { mutableStateOf(initialQuestion.imageFileName) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedName = onImportQuestionImage(uri)
            if (savedName != null) imageFileName = savedName
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialQuestion.id.isBlank()) "إضافة سؤال جديد للبنك" else "تعديل السؤال") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("الفصل:", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chapters.forEach { ch ->
                        FilterChip(selected = ch.id == chapterId, onClick = { chapterId = ch.id }, label = { Text(ch.title) })
                    }
                }
                Text("نوع السؤال (القسم):", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sections.forEach { sec ->
                        val secColor = Color(sec.colorHex)
                        FilterChip(
                            selected = sec.id == sectionId,
                            onClick = { sectionId = sec.id },
                            label = { Text(sec.title) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = secColor.copy(alpha = 0.25f))
                        )
                    }
                }

                OutlinedTextField(
                    value = qText,
                    onValueChange = { qText = it },
                    label = { Text("نص السؤال (اكتبه يدوياً)") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("qb_question_text_input")
                )
                OutlinedTextField(
                    value = aText,
                    onValueChange = { aText = it },
                    label = { Text("الإجابة النموذجية (اختياري)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = marksTxt,
                    onValueChange = { marksTxt = it.filter(Char::isDigit) },
                    label = { Text("درجة السؤال") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Optional Image Attachment via Zero-Permission Photo Picker
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f).testTag("qb_attach_image_btn")
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (imageFileName == null) "إدراج صورة للسؤال" else "تغيير الصورة")
                    }
                    if (imageFileName != null) {
                        IconButton(onClick = { imageFileName = null }) {
                            Icon(Icons.Filled.Delete, contentDescription = "إزالة الصورة", tint = DangerRed)
                        }
                    }
                }

                val imgFile = getQuestionImageFile(imageFileName)
                if (imgFile != null) {
                    val bmp = remember(imgFile.absolutePath) { BitmapFactory.decodeFile(imgFile.absolutePath) }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "معاينة الصورة المرفقة",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp).clip(RoundedCornerShape(10.dp))
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalId = initialQuestion.id.ifBlank { "bq_${System.currentTimeMillis()}" }
                    onSave(
                        initialQuestion.copy(
                            id = finalId,
                            chapterId = chapterId,
                            sectionId = sectionId,
                            questionText = qText.trim(),
                            answerText = aText.trim(),
                            marks = marksTxt.toIntOrNull()?.coerceIn(1, 100) ?: 5,
                            imageFileName = imageFileName
                        )
                    )
                },
                enabled = qText.isNotBlank(),
                modifier = Modifier.testTag("qb_save_question_btn")
            ) { Text("حفظ السؤال") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
