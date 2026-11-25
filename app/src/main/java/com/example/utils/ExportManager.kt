package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.model.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

enum class CertificateTemplateStyle(val id: String, val titleAr: String) {
    ROYAL_TURQUOISE("royal_turquoise", "الإطار الملكي التركوازي"),
    CLASSIC_GOLD("classic_gold", "الوسام الذهبي الكلاسيكي"),
    ACADEMIC_LIGHT("academic_light", "الأكاديمي الأبيض الفاخر"),
    EMERALD_HONOR("emerald_honor", "درع التفوق الزمردي")
}

object ExportManager {

    private fun exportsDir(context: Context): File =
        File(context.filesDir, "exports").apply { mkdirs() }

    fun shareText(context: Context, title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة عبر"))
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun subjectDisplayName(
        profile: TeacherProfile,
        ledgerSubject: LedgerSubject,
        ledgerId: String? = null,
        ledgerTitle: String? = null
    ): String {
        if (!ledgerTitle.isNullOrBlank()) return ledgerTitle
        if (!ledgerId.isNullOrBlank()) {
            profile.effectiveLedgers().find { it.id == ledgerId }?.let { return it.title }
        }
        return if (ledgerSubject == LedgerSubject.MATH) "الرياضيات" else profile.subjectName.ifBlank { profile.subjectSpecialty.titleAr }
    }

    fun buildStudentShareText(
        student: Student,
        profile: TeacherProfile,
        settings: GradebookSettings,
        ledgerSubject: LedgerSubject = LedgerSubject.GENERAL,
        ledgerId: String? = null,
        ledgerTitle: String? = null
    ): String {
        val targetId = ledgerId ?: ledgerSubject.id
        val res = GradeCalculator.computeForLedger(student, settings, targetId)
        val g = student.gradesForLedger(targetId)
        val r = settings.enableSmartRounding
        val teacherDisplay = profile.teacherName.ifBlank { "مدرس المادة" }
        val subjDisplay = subjectDisplayName(profile, ledgerSubject, targetId, ledgerTitle)
        val h = profile.effectiveLedgers().find { it.id == targetId }?.customHeaders ?: settings.headers
        return buildString {
            appendLine("📋 بطاقة درجات الطالب ($subjDisplay)")
            appendLine("المدرسة: ${profile.schoolName}")
            appendLine("المادة: $subjDisplay | المدرس: $teacherDisplay")
            appendLine("العام الدراسي: ${profile.academicYear}")
            appendLine("━━━━━━━━━━━━━━")
            appendLine("الطالب: ${student.fullName}")
            appendLine("الصف: ${student.gradeLevel.titleAr} - ${student.section.titleAr}")
            appendLine("• ${h.t1M1Header}: ${GradeCalculator.formatScore(res.term1Month1Effective, r)}")
            if (settings.monthsPerTerm >= 2) {
                appendLine("• ${h.t1M2Header}: ${GradeCalculator.formatScore(res.term1Month2Effective, r)}")
            }
            if (settings.monthsPerTerm >= 3) {
                appendLine("• ${h.t1M3Header}: ${GradeCalculator.formatScore(res.term1Month3Effective, r)}")
            }
            appendLine("• ${h.t1AvgHeader}: ${GradeCalculator.formatScore(res.term1Average, r)}")
            appendLine("• ${h.midYearHeader}: ${GradeCalculator.formatScore(res.midYearEffective, r)}")
            appendLine("• ${h.t2M1Header}: ${GradeCalculator.formatScore(res.term2Month1Effective, r)}")
            if (settings.monthsPerTerm >= 2) {
                appendLine("• ${h.t2M2Header}: ${GradeCalculator.formatScore(res.term2Month2Effective, r)}")
            }
            if (settings.monthsPerTerm >= 3) {
                appendLine("• ${h.t2M3Header}: ${GradeCalculator.formatScore(res.term2Month3Effective, r)}")
            }
            appendLine("• ${h.t2AvgHeader}: ${GradeCalculator.formatScore(res.term2Average, r)}")
            appendLine("━━━━━━━━━━━━━━")
            appendLine("⭐ ${h.annualEffortHeader}: ${GradeCalculator.formatScore(res.annualEffort, r)}")
            appendLine("📝 ${h.finalExamHeader}: ${GradeCalculator.formatScore(g.finalExam, r)}")
            appendLine("🏆 ${h.finalGradeHeader}: ${GradeCalculator.formatScore(res.finalGrade, r)}")
            if (GradeCalculator.isExemptEligible(student, res, settings)) {
                appendLine("🌟 الحالة: معفو (${settings.exemptionThreshold}+)")
            }
            if (student.notes.isNotBlank()) {
                appendLine("ملاحظات: ${student.notes}")
            }
            appendLine("مع تحيات الأستاذ: $teacherDisplay")
        }
    }

    fun buildStudentDualShareText(
        student: Student,
        profile: TeacherProfile,
        settings: GradebookSettings
    ): String {
        val r = settings.enableSmartRounding
        val h = settings.headers
        val genRes = GradeCalculator.compute(student, settings, LedgerSubject.GENERAL)
        val mathRes = GradeCalculator.compute(student, settings, LedgerSubject.MATH)
        val genSubj = subjectDisplayName(profile, LedgerSubject.GENERAL)
        val teacherDisplay = profile.teacherName.ifBlank { "مدرس المادة" }
        return buildString {
            appendLine("📋 بطاقة درجات الطالب (المادتان)")
            appendLine("الطالب: ${student.fullName}")
            appendLine("الصف: ${student.gradeLevel.titleAr} - ${student.section.titleAr}")
            appendLine("━━━━━━━━━━━━━━")
            appendLine("🔹 $genSubj: ف1=${GradeCalculator.formatScore(genRes.term1Average, r)} | نصف=${GradeCalculator.formatScore(genRes.midYearEffective, r)} | ف2=${GradeCalculator.formatScore(genRes.term2Average, r)} | ${h.annualEffortHeader}=${GradeCalculator.formatScore(genRes.annualEffort, r)} | ${h.finalGradeHeader}=${GradeCalculator.formatScore(genRes.finalGrade, r)}")
            appendLine("🔸 الرياضيات: ف1=${GradeCalculator.formatScore(mathRes.term1Average, r)} | نصف=${GradeCalculator.formatScore(mathRes.midYearEffective, r)} | ف2=${GradeCalculator.formatScore(mathRes.term2Average, r)} | ${h.annualEffortHeader}=${GradeCalculator.formatScore(mathRes.annualEffort, r)} | ${h.finalGradeHeader}=${GradeCalculator.formatScore(mathRes.finalGrade, r)}")
            appendLine("━━━━━━━━━━━━━━")
            appendLine("مع تحيات الأستاذ: $teacherDisplay")
        }
    }

    fun buildSectionShareText(
        grade: GradeLevel,
        section: SectionLetter,
        students: List<Student>,
        profile: TeacherProfile,
        settings: GradebookSettings,
        ledgerSubject: LedgerSubject = LedgerSubject.GENERAL,
        ledgerId: String? = null,
        ledgerTitle: String? = null
    ): String {
        val targetId = ledgerId ?: ledgerSubject.id
        val teacherDisplay = profile.teacherName.ifBlank { "مدرس المادة" }
        val subjDisplay = subjectDisplayName(profile, ledgerSubject, targetId, ledgerTitle)
        val r = settings.enableSmartRounding
        val h = profile.effectiveLedgers().find { it.id == targetId }?.customHeaders ?: settings.headers
        return buildString {
            appendLine("📚 سجل درجات ($subjDisplay) - ${grade.titleAr} ${section.titleAr}")
            appendLine("المدرسة: ${profile.schoolName} | المادة: $subjDisplay")
            appendLine("المدرس: $teacherDisplay | العام: ${profile.academicYear}")
            appendLine("════════════════════")
            students.forEachIndexed { idx, st ->
                val res = GradeCalculator.computeForLedger(st, settings, targetId)
                val f1 = GradeCalculator.formatScore(res.term1Average, r)
                val mid = GradeCalculator.formatScore(res.midYearEffective, r)
                val f2 = GradeCalculator.formatScore(res.term2Average, r)
                val effort = GradeCalculator.formatScore(res.annualEffort, r)
                val fin = GradeCalculator.formatScore(res.finalGrade, r)
                val exemptTag = if (GradeCalculator.isExemptEligible(st, res, settings)) " [معفو]" else ""
                appendLine("${idx + 1}. ${st.fullName}$exemptTag")
                appendLine("   ${h.t1AvgHeader}: $f1 | ${h.midYearHeader}: $mid | ${h.t2AvgHeader}: $f2 | ${h.annualEffortHeader}: $effort | ${h.finalGradeHeader}: $fin")
            }
            appendLine("════════════════════")
            appendLine("تم الإصدار بواسطة: أ. $teacherDisplay")
        }
    }

    fun generateSectionPdf(
        context: Context,
        grade: GradeLevel,
        section: SectionLetter,
        students: List<Student>,
        profile: TeacherProfile,
        settings: GradebookSettings,
        ledgerSubject: LedgerSubject = LedgerSubject.GENERAL,
        ledgerId: String? = null,
        ledgerTitle: String? = null
    ): File {
        val targetId = ledgerId ?: ledgerSubject.id
        val doc = PdfDocument()
        val pageWidth = 842
        val pageHeight = 595
        val rowsPerPage = 16
        val chunks = if (students.isEmpty()) listOf(emptyList()) else students.chunked(rowsPerPage)
        val teacherDisplay = profile.teacherName.ifBlank { "مدرس المادة" }
        val subjDisplay = subjectDisplayName(profile, ledgerSubject, targetId, ledgerTitle)
        val isMath = ledgerSubject == LedgerSubject.MATH
        val r = settings.enableSmartRounding
        val h = profile.effectiveLedgers().find { it.id == targetId }?.customHeaders ?: settings.headers

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(if (isMath) "#2D1B2E" else "#102030")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 12f
            textAlign = Paint.Align.RIGHT
        }
        val headerBg = Paint().apply { color = Color.parseColor(if (isMath) "#2D1B2E" else "#102030") }
        val headerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(if (isMath) "#FF9F43" else "#40D6B4")
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val cellText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 10.5f
            textAlign = Paint.Align.CENTER
        }
        val nameText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 10.5f
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
        }
        val altRowPaint = Paint().apply { color = Color.parseColor(if (isMath) "#FFF7ED" else "#F1F5F9") }

        chunks.forEachIndexed { pageIdx, chunk ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIdx + 1).create()
            val page = doc.startPage(pageInfo)
            val c = page.canvas
            c.drawColor(Color.WHITE)

            c.drawText("سجل درجات ($subjDisplay) - ${grade.titleAr} ${section.titleAr}", pageWidth - 32f, 36f, titlePaint)
            c.drawText(
                "المدرس: $teacherDisplay  |  المادة: $subjDisplay  |  ${profile.schoolName}  |  صفحة ${pageIdx + 1}/${chunks.size}",
                pageWidth - 32f,
                56f,
                subPaint
            )

            val colWidths = floatArrayOf(35f, 185f, 52f, 52f, 55f, 60f, 52f, 52f, 55f, 65f, 60f, 60f)
            val colTitles = arrayOf(
                h.seqHeader, h.nameHeader, h.t1M1Header, h.t1M2Header, h.t1AvgHeader,
                h.midYearHeader, h.t2M1Header, h.t2M2Header, h.t2AvgHeader,
                h.annualEffortHeader, h.finalExamHeader, h.finalGradeHeader
            )

            var top = 74f
            val headerHeight = 28f
            c.drawRect(32f, top, pageWidth - 32f, top + headerHeight, headerBg)

            var curRight = pageWidth - 32f
            colWidths.forEachIndexed { idx, w ->
                val centerX = curRight - w / 2f
                c.drawText(colTitles[idx], centerX, top + 18f, headerText)
                curRight -= w
            }
            top += headerHeight

            val rowHeight = 28f
            chunk.forEachIndexed { rowIdx, st ->
                if (rowIdx % 2 == 1) {
                    c.drawRect(32f, top, pageWidth - 32f, top + rowHeight, altRowPaint)
                }
                val res = GradeCalculator.computeForLedger(st, settings, targetId)
                val g = st.gradesForLedger(targetId)
                val vals = arrayOf(
                    st.orderNumber.toString(),
                    st.fullName,
                    GradeCalculator.formatScore(res.term1Month1Effective, r),
                    GradeCalculator.formatScore(res.term1Month2Effective, r),
                    GradeCalculator.formatScore(res.term1Average, r),
                    GradeCalculator.formatScore(res.midYearEffective, r),
                    GradeCalculator.formatScore(res.term2Month1Effective, r),
                    GradeCalculator.formatScore(res.term2Month2Effective, r),
                    GradeCalculator.formatScore(res.term2Average, r),
                    GradeCalculator.formatScore(res.annualEffort, r),
                    GradeCalculator.formatScore(g.finalExam, r),
                    GradeCalculator.formatScore(res.finalGrade, r)
                )

                var rEdge = pageWidth - 32f
                colWidths.forEachIndexed { idx, w ->
                    if (idx == 1) {
                        c.drawText(vals[idx].take(28), rEdge - 6f, top + 18f, nameText)
                    } else {
                        c.drawText(vals[idx], rEdge - w / 2f, top + 18f, cellText)
                    }
                    c.drawLine(rEdge - w, top, rEdge - w, top + rowHeight, linePaint)
                    rEdge -= w
                }
                c.drawLine(32f, top + rowHeight, pageWidth - 32f, top + rowHeight, linePaint)
                top += rowHeight
            }
            doc.finishPage(page)
        }

        val outFile = File(exportsDir(context), "grades_${targetId}_${grade.id}_${section.id}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    fun generateSectionWordDoc(
        context: Context,
        grade: GradeLevel,
        section: SectionLetter,
        students: List<Student>,
        profile: TeacherProfile,
        settings: GradebookSettings,
        ledgerSubject: LedgerSubject = LedgerSubject.GENERAL,
        ledgerId: String? = null,
        ledgerTitle: String? = null
    ): File {
        val targetId = ledgerId ?: ledgerSubject.id
        val teacherDisplay = profile.teacherName.ifBlank { "مدرس المادة" }
        val subjDisplay = subjectDisplayName(profile, ledgerSubject, targetId, ledgerTitle)
        val isMath = ledgerSubject == LedgerSubject.MATH
        val thBg = if (isMath) "#2D1B2E" else "#102030"
        val thColor = if (isMath) "#FF9F43" else "#40D6B4"
        val r = settings.enableSmartRounding
        val h = profile.effectiveLedgers().find { it.id == targetId }?.customHeaders ?: settings.headers
        val html = buildString {
            appendLine("<html dir='rtl' lang='ar'><head><meta charset='UTF-8'>")
            appendLine("<style>body{font-family:Arial,sans-serif;direction:rtl;} table{width:100%;border-collapse:collapse;} th{background:$thBg;color:$thColor;padding:6px;border:1px solid #334155;} td{padding:6px;border:1px solid #94A3B8;text-align:center;} td.name{text-align:right;}</style>")
            appendLine("</head><body>")
            appendLine("<h2>سجل درجات ($subjDisplay) - ${grade.titleAr} ${section.titleAr}</h2>")
            appendLine("<p>المدرسة: ${profile.schoolName} | المادة: $subjDisplay | المدرس: $teacherDisplay | العام الدراسي: ${profile.academicYear}</p>")
            appendLine("<table><tr>")
            listOf(
                h.seqHeader, h.nameHeader, h.t1M1Header, h.t1M2Header, h.t1AvgHeader,
                h.midYearHeader, h.t2M1Header, h.t2M2Header, h.t2AvgHeader,
                h.annualEffortHeader, h.finalExamHeader, h.finalGradeHeader
            ).forEach { append("<th>$it</th>") }
            appendLine("</tr>")
            students.forEach { st ->
                val res = GradeCalculator.computeForLedger(st, settings, targetId)
                val g = st.gradesForLedger(targetId)
                appendLine("<tr>")
                append("<td>${st.orderNumber}</td>")
                append("<td class='name'>${st.fullName}</td>")
                append("<td>${GradeCalculator.formatScore(res.term1Month1Effective, r)}</td>")
                append("<td>${GradeCalculator.formatScore(res.term1Month2Effective, r)}</td>")
                append("<td><b>${GradeCalculator.formatScore(res.term1Average, r)}</b></td>")
                append("<td>${GradeCalculator.formatScore(res.midYearEffective, r)}</td>")
                append("<td>${GradeCalculator.formatScore(res.term2Month1Effective, r)}</td>")
                append("<td>${GradeCalculator.formatScore(res.term2Month2Effective, r)}</td>")
                append("<td><b>${GradeCalculator.formatScore(res.term2Average, r)}</b></td>")
                append("<td><b>${GradeCalculator.formatScore(res.annualEffort, r)}</b></td>")
                append("<td>${GradeCalculator.formatScore(g.finalExam, r)}</td>")
                append("<td><b>${GradeCalculator.formatScore(res.finalGrade, r)}</b></td>")
                appendLine("</tr>")
            }
            appendLine("</table></body></html>")
        }
        val outFile = File(exportsDir(context), "grades_${targetId}_${grade.id}_${section.id}.doc")
        outFile.writeText(html, Charsets.UTF_8)
        return outFile
    }

    fun generateSectionImage(
        context: Context,
        grade: GradeLevel,
        section: SectionLetter,
        students: List<Student>,
        profile: TeacherProfile,
        settings: GradebookSettings,
        ledgerSubject: LedgerSubject = LedgerSubject.GENERAL,
        ledgerId: String? = null,
        ledgerTitle: String? = null
    ): File {
        val targetId = ledgerId ?: ledgerSubject.id
        val displayStudents = students.take(25)
        val width = 1200
        val height = 180 + (displayStudents.size + 1) * 46
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val isMath = ledgerSubject == LedgerSubject.MATH
        val subjDisplay = subjectDisplayName(profile, ledgerSubject, targetId, ledgerTitle)
        val bgHex = if (isMath) "#211524" else "#102030"
        val headerHex = if (isMath) "#2D1B2E" else "#1B344D"
        val altHex = if (isMath) "#2C1D30" else "#15293D"
        val accentHex = if (isMath) "#FF9F43" else "#40D6B4"

        c.drawColor(Color.parseColor(bgHex))

        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(accentHex)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }
        val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            textAlign = Paint.Align.RIGHT
        }
        val teacherDisplay = profile.teacherName.ifBlank { "مدرس المادة" }
        c.drawText("سجل درجات ($subjDisplay) - ${grade.titleAr} ${section.titleAr}", width - 40f, 55f, titleP)
        c.drawText("المدرس: $teacherDisplay  |  $subjDisplay  |  ${profile.schoolName}", width - 40f, 95f, subP)

        val r = settings.enableSmartRounding
        val h = profile.effectiveLedgers().find { it.id == targetId }?.customHeaders ?: settings.headers
        val headerBox = Paint().apply { color = Color.parseColor(headerHex) }
        val rowAlt = Paint().apply { color = Color.parseColor(altHex) }
        val textCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }
        val accentCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(accentHex)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val textRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            textAlign = Paint.Align.RIGHT
        }

        var y = 130f
        c.drawRect(30f, y, width - 30f, y + 44f, headerBox)
        c.drawText(h.seqHeader, width - 60f, y + 29f, accentCenter)
        c.drawText(h.nameHeader, width - 110f, y + 29f, textRight.apply { color = Color.parseColor(accentHex) })
        textRight.color = Color.WHITE
        val colsX = floatArrayOf(710f, 600f, 490f, 380f, 260f, 120f)
        val colNames = arrayOf(h.t1AvgHeader, h.midYearHeader, h.t2AvgHeader, h.annualEffortHeader, h.finalExamHeader, h.finalGradeHeader)
        colsX.forEachIndexed { idx, x -> c.drawText(colNames[idx], x, y + 29f, accentCenter) }
        y += 46f

        displayStudents.forEachIndexed { idx, st ->
            if (idx % 2 == 1) c.drawRect(30f, y, width - 30f, y + 44f, rowAlt)
            val res = GradeCalculator.computeForLedger(st, settings, targetId)
            val g = st.gradesForLedger(targetId)
            c.drawText(st.orderNumber.toString(), width - 60f, y + 29f, textCenter)
            c.drawText(st.fullName.take(26), width - 110f, y + 29f, textRight)
            c.drawText(GradeCalculator.formatScore(res.term1Average, r), colsX[0], y + 29f, textCenter)
            c.drawText(GradeCalculator.formatScore(res.midYearEffective, r), colsX[1], y + 29f, textCenter)
            c.drawText(GradeCalculator.formatScore(res.term2Average, r), colsX[2], y + 29f, textCenter)
            c.drawText(GradeCalculator.formatScore(res.annualEffort, r), colsX[3], y + 29f, accentCenter)
            c.drawText(GradeCalculator.formatScore(g.finalExam, r), colsX[4], y + 29f, textCenter)
            c.drawText(GradeCalculator.formatScore(res.finalGrade, r), colsX[5], y + 29f, accentCenter)
            y += 46f
        }

        val outFile = File(exportsDir(context), "grades_${targetId}_${grade.id}_${section.id}.png")
        FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return outFile
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 2
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.parseColor("#102030") else Color.WHITE)
            }
        }
        return bmp
    }

    fun generateCertificateBitmap(
        recipientName: String,
        bodyText: String,
        teacherName: String,
        schoolName: String,
        subjectName: String,
        dateStr: String,
        badgeTitle: String,
        bgColorInt: Int,
        accentColorInt: Int,
        isLightCard: Boolean = false
    ): Bitmap {
        val w = 1480
        val h = 1040
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bgColorInt)

        val primaryTextInt = if (isLightCard) Color.parseColor("#102030") else Color.WHITE
        val bodyTextInt = if (isLightCard) Color.parseColor("#334155") else Color.parseColor("#E2E8F0")

        // Subtle radial glow at top-center
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w / 2f, h * 0.25f, w * 0.45f,
                Color.argb(38, Color.red(accentColorInt), Color.green(accentColorInt), Color.blue(accentColorInt)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), glowPaint)

        // Outer & inner ornamental borders
        val borderOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 18f
            color = accentColorInt
        }
        val borderInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = primaryTextInt
            alpha = 180
        }
        c.drawRect(36f, 36f, w - 36f, h - 36f, borderOuter)
        c.drawRect(64f, 64f, w - 64f, h - 64f, borderInner)

        // Corner ornamental diamonds
        val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColorInt
            style = Paint.Style.FILL
        }
        val corners = listOf(64f to 64f, (w - 64f) to 64f, 64f to (h - 64f), (w - 64f) to (h - 64f))
        corners.forEach { (cx, cy) ->
            val p = Path().apply {
                moveTo(cx, cy - 18f)
                lineTo(cx + 18f, cy)
                lineTo(cx, cy + 18f)
                lineTo(cx - 18f, cy)
                close()
            }
            c.drawPath(p, cornerPaint)
        }

        // Top Seal Emblem
        val sealRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = accentColorInt
        }
        c.drawCircle(w / 2f, 138f, 44f, sealRing)
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColorInt
            textSize = 44f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText("★", w / 2f, 154f, starPaint)

        val schoolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryTextInt
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColorInt
            textSize = 70f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColorInt
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryTextInt
            textSize = 54f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        c.drawText("$schoolName  •  مادة $subjectName", w / 2f, 224f, schoolPaint)
        c.drawText("شهادة شكر وتقدير", w / 2f, 308f, headerPaint)
        if (badgeTitle.isNotBlank()) {
            c.drawText("« $badgeTitle »", w / 2f, 362f, badgePaint)
        }
        c.drawText("تُمنح إلى: $recipientName", w / 2f, 438f, namePaint)

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColorInt
            strokeWidth = 3f
            alpha = 190
        }
        c.drawLine(w * 0.25f, 466f, w * 0.75f, 466f, dividerPaint)

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bodyTextInt
            textSize = 33f
        }
        val staticLayout = StaticLayout.Builder
            .obtain(bodyText, 0, bodyText.length, bodyPaint, w - 280)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(14f, 1.18f)
            .build()
        c.save()
        c.translate(140f, 505f)
        staticLayout.draw(c)
        c.restore()

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColorInt
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryTextInt
            textSize = 28f
            textAlign = Paint.Align.RIGHT
        }
        val tName = teacherName.ifBlank { "مدرس المادة" }
        c.drawText("التاريخ: $dateStr", w - 120f, h - 120f, datePaint)
        c.drawText("توقيع المدرس: أ. $tName", 120f, h - 120f, footerPaint)
        return bmp
    }

    fun generateCertificatePdf(context: Context, bmp: Bitmap, fileName: String): File {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, 1).create()
        val page = doc.startPage(pageInfo)
        page.canvas.drawBitmap(bmp, 0f, 0f, null)
        doc.finishPage(page)
        val outFile = File(exportsDir(context), fileName)
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    fun saveBitmapPng(context: Context, bitmap: Bitmap, fileName: String): File {
        val outFile = File(exportsDir(context), fileName)
        FileOutputStream(outFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return outFile
    }

    data class FolderExportResult(
        val success: Boolean = true,
        val savedLocationDisplay: String = "",
        val file: File? = null,
        val message: String = savedLocationDisplay
    )

    private fun saveToRafiqDownloadsFolder(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): FolderExportResult {
        val folderName = "رفيق المدرس"
        val internalOut = File(exportsDir(context), fileName)
        return runCatching {
            internalOut.writeBytes(bytes)

            val displayLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$folderName")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out -> out.write(bytes) }
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                        "التنزيلات / $folderName / $fileName"
                    } else {
                        internalOut.absolutePath
                    }
                }.getOrElse { internalOut.absolutePath }
            } else {
                runCatching {
                    val appExtDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), folderName).apply { mkdirs() }
                    val extFile = File(appExtDir, fileName)
                    extFile.writeBytes(bytes)
                    "$folderName/${fileName}"
                }.getOrElse { internalOut.absolutePath }
            }
            FolderExportResult(
                success = true,
                savedLocationDisplay = displayLocation,
                file = internalOut,
                message = "تم حفظ الملف بنجاح في مجلد ($folderName): $displayLocation"
            )
        }.getOrElse { err ->
            FolderExportResult(
                success = false,
                savedLocationDisplay = "",
                file = null,
                message = err.localizedMessage ?: "تعذر حفظ الملف"
            )
        }
    }

    private fun xmlEscape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    fun buildExcelSpreadsheetXml(
        state: AppState,
        grade: GradeLevel?,
        section: SectionLetter?,
        ledgerId: String
    ): String {
        val settings = state.gradebookSettings
        val profile = state.profile
        val r = settings.enableSmartRounding
        val targetStudents = state.students.filter { st ->
            (grade == null || st.gradeLevel == grade) &&
                (section == null || st.section == section) &&
                (!settings.hideTransferredStudents || !st.isTransferred)
        }.sortedWith(compareBy({ it.gradeLevel.ordinal }, { it.section.id }, { it.orderNumber }))

        val activeLedgerDef = profile.effectiveLedgers().find { it.id == ledgerId }
        val subjectLabel = activeLedgerDef?.title ?: profile.subjectName.ifBlank { profile.subjectSpecialty.titleAr }
        val h = activeLedgerDef?.customHeaders ?: settings.headers
        val sectionStats = GradeCalculator.computeSectionStats(
            students = targetStudents,
            settings = settings,
            ledgerSubject = LedgerSubject.GENERAL,
            ledgerId = ledgerId
        )
        val comprehensive = GradeCalculator.computeComprehensiveAnalytics(
            students = targetStudents,
            settings = settings,
            absenceCountMap = state.absences.groupingBy { it.studentId }.eachCount(),
            ledgerId = ledgerId
        )
        val referenceScores = targetStudents.mapNotNull { st ->
            GradeCalculator.referenceScore(GradeCalculator.computeForLedger(st, settings, ledgerId))
        }
        val highestScore = referenceScores.maxOrNull()
        val lowestScore = referenceScores.minOrNull()
        val exemptionRatePercent = if (sectionStats.totalStudents > 0) {
            (comprehensive.exemptStudents.size * 100) / sectionStats.totalStudents
        } else 0

        fun cellStr(value: String, styleId: String = "sDefault"): String =
            "<Cell ss:StyleID=\"$styleId\"><Data ss:Type=\"String\">${xmlEscape(value)}</Data></Cell>"

        fun cellNum(value: Double?, styleId: String = "sNum"): String =
            if (value == null) "<Cell ss:StyleID=\"$styleId\"><Data ss:Type=\"String\">—</Data></Cell>"
            else "<Cell ss:StyleID=\"$styleId\"><Data ss:Type=\"Number\">${GradeCalculator.formatScore(value, r)}</Data></Cell>"

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<?mso-application progid="Excel.Sheet"?>""")
            appendLine("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">""")
            appendLine("""<Styles>""")
            appendLine("""<Style ss:ID="sHeader"><Font ss:Bold="1" ss:Color="#FFFFFF"/><Interior ss:Color="#0F4C81" ss:Pattern="Solid"/><Alignment ss:Horizontal="Center" ss:Vertical="Center"/></Style>""")
            appendLine("""<Style ss:ID="sTitle"><Font ss:Bold="1" ss:Size="14" ss:Color="#0F4C81"/><Alignment ss:Horizontal="Center"/></Style>""")
            appendLine("""<Style ss:ID="sDefault"><Alignment ss:Horizontal="Right" ss:Vertical="Center"/></Style>""")
            appendLine("""<Style ss:ID="sNum"><Alignment ss:Horizontal="Center" ss:Vertical="Center"/></Style>""")
            appendLine("""<Style ss:ID="sPass"><Font ss:Bold="1" ss:Color="#137333"/><Alignment ss:Horizontal="Center"/></Style>""")
            appendLine("""<Style ss:ID="sFail"><Font ss:Bold="1" ss:Color="#C5221F"/><Alignment ss:Horizontal="Center"/></Style>""")
            appendLine("""</Styles>""")

            // Sheet 1: Full Grades
            appendLine("""<Worksheet ss:Name="سجل الدرجات" ss:RightToLeft="1"><Table>""")
            appendLine("<Row>${cellStr("سجل الدرجات الرسمي — $subjectLabel — ${profile.schoolName}", "sTitle")}</Row>")
            appendLine("<Row>${cellStr("المدرس: ${profile.teacherName.ifBlank { "—" }} | العام: ${profile.academicYear}")}</Row>")
            appendLine(
                "<Row>" +
                    cellStr(h.seqHeader, "sHeader") +
                    cellStr(h.nameHeader, "sHeader") +
                    cellStr("الصف والشعبة", "sHeader") +
                    cellStr(h.t1M1Header, "sHeader") +
                    cellStr(h.t1M2Header, "sHeader") +
                    cellStr(h.t1DailyHeader, "sHeader") +
                    cellStr(h.t1AvgHeader, "sHeader") +
                    cellStr(h.midYearHeader, "sHeader") +
                    cellStr(h.t2M1Header, "sHeader") +
                    cellStr(h.t2M2Header, "sHeader") +
                    cellStr(h.t2DailyHeader, "sHeader") +
                    cellStr(h.t2AvgHeader, "sHeader") +
                    cellStr(h.annualEffortHeader, "sHeader") +
                    cellStr(h.finalExamHeader, "sHeader") +
                    cellStr(h.finalGradeHeader, "sHeader") +
                    cellStr("حالة الإعفاء / التأهيل", "sHeader") +
                    cellStr("نقاط اليومي", "sHeader") +
                    cellStr("الملاحظات", "sHeader") +
                    "</Row>"
            )
            targetStudents.forEachIndexed { idx, st ->
                val g = st.gradesForLedger(ledgerId)
                val res = GradeCalculator.computeForLedger(st, settings, ledgerId)
                val statusLabel = when {
                    st.gradeLevel == GradeLevel.THIRD && (res.annualEffort ?: 0.0) >= 50.0 -> "مؤهل للوزاري"
                    GradeCalculator.isExemptEligible(st, res, settings) -> "معفى (${settings.exemptionThreshold}+)"
                    (res.finalGrade ?: res.annualEffort ?: 0.0) >= settings.passingScore -> "ناجح"
                    else -> "متابع"
                }
                val statusStyle = if ((res.finalGrade ?: res.annualEffort ?: 0.0) >= settings.passingScore) "sPass" else "sFail"
                val dailyPts = st.participationPoints + st.homeworkPoints + st.behaviorPoints
                appendLine(
                    "<Row>" +
                        cellStr("${idx + 1}", "sNum") +
                        cellStr(st.fullName) +
                        cellStr("${st.gradeLevel.shortAr} ${st.section.id}", "sNum") +
                        cellNum(g.term1Month1) +
                        cellNum(g.term1Month2) +
                        cellNum(g.term1Daily) +
                        cellNum(res.term1Average) +
                        cellNum(res.midYearEffective) +
                        cellNum(g.term2Month1) +
                        cellNum(g.term2Month2) +
                        cellNum(g.term2Daily) +
                        cellNum(res.term2Average) +
                        cellNum(res.annualEffort) +
                        cellNum(g.finalExam) +
                        cellNum(res.finalGrade) +
                        cellStr(statusLabel, statusStyle) +
                        cellStr("$dailyPts", "sNum") +
                        cellStr(st.notes) +
                        "</Row>"
                )
            }
            appendLine("</Table></Worksheet>")

            // Sheet 2: Exemptions & Ministerial Eligibility
            appendLine("""<Worksheet ss:Name="الإعفاء والوزاري" ss:RightToLeft="1"><Table>""")
            appendLine("<Row>${cellStr("كشف المعفين والمؤهلين لدخول الامتحان الوزاري", "sTitle")}</Row>")
            appendLine("<Row>${cellStr("الطالب", "sHeader")}${cellStr("الصف", "sHeader")}${cellStr("السعي السنوي", "sHeader")}${cellStr("التصنيف", "sHeader")}</Row>")
            targetStudents.forEach { st ->
                val res = GradeCalculator.computeForLedger(st, settings, ledgerId)
                val exempt = GradeCalculator.isExemptEligible(st, res, settings)
                val minQual = st.gradeLevel == GradeLevel.THIRD && (res.annualEffort ?: 0.0) >= 50.0
                if (exempt || minQual) {
                    appendLine(
                        "<Row>" +
                            cellStr(st.fullName) +
                            cellStr("${st.gradeLevel.titleAr} - ${st.section.id}") +
                            cellNum(res.annualEffort) +
                            cellStr(if (minQual) "مؤهل لدخول الوزاري" else "إعفاء عام (${settings.exemptionThreshold}+)", "sPass") +
                            "</Row>"
                    )
                }
            }
            appendLine("</Table></Worksheet>")

            // Sheet 3: Analytics Summary
            appendLine("""<Worksheet ss:Name="التحليل والإحصائيات" ss:RightToLeft="1"><Table>""")
            appendLine("<Row>${cellStr("الملخص الإحصائي العام", "sTitle")}</Row>")
            appendLine("<Row>${cellStr("المؤشر", "sHeader")}${cellStr("القيمة", "sHeader")}</Row>")
            appendLine("<Row>${cellStr("عدد الطلبة")}${cellStr("${sectionStats.totalStudents}", "sNum")}</Row>")
            appendLine("<Row>${cellStr("المتوسط العام")}${cellStr(String.format(java.util.Locale.US, "%.1f", sectionStats.averageScore), "sNum")}</Row>")
            appendLine("<Row>${cellStr("نسبة النجاح")}${cellStr("${sectionStats.passRatePercent.toInt()}%", "sNum")}</Row>")
            appendLine("<Row>${cellStr("نسبة الإعفاء")}${cellStr("$exemptionRatePercent%", "sNum")}</Row>")
            appendLine("<Row>${cellStr("أعلى درجة")}${cellStr(GradeCalculator.formatScore(highestScore, r), "sNum")}</Row>")
            appendLine("<Row>${cellStr("أدنى درجة")}${cellStr(GradeCalculator.formatScore(lowestScore, r), "sNum")}</Row>")
            appendLine("</Table></Worksheet>")
            appendLine("</Workbook>")
        }
    }

    fun buildWordHtmlDocument(
        state: AppState,
        grade: GradeLevel?,
        section: SectionLetter?,
        ledgerId: String
    ): String {
        val settings = state.gradebookSettings
        val profile = state.profile
        val r = settings.enableSmartRounding
        val targetStudents = state.students.filter { st ->
            (grade == null || st.gradeLevel == grade) &&
                (section == null || st.section == section) &&
                (!settings.hideTransferredStudents || !st.isTransferred)
        }.sortedWith(compareBy({ it.gradeLevel.ordinal }, { it.section.id }, { it.orderNumber }))

        val activeLedgerDef = profile.effectiveLedgers().find { it.id == ledgerId }
        val subjectLabel = activeLedgerDef?.title ?: profile.subjectName.ifBlank { profile.subjectSpecialty.titleAr }
        val h = activeLedgerDef?.customHeaders ?: settings.headers
        val sectionStats = GradeCalculator.computeSectionStats(
            students = targetStudents,
            settings = settings,
            ledgerSubject = LedgerSubject.GENERAL,
            ledgerId = ledgerId
        )
        val comprehensive = GradeCalculator.computeComprehensiveAnalytics(
            students = targetStudents,
            settings = settings,
            absenceCountMap = state.absences.groupingBy { it.studentId }.eachCount(),
            ledgerId = ledgerId
        )
        val exemptionRatePercent = if (sectionStats.totalStudents > 0) {
            (comprehensive.exemptStudents.size * 100) / sectionStats.totalStudents
        } else 0

        return buildString {
            appendLine("""<html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' dir='rtl' lang='ar'>""")
            appendLine("<head><meta charset='UTF-8'/><style>")
            appendLine("body{font-family:'Cairo','Arial',sans-serif;direction:rtl;color:#111827;margin:20px;}")
            appendLine("h1{color:#0f4c81;text-align:center;margin-bottom:4px;}")
            appendLine("h2{color:#0f4c81;border-bottom:2px solid #02c39a;padding-bottom:4px;margin-top:18px;}")
            appendLine("table{width:100%;border-collapse:collapse;margin-top:8px;font-size:12px;}")
            appendLine("th{background:#0f4c81;color:#ffffff;padding:6px;border:1px solid #cbd5e1;}")
            appendLine("td{padding:5px;border:1px solid #cbd5e1;text-align:center;}")
            appendLine(".right{text-align:right;}")
            appendLine("</style></head><body>")
            appendLine("<h1>تقرير الدرجات والإحصاء الشامل — ${xmlEscape(subjectLabel)}</h1>")
            appendLine("<p style='text-align:center;'>المدرسة: ${xmlEscape(profile.schoolName)} | المدرس: ${xmlEscape(profile.teacherName.ifBlank { "—" })} | العام الدراسي: ${xmlEscape(profile.academicYear)}</p>")

            appendLine("<h2>1) الملخص الإحصائي</h2>")
            appendLine("<table><tr><th>إجمالي الطلبة</th><th>المتوسط العام</th><th>نسبة النجاح</th><th>نسبة الإعفاء</th><th>الناجحون</th><th>الحالات الحرجة</th></tr>")
            appendLine("<tr><td>${sectionStats.totalStudents}</td><td>${String.format(java.util.Locale.US, "%.1f", sectionStats.averageScore)}</td><td>${sectionStats.passRatePercent.toInt()}%</td><td>$exemptionRatePercent%</td><td>${sectionStats.passingCount}</td><td>${comprehensive.criticalCases.size}</td></tr></table>")

            appendLine("<h2>2) سجل الدرجات التفصيلي</h2>")
            appendLine("<table><tr><th>ت</th><th>${xmlEscape(h.nameHeader)}</th><th>الصف</th><th>${xmlEscape(h.t1AvgHeader)}</th><th>${xmlEscape(h.midYearHeader)}</th><th>${xmlEscape(h.t2AvgHeader)}</th><th>${xmlEscape(h.annualEffortHeader)}</th><th>${xmlEscape(h.finalExamHeader)}</th><th>${xmlEscape(h.finalGradeHeader)}</th><th>الحالة</th></tr>")
            targetStudents.forEachIndexed { idx, st ->
                val g = st.gradesForLedger(ledgerId)
                val res = GradeCalculator.computeForLedger(st, settings, ledgerId)
                val statusLabel = when {
                    st.gradeLevel == GradeLevel.THIRD && (res.annualEffort ?: 0.0) >= 50.0 -> "مؤهل للوزاري"
                    GradeCalculator.isExemptEligible(st, res, settings) -> "معفى"
                    (res.finalGrade ?: res.annualEffort ?: 0.0) >= settings.passingScore -> "ناجح"
                    else -> "متابع"
                }
                appendLine(
                    "<tr><td>${idx + 1}</td><td class='right'>${xmlEscape(st.fullName)}</td><td>${st.gradeLevel.shortAr} ${st.section.id}</td>" +
                        "<td>${GradeCalculator.formatScore(res.term1Average, r)}</td><td>${GradeCalculator.formatScore(res.midYearEffective, r)}</td>" +
                        "<td>${GradeCalculator.formatScore(res.term2Average, r)}</td><td>${GradeCalculator.formatScore(res.annualEffort, r)}</td>" +
                        "<td>${GradeCalculator.formatScore(g.finalExam, r)}</td><td>${GradeCalculator.formatScore(res.finalGrade, r)}</td><td>$statusLabel</td></tr>"
                )
            }
            appendLine("</table></body></html>")
        }
    }

    fun exportFullGradesAndAnalyticsToExcel(
        context: Context,
        state: AppState,
        grade: GradeLevel?,
        section: SectionLetter?,
        ledgerId: String
    ): FolderExportResult {
        val xml = buildExcelSpreadsheetXml(state, grade, section, ledgerId)
        val scope = if (grade != null && section != null) "${grade.id}_${section.id}" else "all"
        val fileName = "سجل_${ledgerId}_${scope}_${System.currentTimeMillis()}.xls"
        return saveToRafiqDownloadsFolder(
            context = context,
            fileName = fileName,
            mimeType = "application/vnd.ms-excel",
            bytes = xml.toByteArray(Charsets.UTF_8)
        )
    }

    fun exportFullGradesAndAnalyticsToWord(
        context: Context,
        state: AppState,
        grade: GradeLevel?,
        section: SectionLetter?,
        ledgerId: String
    ): FolderExportResult {
        val html = buildWordHtmlDocument(state, grade, section, ledgerId)
        val scope = if (grade != null && section != null) "${grade.id}_${section.id}" else "all"
        val fileName = "تقرير_${ledgerId}_${scope}_${System.currentTimeMillis()}.doc"
        return saveToRafiqDownloadsFolder(
            context = context,
            fileName = fileName,
            mimeType = "application/msword",
            bytes = html.toByteArray(Charsets.UTF_8)
        )
    }
}
