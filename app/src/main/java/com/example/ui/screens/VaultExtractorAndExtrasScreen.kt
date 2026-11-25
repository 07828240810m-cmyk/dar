package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.model.*
import com.example.ui.components.SectionHeaderWithInfo
import com.example.ui.components.neonCardGlow
import com.example.ui.theme.*
import com.example.utils.ExportManager
import com.example.utils.OcrAndImportHelper
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun VaultExtractorAndExtrasScreen(
    state: AppState,
    initialTab: Int = 0, // 0=Text Extractor, 1=PIN Secret Vault
    onAddVaultNote: (String, String) -> Unit,
    onDeleteVaultNote: (String) -> Unit,
    onUpdateVaultPin: (String) -> Unit,
    onSendTextToGradeImport: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val ext = LocalExtendedColors.current
    val scope = rememberCoroutineScope()

    var activeTab by remember(initialTab) { mutableStateOf(initialTab.coerceIn(0, 1)) }

    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = accent)
            }
            SectionHeaderWithInfo(
                title = "مستخرج النصوص والحافظة السرية",
                infoTitle = "تفريغ النصوص وحفظها بأمان",
                infoDescription = "صوّر أي ورقة أو استورد صورة أو ملف Word لاستخراج النص بدون إنترنت، ثم أرسله لسجل الدرجات أو احفظه داخل الحافظة السرية المحمية برمز PIN."
            )
        }

        Spacer(Modifier.height(6.dp))
        // Sleek Dual Pill Switcher
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ext.cardElevated,
            border = BorderStroke(1.dp, ext.borderSubtle),
            modifier = Modifier.fillMaxWidth().neonCardGlow(accent, ext.neonGlowEnabled, 16.dp, alphaFactor = 0.2f)
        ) {
            Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    Triple(0, "مستخرج النصوص", Icons.Filled.DocumentScanner),
                    Triple(1, "الحافظة السرية", Icons.Filled.Lock)
                ).forEach { (idx, title, icon) ->
                    val selected = activeTab == idx
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) accent else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { activeTab = idx }
                            .testTag("vault_tab_$idx")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                icon,
                                null,
                                tint = if (selected) onAccent else ext.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) onAccent else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        AnimatedContent(targetState = activeTab, label = "vault_extractor_tab") { tab ->
            if (tab == 0) {
                RedesignedTextExtractorPanel(
                    onCopy = { txt -> clipboard.setText(AnnotatedString(txt)) },
                    onSaveToVault = { txt -> onAddVaultNote("نص مستخرج", txt) },
                    onSendToGrades = onSendTextToGradeImport,
                    onShare = { txt -> ExportManager.shareText(context, "نص مستخرج", txt) }
                )
            } else {
                RedesignedSecretVaultPanel(
                    state = state,
                    onAddVaultNote = onAddVaultNote,
                    onDeleteVaultNote = onDeleteVaultNote,
                    onUpdateVaultPin = onUpdateVaultPin,
                    onCopy = { txt -> clipboard.setText(AnnotatedString(txt)) }
                )
            }
        }
    }
}

@Composable
private fun RedesignedTextExtractorPanel(
    onCopy: (String) -> Unit,
    onSaveToVault: (String) -> Unit,
    onSendToGrades: (String) -> Unit,
    onShare: (String) -> Unit
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val scope = rememberCoroutineScope()

    var extractedText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    val cameraFileUri = remember {
        val file = File(context.cacheDir, "extract_cam_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            busy = true
            scope.launch {
                val bmp = OcrAndImportHelper.decodeBitmapFromUri(context, cameraFileUri)
                if (bmp != null) {
                    OcrAndImportHelper.recognizeTextFromBitmap(bmp)
                        .onSuccess {
                            extractedText = it
                            feedback = "تم تفريغ النص من الكاميرا بنجاح ✓"
                        }
                        .onFailure { feedback = "تعذر قراءة الصورة، جرّب إضاءة أوضح." }
                }
                busy = false
            }
        }
    }
    val imgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val bmp = OcrAndImportHelper.decodeBitmapFromUri(context, uri)
                if (bmp != null) {
                    OcrAndImportHelper.recognizeTextFromBitmap(bmp)
                        .onSuccess {
                            extractedText = it
                            feedback = "تم استخراج النص من الصورة ✓"
                        }
                        .onFailure { feedback = "تعذر الاستخراج من هذه الصورة." }
                }
                busy = false
            }
        }
    }
    val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            var name = "doc.txt"
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) name = c.getString(i) ?: name
                }
            }
            extractedText = OcrAndImportHelper.extractPlainTextFromDocumentUri(context, uri, name)
            feedback = "تم استخراج النص من ($name) ✓"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth().neonCardGlow(MaterialTheme.colorScheme.primary, ext.neonGlowEnabled, 20.dp, alphaFactor = 0.24f)
            ) {
                Column(
                    modifier = Modifier
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, ext.cardElevated)))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "اختر مصدر استخراج النص (يعمل بدون إنترنت)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ExtractorSourceCard(
                            label = "الكاميرا",
                            icon = Icons.Filled.PhotoCamera,
                            onClick = { camLauncher.launch(cameraFileUri) },
                            modifier = Modifier.weight(1f)
                        )
                        ExtractorSourceCard(
                            label = "الاستوديو",
                            icon = Icons.Filled.Image,
                            onClick = { imgLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f)
                        )
                        ExtractorSourceCard(
                            label = "Word / نص",
                            icon = Icons.Filled.Description,
                            onClick = { docLauncher.launch(arrayOf("text/*", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (busy) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
            }
        }

        feedback?.let { msg ->
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(msg, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ext.cardElevated,
                border = BorderStroke(1.dp, ext.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("النص المستخرج", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "${extractedText.lines().count { it.isNotBlank() }} سطر • ${extractedText.length} حرف",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.textMuted
                        )
                    }
                    OutlinedTextField(
                        value = extractedText,
                        onValueChange = { extractedText = it },
                        placeholder = { Text("سيظهر النص المستخرج هنا ويمكنك تعديله بحرية...") },
                        minLines = 8,
                        maxLines = 14,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (extractedText.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    onCopy(extractedText)
                                    feedback = "تم نسخ النص ✓"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("نسخ")
                            }
                            OutlinedButton(
                                onClick = {
                                    onSaveToVault(extractedText)
                                    feedback = "تم حفظ النص داخل الحافظة السرية ✓"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Lock, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("حفظ بالسرية")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { onSendToGrades(extractedText) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.FactCheck, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text("إرسال لاستيراد الدرجات")
                            }
                            IconButton(onClick = { onShare(extractedText) }) {
                                Icon(Icons.Filled.Share, contentDescription = "مشاركة", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { extractedText = "" }) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = "مسح", tint = DangerRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtractorSourceCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun RedesignedSecretVaultPanel(
    state: AppState,
    onAddVaultNote: (String, String) -> Unit,
    onDeleteVaultNote: (String) -> Unit,
    onUpdateVaultPin: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    val ext = LocalExtendedColors.current
    val accent = MaterialTheme.colorScheme.primary
    var isUnlocked by remember { mutableStateOf(false) }
    var pinAttempt by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var showChangePin by remember { mutableStateOf(false) }

    if (!isUnlocked) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ext.cardElevated,
                border = BorderStroke(1.5.dp, accent.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(8.dp).neonCardGlow(accent, ext.neonGlowEnabled, 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = accent, modifier = Modifier.size(38.dp))
                    }
                    Text("الحافظة السرية للمدرس", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "بياناتك وملاحظاتك الخاصة محمية برمز PIN (الافتراضي: 1234)",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textMuted,
                        textAlign = TextAlign.Center
                    )

                    // Visual PIN Dots
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(state.profile.vaultPin.length.coerceAtLeast(4)) { idx ->
                            val filled = idx < pinAttempt.length
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) accent else MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = pinAttempt,
                        onValueChange = { pinAttempt = it; pinError = false },
                        label = { Text("أدخل الرمز السري (PIN)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pinError,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(0.85f).testTag("vault_pin_input")
                    )
                    if (pinError) {
                        Text("الرمز السري غير صحيح، حاول مجدداً!", color = DangerRed, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            if (pinAttempt == state.profile.vaultPin) {
                                isUnlocked = true
                                pinAttempt = ""
                            } else {
                                pinError = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.85f).testTag("unlock_vault_btn")
                    ) {
                        Icon(Icons.Filled.LockOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("فتح الحافظة السرية")
                    }
                }
            }
        }
    } else {
        var noteTitle by remember { mutableStateOf("") }
        var noteContent by remember { mutableStateOf("") }
        var vaultSearch by remember { mutableStateOf("") }
        var copyFeedback by remember { mutableStateOf<String?>(null) }

        val visibleNotes = remember(state.vaultNotes, vaultSearch) {
            if (vaultSearch.isBlank()) state.vaultNotes
            else state.vaultNotes.filter {
                it.title.contains(vaultSearch, ignoreCase = true) || it.content.contains(vaultSearch, ignoreCase = true)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { isUnlocked = false }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Lock, null, modifier = Modifier.size(16.dp), tint = accent)
                        Spacer(Modifier.width(4.dp))
                        Text("قفل الحافظة")
                    }
                    OutlinedButton(onClick = { showChangePin = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Key, null, modifier = Modifier.size(16.dp), tint = accent)
                        Spacer(Modifier.width(4.dp))
                        Text("تغيير الرمز")
                    }
                }

                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().neonCardGlow(accent, ext.neonGlowEnabled, 18.dp, alphaFactor = 0.2f)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("إضافة ملاحظة سرية جديدة", style = MaterialTheme.typography.titleSmall, color = accent)
                        OutlinedTextField(
                            value = noteTitle,
                            onValueChange = { noteTitle = it },
                            label = { Text("العنوان (مثلاً: كلمة مرور المنصة / ملاحظة سرية)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = noteContent,
                            onValueChange = { noteContent = it },
                            label = { Text("المحتوى السري...") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                onAddVaultNote(noteTitle, noteContent)
                                noteTitle = ""
                                noteContent = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.AddModerator, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("حفظ في الحافظة السرية")
                        }
                    }
                }

                if (state.vaultNotes.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vaultSearch,
                        onValueChange = { vaultSearch = it },
                        placeholder = { Text("ابحث داخل الحافظة السرية...") },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = accent) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                copyFeedback?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = accent)
                }
            }

            if (visibleNotes.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد ملاحظات محفوظة بعد", style = MaterialTheme.typography.bodyMedium, color = ext.textMuted)
                    }
                }
            } else {
                items(visibleNotes, key = { it.id }) { note ->
                    val dateStr = remember(note.createdAtMillis) {
                        if (note.createdAtMillis > 0L) {
                            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar")).format(Date(note.createdAtMillis))
                        } else ""
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ext.cardElevated,
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(note.title, style = MaterialTheme.typography.titleSmall, color = accent, fontWeight = FontWeight.Bold)
                                    if (dateStr.isNotBlank()) {
                                        Text(dateStr, style = MaterialTheme.typography.labelSmall, color = ext.textMuted)
                                    }
                                }
                                Row {
                                    IconButton(onClick = {
                                        onCopy(note.content)
                                        copyFeedback = "تم نسخ (${note.title}) ✓"
                                    }) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = "نسخ", tint = accent, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { onDeleteVaultNote(note.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = DangerRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Text(note.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        if (showChangePin) {
            var newPin by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showChangePin = false },
                title = { Text("تغيير الرمز السري للحافظة") },
                text = {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it },
                        label = { Text("رمز جديد (4 أرقام على الأقل)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (newPin.length >= 4) {
                            onUpdateVaultPin(newPin)
                            showChangePin = false
                        }
                    }) { Text("حفظ الرمز") }
                },
                dismissButton = { TextButton(onClick = { showChangePin = false }) { Text("إلغاء") } }
            )
        }
    }
}
