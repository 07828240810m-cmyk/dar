package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.model.*
import java.io.File
import java.io.FileOutputStream

private val paperThemeColors = listOf(
    0xFF1E88E5L,
    0xFF00897BL,
    0xFF8E24AAL,
    0xFFD81B60L,
    0xFFF4511EL,
    0xFF3949ABL
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentPapersScreen(
    state: AppState,
    selectedGrade: GradeLevel,
    selectedSection: SectionLetter,
    getPaperFile: (String) -> File,
    onAddPaperGroup: (String, String, GradeLevel, SectionLetter, String, String, List<Uri>, Long) -> Boolean,
    onAddMorePages: (String, List<Uri>) -> Boolean,
    onDeletePaperGroup: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var viewingGroup by remember { mutableStateOf<StudentPaperGroup?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var appendTargetGroupId by remember { mutableStateOf<String?>(null) }

    val appendPhotosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        val targetId = appendTargetGroupId
        if (targetId != null && uris.isNotEmpty()) {
            val ok = onAddMorePages(targetId, uris)
            if (ok) {
                viewingGroup = state.studentPapers.find { it.id == targetId }
                Toast.makeText(context, "تمت إضافة ${uris.size} صفحة بنجاح", Toast.LENGTH_SHORT).show()
            }
        }
        appendTargetGroupId = null
    }

    val filteredGroups = remember(state.studentPapers, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) state.studentPapers
        else state.studentPapers.filter {
            it.studentName.contains(q, true) || it.notes.contains(q, true) || it.scoreText.contains(q, true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("student_papers_screen")
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("أوراق إجابات الطلبة", fontWeight = FontWeight.Bold)
                    Text(
                        "حفظ وتوثيق أوراق الامتحانات بملفات متعددة الصفحات",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("papers_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                }
            },
            actions = {
                FilledTonalButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.padding(end = 8.dp).testTag("add_paper_group_btn")
                ) {
                    Icon(Icons.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إضافة ورقة")
                }
            }
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("ابحث باسم الطالب أو الدرجة أو الملاحظات...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (filteredGroups.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Filled.CollectionsBookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text("لا توجد أوراق محفوظة حالياً", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "يمكنك اختيار ورقتين أو أكثر لكل طالب (صفحة أولى وثانية...) وعرضها بشكل كتاب PDF أنيق مع الدرجة والملاحظات ومشاركتها مع ولي الأمر.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إضافة أول ملف أوراق طالب")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredGroups, key = { it.id }) { group ->
                    val accent = Color(group.themeColorHex)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewingGroup = group }
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(accent.copy(alpha = 0.16f))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = group.studentName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${group.gradeLevel.shortAr} (${group.section.id}) • ${group.pageFileNames.size} صفحات",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accent
                                        )
                                    }
                                    if (group.scoreText.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = accent
                                        ) {
                                            Text(
                                                text = "الدرجة: ${group.scoreText}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelLarge,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (group.notes.isNotBlank()) {
                                    Text(
                                        text = "ملاحظات المدرس: ${group.notes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { viewingGroup = group },
                                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("عرض كملف PDF", color = Color.White)
                                    }
                                    IconButton(
                                        onClick = {
                                            appendTargetGroupId = group.id
                                            appendPhotosLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "إضافة صفحات", tint = accent)
                                    }
                                    IconButton(onClick = { onDeletePaperGroup(group.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AddStudentPaperDialog(
            students = state.students,
            defaultGrade = selectedGrade,
            defaultSection = selectedSection,
            onDismiss = { showCreateDialog = false },
            onSave = { studentId, name, grade, sec, score, notes, uris, colorHex ->
                val ok = onAddPaperGroup(studentId, name, grade, sec, score, notes, uris, colorHex)
                if (ok) {
                    Toast.makeText(context, "تم حفظ أوراق $name (${uris.size} صفحات)", Toast.LENGTH_SHORT).show()
                    showCreateDialog = false
                } else {
                    Toast.makeText(context, "يرجى إدخال الاسم واختيار صورة واحدة على الأقل", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    viewingGroup?.let { group ->
        val freshGroup = state.studentPapers.find { it.id == group.id } ?: group
        StudentPaperBookViewerDialog(
            group = freshGroup,
            getPaperFile = getPaperFile,
            onAddPages = {
                appendTargetGroupId = freshGroup.id
                appendPhotosLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onSharePdf = {
                shareStudentPaperPdf(context, freshGroup, getPaperFile)
            },
            onDismiss = { viewingGroup = null }
        )
    }
}

@Composable
private fun AddStudentPaperDialog(
    students: List<Student>,
    defaultGrade: GradeLevel,
    defaultSection: SectionLetter,
    onDismiss: () -> Unit,
    onSave: (String, String, GradeLevel, SectionLetter, String, String, List<Uri>, Long) -> Unit
) {
    val context = LocalContext.current
    var grade by remember { mutableStateOf(defaultGrade) }
    var section by remember { mutableStateOf(defaultSection) }
    val sectionStudents = remember(students, grade, section) {
        students.filter { it.gradeLevel == grade && it.section == section && !it.isTransferred }
    }
    var selectedStudentId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var score by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedColor by remember { mutableStateOf(paperThemeColors.first()) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) selectedUris = selectedUris + uris
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = tempCameraUri
        if (ok && uri != null) {
            selectedUris = selectedUris + uri
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("حفظ أوراق إجابة طالب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(GradeLevel.entries) { g ->
                        FilterChip(selected = grade == g, onClick = { grade = g }, label = { Text(g.shortAr) })
                    }
                    items(SectionLetter.entries) { s ->
                        FilterChip(selected = section == s, onClick = { section = s }, label = { Text(s.id) })
                    }
                }

                if (sectionStudents.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sectionStudents.take(25)) { st ->
                            AssistChip(
                                onClick = {
                                    selectedStudentId = st.id
                                    name = st.fullName
                                },
                                label = { Text(st.fullName, maxLines = 1) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الطالب") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = score,
                    onValueChange = { score = it },
                    label = { Text("الدرجة (مثال: 94 / 100)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات على الورقة") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اختيار صور (${selectedUris.size})")
                    }
                    OutlinedButton(
                        onClick = {
                            val dir = File(context.cacheDir, "cam_tmp").apply { mkdirs() }
                            val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            tempCameraUri = uri
                            cameraLauncher.launch(uri)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصوير صفحة")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("لون الغلاف:", style = MaterialTheme.typography.labelMedium)
                    paperThemeColors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(if (selectedColor == hex) 2.5.dp else 0.dp, Color.White, CircleShape)
                                .clickable { selectedColor = hex }
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                    Button(
                        onClick = { onSave(selectedStudentId, name, grade, section, score, notes, selectedUris, selectedColor) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("حفظ (${selectedUris.size} ص)")
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentPaperBookViewerDialog(
    group: StudentPaperGroup,
    getPaperFile: (String) -> File,
    onAddPages: () -> Unit,
    onSharePdf: () -> Unit,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { group.pageFileNames.size.coerceAtLeast(1) })
    val accent = Color(group.themeColorHex)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(color = accent) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "إغلاق", tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group.studentName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "صفحة ${pagerState.currentPage + 1} من ${group.pageFileNames.size}" +
                                    if (group.scoreText.isNotBlank()) " • الدرجة: ${group.scoreText}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.92f)
                            )
                        }
                        IconButton(onClick = onAddPages) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "إضافة صفحة", tint = Color.White)
                        }
                        IconButton(onClick = onSharePdf) {
                            Icon(Icons.Filled.Share, contentDescription = "مشاركة PDF", tint = Color.White)
                        }
                    }
                }
            }
        ) { pad ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
            ) {
                if (group.notes.isNotBlank()) {
                    Surface(
                        color = accent.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ملاحظات: ${group.notes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { pageIdx ->
                    val fileName = group.pageFileNames.getOrNull(pageIdx)
                    val file = fileName?.let(getPaperFile)
                    val bmp = remember(file?.absolutePath) {
                        file?.takeIf { it.exists() }?.let {
                            runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull()
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, accent),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "صفحة ${pageIdx + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("تعذر تحميل الصفحة", color = Color.DarkGray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shareStudentPaperPdf(
    context: android.content.Context,
    group: StudentPaperGroup,
    getPaperFile: (String) -> File
) {
    try {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val headerPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 16f
            isFakeBoldText = true
        }
        val subPaint = Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 12f
        }

        group.pageFileNames.forEachIndexed { idx, fileName ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, idx + 1).create()
            val page = doc.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            canvas.drawColor(android.graphics.Color.WHITE)

            canvas.drawText("الطالب: ${group.studentName} | ${group.gradeLevel.shortAr} (${group.section.id})", 32f, 36f, headerPaint)
            canvas.drawText(
                "الدرجة: ${group.scoreText.ifBlank { "—" }}   |   صفحة ${idx + 1} من ${group.pageFileNames.size}",
                32f,
                56f,
                subPaint
            )
            if (group.notes.isNotBlank()) {
                canvas.drawText("ملاحظات: ${group.notes.take(80)}", 32f, 74f, subPaint)
            }

            val imgFile = getPaperFile(fileName)
            val bmp = runCatching { BitmapFactory.decodeFile(imgFile.absolutePath) }.getOrNull()
            if (bmp != null) {
                val availW = pageWidth - 48f
                val availH = pageHeight - 108f
                val scale = minOf(availW / bmp.width, availH / bmp.height)
                val targetW = (bmp.width * scale).toInt().coerceAtLeast(1)
                val targetH = (bmp.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
                val left = (pageWidth - targetW) / 2f
                canvas.drawBitmap(scaled, left, 90f, null)
            }
            doc.finishPage(page)
        }

        val outDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outFile = File(outDir, "paper_${group.id}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة أوراق ${group.studentName}"))
    } catch (_: Exception) {
        Toast.makeText(context, "تعذر إنشاء ملف PDF للمشاركة", Toast.LENGTH_SHORT).show()
    }
}
