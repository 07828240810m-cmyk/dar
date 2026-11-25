package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.ClassSelectorBar
import com.example.ui.components.SectionHeaderWithInfo
import com.example.ui.components.neonCardGlow
import com.example.ui.theme.*
import com.example.utils.GradeCalculator

@Composable
fun DailyLedgerScreen(
    state: AppState,
    selectedGrade: GradeLevel,
    selectedSection: SectionLetter,
    activeLedger: LedgerSubject,
    activeLedgerId: String = state.profile.currentLedgerDef().id,
    onSelectClass: (GradeLevel, SectionLetter) -> Unit,
    onSelectLedger: (LedgerSubject) -> Unit,
    onSelectLedgerId: (String) -> Unit = { id ->
        onSelectLedger(if (id == "math") LedgerSubject.MATH else LedgerSubject.GENERAL)
    },
    onAddColumn: (LedgerSubject, DailyPeriodSlot, String, Double, String) -> Unit,
    onUpdateColumn: (String, String, Double) -> Unit,
    onDeleteColumn: (String) -> Unit,
    onSetDailyScore: (String, String, Double?) -> Unit,
    onTransferTotalToMain: (LedgerSubject, GradeLevel, SectionLetter, DailyPeriodSlot, Boolean, DailyCalcMode?, String) -> Int,
    onUpdateDailyCalcMode: (DailyCalcMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val ext = LocalExtendedColors.current
    val ledgers = remember(state.profile.customLedgers, state.profile.subjectName, state.profile.subjectSpecialty) {
        state.profile.effectiveLedgers()
    }
    val currentLedgerDef = remember(ledgers, activeLedgerId) {
        ledgers.find { it.id == activeLedgerId } ?: ledgers.first()
    }
    val mainSubName = currentLedgerDef.title
    val accent = MaterialTheme.colorScheme.primary
    val tableHeaderBg = MaterialTheme.colorScheme.primaryContainer
    val tableBg = MaterialTheme.colorScheme.surface

    var selectedSlot by remember { mutableStateOf(DailyPeriodSlot.T1_M1) }
    var directSumMode by remember { mutableStateOf(false) }
    var showAddColDialog by remember { mutableStateOf(false) }
    var editingColumn by remember { mutableStateOf<DailyColumnDef?>(null) }
    var editingCell by remember { mutableStateOf<Pair<Student, DailyColumnDef>?>(null) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }

    val slotColumns = remember(state.dailyColumns, selectedSlot, currentLedgerDef.id) {
        state.dailyColumns.filter {
            it.slot == selectedSlot &&
                (it.ledgerId == currentLedgerDef.id || (currentLedgerDef.id == "general" && it.ledgerId.isBlank()))
        }
    }
    val totalMaxSum = remember(slotColumns) {
        slotColumns.sumOf { it.maxScore }.toInt()
    }
    val sectionStudents = remember(state.students, selectedGrade, selectedSection, state.gradebookSettings.hideTransferredStudents) {
        state.students
            .filter {
                it.gradeLevel == selectedGrade &&
                    it.section == selectedSection &&
                    (!state.gradebookSettings.hideTransferredStudents || !it.isTransferred)
            }
            .sortedBy { it.orderNumber }
    }
    val hScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("daily_ledger_back_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = accent)
            }
            SectionHeaderWithInfo(
                title = "قائمة اليومي ($mainSubName)",
                infoTitle = "قائمة اليومي والأعمدة والترحيل",
                infoDescription = "أضف عموداً واحداً أو عدة أعمدة يومية لكل شهر (الشهر الأول أو الثاني بالفصل الأول أو الثاني)، ثم رحّل المجموع من 100 مباشرةً إلى سجل الدرجات الرئيسي ($mainSubName) ليُحتسب ضمن المعدل."
            ) {
                IconButton(
                    onClick = { showAddColDialog = true },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("add_daily_column_btn")
                ) {
                    Icon(Icons.Filled.AddBox, contentDescription = "إضافة عمود يومي", tint = accent)
                }
            }
        }

        ClassSelectorBar(
            selectedGrade = selectedGrade,
            selectedSection = selectedSection,
            onSelect = onSelectClass
        )

        if (ledgers.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ledgers.forEach { lDef ->
                    val isSel = lDef.id == currentLedgerDef.id
                    FilterChip(
                        selected = isSel,
                        onClick = { onSelectLedgerId(lDef.id) },
                        label = { Text(lDef.title) }
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Monthly Slot Selector (Term 1 M1/M2 & Term 2 M1/M2)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DailyPeriodSlot.entries.forEach { slot ->
                val selected = slot == selectedSlot
                FilterChip(
                    selected = selected,
                    onClick = { selectedSlot = slot },
                    label = { Text(slot.shortAr, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("daily_slot_${slot.id}")
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Columns Bar + Transfer Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showTransferDialog = true },
                enabled = slotColumns.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.weight(1f).testTag("transfer_daily_to_main_btn")
            ) {
                Icon(Icons.Filled.Publish, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("ترحيل /100 للرئيسي", fontWeight = FontWeight.Bold, maxLines = 1)
            }
            OutlinedButton(
                onClick = { showAddColDialog = true },
                modifier = Modifier.testTag("daily_new_col_btn")
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("عمود جديد")
            }
        }

        // Calc mode indicator + direct/proportional switch
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "مجموع العظمى: $totalMaxSum • الاحتساب: ${state.gradebookSettings.dailyCalcMode.titleAr}",
                style = MaterialTheme.typography.labelMedium,
                color = ext.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { directSumMode = !directSumMode }) {
                Text(
                    text = if (directSumMode) "جمع مباشر" else "تناسب /100",
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        feedbackBanner?.let { msg ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.16f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        if (slotColumns.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.ViewColumn, null, tint = ext.textMuted, modifier = Modifier.size(48.dp))
                    Text("لا توجد أعمدة في ${selectedSlot.titleAr}", color = ext.textMuted)
                    Button(onClick = { showAddColDialog = true }) { Text("إضافة أول عمود يومي") }
                }
            }
        } else {
            val resolvedTableBg = if (state.profile.gradebookTableBgColorHex != 0L) {
                Color(state.profile.gradebookTableBgColorHex)
            } else tableBg
            val resolvedStudentNameColor = if (state.profile.studentNamesColorHex != 0L) {
                Color(state.profile.studentNamesColorHex)
            } else MaterialTheme.colorScheme.onSurface
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = resolvedTableBg,
                border = BorderStroke(1.2.dp, accent.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxSize().neonCardGlow(accent, ext.neonGlowEnabled, 16.dp, alphaFactor = 0.25f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header row (tap any column header to rename/delete)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(tableHeaderBg)
                            .horizontalScroll(hScroll)
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ت",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(34.dp)
                        )
                        Text(
                            text = "اسم الطالب",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(195.dp).padding(horizontal = 4.dp)
                        )
                        slotColumns.forEach { col ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(86.dp)
                                    .clickable { editingColumn = col }
                                    .padding(horizontal = 2.dp)
                            ) {
                                Text(
                                    text = col.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "/${col.maxScore.toInt()} ✎",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.textMuted
                                )
                            }
                        }
                        Text(
                            text = "المجموع /100",
                            style = MaterialTheme.typography.labelMedium,
                            color = GoldBright,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(86.dp)
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(sectionStudents, key = { it.id }) { st ->
                            val rowBg = if (st.orderNumber % 2 == 0) ext.cardElevated.copy(alpha = 0.35f) else Color.Transparent
                            val total100 = GradeCalculator.computeDailyOutOf100(
                                studentId = st.id,
                                columns = slotColumns,
                                dailyScores = state.dailyScores,
                                directSum = directSumMode
                            )
                            val ledgerGrades = st.gradesForLedger(currentLedgerDef.id)
                            val transferredVal = when (selectedSlot) {
                                DailyPeriodSlot.T1_M1 -> ledgerGrades.term1Month1Daily
                                DailyPeriodSlot.T1_M2 -> ledgerGrades.term1Month2Daily
                                DailyPeriodSlot.T2_M1 -> ledgerGrades.term2Month1Daily
                                DailyPeriodSlot.T2_M2 -> ledgerGrades.term2Month2Daily
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg)
                                    .horizontalScroll(hScroll)
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = st.orderNumber.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = accent,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(34.dp)
                                )
                                Column(modifier = Modifier.width(195.dp).padding(horizontal = 4.dp)) {
                                    Text(
                                        text = st.fullName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                                        fontWeight = FontWeight.SemiBold,
                                        color = resolvedStudentNameColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (transferredVal != null) {
                                        Text(
                                            text = "مرحّل للرئيسي: ${GradeCalculator.formatScore(transferredVal, state.gradebookSettings.enableSmartRounding)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accent
                                        )
                                    }
                                }
                                slotColumns.forEach { col ->
                                    val cellVal = state.dailyScores[dailyCellKey(col.id, st.id)]
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (cellVal != null) accent.copy(alpha = 0.14f) else ext.cardElevated.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, if (cellVal != null) accent.copy(alpha = 0.45f) else ext.borderSubtle),
                                        modifier = Modifier
                                            .width(82.dp)
                                            .padding(horizontal = 3.dp)
                                            .clickable { editingCell = st to col }
                                    ) {
                                        Text(
                                            text = cellVal?.let {
                                                if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it)
                                            } ?: "—",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            color = if (cellVal != null) MaterialTheme.colorScheme.onSurface else ext.textMuted,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = GradeCalculator.formatScore(total100, state.gradebookSettings.enableSmartRounding),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldBright,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(86.dp)
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }

    // Add Daily Column Dialog
    if (showAddColDialog) {
        var title by remember { mutableStateOf("") }
        var maxTxt by remember { mutableStateOf("25") }
        AlertDialog(
            onDismissRequest = { showAddColDialog = false },
            title = { Text("إضافة عمود يومي جديد (${selectedSlot.shortAr})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("اسم العمود (مثال: مشاركة، تحضير، اختبار)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_daily_col_title_input")
                    )
                    OutlinedTextField(
                        value = maxTxt,
                        onValueChange = { maxTxt = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("الدرجة العظمى للعمود (مثال: 20، 25، 100)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val maxVal = maxTxt.toDoubleOrNull()?.coerceIn(1.0, 100.0) ?: 25.0
                        onAddColumn(activeLedger, selectedSlot, title, maxVal, currentLedgerDef.id)
                        showAddColDialog = false
                        feedbackBanner = "تمت إضافة عمود (${title.ifBlank { "عمود يومي" }}) ✓"
                    },
                    modifier = Modifier.testTag("confirm_add_daily_col_btn")
                ) { Text("إضافة") }
            },
            dismissButton = { TextButton(onClick = { showAddColDialog = false }) { Text("إلغاء") } }
        )
    }

    // Edit / Delete Column Dialog
    editingColumn?.let { col ->
        var title by remember(col.id) { mutableStateOf(col.title) }
        var maxTxt by remember(col.id) { mutableStateOf(col.maxScore.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { editingColumn = null },
            title = { Text("تعديل عمود اليومي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("اسم العمود") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = maxTxt,
                        onValueChange = { maxTxt = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("الدرجة العظمى") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val m = maxTxt.toDoubleOrNull()?.coerceIn(1.0, 100.0) ?: col.maxScore
                    onUpdateColumn(col.id, title, m)
                    editingColumn = null
                }) { Text("حفظ") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onDeleteColumn(col.id)
                        editingColumn = null
                    }) { Text("حذف العمود", color = DangerRed) }
                    TextButton(onClick = { editingColumn = null }) { Text("إلغاء") }
                }
            }
        )
    }

    // Edit Score Cell Dialog with Next-Student Quick Advance
    editingCell?.let { (st, col) ->
        val currentIdx = sectionStudents.indexOfFirst { it.id == st.id }
        val existingScore = state.dailyScores[dailyCellKey(col.id, st.id)]
        var scoreTxt by remember(st.id, col.id) {
            mutableStateOf(existingScore?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "")
        }

        fun saveCurrent(advanceToNext: Boolean) {
            val num = scoreTxt.trim().toDoubleOrNull()?.coerceIn(0.0, col.maxScore)
            onSetDailyScore(col.id, st.id, num)
            if (advanceToNext && currentIdx >= 0 && currentIdx < sectionStudents.lastIndex) {
                editingCell = sectionStudents[currentIdx + 1] to col
            } else {
                editingCell = null
            }
        }

        AlertDialog(
            onDismissRequest = { editingCell = null },
            title = { Text("${st.orderNumber}. ${st.fullName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("العمود: ${col.title} (من ${col.maxScore.toInt()})", color = accent, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = scoreTxt,
                        onValueChange = { txt ->
                            if (txt.isEmpty() || txt.matches(Regex("^\\d{0,3}(\\.\\d{0,2})?$"))) {
                                scoreTxt = txt
                            }
                        },
                        label = { Text("الدرجة (0 - ${col.maxScore.toInt()})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { saveCurrent(true) }, onDone = { saveCurrent(false) }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (currentIdx >= 0 && currentIdx < sectionStudents.lastIndex) {
                        OutlinedButton(onClick = { saveCurrent(true) }) { Text("حفظ والتالي") }
                    }
                    Button(onClick = { saveCurrent(false) }) { Text("حفظ") }
                }
            },
            dismissButton = { TextButton(onClick = { editingCell = null }) { Text("إلغاء") } }
        )
    }

    // Transfer Total (/100) to Main Gradebook Dialog
    if (showTransferDialog) {
        var calcMode by remember {
            mutableStateOf(
                if (state.gradebookSettings.dailyCalcMode == DailyCalcMode.NEVER) DailyCalcMode.WITH_EACH_MONTH
                else state.gradebookSettings.dailyCalcMode
            )
        }
        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text("ترحيل مجموع اليومي (من 100) للجدول الرئيسي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "سيتم ترحيل مجموع درجات اليومي من 100 لطلبة ${selectedGrade.shortAr} ${selectedSection.titleAr} إلى خانة (${selectedSlot.titleAr}) في سجل (${currentLedgerDef.title}).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("طريقة احتساب اليومي في المعدل:", style = MaterialTheme.typography.labelLarge, color = accent)
                    DailyCalcMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { calcMode = mode }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = calcMode == mode, onClick = { calcMode = mode })
                            Column {
                                Text(mode.titleAr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(mode.descriptionAr, style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateDailyCalcMode(calcMode)
                        val count = onTransferTotalToMain(
                            activeLedger,
                            selectedGrade,
                            selectedSection,
                            selectedSlot,
                            directSumMode,
                            calcMode,
                            currentLedgerDef.id
                        )
                        showTransferDialog = false
                        feedbackBanner = "تم ترحيل درجات $count طالب من 100 إلى (${selectedSlot.shortAr}) في سجل (${currentLedgerDef.title}) ✓"
                    },
                    modifier = Modifier.testTag("confirm_transfer_daily_btn")
                ) { Text("ترحيل واحتساب الآن") }
            },
            dismissButton = { TextButton(onClick = { showTransferDialog = false }) { Text("إلغاء") } }
        )
    }
}
