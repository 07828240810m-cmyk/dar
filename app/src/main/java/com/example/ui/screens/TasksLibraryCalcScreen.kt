package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.SectionHeaderWithInfo
import com.example.ui.components.neonCardGlow
import com.example.ui.theme.*
import com.example.ui.viewmodel.AnnotationPoint
import com.example.ui.viewmodel.AnnotationStroke
import com.example.utils.AlertSoundHelper
import com.example.utils.GradeCalculator
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun TasksAndChaptersScreen(
    state: AppState,
    onAddTask: (String, String) -> Unit,
    onToggleTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onAddChapter: (GradeLevel, String) -> Unit,
    onToggleChapter: (String) -> Unit,
    onDeleteChapter: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val ext = LocalExtendedColors.current
    var activeTab by remember { mutableStateOf(0) } // 0=Tasks, 1=Curriculum Chapters

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.primary)
            }
            SectionHeaderWithInfo(
                title = "المهام وإنجاز الفصول",
                infoTitle = "متابعة المهام والمنهج",
                infoDescription = "أضف مهامك اليومية والشهرية وتابع إنجازاتك، كما يمكنك تسجيل أسماء فصول المنهج والتأشير على كل فصل منجز لحساب نسبة إكمال المنهج."
            )
        }

        TabRow(selectedTabIndex = activeTab) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("مهام وإنجازات المدرس") })
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("فصول المنهج") })
        }
        Spacer(Modifier.height(10.dp))

        if (activeTab == 0) {
            var taskText by remember { mutableStateOf("") }
            var taskCategory by remember { mutableStateOf("يومي") }
            val doneCount = state.tasks.count { it.isDone }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.dp, ext.borderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .neonCardGlow(MaterialTheme.colorScheme.primary, ext.neonGlowEnabled, 16.dp, alphaFactor = 0.22f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("الإنجاز: $doneCount من ${state.tasks.size} مهمة", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("يومي", "شهري", "إنجاز").forEach { cat ->
                                    FilterChip(selected = taskCategory == cat, onClick = { taskCategory = cat }, label = { Text(cat) })
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = taskText,
                                    onValueChange = { taskText = it },
                                    placeholder = { Text("اكتب مهمة جديدة...") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(onClick = {
                                    onAddTask(taskText, taskCategory)
                                    taskText = ""
                                }) { Text("إضافة") }
                            }
                        }
                    }
                }

                items(state.tasks, key = { it.id }) { task ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.dp, if (task.isDone) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else ext.borderSubtle),
                        modifier = Modifier.fillMaxWidth().clickable { onToggleTask(task.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = task.isDone, onCheckedChange = { onToggleTask(task.id) })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None
                                )
                                Text(task.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onDeleteTask(task.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = DangerRed)
                            }
                        }
                    }
                }
            }
        } else {
            var chGrade by remember { mutableStateOf(state.profile.preferredGrade) }
            var chTitle by remember { mutableStateOf("") }
            val gradeChapters = state.chapters.filter { it.gradeLevel == chGrade }
            val completed = gradeChapters.count { it.isCompleted }
            val progress = if (gradeChapters.isNotEmpty()) completed.toFloat() / gradeChapters.size else 0f

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradeLevel.entries.forEach { g ->
                            FilterChip(selected = g == chGrade, onClick = { chGrade = g }, label = { Text(g.shortAr) })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("المُنجز: $completed من ${gradeChapters.size} فصل", style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = chTitle,
                            onValueChange = { chTitle = it },
                            placeholder = { Text("عنوان الفصل الجديد...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            onAddChapter(chGrade, chTitle)
                            chTitle = ""
                        }) { Text("إضافة") }
                    }
                }

                items(gradeChapters, key = { it.id }) { ch ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.dp, if (ch.isCompleted) MaterialTheme.colorScheme.primary else ext.borderSubtle),
                        modifier = Modifier.fillMaxWidth().clickable { onToggleChapter(ch.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = ch.isCompleted, onCheckedChange = { onToggleChapter(ch.id) })
                            Text(
                                text = ch.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (ch.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDeleteChapter(ch.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = DangerRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryBooksScreen(
    state: AppState,
    getLibraryFile: (LibraryFileItem) -> File,
    onImportFile: (Uri, String, String) -> Boolean,
    onDeleteFile: (String) -> Unit,
    onUpdateFilePage: (String, Int) -> Unit,
    loadPageAnnotations: (String, Int) -> List<AnnotationStroke>,
    savePageAnnotations: (String, Int, List<AnnotationStroke>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    var viewingFileId by remember { mutableStateOf<String?>(null) }
    val viewingFile = remember(state.libraryFiles, viewingFileId) {
        state.libraryFiles.find { it.id == viewingFileId }
    }

    BackHandler {
        if (viewingFile != null) viewingFileId = null else onBack()
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var name = "ملف_دراسي"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx) ?: name
                }
            }
            val mime = context.contentResolver.getType(uri) ?: "application/pdf"
            onImportFile(uri, name, mime)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (viewingFile != null) viewingFileId = null else onBack() },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.primary)
            }
            SectionHeaderWithInfo(
                title = viewingFile?.displayName ?: "مكتبة الكتب والملفات (${state.libraryFiles.size}/5)",
                infoTitle = "قارئ الكتب والتأشير وحفظ الصفحة",
                infoDescription = "استعرض حتى 5 كتب أو ملازم PDF وصور أوفلاين، وسيتم حفظ آخر صفحة توقفت عندها تلقائياً عند العودة، مع أداة تأشير وتظليل والكتابة فوق الصفحات."
            )
        }

        if (viewingFile != null) {
            val file = getLibraryFile(viewingFile)
            InternalFileViewer(
                item = viewingFile,
                file = file,
                onUpdateFilePage = { newPage -> onUpdateFilePage(viewingFile.id, newPage) },
                loadPageAnnotations = { pageIdx -> loadPageAnnotations(viewingFile.id, pageIdx) },
                savePageAnnotations = { pageIdx, strokes -> savePageAnnotations(viewingFile.id, pageIdx, strokes) }
            )
        } else {
            Spacer(Modifier.height(10.dp))
            Button(
                enabled = state.libraryFiles.size < 5,
                onClick = { openDocLauncher.launch(arrayOf("application/pdf", "image/*", "text/*")) },
                modifier = Modifier.fillMaxWidth().testTag("import_book_btn")
            ) {
                Icon(Icons.Filled.UploadFile, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.libraryFiles.size < 5) "إضافة كتاب أو ملف (متاح ${5 - state.libraryFiles.size})"
                    else "تم الوصول للحد الأقصى (5 ملفات)"
                )
            }
            Spacer(Modifier.height(12.dp))
            if (state.libraryFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.MenuBook, contentDescription = null, tint = ext.textMuted, modifier = Modifier.size(56.dp))
                        Text("لا توجد كتب مضافة بعد", style = MaterialTheme.typography.titleMedium, color = ext.textMuted)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(state.libraryFiles, key = { it.id }) { item ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ext.cardElevated,
                            border = BorderStroke(1.dp, ext.borderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .neonCardGlow(MaterialTheme.colorScheme.primary, ext.neonGlowEnabled, 16.dp, alphaFactor = 0.22f)
                                .clickable { viewingFileId = item.id }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(item.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = "آخر صفحة: ${item.lastPageIndex + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                item.mimeType,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ext.textMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                IconButton(onClick = { onDeleteFile(item.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "حذف الملف", tint = DangerRed)
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
private fun InternalFileViewer(
    item: LibraryFileItem,
    file: File,
    onUpdateFilePage: (Int) -> Unit,
    loadPageAnnotations: (Int) -> List<AnnotationStroke>,
    savePageAnnotations: (Int, List<AnnotationStroke>) -> Unit
) {
    val ext = LocalExtendedColors.current
    if (!file.exists()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("تعذر العثور على الملف الداخلي")
        }
        return
    }

    val isPdf = item.mimeType.contains("pdf", ignoreCase = true) || file.name.endsWith(".pdf", ignoreCase = true)
    val isImage = item.mimeType.startsWith("image/")

    if (isPdf || isImage) {
        var pageIndex by remember(item.id) { mutableStateOf(item.lastPageIndex.coerceAtLeast(0)) }
        var pageCount by remember(item.id) { mutableStateOf(if (isImage) 1 else 0) }
        var pageBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
        var showJumpDialog by remember { mutableStateOf(false) }

        // Annotation state per current page
        var drawEnabled by remember { mutableStateOf(false) }
        var isHighlighter by remember { mutableStateOf(false) }
        var selectedColor by remember { mutableStateOf(0xFF40D6B4L) } // Turquoise default
        val colorChoices = listOf(0xFF40D6B4L, 0xFFEF4444L, 0xFFFACC15L, 0xFF38BDF8L)
        var strokes by remember(item.id, pageIndex) { mutableStateOf(loadPageAnnotations(pageIndex)) }
        var currentPoints by remember(item.id, pageIndex) { mutableStateOf<List<AnnotationPoint>>(emptyList()) }

        LaunchedEffect(item.id, pageIndex) {
            strokes = loadPageAnnotations(pageIndex)
            currentPoints = emptyList()
        }

        if (isPdf) {
            DisposableEffect(file, pageIndex) {
                var pfd: ParcelFileDescriptor? = null
                var renderer: PdfRenderer? = null
                try {
                    pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(pfd)
                    val total = renderer.pageCount
                    pageCount = total
                    if (total > 0) {
                        val safeIdx = pageIndex.coerceIn(0, total - 1)
                        if (safeIdx != pageIndex) {
                            pageIndex = safeIdx
                            onUpdateFilePage(safeIdx)
                        }
                        val page = renderer.openPage(safeIdx)
                        val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        pageBitmap = bmp
                    }
                } catch (_: Exception) {
                }
                onDispose {
                    renderer?.close()
                    pfd?.close()
                }
            }
        } else {
            LaunchedEffect(file) {
                pageBitmap = BitmapFactory.decodeFile(file.absolutePath)
                pageCount = 1
                pageIndex = 0
            }
        }

        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Top pagination + Annotation toggle bar
            if (isPdf) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        enabled = pageIndex > 0,
                        onClick = {
                            val next = (pageIndex - 1).coerceAtLeast(0)
                            pageIndex = next
                            onUpdateFilePage(next)
                        }
                    ) { Text("السابق") }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable { showJumpDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "صفحة ${pageIndex + 1} من ${pageCount.coerceAtLeast(1)}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(Icons.Filled.Pin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }

                    Button(
                        enabled = pageIndex < pageCount - 1,
                        onClick = {
                            val next = (pageIndex + 1).coerceAtMost((pageCount - 1).coerceAtLeast(0))
                            pageIndex = next
                            onUpdateFilePage(next)
                        }
                    ) { Text("التالي") }
                }
            }

            // Annotation Toolbar
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ext.cardElevated,
                border = BorderStroke(1.dp, if (drawEnabled) MaterialTheme.colorScheme.primary else ext.borderSubtle),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = drawEnabled,
                        onClick = { drawEnabled = !drawEnabled },
                        leadingIcon = { Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp)) },
                        label = { Text(if (drawEnabled) "إيقاف الكتابة" else "تأشير وكتابة") }
                    )
                    if (drawEnabled) {
                        FilterChip(
                            selected = !isHighlighter,
                            onClick = { isHighlighter = false },
                            label = { Text("قلم") }
                        )
                        FilterChip(
                            selected = isHighlighter,
                            onClick = { isHighlighter = true },
                            label = { Text("تظليل") }
                        )
                        colorChoices.forEach { argb ->
                            val c = Color(argb)
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (selectedColor == argb) 2.5.dp else 1.dp,
                                        color = if (selectedColor == argb) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = argb }
                            )
                        }
                        IconButton(
                            enabled = strokes.isNotEmpty(),
                            onClick = {
                                val updated = strokes.dropLast(1)
                                strokes = updated
                                savePageAnnotations(pageIndex, updated)
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "تراجع")
                        }
                        IconButton(
                            enabled = strokes.isNotEmpty(),
                            onClick = {
                                strokes = emptyList()
                                savePageAnnotations(pageIndex, emptyList())
                            }
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "مسح تأشيرات الصفحة", tint = DangerRed)
                        }
                    }
                }
            }

            // Page Canvas with 1:1 normalized aspect ratio alignment
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                pageBitmap?.let { bmp ->
                    val ratio = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()).coerceIn(0.35f, 2.5f)
                    Box(
                        modifier = Modifier
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "صفحة الكتاب",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (drawEnabled) {
                                        Modifier.pointerInput(item.id, pageIndex, selectedColor, isHighlighter) {
                                            detectDragGestures(
                                                onDragStart = { startOffset ->
                                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                                    currentPoints = listOf(
                                                        AnnotationPoint(
                                                            (startOffset.x / w).coerceIn(0f, 1f),
                                                            (startOffset.y / h).coerceIn(0f, 1f)
                                                        )
                                                    )
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                                    val pt = AnnotationPoint(
                                                        (change.position.x / w).coerceIn(0f, 1f),
                                                        (change.position.y / h).coerceIn(0f, 1f)
                                                    )
                                                    currentPoints = currentPoints + pt
                                                },
                                                onDragEnd = {
                                                    if (currentPoints.isNotEmpty()) {
                                                        val newStroke = AnnotationStroke(
                                                            colorArgb = selectedColor,
                                                            strokeWidthDp = if (isHighlighter) 14f else 3.5f,
                                                            isHighlighter = isHighlighter,
                                                            points = currentPoints
                                                        )
                                                        val updated = strokes + newStroke
                                                        strokes = updated
                                                        currentPoints = emptyList()
                                                        savePageAnnotations(pageIndex, updated)
                                                    }
                                                },
                                                onDragCancel = {
                                                    currentPoints = emptyList()
                                                }
                                            )
                                        }
                                    } else Modifier
                                )
                        ) {
                            fun drawStrokeList(st: AnnotationStroke) {
                                if (st.points.isEmpty()) return
                                val col = Color(st.colorArgb).copy(alpha = if (st.isHighlighter) 0.36f else 0.95f)
                                val widthPx = st.strokeWidthDp.dp.toPx()
                                if (st.points.size == 1) {
                                    val pt = st.points.first()
                                    drawCircle(
                                        color = col,
                                        radius = widthPx / 2f,
                                        center = Offset(pt.x * size.width, pt.y * size.height)
                                    )
                                } else {
                                    val path = Path()
                                    st.points.forEachIndexed { i, pt ->
                                        val px = pt.x * size.width
                                        val py = pt.y * size.height
                                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                    }
                                    drawPath(
                                        path = path,
                                        color = col,
                                        style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                            }
                            strokes.forEach { drawStrokeList(it) }
                            if (currentPoints.isNotEmpty()) {
                                drawStrokeList(
                                    AnnotationStroke(
                                        colorArgb = selectedColor,
                                        strokeWidthDp = if (isHighlighter) 14f else 3.5f,
                                        isHighlighter = isHighlighter,
                                        points = currentPoints
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showJumpDialog) {
            var jumpInput by remember { mutableStateOf((pageIndex + 1).toString()) }
            AlertDialog(
                onDismissRequest = { showJumpDialog = false },
                title = { Text("انتقال إلى صفحة") },
                text = {
                    OutlinedTextField(
                        value = jumpInput,
                        onValueChange = { if (it.all { ch -> ch.isDigit() }) jumpInput = it },
                        label = { Text("رقم الصفحة (1 - ${pageCount.coerceAtLeast(1)})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        val targetOneBased = jumpInput.toIntOrNull() ?: (pageIndex + 1)
                        val safe = (targetOneBased - 1).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                        pageIndex = safe
                        onUpdateFilePage(safe)
                        showJumpDialog = false
                    }) { Text("انتقال") }
                },
                dismissButton = {
                    TextButton(onClick = { showJumpDialog = false }) { Text("إلغاء") }
                }
            )
        }
    } else {
        val text = remember(file) { runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("صيغة ملف غير نصية") }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            item { Text(text, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
fun QuickNotesAndTimerScreen(
    state: AppState,
    onAddQuickNote: (String, String) -> Unit,
    onDeleteQuickNote: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val ext = LocalExtendedColors.current

    var noteTitle by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf("") }

    // Simple classroom countdown timer
    var totalSeconds by remember { mutableStateOf(180) }
    var remainingSeconds by remember { mutableStateOf(180) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
            if (remainingSeconds == 0) {
                running = false
                AlertSoundHelper.playGentleSixSecondAlert(context)
            }
        }
    }

    val topStudents = remember(state.students, state.gradebookSettings) {
        state.students.mapNotNull { st ->
            val res = GradeCalculator.compute(st, state.gradebookSettings)
            val score = res.finalGrade ?: res.annualEffort ?: return@mapNotNull null
            Triple(st, score, res)
        }.sortedByDescending { it.second }.take(5)
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.primary)
            }
            SectionHeaderWithInfo(
                title = "مفكرة الحصة والمؤقت البسيط",
                infoTitle = "أدوات مساعدة صفية بسيطة",
                infoDescription = "مؤقت بسيط لأنشطة الطلاب داخل الصف، ومفكرة لتسجيل ملاحظات الحصة السريعة، مع عرض سريع لأوائل المدرسة."
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Simple Activity Countdown Timer
            item {
                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                val timeDisplay = "%02d:%02d".format(mins, secs)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .neonCardGlow(MaterialTheme.colorScheme.primary, ext.neonGlowEnabled, 20.dp, alphaFactor = 0.28f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("مؤقت النشاط الصفي", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = timeDisplay,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (remainingSeconds == 0) WarningAmber else MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 3, 5, 10).forEach { m ->
                                FilterChip(
                                    selected = totalSeconds == m * 60,
                                    onClick = {
                                        running = false
                                        totalSeconds = m * 60
                                        remainingSeconds = m * 60
                                    },
                                    label = { Text("$m د") }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    if (remainingSeconds == 0) remainingSeconds = totalSeconds
                                    running = !running
                                },
                                modifier = Modifier.weight(1f).testTag("classroom_timer_toggle_btn")
                            ) {
                                Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (running) "إيقاف مؤقت" else "بدء المؤقت")
                            }
                            OutlinedButton(
                                onClick = {
                                    running = false
                                    remainingSeconds = totalSeconds
                                    AlertSoundHelper.stopGentleAlert()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Refresh, null)
                                Spacer(Modifier.width(6.dp))
                                Text("إعادة")
                            }
                        }
                    }
                }
            }

            // Quick Sticky Notes
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.dp, ext.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("مفكرة الحصة السريعة", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = noteTitle,
                            onValueChange = { noteTitle = it },
                            label = { Text("عنوان سريع (مثلاً: واجب الثاني أ)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = noteContent,
                            onValueChange = { noteContent = it },
                            label = { Text("نص الملاحظة...") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                onAddQuickNote(noteTitle, noteContent)
                                noteTitle = ""
                                noteContent = ""
                            },
                            modifier = Modifier.fillMaxWidth().testTag("save_quick_note_btn")
                        ) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("حفظ الملاحظة")
                        }
                    }
                }
            }

            items(state.quickNotes, key = { it.id }) { note ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.dp, ext.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(note.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            if (note.content.isNotBlank()) {
                                Text(note.content, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString("${note.title}\n${note.content}")) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "نسخ", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { onDeleteQuickNote(note.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = DangerRed)
                        }
                    }
                }
            }

            // Top Students Honor Board
            if (topStudents.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.EmojiEvents, null, tint = GoldBright)
                                Text("لوحة الأوائل السريعة (أعلى 5 طلبة)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            topStudents.forEachIndexed { idx, (st, score, _) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${idx + 1}. ${st.fullName} (${st.gradeLevel.shortAr} ${st.section.id})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        GradeCalculator.formatScore(score, state.gradebookSettings.enableSmartRounding),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
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
