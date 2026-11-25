package com.example.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.model.AppState
import com.example.model.PeriodTime
import com.example.model.PrayerLocationSettings
import com.example.model.SchoolDay
import com.example.model.TimetableCell
import com.example.model.defaultPeriodTimesForDay
import java.util.Calendar

enum class LessonBoundaryType {
    BEFORE_START,
    BEFORE_END
}

data class InAppLessonAlert(
    val eventKey: String,
    val title: String,
    val body: String,
    val spokenText: String,
    val boundaryType: LessonBoundaryType
)

data class LiveScheduleBannerInfo(
    val hasLessonNow: Boolean,
    val title: String,
    val subtitle: String,
    val timeRange: String,
    val nextPeriodTitle: String = "لا توجد حصة قادمة",
    val nextPeriodSubtitle: String = "",
    val nextPeriodTimeRange: String = "",
    val hasNextPeriod: Boolean = false
)

object LessonAlarmScheduler {

    private const val LESSON_ALARM_BASE_ID = 2000
    private const val MAX_LESSON_ALARMS = 150
    private const val ATHKAR_MORNING_ID = 7101
    private const val ATHKAR_EVENING_ID = 7102
    private const val PRAYER_BASE_ID = 7200
    private const val FLOATING_DHIKR_ID = 7301

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    private fun setExactOrFallback(alarmManager: AlarmManager, triggerAtMillis: Long, pi: PendingIntent) {
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun periodOrdinalArabic(periodNumber: Int): String = when (periodNumber) {
        1 -> "الأول"
        2 -> "الثاني"
        3 -> "الثالث"
        4 -> "الرابع"
        5 -> "الخامس"
        6 -> "السادس"
        7 -> "السابع"
        8 -> "الثامن"
        else -> "رقم $periodNumber"
    }

    fun buildSpokenAnnouncement(
        boundaryType: LessonBoundaryType,
        periodNumber: Int,
        subject: String,
        leadMinutes: Int
    ): String {
        val ordinal = periodOrdinalArabic(periodNumber)
        val subj = if (subject.isNotBlank()) "، مادة $subject" else ""
        return if (boundaryType == LessonBoundaryType.BEFORE_START) {
            if (leadMinutes == 1) {
                "بعد دقيقة واحدة، بداية الدرس $ordinal$subj"
            } else {
                "بعد $leadMinutes دقائق، بداية الدرس $ordinal$subj"
            }
        } else {
            if (leadMinutes == 1) {
                "متبقي دقيقة واحدة لانتهاء الدرس $ordinal$subj"
            } else {
                "متبقي $leadMinutes دقائق لانتهاء الدرس $ordinal$subj"
            }
        }
    }

    fun rescheduleAll(context: Context, state: AppState) {
        val p = state.profile
        scheduleNextLessons(
            context = context,
            teacherKey = p.timetableTeacherKey.ifBlank { p.teacherName },
            timetable = state.timetable,
            dayPeriodTimes = state.dayPeriodTimes,
            minutesBefore = p.notifyMinutesBefore,
            enabled = p.lessonNotificationsEnabled,
            masterEnabled = p.notificationsMasterEnabled,
            notifyBeforeStart = p.notifyBeforeLessonStart,
            notifyBeforeEnd = p.notifyBeforeLessonEnd,
            halfMinuteEnabled = p.halfMinuteAlertEnabled,
            voiceEnabled = p.voiceAnnouncementEnabled,
            volumePercent = p.alertVolumePercent
        )
        scheduleAthkarAlarms(
            context = context,
            masterEnabled = p.notificationsMasterEnabled,
            morningEnabled = p.morningAthkarNotifyEnabled,
            morningHour = p.morningAthkarHour,
            morningMinute = p.morningAthkarMinute,
            eveningEnabled = p.eveningAthkarNotifyEnabled,
            eveningHour = p.eveningAthkarHour,
            eveningMinute = p.eveningAthkarMinute
        )
        schedulePrayerAlarms(
            context = context,
            masterEnabled = p.notificationsMasterEnabled,
            settings = p.prayerSettings
        )
        scheduleFloatingDhikr(
            context = context,
            enabled = p.floatingDhikrEnabled,
            intervalMinutes = p.floatingDhikrIntervalMinutes,
            text = p.floatingDhikrCustomText
        )
    }

    fun scheduleNextLessons(
        context: Context,
        teacherKey: String,
        timetable: List<TimetableCell>,
        dayPeriodTimes: Map<SchoolDay, List<PeriodTime>>,
        minutesBefore: Int = 1,
        enabled: Boolean,
        masterEnabled: Boolean = true,
        notifyBeforeStart: Boolean = true,
        notifyBeforeEnd: Boolean = true,
        halfMinuteEnabled: Boolean = true,
        voiceEnabled: Boolean = true,
        volumePercent: Int = 95
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until MAX_LESSON_ALARMS) {
            val pi = PendingIntent.getBroadcast(
                context,
                LESSON_ALARM_BASE_ID + i,
                Intent(context, LessonNotificationReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) alarmManager.cancel(pi)
        }
        if (!enabled || !masterEnabled || (!notifyBeforeStart && !notifyBeforeEnd)) return

        val now = Calendar.getInstance()
        val myCells = if (teacherKey.isNotBlank()) {
            timetable.filter { it.teacherName.contains(teacherKey, ignoreCase = true) }
        } else {
            timetable.distinctBy { it.day to it.periodNumber }
        }

        val leadMinutes = minutesBefore.coerceIn(1, 15)
        var alarmIdx = 0

        myCells.forEach { cell ->
            val times = dayPeriodTimes[cell.day] ?: defaultPeriodTimesForDay(cell.day)
            val pt = times.find { it.periodNumber == cell.periodNumber } ?: return@forEach

            if (notifyBeforeStart && alarmIdx < MAX_LESSON_ALARMS) {
                val startTarget = computeNextTriggerCalendar(
                    now = now,
                    day = cell.day,
                    hour = pt.startHour,
                    minute = pt.startMinute,
                    subtractMinutes = leadMinutes
                )
                val ordinal = periodOrdinalArabic(cell.periodNumber)
                val title = "بداية الدرس $ordinal (${cell.day.titleAr})"
                val body = "بعد $leadMinutes دقيقة يبدأ درس ${cell.subject} • ${cell.gradeLevel.shortAr} (${cell.section.id}) — ${pt.formattedRangeArabic()}"
                val spoken = buildSpokenAnnouncement(LessonBoundaryType.BEFORE_START, cell.periodNumber, cell.subject, leadMinutes)
                scheduleSingleBroadcast(
                    context = context,
                    alarmManager = alarmManager,
                    requestCode = LESSON_ALARM_BASE_ID + alarmIdx,
                    triggerAtMillis = startTarget.timeInMillis,
                    title = title,
                    body = body,
                    boundaryType = LessonBoundaryType.BEFORE_START,
                    spokenText = spoken,
                    voiceEnabled = voiceEnabled,
                    volumePercent = volumePercent
                )
                alarmIdx++
            }

            if (notifyBeforeStart && halfMinuteEnabled && alarmIdx < MAX_LESSON_ALARMS) {
                val start30sTarget = computeNextTriggerCalendarSeconds(
                    now = now,
                    day = cell.day,
                    hour = pt.startHour,
                    minute = pt.startMinute,
                    subtractSeconds = 30
                )
                val ordinal = periodOrdinalArabic(cell.periodNumber)
                val subj = if (cell.subject.isNotBlank()) "، مادة ${cell.subject}" else ""
                scheduleSingleBroadcast(
                    context = context,
                    alarmManager = alarmManager,
                    requestCode = LESSON_ALARM_BASE_ID + alarmIdx,
                    triggerAtMillis = start30sTarget.timeInMillis,
                    title = "باقي 30 ثانية — الدرس $ordinal (${cell.day.titleAr})",
                    body = "استعد الآن: يبدأ درس ${cell.subject} • ${cell.gradeLevel.shortAr} (${cell.section.id}) خلال 30 ثانية",
                    boundaryType = LessonBoundaryType.BEFORE_START,
                    spokenText = "متبقي ثلاثون ثانية لبداية الدرس $ordinal$subj",
                    voiceEnabled = voiceEnabled,
                    volumePercent = volumePercent
                )
                alarmIdx++
            }

            if (notifyBeforeEnd && alarmIdx < MAX_LESSON_ALARMS) {
                val endTarget = computeNextTriggerCalendar(
                    now = now,
                    day = cell.day,
                    hour = pt.endHour,
                    minute = pt.endMinute,
                    subtractMinutes = leadMinutes
                )
                val ordinal = periodOrdinalArabic(cell.periodNumber)
                val title = "قرب انتهاء الدرس $ordinal (${cell.day.titleAr})"
                val body = "متبقٍ $leadMinutes دقيقة لانتهاء درس ${cell.subject} • ${cell.gradeLevel.shortAr} (${cell.section.id})"
                val spoken = buildSpokenAnnouncement(LessonBoundaryType.BEFORE_END, cell.periodNumber, cell.subject, leadMinutes)
                scheduleSingleBroadcast(
                    context = context,
                    alarmManager = alarmManager,
                    requestCode = LESSON_ALARM_BASE_ID + alarmIdx,
                    triggerAtMillis = endTarget.timeInMillis,
                    title = title,
                    body = body,
                    boundaryType = LessonBoundaryType.BEFORE_END,
                    spokenText = spoken,
                    voiceEnabled = voiceEnabled,
                    volumePercent = volumePercent
                )
                alarmIdx++
            }
        }
    }

    fun scheduleAthkarAlarms(
        context: Context,
        masterEnabled: Boolean,
        morningEnabled: Boolean,
        morningHour: Int,
        morningMinute: Int,
        eveningEnabled: Boolean,
        eveningHour: Int,
        eveningMinute: Int
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelReceiverAlarm(context, am, ATHKAR_MORNING_ID)
        cancelReceiverAlarm(context, am, ATHKAR_EVENING_ID)
        if (!masterEnabled) return

        val now = Calendar.getInstance()
        if (morningEnabled) {
            val cal = nextDailyCalendar(now, morningHour, morningMinute)
            scheduleSingleBroadcast(
                context = context,
                alarmManager = am,
                requestCode = ATHKAR_MORNING_ID,
                triggerAtMillis = cal.timeInMillis,
                title = "أذكار الصباح",
                body = "أصبحنا وأصبح الملك لله — لا تنسَ قراءة أذكار الصباح ليوم دراسي مبارك.",
                boundaryType = LessonBoundaryType.BEFORE_START,
                spokenText = "",
                voiceEnabled = false,
                volumePercent = 80,
                playSound = true
            )
        }
        if (eveningEnabled) {
            val cal = nextDailyCalendar(now, eveningHour, eveningMinute)
            scheduleSingleBroadcast(
                context = context,
                alarmManager = am,
                requestCode = ATHKAR_EVENING_ID,
                triggerAtMillis = cal.timeInMillis,
                title = "أذكار المساء",
                body = "أمسينا وأمسى الملك لله — حصّن نفسك بأذكار المساء.",
                boundaryType = LessonBoundaryType.BEFORE_START,
                spokenText = "",
                voiceEnabled = false,
                volumePercent = 80,
                playSound = true
            )
        }
    }

    fun schedulePrayerAlarms(
        context: Context,
        masterEnabled: Boolean,
        settings: PrayerLocationSettings
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until 6) {
            cancelReceiverAlarm(context, am, PRAYER_BASE_ID + i)
        }
        if (!masterEnabled) return
        val schedule = OfflinePrayerCalculator.computeSchedule(settings)
        val now = Calendar.getInstance()
        schedule.prayers.forEachIndexed { idx, slot ->
            if (slot.notifyEnabled) {
                val cal = nextDailyCalendar(now, slot.hour, slot.minute)
                scheduleSingleBroadcast(
                    context = context,
                    alarmManager = am,
                    requestCode = PRAYER_BASE_ID + idx,
                    triggerAtMillis = cal.timeInMillis,
                    title = "حان موعد صلاة ${slot.titleAr}",
                    body = "${schedule.cityName} • الساعة ${slot.formattedArabic()}",
                    boundaryType = LessonBoundaryType.BEFORE_START,
                    spokenText = "حان الآن موعد صلاة ${slot.titleAr}",
                    voiceEnabled = false,
                    volumePercent = 85,
                    playSound = true
                )
            }
        }
    }

    fun scheduleFloatingDhikr(
        context: Context,
        enabled: Boolean,
        intervalMinutes: Int,
        text: String
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val existingPi = PendingIntent.getBroadcast(
            context,
            FLOATING_DHIKR_ID,
            Intent(context, FloatingDhikrReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existingPi != null) am.cancel(existingPi)
        if (!enabled) return

        val intervalMs = intervalMinutes.coerceIn(5, 240) * 60_000L
        val intent = Intent(context, FloatingDhikrReceiver::class.java).apply {
            putExtra(FloatingDhikrReceiver.EXTRA_DHIKR_TITLE, "النافذة الإيمانية العائمة")
            putExtra(FloatingDhikrReceiver.EXTRA_DHIKR_TEXT, text)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            FLOATING_DHIKR_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + intervalMs,
            intervalMs,
            pi
        )
    }

    private fun cancelReceiverAlarm(context: Context, am: AlarmManager, requestCode: Int) {
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, LessonNotificationReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) am.cancel(pi)
    }

    private fun nextDailyCalendar(now: Calendar, hour: Int, minute: Int): Calendar =
        (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

    fun computeNextTriggerCalendar(
        now: Calendar,
        day: SchoolDay,
        hour: Int,
        minute: Int,
        subtractMinutes: Int
    ): Calendar {
        return (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_WEEK, day.calendarDayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, -subtractMinutes)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
    }

    fun computeNextTriggerCalendarSeconds(
        now: Calendar,
        day: SchoolDay,
        hour: Int,
        minute: Int,
        subtractSeconds: Int
    ): Calendar {
        return (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_WEEK, day.calendarDayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.SECOND, -subtractSeconds)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
    }

    private fun scheduleSingleBroadcast(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        triggerAtMillis: Long,
        title: String,
        body: String,
        boundaryType: LessonBoundaryType,
        spokenText: String,
        voiceEnabled: Boolean,
        volumePercent: Int,
        playSound: Boolean = true
    ) {
        val intent = Intent(context, LessonNotificationReceiver::class.java).apply {
            putExtra(LessonNotificationReceiver.EXTRA_ID, requestCode)
            putExtra(LessonNotificationReceiver.EXTRA_TITLE, title)
            putExtra(LessonNotificationReceiver.EXTRA_BODY, body)
            putExtra(LessonNotificationReceiver.EXTRA_PLAY_SOUND, playSound)
            putExtra(
                LessonNotificationReceiver.EXTRA_BOUNDARY_TYPE,
                if (boundaryType == LessonBoundaryType.BEFORE_END) "end" else "start"
            )
            putExtra(LessonNotificationReceiver.EXTRA_SPOKEN_TEXT, spokenText)
            putExtra(LessonNotificationReceiver.EXTRA_VOICE_ENABLED, voiceEnabled)
            putExtra(LessonNotificationReceiver.EXTRA_VOLUME_PERCENT, volumePercent)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExactOrFallback(alarmManager, triggerAtMillis, pi)
    }

    fun scheduleTest30SecondNotification(
        context: Context,
        teacherName: String,
        boundaryType: LessonBoundaryType = LessonBoundaryType.BEFORE_START,
        voiceEnabled: Boolean = true,
        volumePercent: Int = 95
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + 30_000L
        val title = if (teacherName.isNotBlank()) "تجربة تنبيه: أ. $teacherName" else "تجربة إشعار رفيق المدرس"
        val spoken = if (boundaryType == LessonBoundaryType.BEFORE_START) {
            "بداية الدرس الثاني، نتمنى لكم حصة موفقة"
        } else {
            "متبقي دقيقة واحدة لانتهاء الدرس الثاني"
        }
        val intent = Intent(context, LessonNotificationReceiver::class.java).apply {
            putExtra(LessonNotificationReceiver.EXTRA_ID, 9930)
            putExtra(LessonNotificationReceiver.EXTRA_TITLE, title)
            putExtra(
                LessonNotificationReceiver.EXTRA_BODY,
                "نجح اختبار الإشعار الدقيق بعد 30 ثانية حتى في الخلفية!"
            )
            putExtra(LessonNotificationReceiver.EXTRA_PLAY_SOUND, true)
            putExtra(
                LessonNotificationReceiver.EXTRA_BOUNDARY_TYPE,
                if (boundaryType == LessonBoundaryType.BEFORE_END) "end" else "start"
            )
            putExtra(LessonNotificationReceiver.EXTRA_SPOKEN_TEXT, spoken)
            putExtra(LessonNotificationReceiver.EXTRA_VOICE_ENABLED, voiceEnabled)
            putExtra(LessonNotificationReceiver.EXTRA_VOLUME_PERCENT, volumePercent)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            9930,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setExactOrFallback(alarmManager, triggerAt, pi)
    }

    fun currentDayAndPeriod(
        dayPeriodTimes: Map<SchoolDay, List<PeriodTime>>,
        cal: Calendar = Calendar.getInstance()
    ): Pair<SchoolDay?, Int?> {
        val day = SchoolDay.entries.find { it.calendarDayOfWeek == cal.get(Calendar.DAY_OF_WEEK) }
            ?: return null to null
        val periods = dayPeriodTimes[day] ?: defaultPeriodTimesForDay(day)
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val activePeriod = periods.firstOrNull { pt ->
            val start = pt.startHour * 60 + pt.startMinute
            val end = pt.endHour * 60 + pt.endMinute
            nowMinutes in start..end
        }?.periodNumber
        return day to activePeriod
    }

    fun computeLiveBannerInfo(
        timetable: List<TimetableCell>,
        dayPeriodTimes: Map<SchoolDay, List<PeriodTime>>,
        teacherKey: String,
        cal: Calendar = Calendar.getInstance()
    ): LiveScheduleBannerInfo {
        val h24 = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        val amPm = if (h24 >= 12) "م" else "ص"
        val nowTimeStr = "%d:%02d %s".format(h12, min, amPm)

        val day = SchoolDay.entries.find { it.calendarDayOfWeek == cal.get(Calendar.DAY_OF_WEEK) }
            ?: return LiveScheduleBannerInfo(false, "الجدول الحي • يوم عطلة أسبوعية", "لا توجد حصص مجدولة اليوم", nowTimeStr)

        val periods = dayPeriodTimes[day] ?: defaultPeriodTimesForDay(day)
        val nowMinutes = h24 * 60 + min
        val myAllCells = timetable.let { list ->
            if (teacherKey.isNotBlank()) {
                val filtered = list.filter { it.teacherName.contains(teacherKey, ignoreCase = true) }
                filtered.ifEmpty { list }
            } else list
        }
        val dayCells = myAllCells.filter { it.day == day }
        val nextOtherInWeek = myAllCells
            .sortedWith(compareBy({ (it.day.ordinal - day.ordinal + 7) % 7 }, { it.periodNumber }))
            .firstOrNull { it.day != day }

        if (dayCells.isEmpty()) {
            return if (nextOtherInWeek != null) {
                val otherTimes = dayPeriodTimes[nextOtherInWeek.day] ?: defaultPeriodTimesForDay(nextOtherInWeek.day)
                val otherPt = otherTimes.find { it.periodNumber == nextOtherInWeek.periodNumber }
                LiveScheduleBannerInfo(
                    hasLessonNow = false,
                    title = "الجدول الحي • اليوم (${day.titleAr}) بدون حصص",
                    subtitle = "أقرب درس: ${nextOtherInWeek.day.titleAr} (ح${nextOtherInWeek.periodNumber}) • ${nextOtherInWeek.gradeLevel.shortAr} (${nextOtherInWeek.section.id})",
                    timeRange = nowTimeStr,
                    nextPeriodTitle = "${nextOtherInWeek.day.titleAr} • الدرس ${nextOtherInWeek.periodNumber} (${nextOtherInWeek.gradeLevel.shortAr} ${nextOtherInWeek.section.id})",
                    nextPeriodSubtitle = nextOtherInWeek.subject,
                    nextPeriodTimeRange = otherPt?.formattedRangeArabic() ?: "",
                    hasNextPeriod = true
                )
            } else {
                LiveScheduleBannerInfo(
                    hasLessonNow = false,
                    title = "الجدول الحي مفعّل • اليوم ${day.titleAr}",
                    subtitle = "اضغط هنا لإضافة حصصك وعرض الدرس الحالي والقادم",
                    timeRange = nowTimeStr,
                    nextPeriodTitle = "أضف حصصك في الجدول الأسبوعي",
                    nextPeriodSubtitle = "لتظهر الحصة القادمة وتنبيهاتها تلقائياً هنا",
                    nextPeriodTimeRange = nowTimeStr,
                    hasNextPeriod = false
                )
            }
        }

        val sortedWithTime = dayCells.mapNotNull { cell ->
            val pt = periods.find { it.periodNumber == cell.periodNumber } ?: return@mapNotNull null
            cell to pt
        }.sortedBy { it.second.startHour * 60 + it.second.startMinute }

        val current = sortedWithTime.firstOrNull { (_, pt) ->
            val s = pt.startHour * 60 + pt.startMinute
            val e = pt.endHour * 60 + pt.endMinute
            nowMinutes in s..e
        }
        val upcoming = sortedWithTime.firstOrNull { (_, pt) ->
            (pt.startHour * 60 + pt.startMinute) > nowMinutes
        }

        val (nextTitle, nextSub, nextRange, hasNext) = when {
            upcoming != null -> {
                val (ucell, upt) = upcoming
                val waitMin = (upt.startHour * 60 + upt.startMinute) - nowMinutes
                val waitLabel = if (waitMin >= 60) "${waitMin / 60} س ${waitMin % 60} د" else "$waitMin دقيقة"
                listOf(
                    "الدرس ${ucell.periodNumber} • ${ucell.gradeLevel.shortAr} (${ucell.section.id})",
                    "${ucell.subject} • تبدأ بعد $waitLabel",
                    upt.formattedRangeArabic(),
                    "true"
                )
            }
            nextOtherInWeek != null -> {
                val otherTimes = dayPeriodTimes[nextOtherInWeek.day] ?: defaultPeriodTimesForDay(nextOtherInWeek.day)
                val otherPt = otherTimes.find { it.periodNumber == nextOtherInWeek.periodNumber }
                listOf(
                    "${nextOtherInWeek.day.titleAr} • الدرس ${nextOtherInWeek.periodNumber} (${nextOtherInWeek.gradeLevel.shortAr} ${nextOtherInWeek.section.id})",
                    nextOtherInWeek.subject,
                    otherPt?.formattedRangeArabic() ?: "",
                    "true"
                )
            }
            else -> listOf("انتهت حصص اليوم", "لا توجد حصص قادمة متبقية", nowTimeStr, "false")
        }

        if (current != null) {
            val (cell, pt) = current
            val rem = (pt.endHour * 60 + pt.endMinute) - nowMinutes
            return LiveScheduleBannerInfo(
                hasLessonNow = true,
                title = "الجدول الحي • الآن الحصة ${cell.periodNumber} (${cell.gradeLevel.shortAr} ${cell.section.id})",
                subtitle = "${cell.subject} • متبقي $rem دقيقة لانتهاء الدرس",
                timeRange = pt.formattedRangeArabic(),
                nextPeriodTitle = nextTitle,
                nextPeriodSubtitle = nextSub,
                nextPeriodTimeRange = nextRange,
                hasNextPeriod = hasNext == "true"
            )
        }

        return if (upcoming != null) {
            val (cell, pt) = upcoming
            val waitMin = (pt.startHour * 60 + pt.startMinute) - nowMinutes
            LiveScheduleBannerInfo(
                hasLessonNow = false,
                title = "الحصة القادمة: الدرس ${cell.periodNumber} • ${cell.gradeLevel.shortAr} (${cell.section.id})",
                subtitle = "المادة: ${cell.subject} • تبدأ بعد $waitMin دقيقة",
                timeRange = pt.formattedRangeArabic(),
                nextPeriodTitle = nextTitle,
                nextPeriodSubtitle = nextSub,
                nextPeriodTimeRange = nextRange,
                hasNextPeriod = true
            )
        } else {
            LiveScheduleBannerInfo(
                hasLessonNow = false,
                title = "الجدول الحي • انتهت حصص ${day.titleAr}",
                subtitle = "لا توجد حصص متبقية لهذا اليوم",
                timeRange = nowTimeStr,
                nextPeriodTitle = nextTitle,
                nextPeriodSubtitle = nextSub,
                nextPeriodTimeRange = nextRange,
                hasNextPeriod = hasNext == "true"
            )
        }
    }

    fun detectDueInAppAlert(
        teacherKey: String,
        timetable: List<TimetableCell>,
        dayPeriodTimes: Map<SchoolDay, List<PeriodTime>>,
        leadMinutes: Int = 1,
        notifyBeforeStart: Boolean = true,
        notifyBeforeEnd: Boolean = true,
        halfMinuteEnabled: Boolean = true,
        cal: Calendar = Calendar.getInstance()
    ): InAppLessonAlert? {
        if (!notifyBeforeStart && !notifyBeforeEnd) return null
        val day = SchoolDay.entries.find { it.calendarDayOfWeek == cal.get(Calendar.DAY_OF_WEEK) }
            ?: return null
        val periods = dayPeriodTimes[day] ?: defaultPeriodTimesForDay(day)
        val nowSeconds = cal.get(Calendar.HOUR_OF_DAY) * 3600 +
            cal.get(Calendar.MINUTE) * 60 +
            cal.get(Calendar.SECOND)
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val dateStamp = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"

        val dayCells = timetable.filter { it.day == day }
        val relevantPeriods = if (teacherKey.isNotBlank()) {
            dayCells.filter { it.teacherName.contains(teacherKey, ignoreCase = true) }
        } else {
            dayCells.distinctBy { it.periodNumber }
        }

        for (cell in relevantPeriods) {
            val pt = periods.find { it.periodNumber == cell.periodNumber } ?: continue
            val startTotal = pt.startHour * 60 + pt.startMinute
            val startSec = startTotal * 60
            val endTotal = pt.endHour * 60 + pt.endMinute
            val targetStartAlert = startTotal - leadMinutes.coerceIn(1, 15)
            val targetEndAlert = endTotal - leadMinutes.coerceIn(1, 15)
            val ordinal = periodOrdinalArabic(cell.periodNumber)

            if (notifyBeforeStart && halfMinuteEnabled) {
                val remSec = startSec - nowSeconds
                if (remSec in 1..30) {
                    val subj = if (cell.subject.isNotBlank()) "، مادة ${cell.subject}" else ""
                    return InAppLessonAlert(
                        eventKey = "${dateStamp}_${day.id}_p${cell.periodNumber}_start30s",
                        title = "باقي 30 ثانية — الدرس $ordinal",
                        body = "استعد الآن! يبدأ درس ${cell.subject} • ${cell.gradeLevel.shortAr} (${cell.section.id}) خلال أقل من 30 ثانية.",
                        spokenText = "متبقي ثلاثون ثانية لبداية الدرس $ordinal$subj",
                        boundaryType = LessonBoundaryType.BEFORE_START
                    )
                }
            }

            if (notifyBeforeStart && nowMinutes == targetStartAlert) {
                return InAppLessonAlert(
                    eventKey = "${dateStamp}_${day.id}_p${cell.periodNumber}_start",
                    title = "بداية الدرس $ordinal",
                    body = "بعد $leadMinutes دقيقة يبدأ درس ${cell.subject} • ${cell.gradeLevel.shortAr} (${cell.section.id}) (${pt.formattedRangeArabic()})",
                    spokenText = buildSpokenAnnouncement(LessonBoundaryType.BEFORE_START, cell.periodNumber, cell.subject, leadMinutes),
                    boundaryType = LessonBoundaryType.BEFORE_START
                )
            }
            if (notifyBeforeEnd && nowMinutes == targetEndAlert) {
                return InAppLessonAlert(
                    eventKey = "${dateStamp}_${day.id}_p${cell.periodNumber}_end",
                    title = "نهاية الدرس $ordinal",
                    body = "متبقٍ $leadMinutes دقيقة لانتهاء درس ${cell.subject} • ${cell.gradeLevel.shortAr} (${cell.section.id})",
                    spokenText = buildSpokenAnnouncement(LessonBoundaryType.BEFORE_END, cell.periodNumber, cell.subject, leadMinutes),
                    boundaryType = LessonBoundaryType.BEFORE_END
                )
            }
        }
        return null
    }
}
