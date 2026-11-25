package com.example.model

import com.example.ui.theme.AppFontOption
import com.example.ui.theme.AppTextTone
import com.example.ui.theme.StudentColorTag
import com.example.ui.theme.ThemePalette
import java.util.Calendar

enum class GradeLevel(val id: String, val titleAr: String, val shortAr: String) {
    FIRST("1", "الأول المتوسط", "الأول"),
    SECOND("2", "الثاني المتوسط", "الثاني"),
    THIRD("3", "الثالث المتوسط", "الثالث")
}

enum class SectionLetter(val id: String, val titleAr: String) {
    A("أ", "شعبة ( أ )"),
    B("ب", "شعبة ( ب )"),
    C("ج", "شعبة ( ج )"),
    D("د", "شعبة ( د )"),
    E("هـ", "شعبة ( هـ )")
}

enum class SubjectSpecialty(val id: String, val titleAr: String) {
    ISLAMIC("islamic", "التربية الإسلامية"),
    ARABIC("arabic", "اللغة العربية"),
    ENGLISH("english", "اللغة الإنكليزية"),
    SOCIAL_STUDIES("social", "الاجتماعيات"),
    MATH("math", "الرياضيات"),
    PHYSICS("physics", "الفيزياء"),
    CHEMISTRY("chemistry", "الكيمياء"),
    BIOLOGY("biology", "الأحياء"),
    COMPUTER("computer", "الحاسوب"),
    SPORTS("sports", "التربية الرياضية"),
    GENERAL("general", "تخصص عام / أخرى")
}

enum class DailyCalcMode(val id: String, val titleAr: String, val descriptionAr: String) {
    NEVER(
        "never",
        "لا يُحسب نهائياً",
        "تُسجَّل درجات اليومي والسلوك للمتابعة فقط ولا تدخل في احتساب المعدل."
    ),
    WITH_EACH_MONTH(
        "per_month",
        "يُحسب مع كل شهر",
        "يُدمج معدل اليومي مع درجة كل شهر (متوسط الشهر واليومي) قبل استخراج معدل الفصل."
    ),
    ONCE_WITH_TWO_MONTHS(
        "once_per_term",
        "مرة واحدة مع الشهرين",
        "تُجمع أشهر الفصل مع درجة اليومي كحقل مستقل وتُقسم على عددها."
    )
}

enum class NotebookStatus(val titleAr: String) {
    EXCELLENT("مكتمل ومرتب"),
    GOOD("جيد"),
    INCOMPLETE("ناقص"),
    MISSING("بدون دفتر")
}

enum class BehaviorStatus(val titleAr: String) {
    EXEMPLARY("متميز"),
    GOOD("منضبط"),
    NEEDS_ATTENTION("يحتاج متابعة"),
    BAD("سلوك سيء")
}

data class StudentGrades(
    val term1Month1: Double? = null,
    val term1Month2: Double? = null,
    val term1Month3: Double? = null,
    val term1Daily: Double? = null,
    val term1Month1Daily: Double? = null,
    val term1Month2Daily: Double? = null,
    val midYearTotal: Double? = null,
    val midYearOral: Double? = null,
    val midYearWritten: Double? = null,
    val term2Month1: Double? = null,
    val term2Month2: Double? = null,
    val term2Month3: Double? = null,
    val term2Daily: Double? = null,
    val term2Month1Daily: Double? = null,
    val term2Month2Daily: Double? = null,
    val finalExam: Double? = null
)

enum class LedgerSubject(val id: String, val titleAr: String, val shortAr: String) {
    GENERAL("general", "السجل الرئيسي (المادة)", "السجل الرئيسي"),
    MATH("math", "سجل الرياضيات", "الرياضيات")
}

data class CustomLedgerDef(
    val id: String,
    val title: String,
    val headerColorHex: Long = 0xFFFFD54FL,
    val customHeaders: GradebookHeaders? = null
)

fun defaultLedgersForSubject(subjectTitle: String): List<CustomLedgerDef> = listOf(
    CustomLedgerDef(
        id = "general",
        title = subjectTitle.ifBlank { "الأحياء" },
        headerColorHex = 0xFFFFD54FL
    )
)

enum class DailyPeriodSlot(val id: String, val titleAr: String, val shortAr: String, val termNumber: Int) {
    T1_M1("t1_m1", "يومي الشهر الأول (الفصل الأول)", "يومي ش1 ف1", 1),
    T1_M2("t1_m2", "يومي الشهر الثاني (الفصل الأول)", "يومي ش2 ف1", 1),
    T2_M1("t2_m1", "يومي الشهر الأول (الفصل الثاني)", "يومي ش1 ف2", 2),
    T2_M2("t2_m2", "يومي الشهر الثاني (الفصل الثاني)", "يومي ش2 ف2", 2)
}

data class DailyColumnDef(
    val id: String,
    val ledgerSubject: LedgerSubject = LedgerSubject.GENERAL,
    val slot: DailyPeriodSlot = DailyPeriodSlot.T1_M1,
    val title: String,
    val maxScore: Double = 25.0,
    val ledgerId: String = "general"
)

fun defaultAvailableBadges(): List<String> = listOf(
    "#متميز",
    "#مشارك",
    "#خلوق",
    "#بطل_الواجب",
    "#تحسن_ملحوظ",
    "#يحتاج_متابعة",
    "#سيء_السلوك",
    "#كثير_الغياب"
)

data class Student(
    val id: String,
    val orderNumber: Int,
    val fullName: String,
    val gradeLevel: GradeLevel,
    val section: SectionLetter,
    val birthDate: String = "",
    val registryNumber: String = "",
    val phoneNumber: String = "",
    val notes: String = "",
    val colorTag: StudentColorTag = StudentColorTag.NONE,
    val behaviorStatus: BehaviorStatus = BehaviorStatus.GOOD,
    val behaviorScore: Int = 10,
    val isInactive: Boolean = false,
    val isTransferred: Boolean = false,
    val badges: List<String> = emptyList(),
    val notebookStatus: NotebookStatus = NotebookStatus.GOOD,
    val dailyPrepDone: Boolean = true,
    val participationPoints: Int = 0,
    val homeworkPoints: Int = 0,
    val behaviorPoints: Int = 0,
    val weeklyPoints: Int = 0,
    val grades: StudentGrades = StudentGrades(),
    val mathGrades: StudentGrades = StudentGrades(),
    val extraGrades: Map<String, StudentGrades> = emptyMap()
) {
    val totalPoints: Int
        get() = participationPoints + homeworkPoints + behaviorPoints

    fun gradesFor(subject: LedgerSubject): StudentGrades =
        if (subject == LedgerSubject.MATH) mathGrades else grades

    fun withGradesFor(subject: LedgerSubject, updated: StudentGrades): Student =
        if (subject == LedgerSubject.MATH) copy(mathGrades = updated) else copy(grades = updated)

    fun gradesForLedger(ledgerId: String): StudentGrades = when (ledgerId) {
        "general", "" -> grades
        "math" -> mathGrades
        else -> extraGrades[ledgerId] ?: StudentGrades()
    }

    fun withGradesForLedger(ledgerId: String, updated: StudentGrades): Student = when (ledgerId) {
        "general", "" -> copy(grades = updated)
        "math" -> copy(mathGrades = updated)
        else -> copy(extraGrades = extraGrades + (ledgerId to updated))
    }
}

data class GradebookHeaders(
    val seqHeader: String = "ت",
    val nameHeader: String = "اسم الطالب",
    val t1M1Header: String = "ش1 ف1",
    val t1M2Header: String = "ش2 ف1",
    val t1M3Header: String = "ش3 ف1",
    val t1DailyHeader: String = "يومي ف1",
    val t1AvgHeader: String = "معدل ف1",
    val midYearHeader: String = "نصف السنة",
    val t2M1Header: String = "ش1 ف2",
    val t2M2Header: String = "ش2 ف2",
    val t2M3Header: String = "ش3 ف2",
    val t2DailyHeader: String = "يومي ف2",
    val t2AvgHeader: String = "معدل ف2",
    val annualEffortHeader: String = "السعي السنوي",
    val finalExamHeader: String = "الامتحان النهائي",
    val finalGradeHeader: String = "الدرجة النهائية"
)

fun presetHeadersForSpecialty(specialty: SubjectSpecialty): GradebookHeaders = when (specialty) {
    SubjectSpecialty.ISLAMIC -> GradebookHeaders(
        t1M1Header = "تلاوة ف1",
        t1M2Header = "حفظ وتفسير",
        t1M3Header = "عقيدة وفقه",
        t1DailyHeader = "يومي ف1",
        t1AvgHeader = "معدل ف1",
        midYearHeader = "نصف السنة",
        t2M1Header = "تلاوة ف2",
        t2M2Header = "حديث وتهذيب",
        t2M3Header = "فقه ف2",
        t2DailyHeader = "يومي ف2",
        t2AvgHeader = "معدل ف2",
        annualEffortHeader = "السعي السنوي",
        finalExamHeader = "الامتحان النهائي",
        finalGradeHeader = "الدرجة النهائية"
    )
    SubjectSpecialty.ARABIC -> GradebookHeaders(
        t1M1Header = "قواعد ف1",
        t1M2Header = "أدب ونصوص",
        t1M3Header = "إملاء وإنشاء",
        t1DailyHeader = "يومي ف1",
        t1AvgHeader = "معدل ف1",
        midYearHeader = "نصف السنة",
        t2M1Header = "قواعد ف2",
        t2M2Header = "أدب ف2",
        t2M3Header = "إنشاء ومطالعة",
        t2DailyHeader = "يومي ف2",
        t2AvgHeader = "معدل ف2",
        annualEffortHeader = "السعي السنوي",
        finalExamHeader = "الامتحان النهائي",
        finalGradeHeader = "الدرجة النهائية"
    )
    SubjectSpecialty.ENGLISH -> GradebookHeaders(
        t1M1Header = "Reading",
        t1M2Header = "Grammar",
        t1M3Header = "Writing/Vocab",
        t1DailyHeader = "Daily T1",
        t1AvgHeader = "Avg T1",
        midYearHeader = "Mid-Year",
        t2M1Header = "Reading 2",
        t2M2Header = "Grammar 2",
        t2M3Header = "Speaking 2",
        t2DailyHeader = "Daily T2",
        t2AvgHeader = "Avg T2",
        annualEffortHeader = "السعي السنوي",
        finalExamHeader = "Final Exam",
        finalGradeHeader = "الدرجة النهائية"
    )
    else -> GradebookHeaders()
}

data class GradebookSettings(
    val monthsPerTerm: Int = 2,
    val dailyCalcMode: DailyCalcMode = DailyCalcMode.NEVER,
    val splitMidYearOralWritten: Boolean = false,
    val enableSmartRounding: Boolean = true,
    val passingScore: Int = 50,
    val exemptionThreshold: Int = 90,
    val hideTransferredStudents: Boolean = true,
    val includeTransferredInStats: Boolean = false,
    val headers: GradebookHeaders = GradebookHeaders()
)

enum class SchoolDay(val id: String, val titleAr: String, val calendarDayOfWeek: Int) {
    SUNDAY("sun", "الأحد", Calendar.SUNDAY),
    MONDAY("mon", "الاثنين", Calendar.MONDAY),
    TUESDAY("tue", "الثلاثاء", Calendar.TUESDAY),
    WEDNESDAY("wed", "الأربعاء", Calendar.WEDNESDAY),
    THURSDAY("thu", "الخميس", Calendar.THURSDAY),
    FRIDAY("fri", "الجمعة", Calendar.FRIDAY),
    SATURDAY("sat", "السبت", Calendar.SATURDAY);

    val isAfternoonShiftDefault: Boolean
        get() = this == SUNDAY || this == MONDAY
}

fun defaultWorkingDays(): Set<SchoolDay> = setOf(
    SchoolDay.SUNDAY,
    SchoolDay.MONDAY,
    SchoolDay.TUESDAY,
    SchoolDay.WEDNESDAY,
    SchoolDay.THURSDAY
)

data class PeriodTime(
    val periodNumber: Int,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
) {
    fun formattedRange(): String =
        "%02d:%02d - %02d:%02d".format(startHour, startMinute, endHour, endMinute)

    fun formattedRangeArabic(): String {
        fun fmt12(h: Int, m: Int): String {
            val h12 = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            return "%d:%02d".format(h12, m)
        }
        val suffix = if (startHour >= 12) "ظ" else "ص"
        return "${fmt12(startHour, startMinute)} - ${fmt12(endHour, endMinute)} $suffix"
    }
}

data class TimetableCell(
    val day: SchoolDay,
    val periodNumber: Int,
    val gradeLevel: GradeLevel,
    val section: SectionLetter,
    val subject: String,
    val teacherName: String
)

data class StudentPaperGroup(
    val id: String,
    val studentId: String = "",
    val studentName: String,
    val gradeLevel: GradeLevel,
    val section: SectionLetter,
    val scoreText: String = "",
    val notes: String = "",
    val pageFileNames: List<String> = emptyList(),
    val themeColorHex: Long = 0xFF1E88E5L,
    val createdAtMillis: Long = System.currentTimeMillis()
)

enum class ExamArchiveCategory(val id: String, val titleAr: String, val colorHex: Long) {
    DAILY("daily", "امتحانات يومية", 0xFF00BFA5L),
    MONTHLY("monthly", "امتحانات شهرية", 0xFF2979FFL),
    MID_YEAR("mid_year", "نصف السنة", 0xFFFF9100L),
    PREVIOUS_YEARS("prev_years", "سنوات سابقة ووزاري", 0xFFAA00FFL)
}

data class ExamArchiveItem(
    val id: String,
    val title: String,
    val category: ExamArchiveCategory,
    val gradeLevel: GradeLevel,
    val fileName: String,
    val mimeType: String,
    val notes: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class SectionStarEntry(
    val id: String,
    val studentId: String = "",
    val studentName: String,
    val gradeLevel: GradeLevel,
    val section: SectionLetter,
    val customTitle: String = "نجم الشعبة",
    val emblem: String = "⭐",
    val photoFileName: String? = null,
    val glowColorHex: Long = 0xFFFFD54FL,
    val orbitTier: Int = 1
)

data class PrayerLocationSettings(
    val cityName: String = "الفلوجة - الأنبار",
    val latitude: Double = 33.3491,
    val longitude: Double = 43.7859,
    val timezoneOffsetHours: Double = 3.0,
    val fajrNotifyEnabled: Boolean = false,
    val dhuhrNotifyEnabled: Boolean = false,
    val asrNotifyEnabled: Boolean = false,
    val maghribNotifyEnabled: Boolean = false,
    val ishaNotifyEnabled: Boolean = false
)

enum class HeroBackgroundMode(val id: String, val titleAr: String) {
    NAME_ONLY("name_only", "اسم المدرس فقط (إخفاء الخلفية)"),
    GALLERY_IMAGE("gallery_image", "صورة / دعاء من المعرض"),
    TEACHER_LOGO("teacher_logo", "لوغو المدرس"),
    DUA_TEXT("dua_text", "دعاء مكتوب بزخرفة")
}

enum class HeroBackgroundSize(val id: String, val titleAr: String) {
    HIDDEN("hidden", "مخفية"),
    MINI("mini", "مصغّرة"),
    MEDIUM("medium", "متوسطة"),
    FULL("full", "ملء الخلفية")
}

data class TeacherProfile(
    val teacherName: String = "",
    val timetableTeacherKey: String = "",
    val schoolName: String = "مدرستي",
    val subjectName: String = "الأحياء",
    val subjectSpecialty: SubjectSpecialty = SubjectSpecialty.BIOLOGY,
    val preferredGrade: GradeLevel = GradeLevel.FIRST,
    val academicYear: String = "2026 - 2027",
    val showOnlyMyLessons: Boolean = false,
    val workingDays: Set<SchoolDay> = defaultWorkingDays(),
    val notificationsMasterEnabled: Boolean = true,
    val lessonNotificationsEnabled: Boolean = true,
    val notifyMinutesBefore: Int = 1,
    val periodDurationMinutes: Int = 40,
    val breakDurationMinutes: Int = 5,
    val schoolStartHour: Int = 8,
    val schoolStartMinute: Int = 0,
    val themePalette: ThemePalette = ThemePalette.DESIGN_7_MIDNIGHT_NEON,
    val useSpecialtyTheme: Boolean = false,
    val textTone: AppTextTone = AppTextTone.AUTO,
    val isDarkMode: Boolean = true,
    val neonGlowEnabled: Boolean = true,
    val fontOption: AppFontOption = AppFontOption.CAIRO,
    val fontScaleMultiplier: Float = 1.0f,
    val vaultPin: String = "1234",
    val teacherSelectedOnStartup: Boolean = false,
    val enableGeneralLedger: Boolean = true,
    val enableMathLedger: Boolean = false,
    val activeLedgerSubject: LedgerSubject = LedgerSubject.GENERAL,
    val notifyBeforeLessonStart: Boolean = true,
    val notifyBeforeLessonEnd: Boolean = true,
    val voiceAnnouncementEnabled: Boolean = true,
    val alertVolumePercent: Int = 95,
    val questionBankLockEnabled: Boolean = true,
    val questionBankPin: String = "1234",
    val questionBankBiometricEnabled: Boolean = true,
    val questionBankThemeDark: Boolean = true,
    val customLedgers: List<CustomLedgerDef> = defaultLedgersForSubject("الأحياء"),
    val activeLedgerId: String = "general",
    val autoScrollEnabled: Boolean = false,
    val autoScrollHighlightColorHex: Long = 0xFF00E5FFL,
    val appLockEnabled: Boolean = false,
    val appLockFingerprintEnabled: Boolean = true,
    val appLockPassword: String = "1234",
    val safeGradeEditLockEnabled: Boolean = false,
    val splashEnabled: Boolean = true,
    val splashShowName: Boolean = true,
    val splashShowLogo: Boolean = true,
    val splashLogoFileName: String? = null,
    val splashGreeting: String = "بسم الله نبدأ — أهلاً وسهلاً أستاذنا الكريم",
    val splashEmoji: String = "",
    val profileCoverFileName: String? = null,
    val teacherBio: String = "",
    val liveScheduleBannerEnabled: Boolean = true,
    val floatingDhikrEnabled: Boolean = false,
    val floatingDhikrIntervalMinutes: Int = 60,
    val floatingDhikrCustomText: String = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ",
    val morningAthkarNotifyEnabled: Boolean = false,
    val eveningAthkarNotifyEnabled: Boolean = false,
    val morningAthkarHour: Int = 6,
    val morningAthkarMinute: Int = 30,
    val eveningAthkarHour: Int = 17,
    val eveningAthkarMinute: Int = 30,
    val availableBadges: List<String> = defaultAvailableBadges(),
    val toolOrder: List<String> = emptyList(),
    val toolCustomImages: Map<String, String> = emptyMap(),
    val toolCustomColors: Map<String, Long> = emptyMap(),
    val toolWhiteBackdrop: Map<String, Boolean> = emptyMap(),
    val quranLastSurahId: Int = 1,
    val quranLastAyahNumber: Int = 1,
    val quranColoredAyahs: Map<String, Long> = emptyMap(),
    val prayerSettings: PrayerLocationSettings = PrayerLocationSettings(),
    val analyticsShowExemptions: Boolean = true,
    val analyticsShowGrade3: Boolean = true,
    val analyticsShowPassFail: Boolean = true,
    val analyticsShowCriticalCases: Boolean = true,
    val heroBgMode: HeroBackgroundMode = HeroBackgroundMode.NAME_ONLY,
    val heroBgSize: HeroBackgroundSize = HeroBackgroundSize.HIDDEN,
    val heroBgFileName: String? = null,
    val heroBgOpacity: Float = 0.42f,
    val heroShowTeacherName: Boolean = true,
    val heroDuaText: String = "",
    val timetableBgColorHex: Long = 0L,
    val timetableAccentColorHex: Long = 0L,
    val studentNamesColorHex: Long = 0L,
    val gradebookTableBgColorHex: Long = 0L,
    val accountUsername: String = "",
    val halfMinuteAlertEnabled: Boolean = true,
    val feedbackLockedUntilMillis: Long = 0L,
    val lastComplaintText: String = "",
    val unlockedPointsTools: List<String> = emptyList(),
    val hiddenTools: List<String> = emptyList(),
    val toolTileScale: Float = 1.0f,
    val appBgImageFileName: String? = null,
    val sectionBgImageFiles: Map<String, String> = emptyMap(),
    val dismissedNotificationIds: List<String> = emptyList(),
    val lastAppActiveMillis: Long = 0L,
    val lastPasswordChangeMillis: Long = 0L,
    val lastNameChangeMillis: Long = 0L,
    val initialNameChosen: Boolean = false,
    val autoGeneratedUsername: String = "",
    val deviceInstallId: String = "",
    val adminSectionUnlocked: Boolean = false,
    val customColorsEnabled: Boolean = false,
    val customTextColorHex: Long = 0L,
    val customButtonColorHex: Long = 0L,
    val customBgColorHex: Long = 0L,
    val customCardAccentColorHex: Long = 0L,
    val appliedInternalUpdateRevision: Long = 0L,
    val preferDefaultDesign: Boolean = false,
    val adminLocalExperimentalMode: String = "CLASSIC",
    val archivedTools: List<String> = emptyList(),
    val favoriteTools: List<String> = emptyList(),
    val gradebookSimpleMode: Boolean = false,
    val gradebookVisibleActions: List<String> = emptyList(),
    val chatActiveOnlineVisible: Boolean = true
) {
    fun effectiveActiveLedger(): LedgerSubject =
        if (activeLedgerId == "math") LedgerSubject.MATH else LedgerSubject.GENERAL

    fun effectiveLedgers(): List<CustomLedgerDef> =
        customLedgers.ifEmpty { defaultLedgersForSubject(subjectName.ifBlank { subjectSpecialty.titleAr }) }

    fun currentLedgerDef(): CustomLedgerDef {
        val list = effectiveLedgers()
        return list.find { it.id == activeLedgerId } ?: list.first()
    }
}

data class AbsenceRecord(
    val id: String,
    val studentId: String,
    val dateStr: String,
    val reason: String = "غياب"
)

data class VaultNote(
    val id: String,
    val title: String,
    val content: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class TeacherTask(
    val id: String,
    val title: String,
    val category: String = "يومي",
    val isDone: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class CurriculumChapter(
    val id: String,
    val gradeLevel: GradeLevel,
    val title: String,
    val isCompleted: Boolean = false
)

data class QuickStickyNote(
    val id: String,
    val title: String,
    val content: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class LibraryFileItem(
    val id: String,
    val displayName: String,
    val localFileName: String,
    val mimeType: String,
    val addedAtMillis: Long = System.currentTimeMillis(),
    val lastPageIndex: Int = 0
)

data class QuestionSectionDef(
    val id: String,
    val title: String,
    val colorHex: Long,
    val isBuiltIn: Boolean = false
)

data class QuestionChapter(
    val id: String,
    val chapterNumber: Int,
    val title: String,
    val gradeWeight: Int = 25
)

data class BankQuestion(
    val id: String,
    val chapterId: String,
    val sectionId: String,
    val questionText: String,
    val answerText: String = "",
    val marks: Int = 5,
    val imageFileName: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class ExamSlotConfig(
    val id: String,
    val questionNumber: Int,
    val sectionId: String,
    val customHeading: String = "",
    val totalMarks: Int = 20,
    val branchCount: Int = 4
)

data class GeneratedExamQuestionBlock(
    val questionNumber: Int,
    val sectionTitle: String,
    val sectionColorHex: Long,
    val totalMarks: Int,
    val branches: List<BankQuestion>
)

data class GeneratedExamPaper(
    val examTitle: String,
    val subjectName: String,
    val gradeTitle: String,
    val examDate: String,
    val examDuration: String,
    val teacherName: String,
    val schoolName: String,
    val noteLine: String,
    val totalMarks: Int,
    val blocks: List<GeneratedExamQuestionBlock>
)

data class AppState(
    val profile: TeacherProfile = TeacherProfile(),
    val gradebookSettings: GradebookSettings = GradebookSettings(),
    val students: List<Student> = emptyList(),
    val timetable: List<TimetableCell> = emptyList(),
    val dayPeriodTimes: Map<SchoolDay, List<PeriodTime>> = defaultDayPeriodTimes(),
    val absences: List<AbsenceRecord> = emptyList(),
    val vaultNotes: List<VaultNote> = emptyList(),
    val tasks: List<TeacherTask> = emptyList(),
    val chapters: List<CurriculumChapter> = emptyList(),
    val quickNotes: List<QuickStickyNote> = emptyList(),
    val libraryFiles: List<LibraryFileItem> = emptyList(),
    val dailyColumns: List<DailyColumnDef> = defaultDailyColumns(),
    val dailyScores: Map<String, Double> = emptyMap(),
    val questionChapters: List<QuestionChapter> = defaultQuestionChapters(),
    val questionSections: List<QuestionSectionDef> = defaultQuestionSections(),
    val bankQuestions: List<BankQuestion> = emptyList(),
    val studentPapers: List<StudentPaperGroup> = emptyList(),
    val examArchive: List<ExamArchiveItem> = emptyList(),
    val sectionStars: List<SectionStarEntry> = emptyList()
) {
    fun periodTimesForDay(day: SchoolDay): List<PeriodTime> =
        dayPeriodTimes[day] ?: defaultPeriodTimesForDay(day)
}

fun defaultAfternoonPeriodTimes(): List<PeriodTime> = listOf(
    PeriodTime(1, 12, 0, 12, 40),
    PeriodTime(2, 12, 45, 13, 25),
    PeriodTime(3, 13, 35, 14, 15),
    PeriodTime(4, 14, 20, 15, 0),
    PeriodTime(5, 15, 5, 15, 45)
)

fun defaultMorningPeriodTimes(): List<PeriodTime> = listOf(
    PeriodTime(1, 8, 0, 8, 35),
    PeriodTime(2, 8, 40, 9, 15),
    PeriodTime(3, 9, 20, 9, 55),
    PeriodTime(4, 10, 0, 10, 35),
    PeriodTime(5, 10, 40, 11, 15),
    PeriodTime(6, 11, 20, 11, 55)
)

fun defaultPeriodTimesForDay(day: SchoolDay): List<PeriodTime> = when (day) {
    SchoolDay.SUNDAY, SchoolDay.MONDAY -> defaultAfternoonPeriodTimes()
    SchoolDay.TUESDAY, SchoolDay.WEDNESDAY, SchoolDay.THURSDAY, SchoolDay.FRIDAY, SchoolDay.SATURDAY -> defaultMorningPeriodTimes()
}

fun defaultDayPeriodTimes(): Map<SchoolDay, List<PeriodTime>> =
    SchoolDay.entries.associateWith { defaultPeriodTimesForDay(it) }

fun generatePeriodTimes(
    startHour: Int,
    startMinute: Int,
    periodDurationMin: Int,
    breakDurationMin: Int,
    count: Int = 6
): List<PeriodTime> {
    var cursorMinutes = startHour * 60 + startMinute
    return (1..count).map { p ->
        val sH = (cursorMinutes / 60) % 24
        val sM = cursorMinutes % 60
        val endTotal = cursorMinutes + periodDurationMin
        val eH = (endTotal / 60) % 24
        val eM = endTotal % 60
        cursorMinutes = endTotal + breakDurationMin
        PeriodTime(p, sH, sM, eH, eM)
    }
}

fun classKey(grade: GradeLevel, section: SectionLetter): String = "${grade.id}_${section.id}"

fun dailyCellKey(columnId: String, studentId: String): String = "${columnId}__${studentId}"

fun defaultDailyColumns(): List<DailyColumnDef> = buildList {
    val ledger = LedgerSubject.GENERAL
    DailyPeriodSlot.entries.forEach { slot ->
        add(DailyColumnDef("dc_${ledger.id}_${slot.id}_prep", ledger, slot, "التحضير اليومي", 25.0))
        add(DailyColumnDef("dc_${ledger.id}_${slot.id}_part", ledger, slot, "المشاركة الصفية", 25.0))
        add(DailyColumnDef("dc_${ledger.id}_${slot.id}_hw", ledger, slot, "الواجب والدفتر", 25.0))
        add(DailyColumnDef("dc_${ledger.id}_${slot.id}_quiz", ledger, slot, "الاختبار اليومي", 25.0))
    }
}

fun defaultQuestionSections(): List<QuestionSectionDef> = listOf(
    QuestionSectionDef("sec_define", "التعاريف (عرّف)", 0xFF00D2D3, isBuiltIn = true),
    QuestionSectionDef("sec_blanks", "املأ الفراغات", 0xFFFF9F43, isBuiltIn = true),
    QuestionSectionDef("sec_reason", "التعاليل (علّل)", 0xFFFF6B81, isBuiltIn = true),
    QuestionSectionDef("sec_tf", "صح وخطأ", 0xFF2ED573, isBuiltIn = true),
    QuestionSectionDef("sec_explain", "اشرح / وضّح", 0xFF54A0FF, isBuiltIn = true),
    QuestionSectionDef("sec_list", "عدّد", 0xFFA55EEA, isBuiltIn = true),
    QuestionSectionDef("sec_draw", "ارسم / مخطط", 0xFF1ABC9C, isBuiltIn = true)
)

fun defaultQuestionChapters(): List<QuestionChapter> = listOf(
    QuestionChapter("qch_1", 1, "الفصل الأول", 25),
    QuestionChapter("qch_2", 2, "الفصل الثاني", 25),
    QuestionChapter("qch_3", 3, "الفصل الثالث", 25),
    QuestionChapter("qch_4", 4, "الفصل الرابع", 25)
)

fun defaultExamSlots(): List<ExamSlotConfig> = listOf(
    ExamSlotConfig("slot_1", 1, "sec_define", "", 20, 4),
    ExamSlotConfig("slot_2", 2, "sec_blanks", "", 15, 5),
    ExamSlotConfig("slot_3", 3, "sec_tf", "", 15, 5),
    ExamSlotConfig("slot_4", 4, "sec_reason", "", 15, 3),
    ExamSlotConfig("slot_5", 5, "sec_list", "", 15, 3),
    ExamSlotConfig("slot_6", 6, "sec_explain", "ارسم أو اشرح", 20, 2)
)
