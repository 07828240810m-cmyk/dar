package com.example.data

import com.example.model.Student

object InitialStudentsData {

    fun buildAllStudents(): List<Student> = emptyList()

    private val legacyIdRegex = Regex("^[123]_[أبجد]_\\d+$")

    /**
     * One-time migration to strip the old preloaded 516 school students on existing installations
     * while preserving any students that already have entered grades/notes/custom IDs.
     */
    fun stripLegacySeededStudents(students: List<Student>): List<Student> {
        return students.filterNot { st ->
            legacyIdRegex.matches(st.id) &&
                st.grades.term1Month1 == null &&
                st.grades.term1Month2 == null &&
                st.grades.term1Daily == null &&
                st.grades.midYearTotal == null &&
                st.grades.term2Month1 == null &&
                st.grades.term2Month2 == null &&
                st.grades.term2Daily == null &&
                st.grades.finalExam == null
        }
    }
}
