package com.example.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.model.BankQuestion
import com.example.model.ExamSlotConfig
import com.example.model.GeneratedExamPaper
import com.example.model.GeneratedExamQuestionBlock
import com.example.model.QuestionChapter
import com.example.model.QuestionSectionDef
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlin.random.Random

object ExamGeneratorHelper {

    fun generateExamPaper(
        examTitle: String,
        subjectName: String,
        gradeTitle: String,
        examDate: String,
        examDuration: String,
        teacherName: String,
        schoolName: String,
        noteLine: String,
        selectedChapterIds: Set<String>,
        chapters: List<QuestionChapter>,
        sections: List<QuestionSectionDef>,
        allQuestions: List<BankQuestion>,
        slots: List<ExamSlotConfig>,
        seed: Long = System.currentTimeMillis()
    ): GeneratedExamPaper {
        val rng = Random(seed)
        val activeChapters = chapters.filter { it.id in selectedChapterIds }
            .ifEmpty { chapters }
        val chapterWeightMap = activeChapters.associate { it.id to it.gradeWeight.coerceAtLeast(1) }
        val usedQuestionIds = mutableSetOf<String>()

        val blocks = slots.sortedBy { it.questionNumber }.mapNotNull { slot ->
            val sec = sections.find { it.id == slot.sectionId } ?: return@mapNotNull null
            val heading = slot.customHeading.trim().ifBlank { sec.title }
            val countNeeded = slot.branchCount.coerceIn(1, 20)

            // Eligible questions in this section & selected chapters (not yet used)
            val candidatesByChapter = activeChapters.associate { ch ->
                ch.id to allQuestions
                    .filter { q ->
                        q.sectionId == sec.id &&
                            q.chapterId == ch.id &&
                            q.id !in usedQuestionIds
                    }
                    .shuffled(rng)
                    .toMutableList()
            }

            val picked = mutableListOf<BankQuestion>()
            val totalWeight = activeChapters.sumOf { ch ->
                if (candidatesByChapter[ch.id].orEmpty().isNotEmpty()) (chapterWeightMap[ch.id] ?: 1) else 0
            }.coerceAtLeast(1)

            // 1. Weighted quota allocation per chapter
            val quotas = activeChapters.map { ch ->
                val avail = candidatesByChapter[ch.id].orEmpty()
                if (avail.isEmpty()) ch.id to 0
                else {
                    val w = chapterWeightMap[ch.id] ?: 1
                    ch.id to ((w.toDouble() / totalWeight) * countNeeded).roundToInt()
                }
            }.toMap()

            for (ch in activeChapters.shuffled(rng)) {
                val list = candidatesByChapter[ch.id] ?: continue
                var qQuota = (quotas[ch.id] ?: 0).coerceAtMost(list.size)
                while (qQuota > 0 && picked.size < countNeeded && list.isNotEmpty()) {
                    val item = list.removeAt(0)
                    picked += item
                    usedQuestionIds += item.id
                    qQuota--
                }
            }

            // 2. Fill remaining needed branches proportionally using weighted roulette
            while (picked.size < countNeeded) {
                val nonEmptyChapters = activeChapters.filter { candidatesByChapter[it.id].orEmpty().isNotEmpty() }
                if (nonEmptyChapters.isEmpty()) break
                val chosenCh = pickWeightedChapter(nonEmptyChapters, chapterWeightMap, rng)
                val list = candidatesByChapter[chosenCh.id] ?: break
                if (list.isNotEmpty()) {
                    val item = list.removeAt(0)
                    picked += item
                    usedQuestionIds += item.id
                }
            }

            GeneratedExamQuestionBlock(
                questionNumber = slot.questionNumber,
                sectionTitle = heading,
                sectionColorHex = sec.colorHex,
                totalMarks = slot.totalMarks,
                branches = picked
            )
        }

        val totalMarks = slots.sumOf { it.totalMarks }
        return GeneratedExamPaper(
            examTitle = examTitle.ifBlank { "أسئلة امتحان" },
            subjectName = subjectName,
            gradeTitle = gradeTitle,
            examDate = examDate,
            examDuration = examDuration,
            teacherName = teacherName,
            schoolName = schoolName,
            noteLine = noteLine,
            totalMarks = totalMarks,
            blocks = blocks
        )
    }

    private fun pickWeightedChapter(
        chapters: List<QuestionChapter>,
        weightMap: Map<String, Int>,
        rng: Random
    ): QuestionChapter {
        val total = chapters.sumOf { (weightMap[it.id] ?: 1).coerceAtLeast(1) }
        var ticket = rng.nextInt(total.coerceAtLeast(1))
        for (ch in chapters) {
            val w = (weightMap[ch.id] ?: 1).coerceAtLeast(1)
            if (ticket < w) return ch
            ticket -= w
        }
        return chapters.first()
    }

    fun buildExamShareText(paper: GeneratedExamPaper, includeAnswers: Boolean = false): String {
        val sb = StringBuilder()
        sb.appendLine("بسم الله الرحمن الرحيم")
        sb.appendLine("${paper.schoolName} — ${paper.examTitle}")
        sb.appendLine("المادة: ${paper.subjectName} | الصف: ${paper.gradeTitle} | الدرجة: ${paper.totalMarks} | الزمن: ${paper.examDuration}")
        sb.appendLine("التاريخ: ${paper.examDate} | مدرس المادة: ${paper.teacherName}")
        if (paper.noteLine.isNotBlank()) sb.appendLine("ملاحظة: ${paper.noteLine}")
        sb.appendLine("======================================")
        val branchLetters = listOf("أ", "ب", "ج", "د", "هـ", "و", "ز", "ح", "ط", "ي")
        paper.blocks.forEach { block ->
            sb.appendLine("س${block.questionNumber} / ${block.sectionTitle} (${block.totalMarks} درجة):")
            if (block.branches.isEmpty()) {
                sb.appendLine("   (لم تتم إضافة أسئلة كافية لهذا القسم بعد في بنك الأسئلة)")
            } else {
                block.branches.forEachIndexed { idx, q ->
                    val prefix = branchLetters.getOrElse(idx) { "${idx + 1}" }
                    sb.appendLine("   $prefix- ${q.questionText}")
                    if (includeAnswers && q.answerText.isNotBlank()) {
                        sb.appendLine("      [الإجابة: ${q.answerText}]")
                    }
                }
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    fun generateExamPdf(
        context: Context,
        paper: GeneratedExamPaper,
        includeAnswers: Boolean = false
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "exam_paper_${System.currentTimeMillis()}.pdf")
        val doc = PdfDocument()

        val pageWidth = 595
        val pageHeight = 842
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas

        val headerBgPaint = Paint().apply { color = Color.parseColor("#0D1829") }
        val accentPaint = Paint().apply { color = Color.parseColor("#40D6B4"); strokeWidth = 2f }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subHeaderPaint = Paint().apply {
            color = Color.parseColor("#DCE7F3")
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        val bodyPaint = Paint().apply {
            color = Color.parseColor("#111827")
            textSize = 12f
            textAlign = Paint.Align.RIGHT
        }
        val ansPaint = Paint().apply {
            color = Color.parseColor("#0F766E")
            textSize = 10.5f
            textAlign = Paint.Align.RIGHT
        }
        val sectionTitlePaint = Paint().apply {
            color = Color.parseColor("#0D1829")
            textSize = 13.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }

        fun drawHeader(pCanvas: android.graphics.Canvas) {
            pCanvas.drawColor(Color.WHITE)
            pCanvas.drawRect(0f, 0f, pageWidth.toFloat(), 96f, headerBgPaint)
            pCanvas.drawLine(0f, 96f, pageWidth.toFloat(), 96f, accentPaint)
            pCanvas.drawText("بسم الله الرحمن الرحيم — ${paper.schoolName}", pageWidth / 2f, 26f, subHeaderPaint)
            pCanvas.drawText("${paper.examTitle} (${paper.subjectName} — ${paper.gradeTitle})", pageWidth / 2f, 52f, titlePaint)
            pCanvas.drawText(
                "التاريخ: ${paper.examDate}  |  الزمن: ${paper.examDuration}  |  الدرجة الكلية: ${paper.totalMarks}  |  الأستاذ: ${paper.teacherName}",
                pageWidth / 2f,
                78f,
                subHeaderPaint
            )
        }

        drawHeader(canvas)
        var y = 120f
        if (paper.noteLine.isNotBlank()) {
            canvas.drawText("ملاحظة: ${paper.noteLine}", pageWidth - 28f, y, bodyPaint)
            y += 22f
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - 36f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                y = 40f
            }
        }

        val branchLetters = listOf("أ", "ب", "ج", "د", "هـ", "و", "ز", "ح", "ط", "ي")
        val imgDir = File(context.filesDir, "question_images")

        paper.blocks.forEach { block ->
            ensureSpace(50f)
            val secColorInt = block.sectionColorHex.toInt()
            val barPaint = Paint().apply { color = secColorInt }
            canvas.drawRect(pageWidth - 24f, y - 14f, pageWidth - 18f, y + 4f, barPaint)
            canvas.drawText(
                "س${block.questionNumber} / ${block.sectionTitle}   (${block.totalMarks} درجة)",
                pageWidth - 32f,
                y,
                sectionTitlePaint
            )
            y += 20f

            if (block.branches.isEmpty()) {
                canvas.drawText("— لم تتوفر أسئلة في هذا القسم ضمن الفصول المحددة —", pageWidth - 40f, y, bodyPaint)
                y += 20f
            } else {
                block.branches.forEachIndexed { idx, q ->
                    val prefix = branchLetters.getOrElse(idx) { "${idx + 1}" }
                    val lines = wrapText("$prefix- ${q.questionText}", 68)
                    ensureSpace(lines.size * 18f + 16f)
                    lines.forEach { line ->
                        canvas.drawText(line, pageWidth - 40f, y, bodyPaint)
                        y += 18f
                    }
                    if (!q.imageFileName.isNullOrBlank()) {
                        val imgFile = File(imgDir, q.imageFileName)
                        if (imgFile.exists()) {
                            val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                            if (bmp != null) {
                                val maxW = 220f
                                val maxH = 120f
                                val ratio = minOf(maxW / bmp.width.coerceAtLeast(1), maxH / bmp.height.coerceAtLeast(1))
                                val dw = bmp.width * ratio
                                val dh = bmp.height * ratio
                                ensureSpace(dh + 14f)
                                val dst = RectF(pageWidth - 44f - dw, y, pageWidth - 44f, y + dh)
                                canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), dst, null)
                                bmp.recycle()
                                y += dh + 10f
                            }
                        }
                    }
                    if (includeAnswers && q.answerText.isNotBlank()) {
                        val aLines = wrapText("الإجابة: ${q.answerText}", 72)
                        ensureSpace(aLines.size * 16f)
                        aLines.forEach { al ->
                            canvas.drawText(al, pageWidth - 48f, y, ansPaint)
                            y += 16f
                        }
                    }
                    y += 4f
                }
            }
            y += 10f
        }

        doc.finishPage(page)
        FileOutputStream(file).use { out -> doc.writeTo(out) }
        doc.close()
        return file
    }

    private fun wrapText(text: String, maxCharsPerLine: Int): List<String> {
        val result = mutableListOf<String>()
        text.lines().forEach { paragraph ->
            if (paragraph.length <= maxCharsPerLine) {
                result += paragraph
            } else {
                var current = StringBuilder()
                paragraph.split(" ").forEach { word ->
                    if (current.length + word.length + 1 > maxCharsPerLine && current.isNotEmpty()) {
                        result += current.toString()
                        current = StringBuilder(word)
                    } else {
                        if (current.isNotEmpty()) current.append(' ')
                        current.append(word)
                    }
                }
                if (current.isNotEmpty()) result += current.toString()
            }
        }
        return result.ifEmpty { listOf("") }
    }
}
