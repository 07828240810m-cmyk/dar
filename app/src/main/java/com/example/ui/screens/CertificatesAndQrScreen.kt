package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.CalendarDatePickerDialog
import com.example.ui.components.ClassSelectorBar
import com.example.ui.components.SectionHeaderWithInfo
import com.example.ui.components.formatCalendarDateArabic
import com.example.ui.theme.*
import com.example.utils.CertificateTemplateStyle
import com.example.utils.ExportManager

private data class CertStylePalette(
    val style: CertificateTemplateStyle,
    val bg: Color,
    val accent: Color,
    val isLight: Boolean
)

@Composable
fun CertificatesAndQrScreen(
    state: AppState,
    selectedGrade: GradeLevel,
    selectedSection: SectionLetter,
    initialStudent: Student?,
    initialTab: Int = 0, // 0=Certificates, 1=QR
    onSelectClass: (GradeLevel, SectionLetter) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    var activeTab by remember(initialTab) { mutableStateOf(initialTab.coerceIn(0, 1)) }

    val sectionStudents = remember(state.students, selectedGrade, selectedSection) {
        state.students
            .filter { it.gradeLevel == selectedGrade && it.section == selectedSection }
            .sortedBy { it.orderNumber }
    }
    var chosenStudent by remember(initialStudent, sectionStudents) {
        mutableStateOf(initialStudent ?: sectionStudents.firstOrNull())
    }
    var showPickStudentDialog by remember { mutableStateOf(false) }

    val templatePalettes = remember {
        listOf(
            CertStylePalette(CertificateTemplateStyle.ROYAL_TURQUOISE, Color(0xFF102030), Color(0xFF40D6B4), false),
            CertStylePalette(CertificateTemplateStyle.CLASSIC_GOLD, Color(0xFF141E2E), Color(0xFFFFD54F), false),
            CertStylePalette(CertificateTemplateStyle.ACADEMIC_LIGHT, Color(0xFFF8FAFC), Color(0xFF0F766E), true),
            CertStylePalette(CertificateTemplateStyle.EMERALD_HONOR, Color(0xFF0D231E), Color(0xFF34D399), false)
        )
    }
    val achievementBadges = remember {
        listOf("التفوق العلمي", "الانضباط والمواظبة", "الطالب المثالي", "التقدم الملحوظ", "مشاركة فعالة", "")
    }

    var selectedPalette by remember { mutableStateOf(templatePalettes.first()) }
    var selectedBadge by remember { mutableStateOf(achievementBadges.first()) }
    var customRecipient by remember(chosenStudent) {
        mutableStateOf(chosenStudent?.fullName ?: "${selectedGrade.titleAr} - ${selectedSection.titleAr}")
    }
    var subjectInput by remember(state.profile.subjectName) {
        mutableStateOf(state.profile.subjectName.ifBlank { "المادة الدراسية" })
    }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var dateStr by remember { mutableStateOf(formatCalendarDateArabic(System.currentTimeMillis())) }
    var showDateDialog by remember { mutableStateOf(false) }
    var certText by remember {
        mutableStateOf("يسر إدارة المدرسة ومدرس المادة تقديم أسمى عبارات الشكر والتقدير لتميزه العلمي والأخلاقي وتفوقه المستمر، متمنين له دوام النجاح والتفوق.")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        val accent = MaterialTheme.colorScheme.primary
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = accent)
            }
            SectionHeaderWithInfo(
                title = "الشهادات التقديرية و QR النتائج",
                infoTitle = "التكريم المتكامل ورمز QR",
                infoDescription = "صمّم شهادات شكر وتقدير بقوالب جاهزة وأوسمة تكريم وتاريخ، وصدّرها بصيغة صورة عالية الدقة أو PDF، أو أنشئ رمز QR لنتائج الطالب."
            )
        }

        ClassSelectorBar(selectedGrade, selectedSection, onSelectClass)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "الطالب: ${chosenStudent?.fullName ?: "اختر طالباً"}",
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { showPickStudentDialog = true }) {
                Text("تغيير الطالب")
            }
        }

        Spacer(Modifier.height(6.dp))
        TabRow(selectedTabIndex = activeTab) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("الشهادات التقديرية") })
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("QR النتائج") })
        }

        Spacer(Modifier.height(10.dp))

        if (activeTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("قالب الشهادة:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        templatePalettes.forEach { pal ->
                            val active = pal.style == selectedPalette.style
                            FilterChip(
                                selected = active,
                                onClick = { selectedPalette = pal },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(pal.accent)
                                    )
                                },
                                label = { Text(pal.style.titleAr) }
                            )
                        }
                    }
                }

                item {
                    Text("وسام التكريم:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        achievementBadges.forEach { badge ->
                            val label = badge.ifBlank { "بدون وسام" }
                            FilterChip(
                                selected = badge == selectedBadge,
                                onClick = { selectedBadge = badge },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = customRecipient,
                        onValueChange = { customRecipient = it },
                        label = { Text("اسم المكرم (طالب أو اسم مجموعة)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = subjectInput,
                            onValueChange = { subjectInput = it },
                            label = { Text("المادة") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dateStr,
                            onValueChange = { dateStr = it },
                            label = { Text("تاريخ الشهادة") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { showDateDialog = true }) {
                                    Icon(Icons.Filled.CalendarMonth, contentDescription = "التقويم", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = certText,
                        onValueChange = { certText = it },
                        label = { Text("نص الشهادة التقديرية") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    val primaryTxt = if (selectedPalette.isLight) Color(0xFF102030) else Color.White
                    val bodyTxt = if (selectedPalette.isLight) Color(0xFF334155) else Color(0xFFE2E8F0)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = selectedPalette.bg,
                        border = BorderStroke(3.dp, selectedPalette.accent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = selectedPalette.accent.copy(alpha = 0.18f),
                                border = BorderStroke(1.5.dp, selectedPalette.accent)
                            ) {
                                Icon(
                                    Icons.Filled.WorkspacePremium,
                                    contentDescription = null,
                                    tint = selectedPalette.accent,
                                    modifier = Modifier.padding(10.dp).size(28.dp)
                                )
                            }
                            Text(
                                "${state.profile.schoolName} • مادة $subjectInput",
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryTxt
                            )
                            Text(
                                "شهادة شكر وتقدير",
                                style = MaterialTheme.typography.headlineMedium,
                                color = selectedPalette.accent,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedBadge.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = selectedPalette.accent.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        "« $selectedBadge »",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = selectedPalette.accent,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                "تُمنح إلى: $customRecipient",
                                style = MaterialTheme.typography.titleMedium,
                                color = primaryTxt,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                certText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = bodyTxt,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "التاريخ: $dateStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = primaryTxt
                                )
                                Text(
                                    "أ. ${state.profile.teacherName.ifBlank { "مدرس المادة" }}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = selectedPalette.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val bmp = ExportManager.generateCertificateBitmap(
                                    recipientName = customRecipient,
                                    bodyText = certText,
                                    teacherName = state.profile.teacherName,
                                    schoolName = state.profile.schoolName,
                                    subjectName = subjectInput,
                                    dateStr = dateStr,
                                    badgeTitle = selectedBadge,
                                    bgColorInt = selectedPalette.bg.toArgb(),
                                    accentColorInt = selectedPalette.accent.toArgb(),
                                    isLightCard = selectedPalette.isLight
                                )
                                val file = ExportManager.saveBitmapPng(context, bmp, "certificate_${System.currentTimeMillis()}.png")
                                ExportManager.shareFile(context, file, "image/png", "مشاركة الشهادة التقديرية")
                            },
                            modifier = Modifier.weight(1f).testTag("export_certificate_png_btn")
                        ) {
                            Icon(Icons.Filled.Image, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("مشاركة صورة")
                        }
                        OutlinedButton(
                            onClick = {
                                val bmp = ExportManager.generateCertificateBitmap(
                                    recipientName = customRecipient,
                                    bodyText = certText,
                                    teacherName = state.profile.teacherName,
                                    schoolName = state.profile.schoolName,
                                    subjectName = subjectInput,
                                    dateStr = dateStr,
                                    badgeTitle = selectedBadge,
                                    bgColorInt = selectedPalette.bg.toArgb(),
                                    accentColorInt = selectedPalette.accent.toArgb(),
                                    isLightCard = selectedPalette.isLight
                                )
                                val pdfFile = ExportManager.generateCertificatePdf(context, bmp, "certificate_${System.currentTimeMillis()}.pdf")
                                ExportManager.shareFile(context, pdfFile, "application/pdf", "مشاركة الشهادة PDF")
                            },
                            modifier = Modifier.weight(1f).testTag("export_certificate_pdf_btn")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تصدير PDF")
                        }
                    }
                }
            }
        } else {
            val st = chosenStudent
            val qrText = if (st != null) {
                remember(st, state.profile, state.gradebookSettings) {
                    ExportManager.buildStudentShareText(st, state.profile, state.gradebookSettings, LedgerSubject.GENERAL)
                }
            } else ""
            val qrBitmap = if (st != null) {
                remember(qrText) { ExportManager.generateQrBitmap(qrText, 520) }
            } else null

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (st != null && qrBitmap != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "رمز QR لنتائج ${st.fullName}",
                                modifier = Modifier.size(240.dp).padding(12.dp)
                            )
                        }
                    }
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ext.cardElevated,
                            border = BorderStroke(1.dp, ext.borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = qrText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    val file = ExportManager.saveBitmapPng(context, qrBitmap, "qr_${st.id}.png")
                                    ExportManager.shareFile(context, file, "image/png", "مشاركة رمز QR")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.QrCode, null)
                                Spacer(Modifier.width(6.dp))
                                Text("مشاركة QR")
                            }
                            OutlinedButton(
                                onClick = {
                                    ExportManager.shareText(context, "نتائج ${st.fullName}", qrText)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Share, null)
                                Spacer(Modifier.width(6.dp))
                                Text("مشاركة النص")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPickStudentDialog) {
        AlertDialog(
            onDismissRequest = { showPickStudentDialog = false },
            title = { Text("اختر الطالب") },
            text = {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(sectionStudents.size) { idx ->
                        val s = sectionStudents[idx]
                        TextButton(
                            onClick = {
                                chosenStudent = s
                                customRecipient = s.fullName
                                showPickStudentDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${s.orderNumber}. ${s.fullName}", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPickStudentDialog = false }) { Text("إغلاق") } }
        )
    }

    if (showDateDialog) {
        CalendarDatePickerDialog(
            initialMillis = dateMillis,
            onDismiss = { showDateDialog = false },
            onDateSelected = {
                dateMillis = it
                dateStr = formatCalendarDateArabic(it)
                showDateDialog = false
            }
        )
    }
}
