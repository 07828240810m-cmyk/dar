package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.model.Student
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class ParsedGradeCandidate(
    val id: String,
    val extractedName: String,
    val extractedScore: Double?,
    val matchedStudentId: String?,
    val matchedStudentName: String
)

object OcrAndImportHelper {

    fun normalizeArabicForSearch(text: String): String {
        val noHarakat = text.replace(Regex("[\u064B-\u065F\u0670\u06D6-\u06ED]"), "")
        return noHarakat
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace("ـ", "")
            .trim()
            .lowercase()
    }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): Result<String> = suspendCoroutine { cont ->
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    cont.resume(Result.success(visionText.text))
                }
                .addOnFailureListener { ex ->
                    cont.resume(Result.failure(ex))
                }
        } catch (ex: Exception) {
            cont.resume(Result.failure(ex))
        }
    }

    fun decodeBitmapFromUri(context: Context, uri: Uri, maxDim: Int = 1800): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val original = BitmapFactory.decodeStream(stream) ?: return null
                val scale = minOf(1f, maxDim.toFloat() / maxOf(original.width, original.height).coerceAtLeast(1))
                if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        original,
                        (original.width * scale).toInt().coerceAtLeast(1),
                        (original.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else original
            }
        } catch (_: Exception) {
            null
        }
    }

    fun extractPlainTextFromDocumentUri(context: Context, uri: Uri, fileName: String): String {
        val lower = fileName.lowercase()
        val mime = runCatching { context.contentResolver.getType(uri).orEmpty().lowercase() }.getOrDefault("")
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
            when {
                lower.endsWith(".xlsx") || mime.contains("spreadsheetml") ->
                    extractTextFromXlsx(ByteArrayInputStream(bytes))
                lower.endsWith(".docx") || mime.contains("wordprocessingml") ->
                    extractTextFromDocx(ByteArrayInputStream(bytes))
                else -> decodePlainTextSmart(bytes)
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun decodePlainTextSmart(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE).trim()
            }
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE).trim()
            }
        }
        val utf8 = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        return if (utf8.contains('\uFFFD')) {
            runCatching { String(bytes, charset("windows-1256")) }.getOrDefault(utf8).trim()
        } else {
            utf8.trim()
        }
    }

    fun extractTextFromDocx(inputStream: InputStream): String {
        val sb = StringBuilder()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zis.readBytes().toString(Charsets.UTF_8)
                    val tagTextRegex = Regex("<w:t[^>]*>([^<]*)</w:t>")
                    if (xml.contains("<w:tr")) {
                        // Extract structured table rows (Name\tScore or Student lists) + outside paragraphs
                        val rowChunks = xml.split(Regex("</?w:tr[^>]*>"))
                        rowChunks.forEach { chunk ->
                            if (chunk.contains("<w:tc")) {
                                val cells = chunk.split("</w:tc>").mapNotNull { cellXml ->
                                    val txt = tagTextRegex.findAll(cellXml)
                                        .joinToString("") { decodeXmlEntities(it.groupValues[1]) }
                                        .trim()
                                    txt.takeIf { it.isNotBlank() }
                                }
                                if (cells.isNotEmpty()) sb.appendLine(cells.joinToString("\t"))
                            } else {
                                val paragraphs = chunk.split("</w:p>")
                                paragraphs.forEach { p ->
                                    val line = tagTextRegex.findAll(p)
                                        .joinToString("") { decodeXmlEntities(it.groupValues[1]) }
                                        .trim()
                                    if (line.isNotBlank()) sb.appendLine(line)
                                }
                            }
                        }
                    } else {
                        val paragraphs = xml.split("</w:p>")
                        paragraphs.forEach { p ->
                            val line = tagTextRegex.findAll(p)
                                .joinToString("") { decodeXmlEntities(it.groupValues[1]) }
                                .trim()
                            if (line.isNotBlank()) sb.appendLine(line)
                        }
                    }
                    break
                }
                entry = zis.nextEntry
            }
        }
        return sb.toString().trim()
    }

    fun extractTextFromXlsx(inputStream: InputStream): String {
        val sharedStrings = mutableListOf<String>()
        var sheetXml: String? = null

        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "xl/sharedStrings.xml" -> {
                        val xml = zis.readBytes().toString(Charsets.UTF_8)
                        val siMatches = Regex("<si\\b[^>]*>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
                        val tRegex = Regex("<t\\b[^>]*>([^<]*)</t>")
                        siMatches.forEach { m ->
                            val text = tRegex.findAll(m.groupValues[1])
                                .joinToString("") { decodeXmlEntities(it.groupValues[1]) }
                                .trim()
                            sharedStrings += text
                        }
                    }
                    name == "xl/worksheets/sheet1.xml" || (sheetXml == null && name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) -> {
                        sheetXml = zis.readBytes().toString(Charsets.UTF_8)
                    }
                }
                entry = zis.nextEntry
            }
        }

        val xml = sheetXml ?: return ""
        val sb = StringBuilder()
        val rowRegex = Regex("<row\\b[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("<c\\b([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
        val vRegex = Regex("<v\\b[^>]*>([^<]*)</v>")
        val tRegex = Regex("<t\\b[^>]*>([^<]*)</t>")
        val typeAttrRegex = Regex("t=\"([^\"]+)\"")

        rowRegex.findAll(xml).forEach { rowMatch ->
            val values = mutableListOf<String>()
            cellRegex.findAll(rowMatch.groupValues[1]).forEach { cellMatch ->
                val attrs = cellMatch.groupValues[1]
                val body = cellMatch.groupValues[2]
                val cellType = typeAttrRegex.find(attrs)?.groupValues?.get(1) ?: ""
                val rawVal = when (cellType) {
                    "s" -> {
                        val idx = vRegex.find(body)?.groupValues?.get(1)?.trim()?.toIntOrNull()
                        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                    }
                    "inlineStr" -> {
                        tRegex.findAll(body).joinToString("") { decodeXmlEntities(it.groupValues[1]) }
                    }
                    else -> {
                        decodeXmlEntities(vRegex.find(body)?.groupValues?.get(1).orEmpty())
                    }
                }.trim()
                if (rawVal.isNotBlank()) values += rawVal
            }
            if (values.isNotEmpty()) sb.appendLine(values.joinToString("\t"))
        }
        return sb.toString().trim()
    }

    private fun decodeXmlEntities(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")

    /**
     * Parses a typed list or Word/Excel/CSV text into clean student names.
     * Strips leading numbering ("1-", "1.", "١-"), trailing numeric grades/IDs, and common table headers.
     */
    fun parseStudentNamesList(rawText: String): List<String> {
        val headerWords = setOf(
            "ت", "التسلسل", "الرقم", "الاسم", "اسم الطالب", "اسم الطالبة", "الاسم الكامل",
            "الاسم الرباعي", "الاسم الثلاثي", "الدرجة", "الدرجه", "المعدل", "ملاحظات",
            "الشعبة", "الشعبه", "الصف", "المادة", "name", "student", "student name", "no", "grade", "score"
        ).map { normalizeArabicForSearch(it) }.toSet()
        val result = mutableListOf<String>()
        rawText.lines().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) return@forEach

            val cols = trimmed.split(',', '،', ';', '؛', '\t', '|')
                .map { it.trim() }
                .filter { it.isNotBlank() }

            // Skip entire table header row if any cell matches a column header keyword
            if (cols.size >= 2 && cols.any { normalizeArabicForSearch(it) in headerWords }) {
                return@forEach
            }

            var candidateName: String? = null
            if (cols.size >= 2) {
                // Pick the first alphabetic/Arabic text column (skips leading "1" and trailing numbers)
                candidateName = cols.firstOrNull { col ->
                    val cleaned = col.replace(Regex("^[\\d٠-٩]+[\\-.)\\s]*"), "").trim()
                    cleaned.length >= 2 &&
                        normalizeDigits(cleaned).toDoubleOrNull() == null &&
                        normalizeArabicForSearch(cleaned) !in headerWords
                }
            } else {
                candidateName = trimmed.replace(Regex("^[\\d٠-٩]+[\\-.)\\s]+"), "").trim()
            }

            val cleanName = candidateName
                ?.replace(Regex("^[\\d٠-٩]+[\\-.)\\s]+"), "")
                ?.replace(Regex("\\s+[\\d٠-٩]+(\\.[\\d٠-٩]+)?$"), "")
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                .orEmpty()

            if (cleanName.length >= 2 &&
                normalizeDigits(cleanName).toDoubleOrNull() == null &&
                normalizeArabicForSearch(cleanName) !in headerWords &&
                cleanName !in result
            ) {
                result += cleanName
            }
        }
        return result
    }

    fun parseGradeCandidates(
        rawText: String,
        sectionStudents: List<Student>
    ): List<ParsedGradeCandidate> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val candidates = mutableListOf<ParsedGradeCandidate>()
        var idx = 0
        for (line in lines) {
            val parts = line.split(',', '،', ';', '؛', '\t', '|').map { it.trim() }.filter { it.isNotBlank() }
            var extractedName = ""
            var extractedScore: Double? = null

            if (parts.size >= 2) {
                val numericParts = parts.mapNotNull { p ->
                    normalizeDigits(p).toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
                }
                extractedScore = numericParts.lastOrNull()
                extractedName = parts.firstOrNull { p ->
                    normalizeDigits(p).toDoubleOrNull() == null && p.length >= 2
                } ?: parts.first()
            } else {
                val normalized = normalizeDigits(line)
                val numberMatch = Regex("(\\d{1,3}(\\.\\d{1,2})?)").findAll(normalized).lastOrNull()
                extractedScore = numberMatch?.value?.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
                extractedName = line.replace(Regex("[\\d٠-٩.,;:]+"), " ").trim()
            }

            if (extractedScore == null && extractedName.isBlank()) continue

            val matched = findBestStudentMatch(extractedName, idx, sectionStudents)
            candidates += ParsedGradeCandidate(
                id = "cand_${idx}_${System.currentTimeMillis()}",
                extractedName = extractedName.ifBlank { "سطر ${idx + 1}" },
                extractedScore = extractedScore,
                matchedStudentId = matched?.id,
                matchedStudentName = matched?.fullName ?: "غير مطابق — اختر طالباً"
            )
            idx++
        }
        return candidates
    }

    private fun normalizeDigits(input: String): String {
        val arabicDigits = "٠١٢٣٤٥٦٧٨٩"
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val idx = arabicDigits.indexOf(ch)
            sb.append(if (idx >= 0) ('0' + idx) else ch)
        }
        return sb.toString()
    }

    private fun findBestStudentMatch(
        nameQuery: String,
        lineIndex: Int,
        students: List<Student>
    ): Student? {
        if (students.isEmpty()) return null
        val normQuery = normalizeArabicForSearch(nameQuery)
        if (normQuery.isBlank()) {
            return students.getOrNull(lineIndex)
        }
        val queryTokens = normQuery.split(" ").filter { it.length >= 2 }.toSet()
        if (queryTokens.isEmpty()) return students.getOrNull(lineIndex)

        var bestStudent: Student? = null
        var bestScore = 0
        for (st in students) {
            val stNorm = normalizeArabicForSearch(st.fullName)
            if (stNorm == normQuery || stNorm.contains(normQuery)) {
                return st
            }
            val stTokens = stNorm.split(" ").filter { it.length >= 2 }.toSet()
            val overlap = queryTokens.count { qt -> stTokens.any { it.startsWith(qt) || qt.startsWith(it) } }
            if (overlap > bestScore) {
                bestScore = overlap
                bestStudent = st
            }
        }
        return if (bestScore > 0) bestStudent else students.getOrNull(lineIndex)
    }
}
