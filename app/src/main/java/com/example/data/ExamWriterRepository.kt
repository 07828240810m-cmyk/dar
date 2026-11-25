package com.example.data

import android.content.Context
import android.net.Uri
import com.example.model.ExamBranchItem
import com.example.model.ExamFrameStyle
import com.example.model.ExamPageData
import com.example.model.ExamPaperDocument
import com.example.model.ExamPaperOrientation
import com.example.model.ExamPaperSize
import com.example.model.ExamQuestionBox
import com.example.model.ExamQuestionType
import com.example.model.ExamTextAlign
import com.example.model.ExamTypographyConfig
import com.example.ui.theme.AppFontOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ExamWriterRepository(private val context: Context) {

    private val docsFile = File(context.filesDir, "exam_writer_docs_v1.json")
    private val framesDir = File(context.filesDir, "exam_frames").apply { mkdirs() }

    suspend fun loadDocuments(): List<ExamPaperDocument> = withContext(Dispatchers.IO) {
        if (!docsFile.exists()) return@withContext listOf(ExamPaperDocument())
        runCatching {
            val arr = JSONArray(docsFile.readText(Charsets.UTF_8))
            val list = (0 until arr.length()).mapNotNull { idx ->
                parseDoc(arr.optJSONObject(idx) ?: return@mapNotNull null)
            }
            if (list.isEmpty()) listOf(ExamPaperDocument()) else list
        }.getOrElse { listOf(ExamPaperDocument()) }
    }

    suspend fun saveDocuments(docs: List<ExamPaperDocument>) = withContext(Dispatchers.IO) {
        val safeDocs = if (docs.isEmpty()) listOf(ExamPaperDocument()) else docs
        val arr = JSONArray().apply {
            safeDocs.forEach { put(serializeDoc(it)) }
        }
        val tmp = File(context.filesDir, "exam_writer_docs_v1.json.tmp")
        tmp.writeText(arr.toString(), Charsets.UTF_8)
        tmp.renameTo(docsFile)
    }

    fun importCustomFramePng(uri: Uri): String? {
        return runCatching {
            val name = "frame_${System.currentTimeMillis()}.png"
            val dest = File(framesDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { out -> input.copyTo(out) }
            } ?: return null
            name
        }.getOrNull()
    }

    fun getCustomFrameFile(fileName: String?): File? {
        if (fileName.isNullOrBlank()) return null
        val f = File(framesDir, fileName)
        return if (f.exists()) f else null
    }

    private fun serializeDoc(d: ExamPaperDocument): JSONObject = JSONObject().apply {
        put("id", d.id)
        put("docTitle", d.docTitle)
        put("ministryHeader", d.ministryHeader)
        put("schoolName", d.schoolName)
        put("subjectName", d.subjectName)
        put("gradeAndSection", d.gradeAndSection)
        put("examDuration", d.examDuration)
        put("academicYear", d.academicYear)
        put("totalMarksNote", d.totalMarksNote)
        put("orientation", d.orientation.name)
        put("paperSize", d.paperSize.name)
        put("frameStyle", d.frameStyle.name)
        put("customFrameFileName", d.customFrameFileName ?: JSONObject.NULL)
        put("showCoverPage", d.showCoverPage)
        put("showTableOfContents", d.showTableOfContents)
        put("showPageNumbers", d.showPageNumbers)
        put("freeManualDragMode", d.freeManualDragMode)
        put("updatedAtMillis", d.updatedAtMillis)
        put("typography", JSONObject().apply {
            val t = d.typography
            put("fontOption", t.fontOption.id)
            put("baseFontSizeSp", t.baseFontSizeSp.toDouble())
            put("headingFontSizeSp", t.headingFontSizeSp.toDouble())
            put("isBold", t.isBold)
            put("isItalic", t.isItalic)
            put("isUnderlined", t.isUnderlined)
            put("alignment", t.alignment.name)
            put("lineHeightMultiplier", t.lineHeightMultiplier.toDouble())
            put("wordSpacingSp", t.wordSpacingSp.toDouble())
            put("paragraphSpacingDp", t.paragraphSpacingDp.toDouble())
            put("marginTopDp", t.marginTopDp.toDouble())
            put("marginBottomDp", t.marginBottomDp.toDouble())
            put("marginHorizontalDp", t.marginHorizontalDp.toDouble())
        })
        put("pages", JSONArray().apply {
            d.pages.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("pageTitle", p.pageTitle)
                    put("footerNote", p.footerNote)
                    put("questions", JSONArray().apply {
                        p.questions.forEach { q ->
                            put(JSONObject().apply {
                                put("id", q.id)
                                put("headingAbove", q.headingAbove)
                                put("questionTitle", q.questionTitle)
                                put("questionType", q.questionType.name)
                                put("promptText", q.promptText)
                                put("marks", q.marks)
                                put("boxColorHex", q.boxColorHex)
                                put("textColorHex", q.textColorHex)
                                put("headingColorHex", q.headingColorHex)
                                put("showDivider", q.showDivider)
                                put("offsetXFraction", q.offsetXFraction.toDouble())
                                put("offsetYFraction", q.offsetYFraction.toDouble())
                                put("branches", JSONArray().apply {
                                    q.branches.forEach { b ->
                                        put(JSONObject().apply {
                                            put("id", b.id)
                                            put("label", b.label)
                                            put("prompt", b.prompt)
                                            put("marks", b.marks)
                                            put("textColorHex", b.textColorHex)
                                            put("bgColorHex", b.bgColorHex)
                                        })
                                    }
                                })
                            })
                        }
                    })
                })
            }
        })
    }

    private fun parseDoc(o: JSONObject): ExamPaperDocument {
        val tObj = o.optJSONObject("typography")
        val typography = if (tObj != null) {
            ExamTypographyConfig(
                fontOption = AppFontOption.entries.find { it.id == tObj.optString("fontOption") } ?: AppFontOption.ALMARAI,
                baseFontSizeSp = tObj.optDouble("baseFontSizeSp", 14.0).toFloat(),
                headingFontSizeSp = tObj.optDouble("headingFontSizeSp", 18.0).toFloat(),
                isBold = tObj.optBoolean("isBold", false),
                isItalic = tObj.optBoolean("isItalic", false),
                isUnderlined = tObj.optBoolean("isUnderlined", false),
                alignment = runCatching { ExamTextAlign.valueOf(tObj.optString("alignment")) }.getOrDefault(ExamTextAlign.RIGHT),
                lineHeightMultiplier = tObj.optDouble("lineHeightMultiplier", 1.45).toFloat(),
                wordSpacingSp = tObj.optDouble("wordSpacingSp", 0.0).toFloat(),
                paragraphSpacingDp = tObj.optDouble("paragraphSpacingDp", 8.0).toFloat(),
                marginTopDp = tObj.optDouble("marginTopDp", 20.0).toFloat(),
                marginBottomDp = tObj.optDouble("marginBottomDp", 20.0).toFloat(),
                marginHorizontalDp = tObj.optDouble("marginHorizontalDp", 18.0).toFloat()
            )
        } else ExamTypographyConfig()

        val pagesArr = o.optJSONArray("pages")
        val pages = if (pagesArr != null && pagesArr.length() > 0) {
            (0 until pagesArr.length()).mapNotNull { i ->
                val pObj = pagesArr.optJSONObject(i) ?: return@mapNotNull null
                val qArr = pObj.optJSONArray("questions")
                val questions = if (qArr != null) {
                    (0 until qArr.length()).mapNotNull { qi ->
                        val qObj = qArr.optJSONObject(qi) ?: return@mapNotNull null
                        val bArr = qObj.optJSONArray("branches")
                        val branches = if (bArr != null) {
                            (0 until bArr.length()).mapNotNull { bi ->
                                val bObj = bArr.optJSONObject(bi) ?: return@mapNotNull null
                                ExamBranchItem(
                                    id = bObj.optString("id").ifBlank { "${qi}_$bi" },
                                    label = bObj.optString("label", "${bi + 1}"),
                                    prompt = bObj.optString("prompt", ""),
                                    marks = bObj.optInt("marks", 5),
                                    textColorHex = bObj.optLong("textColorHex", 0xFF1A1A1AL),
                                    bgColorHex = bObj.optLong("bgColorHex", 0x00000000L)
                                )
                            }
                        } else emptyList()
                        ExamQuestionBox(
                            id = qObj.optString("id").ifBlank { "q_$qi" },
                            headingAbove = qObj.optString("headingAbove", ""),
                            questionTitle = qObj.optString("questionTitle", "س${qi + 1}/"),
                            questionType = runCatching { ExamQuestionType.valueOf(qObj.optString("questionType")) }
                                .getOrDefault(ExamQuestionType.QA),
                            promptText = qObj.optString("promptText", ""),
                            marks = qObj.optInt("marks", 10),
                            boxColorHex = qObj.optLong("boxColorHex", 0xFF1565C0L),
                            textColorHex = qObj.optLong("textColorHex", 0xFF111827L),
                            headingColorHex = qObj.optLong("headingColorHex", 0xFF0F766EL),
                            showDivider = qObj.optBoolean("showDivider", true),
                            branches = branches,
                            offsetXFraction = qObj.optDouble("offsetXFraction", 0.0).toFloat(),
                            offsetYFraction = qObj.optDouble("offsetYFraction", 0.0).toFloat()
                        )
                    }
                } else emptyList()
                ExamPageData(
                    id = pObj.optString("id").ifBlank { "page_$i" },
                    pageTitle = pObj.optString("pageTitle", "الصفحة ${i + 1}"),
                    questions = questions,
                    footerNote = pObj.optString("footerNote", "مع تمنياتنا لكم بالنجاح والتوفيق")
                )
            }
        } else listOf(ExamPageData())

        val frameNameRaw = if (o.has("customFrameFileName") && !o.isNull("customFrameFileName")) {
            o.optString("customFrameFileName").takeIf { it.isNotBlank() }
        } else null

        return ExamPaperDocument(
            id = o.optString("id").ifBlank { "doc_default" },
            docTitle = o.optString("docTitle", "أسئلة الامتحان الشهري"),
            ministryHeader = o.optString("ministryHeader", "إدارة المدرسة / قسم الامتحانات"),
            schoolName = o.optString("schoolName", "مدرستي"),
            subjectName = o.optString("subjectName", "المادة"),
            gradeAndSection = o.optString("gradeAndSection", "الأول المتوسط"),
            examDuration = o.optString("examDuration", "ساعة واحدة"),
            academicYear = o.optString("academicYear", "2026 - 2027"),
            totalMarksNote = o.optString("totalMarksNote", "ملاحظة: أجب عن جميع الأسئلة"),
            orientation = runCatching { ExamPaperOrientation.valueOf(o.optString("orientation")) }
                .getOrDefault(ExamPaperOrientation.PORTRAIT),
            paperSize = runCatching { ExamPaperSize.valueOf(o.optString("paperSize")) }
                .getOrDefault(ExamPaperSize.A4),
            frameStyle = runCatching { ExamFrameStyle.valueOf(o.optString("frameStyle")) }
                .getOrDefault(ExamFrameStyle.CLASSIC_DOUBLE),
            customFrameFileName = frameNameRaw,
            showCoverPage = o.optBoolean("showCoverPage", false),
            showTableOfContents = o.optBoolean("showTableOfContents", false),
            showPageNumbers = o.optBoolean("showPageNumbers", true),
            freeManualDragMode = o.optBoolean("freeManualDragMode", false),
            typography = typography,
            pages = pages,
            updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis())
        )
    }
}
