package com.example.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.model.*
import com.example.ui.theme.AppFontOption
import java.io.File
import java.io.FileOutputStream

object ExamWriterExportHelper {

    private fun resolveTypeface(context: Context, fontOption: AppFontOption): Typeface {
        val resId = when (fontOption) {
            AppFontOption.CAIRO -> R.font.cairo
            AppFontOption.TAJAWAL -> R.font.tajawal
            AppFontOption.ALMARAI -> R.font.almarai
            AppFontOption.AMIRI -> R.font.amiri
            AppFontOption.READEX_PRO -> R.font.readex_pro
            AppFontOption.IBM_PLEX_SANS_ARABIC -> R.font.ibm_plex_sans_arabic
            AppFontOption.SCHEHERAZADE_NEW -> R.font.scheherazade_new
            AppFontOption.INTER -> R.font.inter
            AppFontOption.LORA -> R.font.lora
        }
        return runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull() ?: Typeface.DEFAULT
    }

    private fun examExportsDir(context: Context): File =
        File(context.filesDir, "exports/exams").apply { mkdirs() }

    private fun sanitizeFileName(raw: String): String =
        raw.replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "_").trim().ifBlank { "exam_paper" }

    fun exportToPdf(
        context: Context,
        doc: ExamPaperDocument,
        customFrameFile: File? = null
    ): Result<File> = runCatching {
        val baseW = if (doc.orientation == ExamPaperOrientation.PORTRAIT) doc.paperSize.widthPt else doc.paperSize.heightPt
        val baseH = if (doc.orientation == ExamPaperOrientation.PORTRAIT) doc.paperSize.heightPt else doc.paperSize.widthPt
        val pdf = PdfDocument()
        val typeface = resolveTypeface(context, doc.typography.fontOption)
        val customFrameBitmap = customFrameFile?.let { f ->
            runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
        }

        var physicalPageNum = 0

        fun drawFrameAndPageNumber(canvas: Canvas, w: Int, h: Int, pageNum: Int, totalPages: Int) {
            canvas.drawColor(Color.WHITE)
            when (doc.frameStyle) {
                ExamFrameStyle.NONE -> Unit
                ExamFrameStyle.CLASSIC_DOUBLE -> {
                    val pOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#1E3A8A")
                        style = Paint.Style.STROKE
                        strokeWidth = 2.5f
                    }
                    val pInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#64748B")
                        style = Paint.Style.STROKE
                        strokeWidth = 1f
                    }
                    canvas.drawRect(18f, 18f, w - 18f, h - 18f, pOuter)
                    canvas.drawRect(23f, 23f, w - 23f, h - 23f, pInner)
                }
                ExamFrameStyle.ROYAL_ORNAMENT -> {
                    val pGold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#B45309")
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                    }
                    val pTeal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#0F766E")
                        style = Paint.Style.STROKE
                        strokeWidth = 1.2f
                    }
                    canvas.drawRoundRect(RectF(16f, 16f, w - 16f, h - 16f), 14f, 14f, pGold)
                    canvas.drawRoundRect(RectF(24f, 24f, w - 24f, h - 24f), 10f, 10f, pTeal)
                }
                ExamFrameStyle.MODERN_MINIMAL -> {
                    val pMod = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#0284C7")
                        style = Paint.Style.STROKE
                        strokeWidth = 2f
                    }
                    canvas.drawRoundRect(RectF(20f, 20f, w - 20f, h - 20f), 8f, 8f, pMod)
                }
                ExamFrameStyle.CUSTOM_PNG -> {
                    if (customFrameBitmap != null) {
                        canvas.drawBitmap(customFrameBitmap, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), null)
                    } else {
                        val pFallback = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#334155")
                            style = Paint.Style.STROKE
                            strokeWidth = 2f
                        }
                        canvas.drawRect(18f, 18f, w - 18f, h - 18f, pFallback)
                    }
                }
            }
            if (doc.showPageNumbers) {
                val pNum = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.typeface = typeface
                    textSize = 10f
                    color = Color.parseColor("#475569")
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("صفحة $pageNum من $totalPages", w / 2f, h - 28f, pNum)
            }
        }

        val totalPhysicalPages = (if (doc.showCoverPage) 1 else 0) +
            (if (doc.showTableOfContents) 1 else 0) +
            doc.pages.size

        if (doc.showCoverPage) {
            physicalPageNum++
            val pageInfo = PdfDocument.PageInfo.Builder(baseW, baseH, physicalPageNum).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            drawFrameAndPageNumber(canvas, baseW, baseH, physicalPageNum, totalPhysicalPages)

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
                textSize = 24f
                color = Color.parseColor("#0F172A")
                textAlign = Paint.Align.CENTER
            }
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = 15f
                color = Color.parseColor("#334155")
                textAlign = Paint.Align.CENTER
            }
            val cy = baseH * 0.34f
            canvas.drawText(doc.ministryHeader, baseW / 2f, cy - 80f, subPaint)
            canvas.drawText(doc.schoolName, baseW / 2f, cy - 50f, subPaint)
            canvas.drawText(doc.docTitle, baseW / 2f, cy, titlePaint)
            canvas.drawText("المادة: ${doc.subjectName} | الصف: ${doc.gradeAndSection}", baseW / 2f, cy + 44f, subPaint)
            canvas.drawText("الزمن: ${doc.examDuration} | العام الدراسي: ${doc.academicYear}", baseW / 2f, cy + 72f, subPaint)
            pdf.finishPage(page)
        }

        if (doc.showTableOfContents) {
            physicalPageNum++
            val pageInfo = PdfDocument.PageInfo.Builder(baseW, baseH, physicalPageNum).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            drawFrameAndPageNumber(canvas, baseW, baseH, physicalPageNum, totalPhysicalPages)
            val hPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
                textSize = 18f
                color = Color.parseColor("#0F766E")
            }
            val bPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = 12.5f
                color = Color.parseColor("#1E293B")
            }
            var y = 64f
            drawRtlLayout(canvas, "فهرس المحتويات والأسئلة", hPaint, baseW - 72f, 36f, y, Layout.Alignment.ALIGN_CENTER).also { y += it + 16f }
            doc.pages.forEachIndexed { idx, pData ->
                val pageHeadingSummary = pData.questions.joinToString(" • ") {
                    it.headingAbove.ifBlank { it.questionTitle }
                }.ifBlank { "أسئلة منوعة" }
                y += drawRtlLayout(
                    canvas,
                    "${pData.pageTitle} (${idx + 1}): $pageHeadingSummary",
                    bPaint,
                    baseW - 72f,
                    36f,
                    y,
                    Layout.Alignment.ALIGN_OPPOSITE
                ) + 8f
            }
            pdf.finishPage(page)
        }

        val leftM = doc.typography.marginHorizontalDp.coerceIn(10f, 50f) + 20f
        val contentW = (baseW - leftM * 2f).coerceAtLeast(180f)
        val layoutAlign = when (doc.typography.alignment) {
            ExamTextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            ExamTextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            ExamTextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
        }
        val styleMask = (if (doc.typography.isBold) Typeface.BOLD else 0) or
            (if (doc.typography.isItalic) Typeface.ITALIC else 0)
        val styledTypeface = Typeface.create(typeface, styleMask)

        doc.pages.forEachIndexed { pageIdx, pData ->
            physicalPageNum++
            val pageInfo = PdfDocument.PageInfo.Builder(baseW, baseH, physicalPageNum).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            drawFrameAndPageNumber(canvas, baseW, baseH, physicalPageNum, totalPhysicalPages)

            var y = doc.typography.marginTopDp.coerceIn(12f, 50f) + 22f

            // Page header bar on first content page
            if (pageIdx == 0) {
                val hdrPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.typeface = Typeface.create(typeface, Typeface.BOLD)
                    textSize = 12f
                    color = Color.parseColor("#0F172A")
                }
                val topBanner = "${doc.schoolName}  •  ${doc.docTitle}\nالمادة: ${doc.subjectName}  |  الصف: ${doc.gradeAndSection}  |  الوقت: ${doc.examDuration}"
                y += drawRtlLayout(canvas, topBanner, hdrPaint, contentW, leftM, y, Layout.Alignment.ALIGN_CENTER) + 4f
                val sepPaint = Paint().apply {
                    color = Color.parseColor("#1E3A8A")
                    strokeWidth = 1.6f
                }
                canvas.drawLine(leftM, y, baseW - leftM, y, sepPaint)
                y += 8f
                if (doc.totalMarksNote.isNotBlank()) {
                    val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.typeface = typeface
                        textSize = 10.5f
                        color = Color.parseColor("#475569")
                    }
                    y += drawRtlLayout(canvas, doc.totalMarksNote, notePaint, contentW, leftM, y, Layout.Alignment.ALIGN_CENTER) + 8f
                }
            }

            pData.questions.forEach { q ->
                val dx = if (doc.freeManualDragMode) q.offsetXFraction * contentW * 0.25f else 0f
                val dy = if (doc.freeManualDragMode) q.offsetYFraction * 120f else 0f
                val drawX = (leftM + dx).coerceIn(24f, baseW - 140f)
                var qY = (y + dy).coerceIn(36f, baseH - 70f)

                if (q.headingAbove.isNotBlank()) {
                    val groupPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.typeface = Typeface.create(typeface, Typeface.BOLD)
                        textSize = doc.typography.headingFontSizeSp * 0.9f
                        color = q.headingColorHex.toInt()
                    }
                    qY += drawRtlLayout(canvas, q.headingAbove, groupPaint, contentW, drawX, qY, Layout.Alignment.ALIGN_CENTER) + 6f
                }

                val qHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.typeface = Typeface.create(typeface, Typeface.BOLD)
                    textSize = (doc.typography.baseFontSizeSp + 1f) * 0.85f
                    color = q.boxColorHex.toInt()
                    isUnderlineText = doc.typography.isUnderlined
                }
                val qBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.typeface = styledTypeface
                    textSize = doc.typography.baseFontSizeSp * 0.82f
                    color = q.textColorHex.toInt()
                    isUnderlineText = doc.typography.isUnderlined
                }

                val qHeadline = "${q.questionTitle} ${q.promptText}   [${q.marks} درجة]"
                val cardStartY = qY
                qY += 6f
                qY += drawRtlLayout(
                    canvas,
                    qHeadline,
                    qHeaderPaint,
                    contentW - 16f,
                    drawX + 8f,
                    qY,
                    layoutAlign,
                    lineMult = doc.typography.lineHeightMultiplier
                ) + 4f

                q.branches.forEach { b ->
                    val bPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.typeface = styledTypeface
                        textSize = doc.typography.baseFontSizeSp * 0.8f
                        color = b.textColorHex.toInt()
                    }
                    qY += drawRtlLayout(
                        canvas,
                        "  ${b.label}) ${b.prompt}  (${b.marks} د)",
                        bPaint,
                        contentW - 26f,
                        drawX + 12f,
                        qY,
                        layoutAlign,
                        lineMult = doc.typography.lineHeightMultiplier
                    ) + 3f
                }
                qY += 6f

                if (q.showDivider) {
                    val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = q.boxColorHex.toInt()
                        style = Paint.Style.STROKE
                        strokeWidth = 1.1f
                    }
                    canvas.drawRoundRect(RectF(drawX, cardStartY, drawX + contentW, qY), 8f, 8f, boxPaint)
                }
                y = qY + doc.typography.paragraphSpacingDp.coerceIn(4f, 24f)
            }

            if (pData.footerNote.isNotBlank()) {
                val footPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.typeface = Typeface.create(typeface, Typeface.ITALIC)
                    textSize = 10.5f
                    color = Color.parseColor("#475569")
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(pData.footerNote, baseW / 2f, baseH - 44f, footPaint)
            }

            pdf.finishPage(page)
        }

        val outFile = File(examExportsDir(context), "${sanitizeFileName(doc.docTitle)}.pdf")
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        outFile
    }

    private fun drawRtlLayout(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        width: Float,
        x: Float,
        y: Float,
        alignment: Layout.Alignment,
        lineMult: Float = 1.25f
    ): Float {
        val safeW = width.toInt().coerceAtLeast(60)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, safeW)
            .setAlignment(alignment)
            .setLineSpacing(0f, lineMult)
            .setIncludePad(true)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    fun exportToWordHtml(
        context: Context,
        doc: ExamPaperDocument
    ): Result<File> = runCatching {
        val alignCss = when (doc.typography.alignment) {
            ExamTextAlign.RIGHT -> "right"
            ExamTextAlign.CENTER -> "center"
            ExamTextAlign.LEFT -> "left"
        }
        val fontName = when (doc.typography.fontOption) {
            AppFontOption.AMIRI -> "Amiri, Traditional Arabic, serif"
            AppFontOption.CAIRO -> "Cairo, Arial, sans-serif"
            AppFontOption.TAJAWAL -> "Tajawal, Arial, sans-serif"
            else -> "Almarai, Arial, sans-serif"
        }
        val html = buildString {
            appendLine("<html dir=\"rtl\" lang=\"ar\"><head><meta charset=\"UTF-8\">")
            appendLine("<style>")
            appendLine("body{font-family:$fontName;padding:24px;line-height:${doc.typography.lineHeightMultiplier};text-align:$alignCss;}")
            appendLine(".header{border-bottom:2px solid #1E3A8A;padding-bottom:10px;margin-bottom:16px;text-align:center;}")
            appendLine(".sec-head{font-size:${doc.typography.headingFontSizeSp}pt;font-weight:bold;text-align:center;margin:14px 0 8px 0;letter-spacing:0.5px;}")
            appendLine(".qbox{border:1.5px solid #1565C0;border-radius:8px;padding:12px;margin-bottom:${doc.typography.paragraphSpacingDp}px;}")
            appendLine(".branch{margin-right:18px;margin-top:4px;}")
            appendLine("</style></head><body>")
            appendLine("<div class=\"header\">")
            appendLine("<div>${escapeHtml(doc.ministryHeader)} — ${escapeHtml(doc.schoolName)}</div>")
            appendLine("<h2>${escapeHtml(doc.docTitle)}</h2>")
            appendLine("<div>المادة: ${escapeHtml(doc.subjectName)} | الصف: ${escapeHtml(doc.gradeAndSection)} | الوقت: ${escapeHtml(doc.examDuration)}</div>")
            appendLine("<p><i>${escapeHtml(doc.totalMarksNote)}</i></p>")
            appendLine("</div>")
            doc.pages.forEachIndexed { idx, pData ->
                if (doc.pages.size > 1) {
                    appendLine("<h4>${escapeHtml(pData.pageTitle)}</h4>")
                }
                pData.questions.forEach { q ->
                    if (q.headingAbove.isNotBlank()) {
                        val hex = String.format("#%06X", 0xFFFFFF and q.headingColorHex.toInt())
                        appendLine("<div class=\"sec-head\" style=\"color:$hex\">${escapeHtml(q.headingAbove)}</div>")
                    }
                    val borderHex = String.format("#%06X", 0xFFFFFF and q.boxColorHex.toInt())
                    appendLine("<div class=\"qbox\" style=\"border-color:$borderHex\">")
                    appendLine("<b>${escapeHtml(q.questionTitle)}</b> ${escapeHtml(q.promptText)} <b>[${q.marks} درجة]</b>")
                    q.branches.forEach { b ->
                        appendLine("<div class=\"branch\"><b>${escapeHtml(b.label)})</b> ${escapeHtml(b.prompt)} (${b.marks} د)</div>")
                    }
                    appendLine("</div>")
                }
                if (pData.footerNote.isNotBlank()) {
                    appendLine("<p style=\"text-align:center;margin-top:18px;\"><i>${escapeHtml(pData.footerNote)}</i></p>")
                }
                if (idx < doc.pages.lastIndex) {
                    appendLine("<hr style=\"page-break-after:always;\"/>")
                }
            }
            appendLine("</body></html>")
        }
        val outFile = File(examExportsDir(context), "${sanitizeFileName(doc.docTitle)}.doc")
        outFile.writeText(html, Charsets.UTF_8)
        outFile
    }

    private fun escapeHtml(raw: String): String =
        raw.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br/>")
}
