package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.components.CalendarDatePickerDialog
import com.example.ui.components.SectionHeaderWithInfo
import com.example.ui.components.formatCalendarDateArabic
import com.example.ui.components.neonCardGlow
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LocalExtendedColors
import com.example.ui.theme.TimetableAccentColorPresets
import com.example.ui.theme.TimetableBgColorPresets
import com.example.utils.LessonAlarmScheduler
import com.example.utils.LessonBoundaryType

@Composable
fun TimetableScreen(
    state: AppState,
    activeDayPeriod: Pair<SchoolDay?, Int?>,
    onToggleShowOnlyMine: () -> Unit,
    onUpdateTeacherName: (String) -> Unit,
    onUpdateWorkingDays: (Set<SchoolDay>) -> Unit,
    onAddBatchLessons: (List<TimetableCell>, Set<SchoolDay>, Map<SchoolDay, List<PeriodTime>>?) -> Unit,
    onClearTimetable: () -> Unit,
    onUpdateCell: (TimetableCell) -> Unit,
    onDeleteCell: (TimetableCell) -> Unit,
    onMoveOrSwapCell: (TimetableCell, Int) -> Unit,
    onSwapDayPeriods: (SchoolDay, Int, Int, String?) -> Unit,
    onUpdateDayPeriodTime: (SchoolDay, PeriodTime) -> Unit,
    onApplyDayPeriodPreset: (SchoolDay, Int, Int, Int, Int, Int) -> Unit,
    onResetDefaultSchedule: () -> Unit,
    onTriggerInAppTestAlert: (LessonBoundaryType) -> Unit,
    onUpdateProfile: (TeacherProfile) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val primaryAccent = if (state.profile.timetableAccentColorHex != 0L) {
        Color(state.profile.timetableAccentColorHex)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val tableCardBg = if (state.profile.timetableBgColorHex != 0L) {
        Color(state.profile.timetableBgColorHex)
    } else {
        ext.cardElevated
    }
    var showColorDialog by remember { mutableStateOf(false) }

    val workingDays = remember(state.profile.workingDays) {
        state.profile.workingDays.sortedBy { it.ordinal }.ifEmpty { defaultWorkingDays().sortedBy { it.ordinal } }
    }
    val (activeDay, activePeriod) = activeDayPeriod
    var selectedDay by remember(activeDay, workingDays) {
        mutableStateOf(
            if (activeDay != null && activeDay in workingDays) activeDay
            else workingDays.firstOrNull() ?: SchoolDay.SUNDAY
        )
    }
    var selectedCalendarMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showCalendarDialog by remember { mutableStateOf(false) }
    var editingCell by remember { mutableStateOf<TimetableCell?>(null) }
    var addingPeriodNum by remember { mutableStateOf<Int?>(null) }
    var showBellTimesDialog by remember { mutableStateOf(false) }
    var showTeacherNameDialog by remember { mutableStateOf(false) }
    var showWeeklyBuilderDialog by remember { mutableStateOf(false) }
    var showSwapDayDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var testNoticeMsg by remember { mutableStateOf<String?>(null) }

    val activeTeacherKey = state.profile.timetableTeacherKey.ifBlank { state.profile.teacherName }
    val dayPeriods = remember(state.dayPeriodTimes, selectedDay) {
        state.periodTimesForDay(selectedDay)
    }
    val maxPeriodsForDay = dayPeriods.size.coerceAtLeast(if (selectedDay.isAfternoonShiftDefault) 5 else 6)

    val pulse = rememberInfiniteTransition(label = "period_glow")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "glow"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        SectionHeaderWithInfo(
            title = "جدولي الأسبوعي",
            infoTitle = "تصميم جدولك الأسبوعي الشخصي",
            infoDescription = "الجدول خاص بك بالكامل بدون أي أسماء مدرسين مسبقة. اختر أيام دوامك، واضبط أوقات الحصص وبدايتها ونهايتها، ثم أضف حصصك في كل يوم بسهولة ليصلك تنبيه صوتي هادئ قبل كل حصة بدقيقة."
        ) {
            Row {
                IconButton(
                    onClick = { showColorDialog = true },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("timetable_color_btn")
                ) {
                    Icon(Icons.Filled.Palette, contentDescription = "تغيير لون الجدول", tint = primaryAccent)
                }
                IconButton(
                    onClick = { showCalendarDialog = true },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("timetable_calendar_btn")
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = "التقويم", tint = primaryAccent)
                }
                IconButton(
                    onClick = {
                        LessonAlarmScheduler.scheduleTest30SecondNotification(
                            context = context,
                            teacherName = activeTeacherKey,
                            boundaryType = LessonBoundaryType.BEFORE_START,
                            voiceEnabled = state.profile.voiceAnnouncementEnabled,
                            volumePercent = state.profile.alertVolumePercent
                        )
                        onTriggerInAppTestAlert(LessonBoundaryType.BEFORE_START)
                        testNoticeMsg = "تم تشغيل نغمة ونطق تجريبي فوري + جدولة إشعار بعد 30 ثانية"
                    },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("test_30s_notif_btn")
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = "تجربة الإشعار", tint = primaryAccent)
                }
                IconButton(
                    onClick = { showBellTimesDialog = true },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("edit_bell_times_btn")
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = "أوقات الدروس والاستراحة", tint = primaryAccent)
                }
            }
        }

        testNoticeMsg?.let { msg ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = primaryAccent.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                Text(msg, style = MaterialTheme.typography.bodySmall, color = primaryAccent, modifier = Modifier.padding(8.dp))
            }
        }

        val hasTeacherLessonDay = activeDay != null &&
            state.timetable.any {
                it.day == activeDay &&
                    (activeTeacherKey.isBlank() || it.teacherName.contains(activeTeacherKey, ignoreCase = true))
            }
        val hasTeacherLessonNow = hasTeacherLessonDay && activePeriod != null &&
            state.timetable.any {
                it.day == activeDay && it.periodNumber == activePeriod &&
                    (activeTeacherKey.isBlank() || it.teacherName.contains(activeTeacherKey, ignoreCase = true))
            }
        val teacherBorderColor = when {
            hasTeacherLessonNow -> primaryAccent.copy(alpha = glowAlpha)
            hasTeacherLessonDay -> primaryAccent.copy(alpha = 0.7f)
            else -> ext.borderSubtle
        }

        // Teacher Control & Schedule Builder Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = tableCardBg,
            border = BorderStroke(
                if (hasTeacherLessonNow) 2.2.dp else if (hasTeacherLessonDay || ext.neonGlowEnabled) 1.5.dp else 1.dp,
                if (ext.neonGlowEnabled && !hasTeacherLessonNow && !hasTeacherLessonDay) primaryAccent.copy(alpha = 0.45f) else teacherBorderColor
            ),
            shadowElevation = if (ext.neonGlowEnabled) 6.dp else 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .neonCardGlow(primaryAccent, ext.neonGlowEnabled, 18.dp, alphaFactor = 0.30f)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.CalendarMonth, null, tint = primaryAccent, modifier = Modifier.size(22.dp))
                        Column {
                            Text(
                                text = if (activeTeacherKey.isNotBlank()) "أ. $activeTeacherKey — ${state.profile.subjectName}" else "أضف اسمك وابنِ جدولك",
                                style = MaterialTheme.typography.titleSmall,
                                color = primaryAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "إجمالي الحصص: ${state.timetable.size} • ${formatCalendarDateArabic(selectedCalendarMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.textMuted
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showTeacherNameDialog = true },
                        modifier = Modifier.testTag("pick_timetable_teacher_btn")
                    ) {
                        Text(if (activeTeacherKey.isNotBlank()) "تعديل الاسم" else "ضع اسمك")
                    }
                }

                // Primary Builder CTA + Quick actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showWeeklyBuilderDialog = true },
                        modifier = Modifier.weight(1.3f).heightIn(min = 44.dp).testTag("open_weekly_builder_btn"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("بناء جدولي بالأيام والزمن", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showSwapDayDialog = true },
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("swap_periods_btn"),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.SwapVert, null, modifier = Modifier.size(16.dp), tint = primaryAccent)
                        Spacer(Modifier.width(4.dp))
                        Text("تبديل دروس", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Switch(
                            checked = state.profile.showOnlyMyLessons,
                            onCheckedChange = { onToggleShowOnlyMine() },
                            modifier = Modifier.testTag("filter_my_lessons_switch")
                        )
                        Text(
                            text = "حصصي فقط",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Switch(
                            checked = state.profile.liveScheduleBannerEnabled,
                            onCheckedChange = { onUpdateProfile(state.profile.copy(liveScheduleBannerEnabled = it)) },
                            modifier = Modifier.testTag("timetable_live_schedule_switch")
                        )
                        Text(
                            text = "الجدول الحي",
                            style = MaterialTheme.typography.bodySmall,
                            color = primaryAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (state.timetable.isNotEmpty()) {
                        TextButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Filled.DeleteSweep, null, tint = DangerRed, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("تفريغ", color = DangerRed, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Design 2-Inspired Vertical Capsule Working Days Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            workingDays.forEach { day ->
                val isSelected = day == selectedDay
                val isToday = day == activeDay
                val dayCount = state.timetable.count { it.day == day }
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (isSelected) primaryAccent else tableCardBg,
                    border = BorderStroke(
                        if (isSelected) 1.5.dp else 1.dp,
                        if (isSelected) primaryAccent else ext.borderSubtle
                    ),
                    shadowElevation = if (isSelected) 6.dp else 1.dp,
                    modifier = Modifier
                        .widthIn(min = 74.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { selectedDay = day }
                        .testTag("day_chip_${day.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (dayCount > 0) "$dayCount" else "•",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else primaryAccent
                        )
                        Text(
                            text = day.titleAr,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        if (isToday) {
                            Text(
                                text = "اليوم",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f) else primaryAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            AssistChip(
                onClick = { showWeeklyBuilderDialog = true },
                label = { Text("تعديل الأيام") },
                leadingIcon = { Icon(Icons.Filled.EditCalendar, null, tint = primaryAccent, modifier = Modifier.size(16.dp)) }
            )
        }

        Spacer(Modifier.height(10.dp))

        // Vertical Timeline Periods List (inspired by Design 2)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.timetable.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = primaryAccent.copy(alpha = 0.09f),
                        border = BorderStroke(1.2.dp, primaryAccent.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Schedule, null, tint = primaryAccent, modifier = Modifier.size(38.dp))
                            Text(
                                "جدولك الأسبوعي جاهز للبناء",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryAccent
                            )
                            Text(
                                "أضف دروسك حسب الأيام والأوقات التي تناسبك، أو استخدم المنشئ السريع لتحديد عدة حصص في أسبوعك بضغطة واحدة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.textMuted,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = { showWeeklyBuilderDialog = true }) {
                                Icon(Icons.Filled.AutoFixHigh, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("ابدأ ببناء جدولي الأسبوعي")
                            }
                        }
                    }
                }
            }

            items((1..maxPeriodsForDay).toList(), key = { it }) { periodNum ->
                val pt = dayPeriods.find { it.periodNumber == periodNum }
                val nextPt = dayPeriods.find { it.periodNumber == periodNum + 1 }
                val breakMinutes = if (pt != null && nextPt != null) {
                    (nextPt.startHour * 60 + nextPt.startMinute) - (pt.endHour * 60 + pt.endMinute)
                } else 0

                val isLiveNow = (selectedDay == activeDay && activePeriod == periodNum)
                val nowCal = remember(activeDayPeriod) { java.util.Calendar.getInstance() }
                val nowMin = nowCal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + nowCal.get(java.util.Calendar.MINUTE)
                val isToday = selectedDay == activeDay
                val isPastToday = isToday && pt != null && nowMin > (pt.endHour * 60 + pt.endMinute)
                val isUpcomingToday = isToday && pt != null && nowMin < (pt.startHour * 60 + pt.startMinute)

                val periodCells = state.timetable.filter { cell ->
                    cell.day == selectedDay && cell.periodNumber == periodNum &&
                        (!state.profile.showOnlyMyLessons || activeTeacherKey.isBlank() ||
                            cell.teacherName.contains(activeTeacherKey, ignoreCase = true))
                }

                val statusCurrentYellow = Color(0xFFFACC15)
                val statusNextLightGreen = Color(0xFF86EFAC)
                val statusPastLightRed = Color(0xFFFCA5A5)
                val periodStatusColor = when {
                    isLiveNow -> statusCurrentYellow
                    isUpcomingToday -> statusNextLightGreen
                    isPastToday -> statusPastLightRed
                    else -> primaryAccent
                }

                val borderColor by animateColorAsState(
                    targetValue = when {
                        isLiveNow -> statusCurrentYellow.copy(alpha = glowAlpha)
                        isUpcomingToday -> statusNextLightGreen.copy(alpha = 0.85f)
                        isPastToday -> statusPastLightRed.copy(alpha = 0.72f)
                        else -> primaryAccent.copy(alpha = 0.28f)
                    },
                    label = "border_glow"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Timeline Gutter (Time + Spine Dot)
                    Column(
                        modifier = Modifier.width(52.dp).padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = pt?.let { String.format(java.util.Locale.ENGLISH, "%02d:%02d", it.startHour, it.startMinute) } ?: "ح$periodNum",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isToday) periodStatusColor else ext.textMuted
                        )
                        Box(
                            modifier = Modifier
                                .size(if (isLiveNow) 12.dp else 8.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (isToday) periodStatusColor else primaryAccent.copy(alpha = 0.45f))
                        )
                        if (periodNum < maxPeriodsForDay) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(if (periodCells.isEmpty()) 44.dp else 72.dp)
                                    .background(if (isToday) periodStatusColor.copy(alpha = 0.55f) else ext.borderSubtle)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isLiveNow) statusCurrentYellow.copy(alpha = 0.14f) else tableCardBg,
                        border = BorderStroke(if (isLiveNow) 2.2.dp else if (isToday) 1.5.dp else 1.dp, borderColor),
                        shadowElevation = if (isLiveNow) 8.dp else 2.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isToday) periodStatusColor else MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = "الدرس $periodNum",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (isToday) Color(0xFF061124) else primaryAccent,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = pt?.formattedRangeArabic() ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (isToday) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = periodStatusColor
                                        ) {
                                            Text(
                                                text = when {
                                                    isLiveNow -> "الحالي"
                                                    isUpcomingToday -> "قادم"
                                                    else -> "فائت"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF061124),
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { addingPeriodNum = periodNum },
                                        modifier = Modifier.minimumInteractiveComponentSize().testTag("add_period_btn_$periodNum")
                                    ) {
                                        Icon(Icons.Filled.AddCircleOutline, contentDescription = "إضافة حصة", tint = primaryAccent, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }

                        if (periodCells.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { addingPeriodNum = periodNum }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "شاغر — اضغط لإضافة درس في هذا التوقيت",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ext.textMuted
                                )
                                Icon(Icons.Filled.Add, null, tint = primaryAccent, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                periodCells.forEach { cell ->
                                    val isMyCell = activeTeacherKey.isNotBlank() &&
                                        cell.teacherName.contains(activeTeacherKey, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isLiveNow || isMyCell) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
                                        border = BorderStroke(
                                            width = if (isMyCell || isLiveNow) 1.5.dp else 1.dp,
                                            color = if (isLiveNow || isMyCell) primaryAccent else ext.borderSubtle
                                        ),
                                        modifier = Modifier
                                            .width(136.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { editingCell = cell }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "${cell.gradeLevel.shortAr} (${cell.section.id})",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = primaryAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = cell.subject,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = cell.teacherName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = ext.textMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }

                if (breakMinutes > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = primaryAccent.copy(alpha = if (breakMinutes >= 10) 0.22f else 0.12f),
                            border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "استراحة $breakMinutes دقائق",
                                style = MaterialTheme.typography.labelMedium,
                                color = primaryAccent,
                                fontWeight = if (breakMinutes >= 10) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Personal Teacher Name Dialog (no pre-seeded list)
    if (showTeacherNameDialog) {
        var customKey by remember { mutableStateOf(activeTeacherKey) }
        AlertDialog(
            onDismissRequest = { showTeacherNameDialog = false },
            title = { Text("تعديل اسمك للجدول والتنبيهات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "اكتب اسمك ليُضاف تلقائياً إلى دروسك الجديدة وتصلك تنبيهاتها الصوتية:",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textMuted
                    )
                    OutlinedTextField(
                        value = customKey,
                        onValueChange = { customKey = it },
                        label = { Text("اسم المدرس") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateTeacherName(customKey.trim())
                    showTeacherNameDialog = false
                }) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { showTeacherNameDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // Weekly Multi-Day & Time Schedule Builder Dialog
    if (showWeeklyBuilderDialog) {
        WeeklyScheduleBuilderDialog(
            state = state,
            onDismiss = { showWeeklyBuilderDialog = false },
            onSaveWorkingDaysOnly = { days ->
                onUpdateWorkingDays(days)
            },
            onApplyLessonsAndTimes = { newCells, selectedDays, updatedTimes ->
                onAddBatchLessons(newCells, selectedDays, updatedTimes)
                if (selectedDays.isNotEmpty() && selectedDay !in selectedDays) {
                    selectedDay = selectedDays.sortedBy { it.ordinal }.first()
                }
                testNoticeMsg = "تم حفظ ${newCells.size} حصة في جدولك الأسبوعي بنجاح"
                showWeeklyBuilderDialog = false
            }
        )
    }

    if (showColorDialog) {
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text("تغيير ألوان جدول الدروس") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("لون خلفية بطاقات الجدول:", style = MaterialTheme.typography.labelLarge, color = primaryAccent)
                    TimetableBgColorPresets.forEach { preset ->
                        val isSel = state.profile.timetableBgColorHex == preset.hex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onUpdateProfile(state.profile.copy(timetableBgColorHex = preset.hex)) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(if (preset.hex == 0L) ext.cardElevated else Color(preset.hex))
                                )
                                Text(preset.titleAr, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                            if (isSel) Icon(Icons.Filled.CheckCircle, null, tint = primaryAccent, modifier = Modifier.size(18.dp))
                        }
                    }

                    HorizontalDivider()
                    Text("لون العناوين وحدود الحصص:", style = MaterialTheme.typography.labelLarge, color = primaryAccent)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TimetableAccentColorPresets.forEach { preset ->
                            val isSel = state.profile.timetableAccentColorHex == preset.hex
                            FilterChip(
                                selected = isSel,
                                onClick = { onUpdateProfile(state.profile.copy(timetableAccentColorHex = preset.hex)) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(if (preset.hex == 0L) MaterialTheme.colorScheme.primary else Color(preset.hex))
                                    )
                                },
                                label = { Text(preset.titleAr) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showColorDialog = false }) { Text("حفظ وإغلاق") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onUpdateProfile(state.profile.copy(timetableBgColorHex = 0L, timetableAccentColorHex = 0L))
                }) { Text("استعادة الافتراضي") }
            }
        )
    }

    // Clear Schedule Confirmation
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("تفريغ الجدول الأسبوعي") },
            text = { Text("هل تريد حذف جميع الحصص من الجدول للبدء بترتيب جدول جديد؟") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearTimetable()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) { Text("نعم، تفريغ") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("إلغاء") } }
        )
    }

    // Edit cell + Move/Advance/Delay Lesson Period Dialog
    editingCell?.let { cell ->
        var subj by remember { mutableStateOf(cell.subject) }
        var teacher by remember { mutableStateOf(cell.teacherName) }
        var targetMovePeriod by remember { mutableStateOf(cell.periodNumber) }

        AlertDialog(
            onDismissRequest = { editingCell = null },
            title = { Text("تعديل ونقل الدرس: ${cell.day.titleAr} - درس ${cell.periodNumber} (${cell.gradeLevel.shortAr} ${cell.section.id})") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(value = subj, onValueChange = { subj = it }, label = { Text("المادة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = teacher, onValueChange = { teacher = it }, label = { Text("اسم المدرس") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    HorizontalDivider()
                    Text("نقل أو تقديم/تأخير الدرس في نفس اليوم:", style = MaterialTheme.typography.labelLarge, color = primaryAccent)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            enabled = cell.periodNumber > 1,
                            onClick = {
                                onMoveOrSwapCell(cell, cell.periodNumber - 1)
                                editingCell = null
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("تقديم (درس ${cell.periodNumber - 1})") }

                        OutlinedButton(
                            enabled = cell.periodNumber < maxPeriodsForDay,
                            onClick = {
                                onMoveOrSwapCell(cell, cell.periodNumber + 1)
                                editingCell = null
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("تأخير (درس ${cell.periodNumber + 1})") }
                    }
                    Text("أو انقله/بدّله مباشرة إلى الدرس رقم:", style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..maxPeriodsForDay).forEach { pNum ->
                            FilterChip(
                                selected = targetMovePeriod == pNum,
                                onClick = { targetMovePeriod = pNum },
                                label = { Text("درس $pNum") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val edited = cell.copy(subject = subj.trim(), teacherName = teacher.trim())
                    onUpdateCell(edited)
                    if (targetMovePeriod != cell.periodNumber) {
                        onMoveOrSwapCell(edited, targetMovePeriod)
                    }
                    editingCell = null
                }) { Text("حفظ التعديل") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onDeleteCell(cell)
                        editingCell = null
                    }) { Text("حذف", color = DangerRed) }
                    TextButton(onClick = { editingCell = null }) { Text("إلغاء") }
                }
            }
        )
    }

    // Add New Lesson Cell to Selected Period
    addingPeriodNum?.let { pNum ->
        var addGrade by remember { mutableStateOf(state.profile.preferredGrade) }
        var addSection by remember { mutableStateOf(SectionLetter.A) }
        var addSubj by remember { mutableStateOf(state.profile.subjectName) }
        var addTeacher by remember { mutableStateOf(activeTeacherKey.ifBlank { "المدرس" }) }

        AlertDialog(
            onDismissRequest = { addingPeriodNum = null },
            title = { Text("إضافة درس جديد — ${selectedDay.titleAr} (الدرس $pNum)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GradeLevel.entries.forEach { g ->
                            FilterChip(selected = g == addGrade, onClick = { addGrade = g }, label = { Text(g.shortAr) })
                        }
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLetter.entries.forEach { s ->
                            FilterChip(selected = s == addSection, onClick = { addSection = s }, label = { Text("شعبة ${s.id}") })
                        }
                    }
                    OutlinedTextField(value = addSubj, onValueChange = { addSubj = it }, label = { Text("المادة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = addTeacher, onValueChange = { addTeacher = it }, label = { Text("اسم المدرس") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val finalTeacher = addTeacher.trim().ifBlank { "المدرس" }
                    val finalSubj = addSubj.trim().ifBlank { state.profile.subjectName }
                    onUpdateCell(
                        TimetableCell(
                            day = selectedDay,
                            periodNumber = pNum,
                            gradeLevel = addGrade,
                            section = addSection,
                            subject = finalSubj,
                            teacherName = finalTeacher
                        )
                    )
                    addingPeriodNum = null
                }) { Text("إضافة") }
            },
            dismissButton = {
                TextButton(onClick = { addingPeriodNum = null }) { Text("إلغاء") }
            }
        )
    }

    // Swap Two Periods for Entire Day or Teacher
    if (showSwapDayDialog) {
        var pA by remember { mutableStateOf(1) }
        var pB by remember { mutableStateOf(minOf(3, maxPeriodsForDay)) }
        var onlyMine by remember { mutableStateOf(activeTeacherKey.isNotBlank()) }

        AlertDialog(
            onDismissRequest = { showSwapDayDialog = false },
            title = { Text("تقديم وتبديل الدروس ليوم ${selectedDay.titleAr}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("بدّل موقع درسين بضغطة واحدة (مثلاً نقل الدرس الأول إلى الدرس الثالث):", style = MaterialTheme.typography.bodySmall, color = ext.textMuted)
                    Text("الدرس الأول للتبديل:", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..maxPeriodsForDay).forEach { p ->
                            FilterChip(selected = p == pA, onClick = { pA = p }, label = { Text("ح$p") })
                        }
                    }
                    Text("مع الدرس:", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..maxPeriodsForDay).forEach { p ->
                            FilterChip(selected = p == pB, onClick = { pB = p }, label = { Text("ح$p") })
                        }
                    }
                    if (activeTeacherKey.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("تبديل حصص ($activeTeacherKey) فقط", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = onlyMine, onCheckedChange = { onlyMine = it })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSwapDayPeriods(selectedDay, pA, pB, if (onlyMine) activeTeacherKey else null)
                    showSwapDayDialog = false
                }) { Text("تبديل الآن") }
            },
            dismissButton = {
                TextButton(onClick = { showSwapDayDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // Bell times editor per day
    if (showBellTimesDialog) {
        AlertDialog(
            onDismissRequest = { showBellTimesDialog = false },
            title = { Text("توقيت الدروس — ${selectedDay.titleAr}") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "اضبط وقت بداية ونهاية كل درس لهذا اليوم، والتنبيه يعمل قبله بدقيقة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = primaryAccent
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { onApplyDayPeriodPreset(selectedDay, 8, 0, 35, 5, 6) },
                            modifier = Modifier.weight(1f)
                        ) { Text("صباحي 6×35د") }
                        OutlinedButton(
                            onClick = { onApplyDayPeriodPreset(selectedDay, 12, 0, 40, 5, 5) },
                            modifier = Modifier.weight(1f)
                        ) { Text("ظهري 5×40د") }
                    }
                    TextButton(onClick = onResetDefaultSchedule, modifier = Modifier.fillMaxWidth()) {
                        Text("استعادة التوقيت الافتراضي لجميع الأيام", color = primaryAccent)
                    }
                    HorizontalDivider()
                    dayPeriods.forEach { pt ->
                        var sh by remember(pt, selectedDay) { mutableStateOf(pt.startHour.toString()) }
                        var sm by remember(pt, selectedDay) { mutableStateOf(pt.startMinute.toString()) }
                        var eh by remember(pt, selectedDay) { mutableStateOf(pt.endHour.toString()) }
                        var em by remember(pt, selectedDay) { mutableStateOf(pt.endMinute.toString()) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("ح${pt.periodNumber}", modifier = Modifier.width(24.dp), fontWeight = FontWeight.Bold, color = primaryAccent)
                            OutlinedTextField(value = sh, onValueChange = { sh = it }, label = { Text("س") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = sm, onValueChange = { sm = it }, label = { Text("د") }, modifier = Modifier.weight(1f), singleLine = true)
                            Text("-")
                            OutlinedTextField(value = eh, onValueChange = { eh = it }, label = { Text("س") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = em, onValueChange = { em = it }, label = { Text("د") }, modifier = Modifier.weight(1f), singleLine = true)
                            IconButton(onClick = {
                                onUpdateDayPeriodTime(
                                    selectedDay,
                                    pt.copy(
                                        startHour = sh.toIntOrNull()?.coerceIn(0, 23) ?: pt.startHour,
                                        startMinute = sm.toIntOrNull()?.coerceIn(0, 59) ?: pt.startMinute,
                                        endHour = eh.toIntOrNull()?.coerceIn(0, 23) ?: pt.endHour,
                                        endMinute = em.toIntOrNull()?.coerceIn(0, 59) ?: pt.endMinute
                                    )
                                )
                            }) {
                                Icon(Icons.Filled.Check, contentDescription = "تحديث وقت الحصة", tint = primaryAccent)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showBellTimesDialog = false }) { Text("إغلاق") }
            }
        )
    }

    if (showCalendarDialog) {
        CalendarDatePickerDialog(
            initialMillis = selectedCalendarMillis,
            onDismiss = { showCalendarDialog = false },
            onDateSelected = { millis ->
                selectedCalendarMillis = millis
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
                val matchedDay = SchoolDay.entries.find { it.calendarDayOfWeek == cal.get(java.util.Calendar.DAY_OF_WEEK) }
                if (matchedDay != null) selectedDay = matchedDay
                showCalendarDialog = false
            }
        )
    }
}

@Composable
private fun WeeklyScheduleBuilderDialog(
    state: AppState,
    onDismiss: () -> Unit,
    onSaveWorkingDaysOnly: (Set<SchoolDay>) -> Unit,
    onApplyLessonsAndTimes: (List<TimetableCell>, Set<SchoolDay>, Map<SchoolDay, List<PeriodTime>>?) -> Unit
) {
    val ext = LocalExtendedColors.current
    val primaryAccent = MaterialTheme.colorScheme.primary

    var selectedDays by remember {
        mutableStateOf(state.profile.workingDays.ifEmpty { defaultWorkingDays() })
    }
    var teacherInput by remember {
        mutableStateOf(state.profile.timetableTeacherKey.ifBlank { state.profile.teacherName })
    }
    var subjectInput by remember {
        mutableStateOf(state.profile.subjectName.ifBlank { state.profile.subjectSpecialty.titleAr })
    }
    var selectedGrade by remember { mutableStateOf(state.profile.preferredGrade) }
    var selectedSection by remember { mutableStateOf(SectionLetter.A) }

    var applyCustomTiming by remember { mutableStateOf(false) }
    var startHourStr by remember { mutableStateOf("8") }
    var startMinStr by remember { mutableStateOf("0") }
    var periodDurStr by remember { mutableStateOf("40") }
    var breakDurStr by remember { mutableStateOf("5") }
    var periodCount by remember { mutableStateOf(6) }

    var selectedSlots by remember { mutableStateOf<Set<Pair<SchoolDay, Int>>>(emptySet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("منشئ جدولي الأسبوعي (أيام وزمن ودروس)") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("1. اختر أيام الدوام في الأسبوع:", style = MaterialTheme.typography.labelLarge, color = primaryAccent)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SchoolDay.entries.forEach { day ->
                        val active = day in selectedDays
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedDays = if (active && selectedDays.size > 1) {
                                    selectedDays - day
                                } else {
                                    selectedDays + day
                                }
                            },
                            label = { Text(day.titleAr) }
                        )
                    }
                }

                HorizontalDivider()
                Text("2. بيانات درسك (الاسم والمادة والصف):", style = MaterialTheme.typography.labelLarge, color = primaryAccent)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = teacherInput,
                        onValueChange = { teacherInput = it },
                        label = { Text("اسمك") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = subjectInput,
                        onValueChange = { subjectInput = it },
                        label = { Text("المادة") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GradeLevel.entries.forEach { g ->
                        FilterChip(selected = g == selectedGrade, onClick = { selectedGrade = g }, label = { Text(g.shortAr) })
                    }
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLetter.entries.forEach { sec ->
                        FilterChip(selected = sec == selectedSection, onClick = { selectedSection = sec }, label = { Text("شعبة ${sec.id}") })
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("3. تخصيص أوقات الحصص وعددها:", style = MaterialTheme.typography.labelLarge, color = primaryAccent)
                    Switch(checked = applyCustomTiming, onCheckedChange = { applyCustomTiming = it })
                }
                if (applyCustomTiming) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = { startHourStr = "8"; startMinStr = "0"; periodDurStr = "35"; breakDurStr = "5"; periodCount = 6 }, label = { Text("صباحي 8:00") })
                        AssistChip(onClick = { startHourStr = "12"; startMinStr = "0"; periodDurStr = "40"; breakDurStr = "5"; periodCount = 5 }, label = { Text("ظهري 12:00") })
                        AssistChip(onClick = { startHourStr = "14"; startMinStr = "0"; periodDurStr = "40"; breakDurStr = "5"; periodCount = 5 }, label = { Text("مسائي 2:00") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = startHourStr,
                            onValueChange = { startHourStr = it },
                            label = { Text("ساعة البدء") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = startMinStr,
                            onValueChange = { startMinStr = it },
                            label = { Text("دقيقة") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = periodDurStr,
                            onValueChange = { periodDurStr = it },
                            label = { Text("مدة الحصة(د)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = breakDurStr,
                            onValueChange = { breakDurStr = it },
                            label = { Text("استراحة(د)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("عدد الدروس:", style = MaterialTheme.typography.bodySmall)
                        listOf(4, 5, 6, 7).forEach { cnt ->
                            FilterChip(selected = periodCount == cnt, onClick = { periodCount = cnt }, label = { Text("$cnt") })
                        }
                    }
                }

                HorizontalDivider()
                Text("4. حدد حصص هذا الصف في كل يوم (${selectedSlots.size} مختارة):", style = MaterialTheme.typography.labelLarge, color = primaryAccent)
                selectedDays.sortedBy { it.ordinal }.forEach { day ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(day.titleAr, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (1..periodCount).forEach { pNum ->
                                val key = day to pNum
                                val picked = key in selectedSlots
                                FilterChip(
                                    selected = picked,
                                    onClick = {
                                        selectedSlots = if (picked) selectedSlots - key else selectedSlots + key
                                    },
                                    label = { Text("ح$pNum") }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val safeTeacher = teacherInput.trim().ifBlank { "المدرس" }
                    val safeSubject = subjectInput.trim().ifBlank { state.profile.subjectSpecialty.titleAr }
                    val cells = selectedSlots.map { (day, pNum) ->
                        TimetableCell(
                            day = day,
                            periodNumber = pNum,
                            gradeLevel = selectedGrade,
                            section = selectedSection,
                            subject = safeSubject,
                            teacherName = safeTeacher
                        )
                    }
                    val updatedTimes = if (applyCustomTiming) {
                        val sH = startHourStr.toIntOrNull()?.coerceIn(0, 23) ?: 8
                        val sM = startMinStr.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        val pD = periodDurStr.toIntOrNull()?.coerceIn(15, 120) ?: 40
                        val bD = breakDurStr.toIntOrNull()?.coerceIn(0, 45) ?: 5
                        val generated = generatePeriodTimes(sH, sM, pD, bD, periodCount)
                        state.dayPeriodTimes + selectedDays.associateWith { generated }
                    } else null
                    if (cells.isEmpty() && !applyCustomTiming) {
                        onSaveWorkingDaysOnly(selectedDays)
                        onDismiss()
                    } else {
                        onApplyLessonsAndTimes(cells, selectedDays, updatedTimes)
                    }
                },
                modifier = Modifier.testTag("weekly_builder_save_btn")
            ) {
                Text(if (selectedSlots.isEmpty()) "حفظ الأيام والأوقات" else "إضافة (${selectedSlots.size}) حصة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
