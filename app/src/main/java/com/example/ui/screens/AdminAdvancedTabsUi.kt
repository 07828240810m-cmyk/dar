package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ActivationCodeRecord
import com.example.data.CloudAnalyticsSummary
import com.example.data.DeveloperFeedbackRecord
import com.example.data.FeedbackCategory
import com.example.data.GradeComparisonRecord
import com.example.data.NameChangeRequestRecord
import com.example.data.ParentReportRecord
import com.example.data.SubAdminRecord
import com.example.data.TeacherAccountRecord
import com.example.ui.theme.DangerRed
import com.example.ui.theme.GoldBright
import com.example.ui.theme.InfoBlue
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber

private fun copyAdminText(context: Context, label: String, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "تم نسخ $label ✓", Toast.LENGTH_SHORT).show()
}

// ==================== Sub-Admins Management Tab ====================

@Composable
fun AdminSubAdminsTab(
    subAdmins: List<SubAdminRecord>,
    onSaveSubAdmin: (SubAdminRecord) -> Unit,
    onDeleteSubAdmin: (String) -> Unit
) {
    val ext = LocalExtendedColors.current
    var uidInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var permUsers by remember { mutableStateOf(true) }
    var permAnnouncements by remember { mutableStateOf(true) }
    var permCodes by remember { mutableStateOf(true) }
    var permAnalytics by remember { mutableStateOf(true) }
    var permRemote by remember { mutableStateOf(false) }
    var permComparisons by remember { mutableStateOf(true) }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = ext.cardElevated, border = BorderStroke(1.dp, ext.borderSubtle)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("إضافة أو تعديل مشرف بصلاحيات مخصصة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = uidInput, onValueChange = { uidInput = it.trim().take(128) }, label = { Text("UID الخاص بالمشرف") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = nameInput, onValueChange = { nameInput = it.take(80) }, label = { Text("اسم المشرف") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = emailInput, onValueChange = { emailInput = it.take(120) }, label = { Text("البريد الإلكتروني (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("صلاحيات المشرف:", style = MaterialTheme.typography.labelLarge)
                    SubAdminToggleRow("إدارة المستخدمين وتغيير الأسماء", permUsers) { permUsers = it }
                    SubAdminToggleRow("إرسال وإدارة الإشعارات والتصويت", permAnnouncements) { permAnnouncements = it }
                    SubAdminToggleRow("صناعة أكواد التفعيل وفك الأجهزة", permCodes) { permCodes = it }
                    SubAdminToggleRow("عرض التحليلات والإحصائيات", permAnalytics) { permAnalytics = it }
                    SubAdminToggleRow("موافقة مقارنات المواد", permComparisons) { permComparisons = it }
                    SubAdminToggleRow("التحكم بالأدوات والصيانة والإعلانات", permRemote) { permRemote = it }
                    Button(
                        enabled = uidInput.length >= 6,
                        onClick = {
                            onSaveSubAdmin(
                                SubAdminRecord(
                                    uid = uidInput,
                                    email = emailInput,
                                    name = nameInput.ifBlank { "مشرف" },
                                    canManageUsers = permUsers,
                                    canManageAnnouncements = permAnnouncements,
                                    canManageCodes = permCodes,
                                    canViewAnalytics = permAnalytics,
                                    canManageRemoteControl = permRemote,
                                    canManageComparisons = permComparisons
                                )
                            )
                            uidInput = ""; emailInput = ""; nameInput = ""
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("حفظ صلاحيات المشرف", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(subAdmins, key = { it.uid }) { sub ->
            Surface(shape = RoundedCornerShape(14.dp), color = ext.cardElevated, border = BorderStroke(1.dp, ext.borderSubtle)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sub.name.ifBlank { sub.uid }, fontWeight = FontWeight.Bold)
                        Text("${sub.email} • UID: ${sub.uid.take(10)}...", style = MaterialTheme.typography.labelSmall, color = ext.textMuted)
                        val granted = buildList {
                            if (sub.canManageUsers) add("مستخدمين")
                            if (sub.canManageAnnouncements) add("إشعارات")
                            if (sub.canManageCodes) add("أكواد")
                            if (sub.canViewAnalytics) add("تحليلات")
                            if (sub.canManageComparisons) add("مقارنات")
                            if (sub.canManageRemoteControl) add("تحكم")
                        }.joinToString(" • ")
                        Text(granted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onDeleteSubAdmin(sub.uid) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "إزالة المشرف", tint = DangerRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubAdminToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ==================== Activation Codes Generator & Device-Lock Manager ====================

@Composable
fun AdminActivationCodesTab(
    codes: List<ActivationCodeRecord>,
    onCreateCode: (customCode: String, username: String, passwordPlain: String, notes: String) -> Unit,
    onResetDevice: (String) -> Unit,
    onDeleteCode: (String) -> Unit
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    var customCode by remember { mutableStateOf("") }
    var assignedUser by remember { mutableStateOf("") }
    var assignedPass by remember { mutableStateOf("1234") }
    var notes by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = ext.cardElevated, border = BorderStroke(1.dp, ext.borderSubtle)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("توليد كود تفعيل ويوزر وكلمة مرور (يرتبط بجهاز واحد)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(
                        value = customCode,
                        onValueChange = { customCode = it.uppercase().take(20) },
                        label = { Text("الكود (اتركه فارغاً للتوليد التلقائي)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = assignedUser,
                            onValueChange = { assignedUser = it.uppercase().take(24) },
                            label = { Text("اسم المستخدم (تلقائي 4 أحرف إذا فارغ)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = assignedPass,
                            onValueChange = { assignedPass = it.take(32) },
                            label = { Text("كلمة المرور") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(value = notes, onValueChange = { notes = it.take(160) }, label = { Text("ملاحظة (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            onCreateCode(customCode, assignedUser, assignedPass.ifBlank { "1234" }, notes)
                            customCode = ""; assignedUser = ""; notes = ""
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.VpnKey, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("إنشاء كود التفعيل الآن", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(codes, key = { it.code }) { rec ->
            Surface(shape = RoundedCornerShape(14.dp), color = ext.cardElevated, border = BorderStroke(1.dp, if (rec.isUsed) WarningAmber.copy(alpha = 0.5f) else SuccessGreen.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${rec.code} • يوزر: ${rec.assignedUsername}", fontWeight = FontWeight.ExtraBold)
                        Text(if (rec.boundDeviceId.isNotBlank()) "مربوط بجهاز ✓" else "متاح للتفعيل", style = MaterialTheme.typography.labelSmall, color = if (rec.boundDeviceId.isNotBlank()) WarningAmber else SuccessGreen)
                    }
                    if (rec.notes.isNotBlank()) {
                        Text(rec.notes, style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { copyAdminText(context, "بيانات التفعيل", "الكود: ${rec.code}\nاسم المستخدم: ${rec.assignedUsername}") }) { Text("نسخ") }
                        if (rec.boundDeviceId.isNotBlank()) {
                            OutlinedButton(onClick = { onResetDevice(rec.code) }) { Text("فك الجهاز") }
                        }
                        IconButton(onClick = { onDeleteCode(rec.code) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = DangerRed)
                        }
                    }
                }
            }
        }
    }
}

// ==================== Name Change Requests & Cross-Subject Comparison Approvals ====================

@Composable
fun AdminRequestsAndComparisonsTab(
    nameRequests: List<NameChangeRequestRecord>,
    comparisons: List<GradeComparisonRecord>,
    onDecideNameRequest: (NameChangeRequestRecord, Boolean) -> Unit,
    onDecideComparison: (String, Boolean) -> Unit,
    onDeleteComparison: (String) -> Unit
) {
    val ext = LocalExtendedColors.current
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("طلبات تغيير اسم المدرس (${nameRequests.size}):", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        if (nameRequests.isEmpty()) {
            item { Text("لا توجد طلبات تغيير اسم حالياً.", style = MaterialTheme.typography.bodySmall, color = ext.textMuted) }
        } else {
            items(nameRequests, key = { it.id }) { req ->
                Surface(shape = RoundedCornerShape(14.dp), color = ext.cardElevated, border = BorderStroke(1.dp, ext.borderSubtle)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("من: ${req.currentName.ifBlank { req.accountKey }}  ←  إلى: ${req.requestedName}", fontWeight = FontWeight.Bold)
                        Text("الحالة: ${req.status}", style = MaterialTheme.typography.labelSmall, color = if (req.status == "PENDING") WarningAmber else SuccessGreen)
                        if (req.status == "PENDING") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onDecideNameRequest(req, true) }, modifier = Modifier.weight(1f)) { Text("موافقة وإشعار") }
                                OutlinedButton(onClick = { onDecideNameRequest(req, false) }, modifier = Modifier.weight(1f)) { Text("رفض", color = DangerRed) }
                            }
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("طلبات مقارنة الدرجات بين المواد (${comparisons.size}):", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        if (comparisons.isEmpty()) {
            item { Text("لا توجد طلبات مقارنة مسجلة.", style = MaterialTheme.typography.bodySmall, color = ext.textMuted) }
        } else {
            items(comparisons, key = { it.code }) { cmp ->
                Surface(shape = RoundedCornerShape(14.dp), color = ext.cardElevated, border = BorderStroke(1.dp, ext.borderSubtle)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${cmp.code} • ${cmp.creatorSubject} ↔ ${cmp.partnerSubject.ifBlank { "—" }}", fontWeight = FontWeight.Bold)
                            Text(cmp.status, style = MaterialTheme.typography.labelSmall, color = InfoBlue)
                        }
                        Text("${cmp.creatorName} (${cmp.creatorGradeTitle} ${cmp.creatorSectionScope})", style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                        if (cmp.partnerName.isNotBlank()) {
                            Text("الشريك: ${cmp.partnerName} (${cmp.partnerGradeTitle} ${cmp.partnerSectionScope})", style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (cmp.status == "PENDING_APPROVAL") {
                                Button(onClick = { onDecideComparison(cmp.code, true) }, modifier = Modifier.weight(1f)) { Text("موافقة") }
                                OutlinedButton(onClick = { onDecideComparison(cmp.code, false) }, modifier = Modifier.weight(1f)) { Text("رفض", color = DangerRed) }
                            }
                            IconButton(onClick = { onDeleteComparison(cmp.code) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = DangerRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== Analytics + Chat Expiry Cleanup + Auto-Promote + Feedback ====================

@Composable
fun AdminAnalyticsAndLogsTab(
    analytics: CloudAnalyticsSummary,
    users: List<TeacherAccountRecord>,
    feedbackList: List<DeveloperFeedbackRecord>,
    parentReports: List<ParentReportRecord>,
    availableRanks: List<String>,
    onAutoPromoteTopActive: (String) -> Unit,
    onPurgeExpiredChats: () -> Unit,
    onToggleFeedbackReviewed: (String, Boolean) -> Unit,
    onDeleteParentReport: (String) -> Unit
) {
    val ext = LocalExtendedColors.current
    var selectedRank by remember(availableRanks) { mutableStateOf(availableRanks.firstOrNull() ?: "معلم متميز") }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(shape = RoundedCornerShape(16.dp), color = ext.cardElevated, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("تتبع مسار المستخدمين وإحصائيات الاستخدام:", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Text("إجمالي الحسابات: ${analytics.totalUsers} • النشطون اليوم: ${analytics.activeTodayUsers}")
                    Text("استخدام اليوم: ${analytics.totalTodayMinutes} دقيقة • الإجمالي: ${String.format("%.1f", analytics.totalAllTimeHours)} ساعة")
                    Text("التقييم: ${String.format("%.1f", analytics.averageRating)} ★ (${analytics.ratingsCount})")
                    if (analytics.topTools.isNotEmpty()) {
                        HorizontalDivider()
                        Text("مسار الأقسام والأدوات الأكثر استخداماً:", fontWeight = FontWeight.Bold)
                        analytics.topTools.take(8).forEachIndexed { idx, (tool, count) ->
                            Text("${idx + 1}. ${tool.shortTitleAr}: $count نقرة", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(14.dp), color = GoldBright.copy(alpha = 0.1f), border = BorderStroke(1.dp, GoldBright)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ترقية أكثر المدرسين تفاعلاً + تنظيف الدردشة", fontWeight = FontWeight.Bold, color = GoldBright)
                    OutlinedTextField(
                        value = selectedRank,
                        onValueChange = { selectedRank = it.take(50) },
                        label = { Text("لقب الترقية (للأكثر تفاعلاً)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAutoPromoteTopActive(selectedRank) }, modifier = Modifier.weight(1f)) {
                            Text("ترقية أعلى 3 نشطين", maxLines = 1)
                        }
                        OutlinedButton(onClick = onPurgeExpiredChats, modifier = Modifier.weight(1f)) {
                            Text("حذف الدردشات المنتهية", maxLines = 1)
                        }
                    }
                }
            }
        }

        item {
            Text("رسائل المطور (${feedbackList.size}):", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        items(feedbackList, key = { it.id }) { fb ->
            Surface(shape = RoundedCornerShape(14.dp), color = ext.cardElevated, border = BorderStroke(1.dp, if (fb.lockedThreeDays) WarningAmber else ext.borderSubtle)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${fb.senderName.ifBlank { "مستخدم" }} • ${FeedbackCategory.fromRaw(fb.category).titleAr}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        FilterChip(
                            selected = fb.status == "REVIEWED",
                            onClick = { onToggleFeedbackReviewed(fb.id, fb.status != "REVIEWED") },
                            label = { Text(if (fb.status == "REVIEWED") "تمت المراجعة" else "قيد الانتظار") }
                        )
                    }
                    Text(fb.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Text("تقارير أولياء الأمور (${parentReports.size}):", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        items(parentReports, key = { it.reportCode }) { rep ->
            Surface(shape = RoundedCornerShape(12.dp), color = ext.cardElevated, border = BorderStroke(1.dp, ext.borderSubtle)) {
                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${rep.studentName} • ${rep.reportCode}", fontWeight = FontWeight.Bold)
                        Text("${rep.teacherName} | ${rep.subjectName} (${rep.gradeTitle})", style = MaterialTheme.typography.labelSmall, color = ext.textMuted)
                    }
                    IconButton(onClick = { onDeleteParentReport(rep.reportCode) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف التقرير", tint = DangerRed)
                    }
                }
            }
        }
    }
}
