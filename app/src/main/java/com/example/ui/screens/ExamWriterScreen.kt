package com.example.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ExamWriterRepository
import com.example.data.QuranSurah
import com.example.model.*
import com.example.ui.components.HoldToSpeakMicButton
import com.example.ui.theme.AppFontOption
import com.example.ui.theme.DangerRed
import com.example.ui.theme.GoldBright
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.theme.SuccessGreen
import com.example.utils.ExamWriterExportHelper
import com.example.utils.ExportManager
import com.example.utils.OcrAndImportHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

private val ExamColorChoices: List<Pair<String, Long>> = listOf(
    "أزرق" to 0xFF1565C0L,
    "أخضر" to 0xFF0F766EL,
    "بنفسجي" to 0xFF7C3AEDL,
    "عنابي" to 0xFFB91C1CL,
    "ذهبي" to 0xFFB45309L,
    "داكن" to 0xFF111827L,
    "رمادي" to 0xFF475569L
)

private sealed interface QuranInsertTarget {
    data object NewQuestion : QuranInsertTarget
    data class ExistingQuestion(val questionId: String) : QuranInsertTarget
    data class ExistingBranch(val questionId: String, val branchId: String) : QuranInsertTarget
}

@Composable
fun ExamWriterScreen(
    state: AppState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val scope = rememberCoroutineScope()
    val repo = remember(context) { ExamWriterRepository(context.applicationContext) }

    var docs by remember { mutableStateOf<List<ExamPaperDocument>>(emptyList()) }
    var activeDocId by remember { mutableStateOf("") }
    var activePageIndex by remember { mutableStateOf(0) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Editor, 1 = Live Preview
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var quranTarget by remember { mutableStateOf<QuranInsertTarget?>(null) }
    var headerExpanded by remember { mutableStateOf(false) }
    var typographyExpanded by remember { mutableStateOf(false) }
    var ocrBusy by remember { mutableStateOf(false) }
    var exportBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = repo.loadDocuments()
        docs = loaded
        activeDocId = loaded.firstOrNull()?.id.orEmpty()
    }

    val activeDoc = remember(docs, activeDocId) {
        docs.find { it.id == activeDocId } ?: docs.firstOrNull() ?: ExamPaperDocument(
            schoolName = state.profile.schoolName.ifBlank { "مدرستي" },
            subjectName = state.profile.subjectName.ifBlank { "المادة" },
            gradeAndSection = state.profile.preferredGrade.titleAr
        )
    }

    val safePageIndex = activePageIndex.coerceIn(0, (activeDoc.pages.size - 1).coerceAtLeast(0))
    val currentPage = activeDoc.pages.getOrElse(safePageIndex) { ExamPageData() }

    fun commitDoc(updated: ExamPaperDocument) {
        val stamped = updated.copy(updatedAtMillis = System.currentTimeMillis())
        val nextList = if (docs.any { it.id == stamped.id }) {
            docs.map { if (it.id == stamped.id) stamped else it }
        } else {
            listOf(stamped) + docs
        }
        docs = nextList
        activeDocId = stamped.id
        scope.launch { repo.saveDocuments(nextList) }
    }

    fun updateActivePage(transform: (ExamPageData) -> ExamPageData) {
        val updatedPages = activeDoc.pages.mapIndexed { idx, p ->
            if (idx == safePageIndex) transform(p) else p
        }
        commitDoc(activeDoc.copy(pages = updatedPages))
    }

    val customFramePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { repo.importCustomFramePng(uri) }
                if (saved != null) {
                    commitDoc(
                        activeDoc.copy(
                            frameStyle = ExamFrameStyle.CUSTOM_PNG,
                            customFrameFileName = saved
                        )
                    )
                    Toast.makeText(context, "تم تثبيت إطار PNG المخصص للورقة ✓", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "تعذر استيراد الصورة المختارة.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val ocrPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            ocrBusy = true
            scope.launch {
                val bmp = OcrAndImportHelper.decodeBitmapFromUri(context, uri)
                if (bmp != null) {
                    OcrAndImportHelper.recognizeTextFromBitmap(bmp)
                        .onSuccess { extracted ->
                            val clean = extracted.trim()
                            if (clean.isNotEmpty()) {
                                val nextNum = currentPage.questions.size + 1
                                updateActivePage { pg ->
                                    pg.copy(
                                        questions = pg.questions + ExamQuestionBox(
                                            questionTitle = "س$nextNum/",
                                            questionType = ExamQuestionType.QA,
                                            promptText = clean,
                                            marks = 10
                                        )
                                    )
                                }
                                Toast.makeText(context, "تم استخراج النص وإضافته كسؤال جديد ✓", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "لم يتم العثور على نص واضح في الصورة.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .onFailure {
                            Toast.makeText(context, "تعذر تحليل النص من الصورة.", Toast.LENGTH_SHORT).show()
                        }
                }
                ocrBusy = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Header & Export Action Row
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = ext.cardElevated,
            border = BorderStroke(1.dp, ext.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp).testTag("exam_writer_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "كتابة الأسئلة الامتحانية",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${activeDoc.docTitle} • ${activeDoc.pages.size} صفحة",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = { showTemplatesDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("exam_writer_templates_btn")
                    ) {
                        Icon(Icons.Filled.Style, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("القوالب", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Document Switcher + Page Strip + PDF / Word Share
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    docs.forEach { d ->
                        val isSel = d.id == activeDoc.id
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                activeDocId = d.id
                                activePageIndex = 0
                            },
                            label = { Text(d.docTitle.take(18), maxLines = 1) }
                        )
                    }
                    AssistChip(
                        onClick = {
                            val newDoc = ExamPaperDocument(
                                docTitle = "أسئلة جديدة (${docs.size + 1})",
                                schoolName = state.profile.schoolName.ifBlank { "مدرستي" },
                                subjectName = state.profile.subjectName.ifBlank { "المادة" },
                                gradeAndSection = state.profile.preferredGrade.titleAr
                            )
                            val updated = listOf(newDoc) + docs
                            docs = updated
                            activeDocId = newDoc.id
                            activePageIndex = 0
                            scope.launch { repo.saveDocuments(updated) }
                        },
                        leadingIcon = { Icon(Icons.Filled.NoteAdd, null, modifier = Modifier.size(16.dp)) },
                        label = { Text("ورقة جديدة") },
                        modifier = Modifier.testTag("exam_writer_new_doc_btn")
                    )
                    if (docs.size > 1) {
                        AssistChip(
                            onClick = {
                                val remaining = docs.filterNot { it.id == activeDoc.id }
                                docs = remaining
                                activeDocId = remaining.first().id
                                activePageIndex = 0
                                scope.launch { repo.saveDocuments(remaining) }
                            },
                            leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = DangerRed, modifier = Modifier.size(16.dp)) },
                            label = { Text("حذف الورقة", color = DangerRed) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        enabled = !exportBusy,
                        onClick = {
                            exportBusy = true
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    ExamWriterExportHelper.exportToPdf(
                                        context = context,
                                        doc = activeDoc,
                                        customFrameFile = repo.getCustomFrameFile(activeDoc.customFrameFileName)
                                    )
                                }
                                exportBusy = false
                                res.onSuccess { file ->
                                    ExportManager.shareFile(context, file, "application/pdf", "مشاركة أسئلة PDF")
                                }.onFailure { err ->
                                    Toast.makeText(context, "فشل التصدير: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("exam_writer_export_pdf_btn")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تصدير ومشاركة PDF", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }

                    OutlinedButton(
                        enabled = !exportBusy,
                        onClick = {
                            exportBusy = true
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    ExamWriterExportHelper.exportToWordHtml(context, activeDoc)
                                }
                                exportBusy = false
                                res.onSuccess { file ->
                                    ExportManager.shareFile(context, file, "application/msword", "مشاركة ملف Word للأسئلة")
                                }.onFailure { err ->
                                    Toast.makeText(context, "فشل تصدير Word: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("exam_writer_export_word_btn")
                    ) {
                        Icon(Icons.Filled.Description, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تصدير Word", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }

        // 2. Two Tabs: Editor vs Live Paper Print Preview
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = ext.cardElevated,
            modifier = Modifier.clip(RoundedCornerShape(14.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                icon = { Icon(Icons.Filled.EditNote, null, modifier = Modifier.size(18.dp)) },
                text = { Text("المحرر والأسئلة", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("exam_writer_tab_editor")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                icon = { Icon(Icons.Filled.Visibility, null, modifier = Modifier.size(18.dp)) },
                text = { Text("معاينة الورقة والطباعة", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("exam_writer_tab_preview")
            )
        }

        // Page Selector Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            activeDoc.pages.forEachIndexed { idx, pg ->
                FilterChip(
                    selected = idx == safePageIndex,
                    onClick = { activePageIndex = idx },
                    label = { Text(pg.pageTitle.ifBlank { "صفحة ${idx + 1}" }) }
                )
            }
            AssistChip(
                onClick = {
                    val newPg = ExamPageData(pageTitle = "الصفحة ${activeDoc.pages.size + 1}")
                    commitDoc(activeDoc.copy(pages = activeDoc.pages + newPg))
                    activePageIndex = activeDoc.pages.size
                },
                leadingIcon = { Icon(Icons.Filled.Add, null, modifier = Modifier.size(15.dp)) },
                label = { Text("صفحة فارغة") }
            )
            AssistChip(
                onClick = {
                    val clonedQuestions = currentPage.questions.map { q ->
                        q.copy(
                            id = UUID.randomUUID().toString(),
                            branches = q.branches.map { it.copy(id = UUID.randomUUID().toString()) }
                        )
                    }
                    val clonedPg = currentPage.copy(
                        id = UUID.randomUUID().toString(),
                        pageTitle = "نسخة ${currentPage.pageTitle}",
                        questions = clonedQuestions
                    )
                    commitDoc(activeDoc.copy(pages = activeDoc.pages + clonedPg))
                    activePageIndex = activeDoc.pages.size
                },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(15.dp)) },
                label = { Text("تكرار الصفحة") },
                modifier = Modifier.testTag("exam_writer_duplicate_page_btn")
            )
            if (activeDoc.pages.size > 1) {
                AssistChip(
                    onClick = {
                        val remaining = activeDoc.pages.filterIndexed { i, _ -> i != safePageIndex }
                        commitDoc(activeDoc.copy(pages = remaining))
                        activePageIndex = (safePageIndex - 1).coerceAtLeast(0)
                    },
                    leadingIcon = { Icon(Icons.Filled.Close, null, tint = DangerRed, modifier = Modifier.size(14.dp)) },
                    label = { Text("حذف الصفحة") }
                )
            }
        }

        if (selectedTab == 0) {
            // ================= TAB 0: STRUCTURED EXAM EDITOR =================
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Document Header & Frame Card
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.dp, ext.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { headerExpanded = !headerExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Article, null, tint = MaterialTheme.colorScheme.primary)
                                    Text("معلومات الترويسة والورقة والإطار", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Icon(if (headerExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                            }
                            AnimatedVisibility(visible = headerExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = activeDoc.docTitle,
                                        onValueChange = { commitDoc(activeDoc.copy(docTitle = it)) },
                                        label = { Text("عنوان نموذج الأسئلة") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = activeDoc.ministryHeader,
                                            onValueChange = { commitDoc(activeDoc.copy(ministryHeader = it)) },
                                            label = { Text("الترويسة العليا") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = activeDoc.schoolName,
                                            onValueChange = { commitDoc(activeDoc.copy(schoolName = it)) },
                                            label = { Text("اسم المدرسة") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = activeDoc.subjectName,
                                            onValueChange = { commitDoc(activeDoc.copy(subjectName = it)) },
                                            label = { Text("المادة") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = activeDoc.gradeAndSection,
                                            onValueChange = { commitDoc(activeDoc.copy(gradeAndSection = it)) },
                                            label = { Text("الصف والشعبة") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = activeDoc.examDuration,
                                            onValueChange = { commitDoc(activeDoc.copy(examDuration = it)) },
                                            label = { Text("الوقت المخصص") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = activeDoc.totalMarksNote,
                                            onValueChange = { commitDoc(activeDoc.copy(totalMarksNote = it)) },
                                            label = { Text("ملاحظة عامة") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    OutlinedTextField(
                                        value = currentPage.footerNote,
                                        onValueChange = { note -> updateActivePage { it.copy(footerNote = note) } },
                                        label = { Text("تذييل هذه الصفحة") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Text("اتجاه الورقة وحجمها:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ExamPaperOrientation.entries.forEach { ori ->
                                            FilterChip(
                                                selected = activeDoc.orientation == ori,
                                                onClick = { commitDoc(activeDoc.copy(orientation = ori)) },
                                                label = { Text(ori.titleAr) }
                                            )
                                        }
                                        ExamPaperSize.entries.forEach { sz ->
                                            FilterChip(
                                                selected = activeDoc.paperSize == sz,
                                                onClick = { commitDoc(activeDoc.copy(paperSize = sz)) },
                                                label = { Text(sz.titleAr) }
                                            )
                                        }
                                    }

                                    Text("الإطارات الزخرفية و PNG مخصص:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ExamFrameStyle.entries.forEach { fs ->
                                            FilterChip(
                                                selected = activeDoc.frameStyle == fs,
                                                onClick = {
                                                    if (fs == ExamFrameStyle.CUSTOM_PNG && activeDoc.customFrameFileName == null) {
                                                        customFramePicker.launch(
                                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                        )
                                                    } else {
                                                        commitDoc(activeDoc.copy(frameStyle = fs))
                                                    }
                                                },
                                                label = { Text(fs.titleAr) }
                                            )
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            customFramePicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.AddPhotoAlternate, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            if (activeDoc.customFrameFileName != null) "تغيير إطار PNG المخصص" else "استيراد إطار صورة PNG من الهاتف"
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = activeDoc.showCoverPage,
                                            onClick = { commitDoc(activeDoc.copy(showCoverPage = !activeDoc.showCoverPage)) },
                                            label = { Text("صفحة غلاف") }
                                        )
                                        FilterChip(
                                            selected = activeDoc.showTableOfContents,
                                            onClick = { commitDoc(activeDoc.copy(showTableOfContents = !activeDoc.showTableOfContents)) },
                                            label = { Text("فهرس المحتويات") }
                                        )
                                        FilterChip(
                                            selected = activeDoc.showPageNumbers,
                                            onClick = { commitDoc(activeDoc.copy(showPageNumbers = !activeDoc.showPageNumbers)) },
                                            label = { Text("ترقيم الصفحات") }
                                        )
                                        FilterChip(
                                            selected = activeDoc.freeManualDragMode,
                                            onClick = { commitDoc(activeDoc.copy(freeManualDragMode = !activeDoc.freeManualDragMode)) },
                                            label = { Text("السحب اليدوي الحر") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Typography & Spacing Toolbar Card
                item {
                    val t = activeDoc.typography
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.dp, ext.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { typographyExpanded = !typographyExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.TextFields, null, tint = InfoBlue)
                                    Text(
                                        "الخط والتنسيق: ${t.fontOption.titleAr} (${t.baseFontSizeSp.toInt()}pt)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(if (typographyExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                            }
                            AnimatedVisibility(visible = typographyExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("اختر نوع الخط (المعروض بحجم وخط حقيقي):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        AppFontOption.entries.forEach { fontOpt ->
                                            FilterChip(
                                                selected = t.fontOption == fontOpt,
                                                onClick = {
                                                    commitDoc(activeDoc.copy(typography = t.copy(fontOption = fontOpt)))
                                                },
                                                label = {
                                                    Text(
                                                        fontOpt.titleAr,
                                                        fontFamily = fontOpt.family,
                                                        fontWeight = if (t.fontOption == fontOpt) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    // Bold / Italic / Underline / Alignment
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = t.isBold,
                                            onClick = { commitDoc(activeDoc.copy(typography = t.copy(isBold = !t.isBold))) },
                                            label = { Text("عريض B", fontWeight = FontWeight.Bold) }
                                        )
                                        FilterChip(
                                            selected = t.isItalic,
                                            onClick = { commitDoc(activeDoc.copy(typography = t.copy(isItalic = !t.isItalic))) },
                                            label = { Text("مائل I", fontStyle = FontStyle.Italic) }
                                        )
                                        FilterChip(
                                            selected = t.isUnderlined,
                                            onClick = { commitDoc(activeDoc.copy(typography = t.copy(isUnderlined = !t.isUnderlined))) },
                                            label = { Text("تحته خط U", textDecoration = TextDecoration.Underline) }
                                        )
                                        ExamTextAlign.entries.forEach { al ->
                                            FilterChip(
                                                selected = t.alignment == al,
                                                onClick = { commitDoc(activeDoc.copy(typography = t.copy(alignment = al))) },
                                                label = { Text("محاذاة: ${al.titleAr}") }
                                            )
                                        }
                                    }

                                    Text("حجم خط الأسئلة: ${t.baseFontSizeSp.toInt()} | العناوين: ${t.headingFontSizeSp.toInt()}", style = MaterialTheme.typography.labelSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Slider(
                                            value = t.baseFontSizeSp,
                                            onValueChange = { commitDoc(activeDoc.copy(typography = t.copy(baseFontSizeSp = it))) },
                                            valueRange = 10f..22f,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Slider(
                                            value = t.headingFontSizeSp,
                                            onValueChange = { commitDoc(activeDoc.copy(typography = t.copy(headingFontSizeSp = it))) },
                                            valueRange = 14f..28f,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Text("تباعد الأسطر (${String.format("%.2f", t.lineHeightMultiplier)}) والهوامش (${t.marginHorizontalDp.toInt()}dp):", style = MaterialTheme.typography.labelSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Slider(
                                            value = t.lineHeightMultiplier,
                                            onValueChange = { commitDoc(activeDoc.copy(typography = t.copy(lineHeightMultiplier = it))) },
                                            valueRange = 1.05f..2.2f,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Slider(
                                            value = t.marginHorizontalDp,
                                            onValueChange = { commitDoc(activeDoc.copy(typography = t.copy(marginHorizontalDp = it))) },
                                            valueRange = 8f..42f,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Question Creation Tools Row: Add + Voice + OCR + Quran Ayah
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val nextNum = currentPage.questions.size + 1
                                        updateActivePage { pg ->
                                            pg.copy(
                                                questions = pg.questions + ExamQuestionBox(
                                                    questionTitle = "س$nextNum/",
                                                    questionType = ExamQuestionType.QA,
                                                    promptText = "",
                                                    marks = 10
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f).testTag("exam_writer_add_question_btn")
                                ) {
                                    Icon(Icons.Filled.AddCircle, null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("إضافة سؤال", maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = { quranTarget = QuranInsertTarget.NewQuestion },
                                    modifier = Modifier.weight(1f).testTag("exam_writer_quran_btn")
                                ) {
                                    Icon(Icons.Filled.AutoStories, null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("آية قرآنية ﴿ ﴾", maxLines = 1)
                                }

                                OutlinedButton(
                                    enabled = !ocrBusy,
                                    onClick = {
                                        ocrPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.weight(0.85f).testTag("exam_writer_ocr_btn")
                                ) {
                                    Icon(Icons.Filled.DocumentScanner, null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("OCR", maxLines = 1)
                                }

                                HoldToSpeakMicButton(
                                    onRecognizedText = { spoken ->
                                        val parsed = ExamVoiceParser.parseSpokenArabicToExamQuestion(
                                            spokenText = spoken,
                                            questionIndex = currentPage.questions.size
                                        )
                                        updateActivePage { pg -> pg.copy(questions = pg.questions + parsed) }
                                        Toast.makeText(context, "تمت إضافة السؤال من الصوت ✓", Toast.LENGTH_SHORT).show()
                                    },
                                    onStatusMessage = { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    },
                                    testTagName = "exam_writer_voice_btn"
                                )
                            }
                            if (ocrBusy) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Text(
                                text = "إملاء صوتي ذكي: انطق مثلاً (السؤال الأول فراغات عليه 10 درجات فرع أ ... فرع ب ...)",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.textMuted
                            )
                        }
                    }
                }

                // Questions List
                itemsIndexed(currentPage.questions, key = { _, q -> q.id }) { qIdx, q ->
                    QuestionEditorBoxCard(
                        question = q,
                        index = qIdx,
                        totalCount = currentPage.questions.size,
                        onUpdate = { updatedQ ->
                            updateActivePage { pg ->
                                pg.copy(questions = pg.questions.map { if (it.id == q.id) updatedQ else it })
                            }
                        },
                        onMoveUp = {
                            if (qIdx > 0) {
                                val list = currentPage.questions.toMutableList()
                                val prev = list[qIdx - 1]
                                list[qIdx - 1] = list[qIdx]
                                list[qIdx] = prev
                                updateActivePage { it.copy(questions = list) }
                            }
                        },
                        onMoveDown = {
                            if (qIdx < currentPage.questions.lastIndex) {
                                val list = currentPage.questions.toMutableList()
                                val next = list[qIdx + 1]
                                list[qIdx + 1] = list[qIdx]
                                list[qIdx] = next
                                updateActivePage { it.copy(questions = list) }
                            }
                        },
                        onDelete = {
                            updateActivePage { pg ->
                                pg.copy(questions = pg.questions.filterNot { it.id == q.id })
                            }
                        },
                        onInsertQuranIntoQuestion = {
                            quranTarget = QuranInsertTarget.ExistingQuestion(q.id)
                        },
                        onInsertQuranIntoBranch = { branchId ->
                            quranTarget = QuranInsertTarget.ExistingBranch(q.id, branchId)
                        }
                    )
                }
            }
        } else {
            // ================= TAB 1: LIVE PAPER PRINT PREVIEW =================
            ExamLivePreviewPane(
                doc = activeDoc,
                page = currentPage,
                pageIndex = safePageIndex,
                customFrameFile = repo.getCustomFrameFile(activeDoc.customFrameFileName),
                onUpdateQuestionOffset = { qId, dxFrac, dyFrac ->
                    updateActivePage { pg ->
                        pg.copy(
                            questions = pg.questions.map { item ->
                                if (item.id == qId) {
                                    item.copy(
                                        offsetXFraction = (item.offsetXFraction + dxFrac).coerceIn(-0.25f, 0.25f),
                                        offsetYFraction = (item.offsetYFraction + dyFrac).coerceIn(-0.25f, 0.25f)
                                    )
                                } else item
                            }
                        )
                    }
                },
                onResetOffsets = {
                    updateActivePage { pg ->
                        pg.copy(questions = pg.questions.map { it.copy(offsetXFraction = 0f, offsetYFraction = 0f) })
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }

    // Templates Picker Dialog
    if (showTemplatesDialog) {
        AlertDialog(
            onDismissRequest = { showTemplatesDialog = false },
            icon = { Icon(Icons.Filled.Style, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("قوالب الأسئلة الجاهزة") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExamPaperTemplates.presets.forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ext.cardElevated,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    val built = preset.builder(
                                        state.profile.schoolName,
                                        state.profile.subjectName,
                                        state.profile.preferredGrade.titleAr
                                    )
                                    val nextDocs = listOf(built) + docs
                                    docs = nextDocs
                                    activeDocId = built.id
                                    activePageIndex = 0
                                    showTemplatesDialog = false
                                    scope.launch { repo.saveDocuments(nextDocs) }
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(preset.titleAr, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(preset.subtitleAr, style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplatesDialog = false }) { Text("إغلاق") }
            }
        )
    }

    // Quranic Ayah Selector Dialog
    quranTarget?.let { target ->
        QuranAyahPickerDialog(
            onInsert = { formattedAyahText ->
                when (target) {
                    is QuranInsertTarget.NewQuestion -> {
                        val nextNum = currentPage.questions.size + 1
                        updateActivePage { pg ->
                            pg.copy(
                                questions = pg.questions + ExamQuestionBox(
                                    headingAbove = "القرآن الكريم والحفظ",
                                    questionTitle = "س$nextNum/",
                                    questionType = ExamQuestionType.QA,
                                    promptText = "اكتب أو أكمِل قوله تعالى:\n$formattedAyahText",
                                    marks = 15,
                                    boxColorHex = 0xFF0F766EL,
                                    headingColorHex = 0xFF0F766EL
                                )
                            )
                        }
                    }
                    is QuranInsertTarget.ExistingQuestion -> {
                        updateActivePage { pg ->
                            pg.copy(
                                questions = pg.questions.map { q ->
                                    if (q.id == target.questionId) {
                                        val nextPrompt = if (q.promptText.isBlank()) formattedAyahText else "${q.promptText}\n$formattedAyahText"
                                        q.copy(promptText = nextPrompt)
                                    } else q
                                }
                            )
                        }
                    }
                    is QuranInsertTarget.ExistingBranch -> {
                        updateActivePage { pg ->
                            pg.copy(
                                questions = pg.questions.map { q ->
                                    if (q.id == target.questionId) {
                                        q.copy(
                                            branches = q.branches.map { b ->
                                                if (b.id == target.branchId) {
                                                    val nextPrompt = if (b.prompt.isBlank()) formattedAyahText else "${b.prompt}\n$formattedAyahText"
                                                    b.copy(prompt = nextPrompt)
                                                } else b
                                            }
                                        )
                                    } else q
                                }
                            )
                        }
                    }
                }
                quranTarget = null
            },
            onDismiss = { quranTarget = null }
        )
    }
}

@Composable
private fun QuestionEditorBoxCard(
    question: ExamQuestionBox,
    index: Int,
    totalCount: Int,
    onUpdate: (ExamQuestionBox) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onInsertQuranIntoQuestion: () -> Unit,
    onInsertQuranIntoBranch: (String) -> Unit
) {
    val ext = LocalExtendedColors.current
    val boxColor = Color(question.boxColorHex)
    val headingColor = Color(question.headingColorHex)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ext.cardElevated,
        border = BorderStroke(1.8.dp, boxColor.copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Optional Group Heading Above Question
            OutlinedTextField(
                value = question.headingAbove,
                onValueChange = { onUpdate(question.copy(headingAbove = it)) },
                label = { Text("عنوان المجموعة فوق السؤال (مثلاً: الحفظ / الأحكام / السيرة)") },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = headingColor, fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth()
            )

            // Title + Marks + Up/Down/Delete row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = question.questionTitle,
                    onValueChange = { onUpdate(question.copy(questionTitle = it)) },
                    label = { Text("الرقم") },
                    singleLine = true,
                    modifier = Modifier.width(86.dp)
                )
                OutlinedTextField(
                    value = question.marks.toString(),
                    onValueChange = { txt ->
                        val parsed = txt.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 200) ?: 0
                        onUpdate(question.copy(marks = parsed))
                    },
                    label = { Text("الدرجة") },
                    singleLine = true,
                    modifier = Modifier.width(86.dp)
                )
                Spacer(Modifier.weight(1f))
                IconButton(enabled = index > 0, onClick = onMoveUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "أعلى")
                }
                IconButton(enabled = index < totalCount - 1, onClick = onMoveDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "أسفل")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف السؤال", tint = DangerRed)
                }
            }

            // Question Type Chips
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExamQuestionType.entries.forEach { qt ->
                    FilterChip(
                        selected = question.questionType == qt,
                        onClick = { onUpdate(question.copy(questionType = qt)) },
                        label = { Text(qt.titleAr, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Main Question Prompt + Quick Quran Insert button
            OutlinedTextField(
                value = question.promptText,
                onValueChange = { onUpdate(question.copy(promptText = it)) },
                label = { Text("نص السؤال") },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            // Color & Divider Row
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("لون الصندوق:", style = MaterialTheme.typography.labelSmall)
                ExamColorChoices.forEach { (_, hex) ->
                    val isSel = question.boxColorHex == hex
                    Box(
                        modifier = Modifier
                            .size(if (isSel) 26.dp else 22.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(
                                width = if (isSel) 2.5.dp else 1.dp,
                                color = if (isSel) Color.White else ext.borderSubtle,
                                shape = CircleShape
                            )
                            .clickable { onUpdate(question.copy(boxColorHex = hex, headingColorHex = hex)) }
                    )
                }
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = question.showDivider,
                    onClick = { onUpdate(question.copy(showDivider = !question.showDivider)) },
                    label = { Text("إطار حول السؤال") }
                )
                AssistChip(
                    onClick = onInsertQuranIntoQuestion,
                    leadingIcon = { Icon(Icons.Filled.AutoStories, null, modifier = Modifier.size(15.dp)) },
                    label = { Text("إدراج آية ﴿ ﴾") }
                )
            }

            // Nested Branches (أ، ب، ج...)
            if (question.branches.isNotEmpty()) {
                HorizontalDivider()
                Text("الأفرع المتداخلة:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = boxColor)
            }
            question.branches.forEach { branch ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, ext.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = branch.label,
                                onValueChange = { lbl ->
                                    onUpdate(
                                        question.copy(
                                            branches = question.branches.map { if (it.id == branch.id) it.copy(label = lbl) else it }
                                        )
                                    )
                                },
                                label = { Text("الفرع") },
                                singleLine = true,
                                modifier = Modifier.width(72.dp)
                            )
                            OutlinedTextField(
                                value = branch.marks.toString(),
                                onValueChange = { txt ->
                                    val m = txt.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 100) ?: 0
                                    onUpdate(
                                        question.copy(
                                            branches = question.branches.map { if (it.id == branch.id) it.copy(marks = m) else it }
                                        )
                                    )
                                },
                                label = { Text("الدرجة") },
                                singleLine = true,
                                modifier = Modifier.width(80.dp)
                            )
                            IconButton(onClick = { onInsertQuranIntoBranch(branch.id) }) {
                                Icon(Icons.Filled.AutoStories, contentDescription = "إدراج آية بالفرع", tint = SuccessGreen)
                            }
                            IconButton(onClick = {
                                onUpdate(question.copy(branches = question.branches.filterNot { it.id == branch.id }))
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "حذف الفرع", tint = DangerRed)
                            }
                        }
                        OutlinedTextField(
                            value = branch.prompt,
                            onValueChange = { pTxt ->
                                onUpdate(
                                    question.copy(
                                        branches = question.branches.map { if (it.id == branch.id) it.copy(prompt = pTxt) else it }
                                    )
                                )
                            },
                            label = { Text("نص الفرع (${branch.label})") },
                            minLines = 1,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    val arabicLabels = listOf("أ", "ب", "ج", "د", "هـ", "و")
                    val nextLabel = arabicLabels.getOrElse(question.branches.size) { "${question.branches.size + 1}" }
                    onUpdate(
                        question.copy(
                            branches = question.branches + ExamBranchItem(
                                label = nextLabel,
                                prompt = "",
                                marks = 5
                            )
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.SubdirectoryArrowLeft, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("إضافة فرع جديد (أ / ب / ج)")
            }
        }
    }
}

@Composable
private fun ExamLivePreviewPane(
    doc: ExamPaperDocument,
    page: ExamPageData,
    pageIndex: Int,
    customFrameFile: java.io.File?,
    onUpdateQuestionOffset: (String, Float, Float) -> Unit,
    onResetOffsets: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = doc.typography
    val textAlign = when (t.alignment) {
        ExamTextAlign.RIGHT -> TextAlign.Right
        ExamTextAlign.CENTER -> TextAlign.Center
        ExamTextAlign.LEFT -> TextAlign.Left
    }
    val customFrameBmp = remember(customFrameFile?.absolutePath, customFrameFile?.lastModified()) {
        customFrameFile?.takeIf { it.exists() }?.let { f ->
            runCatching { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull()
        }
    }
    val aspect = if (doc.orientation == ExamPaperOrientation.PORTRAIT) {
        doc.paperSize.widthPt.toFloat() / doc.paperSize.heightPt.toFloat()
    } else {
        doc.paperSize.heightPt.toFloat() / doc.paperSize.widthPt.toFloat()
    }
    var paperPixelSize by remember { mutableStateOf(IntSize(1000, 1400)) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (doc.freeManualDragMode) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "وضع السحب اليدوي مفعّل: اسحب أي سؤال داخل الورقة",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onResetOffsets) {
                        Text("إعادة ضبط")
                    }
                }
            }
        }

        // Optional Cover Page Miniature Preview
        if (doc.showCoverPage) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(2.dp, Color(0xFF1E3A8A)),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("غلاف الأسئلة", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                    Text(doc.ministryHeader, fontFamily = t.fontOption.family, color = Color(0xFF1E3A8A), fontWeight = FontWeight.Bold)
                    Text(doc.schoolName, fontFamily = t.fontOption.family, color = Color(0xFF0F766E), fontWeight = FontWeight.Bold)
                    Text(doc.docTitle, fontFamily = t.fontOption.family, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                    Text("المادة: ${doc.subjectName} | الصف: ${doc.gradeAndSection}", fontFamily = t.fontOption.family, color = Color(0xFF334155))
                }
            }
        }

        // Optional Table of Contents Preview
        if (doc.showTableOfContents) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("فهرس المحتويات والأسئلة", fontFamily = t.fontOption.family, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                    doc.pages.forEachIndexed { pIdx, pItem ->
                        val headings = pItem.questions.mapNotNull { it.headingAbove.takeIf(String::isNotBlank) }.joinToString(" • ")
                        Text(
                            "• ${pItem.pageTitle}: ${headings.ifBlank { "${pItem.questions.size} أسئلة" }}",
                            fontFamily = t.fontOption.family,
                            fontSize = 12.sp,
                            color = Color(0xFF334155)
                        )
                    }
                }
            }
        }

        // Active Print Paper Preview
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect.coerceIn(0.6f, 1.6f), matchHeightConstraintsFirst = false)
                .heightIn(min = 520.dp)
                .onSizeChanged { paperPixelSize = it }
                .testTag("exam_writer_paper_preview")
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (doc.frameStyle == ExamFrameStyle.CUSTOM_PNG && customFrameBmp != null) {
                    Image(
                        bitmap = customFrameBmp,
                        contentDescription = "إطار مخصص",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        when (doc.frameStyle) {
                            ExamFrameStyle.NONE -> Unit
                            ExamFrameStyle.CLASSIC_DOUBLE -> {
                                drawRect(Color(0xFF1E3A8A), topLeft = Offset(12f, 12f), size = Size(size.width - 24f, size.height - 24f), style = Stroke(3f))
                                drawRect(Color(0xFF64748B), topLeft = Offset(20f, 20f), size = Size(size.width - 40f, size.height - 40f), style = Stroke(1.2f))
                            }
                            ExamFrameStyle.ROYAL_ORNAMENT -> {
                                drawRoundRect(Color(0xFFB45309), topLeft = Offset(12f, 12f), size = Size(size.width - 24f, size.height - 24f), cornerRadius = CornerRadius(18f, 18f), style = Stroke(3.5f))
                                drawRoundRect(Color(0xFF0F766E), topLeft = Offset(22f, 22f), size = Size(size.width - 44f, size.height - 44f), cornerRadius = CornerRadius(12f, 12f), style = Stroke(1.4f))
                            }
                            ExamFrameStyle.MODERN_MINIMAL -> {
                                drawRoundRect(Color(0xFF0284C7), topLeft = Offset(16f, 16f), size = Size(size.width - 32f, size.height - 32f), cornerRadius = CornerRadius(10f, 10f), style = Stroke(2.2f))
                            }
                            ExamFrameStyle.CUSTOM_PNG -> {
                                drawRect(Color(0xFF334155), topLeft = Offset(14f, 14f), size = Size(size.width - 28f, size.height - 28f), style = Stroke(2f))
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = t.marginHorizontalDp.dp,
                            vertical = t.marginTopDp.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(t.paragraphSpacingDp.dp)
                ) {
                    // Header inside paper
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "${doc.ministryHeader}  —  ${doc.schoolName}",
                            fontFamily = t.fontOption.family,
                            fontSize = (t.baseFontSizeSp - 2f).coerceAtLeast(10f).sp,
                            color = Color(0xFF334155),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            doc.docTitle,
                            fontFamily = t.fontOption.family,
                            fontSize = t.headingFontSizeSp.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "المادة: ${doc.subjectName} | الصف: ${doc.gradeAndSection} | الوقت: ${doc.examDuration}",
                            fontFamily = t.fontOption.family,
                            fontSize = (t.baseFontSizeSp - 2f).coerceAtLeast(10f).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E3A8A),
                            textAlign = TextAlign.Center
                        )
                        if (doc.totalMarksNote.isNotBlank()) {
                            Text(
                                doc.totalMarksNote,
                                fontFamily = t.fontOption.family,
                                fontSize = (t.baseFontSizeSp - 3f).coerceAtLeast(9f).sp,
                                color = Color(0xFF475569),
                                textAlign = TextAlign.Center
                            )
                        }
                        HorizontalDivider(color = Color(0xFF1E3A8A), thickness = 1.2.dp, modifier = Modifier.padding(top = 4.dp))
                    }

                    // Questions
                    page.questions.forEach { q ->
                        val dragModifier = if (doc.freeManualDragMode) {
                            Modifier
                                .offset {
                                    IntOffset(
                                        (q.offsetXFraction * paperPixelSize.width).roundToInt(),
                                        (q.offsetYFraction * paperPixelSize.height).roundToInt()
                                    )
                                }
                                .pointerInput(q.id, paperPixelSize) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val w = paperPixelSize.width.coerceAtLeast(1).toFloat()
                                        val h = paperPixelSize.height.coerceAtLeast(1).toFloat()
                                        onUpdateQuestionOffset(q.id, dragAmount.x / w, dragAmount.y / h)
                                    }
                                }
                        } else Modifier

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(dragModifier),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (q.headingAbove.isNotBlank()) {
                                Text(
                                    text = q.headingAbove,
                                    fontFamily = t.fontOption.family,
                                    fontSize = (t.headingFontSizeSp - 2f).coerceAtLeast(12f).sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(q.headingColorHex),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Transparent,
                                border = if (q.showDivider) BorderStroke(1.2.dp, Color(q.boxColorHex)) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    val baseStyle = TextStyle(
                                        fontFamily = t.fontOption.family,
                                        fontSize = (t.baseFontSizeSp * 0.85f).sp,
                                        fontWeight = if (t.isBold) FontWeight.Bold else FontWeight.SemiBold,
                                        fontStyle = if (t.isItalic) FontStyle.Italic else FontStyle.Normal,
                                        textDecoration = if (t.isUnderlined) TextDecoration.Underline else TextDecoration.None,
                                        color = Color(q.textColorHex),
                                        lineHeight = (t.baseFontSizeSp * t.lineHeightMultiplier * 0.85f).sp,
                                        textAlign = textAlign
                                    )
                                    Text(
                                        text = "${q.questionTitle} ${q.promptText}   [${q.marks} درجة]",
                                        style = baseStyle,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    q.branches.forEach { b ->
                                        Text(
                                            text = "   ${b.label}) ${b.prompt}  (${b.marks} د)",
                                            style = baseStyle.copy(
                                                fontWeight = FontWeight.Normal,
                                                color = Color(b.textColorHex)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    if (page.footerNote.isNotBlank()) {
                        Text(
                            text = page.footerNote,
                            fontFamily = t.fontOption.family,
                            fontSize = 10.sp,
                            fontStyle = FontStyle.Italic,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (doc.showPageNumbers) {
                        Text(
                            text = "صفحة ${pageIndex + 1} من ${doc.pages.size}",
                            fontFamily = t.fontOption.family,
                            fontSize = 9.5.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuranAyahPickerDialog(
    onInsert: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val allSurahs = remember { QuranExamHelper.allSurahs() }
    var search by remember { mutableStateOf("") }
    val filteredSurahs = remember(search) { QuranExamHelper.searchSurahs(search) }
    var selectedSurah by remember { mutableStateOf<QuranSurah?>(allSurahs.firstOrNull()) }
    var fromAyahText by remember { mutableStateOf("1") }
    var toAyahText by remember { mutableStateOf("3") }
    var withTashkeel by remember { mutableStateOf(true) }

    val previewText = remember(selectedSurah, fromAyahText, toAyahText, withTashkeel) {
        val s = selectedSurah ?: return@remember ""
        val from = fromAyahText.toIntOrNull()?.coerceIn(1, s.ayahs.size) ?: 1
        val to = toAyahText.toIntOrNull()?.coerceIn(1, s.ayahs.size) ?: from
        QuranExamHelper.formatAyahRange(
            surah = s,
            fromAyah = from,
            toAyah = to,
            withTashkeel = withTashkeel
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.AutoStories, null, tint = SuccessGreen) },
        title = { Text("إدراج آية قرآنية في الأسئلة") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("ابحث عن اسم السورة أو رقمها") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filteredSurahs.take(25).forEach { surah ->
                        FilterChip(
                            selected = selectedSurah?.id == surah.id,
                            onClick = {
                                selectedSurah = surah
                                fromAyahText = "1"
                                toAyahText = minOf(3, surah.ayahs.size).toString()
                            },
                            label = { Text("${surah.id}. ${surah.nameArabic}") }
                        )
                    }
                }
                selectedSurah?.let { s ->
                    Text(
                        "سورة ${s.nameArabic} (عدد الآيات: ${s.ayahs.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fromAyahText,
                            onValueChange = { fromAyahText = it.filter(Char::isDigit) },
                            label = { Text("من آية") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = toAyahText,
                            onValueChange = { toAyahText = it.filter(Char::isDigit) },
                            label = { Text("إلى آية") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = withTashkeel,
                            onClick = { withTashkeel = true },
                            label = { Text("رسم بالتشكيل الكامل") }
                        )
                        FilterChip(
                            selected = !withTashkeel,
                            onClick = { withTashkeel = false },
                            label = { Text("نص مبسط بدون تشكيل") }
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = previewText.isNotBlank(),
                onClick = { onInsert(previewText) }
            ) {
                Text("إدراج داخل السؤال")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
