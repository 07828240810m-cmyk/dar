package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.ActivationCodeRecord
import com.example.data.GradeComparisonRecord
import com.example.data.TeacherAccountRecord
import com.example.data.TeacherChatMessageRecord
import com.example.model.AppState
import com.example.model.GradeLevel
import com.example.model.SectionLetter
import com.example.ui.theme.DangerRed
import com.example.ui.theme.GoldBright
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.utils.GoogleSignInArabicButton
import com.example.utils.GradeCalculator
import com.google.firebase.auth.FirebaseUser
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "تم نسخ $label ✓", Toast.LENGTH_SHORT).show()
}

private fun compressImageUriToJpegBase64(context: Context, uri: Uri, maxChars: Int = 180_000): String? {
    return runCatching {
        val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val maxDim = 860f
        val scale = minOf(1f, maxDim / maxOf(raw.width, raw.height).coerceAtLeast(1))
        val targetW = (raw.width * scale).toInt().coerceAtLeast(1)
        val targetH = (raw.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(raw, targetW, targetH, true) else raw
        for (q in listOf(78, 60, 44, 30)) {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (b64.length <= maxChars) return b64
        }
        null
    }.getOrNull()
}

// ==================== 1. Internal 8-Second Scrolling Update Marquee Strip ====================

@Composable
fun InternalUpdateMarqueeStrip(
    visible: Boolean,
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && text.isNotBlank(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0A244D),
            border = BorderStroke(1.3.dp, Color(0xFF22D3EE)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .testTag("internal_update_marquee_strip")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black,
                    border = BorderStroke(1.dp, Color(0xFF22D3EE)),
                    modifier = Modifier.size(30.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_internal_update_logo),
                        contentDescription = "شعار التحديث",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
                Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(iterations = Int.MAX_VALUE)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "إغلاق الشريط",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ==================== 2. 7-Day Inactivity Warm Dua Welcome-Back Dialog ====================

@Composable
fun InactivityDuaDialog(
    message: String,
    teacherName: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, GoldBright.copy(alpha = 0.7f)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 460.dp)
                .testTag("inactivity_dua_dialog")
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = GoldBright.copy(alpha = 0.16f),
                    border = BorderStroke(1.5.dp, GoldBright),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = GoldBright, modifier = Modifier.size(34.dp))
                    }
                }
                Text(
                    text = if (teacherName.isNotBlank()) "أهلاً بعودتك أ. $teacherName" else "أهلاً بعودتك أستاذنا الكريم",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message.ifBlank {
                            "أهلاً بك يا أستاذنا العزيز! غيابك عنا لفترة يشتاق لجهودك. نسأل الله أن يبارك في وقتك وجهدك وعلمك."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("آمين، متابعة العمل ✓", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==================== 3. Teacher Specialty & Direct Chat v2 (50/day, DMs, Read Ticks, Images) ====================

private data class ChatDmPeer(
    val uid: String,
    val accountKey: String,
    val displayName: String
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TeacherChatDialog(
    currentUser: FirebaseUser?,
    specialtyId: String,
    specialtyTitleAr: String,
    gradeId: String,
    messages: List<TeacherChatMessageRecord>,
    remainingToday: Int,
    expiryHours: Int,
    onSendMessage: (String) -> Unit,
    onDismiss: () -> Unit,
    directMessages: List<TeacherChatMessageRecord> = emptyList(),
    isOnlineVisible: Boolean = true,
    onToggleOnlineVisible: (Boolean) -> Unit = {},
    dmThreadIdFor: (String) -> String = { "DM_$it" },
    onFindUserByUsername: (String, (Result<TeacherAccountRecord>) -> Unit) -> Unit = { _, cb ->
        cb(Result.failure(IllegalStateException("البحث غير متاح")))
    },
    onSendMessageV2: ((
        text: String,
        threadId: String,
        recipientAccountKey: String,
        recipientUid: String,
        recipientName: String,
        imageBase64: String,
        onSent: () -> Unit
    ) -> Unit)? = null,
    onEditMessage: (String, String) -> Unit = { _, _ -> },
    onDeleteForMe: (String) -> Unit = {},
    onDeleteForEveryone: (TeacherChatMessageRecord) -> Unit = {},
    onMarkDeliveredAndRead: (List<TeacherChatMessageRecord>, Boolean) -> Unit = { _, _ -> }
) {
    val ext = LocalExtendedColors.current
    val context = LocalContext.current
    var chatTab by remember { mutableStateOf(0) } // 0 = غرفة الاختصاص, 1 = محادثات خاصة
    var draft by remember { mutableStateOf("") }
    var filterSameGradeOnly by remember { mutableStateOf(false) }
    var pendingImageBase64 by remember { mutableStateOf("") }
    var peerSearchUsername by remember { mutableStateOf("") }
    var peerSearchError by remember { mutableStateOf<String?>(null) }
    var peerSearchBusy by remember { mutableStateOf(false) }
    var activeDmPeer by remember { mutableStateOf<ChatDmPeer?>(null) }
    var actionTargetMsg by remember { mutableStateOf<TeacherChatMessageRecord?>(null) }
    var editingMsg by remember { mutableStateOf<TeacherChatMessageRecord?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = compressImageUriToJpegBase64(context, uri)
            if (encoded != null) {
                pendingImageBase64 = encoded
            } else {
                Toast.makeText(context, "حجم الصورة كبير جداً، اختر صورة أصغر.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val knownDmPeers = remember(directMessages, currentUser?.uid, activeDmPeer) {
        val myUid = currentUser?.uid.orEmpty()
        val map = linkedMapOf<String, ChatDmPeer>()
        activeDmPeer?.let { map[it.uid] = it }
        directMessages.asReversed().forEach { msg ->
            if (myUid.isNotBlank() && myUid !in msg.deletedForUids) {
                val isMine = msg.senderUid == myUid
                val peerUid = if (isMine) msg.recipientUid else msg.senderUid
                val peerName = if (isMine) msg.recipientName else msg.senderName
                val peerKey = if (isMine) msg.recipientAccountKey else msg.senderUsername
                if (peerUid.isNotBlank() && peerUid != myUid && peerUid !in map) {
                    map[peerUid] = ChatDmPeer(
                        uid = peerUid,
                        accountKey = peerKey,
                        displayName = peerName.ifBlank { peerKey.ifBlank { "مدرس" } }
                    )
                }
            }
        }
        map.values.toList()
    }

    val activeDmThreadId = remember(activeDmPeer, dmThreadIdFor) {
        activeDmPeer?.let { dmThreadIdFor(it.uid) }.orEmpty()
    }

    val displayedMsgs = remember(
        chatTab,
        messages,
        directMessages,
        filterSameGradeOnly,
        gradeId,
        activeDmThreadId,
        currentUser?.uid
    ) {
        val myUid = currentUser?.uid.orEmpty()
        if (chatTab == 0) {
            val visible = messages.filter { myUid !in it.deletedForUids }
            if (filterSameGradeOnly) visible.filter { it.gradeId.equals(gradeId, ignoreCase = true) }
            else visible
        } else {
            if (activeDmThreadId.isBlank()) emptyList()
            else directMessages.filter { it.threadId == activeDmThreadId && myUid !in it.deletedForUids }
        }
    }

    LaunchedEffect(currentUser?.uid, displayedMsgs.size, chatTab, activeDmThreadId) {
        if (currentUser != null && displayedMsgs.isNotEmpty()) {
            onMarkDeliveredAndRead(displayedMsgs, true)
        }
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
                .fillMaxHeight(0.90f)
                .padding(horizontal = 12.dp)
                .widthIn(max = 600.dp)
                .testTag("teacher_chat_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                text = if (chatTab == 0) "ملتقى مدرسي $specialtyTitleAr" else "محادثات المدرسين الخاصة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "الحد: 50 رسالة (25 لكل طرف) • المتبقي: $remainingToday • حذف بعد $expiryHours س",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.textMuted
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isOnlineVisible) "نشط الآن" else "مخفي",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOnlineVisible) Color(0xFF16A34A) else ext.textMuted,
                                fontWeight = FontWeight.Bold
                            )
                            Switch(
                                checked = isOnlineVisible,
                                onCheckedChange = onToggleOnlineVisible,
                                modifier = Modifier.testTag("chat_online_visibility_switch")
                            )
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
                    }
                }

                TabRow(selectedTabIndex = chatTab) {
                    Tab(
                        selected = chatTab == 0,
                        onClick = { chatTab = 0 },
                        text = { Text("غرفة الاختصاص") }
                    )
                    Tab(
                        selected = chatTab == 1,
                        onClick = { chatTab = 1 },
                        text = {
                            Text(
                                if (knownDmPeers.isNotEmpty()) "محادثات خاصة (${knownDmPeers.size})"
                                else "محادثات خاصة"
                            )
                        }
                    )
                }

                if (chatTab == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        FilterChip(
                            selected = !filterSameGradeOnly,
                            onClick = { filterSameGradeOnly = false },
                            label = { Text("كل المراحل ($specialtyTitleAr)") }
                        )
                        FilterChip(
                            selected = filterSameGradeOnly,
                            onClick = { filterSameGradeOnly = true },
                            label = { Text("مرحلتي فقط") }
                        )
                    }
                } else {
                    Column(modifier = Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = peerSearchUsername,
                                onValueChange = {
                                    peerSearchUsername = it.take(40)
                                    peerSearchError = null
                                },
                                label = { Text("ابحث باسم مستخدم الزميل") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("dm_username_search_input")
                            )
                            Button(
                                enabled = !peerSearchBusy && peerSearchUsername.trim().length >= 2,
                                onClick = {
                                    peerSearchBusy = true
                                    peerSearchError = null
                                    onFindUserByUsername(peerSearchUsername.trim()) { res ->
                                        peerSearchBusy = false
                                        res.onSuccess { rec ->
                                            val peerUid = rec.ownerUid
                                            if (peerUid == currentUser?.uid) {
                                                peerSearchError = "لا يمكنك مراسلة نفسك."
                                            } else {
                                                activeDmPeer = ChatDmPeer(
                                                    uid = peerUid,
                                                    accountKey = rec.accountKey,
                                                    displayName = rec.teacherName.ifBlank { rec.usernameDisplay.ifBlank { rec.accountKey } }
                                                )
                                                peerSearchUsername = ""
                                            }
                                        }.onFailure { err ->
                                            peerSearchError = err.localizedMessage ?: "لم يتم العثور على المستخدم"
                                        }
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("dm_find_user_btn")
                            ) {
                                Text(if (peerSearchBusy) "..." else "مراسلة")
                            }
                        }
                        peerSearchError?.let {
                            Text(it, color = DangerRed, style = MaterialTheme.typography.labelSmall)
                        }
                        if (knownDmPeers.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                knownDmPeers.forEach { peer ->
                                    val selected = activeDmPeer?.uid == peer.uid
                                    FilterChip(
                                        selected = selected,
                                        onClick = { activeDmPeer = peer },
                                        leadingIcon = { Icon(Icons.Filled.Person, null, modifier = Modifier.size(16.dp)) },
                                        label = { Text(peer.displayName, maxLines = 1) }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                if (currentUser == null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        GoogleSignInArabicButton(
                            label = "سجّل الدخول بـ Google للدردشة مع الزملاء",
                            onAuthSuccess = {},
                            onAuthError = {},
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        reverseLayout = true
                    ) {
                        val reversed = displayedMsgs.asReversed()
                        if (reversed.isEmpty()) {
                            item {
                                Text(
                                    text = if (chatTab == 0) {
                                        "لا توجد رسائل نشطة حالياً في تخصص $specialtyTitleAr. ابدأ المحادثة مع زملائك!"
                                    } else if (activeDmPeer == null) {
                                        "ابحث باسم مستخدم الزميل بالأعلى أو اختر محادثة سابقة للبدء."
                                    } else {
                                        "لا توجد رسائل بعد مع ${activeDmPeer?.displayName}. اكتب أول رسالة!"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.textMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                                )
                            }
                        } else {
                            items(reversed, key = { it.id }) { msg ->
                                val isMine = msg.senderUid == currentUser.uid
                                val gradeShort = GradeLevel.entries.firstOrNull { it.id == msg.gradeId }?.shortAr ?: msg.gradeId
                                val timeStr = msg.createdAt?.let {
                                    SimpleDateFormat("hh:mm a", Locale("ar")).format(Date(it.seconds * 1000L))
                                } ?: "الآن"
                                val imgBmp = remember(msg.id, msg.imageBase64, msg.imageExpiresAtMillis, msg.deletedForAll) {
                                    if (!msg.deletedForAll && msg.hasValidImage()) {
                                        runCatching {
                                            val bytes = Base64.decode(msg.imageBase64, Base64.DEFAULT)
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        }.getOrNull()
                                    } else null
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isMine) MaterialTheme.colorScheme.primaryContainer else ext.cardElevated,
                                        border = BorderStroke(1.dp, if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else ext.borderSubtle),
                                        modifier = Modifier
                                            .widthIn(max = 315.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = { actionTargetMsg = msg }
                                            )
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        msg.senderName.ifBlank { "أستاذ" },
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    if (chatTab == 0 && gradeShort.isNotBlank()) {
                                                        Text("($gradeShort)", style = MaterialTheme.typography.labelSmall, color = ext.textMuted)
                                                    }
                                                }
                                                IconButton(
                                                    onClick = { actionTargetMsg = msg },
                                                    modifier = Modifier.size(22.dp)
                                                ) {
                                                    Icon(Icons.Filled.MoreVert, contentDescription = "خيارات الرسالة", modifier = Modifier.size(15.dp), tint = ext.textMuted)
                                                }
                                            }
                                            if (msg.deletedForAll) {
                                                Text(
                                                    "تم حذف هذه الرسالة",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontStyle = FontStyle.Italic,
                                                    color = ext.textMuted
                                                )
                                            } else {
                                                if (imgBmp != null) {
                                                    Image(
                                                        bitmap = imgBmp.asImageBitmap(),
                                                        contentDescription = "صورة مرفقة",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(max = 190.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                    )
                                                    Text(
                                                        "صورة مؤقتة (تختفي بعد 8 ساعات)",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = WarningAmber
                                                    )
                                                }
                                                if (msg.messageText.isNotBlank()) {
                                                    Text(msg.messageText, style = MaterialTheme.typography.bodyMedium)
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.align(Alignment.End),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (msg.editedAtMillis > 0L && !msg.deletedForAll) {
                                                    Text("معدّلة", style = MaterialTheme.typography.labelSmall, color = ext.textMuted)
                                                }
                                                Text(timeStr, style = MaterialTheme.typography.labelSmall, color = ext.textMuted)
                                                if (isMine && !msg.deletedForAll) {
                                                    val isReadByOther = msg.readByUids.any { it != currentUser.uid }
                                                    val isDeliveredToOther = msg.deliveredToUids.any { it != currentUser.uid }
                                                    Icon(
                                                        imageVector = if (isReadByOther || isDeliveredToOther) Icons.Filled.DoneAll else Icons.Filled.Done,
                                                        contentDescription = if (isReadByOther) "تمت القراءة" else if (isDeliveredToOther) "تم التسليم" else "تم الإرسال",
                                                        tint = if (isReadByOther) Color(0xFF16A34A) else ext.textMuted,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (pendingImageBase64.isNotBlank()) {
                        val previewBmp = remember(pendingImageBase64) {
                            runCatching {
                                val bytes = Base64.decode(pendingImageBase64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }.getOrNull()
                        }
                        if (previewBmp != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Image(
                                    bitmap = previewBmp.asImageBitmap(),
                                    contentDescription = "معاينة الصورة",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                                )
                                Text("صورة جاهزة للإرسال (تُحذف بعد 8 ساعات)", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = { pendingImageBase64 = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "إزالة الصورة")
                                }
                            }
                        }
                    }

                    val canSendHere = remainingToday > 0 && (chatTab == 0 || activeDmPeer != null)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            enabled = canSendHere,
                            onClick = {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.size(44.dp).testTag("teacher_chat_attach_image_btn")
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "إرفاق صورة مؤقتة")
                        }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it.take(500) },
                            placeholder = {
                                Text(
                                    when {
                                        remainingToday <= 0 -> "انتهى الحد اليومي (50 رسالة)"
                                        chatTab == 1 && activeDmPeer == null -> "اختر زميلاً أولاً..."
                                        chatTab == 1 -> "رسالة خاصة إلى ${activeDmPeer?.displayName}..."
                                        else -> "اكتب رسالة لزملاء التخصص..."
                                    }
                                )
                            },
                            enabled = canSendHere,
                            maxLines = 3,
                            modifier = Modifier.weight(1f).testTag("teacher_chat_input")
                        )
                        FilledIconButton(
                            enabled = canSendHere && (draft.trim().isNotEmpty() || pendingImageBase64.isNotBlank()),
                            onClick = {
                                val textToSend = draft.trim()
                                val imgToSend = pendingImageBase64
                                if (onSendMessageV2 != null) {
                                    val peer = activeDmPeer
                                    val thread = if (chatTab == 1 && peer != null) activeDmThreadId else "ROOM"
                                    onSendMessageV2(
                                        textToSend,
                                        thread,
                                        if (chatTab == 1 && peer != null) peer.accountKey else "",
                                        if (chatTab == 1 && peer != null) peer.uid else "",
                                        if (chatTab == 1 && peer != null) peer.displayName else "",
                                        imgToSend
                                    ) {
                                        draft = ""
                                        pendingImageBase64 = ""
                                    }
                                } else {
                                    onSendMessage(textToSend)
                                    draft = ""
                                    pendingImageBase64 = ""
                                }
                            },
                            modifier = Modifier.size(48.dp).testTag("teacher_chat_send_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال")
                        }
                    }
                }
            }
        }
    }

    actionTargetMsg?.let { target ->
        val isMine = target.senderUid == currentUser?.uid
        AlertDialog(
            onDismissRequest = { actionTargetMsg = null },
            title = { Text("خيارات الرسالة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (target.messageText.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, "الرسالة", target.messageText)
                                actionTargetMsg = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("نسخ النص") }
                    }
                    if (isMine && !target.deletedForAll) {
                        OutlinedButton(
                            onClick = {
                                editingMsg = target
                                actionTargetMsg = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("تعديل الرسالة") }
                    }
                    OutlinedButton(
                        onClick = {
                            onDeleteForMe(target.id)
                            actionTargetMsg = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("حذف لدي فقط") }
                    if (isMine && !target.deletedForAll) {
                        Button(
                            onClick = {
                                onDeleteForEveryone(target)
                                actionTargetMsg = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("حذف لدى الجميع", color = Color.White) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTargetMsg = null }) { Text("إغلاق") }
            }
        )
    }

    editingMsg?.let { msgToEdit ->
        var editDraft by remember(msgToEdit.id) { mutableStateOf(msgToEdit.messageText) }
        AlertDialog(
            onDismissRequest = { editingMsg = null },
            title = { Text("تعديل الرسالة") },
            text = {
                OutlinedTextField(
                    value = editDraft,
                    onValueChange = { editDraft = it.take(500) },
                    label = { Text("نص الرسالة") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = editDraft.trim().isNotEmpty(),
                    onClick = {
                        onEditMessage(msgToEdit.id, editDraft.trim())
                        editingMsg = null
                    }
                ) { Text("حفظ التعديل") }
            },
            dismissButton = {
                TextButton(onClick = { editingMsg = null }) { Text("إلغاء") }
            }
        )
    }
}

// ==================== 4. Cross-Subject Grade Comparison (Code + Admin Approval) ====================

private fun buildSnapshotStatsJson(
    state: AppState,
    grade: GradeLevel,
    section: SectionLetter,
    ledgerId: String
): String {
    val students = state.students.filter { it.gradeLevel == grade && it.section == section && !it.isTransferred }
    val stats = GradeCalculator.computeSectionStats(students, state.gradebookSettings, ledgerId = ledgerId)
    val rows = JSONArray()
    students.take(60).forEach { st ->
        val r = GradeCalculator.computeForLedger(st, state.gradebookSettings, ledgerId)
        rows.put(
            JSONObject().apply {
                put("name", st.fullName)
                put("effort", r.annualEffort ?: -1.0)
                put("final", r.finalGrade ?: -1.0)
            }
        )
    }
    return JSONObject().apply {
        put("count", students.size)
        put("graded", stats.gradedStudents)
        put("passRate", stats.passRatePercent.toDouble())
        put("average", stats.averageScore)
        put("students", rows)
    }.toString()
}

@Composable
fun CrossSubjectComparisonDialog(
    currentUser: FirebaseUser?,
    state: AppState,
    selectedGrade: GradeLevel,
    selectedSection: SectionLetter,
    activeLedgerId: String,
    myComparisons: List<GradeComparisonRecord>,
    joinedComparisons: List<GradeComparisonRecord>,
    busy: Boolean,
    actionMessage: String?,
    onClearMessage: () -> Unit,
    onCreateComparison: (
        creatorName: String,
        creatorSubject: String,
        creatorGradeTitle: String,
        creatorSectionScope: String,
        creatorStatsJson: String,
        onCreated: (GradeComparisonRecord) -> Unit
    ) -> Unit,
    onJoinComparison: (
        code: String,
        partnerName: String,
        partnerSubject: String,
        partnerGradeTitle: String,
        partnerSectionScope: String,
        partnerStatsJson: String,
        onJoined: (GradeComparisonRecord) -> Unit
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    var joinCode by remember { mutableStateOf("") }
    var lastCode by remember { mutableStateOf<String?>(null) }
    var viewingRecord by remember { mutableStateOf<GradeComparisonRecord?>(null) }

    val ledgerDef = remember(state.profile, activeLedgerId) {
        state.profile.effectiveLedgers().find { it.id == activeLedgerId } ?: state.profile.currentLedgerDef()
    }
    val allCombined = remember(myComparisons, joinedComparisons) {
        (myComparisons + joinedComparisons).distinctBy { it.code }
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
                .testTag("cross_subject_comparison_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.CompareArrows, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("مقارنة الدرجات بين المواد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            Text("شارك كوداً مع مدرس مادة أخرى (تُفتح المقارنة بعد موافقة الإدارة)", style = MaterialTheme.typography.labelSmall, color = ext.textMuted)
                        }
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
                }

                if (currentUser == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GoogleSignInArabicButton(label = "سجّل الدخول بـ Google لاستخدام المقارنة", onAuthSuccess = {}, onAuthError = {}, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    actionMessage?.let { msg ->
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = onClearMessage, modifier = Modifier.size(22.dp)) { Icon(Icons.Filled.Close, null, modifier = Modifier.size(14.dp)) }
                            }
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            Surface(shape = RoundedCornerShape(16.dp), color = ext.cardElevated, border = BorderStroke(1.dp, ext.borderSubtle)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("1. إنشاء كود مقارنة لشعبة (${selectedGrade.shortAr} - ${selectedSection.id})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("المادة الحالية: ${ledgerDef.title}", style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                                    Button(
                                        enabled = !busy,
                                        onClick = {
                                            val json = buildSnapshotStatsJson(state, selectedGrade, selectedSection, ledgerDef.id)
                                            onCreateComparison(
                                                state.profile.teacherName.ifBlank { "مدرس" },
                                                ledgerDef.title.ifBlank { state.profile.subjectName },
                                                selectedGrade.titleAr,
                                                selectedSection.titleAr,
                                                json
                                            ) { rec -> lastCode = rec.code }
                                        },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Filled.AddLink, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("توليد كود مقارنة الآن", fontWeight = FontWeight.Bold)
                                    }
                                    lastCode?.let { c ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("الكود: $c", fontWeight = FontWeight.ExtraBold, color = SuccessGreen)
                                            OutlinedButton(onClick = { copyToClipboard(context, "كود المقارنة", c) }) { Text("نسخ الكود") }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Surface(shape = RoundedCornerShape(16.dp), color = ext.cardElevated, border = BorderStroke(1.dp, ext.borderSubtle)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("2. الانضمام لكود زميلك", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = joinCode,
                                            onValueChange = { joinCode = it.uppercase().take(20) },
                                            label = { Text("مثال: CMP-AB12CD") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            enabled = !busy && joinCode.trim().length >= 5,
                                            onClick = {
                                                val json = buildSnapshotStatsJson(state, selectedGrade, selectedSection, ledgerDef.id)
                                                onJoinComparison(
                                                    joinCode,
                                                    state.profile.teacherName.ifBlank { "مدرس" },
                                                    ledgerDef.title.ifBlank { state.profile.subjectName },
                                                    selectedGrade.titleAr,
                                                    selectedSection.titleAr,
                                                    json
                                                ) { joinCode = "" }
                                            },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) { Text("انضمام") }
                                    }
                                }
                            }
                        }

                        if (allCombined.isNotEmpty()) {
                            item {
                                Text("سجل المقارنات (${allCombined.size}):", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            items(allCombined, key = { it.code }) { rec ->
                                val statusLabel = when (rec.status.uppercase()) {
                                    "OPEN" -> "بانتظار انضمام مدرس آخر"
                                    "PENDING_APPROVAL" -> "بانتظار موافقة الإدارة"
                                    "APPROVED" -> "معتمدة • جاهزة للعرض ✓"
                                    "REJECTED" -> "مرفوضة"
                                    else -> rec.status
                                }
                                val statusCol = when (rec.status.uppercase()) {
                                    "APPROVED" -> SuccessGreen
                                    "REJECTED" -> DangerRed
                                    else -> WarningAmber
                                }
                                Surface(shape = RoundedCornerShape(14.dp), color = ext.cardElevated, border = BorderStroke(1.dp, statusCol.copy(alpha = 0.5f))) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("${rec.code} • ${rec.creatorSubject}", fontWeight = FontWeight.Bold)
                                            Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusCol, fontWeight = FontWeight.Bold)
                                        }
                                        Text("${rec.creatorGradeTitle} (${rec.creatorSectionScope}) — ${rec.creatorName}", style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                                        if (rec.partnerSubject.isNotBlank()) {
                                            Text("ضد: ${rec.partnerSubject} (${rec.partnerName})", style = MaterialTheme.typography.bodySmall, color = InfoBlue)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = { copyToClipboard(context, "كود المقارنة", rec.code) }) { Text("نسخ") }
                                            if (rec.status.equals("APPROVED", ignoreCase = true)) {
                                                Button(onClick = { viewingRecord = rec }) { Text("عرض المقارنة") }
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
    }

    viewingRecord?.let { rec ->
        val cObj = remember(rec.creatorStatsJson) { runCatching { JSONObject(rec.creatorStatsJson) }.getOrNull() ?: JSONObject() }
        val pObj = remember(rec.partnerStatsJson) { runCatching { JSONObject(rec.partnerStatsJson) }.getOrNull() ?: JSONObject() }
        AlertDialog(
            onDismissRequest = { viewingRecord = null },
            title = { Text("مقارنة ${rec.creatorSubject} مع ${rec.partnerSubject}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rec.creatorSubject, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("أ. ${rec.creatorName}", style = MaterialTheme.typography.labelSmall)
                            Text("الطلبة: ${cObj.optInt("count")}")
                            Text("نسبة النجاح: ${cObj.optDouble("passRate", 0.0).toInt()}%")
                            Text("المعدل: ${"%.1f".format(cObj.optDouble("average", 0.0))}")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rec.partnerSubject, fontWeight = FontWeight.Bold, color = InfoBlue)
                            Text("أ. ${rec.partnerName}", style = MaterialTheme.typography.labelSmall)
                            Text("الطلبة: ${pObj.optInt("count")}")
                            Text("نسبة النجاح: ${pObj.optDouble("passRate", 0.0).toInt()}%")
                            Text("المعدل: ${"%.1f".format(pObj.optDouble("average", 0.0))}")
                        }
                    }
                    val cStudents = cObj.optJSONArray("students")
                    val pStudents = pObj.optJSONArray("students")
                    if (cStudents != null && pStudents != null) {
                        val pMap = mutableMapOf<String, Double>()
                        for (i in 0 until pStudents.length()) {
                            val o = pStudents.optJSONObject(i) ?: continue
                            val nm = o.optString("name").trim()
                            val score = o.optDouble("final", -1.0).takeIf { it >= 0 } ?: o.optDouble("effort", -1.0)
                            if (nm.isNotBlank() && score >= 0) pMap[nm] = score
                        }
                        val matched = mutableListOf<Triple<String, Double, Double>>()
                        for (i in 0 until cStudents.length()) {
                            val o = cStudents.optJSONObject(i) ?: continue
                            val nm = o.optString("name").trim()
                            val cScore = o.optDouble("final", -1.0).takeIf { it >= 0 } ?: o.optDouble("effort", -1.0)
                            val pScore = pMap[nm]
                            if (cScore >= 0 && pScore != null) matched.add(Triple(nm, cScore, pScore))
                        }
                        if (matched.isNotEmpty()) {
                            HorizontalDivider()
                            Text("مقارنة الطلبة المشتركين (${matched.size}):", fontWeight = FontWeight.Bold)
                            matched.take(25).forEach { (nm, s1, s2) ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(nm, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${s1.toInt()} | ${s2.toInt()}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewingRecord = null }) { Text("إغلاق") } }
        )
    }
}

// ==================== 5. Activation Code Claim (1-Device Lock) & Display Name Request ====================

@Composable
fun ActivationCodeClaimDialog(
    currentUser: FirebaseUser?,
    busy: Boolean,
    actionMessage: String?,
    onClaimCode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("تفعيل الحساب بكود من الإدارة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text("يرتبط الكود بجهاز واحد فقط حتى تسجيل الخروج أو فك الارتباط من الإدارة.", style = MaterialTheme.typography.bodySmall)
                if (currentUser == null) {
                    GoogleSignInArabicButton(label = "سجّل الدخول بـ Google أولاً", onAuthSuccess = {}, onAuthError = {}, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase().take(24) },
                        label = { Text("كود التفعيل (مثال: ACT-XXXX)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    actionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                        Button(
                            enabled = !busy && code.trim().length >= 4,
                            onClick = { onClaimCode(code) },
                            modifier = Modifier.weight(1f)
                        ) { Text("تفعيل") }
                    }
                }
            }
        }
    }
}

@Composable
fun TeacherNameChangeDialog(
    currentName: String,
    initialChosen: Boolean,
    hasWeeklyGift: Boolean,
    onSubmit: (requestedName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var requested by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (!initialChosen) "اختيار اسم المدرس (المرة الأولى)" else "تغيير اسم المدرس") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = when {
                        !initialChosen -> "يمكنك اختيار اسمك بحرية للمرة الأولى الآن."
                        hasWeeklyGift -> "لديك صلاحية التبديل الأسبوعي، سيتم اعتماد الاسم الجديد فوراً."
                        else -> "تم اختيار الاسم مسبقاً؛ التعديل الآن يُرسل طلباً للمراجعة ويصلك إشعار فور الموافقة."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = requested,
                    onValueChange = { requested = it.take(80) },
                    label = { Text("الاسم الكامل الجديد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = requested.trim().length >= 2,
                onClick = {
                    onSubmit(requested.trim())
                    onDismiss()
                }
            ) {
                Text(if (!initialChosen || hasWeeklyGift) "حفظ الاسم" else "إرسال طلب التغيير")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
