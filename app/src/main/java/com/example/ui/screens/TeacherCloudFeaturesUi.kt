package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.DeveloperFeedbackRecord
import com.example.data.FeedbackCategory
import com.example.data.ParentReportRecord
import com.example.data.ParentReportVisibility
import com.example.data.TeacherAccountRecord
import com.example.data.TrialAccessStatus
import com.example.model.AppState
import com.example.model.Student
import com.example.ui.theme.DangerRed
import com.example.ui.theme.GoldBright
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.utils.BackupCryptoHelper
import com.example.utils.ExportManager
import com.example.utils.GoogleSignInArabicButton
import com.example.utils.GradeCalculator
import com.google.firebase.auth.FirebaseUser
import org.json.JSONObject

private fun copyTextWithToast(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "تم نسخ $label ✓", Toast.LENGTH_SHORT).show()
}

// ==================== 1. Cloud Account + Encrypted Backup & Restore ====================

@Composable
fun TeacherAccountBackupDialog(
    currentUser: FirebaseUser?,
    initialUsername: String,
    autoUsername: String = "",
    passwordChangeIntervalDays: Int = 3,
    teacherAccount: TeacherAccountRecord?,
    trialStatus: TrialAccessStatus,
    remainingTrialDays: Int,
    studentCount: Int,
    isOnline: Boolean,
    pendingOutboxCount: Int,
    busy: Boolean,
    actionMessage: String?,
    onClearMessage: () -> Unit,
    onBackupEncrypted: (username: String, password: String) -> Unit,
    onRestoreEncrypted: (username: String, password: String) -> Unit,
    onChangePassword: (username: String, newPassword: String) -> Unit = { _, _ -> },
    onOpenActivationCode: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val ext = LocalExtendedColors.current
    val context = LocalContext.current
    var username by remember(initialUsername, autoUsername) {
        mutableStateOf(
            initialUsername.ifBlank { teacherAccount?.usernameDisplay.orEmpty() }.ifBlank { autoUsername }
        )
    }
    var password by remember { mutableStateOf("1234") }
    var passVisible by remember { mutableStateOf(false) }
    var showChangePass by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 560.dp)
                .testTag("cloud_account_backup_dialog")
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                "الحساب السحابي والنسخ الاحتياطي",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "نقل كامل البيانات والدرجات والألوان لأي هاتف أو تطبيق آخر",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.textMuted
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                    }
                }

                // Connectivity + Account Metrics Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ext.cardElevated,
                    border = BorderStroke(1.dp, ext.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isOnline) "الحالة: متصل بالسحابة ✓" else "الحالة: وضع أوفلاين",
                                color = if (isOnline) SuccessGreen else WarningAmber,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "النقاط: ${teacherAccount?.points ?: 0}",
                                color = GoldBright,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        if (autoUsername.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "المعرّف التلقائي: $autoUsername • كلمة المرور الافتراضية: 1234",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = { copyTextWithToast(context, "اسم المستخدم", autoUsername) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) { Text("نسخ", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        if (teacherAccount?.promotionTitle?.isNotBlank() == true) {
                            Text(
                                text = "الرتبة والوسام: ${teacherAccount.promotionTitle}",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldBright,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = when (trialStatus) {
                                TrialAccessStatus.OPEN -> "صلاحية الوصول: مفتوح بالكامل ✓"
                                TrialAccessStatus.TRIAL_ACTIVE -> "فترة تجريبية نشطة (متبقي $remainingTrialDays يوم)"
                                TrialAccessStatus.TRIAL_EXPIRED -> "انتهت الفترة التجريبية"
                                TrialAccessStatus.SUSPENDED -> "الحساب موقوف مؤقتاً"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textMuted
                        )
                        if (pendingOutboxCount > 0) {
                            Text(
                                text = "عمليات بانتظار المزامنة التلقائية: $pendingOutboxCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarningAmber
                            )
                        }
                    }
                }

                // Clear 2-Step Quick Guide
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "طريقة الاستخدام: (1) سجل بحساب Google، ثم (2) اكتب اسم مستخدم وكلمة مرور واضغط «نسخ احتياطي» لحفظ جميع درجاتك وألوانك، وعند استخدام هاتف آخر أدخل نفس الاسم وكلمة المرور واضغط «استرجاع».",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textMuted,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                if (currentUser == null) {
                    GoogleSignInArabicButton(
                        label = "تسجيل الدخول بـ Google لحفظ واستدعاء بياناتك",
                        onAuthSuccess = { localError = null },
                        onAuthError = { localError = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it.take(50)
                            localError = null
                        },
                        label = { Text("اسم المستخدم الخاص بك (عربي أو إنجليزي)") },
                        placeholder = { Text("مثال: ${autoUsername.ifBlank { "ABXY" }} أو ahmed_phys") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        trailingIcon = {
                            if (username.isNotBlank()) {
                                IconButton(
                                    onClick = { copyTextWithToast(context, "اسم المستخدم", username) },
                                    modifier = Modifier.testTag("cloud_copy_username_btn")
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "نسخ اسم المستخدم", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("cloud_username_input")
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it.take(64)
                            localError = null
                        },
                        label = { Text("كلمة المرور السحابية (الافتراضية 1234)") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passVisible = !passVisible }) {
                                Icon(
                                    imageVector = if (passVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "إظهار/إخفاء كلمة المرور"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("cloud_password_input")
                    )

                    Text(
                        text = "سيتم تشفير جميع السجلات ($studentCount طالب، الدرجات، الجدول، الألوان، التفضيلات وبنك الأسئلة) بكلمة المرور هذه قبل الرفع لقاعدة البيانات.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.textMuted
                    )

                    localError?.let {
                        Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall)
                    }

                    actionMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = onClearMessage, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = "إغلاق", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Button(
                        enabled = !busy,
                        onClick = {
                            if (username.trim().length < 2) {
                                localError = "أدخل اسم مستخدم لا يقل عن حرفين."
                            } else if (password.length < 4) {
                                localError = "أدخل كلمة مرور من 4 أحرف/أرقام على الأقل."
                            } else {
                                onBackupEncrypted(username.trim(), password)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cloud_backup_now_btn")
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("نسخ البيانات احتياطياً للسحابة", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            if (username.trim().length < 2 || password.length < 4) {
                                localError = "أدخل اسم المستخدم وكلمة المرور لاستدعاء بياناتك."
                            } else {
                                onRestoreEncrypted(username.trim(), password)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cloud_restore_now_btn")
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("استدعاء البيانات (باسم المستخدم وكلمة المرور)", fontWeight = FontWeight.Bold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showChangePass = !showChangePass },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                        ) {
                            Icon(Icons.Filled.Password, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("تغيير المرور", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onOpenActivationCode,
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                        ) {
                            Icon(Icons.Filled.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("تفعيل بكود", maxLines = 1)
                        }
                    }

                    if (showChangePass) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ext.cardElevated,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "تغيير كلمة المرور (مسموح مرة كل $passwordChangeIntervalDays أيام):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    value = newPassword,
                                    onValueChange = { newPassword = it.take(64) },
                                    label = { Text("كلمة المرور الجديدة (4 أحرف فأكثر)") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    enabled = !busy && username.trim().length >= 2 && newPassword.trim().length >= 4,
                                    onClick = {
                                        onChangePassword(username.trim(), newPassword.trim())
                                        password = newPassword.trim()
                                        newPassword = ""
                                        showChangePass = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("حفظ كلمة المرور الجديدة")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 2. Referrals, Points & Star Rating ====================

@Composable
fun ReferralAndRatingDialog(
    currentUser: FirebaseUser?,
    teacherAccount: TeacherAccountRecord?,
    busy: Boolean,
    actionMessage: String?,
    onClearMessage: () -> Unit,
    onRedeemCode: (String) -> Unit,
    onSubmitRating: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    var friendCode by remember { mutableStateOf("") }
    var stars by remember(teacherAccount?.ratingStars) {
        mutableStateOf((teacherAccount?.ratingStars ?: 5).coerceIn(1, 5))
    }
    var comment by remember(teacherAccount?.ratingComment) {
        mutableStateOf(teacherAccount?.ratingComment.orEmpty())
    }
    val myCode = remember(currentUser?.uid, teacherAccount?.referralCode) {
        teacherAccount?.referralCode?.takeIf { it.isNotBlank() }
            ?: currentUser?.uid?.let { BackupCryptoHelper.deriveDeterministicReferralCode(it) }.orEmpty()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, GoldBright.copy(alpha = 0.65f)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 560.dp)
                .testTag("referral_and_rating_dialog")
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
                        Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = GoldBright)
                        Text(
                            "النقاط والإحالات وتقييم التطبيق",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                    }
                }

                if (currentUser == null) {
                    GoogleSignInArabicButton(
                        label = "سجّل الدخول بـ Google لحفظ نقاطك وتقييمك",
                        onAuthSuccess = {},
                        onAuthError = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Points & My Referral Code Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = GoldBright.copy(alpha = 0.12f),
                        border = BorderStroke(1.2.dp, GoldBright.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("رصيد نقاطك الحالي", fontWeight = FontWeight.Bold)
                                Text(
                                    "${teacherAccount?.points ?: 0} نقطة ★",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = GoldBright,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = "كود الإحالة الخاص بك (شارك مع زملائك ليربح كل منكما +50 نقطة):",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.textMuted
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(
                                        text = myCode,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = { copyTextWithToast(context, "كود الإحالة", myCode) }) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("نسخ")
                                    }
                                    Button(onClick = {
                                        ExportManager.shareText(
                                            context,
                                            "دعوة لتطبيق رفيق المدرس",
                                            "انضم لتطبيق رفيق المدرس واستخدم كود الإحالة ($myCode) لتحصل فوراً على 50 نقطة إضافية!"
                                        )
                                    }) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("مشاركة")
                                    }
                                }
                            }
                            Text(
                                text = "عدد الإحالات المكتملة: ${teacherAccount?.referralCount ?: 0}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.textMuted
                            )
                        }
                    }

                    // Claim Friend Code
                    if (teacherAccount?.referredByCode.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = friendCode,
                                onValueChange = { friendCode = it.uppercase().take(20) },
                                label = { Text("كود إحالة زميلك") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("friend_referral_input")
                            )
                            Button(
                                enabled = !busy && friendCode.trim().length >= 4,
                                onClick = { onRedeemCode(friendCode) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("تفعيل")
                            }
                        }
                    } else {
                        Text(
                            text = "تم تفعيل كود الإحالة: ${teacherAccount?.referredByCode} ✓",
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()

                    // Star Rating
                    Text("قيّم تجربتك مع التطبيق:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { idx ->
                            IconButton(
                                onClick = { stars = idx },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (idx <= stars) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    contentDescription = "$idx نجوم",
                                    tint = if (idx <= stars) GoldBright else ext.textMuted,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it.take(500) },
                        label = { Text("رأيك أو ملاحظتك السريعة (اختياري)") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    actionMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = onClearMessage, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    Button(
                        enabled = !busy,
                        onClick = { onSubmitRating(stars, comment) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("submit_star_rating_btn")
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إرسال التقييم الآن", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==================== 3. Developer Feedback & Complaints (NO Admin Mention!) ====================

@Composable
fun DeveloperFeedbackDialog(
    currentUser: FirebaseUser?,
    lockedUntilMillis: Long,
    verifiedNowMillis: Long,
    lastComplaintText: String,
    busy: Boolean,
    actionMessage: String?,
    onClearMessage: () -> Unit,
    onSubmitFeedback: (FeedbackCategory, String, (DeveloperFeedbackRecord) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val ext = LocalExtendedColors.current
    var category by remember { mutableStateOf(FeedbackCategory.SUGGESTION) }
    var message by remember { mutableStateOf("") }
    var sentConfirmation by remember { mutableStateOf<String?>(null) }

    val wordCount = remember(message) { BackupCryptoHelper.countWords(message) }
    val isThreeDayLocked = lockedUntilMillis > verifiedNowMillis
    val remainingMillis = (lockedUntilMillis - verifiedNowMillis).coerceAtLeast(0L)
    val remainingHours = (remainingMillis / 3_600_000L).toInt()
    val remainingDays = remainingHours / 24
    val remHoursPart = remainingHours % 24

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 560.dp)
                .testTag("developer_feedback_dialog")
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
                        Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                "مراسلة المطور — الاقتراحات والشكاوى",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "صندوق تواصل مباشر وسري مع فريق تطوير التطبيق",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.textMuted
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                    }
                }

                if (currentUser == null) {
                    GoogleSignInArabicButton(
                        label = "سجّل الدخول بـ Google لمراسلة المطور",
                        onAuthSuccess = {},
                        onAuthError = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (isThreeDayLocked) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = WarningAmber.copy(alpha = 0.14f),
                        border = BorderStroke(1.4.dp, WarningAmber),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Schedule, contentDescription = null, tint = WarningAmber)
                                Text(
                                    "تذكرة شكوى مفعّلة ومثبتة قيد المتابعة",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = WarningAmber
                                )
                            }
                            Text(
                                text = "تم استلام رسالتك المفصلة (> 100 كلمة). تبقى هذه النافذة مفتوحة ومثبتة لمدة 3 أيام لضمان مراجعة المطور الكاملة لرسالتك.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "الوقت المتبقي لإغلاق المتابعة: $remainingDays يوم و $remHoursPart ساعة",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            if (lastComplaintText.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, ext.borderSubtle),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = lastComplaintText,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "تم مراسلة المطور بنجاح ✓",
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FeedbackCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat.titleAr) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it.take(5000) },
                        label = { Text("اكتب اقتراحك أو شكواك للمطور بالتفصيل") },
                        minLines = 4,
                        maxLines = 7,
                        modifier = Modifier.fillMaxWidth().testTag("developer_feedback_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "عدد الكلمات: $wordCount كلمة",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (wordCount > 100) WarningAmber else ext.textMuted,
                            fontWeight = FontWeight.Bold
                        )
                        if (wordCount > 100) {
                            Text(
                                text = "رسالة مفصلة (>100 كلمة) — ستبقى مفتوحة 3 أيام",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarningAmber
                            )
                        }
                    }

                    (sentConfirmation ?: actionMessage)?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SuccessGreen.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, SuccessGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(msg, color = SuccessGreen, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        sentConfirmation = null
                                        onClearMessage()
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    Button(
                        enabled = !busy && message.trim().length >= 2,
                        onClick = {
                            onSubmitFeedback(category, message) {
                                message = ""
                                sentConfirmation = "تم مراسلة المطور بنجاح ✓"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("send_developer_feedback_btn")
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إرسال للمطور", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==================== 4. Parent Read-Only Cloud Report (Create & View) ====================

@Composable
fun ShareParentReportDialog(
    student: Student,
    state: AppState,
    activeLedgerId: String,
    myReports: List<ParentReportRecord>,
    busy: Boolean,
    onPublishReport: (
        teacherName: String,
        subjectName: String,
        studentName: String,
        gradeTitle: String,
        sectionId: String,
        visibilityJson: String,
        reportDataJson: String,
        onDone: (Result<ParentReportRecord>) -> Unit
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    var vis by remember { mutableStateOf(ParentReportVisibility()) }
    var generatedReport by remember { mutableStateOf<ParentReportRecord?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val ledgerDef = remember(state.profile, activeLedgerId) {
        state.profile.effectiveLedgers().find { it.id == activeLedgerId }
            ?: state.profile.currentLedgerDef()
    }
    val calc = remember(student, state.gradebookSettings, ledgerDef.id) {
        GradeCalculator.computeForLedger(student, state.gradebookSettings, ledgerDef.id)
    }
    val r = state.gradebookSettings.enableSmartRounding
    val absencesCount = remember(state.absences, student.id) {
        state.absences.count { it.studentId == student.id }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 560.dp)
                .testTag("share_parent_report_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "تقرير ولي الأمر السحابي (للقراءة فقط)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "${student.fullName} • ${student.gradeLevel.shortAr} (${student.section.id})",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.textMuted
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
                }

                Text("اختر العناصر المسموح لولي الأمر بمشاهدتها:", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    VisibilityCheckRow("درجات الفصل الأول (${GradeCalculator.formatScore(calc.term1Average, r)})", vis.showTerm1) {
                        vis = vis.copy(showTerm1 = it)
                    }
                    VisibilityCheckRow("امتحان نصف السنة (${GradeCalculator.formatScore(calc.midYearEffective, r)})", vis.showMidYear) {
                        vis = vis.copy(showMidYear = it)
                    }
                    VisibilityCheckRow("درجات الفصل الثاني (${GradeCalculator.formatScore(calc.term2Average, r)})", vis.showTerm2) {
                        vis = vis.copy(showTerm2 = it)
                    }
                    VisibilityCheckRow("السعي السنوي (${GradeCalculator.formatScore(calc.annualEffort, r)})", vis.showAnnualEffort) {
                        vis = vis.copy(showAnnualEffort = it)
                    }
                    VisibilityCheckRow("الدرجة النهائية (${GradeCalculator.formatScore(calc.finalGrade, r)})", vis.showFinalGrade) {
                        vis = vis.copy(showFinalGrade = it)
                    }
                    VisibilityCheckRow("التحضير اليومي والالتزام", vis.showDailyPrep) {
                        vis = vis.copy(showDailyPrep = it)
                    }
                    VisibilityCheckRow("السلوك والغيابات ($absencesCount غياب)", vis.showBehaviorAndAbsences) {
                        vis = vis.copy(showBehaviorAndAbsences = it)
                    }
                    VisibilityCheckRow("الأوسمة والملاحظات", vis.showBadgesAndNotes) {
                        vis = vis.copy(showBadgesAndNotes = it)
                    }
                }

                errorMsg?.let { Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall) }

                generatedReport?.let { rep ->
                    val deepLink = "rafiqteacher://report/${rep.reportCode}"
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SuccessGreen.copy(alpha = 0.14f),
                        border = BorderStroke(1.2.dp, SuccessGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("تم إنشاء تقرير ولي الأمر بنجاح (صالح لمدة 3 أيام) ✓", color = SuccessGreen, fontWeight = FontWeight.Bold)
                            Text("كود التقرير: ${rep.reportCode}", fontWeight = FontWeight.ExtraBold)
                            Text("الرابط: $deepLink", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { copyTextWithToast(context, "كود التقرير", rep.reportCode) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("نسخ الكود", maxLines = 1) }
                                OutlinedButton(
                                    onClick = { copyTextWithToast(context, "الرابط", deepLink) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("نسخ الرابط", maxLines = 1) }
                                Button(
                                    onClick = {
                                        ExportManager.shareText(
                                            context,
                                            "تقرير الطالب ${student.fullName}",
                                            "تقرير الطالب: ${student.fullName}\nالمادة: ${rep.subjectName}\nكود التقرير: ${rep.reportCode}\nافتح عبر الرابط داخل تطبيق رفيق المدرس:\n$deepLink\n(صالح لمدة 3 أيام من الآن)"
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("مشاركة", maxLines = 1) }
                            }
                        }
                    }
                }

                Button(
                    enabled = !busy,
                    onClick = {
                        errorMsg = null
                        val visJson = JSONObject().apply {
                            put("showTerm1", vis.showTerm1)
                            put("showMidYear", vis.showMidYear)
                            put("showTerm2", vis.showTerm2)
                            put("showAnnualEffort", vis.showAnnualEffort)
                            put("showFinalGrade", vis.showFinalGrade)
                            put("showDailyPrep", vis.showDailyPrep)
                            put("showBehaviorAndAbsences", vis.showBehaviorAndAbsences)
                            put("showBadgesAndNotes", vis.showBadgesAndNotes)
                        }.toString()

                        val dataJson = JSONObject().apply {
                            if (vis.showTerm1) put("term1", GradeCalculator.formatScore(calc.term1Average, r))
                            if (vis.showMidYear) put("midYear", GradeCalculator.formatScore(calc.midYearEffective, r))
                            if (vis.showTerm2) put("term2", GradeCalculator.formatScore(calc.term2Average, r))
                            if (vis.showAnnualEffort) put("annualEffort", GradeCalculator.formatScore(calc.annualEffort, r))
                            if (vis.showFinalGrade) put("finalGrade", GradeCalculator.formatScore(calc.finalGrade, r))
                            if (vis.showDailyPrep) put("dailyPrep", if (student.dailyPrepDone) "ملتزم بالتحضير" else "يحتاج متابعة")
                            if (vis.showBehaviorAndAbsences) {
                                put("behavior", student.behaviorStatus.titleAr)
                                put("absences", absencesCount)
                            }
                            if (vis.showBadgesAndNotes) {
                                put("badges", student.badges.joinToString(" • "))
                                put("notes", student.notes)
                            }
                        }.toString()

                        onPublishReport(
                            state.profile.teacherName.ifBlank { "المدرس" },
                            ledgerDef.title.ifBlank { state.profile.subjectName },
                            student.fullName,
                            student.gradeLevel.titleAr,
                            student.section.id,
                            visJson,
                            dataJson
                        ) { res ->
                            res.onSuccess { generatedReport = it }
                                .onFailure { errorMsg = it.localizedMessage ?: "تعذر إنشاء التقرير" }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("توليد رابط وكود سحابي (3 أيام)", fontWeight = FontWeight.Bold)
                }

                if (myReports.isNotEmpty()) {
                    HorizontalDivider()
                    Text("التقارير الصادرة مؤخراً:", style = MaterialTheme.typography.labelLarge)
                    myReports.take(3).forEach { prev ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${prev.studentName} • ${prev.reportCode}", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { copyTextWithToast(context, "الكود", prev.reportCode) }) {
                                Text("نسخ")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisibilityCheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ParentReportViewerDialog(
    initialCode: String,
    currentUser: FirebaseUser?,
    busy: Boolean,
    onLookupCode: (String, (Result<ParentReportRecord>) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val ext = LocalExtendedColors.current
    var codeInput by remember(initialCode) {
        mutableStateOf(
            initialCode.removePrefix("rafiqteacher://report/").trim().uppercase()
        )
    }
    var report by remember { mutableStateOf<ParentReportRecord?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialCode, currentUser) {
        val clean = initialCode.removePrefix("rafiqteacher://report/").trim().uppercase()
        if (clean.length >= 4 && currentUser != null) {
            onLookupCode(clean) { res ->
                res.onSuccess { report = it; errorMsg = null }
                    .onFailure { report = null; errorMsg = it.localizedMessage }
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
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 560.dp)
                .testTag("parent_report_viewer_dialog")
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
                        Icon(Icons.Filled.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "بوابة ولي الأمر (عرض فقط)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
                }

                if (currentUser == null) {
                    GoogleSignInArabicButton(
                        label = "سجّل الدخول بـ Google لعرض التقرير",
                        onAuthSuccess = {},
                        onAuthError = { errorMsg = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = {
                                codeInput = it.removePrefix("rafiqteacher://report/").trim().uppercase().take(24)
                            },
                            label = { Text("كود التقرير (مثال: PR...)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            enabled = !busy && codeInput.length >= 4,
                            onClick = {
                                errorMsg = null
                                onLookupCode(codeInput) { res ->
                                    res.onSuccess { report = it }
                                        .onFailure { report = null; errorMsg = it.localizedMessage ?: "تعذر الجلب" }
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("عرض")
                        }
                    }

                    errorMsg?.let { Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall) }

                    report?.let { rep ->
                        val dataObj = remember(rep.reportDataJson) {
                            runCatching { JSONObject(rep.reportDataJson) }.getOrNull() ?: JSONObject()
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ext.cardElevated,
                            border = BorderStroke(1.3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(rep.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                Text("${rep.gradeTitle} (${rep.sectionId}) • المادة: ${rep.subjectName}", style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                                Text("المدرس: أ. ${rep.teacherName}", style = MaterialTheme.typography.labelMedium)
                                HorizontalDivider()
                                if (dataObj.has("term1")) ReportLine("معدل الفصل الأول", dataObj.optString("term1"))
                                if (dataObj.has("midYear")) ReportLine("نصف السنة", dataObj.optString("midYear"))
                                if (dataObj.has("term2")) ReportLine("معدل الفصل الثاني", dataObj.optString("term2"))
                                if (dataObj.has("annualEffort")) ReportLine("السعي السنوي", dataObj.optString("annualEffort"))
                                if (dataObj.has("finalGrade")) ReportLine("الدرجة النهائية", dataObj.optString("finalGrade"))
                                if (dataObj.has("dailyPrep")) ReportLine("التحضير اليومي", dataObj.optString("dailyPrep"))
                                if (dataObj.has("behavior")) ReportLine("السلوك", dataObj.optString("behavior"))
                                if (dataObj.has("absences")) ReportLine("أيام الغياب", "${dataObj.optInt("absences")} يوم")
                                if (dataObj.has("badges") && dataObj.optString("badges").isNotBlank()) {
                                    ReportLine("الأوسمة", dataObj.optString("badges"))
                                }
                                if (dataObj.has("notes") && dataObj.optString("notes").isNotBlank()) {
                                    Text("ملاحظة المدرس: ${dataObj.optString("notes")}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "تقرير للقراءة فقط (يُغلق تلقائياً بعد 3 أيام من الإصدار)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.textMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
    }
}
