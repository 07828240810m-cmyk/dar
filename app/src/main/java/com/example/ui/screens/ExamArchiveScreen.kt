package com.example.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.model.AppState
import com.example.model.ExamArchiveCategory
import com.example.model.ExamArchiveItem
import com.example.model.GradeLevel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamArchiveScreen(
    state: AppState,
    selectedGrade: GradeLevel,
    getExamFile: (String) -> File,
    onAddExamItem: (String, ExamArchiveCategory, GradeLevel, Uri, String, String) -> Boolean,
    onDeleteExamItem: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var selectedCategory by remember { mutableStateOf<ExamArchiveCategory?>(null) }
    var filterGrade by remember { mutableStateOf<GradeLevel?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<ExamArchiveItem?>(null) }

    val filteredItems = remember(state.examArchive, selectedCategory, filterGrade) {
        state.examArchive.filter { item ->
            (selectedCategory == null || item.category == selectedCategory) &&
                (filterGrade == null || item.gradeLevel == filterGrade)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("exam_archive_screen")
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("أرشيف أسئلة الامتحانات", fontWeight = FontWeight.Bold)
                    Text(
                        "نماذج يومية، شهرية، نصف السنة، وسنوات سابقة",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("exam_archive_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                }
            },
            actions = {
                FilledTonalButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.padding(end = 8.dp).testTag("add_exam_archive_btn")
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("رفع نموذج")
                }
            }
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("الكل (${state.examArchive.size})") }
                )
            }
            items(ExamArchiveCategory.entries) { cat ->
                val count = state.examArchive.count { it.category == cat }
                val catColor = Color(cat.colorHex)
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text("${cat.titleAr} ($count)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = catColor,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                AssistChip(
                    onClick = { filterGrade = null },
                    label = { Text(if (filterGrade == null) "✓ كل الصفوف" else "كل الصفوف") }
                )
            }
            items(GradeLevel.entries) { g ->
                FilterChip(
                    selected = filterGrade == g,
                    onClick = { filterGrade = if (filterGrade == g) null else g },
                    label = { Text(g.titleAr) }
                )
            }
        }

        if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Filled.FolderSpecial,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text("الأرشيف فارغ في هذا القسم", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "ارفع صور الأسئلة أو ملفات PDF / Word مصنفة بالألوان لسهولة الرجوع إليها في أي وقت.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("رفع أسئلة امتحان")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    val accent = Color(item.category.colorHex)
                    val isImage = item.mimeType.startsWith("image")
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { previewItem = item }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = accent.copy(alpha = 0.16f),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isImage) Icons.Filled.Image else Icons.Filled.PictureAsPdf,
                                        contentDescription = null,
                                        tint = accent
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(shape = RoundedCornerShape(8.dp), color = accent) {
                                        Text(
                                            item.category.titleAr,
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            item.gradeLevel.shortAr,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (item.notes.isNotBlank()) {
                                    Text(
                                        item.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { onDeleteExamItem(item.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddExamArchiveDialog(
            defaultGrade = selectedGrade,
            onDismiss = { showAddDialog = false },
            onSave = { title, cat, grade, uri, mime, notes ->
                val ok = onAddExamItem(title, cat, grade, uri, mime, notes)
                if (ok) {
                    Toast.makeText(context, "تم حفظ النموذج في الأرشيف", Toast.LENGTH_SHORT).show()
                    showAddDialog = false
                } else {
                    Toast.makeText(context, "يرجى اختيار ملف أو صورة أولاً", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    previewItem?.let { item ->
        val file = getExamFile(item.fileName)
        if (item.mimeType.startsWith("image")) {
            Dialog(onDismissRequest = { previewItem = null }) {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val bmp = remember(file.absolutePath) {
                            runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = item.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { shareExamFile(context, file, item.mimeType) }) {
                                Text("مشاركة")
                            }
                            Button(onClick = { previewItem = null }) { Text("إغلاق") }
                        }
                    }
                }
            }
        } else {
            LaunchedEffect(item.id) {
                openOrShareExamDoc(context, file, item.mimeType)
                previewItem = null
            }
        }
    }
}

@Composable
private fun AddExamArchiveDialog(
    defaultGrade: GradeLevel,
    onDismiss: () -> Unit,
    onSave: (String, ExamArchiveCategory, GradeLevel, Uri, String, String) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExamArchiveCategory.MONTHLY) }
    var grade by remember { mutableStateOf(defaultGrade) }
    var notes by remember { mutableStateOf("") }
    var chosenUri by remember { mutableStateOf<Uri?>(null) }
    var chosenMime by remember { mutableStateOf("image/jpeg") }
    var chosenFileLabel by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            chosenUri = uri
            chosenMime = "image/jpeg"
            chosenFileLabel = "صورة امتحان مختارة"
        }
    }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            chosenUri = uri
            chosenMime = context.contentResolver.getType(uri) ?: "application/pdf"
            chosenFileLabel = runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
                }
            }.getOrNull() ?: "ملف وثيقة"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("إضافة نموذج أسئلة امتحان", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان النموذج (مثال: امتحان الشهر الأول)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ExamArchiveCategory.entries) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.titleAr) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(cat.colorHex),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GradeLevel.entries.forEach { g ->
                        FilterChip(selected = grade == g, onClick = { grade = g }, label = { Text(g.shortAr) })
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("صورة")
                    }
                    OutlinedButton(
                        onClick = {
                            docPicker.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("PDF / Word")
                    }
                }
                if (chosenFileLabel.isNotBlank()) {
                    Text("✓ $chosenFileLabel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                    Button(
                        onClick = {
                            val u = chosenUri
                            if (u != null) onSave(title, category, grade, u, chosenMime, notes)
                        },
                        enabled = chosenUri != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("حفظ")
                    }
                }
            }
        }
    }
}

private fun openOrShareExamDoc(context: android.content.Context, file: File, mime: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(viewIntent, "فتح ملف الأسئلة"))
    } catch (_: Exception) {
        shareExamFile(context, file, mime)
    }
}

private fun shareExamFile(context: android.content.Context, file: File, mime: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة نموذج الأسئلة"))
    } catch (_: Exception) {
        Toast.makeText(context, "لا يوجد تطبيق متاح لفتح هذا الملف", Toast.LENGTH_SHORT).show()
    }
}
