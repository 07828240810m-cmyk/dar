package com.example.utils

import com.example.model.BehaviorStatus
import com.example.model.DailyCalcMode
import com.example.model.DailyColumnDef
import com.example.model.GradeLevel
import com.example.model.GradebookSettings
import com.example.model.LedgerSubject
import com.example.model.Student
import com.example.model.StudentGrades
import com.example.model.dailyCellKey
import kotlin.math.floor

enum class LevelTrend(val labelAr: String) {
    IMPROVED("تحسّن المستوى"),
    STABLE("مستوى ثابت"),
    DECLINED("تراجع المستوى"),
    NO_DATA("بيانات غير كافية")
}

data class ComputedStudentResult(
    val term1Month1Effective: Double?,
    val term1Month2Effective: Double?,
    val term1Month3Effective: Double?,
    val term1Average: Double?,
    val midYearEffective: Double?,
    val term2Month1Effective: Double?,
    val term2Month2Effective: Double?,
    val term2Month3Effective: Double?,
    val term2Average: Double?,
    val annualEffort: Double?,
    val finalGrade: Double?,
    val isPassing: Boolean?,
    val trend: LevelTrend,
    val trendDelta: Double?
)

data class SectionStats(
    val totalStudents: Int,
    val gradedStudents: Int,
    val passingCount: Int,
    val failingCount: Int,
    val passRatePercent: Float,
    val averageScore: Double,
    val badBehaviorCount: Int,
    val inactiveCount: Int,
    val improvedCount: Int,
    val stableCount: Int,
    val declinedCount: Int
)

data class SmartClassroomInsight(
    val title: String,
    val summary: String,
    val recommendations: List<String>,
    val atRiskNames: List<String>,
    val topAchieverNames: List<String>
)

data class TimelinePoint(
    val label: String,
    val score: Double?
)

enum class TimelineSourceMode(val id: String, val titleAr: String) {
    DAILY_ONLY("daily", "اليومي فقط"),
    MONTHLY_ONLY("monthly", "الشهري فقط"),
    BOTH("both", "اليومي والشهري معاً")
}

data class ComprehensiveAnalyticsSnapshot(
    val exemptStudents: List<Pair<Student, Double>>,
    val ministerialQualifiedStudents: List<Pair<Student, Double>>,
    val grade3Total: Int,
    val grade3Graded: Int,
    val grade3Passing: Int,
    val grade3PassRate: Float,
    val grade3Average: Double,
    val criticalCases: List<Pair<Student, String>>,
    val transferredCount: Int
)

object GradeCalculator {

    fun applyRounding(value: Double?, enableSmartRounding: Boolean): Double? {
        if (value == null) return null
        return if (enableSmartRounding) {
            floor(value + 0.500001)
        } else {
            kotlin.math.round(value * 10.0) / 10.0
        }
    }

    fun formatScore(value: Double?, enableSmartRounding: Boolean): String {
        val rounded = applyRounding(value, enableSmartRounding) ?: return "—"
        return if (enableSmartRounding || rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            "%.1f".format(rounded)
        }
    }

    fun referenceScore(res: ComputedStudentResult): Double? =
        res.finalGrade ?: res.annualEffort ?: res.midYearEffective ?: res.term2Average ?: res.term1Average

    fun exemptionCandidateScore(res: ComputedStudentResult): Double? =
        res.annualEffort ?: res.finalGrade ?: res.term2Average ?: res.term1Average ?: res.midYearEffective

    fun isExemptEligible(
        student: Student,
        res: ComputedStudentResult,
        settings: GradebookSettings
    ): Boolean {
        if (student.gradeLevel != GradeLevel.FIRST && student.gradeLevel != GradeLevel.SECOND) {
            return false
        }
        val score = exemptionCandidateScore(res) ?: return false
        return score >= settings.exemptionThreshold
    }

    fun isMinisterialQualified(
        student: Student,
        res: ComputedStudentResult
    ): Boolean {
        if (student.gradeLevel != GradeLevel.THIRD) return false
        val effort = res.annualEffort ?: return false
        return effort >= 50.0
    }

    fun computeDailyOutOf100(
        studentId: String,
        columns: List<DailyColumnDef>,
        dailyScores: Map<String, Double>,
        directSum: Boolean = false
    ): Double? {
        if (columns.isEmpty()) return null
        var earnedSum = 0.0
        var maxSum = 0.0
        var hasAnyScore = false
        for (col in columns) {
            maxSum += col.maxScore.coerceAtLeast(1.0)
            val s = dailyScores[dailyCellKey(col.id, studentId)]
            if (s != null) {
                hasAnyScore = true
                earnedSum += s.coerceIn(0.0, col.maxScore.coerceAtLeast(1.0))
            }
        }
        if (!hasAnyScore) return null
        return if (directSum || maxSum <= 0.0) {
            earnedSum.coerceIn(0.0, 100.0)
        } else {
            ((earnedSum / maxSum) * 100.0).coerceIn(0.0, 100.0)
        }
    }

    private fun computeTerm(
        m1: Double?,
        m2: Double?,
        m3: Double?,
        m1Daily: Double?,
        m2Daily: Double?,
        termDaily: Double?,
        settings: GradebookSettings
    ): Triple<List<Double?>, Double?, Boolean> {
        val activeRaw = when (settings.monthsPerTerm) {
            1 -> listOf(m1 to (m1Daily ?: termDaily))
            3 -> listOf(
                m1 to (m1Daily ?: termDaily),
                m2 to (m2Daily ?: termDaily),
                m3 to (termDaily ?: m2Daily ?: m1Daily)
            )
            else -> listOf(
                m1 to (m1Daily ?: termDaily),
                m2 to (m2Daily ?: termDaily)
            )
        }

        val effectiveMonths = activeRaw.map { (monthScore, dailyForMonth) ->
            if (monthScore == null) null
            else when (settings.dailyCalcMode) {
                DailyCalcMode.WITH_EACH_MONTH -> {
                    if (dailyForMonth != null) (monthScore + dailyForMonth) / 2.0 else monthScore
                }
                else -> monthScore
            }
        }

        val validMonths = effectiveMonths.filterNotNull()
        if (validMonths.isEmpty()) return Triple(effectiveMonths, null, false)

        val effectiveTermDaily = termDaily ?: listOfNotNull(m1Daily, m2Daily).takeIf { it.isNotEmpty() }?.average()
        val rawAvg = when (settings.dailyCalcMode) {
            DailyCalcMode.ONCE_WITH_TWO_MONTHS -> {
                if (effectiveTermDaily != null) {
                    (validMonths.sum() + effectiveTermDaily) / (validMonths.size + 1)
                } else {
                    validMonths.average()
                }
            }
            else -> validMonths.average()
        }

        return Triple(effectiveMonths, applyRounding(rawAvg, settings.enableSmartRounding), true)
    }

    fun compute(
        student: Student,
        settings: GradebookSettings,
        ledgerSubject: LedgerSubject = LedgerSubject.GENERAL
    ): ComputedStudentResult = computeGrades(student.gradesFor(ledgerSubject), settings)

    fun computeForLedger(
        student: Student,
        settings: GradebookSettings,
        ledgerId: String
    ): ComputedStudentResult = computeGrades(student.gradesForLedger(ledgerId), settings)

    fun computeGrades(g: StudentGrades, settings: GradebookSettings): ComputedStudentResult {
        val (t1Months, t1Avg, _) = computeTerm(
            m1 = g.term1Month1,
            m2 = g.term1Month2,
            m3 = g.term1Month3,
            m1Daily = g.term1Month1Daily,
            m2Daily = g.term1Month2Daily,
            termDaily = g.term1Daily,
            settings = settings
        )
        val (t2Months, t2Avg, _) = computeTerm(
            m1 = g.term2Month1,
            m2 = g.term2Month2,
            m3 = g.term2Month3,
            m1Daily = g.term2Month1Daily,
            m2Daily = g.term2Month2Daily,
            termDaily = g.term2Daily,
            settings = settings
        )

        val midRaw = if (settings.splitMidYearOralWritten && (g.midYearOral != null || g.midYearWritten != null)) {
            (g.midYearOral ?: 0.0) + (g.midYearWritten ?: 0.0)
        } else {
            g.midYearTotal
        }
        val midEffective = applyRounding(midRaw, settings.enableSmartRounding)

        val effortRaw = if (t1Avg != null && midEffective != null && t2Avg != null) {
            (t1Avg + midEffective + t2Avg) / 3.0
        } else {
            val parts = listOfNotNull(t1Avg, midEffective, t2Avg)
            if (parts.size >= 2) parts.average() else null
        }
        val annualEffort = applyRounding(effortRaw, settings.enableSmartRounding)

        val finalRaw = if (annualEffort != null && g.finalExam != null) {
            (annualEffort + g.finalExam) / 2.0
        } else null
        val finalGrade = applyRounding(finalRaw, settings.enableSmartRounding)

        val referenceScore = finalGrade ?: annualEffort ?: midEffective ?: t2Avg ?: t1Avg
        val isPassing = referenceScore?.let { it >= settings.passingScore }

        val (trend, delta) = computeTrend(g, t1Avg, t2Avg)

        return ComputedStudentResult(
            term1Month1Effective = applyRounding(t1Months.getOrNull(0), settings.enableSmartRounding),
            term1Month2Effective = applyRounding(t1Months.getOrNull(1), settings.enableSmartRounding),
            term1Month3Effective = applyRounding(t1Months.getOrNull(2), settings.enableSmartRounding),
            term1Average = t1Avg,
            midYearEffective = midEffective,
            term2Month1Effective = applyRounding(t2Months.getOrNull(0), settings.enableSmartRounding),
            term2Month2Effective = applyRounding(t2Months.getOrNull(1), settings.enableSmartRounding),
            term2Month3Effective = applyRounding(t2Months.getOrNull(2), settings.enableSmartRounding),
            term2Average = t2Avg,
            annualEffort = annualEffort,
            finalGrade = finalGrade,
            isPassing = isPassing,
            trend = trend,
            trendDelta = delta
        )
    }

    private fun computeTrend(
        g: StudentGrades,
        t1Avg: Double?,
        t2Avg: Double?
    ): Pair<LevelTrend, Double?> {
        val earlier: Double?
        val later: Double?
        when {
            t1Avg != null && t2Avg != null -> {
                earlier = t1Avg
                later = t2Avg
            }
            g.term2Month1 != null && g.term2Month2 != null -> {
                earlier = g.term2Month1
                later = g.term2Month2
            }
            g.term1Month1 != null && g.term1Month2 != null -> {
                earlier = g.term1Month1
                later = g.term1Month2
            }
            t1Avg != null && g.midYearTotal != null -> {
                earlier = t1Avg
                later = g.midYearTotal
            }
            else -> return LevelTrend.NO_DATA to null
        }
        val diff = later - earlier
        val trend = when {
            diff >= 3.0 -> LevelTrend.IMPROVED
            diff <= -3.0 -> LevelTrend.DECLINED
            else -> LevelTrend.STABLE
        }
        return trend to diff
    }

    fun filterForStats(students: List<Student>, settings: GradebookSettings): List<Student> =
        if (settings.includeTransferredInStats) students else students.filterNot { it.isTransferred }

    fun computeSectionStats(
        students: List<Student>,
        settings: GradebookSettings,
        ledgerSubject: LedgerSubject = LedgerSubject.GENERAL,
        ledgerId: String? = null
    ): SectionStats {
        val activePool = filterForStats(students, settings)
        if (activePool.isEmpty()) {
            return SectionStats(0, 0, 0, 0, 0f, 0.0, 0, 0, 0, 0, 0)
        }
        val effectiveId = ledgerId ?: ledgerSubject.id
        val results = activePool.map { it to computeForLedger(it, settings, effectiveId) }
        val graded = results.mapNotNull { (_, res) ->
            val score = referenceScore(res)
            score?.let { it to (res.isPassing == true) }
        }
        val passing = graded.count { it.second }
        val failing = graded.size - passing
        val passRate = if (graded.isNotEmpty()) (passing * 100f / graded.size) else 0f
        val avgScore = if (graded.isNotEmpty()) graded.map { it.first }.average() else 0.0

        return SectionStats(
            totalStudents = activePool.size,
            gradedStudents = graded.size,
            passingCount = passing,
            failingCount = failing,
            passRatePercent = passRate,
            averageScore = avgScore,
            badBehaviorCount = activePool.count {
                it.behaviorStatus == BehaviorStatus.BAD || it.behaviorStatus == BehaviorStatus.NEEDS_ATTENTION
            },
            inactiveCount = activePool.count { it.isInactive || !it.dailyPrepDone },
            improvedCount = results.count { it.second.trend == LevelTrend.IMPROVED },
            stableCount = results.count { it.second.trend == LevelTrend.STABLE },
            declinedCount = results.count { it.second.trend == LevelTrend.DECLINED }
        )
    }

    fun computeComprehensiveAnalytics(
        students: List<Student>,
        settings: GradebookSettings,
        absenceCountMap: Map<String, Int>,
        ledgerId: String
    ): ComprehensiveAnalyticsSnapshot {
        val pool = filterForStats(students, settings)
        val transferredCount = students.count { it.isTransferred }

        val exemptList = pool.mapNotNull { st ->
            val res = computeForLedger(st, settings, ledgerId)
            if (isExemptEligible(st, res, settings)) {
                val sc = exemptionCandidateScore(res) ?: 90.0
                st to sc
            } else null
        }.sortedByDescending { it.second }

        val g3Students = pool.filter { it.gradeLevel == GradeLevel.THIRD }
        val ministerialList = g3Students.mapNotNull { st ->
            val res = computeForLedger(st, settings, ledgerId)
            if (isMinisterialQualified(st, res)) {
                st to (res.annualEffort ?: 50.0)
            } else null
        }.sortedByDescending { it.second }
        val g3Graded = g3Students.mapNotNull { st ->
            val res = computeForLedger(st, settings, ledgerId)
            referenceScore(res)?.let { it to (res.isPassing == true) }
        }
        val g3Passing = g3Graded.count { it.second }
        val g3PassRate = if (g3Graded.isNotEmpty()) g3Passing * 100f / g3Graded.size else 0f
        val g3Avg = if (g3Graded.isNotEmpty()) g3Graded.map { it.first }.average() else 0.0

        val critical = mutableListOf<Pair<Student, String>>()
        pool.forEach { st ->
            val res = computeForLedger(st, settings, ledgerId)
            val score = referenceScore(res)
            val absCount = absenceCountMap[st.id] ?: 0
            val reasons = mutableListOf<String>()
            if (score != null && score < settings.passingScore && score >= settings.passingScore - 6) {
                reasons += "قريب من النجاح (${score.toInt()})"
            } else if (score != null && score < settings.passingScore) {
                reasons += "راسب (${score.toInt()})"
            }
            if (res.trend == LevelTrend.DECLINED) {
                reasons += "تراجع (${res.trendDelta?.toInt() ?: 0})"
            }
            if (absCount >= 3) {
                reasons += "غيابات ($absCount)"
            }
            if (st.behaviorStatus == BehaviorStatus.BAD) {
                reasons += "سلوك يحتاج متابعة"
            }
            if (reasons.isNotEmpty()) {
                critical += st to reasons.joinToString(" • ")
            }
        }

        return ComprehensiveAnalyticsSnapshot(
            exemptStudents = exemptList,
            ministerialQualifiedStudents = ministerialList,
            grade3Total = g3Students.size,
            grade3Graded = g3Graded.size,
            grade3Passing = g3Passing,
            grade3PassRate = g3PassRate,
            grade3Average = g3Avg,
            criticalCases = critical,
            transferredCount = transferredCount
        )
    }

    fun buildProgressTimeline(
        students: List<Student>,
        settings: GradebookSettings,
        ledgerId: String,
        sourceMode: TimelineSourceMode
    ): List<TimelinePoint> {
        if (students.isEmpty()) return emptyList()
        fun avgOf(extract: (StudentGrades, ComputedStudentResult) -> Double?): Double? {
            val vals = students.mapNotNull { st ->
                val g = st.gradesForLedger(ledgerId)
                val r = computeGrades(g, settings)
                extract(g, r)
            }
            return if (vals.isNotEmpty()) vals.average() else null
        }

        val dailyPoints = listOf(
            TimelinePoint("يومي ش1 ف1", avgOf { g, _ -> g.term1Month1Daily ?: g.term1Daily }),
            TimelinePoint("يومي ش2 ف1", avgOf { g, _ -> g.term1Month2Daily ?: g.term1Daily }),
            TimelinePoint("يومي ش1 ف2", avgOf { g, _ -> g.term2Month1Daily ?: g.term2Daily }),
            TimelinePoint("يومي ش2 ف2", avgOf { g, _ -> g.term2Month2Daily ?: g.term2Daily })
        )

        val monthlyPoints = listOf(
            TimelinePoint("ش1 ف1", avgOf { g, _ -> g.term1Month1 }),
            TimelinePoint("ش2 ف1", avgOf { g, _ -> g.term1Month2 }),
            TimelinePoint("نصف السنة", avgOf { _, r -> r.midYearEffective }),
            TimelinePoint("ش1 ف2", avgOf { g, _ -> g.term2Month1 }),
            TimelinePoint("ش2 ف2", avgOf { g, _ -> g.term2Month2 }),
            TimelinePoint("النهائي", avgOf { _, r -> r.finalGrade })
        )

        return when (sourceMode) {
            TimelineSourceMode.DAILY_ONLY -> dailyPoints
            TimelineSourceMode.MONTHLY_ONLY -> monthlyPoints
            TimelineSourceMode.BOTH -> listOf(
                TimelinePoint("يومي ش1", avgOf { g, _ -> g.term1Month1Daily ?: g.term1Daily }),
                TimelinePoint("ش1 ف1", avgOf { g, _ -> g.term1Month1 }),
                TimelinePoint("يومي ش2", avgOf { g, _ -> g.term1Month2Daily ?: g.term1Daily }),
                TimelinePoint("ش2 ف1", avgOf { g, _ -> g.term1Month2 }),
                TimelinePoint("نصف السنة", avgOf { _, r -> r.midYearEffective }),
                TimelinePoint("يومي ف2", avgOf { g, _ -> g.term2Month1Daily ?: g.term2Daily }),
                TimelinePoint("ش1 ف2", avgOf { g, _ -> g.term2Month1 }),
                TimelinePoint("ش2 ف2", avgOf { g, _ -> g.term2Month2 }),
                TimelinePoint("النهائي", avgOf { _, r -> r.finalGrade })
            )
        }
    }

    fun analyzeClassroom(
        scopeTitle: String,
        students: List<Student>,
        settings: GradebookSettings,
        absenceCountMap: Map<String, Int>,
        ledgerSubject: LedgerSubject = LedgerSubject.GENERAL,
        ledgerId: String? = null
    ): SmartClassroomInsight {
        val activePool = filterForStats(students, settings)
        val effectiveId = ledgerId ?: ledgerSubject.id
        val stats = computeSectionStats(activePool, settings, ledgerSubject, effectiveId)
        val computed = activePool.map { it to computeForLedger(it, settings, effectiveId) }

        val atRisk = computed.filter { (st, res) ->
            val score = referenceScore(res)
            (score != null && score < settings.passingScore) ||
                res.trend == LevelTrend.DECLINED ||
                st.isInactive ||
                (absenceCountMap[st.id] ?: 0) >= 3
        }.take(6).map { it.first.fullName }

        val topAchievers = computed.mapNotNull { (st, res) ->
            val score = referenceScore(res)
            score?.let { st to it }
        }.sortedByDescending { it.second }.take(5).map { "${it.first.fullName} (${it.second.toInt()})" }

        val recommendations = mutableListOf<String>()
        if (stats.gradedStudents == 0) {
            recommendations += "لم تُسجَّل درجات كافية بعد في $scopeTitle؛ ابدأ بإدخال درجات الشهر الأول لتفعيل التشخيص الدقيق."
        } else {
            if (stats.passRatePercent < 60f) {
                recommendations += "نسبة النجاح منخفضة (${stats.passRatePercent.toInt()}%)؛ يُنصح بحصة مراجعة مكثفة وتبسيط الأسئلة الشهرية."
            } else if (stats.passRatePercent >= 85f) {
                recommendations += "أداء متميز للشعبة بنسبة نجاح ${stats.passRatePercent.toInt()}%؛ يمكن طرح أنشطة إثرائية للمتفوقين."
            }
            if (stats.declinedCount > stats.improvedCount) {
                recommendations += "عدد الطلبة المتراجعين (${stats.declinedCount}) أكبر من المتحسّنين (${stats.improvedCount})؛ راجع أسباب التراجع في الدروس الأخيرة."
            }
        }
        if (stats.inactiveCount > 0) {
            recommendations += "يوجد ${stats.inactiveCount} طالباً ضمن قائمة الخاملين أو غير الملتزمين بالتحضير اليومي."
        }
        if (stats.badBehaviorCount > 0) {
            recommendations += "تم رصد ${stats.badBehaviorCount} حالة سلوكية تحتاج متابعة؛ يُفضّل التواصل المبكر مع ولي الأمر."
        }
        if (recommendations.isEmpty()) {
            recommendations += "المؤشرات الصفية متوازنة ومستقرة. استمر في تعزيز المشاركة الأسبوعية."
        }

        val summary = if (stats.gradedStudents > 0) {
            "نسبة النجاح ${stats.passRatePercent.toInt()}% بمعدل عام ${"%.1f".format(stats.averageScore)}، تحسّن ${stats.improvedCount} وتراجع ${stats.declinedCount}."
        } else {
            "العدد الكلي ${stats.totalStudents} طالباً. جاهز لرصد الدرجات والتحليل الفوري."
        }

        return SmartClassroomInsight(
            title = "التشخيص الذكي: $scopeTitle",
            summary = summary,
            recommendations = recommendations,
            atRiskNames = atRisk,
            topAchieverNames = topAchievers
        )
    }
}
