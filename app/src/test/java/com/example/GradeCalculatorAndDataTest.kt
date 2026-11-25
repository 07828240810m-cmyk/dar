package com.example

import com.example.data.InitialStudentsData
import com.example.data.InitialTimetableData
import com.example.data.OfflineQuranData
import com.example.model.*
import com.example.ui.components.visualSpec
import com.example.ui.theme.paletteSpec
import com.example.utils.ExamGeneratorHelper
import com.example.utils.GradeCalculator
import com.example.utils.LessonAlarmScheduler
import com.example.utils.LessonBoundaryType
import com.example.utils.OcrAndImportHelper
import com.example.utils.OfflinePrayerCalculator
import com.example.utils.TimelineSourceMode
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class GradeCalculatorAndDataTest {

    @Test
    fun verifyPreloadedStudentsAndTimetableCounts() {
        val students = InitialStudentsData.buildAllStudents()
        // Zero hardcoded preloaded student names on fresh launch
        assertTrue(students.isEmpty())

        // Legacy seeded students cleanup strips ungraded legacy IDs while preserving custom/graded students
        val legacyEmpty = Student(
            id = "3_أ_1",
            orderNumber = 1,
            fullName = "طالب قديم بدون درجات",
            gradeLevel = GradeLevel.THIRD,
            section = SectionLetter.A
        )
        val customAdded = Student(
            id = "custom_1",
            orderNumber = 2,
            fullName = "سارة علي محمد",
            gradeLevel = GradeLevel.THIRD,
            section = SectionLetter.A
        )
        val legacyGraded = Student(
            id = "3_أ_2",
            orderNumber = 3,
            fullName = "محمد حسن",
            gradeLevel = GradeLevel.THIRD,
            section = SectionLetter.A,
            grades = StudentGrades(term1Month1 = 85.0)
        )
        val cleanedStudents = InitialStudentsData.stripLegacySeededStudents(listOf(legacyEmpty, customAdded, legacyGraded))
        assertEquals(2, cleanedStudents.size)
        assertTrue(cleanedStudents.any { it.id == "custom_1" })
        assertTrue(cleanedStudents.any { it.id == "3_أ_2" })

        val timetable = InitialTimetableData.buildDefaultTimetable()
        // Zero hardcoded third-party teachers or pre-filled teacher timetable entries
        assertTrue(timetable.isEmpty())
    }

    @Test
    fun verifySmartRoundingAndIraqiGradeCalculations() {
        assertEquals(55.0, GradeCalculator.applyRounding(55.3, true)!!, 0.001)
        assertEquals(56.0, GradeCalculator.applyRounding(55.5, true)!!, 0.001)
        assertEquals(56.0, GradeCalculator.applyRounding(55.6, true)!!, 0.001)

        val student = Student(
            id = "s1",
            orderNumber = 1,
            fullName = "طالب تجريبي",
            gradeLevel = GradeLevel.THIRD,
            section = SectionLetter.A,
            grades = StudentGrades(
                term1Month1 = 80.0,
                term1Month2 = 90.0,
                term1Daily = 100.0,
                midYearTotal = 85.0,
                term2Month1 = 70.0,
                term2Month2 = 90.0,
                term2Daily = 100.0,
                finalExam = 90.0
            )
        )

        val neverRes = GradeCalculator.compute(student, GradebookSettings(dailyCalcMode = DailyCalcMode.NEVER, enableSmartRounding = true))
        assertEquals(85.0, neverRes.term1Average!!, 0.001)
        assertEquals(80.0, neverRes.term2Average!!, 0.001)
        assertEquals(83.0, neverRes.annualEffort!!, 0.001)
        assertEquals(87.0, neverRes.finalGrade!!, 0.001)

        val onceRes = GradeCalculator.compute(student, GradebookSettings(dailyCalcMode = DailyCalcMode.ONCE_WITH_TWO_MONTHS, enableSmartRounding = true))
        assertEquals(90.0, onceRes.term1Average!!, 0.001)

        val perMonthRes = GradeCalculator.compute(student, GradebookSettings(dailyCalcMode = DailyCalcMode.WITH_EACH_MONTH, enableSmartRounding = true))
        assertEquals(93.0, perMonthRes.term1Average!!, 0.001)
    }

    @Test
    fun verifyDayShiftBellSchedulesAndOneMinuteStartEndAlerts() {
        val afternoon = defaultPeriodTimesForDay(SchoolDay.SUNDAY)
        assertEquals(5, afternoon.size)
        assertEquals(12, afternoon[0].startHour)
        assertEquals(0, afternoon[0].startMinute)
        assertEquals(12, afternoon[0].endHour)
        assertEquals(40, afternoon[0].endMinute)
        // Break between period 2 (13:25) and period 3 (13:35) is 10 minutes
        assertEquals(13, afternoon[2].startHour)
        assertEquals(35, afternoon[2].startMinute)
        assertEquals(15, afternoon[4].endHour)
        assertEquals(45, afternoon[4].endMinute)

        val morning = defaultPeriodTimesForDay(SchoolDay.TUESDAY)
        assertEquals(6, morning.size)
        assertEquals(8, morning[0].startHour)
        assertEquals(0, morning[0].startMinute)
        assertEquals(11, morning[5].endHour)
        assertEquals(55, morning[5].endMinute)

        val timetable = listOf(
            TimetableCell(
                day = SchoolDay.SUNDAY,
                periodNumber = 1,
                gradeLevel = GradeLevel.FIRST,
                section = SectionLetter.A,
                subject = "الرياضيات",
                teacherName = "هدى"
            )
        )
        val dayMap = defaultDayPeriodTimes()

        // 11:59 on Sunday -> 1 minute BEFORE Period 1 start (12:00)
        val beforeStartCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 0)
        }
        val startAlert = LessonAlarmScheduler.detectDueInAppAlert(
            teacherKey = "هدى",
            timetable = timetable,
            dayPeriodTimes = dayMap,
            leadMinutes = 1,
            cal = beforeStartCal
        )
        assertNotNull(startAlert)
        assertEquals(LessonBoundaryType.BEFORE_START, startAlert!!.boundaryType)

        // 12:39 on Sunday -> 1 minute BEFORE Period 1 end (12:40)
        val beforeEndCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 39)
            set(Calendar.SECOND, 0)
        }
        val endAlert = LessonAlarmScheduler.detectDueInAppAlert(
            teacherKey = "هدى",
            timetable = timetable,
            dayPeriodTimes = dayMap,
            leadMinutes = 1,
            cal = beforeEndCal
        )
        assertNotNull(endAlert)
        assertEquals(LessonBoundaryType.BEFORE_END, endAlert!!.boundaryType)
    }

    @Test
    fun verifyGradeAndStudentNameListImporterParsing() {
        val sectionStudents = listOf(
            Student(id = "s_1", orderNumber = 1, fullName = "زينب علي محمد", gradeLevel = GradeLevel.FIRST, section = SectionLetter.A),
            Student(id = "s_2", orderNumber = 2, fullName = "حيدر جاسم كريم", gradeLevel = GradeLevel.FIRST, section = SectionLetter.A)
        )
        val parsed = OcrAndImportHelper.parseGradeCandidates(
            "زينب علي محمد, 88",
            sectionStudents
        )
        assertEquals(1, parsed.size)
        assertEquals(88.0, parsed.first().extractedScore!!, 0.001)
        assertEquals("s_1", parsed.first().matchedStudentId)

        val importedNames = OcrAndImportHelper.parseStudentNamesList(
            """
            ت	اسم الطالب	الدرجة
            1	أحمد كريم جاسم	90
            2- فاطمة علي حسن
            3. نور الهدى حسين، 85
            """.trimIndent()
        )
        assertEquals(listOf("أحمد كريم جاسم", "فاطمة علي حسن", "نور الهدى حسين"), importedNames)
    }

    @Test
    fun verifySingleSpecialtyGradebookAndDefaultIndigoPalette() {
        val student = Student(
            id = "gen_s1",
            orderNumber = 1,
            fullName = "محمد علي جاسم",
            gradeLevel = GradeLevel.FIRST,
            section = SectionLetter.A,
            grades = StudentGrades(
                term1Month1 = 80.0,
                term1Month2 = 80.0,
                midYearTotal = 80.0,
                term2Month1 = 80.0,
                term2Month2 = 80.0,
                finalExam = 80.0
            )
        )
        val settings = GradebookSettings(dailyCalcMode = DailyCalcMode.NEVER, enableSmartRounding = true)
        val genRes = GradeCalculator.compute(student, settings, LedgerSubject.GENERAL)
        assertEquals(80.0, genRes.annualEffort!!, 0.001)
        assertEquals(80.0, genRes.finalGrade!!, 0.001)

        val defaultProfile = TeacherProfile()
        assertEquals(LedgerSubject.GENERAL, defaultProfile.effectiveActiveLedger())
        assertTrue(defaultProfile.enableGeneralLedger)
        assertFalse(defaultProfile.enableMathLedger)
        assertEquals(com.example.ui.theme.ThemePalette.DESIGN_7_MIDNIGHT_NEON, defaultProfile.themePalette)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF061124), com.example.ui.theme.ThemePalette.DESIGN_7_MIDNIGHT_NEON.darkBg)
        assertEquals(com.example.ui.theme.AppTextTone.AUTO, defaultProfile.textTone)
        assertTrue(defaultProfile.neonGlowEnabled)
        assertTrue(defaultProfile.liveScheduleBannerEnabled)
        assertEquals("", defaultProfile.splashEmoji)
        assertEquals(HeroBackgroundMode.NAME_ONLY, defaultProfile.heroBgMode)
        assertEquals(HeroBackgroundSize.HIDDEN, defaultProfile.heroBgSize)
        assertTrue(defaultProfile.toolCustomImages.isEmpty())
        assertTrue(defaultProfile.toolCustomColors.isEmpty())
        assertTrue(defaultProfile.toolWhiteBackdrop.isEmpty())
        assertEquals(24, com.example.ui.theme.ThemePalette.entries.size)

        val bannerInfo = com.example.utils.LessonAlarmScheduler.computeLiveBannerInfo(
            timetable = listOf(
                TimetableCell(SchoolDay.SUNDAY, 1, GradeLevel.FIRST, SectionLetter.A, "الأحياء", "أ. محمد")
            ),
            dayPeriodTimes = defaultDayPeriodTimes(),
            teacherKey = "أ. محمد"
        )
        assertTrue(bannerInfo.nextPeriodTitle.isNotBlank())
    }

    @Test
    fun verifyZeroHardcodedTeachersAndLegacyCleanupAndSpecialtyThemes() {
        val mixedCells = listOf(
            TimetableCell(SchoolDay.SUNDAY, 1, GradeLevel.FIRST, SectionLetter.A, "انكليزي", "نجلاء"),
            TimetableCell(SchoolDay.SUNDAY, 2, GradeLevel.SECOND, SectionLetter.B, "اجتماعيات", "ابو امير"),
            TimetableCell(SchoolDay.MONDAY, 1, GradeLevel.SECOND, SectionLetter.A, "الأحياء", "الأستاذ كريم")
        )
        val cleaned = InitialTimetableData.stripLegacySeededTeachers(mixedCells, "الأستاذ كريم")
        assertEquals(1, cleaned.size)
        assertEquals("الأستاذ كريم", cleaned.first().teacherName)

        // Default specialty is Biology with Indigo-Navy palette active and full working days Sun-Thu
        val defaultProfile = TeacherProfile()
        assertEquals(SubjectSpecialty.BIOLOGY, defaultProfile.subjectSpecialty)
        assertFalse(defaultProfile.useSpecialtyTheme)
        assertEquals(5, defaultProfile.workingDays.size)

        // Every subject specialty provides distinct palette and symbol specs
        SubjectSpecialty.entries.forEach { specialty ->
            val palette = specialty.paletteSpec()
            val visuals = specialty.visualSpec()
            assertNotNull(palette.darkPrimary)
            assertNotNull(palette.lightPrimary)
            assertTrue(visuals.badgeIcons.isNotEmpty())
        }
    }

    @Test
    fun verifyDailyLedgerOutOf100AndMonthlyAverageBlending() {
        val cols = listOf(
            DailyColumnDef("c1", LedgerSubject.GENERAL, DailyPeriodSlot.T1_M1, "تحضير", 10.0),
            DailyColumnDef("c2", LedgerSubject.GENERAL, DailyPeriodSlot.T1_M1, "كويز", 15.0)
        )
        val scores = mapOf(
            dailyCellKey("c1", "s1") to 8.0,
            dailyCellKey("c2", "s1") to 12.0
        )
        // Normalized: (20 / 25) * 100 = 80.0
        val normalized = GradeCalculator.computeDailyOutOf100("s1", cols, scores, directSum = false)
        assertEquals(80.0, normalized!!, 0.001)
        // Direct sum clamped out of 100: 8 + 12 = 20.0
        val direct = GradeCalculator.computeDailyOutOf100("s1", cols, scores, directSum = true)
        assertEquals(20.0, direct!!, 0.001)

        // Monthly daily scores blend independently with Month 1 and Month 2
        val student = Student(
            id = "s1",
            orderNumber = 1,
            fullName = "طالب يومي",
            gradeLevel = GradeLevel.SECOND,
            section = SectionLetter.A,
            grades = StudentGrades(
                term1Month1 = 80.0,
                term1Month1Daily = 100.0, // Month1 blended = (80 + 100)/2 = 90
                term1Month2 = 70.0,
                term1Month2Daily = 90.0   // Month2 blended = (70 + 90)/2 = 80
            )
        )
        val res = GradeCalculator.compute(
            student,
            GradebookSettings(dailyCalcMode = DailyCalcMode.WITH_EACH_MONTH, enableSmartRounding = true),
            LedgerSubject.GENERAL
        )
        assertEquals(90.0, res.term1Month1Effective!!, 0.001)
        assertEquals(80.0, res.term1Month2Effective!!, 0.001)
        assertEquals(85.0, res.term1Average!!, 0.001)
    }

    @Test
    fun verifyIndependentStartAndEndLessonAlertsAndVoicePhrases() {
        val timetable = listOf(
            TimetableCell(
                day = SchoolDay.SUNDAY,
                periodNumber = 2,
                gradeLevel = GradeLevel.SECOND,
                section = SectionLetter.A,
                subject = "الاجتماعيات",
                teacherName = "ابو امير"
            )
        )
        val dayMap = defaultDayPeriodTimes()
        // Period 2 Sunday is 12:45..13:25 -> beforeStart=12:44, beforeEnd=13:24
        val calStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 44)
            set(Calendar.SECOND, 0)
        }
        val startOnlyAllowed = LessonAlarmScheduler.detectDueInAppAlert(
            teacherKey = "ابو امير",
            timetable = timetable,
            dayPeriodTimes = dayMap,
            notifyBeforeStart = true,
            notifyBeforeEnd = false,
            cal = calStart
        )
        assertNotNull(startOnlyAllowed)
        assertEquals(LessonBoundaryType.BEFORE_START, startOnlyAllowed!!.boundaryType)
        assertTrue(startOnlyAllowed.spokenText.contains("الدرس الثاني"))

        val startBlocked = LessonAlarmScheduler.detectDueInAppAlert(
            teacherKey = "ابو امير",
            timetable = timetable,
            dayPeriodTimes = dayMap,
            notifyBeforeStart = false,
            notifyBeforeEnd = true,
            cal = calStart
        )
        assertNull(startBlocked)

        val calEnd = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 13)
            set(Calendar.MINUTE, 24)
            set(Calendar.SECOND, 0)
        }
        val endOnlyAllowed = LessonAlarmScheduler.detectDueInAppAlert(
            teacherKey = "ابو امير",
            timetable = timetable,
            dayPeriodTimes = dayMap,
            notifyBeforeStart = false,
            notifyBeforeEnd = true,
            cal = calEnd
        )
        assertNotNull(endOnlyAllowed)
        assertEquals(LessonBoundaryType.BEFORE_END, endOnlyAllowed!!.boundaryType)
        assertTrue(endOnlyAllowed.spokenText.contains("لانتهاء الدرس الثاني"))
    }

    @Test
    fun verifyOfflineWeightedExamPaperGenerationNoDuplicates() {
        val ch1 = QuestionChapter("ch1", 1, "الفصل الأول", 60)
        val ch2 = QuestionChapter("ch2", 2, "الفصل الثاني", 40)
        val sections = defaultQuestionSections()

        val questions = buildList {
            for (i in 1..6) {
                add(BankQuestion("def_$i", if (i <= 4) "ch1" else "ch2", "sec_define", "عرف المصطلح $i", marks = 2))
                add(BankQuestion("fill_$i", if (i <= 3) "ch1" else "ch2", "sec_blanks", "فراغ رقم $i: ......", marks = 2))
                add(BankQuestion("tf_$i", if (i <= 3) "ch1" else "ch2", "sec_tf", "صح ام خطا $i", marks = 2))
            }
        }

        val slots = listOf(
            ExamSlotConfig("sl_1", 1, "sec_define", "التعاريف", totalMarks = 10, branchCount = 4),
            ExamSlotConfig("sl_2", 2, "sec_blanks", "املأ الفراغات", totalMarks = 10, branchCount = 4),
            ExamSlotConfig("sl_3", 3, "sec_tf", "صح وخطأ", totalMarks = 10, branchCount = 4)
        )

        val paper = ExamGeneratorHelper.generateExamPaper(
            examTitle = "امتحان الشهر الأول",
            subjectName = "الاجتماعيات",
            gradeTitle = "الثاني المتوسط",
            examDate = "2026-11-18",
            examDuration = "45 دقيقة",
            teacherName = "ابو امير",
            schoolName = "متوسطة الحضارة",
            noteLine = "الإجابة عن جميع الأسئلة",
            selectedChapterIds = setOf("ch1", "ch2"),
            chapters = listOf(ch1, ch2),
            sections = sections,
            allQuestions = questions,
            slots = slots,
            seed = 42L
        )

        assertEquals(3, paper.blocks.size)
        assertEquals(30, paper.totalMarks)
        val allPickedIds = paper.blocks.flatMap { it.branches.map { q -> q.id } }
        assertEquals(12, allPickedIds.size)
        assertEquals(allPickedIds.toSet().size, allPickedIds.size)
    }

    @Test
    fun verifyExemptionsAfterRoundingTransferredFilteringAndCustomLedgers() {
        val settings = GradebookSettings(
            dailyCalcMode = DailyCalcMode.NEVER,
            enableSmartRounding = true,
            exemptionThreshold = 90,
            includeTransferredInStats = false
        )

        // 89 and 90 -> raw avg 89.5 -> smart rounding gives 90.0 -> Exempt in FIRST/SECOND!
        val firstGradeRoundUp = Student(
            id = "ex1",
            orderNumber = 1,
            fullName = "علي محسن",
            gradeLevel = GradeLevel.FIRST,
            section = SectionLetter.A,
            grades = StudentGrades(
                term1Month1 = 89.0,
                term1Month2 = 90.0, // avg 89.5 -> 90
                midYearTotal = 90.0,
                term2Month1 = 90.0,
                term2Month2 = 90.0
            )
        )
        val resFirst = GradeCalculator.computeForLedger(firstGradeRoundUp, settings, "general")
        assertEquals(90.0, resFirst.annualEffort!!, 0.001)
        assertTrue(GradeCalculator.isExemptEligible(firstGradeRoundUp, resFirst, settings))

        // THIRD intermediate is ministerial and never exempt even with 95
        val thirdGradeHigh = firstGradeRoundUp.copy(id = "ex2", gradeLevel = GradeLevel.THIRD)
        val resThird = GradeCalculator.computeForLedger(thirdGradeHigh, settings, "general")
        assertFalse(GradeCalculator.isExemptEligible(thirdGradeHigh, resThird, settings))

        // Custom ledger isolation
        val customLedgerStudent = firstGradeRoundUp.withGradesForLedger(
            "ledger_lab",
            StudentGrades(term1Month1 = 60.0, term1Month2 = 70.0)
        )
        val labRes = GradeCalculator.computeForLedger(customLedgerStudent, settings, "ledger_lab")
        assertEquals(65.0, labRes.term1Average!!, 0.001)
        assertEquals(90.0, GradeCalculator.computeForLedger(customLedgerStudent, settings, "general").term1Average!!, 0.001)

        // Transferred student excluded by default from stats, included when toggled
        val transferredLow = Student(
            id = "tr1",
            orderNumber = 2,
            fullName = "منقول",
            gradeLevel = GradeLevel.FIRST,
            section = SectionLetter.A,
            isTransferred = true,
            grades = StudentGrades(term1Month1 = 30.0, term1Month2 = 30.0)
        )
        val pool = listOf(firstGradeRoundUp, transferredLow)
        val excludedStats = GradeCalculator.computeSectionStats(pool, settings, LedgerSubject.GENERAL, "general")
        assertEquals(1, excludedStats.totalStudents)
        assertEquals(100f, excludedStats.passRatePercent, 0.01f)

        val includedStats = GradeCalculator.computeSectionStats(
            pool,
            settings.copy(includeTransferredInStats = true),
            LedgerSubject.GENERAL,
            "general"
        )
        assertEquals(2, includedStats.totalStudents)
        assertEquals(50f, includedStats.passRatePercent, 0.01f)

        // Timeline builder
        val timelineDaily = GradeCalculator.buildProgressTimeline(listOf(firstGradeRoundUp), settings, "general", TimelineSourceMode.MONTHLY_ONLY)
        assertTrue(timelineDaily.any { it.label == "نصف السنة" && it.score == 90.0 })
    }

    @Test
    fun verifyOfflinePrayerCalculatorAndQuranSearch() {
        val schedule = OfflinePrayerCalculator.computeSchedule(PrayerLocationSettings())
        assertEquals("الفلوجة - الأنبار", schedule.cityName)
        assertEquals(6, schedule.prayers.size)
        val fajr = schedule.prayers.first { it.id == "fajr" }
        val dhuhr = schedule.prayers.first { it.id == "dhuhr" }
        val maghrib = schedule.prayers.first { it.id == "maghrib" }
        assertTrue(fajr.hour in 3..6)
        assertTrue(dhuhr.hour in 11..13)
        assertTrue(maghrib.hour in 16..20)

        // Harakat-stripped search in OfflineQuranData
        val hits = OfflineQuranData.searchAyahs("الحمد لله رب العالمين")
        assertTrue(hits.isNotEmpty())
        assertEquals(1, hits.first().first.id)
    }

    @Test
    fun verifyThirdIntermediateMinisterialQualification() {
        val settings = GradebookSettings(
            dailyCalcMode = DailyCalcMode.NEVER,
            enableSmartRounding = true,
            exemptionThreshold = 90
        )

        val thirdQualified = Student(
            id = "min_ok",
            orderNumber = 1,
            fullName = "مصطفى كامل",
            gradeLevel = GradeLevel.THIRD,
            section = SectionLetter.A,
            grades = StudentGrades(
                term1Month1 = 49.0,
                term1Month2 = 50.0, // avg 49.5 -> rounded 50.0
                midYearTotal = 50.0,
                term2Month1 = 50.0,
                term2Month2 = 50.0
            )
        )
        val thirdLow = thirdQualified.copy(
            id = "min_low",
            fullName = "سعد جاسم",
            grades = StudentGrades(
                term1Month1 = 40.0,
                term1Month2 = 45.0,
                midYearTotal = 45.0,
                term2Month1 = 45.0,
                term2Month2 = 45.0
            )
        )
        val firstHigh = thirdQualified.copy(
            id = "first_high",
            gradeLevel = GradeLevel.FIRST,
            grades = StudentGrades(
                term1Month1 = 92.0,
                term1Month2 = 94.0,
                midYearTotal = 95.0,
                term2Month1 = 91.0,
                term2Month2 = 93.0
            )
        )

        val resQual = GradeCalculator.computeForLedger(thirdQualified, settings, "general")
        val resLow = GradeCalculator.computeForLedger(thirdLow, settings, "general")
        val resFirst = GradeCalculator.computeForLedger(firstHigh, settings, "general")

        assertEquals(50.0, resQual.annualEffort!!, 0.001)
        assertTrue(GradeCalculator.isMinisterialQualified(thirdQualified, resQual))
        assertFalse(GradeCalculator.isExemptEligible(thirdQualified, resQual, settings))
        assertFalse(GradeCalculator.isMinisterialQualified(thirdLow, resLow))
        assertFalse(GradeCalculator.isMinisterialQualified(firstHigh, resFirst))

        val analytics = GradeCalculator.computeComprehensiveAnalytics(
            students = listOf(thirdQualified, thirdLow, firstHigh),
            settings = settings,
            absenceCountMap = emptyMap(),
            ledgerId = "general"
        )
        assertEquals(1, analytics.ministerialQualifiedStudents.size)
        assertEquals("min_ok", analytics.ministerialQualifiedStudents.first().first.id)
        assertEquals(50.0, analytics.ministerialQualifiedStudents.first().second, 0.001)
        assertEquals(1, analytics.exemptStudents.size)
        assertEquals("first_high", analytics.exemptStudents.first().first.id)
    }

    @Test
    fun verifyMushafJuzAmmaSurahsPaginationAndTafsir() {
        val surahMulk = OfflineQuranData.surahs.find { it.id == 67 }
        assertNotNull(surahMulk)
        assertEquals(30, surahMulk!!.ayahs.size)

        for (id in 78..114) {
            val s = OfflineQuranData.surahs.find { it.id == id }
            assertNotNull("Missing Surah $id in OfflineQuranData", s)
            assertTrue("Surah $id has empty ayahs", s!!.ayahs.isNotEmpty())
        }

        val pages = OfflineQuranData.buildMushafPages()
        assertTrue(pages.size >= 38)
        assertEquals((1..pages.size).toList(), pages.map { it.pageNumber })
        val coveredSurahIds = pages.flatMap { p -> p.segments.map { it.surah.id } }.toSet()
        assertTrue(coveredSurahIds.contains(1))
        assertTrue(coveredSurahIds.contains(67))
        assertTrue(coveredSurahIds.containsAll((78..114).toList()))

        val ikhlasPageIdx = OfflineQuranData.pageIndexFor(112, 1, pages)
        assertTrue(pages[ikhlasPageIdx].segments.any { it.surah.id == 112 })
        assertEquals("١٢٣", OfflineQuranData.toArabicIndicDigits(123))

        val fatihaTafsir = com.example.data.OfflineQuranTafsirHelper.lookup(
            surahId = 1,
            ayahNumber = 2,
            ayahText = "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ"
        )
        assertTrue(fatihaTafsir.tafsirMuyassar.isNotBlank())
        assertTrue(fatihaTafsir.wordMeanings.isNotEmpty())

        val nabaTafsir = com.example.data.OfflineQuranTafsirHelper.lookup(
            surahId = 78,
            ayahNumber = 1,
            ayahText = "عَمَّ يَتَسَآءَلُونَ"
        )
        assertTrue(nabaTafsir.tafsirMuyassar.isNotBlank())
        assertTrue(nabaTafsir.wordMeanings.isNotEmpty())
    }

    @Test
    fun verifyFirebaseUpdateComparisonPriorityParsingAndStrictAdminVisibility() {
        assertTrue(
            com.example.data.FirebaseUpdateAndNotificationsRepository.isNewerVersionAvailable(
                currentVersionCode = 1,
                latestVersionCode = 2
            )
        )
        assertFalse(
            com.example.data.FirebaseUpdateAndNotificationsRepository.isNewerVersionAvailable(
                currentVersionCode = 2,
                latestVersionCode = 2
            )
        )
        assertFalse(
            com.example.data.FirebaseUpdateAndNotificationsRepository.isNewerVersionAvailable(
                currentVersionCode = 3,
                latestVersionCode = 2
            )
        )

        assertEquals(
            com.example.data.AnnouncementPriority.URGENT,
            com.example.data.AnnouncementPriority.fromRaw("urgent")
        )
        assertEquals(
            com.example.data.AnnouncementPriority.NORMAL,
            com.example.data.AnnouncementPriority.fromRaw("unknown_value")
        )

        // Admin panel is now accessible via 7 rapid taps on version / footer and verified by Firebase PIN/Auth,
        // no longer locked strictly to PHYSICS && THIRD.
        assertTrue(
            com.example.data.targetMatchesTeacher(
                targetTypeRaw = "ALL",
                targetValue = "",
                username = "mustafa",
                teacherName = "مصطفى عيسى",
                uid = "uid-1",
                specialtyId = SubjectSpecialty.PHYSICS.id,
                gradeId = GradeLevel.THIRD.id
            )
        )
        assertTrue(
            com.example.data.targetMatchesTeacher(
                targetTypeRaw = "USER",
                targetValue = "mustafa",
                username = "Mustafa",
                teacherName = "مصطفى عيسى",
                uid = "uid-1",
                specialtyId = SubjectSpecialty.PHYSICS.id,
                gradeId = GradeLevel.THIRD.id
            )
        )
        assertFalse(
            com.example.data.targetMatchesTeacher(
                targetTypeRaw = "USER",
                targetValue = "other_teacher",
                username = "mustafa",
                teacherName = "مصطفى عيسى",
                uid = "uid-1",
                specialtyId = SubjectSpecialty.PHYSICS.id,
                gradeId = GradeLevel.THIRD.id
            )
        )
        assertTrue(
            com.example.data.targetMatchesTeacher(
                targetTypeRaw = "GRADE",
                targetValue = GradeLevel.THIRD.id,
                username = "mustafa",
                teacherName = "مصطفى عيسى",
                uid = "uid-1",
                specialtyId = SubjectSpecialty.PHYSICS.id,
                gradeId = GradeLevel.THIRD.id
            )
        )
        assertTrue(
            com.example.data.targetMatchesTeacher(
                targetTypeRaw = "SUBJECT",
                targetValue = SubjectSpecialty.BIOLOGY.titleAr,
                username = "ali",
                teacherName = "علي",
                uid = "uid-2",
                specialtyId = SubjectSpecialty.BIOLOGY.id,
                gradeId = GradeLevel.FIRST.id
            )
        )
    }

    @Test
    fun verifyBackupHelpersWordCounterAnd30SecondAlertDetection() {
        val key1 = com.example.utils.BackupCryptoHelper.normalizeUsernameKey("  Mustafa_IQ  ")
        val key2 = com.example.utils.BackupCryptoHelper.normalizeUsernameKey("mustafa_iq")
        assertEquals(key1, key2)
        assertEquals(40, key1.length)

        val ref1 = com.example.utils.BackupCryptoHelper.deriveDeterministicReferralCode("uid-abc")
        val ref2 = com.example.utils.BackupCryptoHelper.deriveDeterministicReferralCode("uid-abc")
        assertEquals(ref1, ref2)
        assertTrue(ref1.startsWith("RF"))
        assertEquals(8, ref1.length)

        val reportCode = com.example.utils.BackupCryptoHelper.generateReportCode("s1")
        assertTrue(reportCode.startsWith("PR"))
        assertEquals(10, reportCode.length)

        val hundredWords = (1..100).joinToString(" ") { "كلمة$it" }
        val hundredOneWords = (1..101).joinToString(" ") { "كلمة$it" }
        assertEquals(100, com.example.utils.BackupCryptoHelper.countWords(hundredWords))
        assertEquals(101, com.example.utils.BackupCryptoHelper.countWords(hundredOneWords))

        val sundayPeriod1 = listOf(
            TimetableCell(
                day = SchoolDay.SUNDAY,
                periodNumber = 1,
                subject = "الفيزياء",
                teacherName = "أ. مصطفى عيسى",
                gradeLevel = GradeLevel.THIRD,
                section = SectionLetter.A
            )
        )
        val periodTimes = mapOf(
            SchoolDay.SUNDAY to listOf(
                PeriodTime(periodNumber = 1, startHour = 8, startMinute = 0, endHour = 8, endMinute = 45)
            )
        )
        val cal20sBefore = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, SchoolDay.SUNDAY.calendarDayOfWeek)
            set(java.util.Calendar.HOUR_OF_DAY, 7)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 40)
        }
        val alert30s = com.example.utils.LessonAlarmScheduler.detectDueInAppAlert(
            teacherKey = "مصطفى",
            timetable = sundayPeriod1,
            dayPeriodTimes = periodTimes,
            leadMinutes = 2,
            notifyBeforeStart = true,
            notifyBeforeEnd = true,
            halfMinuteEnabled = true,
            cal = cal20sBefore
        )
        assertNotNull(alert30s)
        assertTrue(alert30s!!.eventKey.endsWith("_start30s"))
        assertTrue(alert30s.title.contains("30 ثانية"))

        val no30sAlert = com.example.utils.LessonAlarmScheduler.detectDueInAppAlert(
            teacherKey = "مصطفى",
            timetable = sundayPeriod1,
            dayPeriodTimes = periodTimes,
            leadMinutes = 2,
            notifyBeforeStart = true,
            notifyBeforeEnd = true,
            halfMinuteEnabled = false,
            cal = cal20sBefore
        )
        assertNull(no30sAlert)
    }

    @Test
    fun verifyNewPlatformHelpersScheduledAnnouncementsAndExportBuilders() {
        // 1) Auto-generated 4-letter uppercase username + activation & comparison codes
        val autoUser = com.example.utils.BackupCryptoHelper.generateAutoFourLetterUsername()
        assertEquals(4, autoUser.length)
        assertTrue(autoUser.all { it in 'A'..'Z' })

        val actCode = com.example.utils.BackupCryptoHelper.generateActivationCode()
        assertTrue(actCode.startsWith("RF-"))
        assertEquals(9, actCode.length)

        val cmpCode = com.example.utils.BackupCryptoHelper.generateComparisonCode()
        assertTrue(cmpCode.startsWith("CMP-"))
        assertEquals(10, cmpCode.length)

        // 2) Scheduled notification display gate
        val now = 1_700_000_000_000L
        val immediate = com.example.data.AdminAnnouncement(id = "a1", title = "فوري", scheduledForMillis = 0L)
        val dueFuture = com.example.data.AdminAnnouncement(id = "a2", title = "مجدول حان وقته", scheduledForMillis = now - 1000L)
        val pendingFuture = com.example.data.AdminAnnouncement(id = "a3", title = "مجدول مستقبلي", scheduledForMillis = now + 60_000L)
        assertTrue(immediate.isReadyToDisplay(now))
        assertTrue(dueFuture.isReadyToDisplay(now))
        assertFalse(pendingFuture.isReadyToDisplay(now))

        // 3) RemoteControl defaults for new admin switches
        val remoteDefaults = com.example.data.RemoteControlConfig()
        assertTrue(remoteDefaults.subscriptionsEnabled)
        assertEquals(13, remoteDefaults.internalUpdateDurationSec)
        assertEquals(3, remoteDefaults.passwordChangeIntervalDays)
        assertEquals(24, remoteDefaults.chatExpiryHours)
        assertTrue(remoteDefaults.customPromotionTitles.isNotEmpty())

        // 4) Excel XML and Word HTML full report builders
        val sampleStudent = Student(
            id = "exp_s1",
            orderNumber = 1,
            fullName = "عبدالله سعد كريم",
            gradeLevel = GradeLevel.FIRST,
            section = SectionLetter.A,
            grades = StudentGrades(
                term1Month1 = 92.0,
                term1Month2 = 94.0,
                midYearTotal = 93.0,
                term2Month1 = 95.0,
                term2Month2 = 95.0,
                finalExam = 96.0
            )
        )
        val sampleState = AppState(
            profile = TeacherProfile(teacherName = "أ. مصطفى", schoolName = "متوسطة النور", subjectName = "الفيزياء"),
            students = listOf(sampleStudent)
        )
        val xml = com.example.utils.ExportManager.buildExcelSpreadsheetXml(sampleState, GradeLevel.FIRST, SectionLetter.A, "general")
        assertTrue(xml.contains("<Workbook"))
        assertTrue(xml.contains("سجل الدرجات"))
        assertTrue(xml.contains("الإعفاء والوزاري"))
        assertTrue(xml.contains("التحليل والإحصائيات"))
        assertTrue(xml.contains("عبدالله سعد كريم"))

        val wordHtml = com.example.utils.ExportManager.buildWordHtmlDocument(sampleState, GradeLevel.FIRST, SectionLetter.A, "general")
        assertTrue(wordHtml.contains("dir='rtl'"))
        assertTrue(wordHtml.contains("عبدالله سعد كريم"))
        assertTrue(wordHtml.contains("الملخص الإحصائي"))
    }

    @Test
    fun verifyColorWheelHelpersAndProfileCustomFields() {
        val hex = com.example.ui.components.hsvToHexLong(180f, 0.8f, 0.9f)
        assertTrue((hex and 0xFF000000L) == 0xFF000000L)
        val formatted = com.example.ui.components.formatColorHex(hex)
        assertTrue(formatted.startsWith("#") && formatted.length == 7)
        assertEquals("تلقائي", com.example.ui.components.formatColorHex(0L))

        val profile = TeacherProfile(
            adminSectionUnlocked = true,
            customColorsEnabled = true,
            customTextColorHex = 0xFFFFFFFFL,
            customButtonColorHex = 0xFFFFC83DL,
            customBgColorHex = 0xFF061124L,
            customCardAccentColorHex = 0xFF22D3EEL
        )
        assertTrue(profile.adminSectionUnlocked)
        assertTrue(profile.customColorsEnabled)
        assertEquals(0xFFFFFFFFL, profile.customTextColorHex)
        assertEquals(0xFFFFC83DL, profile.customButtonColorHex)
        assertEquals(0xFF061124L, profile.customBgColorHex)
        assertEquals(0xFF22D3EEL, profile.customCardAccentColorHex)
    }

    @Test
    fun verifyExamWriterHelpersSpecialistProAndNewProfileFields() {
        // 1) ExamVoiceParser parses Arabic spoken exam prompt with type, marks, and branches
        val parsed = ExamVoiceParser.parseSpokenArabicToExamQuestion(
            spokenText = "السؤال الأول املا الفراغات عليه 15 درجات فرع أ يتكون الماء من ذرتي هيدروجين فرع ب عاصمة العراق هي بغداد",
            questionIndex = 0
        )
        assertEquals("س1/", parsed.questionTitle)
        assertEquals(ExamQuestionType.FILL_BLANKS, parsed.questionType)
        assertEquals(15, parsed.marks)
        assertEquals(2, parsed.branches.size)
        assertEquals("أ", parsed.branches[0].label)
        assertTrue(parsed.branches[0].prompt.contains("الهيدروجين") || parsed.branches[0].prompt.contains("هيدروجين"))
        assertEquals("ب", parsed.branches[1].label)

        // 2) QuranExamHelper search & tashkeel toggle
        val fatihaList = QuranExamHelper.searchSurahs("الفاتحة")
        assertTrue(fatihaList.any { it.id == 1 })
        val fatiha = fatihaList.first { it.id == 1 }
        val withTashkeel = QuranExamHelper.formatAyahRange(fatiha, 1, 2, withTashkeel = true)
        val plainText = QuranExamHelper.formatAyahRange(fatiha, 1, 2, withTashkeel = false)
        assertTrue(withTashkeel.contains("[سورة"))
        assertTrue(plainText.contains("الحمد"))

        // 3) ExperimentalLayoutMode.SPECIALIST_PRO & resolution order
        assertEquals(
            com.example.utils.ExperimentalLayoutMode.SPECIALIST_PRO,
            com.example.utils.ExperimentalLayoutMode.fromId("SPECIALIST_PRO")
        )
        val remoteSpecialist = com.example.data.RemoteControlConfig(
            experimentalLayoutEnabled = true,
            experimentalLayoutMode = "SPECIALIST_PRO"
        )
        val defaultTeacher = TeacherProfile()
        assertEquals(
            com.example.utils.ExperimentalLayoutMode.SPECIALIST_PRO,
            com.example.utils.resolveEffectiveLayoutMode(remoteSpecialist, defaultTeacher, isAdmin = false)
        )
        assertEquals(
            com.example.utils.ExperimentalLayoutMode.CLASSIC,
            com.example.utils.resolveEffectiveLayoutMode(
                remoteSpecialist,
                defaultTeacher.copy(preferDefaultDesign = true),
                isAdmin = false
            )
        )

        // 4) TeacherProfile new fields defaults and customization
        assertTrue(defaultTeacher.archivedTools.isEmpty())
        assertTrue(defaultTeacher.favoriteTools.isEmpty())
        assertFalse(defaultTeacher.gradebookSimpleMode)
        assertTrue(defaultTeacher.gradebookVisibleActions.isEmpty())
        assertTrue(defaultTeacher.chatActiveOnlineVisible)
    }
}


