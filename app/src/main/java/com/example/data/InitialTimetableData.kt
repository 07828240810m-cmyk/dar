package com.example.data

import com.example.model.TimetableCell

object InitialTimetableData {

    private val legacySeededTeacherNames = setOf(
        "نجلاء", "هدى", "صافي", "صائب", "علاء", "وليد", "عمر/د", "عمر", "فتاح",
        "محمد/م", "شهد", "بتول", "مصطفى", "أحمد", "إياد", "رياض", "زينة",
        "بسعاد", "خالد", "وسام", "مشتاق", "أحمد/ح", "رحاب", "عقيل", "عبير",
        "علي أحمد", "أيمن", "محمد/د", "زياد", "علي/ح", "محمد عادل", "هاله",
        "أمجد", "وليد/خ", "سياد", "علي/ع", "سجاد", "آيات", "رانيا", "محمد/ع",
        "ابو امير"
    )

    /**
     * Fresh app installations start with a clean empty timetable so every teacher
     * adds their own name, working days, class times, and lessons freely.
     */
    fun buildDefaultTimetable(): List<TimetableCell> = emptyList()

    /**
     * One-time cleanup migration for installations upgrading from the old
     * hardcoded school roster: removes legacy third-party teacher names while
     * keeping any cells belonging to the current user's own name.
     */
    fun stripLegacySeededTeachers(
        cells: List<TimetableCell>,
        currentTeacherName: String
    ): List<TimetableCell> {
        val ownName = currentTeacherName.trim()
        return cells.filter { cell ->
            val t = cell.teacherName.trim()
            val isOwn = ownName.isNotEmpty() && t.equals(ownName, ignoreCase = true)
            isOwn || t !in legacySeededTeacherNames
        }
    }

    fun extractUniqueTeachers(cells: List<TimetableCell>): List<String> =
        cells.map { it.teacherName.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
}
