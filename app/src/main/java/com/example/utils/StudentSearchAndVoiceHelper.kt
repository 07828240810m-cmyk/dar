package com.example.utils

import com.example.data.RemoteControlConfig
import com.example.model.BehaviorStatus
import com.example.model.GradeLevel
import com.example.model.GradebookSettings
import com.example.model.SectionLetter
import com.example.model.Student
import com.example.model.TeacherProfile
import com.example.ui.theme.StudentColorTag

enum class StudentNameMatchMode(val titleAr: String) {
    FULL_NAME("الاسم الكامل"),
    FIRST_NAME_ONLY("اسم الطالب فقط")
}

enum class ScoreRangeFilter(val titleAr: String) {
    ALL("كل الدرجات"),
    EXCELLENT_90_PLUS("90 فأعلى"),
    VERY_GOOD_75_89("75 - 89"),
    PASS_50_74("50 - 74"),
    FAIL_BELOW_50("أقل من 50"),
    CUSTOM("نطاق مخصص")
}

enum class AcademicStatusFilter(val titleAr: String) {
    ALL("كل الحالات"),
    PASSING("ناجح"),
    FAILING("راسب / مكمل"),
    EXEMPT("معفو (90+)"),
    MINISTERIAL("مؤهل وزاري"),
    INTISAB("انتساب"),
    TRANSFERRED("منقول")
}

enum class BehaviorSearchFilter(val titleAr: String) {
    ALL("كل السلوكيات"),
    EXEMPLARY("متميز"),
    GOOD("منضبط"),
    NEEDS_ATTENTION("يحتاج متابعة"),
    BAD("سلوك سيء")
}

enum class SectionScopeFilter(val titleAr: String) {
    CURRENT_SECTION("الشعبة الحالية"),
    ALL_SECTIONS_IN_GRADE("كل شعب المرحلة"),
    ALL_SCHOOL("كل المدرسة")
}

data class AdvancedStudentSearchFilter(
    val query: String = "",
    val nameMatchMode: StudentNameMatchMode = StudentNameMatchMode.FULL_NAME,
    val scoreRange: ScoreRangeFilter = ScoreRangeFilter.ALL,
    val minScore: Double? = null,
    val maxScore: Double? = null,
    val sectionScope: SectionScopeFilter = SectionScopeFilter.CURRENT_SECTION,
    val specificSection: SectionLetter? = null,
    val behaviorFilter: BehaviorSearchFilter = BehaviorSearchFilter.ALL,
    val colorTagFilter: StudentColorTag? = null,
    val academicStatus: AcademicStatusFilter = AcademicStatusFilter.ALL
) {
    val hasActiveStructuredFilters: Boolean
        get() = nameMatchMode != StudentNameMatchMode.FULL_NAME ||
            scoreRange != ScoreRangeFilter.ALL ||
            specificSection != null ||
            sectionScope != SectionScopeFilter.CURRENT_SECTION ||
            behaviorFilter != BehaviorSearchFilter.ALL ||
            colorTagFilter != null ||
            academicStatus != AcademicStatusFilter.ALL
}

object StudentSearchAndVoiceHelper {

    fun normalizeArabic(text: String): String {
        val noHarakat = text.replace(Regex("[\u064B-\u065F\u0670\u06D6-\u06ED]"), "")
        return noHarakat
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace("ـ", "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }

    fun firstNameToken(fullName: String): String {
        val normalized = normalizeArabic(fullName)
        if (normalized.isEmpty()) return ""
        return normalized.split(' ').firstOrNull().orEmpty()
    }

    fun matchStudentName(
        fullName: String,
        query: String,
        mode: StudentNameMatchMode
    ): Boolean {
        val nq = normalizeArabic(query)
        if (nq.isEmpty()) return true
        return when (mode) {
            StudentNameMatchMode.FULL_NAME -> normalizeArabic(fullName).contains(nq)
            StudentNameMatchMode.FIRST_NAME_ONLY -> {
                val firstToken = firstNameToken(fullName)
                val queryFirstToken = nq.split(' ').firstOrNull().orEmpty()
                queryFirstToken.isNotEmpty() && firstToken.contains(queryFirstToken)
            }
        }
    }

    fun effectiveStudentScore(
        student: Student,
        settings: GradebookSettings,
        ledgerId: String
    ): Double? {
        val res = GradeCalculator.computeForLedger(student, settings, ledgerId)
        return res.finalGrade ?: res.annualEffort ?: res.midYearEffective ?: res.term1Average ?: res.term2Average
    }

    fun filterStudentsAdvanced(
        students: List<Student>,
        filter: AdvancedStudentSearchFilter,
        settings: GradebookSettings,
        ledgerId: String,
        currentGrade: GradeLevel? = null,
        currentSection: SectionLetter? = null
    ): List<Student> {
        return students.filter { st ->
            // Scope filter
            val scopeOk = when {
                filter.specificSection != null -> {
                    val gradeMatch = currentGrade == null || st.gradeLevel == currentGrade ||
                        filter.sectionScope == SectionScopeFilter.ALL_SCHOOL
                    gradeMatch && st.section == filter.specificSection
                }
                filter.sectionScope == SectionScopeFilter.CURRENT_SECTION -> {
                    (currentGrade == null || st.gradeLevel == currentGrade) &&
                        (currentSection == null || st.section == currentSection)
                }
                filter.sectionScope == SectionScopeFilter.ALL_SECTIONS_IN_GRADE -> {
                    currentGrade == null || st.gradeLevel == currentGrade
                }
                else -> true
            }
            if (!scopeOk) return@filter false

            // Transferred default visibility vs explicit status
            if (filter.academicStatus == AcademicStatusFilter.TRANSFERRED) {
                if (!st.isTransferred) return@filter false
            } else if (settings.hideTransferredStudents && st.isTransferred) {
                return@filter false
            }

            // Name match
            if (!matchStudentName(st.fullName, filter.query, filter.nameMatchMode)) {
                return@filter false
            }

            // Behavior filter
            val behaviorOk = when (filter.behaviorFilter) {
                BehaviorSearchFilter.ALL -> true
                BehaviorSearchFilter.EXEMPLARY -> st.behaviorStatus == BehaviorStatus.EXEMPLARY
                BehaviorSearchFilter.GOOD -> st.behaviorStatus == BehaviorStatus.GOOD
                BehaviorSearchFilter.NEEDS_ATTENTION -> st.behaviorStatus == BehaviorStatus.NEEDS_ATTENTION
                BehaviorSearchFilter.BAD -> st.behaviorStatus == BehaviorStatus.BAD
            }
            if (!behaviorOk) return@filter false

            // Color tag
            if (filter.colorTagFilter != null && st.colorTag != filter.colorTagFilter) {
                return@filter false
            }

            // Academic status & score range
            val computed = GradeCalculator.computeForLedger(st, settings, ledgerId)
            val statusOk = when (filter.academicStatus) {
                AcademicStatusFilter.ALL -> true
                AcademicStatusFilter.PASSING -> computed.isPassing == true
                AcademicStatusFilter.FAILING -> computed.isPassing == false
                AcademicStatusFilter.EXEMPT -> GradeCalculator.isExemptEligible(st, computed, settings)
                AcademicStatusFilter.MINISTERIAL -> GradeCalculator.isMinisterialQualified(st, computed)
                AcademicStatusFilter.INTISAB -> normalizeArabic(st.notes).contains("انتساب")
                AcademicStatusFilter.TRANSFERRED -> st.isTransferred
            }
            if (!statusOk) return@filter false

            val score = computed.finalGrade ?: computed.annualEffort ?: computed.midYearEffective
                ?: computed.term1Average ?: computed.term2Average
            val scoreOk = when (filter.scoreRange) {
                ScoreRangeFilter.ALL -> true
                ScoreRangeFilter.EXCELLENT_90_PLUS -> score != null && score >= 90.0
                ScoreRangeFilter.VERY_GOOD_75_89 -> score != null && score >= 75.0 && score < 90.0
                ScoreRangeFilter.PASS_50_74 -> score != null && score >= 50.0 && score < 75.0
                ScoreRangeFilter.FAIL_BELOW_50 -> score != null && score < 50.0
                ScoreRangeFilter.CUSTOM -> {
                    if (filter.minScore == null && filter.maxScore == null) true
                    else if (score == null) false
                    else {
                        val minOk = filter.minScore == null || score >= filter.minScore
                        val maxOk = filter.maxScore == null || score <= filter.maxScore
                        minOk && maxOk
                    }
                }
            }
            scoreOk
        }
    }
}

object PdfValidationHelper {
    const val MAX_SHARED_PDF_BYTES = 5 * 1024 * 1024 // 5 MB

    fun isValidPdfBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 5) return false
        return bytes[0] == 0x25.toByte() && // %
            bytes[1] == 0x50.toByte() && // P
            bytes[2] == 0x44.toByte() && // D
            bytes[3] == 0x46.toByte() && // F
            bytes[4] == 0x2D.toByte()    // -
    }

    fun isAllowedFileSize(sizeBytes: Long): Boolean = sizeBytes in 5L..MAX_SHARED_PDF_BYTES.toLong()
}

enum class ExperimentalLayoutMode(
    val id: String,
    val titleAr: String,
    val descriptionAr: String
) {
    CLASSIC(
        "CLASSIC",
        "التصميم الافتراضي",
        "الواجهة الأصلية القياسية بشبكة المربعات الثلاثية."
    ),
    MODERN_SPLIT(
        "MODERN_SPLIT",
        "هيكلة حديثة (بطاقات أفقية مزدوجة)",
        "تقسيم الأدوات إلى شريط سريع علوي + بطاقات عريضة ثنائية العمود بتفاصيل أوضح."
    ),
    COMPACT_CARDS(
        "COMPACT_CARDS",
        "هيكلة مضغوطة سريعة (شبكة رباعية)",
        "عرض مضغوط يجمع أكبر عدد من الأدوات بشبكة سريعة للوصول الفوري."
    ),
    FOCUS_STUDIO(
        "FOCUS_STUDIO",
        "استوديو المدرس (تركيز على الصف والحصة)",
        "يبرز الحصة الحالية وسجل الدرجات والبحث الذكي في الأعلى مع تصنيف الأدوات."
    ),
    SPECIALIST_PRO(
        "SPECIALIST_PRO",
        "التصميم المبتكر المتخصص",
        "لوحة قيادة ذكية: شريط المفضلة + البحث الفوري بالدرجات + تبويب الأرشفة وتصنيف احترافي للأقسام."
    );

    companion object {
        fun fromId(raw: String?): ExperimentalLayoutMode {
            return entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: CLASSIC
        }
    }
}

fun resolveEffectiveLayoutMode(
    remoteControl: RemoteControlConfig,
    profile: TeacherProfile,
    isAdmin: Boolean
): ExperimentalLayoutMode {
    if (profile.preferDefaultDesign) {
        return ExperimentalLayoutMode.CLASSIC
    }
    if (isAdmin) {
        val localMode = ExperimentalLayoutMode.fromId(profile.adminLocalExperimentalMode)
        if (localMode != ExperimentalLayoutMode.CLASSIC) return localMode
    }
    if (remoteControl.experimentalLayoutEnabled) {
        val remoteMode = ExperimentalLayoutMode.fromId(remoteControl.experimentalLayoutMode)
        if (remoteMode != ExperimentalLayoutMode.CLASSIC) return remoteMode
    }
    return ExperimentalLayoutMode.CLASSIC
}

enum class VoiceNavTarget {
    HOME,
    TIMETABLE,
    GRADEBOOK,
    ANALYTICS,
    SETTINGS,
    STUDY_GUIDES,
    DAILY_LEDGER,
    QUESTION_BANK,
    CERTIFICATES_AND_QR,
    LIBRARY_BOOKS,
    TASKS_AND_CHAPTERS,
    QUICK_NOTES_TIMER,
    QURAN_ATHKAR_PRAYER,
    STUDENT_PAPERS,
    EXAM_ARCHIVE,
    SECTION_STARS,
    VAULT_EXTRACTOR,
    TEACHER_PROFILE,
    EXAM_WRITER
}

sealed interface VoiceCommandResolution {
    data class Navigate(val target: VoiceNavTarget, val matchedPhrase: String) : VoiceCommandResolution
    data class SearchStudent(val query: String) : VoiceCommandResolution
    data object Empty : VoiceCommandResolution
}

object VoiceCommandRouter {
    fun resolveVoiceCommand(spokenText: String): VoiceCommandResolution {
        val cleaned = spokenText.trim()
        if (cleaned.isEmpty()) return VoiceCommandResolution.Empty
        val norm = StudentSearchAndVoiceHelper.normalizeArabic(cleaned)
        if (norm.isEmpty()) return VoiceCommandResolution.Empty

        val navRules: List<Pair<List<String>, VoiceNavTarget>> = listOf(
            listOf("كتابه الاسئله", "صانع الاسئله", "اسئله امتحان", "ورقه امتحان") to VoiceNavTarget.EXAM_WRITER,
            listOf("ملزم", "ملازم", "ملفات المشترك", "pdf") to VoiceNavTarget.STUDY_GUIDES,
            listOf("جدول الدروس", "الجدول", "حصص", "حصتي") to VoiceNavTarget.TIMETABLE,
            listOf("سجل الدرجات", "الدرجات", "درجات", "كشف") to VoiceNavTarget.GRADEBOOK,
            listOf("التحليل", "احصائيات", "نسب النجاح") to VoiceNavTarget.ANALYTICS,
            listOf("الاعدادات", "اعدادات", "الضبط", "المظهر") to VoiceNavTarget.SETTINGS,
            listOf("اليومي", "سجل اليومي", "قائمه اليومي") to VoiceNavTarget.DAILY_LEDGER,
            listOf("بنك الاسئله", "اسئله", "اختبارات") to VoiceNavTarget.QUESTION_BANK,
            listOf("الشهادات", "شهاده", "باركود", "qr") to VoiceNavTarget.CERTIFICATES_AND_QR,
            listOf("مكتبه الكتب", "المكتبه", "كتب") to VoiceNavTarget.LIBRARY_BOOKS,
            listOf("المهام", "فصول المنهج") to VoiceNavTarget.TASKS_AND_CHAPTERS,
            listOf("ملاحظات", "مؤقت", "ساعه") to VoiceNavTarget.QUICK_NOTES_TIMER,
            listOf("قران", "اذكار", "صلاه") to VoiceNavTarget.QURAN_ATHKAR_PRAYER,
            listOf("اوراق الطلب", "اوراق الاجاب") to VoiceNavTarget.STUDENT_PAPERS,
            listOf("ارشيف", "سنوات سابقه") to VoiceNavTarget.EXAM_ARCHIVE,
            listOf("كواكب الشعب", "نجوم", "متفوقين") to VoiceNavTarget.SECTION_STARS,
            listOf("حافظه سريه", "مستخرج") to VoiceNavTarget.VAULT_EXTRACTOR,
            listOf("ملف شخصي", "ملفي", "الملف الشخصي") to VoiceNavTarget.TEACHER_PROFILE,
            listOf("الرئيسيه", "الصفحه الرئيسيه") to VoiceNavTarget.HOME
        )

        val isExplicitSearch = norm.startsWith("ابحث عن ") || norm.startsWith("بحث عن ") || norm.startsWith("طالب ")
        if (!isExplicitSearch) {
            for ((keywords, target) in navRules) {
                if (keywords.any { kw -> norm.contains(StudentSearchAndVoiceHelper.normalizeArabic(kw)) }) {
                    return VoiceCommandResolution.Navigate(target, cleaned)
                }
            }
        }

        val strippedQuery = cleaned
            .removePrefix("ابحث عن ")
            .removePrefix("بحث عن ")
            .removePrefix("الطالب ")
            .removePrefix("طالب ")
            .trim()
        return if (strippedQuery.isNotEmpty()) {
            VoiceCommandResolution.SearchStudent(strippedQuery)
        } else {
            VoiceCommandResolution.Empty
        }
    }
}
