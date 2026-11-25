package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.data.AdminAnnouncement
import com.example.data.AnnouncementPriority
import com.example.data.AppReleaseInfo
import com.example.data.CloudPoll
import com.example.data.NotificationTargetType
import com.example.data.PromoBannerConfig
import com.example.data.TrialAccessStatus
import com.example.data.UpdateCheckResult
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.utils.ApkDownloadState
import com.example.utils.ApkInstallLaunchResult
import com.example.utils.ApkUpdateDownloader
import com.example.utils.GoogleSignInArabicButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun formatArabicTimestamp(ts: Timestamp?): String {
    if (ts == null) return "الآن"
    val sdf = SimpleDateFormat("yyyy/MM/dd • hh:mm a", Locale("ar"))
    return sdf.format(Date(ts.seconds * 1000L))
}

@Composable
fun AppUpdateDialog(
    updateState: UpdateCheckResult,
    downloadState: ApkDownloadState,
    currentUser: FirebaseUser?,
    onStartDownload: (String) -> Unit,
    onRetryCheck: () -> Unit,
    onResetDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val isForced = updateState is UpdateCheckResult.UpdateAvailable && updateState.release.forceUpdate
    var installErrorMsg by remember { mutableStateOf<String?>(null) }
    var needsInstallPermission by remember { mutableStateOf(false) }
    var autoLaunchedForPath by remember { mutableStateOf<String?>(null) }
    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(downloadState) {
        if (downloadState is ApkDownloadState.ReadyToInstall) {
            val path = downloadState.file.absolutePath
            if (autoLaunchedForPath != path) {
                autoLaunchedForPath = path
                when (val result = ApkUpdateDownloader.launchApkInstaller(context, downloadState.file)) {
                    ApkInstallLaunchResult.LaunchedInstaller -> {
                        needsInstallPermission = false
                        installErrorMsg = null
                    }
                    ApkInstallLaunchResult.NeedsInstallPermission -> {
                        needsInstallPermission = true
                        installErrorMsg = "يرجى السماح للتطبيق بتثبيت التحديثات من مصادر غير معروفة ثم الضغط على تثبيت."
                    }
                    is ApkInstallLaunchResult.Failed -> {
                        installErrorMsg = result.message
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isForced) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isForced,
            dismissOnClickOutside = !isForced,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp)
                .testTag("app_update_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.SystemUpdate,
                                contentDescription = "تحديث التطبيق",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (updateState) {
                                is UpdateCheckResult.UpdateAvailable -> "يتوفر تحديث جديد"
                                is UpdateCheckResult.UpToDate -> "التطبيق محدث"
                                is UpdateCheckResult.Checking -> "جاري التحقق من التحديثات..."
                                is UpdateCheckResult.Error -> "التحقق من التحديثات"
                                UpdateCheckResult.Idle -> "تحديثات التطبيق"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "تحديثات وروابط التطبيق الرسمية",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textMuted
                        )
                    }
                    if (!isForced) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                        }
                    }
                }

                when (updateState) {
                    UpdateCheckResult.Idle, UpdateCheckResult.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Text("جاري الاتصال بـ Firebase لفحص التحديثات...")
                        }
                    }

                    is UpdateCheckResult.UpToDate -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SuccessGreen.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen)
                                Text(
                                    text = "لا توجد روابط تحديث جديدة حالياً.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text("حسناً")
                        }
                    }

                    is UpdateCheckResult.Error -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = WarningAmber.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = updateState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        if (currentUser == null) {
                            GoogleSignInArabicButton(
                                label = "تسجيل الدخول بـ Google للتحقق",
                                onAuthSuccess = {
                                    authError = null
                                    onRetryCheck()
                                },
                                onAuthError = { authError = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                            authError?.let {
                                Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) { Text("إغلاق") }
                                Button(
                                    onClick = onRetryCheck,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) { Text("إعادة المحاولة") }
                            }
                        }
                    }

                    is UpdateCheckResult.UpdateAvailable -> {
                        val release = updateState.release
                        val updateHeading = release.latestVersionName.takeIf {
                            it.isNotBlank() && it != "1.0" && it != "1.1"
                        } ?: "تحديث جديد للتطبيق"

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Filled.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = updateHeading,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        val localReleaseBmp = remember(release.releaseImageBase64) {
                            release.releaseImageBase64.takeIf { it.isNotBlank() }?.let { b64 ->
                                runCatching {
                                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                }.getOrNull()
                            }
                        }
                        var remoteReleaseBmp by remember(release.releaseImageUrl) { mutableStateOf<Bitmap?>(null) }
                        LaunchedEffect(release.releaseImageUrl) {
                            val imgUrl = release.releaseImageUrl.trim()
                            if (localReleaseBmp == null && (imgUrl.startsWith("https://") || imgUrl.startsWith("http://"))) {
                                remoteReleaseBmp = withContext(Dispatchers.IO) {
                                    runCatching {
                                        URL(imgUrl).openStream().use { BitmapFactory.decodeStream(it) }
                                    }.getOrNull()
                                }
                            } else {
                                remoteReleaseBmp = null
                            }
                        }
                        val displayReleaseBmp = localReleaseBmp ?: remoteReleaseBmp
                        if (displayReleaseBmp != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Image(
                                    bitmap = displayReleaseBmp.asImageBitmap(),
                                    contentDescription = "صورة التحديث",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                                )
                            }
                        }

                        if (release.releaseNotes.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ext.cardElevated,
                                border = BorderStroke(1.dp, ext.borderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "تفاصيل التحديث:",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = release.releaseNotes,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        when (downloadState) {
                            ApkDownloadState.Idle -> {
                                if (release.apkUrl.isBlank()) {
                                    Text(
                                        text = "لم يتم إرفاق رابط تحديث حالياً.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WarningAmber
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            Button(
                                                onClick = {
                                                    val opened = runCatching {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl.trim()))
                                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        )
                                                        true
                                                    }.getOrDefault(false)
                                                    if (!opened) {
                                                        Toast.makeText(context, "تعذر فتح الرابط: ${release.apkUrl}", Toast.LENGTH_LONG).show()
                                                    }
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 48.dp)
                                                    .testTag("update_view_link_btn")
                                            ) {
                                                Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("عرض الرابط", fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                    cm?.setPrimaryClip(ClipData.newPlainText("رابط التحديث", release.apkUrl.trim()))
                                                    Toast.makeText(context, "تم نسخ رابط التحديث ✓", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("update_copy_link_btn")
                                            ) {
                                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("نسخ")
                                            }
                                        }
                                        if (!release.forceUpdate) {
                                            TextButton(
                                                onClick = onDismiss,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 48.dp)
                                                    .testTag("update_later_btn")
                                            ) { Text("إغلاق") }
                                        }
                                    }
                                }
                            }

                            is ApkDownloadState.Downloading -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    val percent = if (downloadState.progress >= 0f) {
                                        (downloadState.progress * 100).toInt().coerceIn(0, 100)
                                    } else null
                                    Text(
                                        text = if (percent != null) {
                                            "جاري تنزيل التحديث... $percent%"
                                        } else {
                                            "جاري تنزيل التحديث (${downloadState.downloadedBytes / 1024} KB)..."
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (downloadState.progress >= 0f) {
                                        LinearProgressIndicator(
                                            progress = { downloadState.progress },
                                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                        )
                                    }
                                }
                            }

                            is ApkDownloadState.ReadyToInstall -> {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = SuccessGreen.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, SuccessGreen),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "اكتمل تنزيل ملف التحديث بنجاح ✓",
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessGreen
                                        )
                                        installErrorMsg?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall, color = WarningAmber)
                                        }
                                    }
                                }
                                if (needsInstallPermission) {
                                    Button(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(ApkUpdateDownloader.buildUnknownSourcesSettingsIntent(context))
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    ) {
                                        Text("فتح إعدادات إذن التثبيت")
                                    }
                                }
                                Button(
                                    onClick = {
                                        when (val result = ApkUpdateDownloader.launchApkInstaller(context, downloadState.file)) {
                                            ApkInstallLaunchResult.LaunchedInstaller -> {
                                                needsInstallPermission = false
                                                installErrorMsg = null
                                            }
                                            ApkInstallLaunchResult.NeedsInstallPermission -> {
                                                needsInstallPermission = true
                                                installErrorMsg = "يرجى تفعيل السماح بتثبيت التطبيقات ثم العودة والضغط هنا."
                                            }
                                            is ApkInstallLaunchResult.Failed -> {
                                                installErrorMsg = result.message
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Filled.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("تثبيت التحديث الآن", fontWeight = FontWeight.Bold)
                                }
                            }

                            is ApkDownloadState.Error -> {
                                Text(downloadState.message, color = DangerRed, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    if (!release.forceUpdate) {
                                        OutlinedButton(
                                            onClick = {
                                                onResetDownload()
                                                onDismiss()
                                            },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                        ) { Text("إغلاق") }
                                    }
                                    Button(
                                        onClick = { onStartDownload(release.apkUrl) },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    ) { Text("إعادة التنزيل") }
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
fun NotificationsInboxDialog(
    currentUser: FirebaseUser?,
    announcements: List<AdminAnnouncement>,
    readIds: Set<String>,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDeleteNotifications: (List<String>) -> Unit = {},
    onDismiss: () -> Unit
) {
    val ext = LocalExtendedColors.current
    var authError by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    val unreadCount = remember(announcements, readIds) {
        announcements.count { it.id.isNotBlank() && it.id !in readIds }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
                .padding(horizontal = 14.dp)
                .widthIn(max = 580.dp)
                .testTag("notifications_inbox_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = "صندوق الإشعارات",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "الإشعارات والتبليغات",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (unreadCount > 0) "لديك $unreadCount إشعار غير مقروء" else "جميع الإشعارات مقروءة",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.textMuted
                            )
                        }
                    }
                    if (unreadCount > 0 && currentUser != null) {
                        TextButton(
                            onClick = onMarkAllRead,
                            modifier = Modifier.testTag("mark_all_notifications_read_btn")
                        ) {
                            Text("قراءة الكل", fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                    }
                }

                if (currentUser != null && announcements.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectionMode,
                            onClick = {
                                selectionMode = !selectionMode
                                selectedIds = emptySet()
                            },
                            label = { Text(if (selectionMode) "إلغاء التحديد" else "تحديد للحذف") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (selectionMode) Icons.Filled.Close else Icons.Filled.Checklist,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        if (selectionMode && selectedIds.isNotEmpty()) {
                            Button(
                                onClick = {
                                    onDeleteNotifications(selectedIds.toList())
                                    selectedIds = emptySet()
                                    selectionMode = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("حذف المحدد (${selectedIds.size})", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showDeleteAllConfirm = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp).testTag("delete_all_notifications_btn")
                            ) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("حذف الكل", color = DangerRed, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (currentUser == null) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "سجّل الدخول بحساب Google لاستلام إشعارات الإدارة والتحديثات",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(14.dp))
                        GoogleSignInArabicButton(
                            onAuthSuccess = { authError = null },
                            onAuthError = { authError = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                        authError?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (announcements.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.NotificationsNone,
                            contentDescription = null,
                            tint = ext.textMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "لا توجد إشعارات حالياً",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ستظهر هنا أحدث التبليغات والتنبيهات المرسلة من الإدارة فور وصولها.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(announcements, key = { it.id }) { item ->
                            val isRead = item.id in readIds
                            val isSelected = item.id in selectedIds
                            val priority = item.priorityLevel()
                            val badgeColor = when (priority) {
                                AnnouncementPriority.URGENT -> DangerRed
                                AnnouncementPriority.IMPORTANT -> WarningAmber
                                AnnouncementPriority.NORMAL -> MaterialTheme.colorScheme.primary
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = when {
                                    isSelected -> DangerRed.copy(alpha = 0.10f)
                                    isRead -> ext.cardElevated
                                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                },
                                border = BorderStroke(
                                    width = if (isSelected || !isRead) 1.6.dp else 1.dp,
                                    color = if (isSelected) DangerRed else if (isRead) ext.borderSubtle else badgeColor
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (selectionMode) {
                                            selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                        } else if (!isRead && item.id.isNotBlank()) {
                                            onMarkRead(item.id)
                                        }
                                    }
                                    .testTag("inbox_notification_card_${item.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (selectionMode) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { checked ->
                                                        selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                                                    }
                                                )
                                            } else if (!isRead) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(9.dp)
                                                        .clip(CircleShape)
                                                        .background(badgeColor)
                                                )
                                            }
                                            val itemIconBmp = remember(item.id, item.iconBase64) {
                                                item.iconBase64.takeIf { it.isNotBlank() }?.let { b64 ->
                                                    runCatching {
                                                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                                    }.getOrNull()
                                                }
                                            }
                                            if (itemIconBmp != null) {
                                                Image(
                                                    bitmap = itemIconBmp.asImageBitmap(),
                                                    contentDescription = "أيقونة الإشعار",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                )
                                            }
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = badgeColor.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = priority.titleAr,
                                                    color = badgeColor,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                            if (!selectionMode && item.id.isNotBlank()) {
                                                IconButton(
                                                    onClick = { onDeleteNotifications(listOf(item.id)) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.DeleteOutline,
                                                        contentDescription = "حذف الإشعار",
                                                        tint = ext.textMuted,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Text(
                                        text = item.body,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    val tEnum = item.targetTypeEnum()
                                    if (tEnum != NotificationTargetType.ALL) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = "توجيه خاص: ${tEnum.titleAr}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = formatArabicTimestamp(item.sentAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ext.textMuted
                                        )
                                        if (!isRead) {
                                            TextButton(
                                                onClick = { onMarkRead(item.id) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("تحديد كمقروء", style = MaterialTheme.typography.labelMedium)
                                            }
                                        } else {
                                            Text(
                                                text = "مقروء ✓",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SuccessGreen,
                                                fontWeight = FontWeight.SemiBold
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
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("حذف جميع الإشعارات السابقة", color = DangerRed) },
            text = { Text("هل تريد حذف كافة الإشعارات المعروضة من صندوقك؟") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteNotifications(announcements.mapNotNull { it.id.takeIf(String::isNotBlank) })
                        showDeleteAllConfirm = false
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) { Text("حذف الكل", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
fun AdminNotificationPanelDialog(
    currentUser: FirebaseUser?,
    isAdmin: Boolean,
    adminCheckLoading: Boolean,
    hasAdminPin: Boolean,
    adminPinUnlocked: Boolean,
    adminAnnouncements: List<AdminAnnouncement>,
    remoteRelease: AppReleaseInfo?,
    actionMessage: String?,
    defaultAuthorName: String,
    onRetryAdminCheck: () -> Unit,
    onSignOut: () -> Unit,
    onSetupAdminPin: (String) -> Boolean,
    onUnlockWithPin: (String) -> Boolean,
    onPublishAnnouncement: (String, String, AnnouncementPriority, String) -> Unit,
    onToggleActive: (String, Boolean) -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
    onPublishRelease: (Int, String, String, Boolean, String) -> Unit,
    onClearActionMessage: () -> Unit,
    onDismiss: () -> Unit
) {
    val ext = LocalExtendedColors.current
    var authError by remember { mutableStateOf<String?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }

    var titleInput by remember { mutableStateOf("") }
    var bodyInput by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(AnnouncementPriority.IMPORTANT) }
    var deleteCandidate by remember { mutableStateOf<AdminAnnouncement?>(null) }

    var versionCodeInput by remember(remoteRelease) {
        mutableStateOf((remoteRelease?.latestVersionCode ?: BuildConfig.VERSION_CODE + 1).toString())
    }
    var versionNameInput by remember(remoteRelease) {
        mutableStateOf(remoteRelease?.latestVersionName ?: "1.1")
    }
    var apkUrlInput by remember(remoteRelease) {
        mutableStateOf(remoteRelease?.apkUrl.orEmpty())
    }
    var notesInput by remember(remoteRelease) {
        mutableStateOf(remoteRelease?.releaseNotes.orEmpty())
    }
    var forceUpdateInput by remember(remoteRelease) {
        mutableStateOf(remoteRelease?.forceUpdate ?: false)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            shadowElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 12.dp)
                .widthIn(max = 600.dp)
                .testTag("admin_notifications_panel_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                        Icon(
                            Icons.Filled.AdminPanelSettings,
                            contentDescription = "إدارة الإشعارات",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "لوحة الأدمن — إدارة الإشعارات",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "الفيزياء • الثالث المتوسط (وصول محمي)",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.textMuted
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                when {
                    currentUser == null -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "مطلوب التحقق من حساب الأدمن",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "سجّل الدخول بحساب Google المصرّح له لإرسال وإدارة الإشعارات أو ضبط التحديثات.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.textMuted
                            )
                            Spacer(Modifier.height(16.dp))
                            GoogleSignInArabicButton(
                                label = "دخول الأدمن عبر Google",
                                onAuthSuccess = {
                                    authError = null
                                    onRetryAdminCheck()
                                },
                                onAuthError = { authError = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                            authError?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    adminCheckLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator()
                                Text("جاري التحقق من صلاحية الأدمن في Firebase...")
                            }
                        }
                    }

                    !isAdmin -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.GppBad,
                                contentDescription = null,
                                tint = DangerRed,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "صلاحية الأدمن غير متوفرة لهذا الحساب",
                                style = MaterialTheme.typography.titleMedium,
                                color = DangerRed,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "الحساب الحالي: ${currentUser.email ?: currentUser.uid}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.textMuted
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = onSignOut,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) { Text("تبديل الحساب") }
                                Button(
                                    onClick = onRetryAdminCheck,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) { Text("إعادة التحقق") }
                            }
                        }
                    }

                    !adminPinUnlocked -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = if (hasAdminPin) "أدخل رمز PIN الخاص بالأدمن" else "أنشئ رمز PIN لحماية لوحة الأدمن",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "حساب الأدمن المؤكد: ${currentUser.email ?: currentUser.uid}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SuccessGreen
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = {
                                    pinInput = it.take(24)
                                    pinError = null
                                },
                                label = { Text(if (hasAdminPin) "رمز الحماية / PIN" else "رمز حماية جديد (4 أحرف/أرقام فأكثر)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth().testTag("admin_pin_input")
                            )
                            pinError?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (!hasAdminPin) {
                                        if (!onSetupAdminPin(pinInput)) {
                                            pinError = "أدخل 4 أحرف أو أرقام على الأقل لحفظ الرمز."
                                        }
                                    } else if (!onUnlockWithPin(pinInput)) {
                                        pinError = "رمز الحماية غير صحيح."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("admin_pin_unlock_btn")
                            ) {
                                Text(if (hasAdminPin) "فتح لوحة الأدمن" else "حفظ الرمز وفتح اللوحة", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    else -> {
                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("الإشعارات (${adminAnnouncements.size})") }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("تحديثات التطبيق") }
                            )
                        }

                        actionMessage?.let { msg ->
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    IconButton(onClick = onClearActionMessage, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Filled.Close, contentDescription = "إخفاء", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        if (selectedTab == 0) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = ext.cardElevated,
                                        border = BorderStroke(1.dp, ext.borderSubtle),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                "إرسال إشعار جديد للطلبة والمدرسين",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            OutlinedTextField(
                                                value = titleInput,
                                                onValueChange = { titleInput = it.take(150) },
                                                label = { Text("عنوان الإشعار") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth().testTag("admin_notif_title_input")
                                            )
                                            OutlinedTextField(
                                                value = bodyInput,
                                                onValueChange = { bodyInput = it.take(2000) },
                                                label = { Text("نص الإشعار") },
                                                minLines = 3,
                                                maxLines = 5,
                                                modifier = Modifier.fillMaxWidth().testTag("admin_notif_body_input")
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                AnnouncementPriority.entries.forEach { prio ->
                                                    FilterChip(
                                                        selected = selectedPriority == prio,
                                                        onClick = { selectedPriority = prio },
                                                        label = { Text(prio.titleAr) }
                                                    )
                                                }
                                            }
                                            Button(
                                                enabled = titleInput.isNotBlank() && bodyInput.isNotBlank(),
                                                onClick = {
                                                    onPublishAnnouncement(
                                                        titleInput,
                                                        bodyInput,
                                                        selectedPriority,
                                                        defaultAuthorName.ifBlank { currentUser.displayName ?: "إدارة التطبيق" }
                                                    )
                                                    titleInput = ""
                                                    bodyInput = ""
                                                },
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("admin_send_notif_btn")
                                            ) {
                                                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("نشر الإشعار الآن", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                items(adminAnnouncements, key = { it.id }) { item ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = ext.cardElevated,
                                        border = BorderStroke(
                                            1.dp,
                                            if (item.active) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else ext.borderSubtle
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(item.title, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        "${item.priorityLevel().titleAr} • ${formatArabicTimestamp(item.sentAt)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = ext.textMuted
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Switch(
                                                        checked = item.active,
                                                        onCheckedChange = { onToggleActive(item.id, it) }
                                                    )
                                                    IconButton(
                                                        onClick = { deleteCandidate = item },
                                                        modifier = Modifier.minimumInteractiveComponentSize()
                                                    ) {
                                                        Icon(Icons.Filled.Delete, contentDescription = "حذف الإشعار", tint = DangerRed)
                                                    }
                                                }
                                            }
                                            Text(item.body, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "تحديث مستند app_config/latest_release في Firebase",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = versionCodeInput,
                                        onValueChange = { versionCodeInput = it.filter(Char::isDigit).take(6) },
                                        label = { Text("latest_version_code") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = versionNameInput,
                                        onValueChange = { versionNameInput = it.take(40) },
                                        label = { Text("latest_version_name") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                OutlinedTextField(
                                    value = apkUrlInput,
                                    onValueChange = { apkUrlInput = it.take(1000) },
                                    label = { Text("apk_url (رابط تحميل APK المباشر)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = notesInput,
                                    onValueChange = { notesInput = it.take(2000) },
                                    label = { Text("release_notes (ملاحظات التحديث)") },
                                    minLines = 3,
                                    maxLines = 5,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("تحديث إجباري (force_update)", fontWeight = FontWeight.Bold)
                                    Switch(checked = forceUpdateInput, onCheckedChange = { forceUpdateInput = it })
                                }
                                Button(
                                    enabled = (versionCodeInput.toIntOrNull() ?: 0) >= 1 && versionNameInput.isNotBlank(),
                                    onClick = {
                                        onPublishRelease(
                                            versionCodeInput.toIntOrNull() ?: 1,
                                            versionNameInput,
                                            notesInput,
                                            forceUpdateInput,
                                            apkUrlInput
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("حفظ إعدادات التحديث في Firebase", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("تأكيد حذف الإشعار", color = DangerRed) },
            text = { Text("هل تريد حذف الإشعار \"${candidate.title}\" نهائياً من قاعدة البيانات؟") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAnnouncement(candidate.id)
                        deleteCandidate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) { Text("حذف", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("إلغاء") }
            }
        )
    }
}

// ==================== Maintenance Lock Screen ("مصطفى عيسى") ====================

@Composable
fun MaintenanceLockScreen(
    maintenanceMessage: String,
    onSevenTapAdminBypass: () -> Unit
) {
    val ext = LocalExtendedColors.current
    var tapCount by remember { mutableStateOf(0) }
    var lastTapMs by remember { mutableStateOf(0L) }

    val clockAnim = rememberInfiniteTransition(label = "maintenance_clock")
    val secondProgress by clockAnim.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sec_hand"
    )
    val minuteProgress by clockAnim.animateFloat(
        initialValue = 0.15f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 36000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "min_hand"
    )
    val gearAngle by clockAnim.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gear_spin"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF061124), Color(0xFF0C1F3F), Color(0xFF061124))
                )
            )
            .systemBarsPadding()
            .padding(24.dp)
            .testTag("maintenance_lock_screen")
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(136.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = WarningAmber.copy(alpha = 0.12f),
                    border = BorderStroke(2.dp, WarningAmber.copy(alpha = 0.8f)),
                    modifier = Modifier.size(124.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = size.minDimension / 2f
                        drawCircle(
                            color = Color.White.copy(alpha = 0.25f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 3f)
                        )
                        for (i in 0 until 12) {
                            val angleRad = (i * 30.0 - 90.0) * PI / 180.0
                            val inner = radius * if (i % 3 == 0) 0.78f else 0.86f
                            val outer = radius * 0.94f
                            drawLine(
                                color = if (i % 3 == 0) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.55f),
                                start = Offset(
                                    center.x + (inner * cos(angleRad)).toFloat(),
                                    center.y + (inner * sin(angleRad)).toFloat()
                                ),
                                end = Offset(
                                    center.x + (outer * cos(angleRad)).toFloat(),
                                    center.y + (outer * sin(angleRad)).toFloat()
                                ),
                                strokeWidth = if (i % 3 == 0) 3.5f else 2f,
                                cap = StrokeCap.Round
                            )
                        }
                        val hourAngle = (minuteProgress * 30.0 + 180.0 - 90.0) * PI / 180.0
                        drawLine(
                            color = Color.White,
                            start = center,
                            end = Offset(
                                center.x + (radius * 0.48f * cos(hourAngle)).toFloat(),
                                center.y + (radius * 0.48f * sin(hourAngle)).toFloat()
                            ),
                            strokeWidth = 5.5f,
                            cap = StrokeCap.Round
                        )
                        val minAngle = (minuteProgress * 360.0 - 90.0) * PI / 180.0
                        drawLine(
                            color = Color(0xFF38BDF8),
                            start = center,
                            end = Offset(
                                center.x + (radius * 0.68f * cos(minAngle)).toFloat(),
                                center.y + (radius * 0.68f * sin(minAngle)).toFloat()
                            ),
                            strokeWidth = 4f,
                            cap = StrokeCap.Round
                        )
                        val secAngle = (secondProgress * 360.0 - 90.0) * PI / 180.0
                        drawLine(
                            color = Color(0xFFFBBF24),
                            start = center,
                            end = Offset(
                                center.x + (radius * 0.78f * cos(secAngle)).toFloat(),
                                center.y + (radius * 0.78f * sin(secAngle)).toFloat()
                            ),
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round
                        )
                        drawCircle(color = Color(0xFFFBBF24), radius = 5.5f, center = center)
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0C1F3F),
                    border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "تحديث النظام",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer { rotationZ = gearAngle }
                        )
                    }
                }
            }
            Text(
                text = "التطبيق في وضع الصيانة والتطوير",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = maintenanceMessage.ifBlank {
                            "نعتذر عن إغلاق التطبيق مؤقتاً لإجراء تحديثات وصيانة مهمة. نرجو منكم التحلي بالصبر وسيعود التطبيق للعمل قريباً."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "شكراً لصبركم وتفهمكم — التطوير مستمر لخدمتكم",
                        style = MaterialTheme.typography.labelLarge,
                        color = WarningAmber,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Required Developer Footer Signature + Secret 7-Tap Admin Bypass
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    val now = System.currentTimeMillis()
                    if (now - lastTapMs <= 1500L) {
                        tapCount += 1
                    } else {
                        tapCount = 1
                    }
                    lastTapMs = now
                    if (tapCount >= 7) {
                        tapCount = 0
                        onSevenTapAdminBypass()
                    }
                }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "مصطفى عيسى",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF38BDF8)
            )
        }
    }
}

// ==================== Tamper-Proof Trial Expiration / Suspension Gate ====================

@Composable
fun TrialAccessGateScreen(
    status: TrialAccessStatus,
    currentUser: FirebaseUser?,
    onRefreshAccess: () -> Unit,
    onSevenTapAdminBypass: () -> Unit
) {
    var tapCount by remember { mutableStateOf(0) }
    var lastTapMs by remember { mutableStateOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF071326))
            .systemBarsPadding()
            .padding(24.dp)
            .testTag("trial_access_gate_screen")
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = if (status == TrialAccessStatus.SUSPENDED) Icons.Filled.Block else Icons.Filled.TimerOff,
                contentDescription = null,
                tint = DangerRed,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = if (status == TrialAccessStatus.SUSPENDED) {
                    "تم إيقاف صلاحية هذا الحساب مؤقتاً"
                } else {
                    "انتهت الفترة التجريبية للتطبيق"
                },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (status == TrialAccessStatus.SUSPENDED) {
                    "يرجى التواصل مع الإدارة لإعادة تفعيل الصلاحية."
                } else {
                    "تستخدم الساعة السحابية الموثقة للتحقق من المدة التجريبية. تواصل مع المطور لتجديد الاشتراك أو فتح الوصول الدائم."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRefreshAccess,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("إعادة فحص الصلاحية الآن", fontWeight = FontWeight.Bold)
            }
            if (currentUser == null) {
                GoogleSignInArabicButton(
                    label = "تسجيل الدخول بحساب Google",
                    onAuthSuccess = { onRefreshAccess() },
                    onAuthError = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Text(
            text = "مصطفى عيسى",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF38BDF8).copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .clickable {
                    val now = System.currentTimeMillis()
                    tapCount = if (now - lastTapMs <= 1500L) tapCount + 1 else 1
                    lastTapMs = now
                    if (tapCount >= 7) {
                        tapCount = 0
                        onSevenTapAdminBypass()
                    }
                }
                .padding(12.dp)
        )
    }
}

// ==================== Live Poll / Voting Modal (Disappears Immediately After Vote) ====================

@Composable
fun PollVotingDialog(
    poll: CloudPoll,
    onVote: (pollId: String, optionIndex: Int) -> Unit,
    onDismissTemporarily: () -> Unit
) {
    var selectedOption by remember(poll.id) { mutableStateOf<Int?>(null) }

    Dialog(
        onDismissRequest = onDismissTemporarily,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
            shadowElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .widthIn(max = 500.dp)
                .testTag("poll_voting_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Poll, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "استطلاع رأي سريع",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismissTemporarily) {
                        Icon(Icons.Filled.Close, contentDescription = "لاحقاً")
                    }
                }

                Text(
                    text = poll.question,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    poll.options.forEachIndexed { idx, opt ->
                        val isSel = selectedOption == idx
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                if (isSel) 1.6.dp else 1.dp,
                                if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedOption = idx }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RadioButton(selected = isSel, onClick = { selectedOption = idx })
                                Text(opt, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }

                Button(
                    enabled = selectedOption != null,
                    onClick = {
                        selectedOption?.let { onVote(poll.id, it) }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("submit_poll_vote_btn")
                ) {
                    Icon(Icons.Filled.HowToVote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("إرسال التصويت", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==================== Promotional Banner / Ad Modal ====================

@Composable
fun PromoBannerDialog(
    promo: PromoBannerConfig,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var loadedBitmap by remember(promo.imageUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(promo.imageUrl) {
        val urlStr = promo.imageUrl.trim()
        if (urlStr.startsWith("https://") || urlStr.startsWith("http://")) {
            loadedBitmap = withContext(Dispatchers.IO) {
                runCatching {
                    URL(urlStr).openStream().use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            shadowElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp)
                .testTag("promo_banner_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = promo.title.ifBlank { "إعلان مميز" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
                }

                loadedBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = promo.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }

                if (promo.description.isNotBlank()) {
                    Text(promo.description, style = MaterialTheme.typography.bodyMedium)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (promo.actionUrl.isNotBlank()) {
                        Button(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(promo.actionUrl)))
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(promo.buttonLabel.ifBlank { "زيارة الرابط" }, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (promo.phoneNumber.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${promo.phoneNumber.trim()}")))
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("اتصال")
                        }
                    }
                }
            }
        }
    }
}

/**
 * نافذة التحديث الداخلي الفوري:
 * - تظهر لجميع المدرسين عند نشر تحديث داخلي من لوحة الإدارة
 * - عند الضغط على "تحديث" يتحرك شريط التقدم بسلاسة لمدة 13 ثانية (أو المدة المحددة)
 * - تُضاف أسماء الطلبة حسب الصف والشعبة دون المساس بأي درجات أو سجلات سابقة
 */
@Composable
fun InternalUpdateProgressDialog(
    title: String,
    description: String,
    durationSec: Int = 13,
    includesSchoolRoster: Boolean,
    onApplyInternalMerge: () -> com.example.data.RosterMergeResult,
    onDismiss: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val safeDurationSec = durationSec.coerceIn(3, 30)
    var started by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var mergeResult by remember { mutableStateOf<com.example.data.RosterMergeResult?>(null) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(started, safeDurationSec) {
        if (!started || finished) return@LaunchedEffect
        val totalMs = safeDurationSec * 1000L
        val stepMs = 50L
        var elapsed = 0L
        var mergedOnce = false
        while (elapsed < totalMs) {
            kotlinx.coroutines.delay(stepMs)
            elapsed += stepMs
            val frac = (elapsed.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            progress = frac
            if (!mergedOnce && frac >= 0.45f) {
                mergedOnce = true
                mergeResult = onApplyInternalMerge()
            }
        }
        if (!mergedOnce) {
            mergeResult = onApplyInternalMerge()
        }
        progress = 1f
        finished = true
    }

    Dialog(
        onDismissRequest = { if (!started || finished) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !started || finished,
            dismissOnClickOutside = !started || finished,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
            shadowElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 480.dp)
                .testTag("internal_update_progress_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (finished) Icons.Filled.CheckCircle else Icons.Filled.SystemUpdateAlt,
                                contentDescription = null,
                                tint = if (finished) SuccessGreen else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title.ifBlank { "تحديث داخلي للنظام وقوائم الطلبة" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "تحديث مباشر داخل التطبيق ($safeDurationSec ثانية)",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.textMuted
                        )
                    }
                }

                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SuccessGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (includesSchoolRoster) {
                            "ضمان حفظ البيانات: سيتم دمج أسماء طلبة المدرسة حسب الصف والشعبة فقط، مع الحفاظ التام على جميع الدرجات والغيابات والسجلات السابقة دون حذف أي طالب."
                        } else {
                            "ضمان حفظ البيانات: يتم تطبيق التحديث الداخلي مع بقاء جميع الدرجات وأسماء الطلبة وسجلاتك محفوظة كما هي."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                if (started) {
                    val percent = (progress * 100f).toInt().coerceIn(0, 100)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = when {
                                    finished -> "اكتمل التحديث الداخلي بنجاح ✓"
                                    progress < 0.45f -> "جاري تحضير قوائم الصفوف والشعب..."
                                    progress < 0.85f -> "جاري دمج الأسماء مع الحفاظ على الدرجات..."
                                    else -> "جاري تثبيت التحديث الداخلي..."
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (finished) SuccessGreen else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "$percent%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(50))
                                .testTag("internal_update_linear_progress")
                        )
                    }
                }

                mergeResult?.let { res ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "تمت إضافة ${res.addedCount} طالب جديد • تم الحفاظ على ${res.skippedExistingCount} سجل سابق",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "إجمالي الطلبة الآن: ${res.students.size} طالب",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.textMuted
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!started) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("internal_update_later_btn")
                        ) {
                            Text("لاحقاً")
                        }
                        Button(
                            onClick = { started = true },
                            modifier = Modifier.weight(1.4f).heightIn(min = 48.dp).testTag("internal_update_start_btn")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تحديث", fontWeight = FontWeight.ExtraBold)
                        }
                    } else if (finished) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("internal_update_done_btn")
                        ) {
                            Text("إغلاق ومتابعة العمل", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}


