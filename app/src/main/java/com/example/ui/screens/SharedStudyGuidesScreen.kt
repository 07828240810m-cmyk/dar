package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.SharedStudyGuideRecord
import com.example.model.GradeLevel
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.utils.GoogleSignInArabicButton
import com.example.utils.PdfValidationHelper
import com.example.utils.StudentSearchAndVoiceHelper
import com.google.firebase.auth.FirebaseUser
import java.io.File
import java.util.Locale

@Composable
fun SharedStudyGuidesScreen(
    guides: List<SharedStudyGuideRecord>,
    currentUser: FirebaseUser?,
    isAdmin: Boolean,
    defaultTeacherName: String,
    defaultSubjectName: String,
    busy: Boolean,
    onUploadPdfGuide: (
        title: String,
        subjectTitle: String,
        gradeId: String,
        teacherName: String,
        description: String,
        fileName: String,
        pdfBytes: ByteArray,
        onResult: (Result<SharedStudyGuideRecord>) -> Unit
    ) -> Unit,
    onDownloadAndPrepareFile: (
        guide: SharedStudyGuideRecord,
        onReady: (File) -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onDeleteGuide: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val ext = LocalExtendedColors.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedGradeFilter by remember { mutableStateOf("ALL") }
    var showUploadDialog by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val filteredGuides = remember(guides, searchQuery, selectedGradeFilter) {
        val normQ = StudentSearchAndVoiceHelper.normalizeArabic(searchQuery)
        guides.filter { guide ->
            val gradeMatch = selectedGradeFilter == "ALL" ||
                guide.gradeId.equals(selectedGradeFilter, ignoreCase = true) ||
                guide.gradeId.equals("ALL", ignoreCase = true)
            val textMatch = if (normQ.isEmpty()) true else {
                val combined = StudentSearchAndVoiceHelper.normalizeArabic(
                    "${guide.title} ${guide.subjectTitle} ${guide.teacherName} ${guide.description} ${guide.fileName}"
                )
                combined.contains(normQ)
            }
            gradeMatch && textMatch
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp).testTag("study_guides_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "رجوع",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text(
                        text = "مكتبة الملازم المشتركة (PDF)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "ارفع ملزمتك بصيغة PDF فقط وتصفح ملازم المدرسين",
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.textMuted
                    )
                }
            }
            Button(
                enabled = currentUser != null && !busy,
                onClick = { showUploadDialog = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag("study_guides_open_upload_btn")
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("رفع ملزمة PDF", fontWeight = FontWeight.Bold)
            }
        }

        if (currentUser == null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ext.cardElevated,
                border = BorderStroke(1.dp, WarningAmber),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "سجل الدخول بحساب Google للوصول إلى مكتبة الملازم المشتركة ورفع أو تحميل ملفات PDF.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    GoogleSignInArabicButton(
                        label = "تسجيل الدخول بحساب Google",
                        onAuthSuccess = { statusBanner = true to "تم تسجيل الدخول بنجاح ✓" },
                        onAuthError = { statusBanner = false to it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        statusBanner?.let { (isOk, msg) ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = (if (isOk) SuccessGreen else DangerRed).copy(alpha = 0.14f),
                border = BorderStroke(1.dp, if (isOk) SuccessGreen else DangerRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOk) SuccessGreen else DangerRed,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("ابحث بالعنوان، المادة، أو اسم المدرس...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("study_guides_search_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedGradeFilter == "ALL",
                onClick = { selectedGradeFilter = "ALL" },
                label = { Text("الجميع (${guides.size})") }
            )
            GradeLevel.entries.forEach { g ->
                FilterChip(
                    selected = selectedGradeFilter == g.id,
                    onClick = { selectedGradeFilter = g.id },
                    label = { Text(g.shortAr) }
                )
            }
        }

        if (busy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("جاري المزامنة مع السحابة...", style = MaterialTheme.typography.labelMedium)
            }
        }

        if (filteredGuides.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ext.cardElevated,
                border = BorderStroke(1.dp, ext.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                    Text(
                        text = if (guides.isEmpty()) "لا توجد ملازم مرفوعة بعد — كن أول من يرفع ملزمة PDF لزملائك!" else "لا توجد نتائج مطابقة لبحثك.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ext.textMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredGuides, key = { it.id }) { guide ->
                    val canDelete = isAdmin || (currentUser != null && guide.uploaderUid == currentUser.uid)
                    val gradeLabel = when (guide.gradeId) {
                        GradeLevel.FIRST.id -> GradeLevel.FIRST.titleAr
                        GradeLevel.SECOND.id -> GradeLevel.SECOND.titleAr
                        GradeLevel.THIRD.id -> GradeLevel.THIRD.titleAr
                        else -> "جميع المراحل"
                    }
                    val sizeKb = (guide.fileSizeBytes / 1024L).coerceAtLeast(1L)
                    val sizeLabel = if (sizeKb >= 1024L) {
                        String.format(Locale.US, "%.1f MB", sizeKb / 1024.0)
                    } else {
                        "$sizeKb KB"
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.dp, ext.borderSubtle),
                        modifier = Modifier.fillMaxWidth().testTag("study_guide_card_${guide.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = DangerRed.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.PictureAsPdf,
                                            contentDescription = "ملف PDF",
                                            tint = DangerRed,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = guide.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${guide.subjectTitle} • $gradeLabel • ${guide.teacherName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (canDelete) {
                                    IconButton(
                                        onClick = { onDeleteGuide(guide.id) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "حذف الملزمة",
                                            tint = DangerRed
                                        )
                                    }
                                }
                            }

                            if (guide.description.isNotBlank()) {
                                Text(
                                    text = guide.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textMuted,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "PDF • $sizeLabel • التحميلات: ${guide.downloadCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.textMuted
                                )
                                Button(
                                    enabled = !busy && currentUser != null,
                                    onClick = {
                                        onDownloadAndPrepareFile(
                                            guide,
                                            { file ->
                                                runCatching {
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        file
                                                    )
                                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, "application/pdf")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(viewIntent, "فتح الملزمة PDF"))
                                                }.onFailure {
                                                    statusBanner = true to "تم حفظ الملزمة محلياً: ${file.name}"
                                                }
                                            },
                                            { errMsg ->
                                                statusBanner = false to errMsg
                                                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("تحميل وفتح PDF", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUploadDialog) {
        var titleInput by remember { mutableStateOf("") }
        var subjectInput by remember { mutableStateOf(defaultSubjectName) }
        var teacherInput by remember { mutableStateOf(defaultTeacherName) }
        var descInput by remember { mutableStateOf("") }
        var selectedGradeId by remember { mutableStateOf(GradeLevel.THIRD.id) }
        var pickedFileName by remember { mutableStateOf("") }
        var pickedPdfBytes by remember { mutableStateOf<ByteArray?>(null) }
        var pickError by remember { mutableStateOf<String?>(null) }

        val pdfPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            val mime = context.contentResolver.getType(uri).orEmpty()
            var displayName = "study_guide.pdf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    displayName = cursor.getString(idx) ?: displayName
                }
            }
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()

            when {
                bytes == null || bytes.isEmpty() -> {
                    pickedPdfBytes = null
                    pickError = "تعذر قراءة الملف المختار."
                }
                !mime.equals("application/pdf", ignoreCase = true) && !displayName.lowercase(Locale.US).endsWith(".pdf") -> {
                    pickedPdfBytes = null
                    pickError = "عذراً: يجب أن يكون ملف الملزمة بصيغة PDF فقط."
                }
                !PdfValidationHelper.isValidPdfBytes(bytes) -> {
                    pickedPdfBytes = null
                    pickError = "الملف المختار ليس ملف PDF حقيقي (%PDF- غير موجود)."
                }
                !PdfValidationHelper.isAllowedFileSize(bytes.size.toLong()) -> {
                    pickedPdfBytes = null
                    pickError = "حجم ملف PDF يتجاوز الحد المسموح (5 ميجابايت)."
                }
                else -> {
                    pickedFileName = displayName
                    pickedPdfBytes = bytes
                    pickError = null
                    if (titleInput.isBlank()) {
                        titleInput = displayName.removeSuffix(".pdf").removeSuffix(".PDF").take(100)
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { if (!busy) showUploadDialog = false },
            icon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = DangerRed) },
            title = { Text("رفع ملزمة PDF للمكتبة المشتركة") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "يجب أن يكون الملف بصيغة PDF حصراً (حد أقصى 5 ميجابايت) ليتاح البحث عنه وتحميله من بقية المدرسين.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textMuted
                    )
                    OutlinedButton(
                        onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pick_pdf_study_guide_btn")
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (pickedFileName.isBlank()) "اختيار ملف PDF من الجهاز" else "المحدد: $pickedFileName",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    pickError?.let {
                        Text(it, color = DangerRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it.take(160) },
                        label = { Text("عنوان الملزمة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subjectInput,
                        onValueChange = { subjectInput = it.take(80) },
                        label = { Text("المادة الدراسية") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = teacherInput,
                        onValueChange = { teacherInput = it.take(120) },
                        label = { Text("اسم المدرس المعدّ للملزمة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("المرحلة الدراسية:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedGradeId == "ALL",
                            onClick = { selectedGradeId = "ALL" },
                            label = { Text("الجميع") }
                        )
                        GradeLevel.entries.forEach { g ->
                            FilterChip(
                                selected = selectedGradeId == g.id,
                                onClick = { selectedGradeId = g.id },
                                label = { Text(g.shortAr) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it.take(600) },
                        label = { Text("وصف مختصر للمحتوى (اختياري)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                val bytesReady = pickedPdfBytes
                Button(
                    enabled = !busy && bytesReady != null && titleInput.trim().length >= 2 && subjectInput.isNotBlank(),
                    onClick = {
                        if (bytesReady != null) {
                            onUploadPdfGuide(
                                titleInput.trim(),
                                subjectInput.trim(),
                                selectedGradeId,
                                teacherInput.trim().ifBlank { "أستاذ" },
                                descInput.trim(),
                                pickedFileName.ifBlank { "study_guide.pdf" },
                                bytesReady
                            ) { res ->
                                res.onSuccess {
                                    statusBanner = true to "تم نشر الملزمة (${it.title}) لجميع المدرسين ✓"
                                    showUploadDialog = false
                                }.onFailure { err ->
                                    pickError = err.localizedMessage ?: "تعذر رفع الملزمة."
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_upload_pdf_guide_btn")
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("نشر الملزمة PDF")
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { showUploadDialog = false }) { Text("إلغاء") }
            }
        )
    }
}
