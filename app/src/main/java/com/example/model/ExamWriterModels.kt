package com.example.model

import com.example.data.OfflineQuranData
import com.example.data.QuranAyah
import com.example.data.QuranSurah
import com.example.ui.theme.AppFontOption
import java.util.UUID

enum class ExamQuestionType(val titleAr: String) {
    FILL_BLANKS("فراغات"),
    TRUE_FALSE("صح وخطأ"),
    DEFINITIONS("تعاريف"),
    REASONS("تعاليل"),
    PROBLEMS("مسائل"),
    EXPLANATION("شرح"),
    QA("سؤال وجواب")
}

enum class ExamTextAlign(val titleAr: String) {
    RIGHT("يمين"),
    CENTER("وسط"),
    LEFT("يسار")
}

enum class ExamPaperOrientation(val titleAr: String) {
    PORTRAIT("عمودي"),
    LANDSCAPE("أفقي")
}

enum class ExamPaperSize(val titleAr: String, val widthPt: Int, val heightPt: Int) {
    A4("A4 قياسي", 595, 842),
    A5("A5 مصغر", 420, 595),
    LETTER("Letter", 612, 792)
}

enum class ExamFrameStyle(val titleAr: String) {
    NONE("بدون إطار"),
    CLASSIC_DOUBLE("إطار مزدوج كلاسيكي"),
    ROYAL_ORNAMENT("إطار مزخرف ذهبي"),
    MODERN_MINIMAL("إطار هندسي حديث"),
    CUSTOM_PNG("إطار صورة مخصص (PNG)")
}

data class ExamTypographyConfig(
    val fontOption: AppFontOption = AppFontOption.ALMARAI,
    val baseFontSizeSp: Float = 14f,
    val headingFontSizeSp: Float = 18f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderlined: Boolean = false,
    val alignment: ExamTextAlign = ExamTextAlign.RIGHT,
    val lineHeightMultiplier: Float = 1.45f,
    val wordSpacingSp: Float = 0f,
    val paragraphSpacingDp: Float = 8f,
    val marginTopDp: Float = 20f,
    val marginBottomDp: Float = 20f,
    val marginHorizontalDp: Float = 18f
)

data class ExamBranchItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "أ",
    val prompt: String = "",
    val marks: Int = 5,
    val textColorHex: Long = 0xFF1A1A1AL,
    val bgColorHex: Long = 0x00000000L
)

data class ExamQuestionBox(
    val id: String = UUID.randomUUID().toString(),
    val headingAbove: String = "",
    val questionTitle: String = "س١/",
    val questionType: ExamQuestionType = ExamQuestionType.QA,
    val promptText: String = "",
    val marks: Int = 10,
    val boxColorHex: Long = 0xFF1565C0L,
    val textColorHex: Long = 0xFF111827L,
    val headingColorHex: Long = 0xFF0F766EL,
    val showDivider: Boolean = true,
    val branches: List<ExamBranchItem> = emptyList(),
    val offsetXFraction: Float = 0f,
    val offsetYFraction: Float = 0f
)

data class ExamPageData(
    val id: String = UUID.randomUUID().toString(),
    val pageTitle: String = "الصفحة 1",
    val questions: List<ExamQuestionBox> = emptyList(),
    val footerNote: String = "مع تمنياتنا لكم بالنجاح والتوفيق"
)

data class ExamPaperDocument(
    val id: String = UUID.randomUUID().toString(),
    val docTitle: String = "أسئلة الامتحان الشهري",
    val ministryHeader: String = "إدارة المدرسة / قسم الامتحانات",
    val schoolName: String = "مدرستي",
    val subjectName: String = "التربية الإسلامية",
    val gradeAndSection: String = "الأول المتوسط",
    val examDuration: String = "ساعة واحدة",
    val academicYear: String = "2026 - 2027",
    val totalMarksNote: String = "ملاحظة: أجب عن جميع الأسئلة",
    val orientation: ExamPaperOrientation = ExamPaperOrientation.PORTRAIT,
    val paperSize: ExamPaperSize = ExamPaperSize.A4,
    val frameStyle: ExamFrameStyle = ExamFrameStyle.CLASSIC_DOUBLE,
    val customFrameFileName: String? = null,
    val showCoverPage: Boolean = false,
    val showTableOfContents: Boolean = false,
    val showPageNumbers: Boolean = true,
    val freeManualDragMode: Boolean = false,
    val typography: ExamTypographyConfig = ExamTypographyConfig(),
    val pages: List<ExamPageData> = listOf(defaultInitialPage()),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

fun defaultInitialPage(): ExamPageData = ExamPageData(
    pageTitle = "الصفحة 1",
    questions = listOf(
        ExamQuestionBox(
            headingAbove = "أولاً: القرآن الكريم والحفظ",
            questionTitle = "س١/",
            questionType = ExamQuestionType.QA,
            promptText = "اكتب ما تحفظه من الآيات الكريمة مع بيان المعاني:",
            marks = 15,
            boxColorHex = 0xFF0F766EL,
            headingColorHex = 0xFF0F766EL,
            branches = listOf(
                ExamBranchItem(label = "أ", prompt = "أكمل قوله تعالى: ﴿ بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ﴾", marks = 10),
                ExamBranchItem(label = "ب", prompt = "ما معنى المفردات الآتية؟", marks = 5)
            )
        ),
        ExamQuestionBox(
            headingAbove = "ثانياً: المفاهيم والتعاليل",
            questionTitle = "س٢/",
            questionType = ExamQuestionType.FILL_BLANKS,
            promptText = "املأ الفراغات الآتية بما يناسبها:",
            marks = 10,
            boxColorHex = 0xFF1565C0L,
            headingColorHex = 0xFF1565C0L,
            branches = listOf(
                ExamBranchItem(label = "1", prompt = "................ هو الركن الأساس في الدرس.", marks = 5),
                ExamBranchItem(label = "2", prompt = "تُعرَّف القاعدة العلمية بأنها ................", marks = 5)
            )
        )
    )
)

data class ExamTemplatePreset(
    val id: String,
    val titleAr: String,
    val subtitleAr: String,
    val builder: (schoolName: String, subjectName: String, gradeName: String) -> ExamPaperDocument
)

object ExamPaperTemplates {
    val presets: List<ExamTemplatePreset> = listOf(
        ExamTemplatePreset(
            id = "islamic_standard",
            titleAr = "قالب الإسلامية (حفظ + أحكام + سيرة)",
            subtitleAr = "يتضمن عناوين الحفظ والتلاوة والأحكام والسيرة النبوية مع أفرع متداخلة",
            builder = { school, subj, grade ->
                ExamPaperDocument(
                    docTitle = "أسئلة امتحان $subj",
                    schoolName = school.ifBlank { "مدرستي" },
                    subjectName = subj.ifBlank { "التربية الإسلامية" },
                    gradeAndSection = grade,
                    frameStyle = ExamFrameStyle.ROYAL_ORNAMENT,
                    pages = listOf(
                        ExamPageData(
                            pageTitle = "الصفحة 1",
                            questions = listOf(
                                ExamQuestionBox(
                                    headingAbove = "الحفظ والتلاوة",
                                    questionTitle = "س١/",
                                    questionType = ExamQuestionType.QA,
                                    promptText = "اكتب من سورة الفاتحة ما تيسّر مع الضبط التام:",
                                    marks = 15,
                                    boxColorHex = 0xFF0F766EL,
                                    headingColorHex = 0xFF0F766EL
                                ),
                                ExamQuestionBox(
                                    headingAbove = "الأحكام والتفسير",
                                    questionTitle = "س٢/",
                                    questionType = ExamQuestionType.DEFINITIONS,
                                    promptText = "عرّف ما يأتي مع ذكر مثال لكل منها:",
                                    marks = 10,
                                    boxColorHex = 0xFF1D4ED8L,
                                    headingColorHex = 0xFF1D4ED8L,
                                    branches = listOf(
                                        ExamBranchItem(label = "أ", prompt = "المد المتصل", marks = 5),
                                        ExamBranchItem(label = "ب", prompt = "القلقلة", marks = 5)
                                    )
                                ),
                                ExamQuestionBox(
                                    headingAbove = "السيرة النبوية الشريفة",
                                    questionTitle = "س٣/",
                                    questionType = ExamQuestionType.EXPLANATION,
                                    promptText = "تحدّث بإيجاز عن أهم الدروس المستفادة من الهجرة النبوية.",
                                    marks = 15,
                                    boxColorHex = 0xFF7C3AEDL,
                                    headingColorHex = 0xFF7C3AEDL
                                )
                            )
                        )
                    )
                )
            }
        ),
        ExamTemplatePreset(
            id = "scientific_problem",
            titleAr = "قالب المواد العلمية (تعاليل + مسائل + صح وخطأ)",
            subtitleAr = "مثالي للفيزياء والكيمياء والرياضيات والأحياء",
            builder = { school, subj, grade ->
                ExamPaperDocument(
                    docTitle = "اختبار الشهر الأول - $subj",
                    schoolName = school.ifBlank { "مدرستي" },
                    subjectName = subj.ifBlank { "العلوم / الرياضيات" },
                    gradeAndSection = grade,
                    frameStyle = ExamFrameStyle.MODERN_MINIMAL,
                    pages = listOf(
                        ExamPageData(
                            pageTitle = "الصفحة 1",
                            questions = listOf(
                                ExamQuestionBox(
                                    headingAbove = "القسم الأول: المفاهيم والتعاليل",
                                    questionTitle = "س١/",
                                    questionType = ExamQuestionType.REASONS,
                                    promptText = "علّل ما يأتي تعليلاً علمياً دقيقاً:",
                                    marks = 12,
                                    boxColorHex = 0xFF0284C7L,
                                    headingColorHex = 0xFF0284C7L,
                                    branches = listOf(
                                        ExamBranchItem(label = "أ", prompt = "التعليل الأول...", marks = 6),
                                        ExamBranchItem(label = "ب", prompt = "التعليل الثاني...", marks = 6)
                                    )
                                ),
                                ExamQuestionBox(
                                    headingAbove = "القسم الثاني: المسائل الرياضية",
                                    questionTitle = "س٢/",
                                    questionType = ExamQuestionType.PROBLEMS,
                                    promptText = "احسب المقدار المطلوب موضحاً خطوات الحل والقانون:",
                                    marks = 18,
                                    boxColorHex = 0xFF059669L,
                                    headingColorHex = 0xFF059669L
                                ),
                                ExamQuestionBox(
                                    headingAbove = "القسم الثالث: الموضوعي",
                                    questionTitle = "س٣/",
                                    questionType = ExamQuestionType.TRUE_FALSE,
                                    promptText = "ضع علامة (✓) أمام العبارة الصحيحة وعلامة (✗) أمام العبارة الخاطئة:",
                                    marks = 10,
                                    boxColorHex = 0xFFD97706L,
                                    headingColorHex = 0xFFD97706L
                                )
                            )
                        )
                    )
                )
            }
        ),
        ExamTemplatePreset(
            id = "blank_clean",
            titleAr = "ورقة فارغة جاهزة للتصميم",
            subtitleAr = "نموذج منظم بسؤال واحد قابل للتعديل والإضافة بحرية",
            builder = { school, subj, grade ->
                ExamPaperDocument(
                    docTitle = "نموذج أسئلة جديد",
                    schoolName = school.ifBlank { "مدرستي" },
                    subjectName = subj.ifBlank { "المادة" },
                    gradeAndSection = grade,
                    frameStyle = ExamFrameStyle.CLASSIC_DOUBLE,
                    pages = listOf(
                        ExamPageData(
                            pageTitle = "الصفحة 1",
                            questions = listOf(
                                ExamQuestionBox(
                                    headingAbove = "",
                                    questionTitle = "س١/",
                                    questionType = ExamQuestionType.QA,
                                    promptText = "اكتب نص السؤال هنا...",
                                    marks = 10
                                )
                            )
                        )
                    )
                )
            }
        )
    )
}

object QuranExamHelper {
    fun allSurahs(): List<QuranSurah> = OfflineQuranData.surahs

    fun searchSurahs(query: String): List<QuranSurah> {
        val clean = OfflineQuranData.stripArabicTashkeel(query).trim()
        if (clean.isEmpty()) return allSurahs()
        return allSurahs().filter {
            OfflineQuranData.stripArabicTashkeel(it.nameArabic).contains(clean, ignoreCase = true) ||
                it.id.toString() == clean
        }
    }

    fun formatAyahRange(
        surah: QuranSurah,
        fromAyah: Int,
        toAyah: Int,
        withTashkeel: Boolean,
        includeSurahReference: Boolean = true
    ): String {
        val start = minOf(fromAyah, toAyah).coerceAtLeast(1)
        val end = maxOf(fromAyah, toAyah).coerceAtMost(surah.ayahs.size)
        val selected: List<QuranAyah> = surah.ayahs.filter { it.numberInSurah in start..end }
        if (selected.isEmpty()) return ""
        val joined = selected.joinToString(" ") { ayah ->
            val raw = if (withTashkeel) ayah.textUthmani else OfflineQuranData.stripArabicTashkeel(ayah.textUthmani)
            "$raw (${ayah.numberInSurah})"
        }
        return if (includeSurahReference) {
            "﴿ $joined ﴾ [سورة ${surah.nameArabic}]"
        } else {
            "﴿ $joined ﴾"
        }
    }
}

object ExamVoiceParser {
    private val arabicOrderWords = listOf(
        "الاول", "الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس"
    )

    fun parseSpokenArabicToExamQuestion(
        spokenText: String,
        questionIndex: Int
    ): ExamQuestionBox {
        val clean = spokenText.trim()
        val norm = clean
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ة', 'ه')

        val detectedType = when {
            norm.contains("فراغ") -> ExamQuestionType.FILL_BLANKS
            norm.contains("صح") && norm.contains("خط") -> ExamQuestionType.TRUE_FALSE
            norm.contains("عرف") || norm.contains("تعاريف") -> ExamQuestionType.DEFINITIONS
            norm.contains("علل") || norm.contains("تعاليل") -> ExamQuestionType.REASONS
            norm.contains("مسال") || norm.contains("احسب") -> ExamQuestionType.PROBLEMS
            norm.contains("اشرح") || norm.contains("شرح") -> ExamQuestionType.EXPLANATION
            else -> ExamQuestionType.QA
        }

        val marksMatch = Regex("(\\d+)\\s*(درج|علام)").find(norm)
        val marks = marksMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 100) ?: 10

        var body = clean
        for (ord in arabicOrderWords) {
            body = body.replace(Regex("^(السؤال|سؤال)\\s+$ord\\s*[:/\\-]?\\s*"), "")
        }
        body = body.replace(Regex("(عليه|بـ|بمقدار)?\\s*\\d+\\s*(درجات|درجة|علامات|علامة)"), "").trim()

        // Split branches if user speaks "فرع أ ... فرع ب ..."
        val branchRegex = Regex("(?:فرع|أولا|ثانيا)\\s*([أبجدهـ12345])[:\\s]+")
        val matches = branchRegex.findAll(body).toList()
        val branches = mutableListOf<ExamBranchItem>()
        val mainPrompt: String
        if (matches.isNotEmpty()) {
            mainPrompt = body.substring(0, matches.first().range.first).trim()
                .ifBlank { "أجب عن الأفرع الآتية:" }
            matches.forEachIndexed { idx, m ->
                val startIdx = m.range.last + 1
                val endIdx = if (idx + 1 < matches.size) matches[idx + 1].range.first else body.length
                val bText = body.substring(startIdx, endIdx).trim()
                val label = m.groupValues.getOrNull(1).orEmpty().ifBlank { "${idx + 1}" }
                if (bText.isNotBlank()) {
                    branches += ExamBranchItem(
                        label = label,
                        prompt = bText,
                        marks = (marks / matches.size.coerceAtLeast(1)).coerceAtLeast(1)
                    )
                }
            }
        } else {
            mainPrompt = body.ifBlank { clean }
        }

        return ExamQuestionBox(
            questionTitle = "س${questionIndex + 1}/",
            questionType = detectedType,
            promptText = mainPrompt,
            marks = marks,
            branches = branches
        )
    }
}
