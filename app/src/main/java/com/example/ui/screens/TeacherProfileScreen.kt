package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppState
import com.example.model.TeacherProfile
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherProfileScreen(
    state: AppState,
    getMediaFile: (String?) -> File?,
    onImportCover: (Uri) -> Boolean,
    onImportSplashLogo: (Uri) -> Boolean,
    onUpdateProfile: (TeacherProfile) -> Unit,
    currentRankTitle: String = "",
    hasWeeklyNameGift: Boolean = false,
    onRequestNameChange: () -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val p = state.profile
    val context = LocalContext.current
    fun copyWithToast(label: String, text: String) {
        if (text.isBlank()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "تم نسخ $label ✓", Toast.LENGTH_SHORT).show()
    }

    var username by remember(p.accountUsername) { mutableStateOf(p.accountUsername) }
    var name by remember(p.teacherName) { mutableStateOf(p.teacherName) }
    var school by remember(p.schoolName) { mutableStateOf(p.schoolName) }
    var subject by remember(p.subjectName) { mutableStateOf(p.subjectName) }
    var bio by remember(p.teacherBio) { mutableStateOf(p.teacherBio) }
    var splashGreeting by remember(p.splashGreeting) { mutableStateOf(p.splashGreeting) }
    var splashEmoji by remember(p.splashEmoji) { mutableStateOf(p.splashEmoji) }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onImportCover(uri) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onImportSplashLogo(uri) }

    val coverFile = getMediaFile(p.profileCoverFileName)
    val coverBmp = remember(coverFile?.absolutePath) {
        coverFile?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
    }
    val logoFile = getMediaFile(p.splashLogoFileName)
    val logoBmp = remember(logoFile?.absolutePath) {
        logoFile?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("teacher_profile_screen")
    ) {
        TopAppBar(
            title = { Text("الملف الشخصي للمدرس", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("profile_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                // ~1/4 screen height rectangular cover banner from Gallery
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
                                    )
                                )
                            )
                            .clickable {
                                coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            .testTag("profile_cover_banner")
                    ) {
                        if (coverBmp != null) {
                            Image(
                                bitmap = coverBmp.asImageBitmap(),
                                contentDescription = "غلاف الملف الشخصي",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                                    )
                                )
                        )
                        FilledTonalButton(
                            onClick = {
                                coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("صورة الغلاف")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, top = 162.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .size(86.dp)
                                .clickable {
                                    logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (logoBmp != null) {
                                    Image(
                                        bitmap = logoBmp.asImageBitmap(),
                                        contentDescription = "شعار المدرس",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else if (p.splashEmoji.isNotBlank() && p.splashEmoji != "🧬") {
                                    Text(p.splashEmoji, fontSize = 36.sp)
                                } else {
                                    Icon(
                                        Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(
                                text = if (p.teacherName.isNotBlank()) "أ. ${p.teacherName}" else "اسم المدرس",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${p.subjectName} • ${p.schoolName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val displayUser = p.accountUsername.ifBlank { p.autoGeneratedUsername }
                            if (displayUser.isNotBlank() || currentRankTitle.isNotBlank()) {
                                Text(
                                    text = listOfNotNull(
                                        displayUser.takeIf { it.isNotBlank() }?.let { "اسم المستخدم: $it" },
                                        currentRankTitle.takeIf { it.isNotBlank() }?.let { "★ $it" }
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("البيانات التعريفية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (p.autoGeneratedUsername.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "المعرف التلقائي للحساب: ${p.autoGeneratedUsername}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { copyWithToast("المعرف التلقائي", p.autoGeneratedUsername) },
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("profile_copy_auto_username_btn")
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "نسخ المعرف", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("اسم المستخدم للحساب السحابي") },
                            trailingIcon = {
                                if (username.isNotBlank()) {
                                    IconButton(
                                        onClick = { copyWithToast("اسم المستخدم", username) },
                                        modifier = Modifier.testTag("profile_copy_username_btn")
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = "نسخ اسم المستخدم", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { if (!p.initialNameChosen) name = it },
                            enabled = !p.initialNameChosen,
                            label = { Text("اسم المدرس") },
                            trailingIcon = {
                                val targetName = if (p.initialNameChosen) p.teacherName else name
                                if (targetName.isNotBlank()) {
                                    IconButton(
                                        onClick = { copyWithToast("اسم المدرس", targetName) },
                                        modifier = Modifier.testTag("profile_copy_teacher_name_btn")
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = "نسخ اسم المدرس", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (p.initialNameChosen) {
                            OutlinedButton(
                                onClick = onRequestNameChange,
                                modifier = Modifier.fillMaxWidth().testTag("profile_request_name_change_btn")
                            ) {
                                Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (hasWeeklyNameGift) "تعديل اسم المدرس (متاح أسبوعياً)"
                                    else "طلب تعديل اسم المدرس"
                                )
                            }
                        }
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text("المادة / الاختصاص") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = school,
                            onValueChange = { school = it },
                            label = { Text("اسم المدرسة") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = bio,
                            onValueChange = { bio = it },
                            label = { Text("نبذة تعريفية / رسالة تربوية") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("إعدادات شاشة الترحيب (1.5 ثانية عند الدخول)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("تفعيل شاشة الدخول الترحيبية", modifier = Modifier.weight(1f))
                            Switch(
                                checked = p.splashEnabled,
                                onCheckedChange = { onUpdateProfile(p.copy(splashEnabled = it)) }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("إظهار اسم المدرس", modifier = Modifier.weight(1f))
                            Switch(
                                checked = p.splashShowName,
                                onCheckedChange = { onUpdateProfile(p.copy(splashShowName = it)) }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("إظهار اللوغو / الصورة", modifier = Modifier.weight(1f))
                            Switch(
                                checked = p.splashShowLogo,
                                onCheckedChange = { onUpdateProfile(p.copy(splashShowLogo = it)) }
                            )
                        }
                        OutlinedTextField(
                            value = splashGreeting,
                            onValueChange = { splashGreeting = it },
                            label = { Text("عبارة الترحيب") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = splashEmoji,
                            onValueChange = { splashEmoji = it },
                            label = { Text("إيموجي أو رمز تعبيري") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = {
                                logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تحميل صورة لوغو خاصة من الاستوديو")
                        }
                        Button(
                            onClick = {
                                val finalName = if (p.initialNameChosen) p.teacherName else name.trim()
                                val chosenNow = p.initialNameChosen || finalName.length >= 2
                                onUpdateProfile(
                                    p.copy(
                                        accountUsername = username.trim(),
                                        teacherName = finalName,
                                        timetableTeacherKey = finalName.ifBlank { p.timetableTeacherKey },
                                        subjectName = subject.trim().ifBlank { p.subjectName },
                                        schoolName = school.trim().ifBlank { p.schoolName },
                                        teacherBio = bio.trim(),
                                        splashGreeting = splashGreeting.trim(),
                                        splashEmoji = splashEmoji.trim(),
                                        initialNameChosen = chosenNow,
                                        lastNameChangeMillis = if (!p.initialNameChosen && chosenNow) System.currentTimeMillis() else p.lastNameChangeMillis
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("save_profile_btn")
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("حفظ الملف الشخصي وإعدادات الدخول")
                        }
                    }
                }
            }
        }
    }
}
