package com.example.data

import android.content.Context
import com.example.model.*
import com.example.ui.theme.AppFontOption
import com.example.ui.theme.AppTextTone
import com.example.ui.theme.StudentColorTag
import com.example.ui.theme.ThemePalette
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class JsonStorageRepository(private val context: Context) {

    private val storageFile: File
        get() = File(context.filesDir, "teacher_ledger_state.json")

    fun loadState(): AppState {
        if (!storageFile.exists()) {
            return createInitialState()
        }
        return try {
            val raw = storageFile.readText(Charsets.UTF_8)
            val root = JSONObject(raw)
            parseAppState(root)
        } catch (_: Exception) {
            createInitialState()
        }
    }

    fun saveState(state: AppState) {
        try {
            val json = serializeAppState(state)
            val tmpFile = File(context.filesDir, "teacher_ledger_state.tmp")
            tmpFile.writeText(json.toString(), Charsets.UTF_8)
            if (storageFile.exists()) storageFile.delete()
            tmpFile.renameTo(storageFile)
        } catch (_: Exception) {
        }
    }

    fun exportBackupJsonFile(state: AppState): File {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(dir, "rafiq_teacher_backup.json")
        file.writeText(serializeAppState(state).toString(2), Charsets.UTF_8)
        return file
    }

    fun parseBackupJson(rawJson: String): AppState? {
        return try {
            parseAppState(JSONObject(rawJson))
        } catch (_: Exception) {
            null
        }
    }

    fun exportStateToJsonString(state: AppState): String =
        serializeAppState(state).toString()

    fun parseStateFromJsonString(rawJson: String): AppState? =
        parseBackupJson(rawJson)

    private fun createInitialState(): AppState {
        val defaultChapters = listOf(
            CurriculumChapter("ch_1_1", GradeLevel.FIRST, "الفصل الأول: الأساسيات والمفاهيم", false),
            CurriculumChapter("ch_1_2", GradeLevel.FIRST, "الفصل الثاني: التطبيقات العملية", false),
            CurriculumChapter("ch_2_1", GradeLevel.SECOND, "الفصل الأول: التمهيد والبنية", false),
            CurriculumChapter("ch_2_2", GradeLevel.SECOND, "الفصل الثاني: التوسع والتحليل", false),
            CurriculumChapter("ch_3_1", GradeLevel.THIRD, "الفصل الأول: المراجعة والمنهج الوزاري", false),
            CurriculumChapter("ch_3_2", GradeLevel.THIRD, "الفصل الثاني: المسائل المتقدمة", false)
        )
        val defaultTasks = listOf(
            TeacherTask("t_1", "إعداد التحضير الأسبوعي للدروس", "يومي", false),
            TeacherTask("t_2", "تدقيق دفاتر الطلبة وتثبيت درجات اليومي", "أسبوعي", false),
            TeacherTask("t_3", "مراجعة كشف المتغيبين وتبليغ الإدارة", "يومي", false)
        )
        return AppState(
            students = InitialStudentsData.buildAllStudents(),
            timetable = InitialTimetableData.buildDefaultTimetable(),
            dayPeriodTimes = defaultDayPeriodTimes(),
            chapters = defaultChapters,
            tasks = defaultTasks
        )
    }

    private fun serializeHeaders(h: GradebookHeaders): JSONObject = JSONObject().apply {
        put("seqHeader", h.seqHeader)
        put("nameHeader", h.nameHeader)
        put("t1M1Header", h.t1M1Header)
        put("t1M2Header", h.t1M2Header)
        put("t1M3Header", h.t1M3Header)
        put("t1DailyHeader", h.t1DailyHeader)
        put("t1AvgHeader", h.t1AvgHeader)
        put("midYearHeader", h.midYearHeader)
        put("t2M1Header", h.t2M1Header)
        put("t2M2Header", h.t2M2Header)
        put("t2M3Header", h.t2M3Header)
        put("t2DailyHeader", h.t2DailyHeader)
        put("t2AvgHeader", h.t2AvgHeader)
        put("annualEffortHeader", h.annualEffortHeader)
        put("finalExamHeader", h.finalExamHeader)
        put("finalGradeHeader", h.finalGradeHeader)
    }

    private fun parseHeaders(hObj: JSONObject?): GradebookHeaders = if (hObj != null) GradebookHeaders(
        seqHeader = hObj.optString("seqHeader", "ت"),
        nameHeader = hObj.optString("nameHeader", "اسم الطالب"),
        t1M1Header = hObj.optString("t1M1Header", "ش1 ف1"),
        t1M2Header = hObj.optString("t1M2Header", "ش2 ف1"),
        t1M3Header = hObj.optString("t1M3Header", "ش3 ف1"),
        t1DailyHeader = hObj.optString("t1DailyHeader", "يومي ف1"),
        t1AvgHeader = hObj.optString("t1AvgHeader", "معدل ف1"),
        midYearHeader = hObj.optString("midYearHeader", "نصف السنة"),
        t2M1Header = hObj.optString("t2M1Header", "ش1 ف2"),
        t2M2Header = hObj.optString("t2M2Header", "ش2 ف2"),
        t2M3Header = hObj.optString("t2M3Header", "ش3 ف2"),
        t2DailyHeader = hObj.optString("t2DailyHeader", "يومي ف2"),
        t2AvgHeader = hObj.optString("t2AvgHeader", "معدل ف2"),
        annualEffortHeader = hObj.optString("annualEffortHeader", "السعي السنوي"),
        finalExamHeader = hObj.optString("finalExamHeader", "الامتحان النهائي"),
        finalGradeHeader = hObj.optString("finalGradeHeader", "الدرجة النهائية")
    ) else GradebookHeaders()

    private fun serializeAppState(state: AppState): JSONObject {
        val root = JSONObject()

        root.put("profile", JSONObject().apply {
            val p = state.profile
            put("teacherName", p.teacherName)
            put("timetableTeacherKey", p.timetableTeacherKey)
            put("schoolName", p.schoolName)
            put("subjectName", p.subjectName)
            put("subjectSpecialty", p.subjectSpecialty.id)
            put("preferredGrade", p.preferredGrade.id)
            put("academicYear", p.academicYear)
            put("showOnlyMyLessons", p.showOnlyMyLessons)
            put("notificationsMasterEnabled", p.notificationsMasterEnabled)
            put("lessonNotificationsEnabled", p.lessonNotificationsEnabled)
            put("notifyMinutesBefore", p.notifyMinutesBefore)
            put("periodDurationMinutes", p.periodDurationMinutes)
            put("breakDurationMinutes", p.breakDurationMinutes)
            put("schoolStartHour", p.schoolStartHour)
            put("schoolStartMinute", p.schoolStartMinute)
            put("workingDays", JSONArray().apply { p.workingDays.forEach { put(it.id) } })
            put("themePalette", p.themePalette.id)
            put("useSpecialtyTheme", p.useSpecialtyTheme)
            put("textTone", p.textTone.id)
            put("isDarkMode", p.isDarkMode)
            put("neonGlowEnabled", p.neonGlowEnabled)
            put("fontOption", p.fontOption.id)
            put("fontScaleMultiplier", p.fontScaleMultiplier.toDouble())
            put("vaultPin", p.vaultPin)
            put("teacherSelectedOnStartup", p.teacherSelectedOnStartup)
            put("enableGeneralLedger", p.enableGeneralLedger)
            put("enableMathLedger", p.enableMathLedger)
            put("activeLedgerSubject", p.activeLedgerSubject.id)
            put("notifyBeforeLessonStart", p.notifyBeforeLessonStart)
            put("notifyBeforeLessonEnd", p.notifyBeforeLessonEnd)
            put("voiceAnnouncementEnabled", p.voiceAnnouncementEnabled)
            put("alertVolumePercent", p.alertVolumePercent)
            put("questionBankLockEnabled", p.questionBankLockEnabled)
            put("questionBankPin", p.questionBankPin)
            put("questionBankBiometricEnabled", p.questionBankBiometricEnabled)
            put("questionBankThemeDark", p.questionBankThemeDark)

            put("customLedgers", JSONArray().apply {
                p.effectiveLedgers().forEach { l ->
                    put(JSONObject().apply {
                        put("id", l.id)
                        put("title", l.title)
                        put("headerColorHex", l.headerColorHex)
                        if (l.customHeaders != null) {
                            put("customHeaders", serializeHeaders(l.customHeaders))
                        }
                    })
                }
            })
            put("activeLedgerId", p.activeLedgerId)
            put("autoScrollEnabled", p.autoScrollEnabled)
            put("autoScrollHighlightColorHex", p.autoScrollHighlightColorHex)
            put("appLockEnabled", p.appLockEnabled)
            put("appLockFingerprintEnabled", p.appLockFingerprintEnabled)
            put("appLockPassword", p.appLockPassword)
            put("safeGradeEditLockEnabled", p.safeGradeEditLockEnabled)
            put("splashEnabled", p.splashEnabled)
            put("splashShowName", p.splashShowName)
            put("splashShowLogo", p.splashShowLogo)
            put("splashLogoFileName", p.splashLogoFileName ?: JSONObject.NULL)
            put("splashGreeting", p.splashGreeting)
            put("splashEmoji", p.splashEmoji)
            put("profileCoverFileName", p.profileCoverFileName ?: JSONObject.NULL)
            put("teacherBio", p.teacherBio)
            put("liveScheduleBannerEnabled", p.liveScheduleBannerEnabled)
            put("floatingDhikrEnabled", p.floatingDhikrEnabled)
            put("floatingDhikrIntervalMinutes", p.floatingDhikrIntervalMinutes)
            put("floatingDhikrCustomText", p.floatingDhikrCustomText)
            put("morningAthkarNotifyEnabled", p.morningAthkarNotifyEnabled)
            put("eveningAthkarNotifyEnabled", p.eveningAthkarNotifyEnabled)
            put("morningAthkarHour", p.morningAthkarHour)
            put("morningAthkarMinute", p.morningAthkarMinute)
            put("eveningAthkarHour", p.eveningAthkarHour)
            put("eveningAthkarMinute", p.eveningAthkarMinute)
            put("availableBadges", JSONArray().apply { p.availableBadges.forEach { put(it) } })
            put("toolOrder", JSONArray().apply { p.toolOrder.forEach { put(it) } })
            put("toolCustomImages", JSONObject().apply {
                p.toolCustomImages.forEach { (k, v) -> put(k, v) }
            })
            put("toolCustomColors", JSONObject().apply {
                p.toolCustomColors.forEach { (k, v) -> put(k, v) }
            })
            put("toolWhiteBackdrop", JSONObject().apply {
                p.toolWhiteBackdrop.forEach { (k, v) -> put(k, v) }
            })
            put("quranLastSurahId", p.quranLastSurahId)
            put("quranLastAyahNumber", p.quranLastAyahNumber)
            put("quranColoredAyahs", JSONObject().apply {
                p.quranColoredAyahs.forEach { (k, v) -> put(k, v) }
            })
            put("prayerSettings", JSONObject().apply {
                val ps = p.prayerSettings
                put("cityName", ps.cityName)
                put("latitude", ps.latitude)
                put("longitude", ps.longitude)
                put("timezoneOffsetHours", ps.timezoneOffsetHours)
                put("fajrNotifyEnabled", ps.fajrNotifyEnabled)
                put("dhuhrNotifyEnabled", ps.dhuhrNotifyEnabled)
                put("asrNotifyEnabled", ps.asrNotifyEnabled)
                put("maghribNotifyEnabled", ps.maghribNotifyEnabled)
                put("ishaNotifyEnabled", ps.ishaNotifyEnabled)
            })
            put("analyticsShowExemptions", p.analyticsShowExemptions)
            put("analyticsShowGrade3", p.analyticsShowGrade3)
            put("analyticsShowPassFail", p.analyticsShowPassFail)
            put("analyticsShowCriticalCases", p.analyticsShowCriticalCases)
            put("heroBgMode", p.heroBgMode.id)
            put("heroBgSize", p.heroBgSize.id)
            put("heroBgFileName", p.heroBgFileName ?: JSONObject.NULL)
            put("heroBgOpacity", p.heroBgOpacity.toDouble())
            put("heroShowTeacherName", p.heroShowTeacherName)
            put("heroDuaText", p.heroDuaText)
            put("timetableBgColorHex", p.timetableBgColorHex)
            put("timetableAccentColorHex", p.timetableAccentColorHex)
            put("studentNamesColorHex", p.studentNamesColorHex)
            put("gradebookTableBgColorHex", p.gradebookTableBgColorHex)
            put("accountUsername", p.accountUsername)
            put("halfMinuteAlertEnabled", p.halfMinuteAlertEnabled)
            put("feedbackLockedUntilMillis", p.feedbackLockedUntilMillis)
            put("lastComplaintText", p.lastComplaintText)
            put("unlockedPointsTools", JSONArray().apply { p.unlockedPointsTools.forEach { put(it) } })
            put("hiddenTools", JSONArray().apply { p.hiddenTools.forEach { put(it) } })
            put("toolTileScale", p.toolTileScale.toDouble())
            put("appBgImageFileName", p.appBgImageFileName ?: JSONObject.NULL)
            put("sectionBgImageFiles", JSONObject().apply {
                p.sectionBgImageFiles.forEach { (k, v) -> put(k, v) }
            })
            put("dismissedNotificationIds", JSONArray().apply { p.dismissedNotificationIds.forEach { put(it) } })
            put("lastAppActiveMillis", p.lastAppActiveMillis)
            put("lastPasswordChangeMillis", p.lastPasswordChangeMillis)
            put("lastNameChangeMillis", p.lastNameChangeMillis)
            put("initialNameChosen", p.initialNameChosen)
            put("autoGeneratedUsername", p.autoGeneratedUsername)
            put("deviceInstallId", p.deviceInstallId)
            put("adminSectionUnlocked", p.adminSectionUnlocked)
            put("customColorsEnabled", p.customColorsEnabled)
            put("customTextColorHex", p.customTextColorHex)
            put("customButtonColorHex", p.customButtonColorHex)
            put("customBgColorHex", p.customBgColorHex)
            put("customCardAccentColorHex", p.customCardAccentColorHex)
            put("appliedInternalUpdateRevision", p.appliedInternalUpdateRevision)
            put("preferDefaultDesign", p.preferDefaultDesign)
            put("adminLocalExperimentalMode", p.adminLocalExperimentalMode)
            put("archivedTools", JSONArray().apply { p.archivedTools.forEach { put(it) } })
            put("favoriteTools", JSONArray().apply { p.favoriteTools.forEach { put(it) } })
            put("gradebookSimpleMode", p.gradebookSimpleMode)
            put("gradebookVisibleActions", JSONArray().apply { p.gradebookVisibleActions.forEach { put(it) } })
            put("chatActiveOnlineVisible", p.chatActiveOnlineVisible)
        })
        root.put("teachersClearedV2", true)
        root.put("studentsClearedV1", true)
        root.put("singleLedgerIndigoV1", true)
        root.put("navy102030YellowV1", true)
        root.put("cleanBiologySplashV1", true)
        root.put("design7DefaultV1", true)

        root.put("gradebookSettings", JSONObject().apply {
            val s = state.gradebookSettings
            put("monthsPerTerm", s.monthsPerTerm)
            put("dailyCalcMode", s.dailyCalcMode.id)
            put("splitMidYearOralWritten", s.splitMidYearOralWritten)
            put("enableSmartRounding", s.enableSmartRounding)
            put("passingScore", s.passingScore)
            put("exemptionThreshold", s.exemptionThreshold)
            put("hideTransferredStudents", s.hideTransferredStudents)
            put("includeTransferredInStats", s.includeTransferredInStats)
            put("headers", serializeHeaders(s.headers))
        })

        root.put("students", JSONArray().apply {
            state.students.forEach { st ->
                put(JSONObject().apply {
                    put("id", st.id)
                    put("orderNumber", st.orderNumber)
                    put("fullName", st.fullName)
                    put("gradeLevel", st.gradeLevel.id)
                    put("section", st.section.id)
                    put("birthDate", st.birthDate)
                    put("registryNumber", st.registryNumber)
                    put("phoneNumber", st.phoneNumber)
                    put("notes", st.notes)
                    put("colorTag", st.colorTag.name)
                    put("behaviorStatus", st.behaviorStatus.name)
                    put("behaviorScore", st.behaviorScore)
                    put("isInactive", st.isInactive)
                    put("isTransferred", st.isTransferred)
                    put("badges", JSONArray().apply { st.badges.forEach { put(it) } })
                    put("notebookStatus", st.notebookStatus.name)
                    put("dailyPrepDone", st.dailyPrepDone)
                    put("participationPoints", st.participationPoints)
                    put("homeworkPoints", st.homeworkPoints)
                    put("behaviorPoints", st.behaviorPoints)
                    put("weeklyPoints", st.weeklyPoints)
                    put("grades", serializeGrades(st.grades))
                    put("mathGrades", serializeGrades(st.mathGrades))
                    put("extraGrades", JSONObject().apply {
                        st.extraGrades.forEach { (ledgerId, gradesObj) ->
                            put(ledgerId, serializeGrades(gradesObj))
                        }
                    })
                })
            }
        })

        root.put("timetable", JSONArray().apply {
            state.timetable.forEach { cell ->
                put(JSONObject().apply {
                    put("day", cell.day.id)
                    put("period", cell.periodNumber)
                    put("grade", cell.gradeLevel.id)
                    put("section", cell.section.id)
                    put("subject", cell.subject)
                    put("teacher", cell.teacherName)
                })
            }
        })

        root.put("dayPeriodTimes", JSONObject().apply {
            SchoolDay.entries.forEach { day ->
                put(day.id, JSONArray().apply {
                    state.periodTimesForDay(day).forEach { pt ->
                        put(JSONObject().apply {
                            put("periodNumber", pt.periodNumber)
                            put("startHour", pt.startHour)
                            put("startMinute", pt.startMinute)
                            put("endHour", pt.endHour)
                            put("endMinute", pt.endMinute)
                        })
                    }
                })
            }
        })

        root.put("absences", JSONArray().apply {
            state.absences.forEach { ab ->
                put(JSONObject().apply {
                    put("id", ab.id)
                    put("studentId", ab.studentId)
                    put("dateStr", ab.dateStr)
                    put("reason", ab.reason)
                })
            }
        })

        root.put("vaultNotes", JSONArray().apply {
            state.vaultNotes.forEach { vn ->
                put(JSONObject().apply {
                    put("id", vn.id)
                    put("title", vn.title)
                    put("content", vn.content)
                    put("createdAtMillis", vn.createdAtMillis)
                })
            }
        })

        root.put("tasks", JSONArray().apply {
            state.tasks.forEach { task ->
                put(JSONObject().apply {
                    put("id", task.id)
                    put("title", task.title)
                    put("category", task.category)
                    put("isDone", task.isDone)
                    put("createdAtMillis", task.createdAtMillis)
                })
            }
        })

        root.put("chapters", JSONArray().apply {
            state.chapters.forEach { ch ->
                put(JSONObject().apply {
                    put("id", ch.id)
                    put("gradeLevel", ch.gradeLevel.id)
                    put("title", ch.title)
                    put("isCompleted", ch.isCompleted)
                })
            }
        })

        root.put("quickNotes", JSONArray().apply {
            state.quickNotes.forEach { qn ->
                put(JSONObject().apply {
                    put("id", qn.id)
                    put("title", qn.title)
                    put("content", qn.content)
                    put("createdAtMillis", qn.createdAtMillis)
                })
            }
        })

        root.put("libraryFiles", JSONArray().apply {
            state.libraryFiles.forEach { f ->
                put(JSONObject().apply {
                    put("id", f.id)
                    put("displayName", f.displayName)
                    put("localFileName", f.localFileName)
                    put("mimeType", f.mimeType)
                    put("addedAtMillis", f.addedAtMillis)
                    put("lastPageIndex", f.lastPageIndex)
                })
            }
        })

        root.put("dailyColumns", JSONArray().apply {
            state.dailyColumns.forEach { col ->
                put(JSONObject().apply {
                    put("id", col.id)
                    put("ledgerSubject", col.ledgerSubject.id)
                    put("slot", col.slot.id)
                    put("title", col.title)
                    put("maxScore", col.maxScore)
                    put("ledgerId", col.ledgerId)
                })
            }
        })

        root.put("dailyScores", JSONObject().apply {
            state.dailyScores.forEach { (k, v) -> put(k, v) }
        })

        root.put("questionChapters", JSONArray().apply {
            state.questionChapters.forEach { ch ->
                put(JSONObject().apply {
                    put("id", ch.id)
                    put("chapterNumber", ch.chapterNumber)
                    put("title", ch.title)
                    put("gradeWeight", ch.gradeWeight)
                })
            }
        })

        root.put("questionSections", JSONArray().apply {
            state.questionSections.forEach { sec ->
                put(JSONObject().apply {
                    put("id", sec.id)
                    put("title", sec.title)
                    put("colorHex", sec.colorHex)
                    put("isBuiltIn", sec.isBuiltIn)
                })
            }
        })

        root.put("bankQuestions", JSONArray().apply {
            state.bankQuestions.forEach { q ->
                put(JSONObject().apply {
                    put("id", q.id)
                    put("chapterId", q.chapterId)
                    put("sectionId", q.sectionId)
                    put("questionText", q.questionText)
                    put("answerText", q.answerText)
                    put("marks", q.marks)
                    put("imageFileName", q.imageFileName ?: JSONObject.NULL)
                    put("createdAtMillis", q.createdAtMillis)
                })
            }
        })

        root.put("studentPapers", JSONArray().apply {
            state.studentPapers.forEach { sp ->
                put(JSONObject().apply {
                    put("id", sp.id)
                    put("studentId", sp.studentId)
                    put("studentName", sp.studentName)
                    put("gradeLevel", sp.gradeLevel.id)
                    put("section", sp.section.id)
                    put("scoreText", sp.scoreText)
                    put("notes", sp.notes)
                    put("pageFileNames", JSONArray().apply { sp.pageFileNames.forEach { put(it) } })
                    put("themeColorHex", sp.themeColorHex)
                    put("createdAtMillis", sp.createdAtMillis)
                })
            }
        })

        root.put("examArchive", JSONArray().apply {
            state.examArchive.forEach { ex ->
                put(JSONObject().apply {
                    put("id", ex.id)
                    put("title", ex.title)
                    put("category", ex.category.id)
                    put("gradeLevel", ex.gradeLevel.id)
                    put("fileName", ex.fileName)
                    put("mimeType", ex.mimeType)
                    put("notes", ex.notes)
                    put("createdAtMillis", ex.createdAtMillis)
                })
            }
        })

        root.put("sectionStars", JSONArray().apply {
            state.sectionStars.forEach { star ->
                put(JSONObject().apply {
                    put("id", star.id)
                    put("studentId", star.studentId)
                    put("studentName", star.studentName)
                    put("gradeLevel", star.gradeLevel.id)
                    put("section", star.section.id)
                    put("customTitle", star.customTitle)
                    put("emblem", star.emblem)
                    put("photoFileName", star.photoFileName ?: JSONObject.NULL)
                    put("glowColorHex", star.glowColorHex)
                    put("orbitTier", star.orbitTier)
                })
            }
        })

        return root
    }

    private fun parseAppState(root: JSONObject): AppState {
        val pObj = root.optJSONObject("profile")
        val profile = if (pObj != null) {
            val wdArr = pObj.optJSONArray("workingDays")
            val parsedWorkingDays = if (wdArr != null && wdArr.length() > 0) {
                (0 until wdArr.length()).mapNotNull { idx ->
                    SchoolDay.entries.find { it.id == wdArr.optString(idx) }
                }.toSet().ifEmpty { defaultWorkingDays() }
            } else defaultWorkingDays()

            val specialty = SubjectSpecialty.entries.find { it.id == pObj.optString("subjectSpecialty") }
                ?: SubjectSpecialty.BIOLOGY
            val subjName = pObj.optString("subjectName", specialty.titleAr).ifBlank { specialty.titleAr }

            val ledgersArr = pObj.optJSONArray("customLedgers")
            val parsedLedgers = if (ledgersArr != null && ledgersArr.length() > 0) {
                (0 until ledgersArr.length()).mapNotNull { idx ->
                    val lo = ledgersArr.optJSONObject(idx) ?: return@mapNotNull null
                    val id = lo.optString("id").ifBlank { "ledger_$idx" }
                    val title = lo.optString("title").ifBlank { subjName }
                    val color = lo.optLong("headerColorHex", 0xFFFFD54FL)
                    val ch = lo.optJSONObject("customHeaders")?.let { parseHeaders(it) }
                    CustomLedgerDef(id = id, title = title, headerColorHex = color, customHeaders = ch)
                }.ifEmpty { defaultLedgersForSubject(subjName) }
            } else {
                defaultLedgersForSubject(subjName)
            }

            val badgesArr = pObj.optJSONArray("availableBadges")
            val parsedBadges = if (badgesArr != null && badgesArr.length() > 0) {
                (0 until badgesArr.length()).mapNotNull { badgesArr.optString(it).takeIf { s -> s.isNotBlank() } }
                    .ifEmpty { defaultAvailableBadges() }
            } else defaultAvailableBadges()

            val toolArr = pObj.optJSONArray("toolOrder")
            val parsedTools = if (toolArr != null) {
                (0 until toolArr.length()).mapNotNull { toolArr.optString(it).takeIf { s -> s.isNotBlank() } }
            } else emptyList()

            val toolImagesObj = pObj.optJSONObject("toolCustomImages")
            val parsedToolImages = mutableMapOf<String, String>()
            if (toolImagesObj != null) {
                val ks = toolImagesObj.keys()
                while (ks.hasNext()) {
                    val k = ks.next()
                    val v = toolImagesObj.optString(k)
                    if (v.isNotBlank()) parsedToolImages[k] = v
                }
            }

            val toolColorsObj = pObj.optJSONObject("toolCustomColors")
            val parsedToolColors = mutableMapOf<String, Long>()
            if (toolColorsObj != null) {
                val ks = toolColorsObj.keys()
                while (ks.hasNext()) {
                    val k = ks.next()
                    parsedToolColors[k] = toolColorsObj.optLong(k, 0L)
                }
            }

            val toolWhiteObj = pObj.optJSONObject("toolWhiteBackdrop")
            val parsedToolWhite = mutableMapOf<String, Boolean>()
            if (toolWhiteObj != null) {
                val ks = toolWhiteObj.keys()
                while (ks.hasNext()) {
                    val k = ks.next()
                    parsedToolWhite[k] = toolWhiteObj.optBoolean(k, true)
                }
            }

            val coloredObj = pObj.optJSONObject("quranColoredAyahs")
            val coloredMap = mutableMapOf<String, Long>()
            if (coloredObj != null) {
                val keys = coloredObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    coloredMap[k] = coloredObj.optLong(k, 0x33FFD54FL)
                }
            }

            val psObj = pObj.optJSONObject("prayerSettings")
            val prayerSettings = if (psObj != null) {
                PrayerLocationSettings(
                    cityName = psObj.optString("cityName", "الفلوجة - الأنبار"),
                    latitude = psObj.optDouble("latitude", 33.3491),
                    longitude = psObj.optDouble("longitude", 43.7859),
                    timezoneOffsetHours = psObj.optDouble("timezoneOffsetHours", 3.0),
                    fajrNotifyEnabled = psObj.optBoolean("fajrNotifyEnabled", false),
                    dhuhrNotifyEnabled = psObj.optBoolean("dhuhrNotifyEnabled", false),
                    asrNotifyEnabled = psObj.optBoolean("asrNotifyEnabled", false),
                    maghribNotifyEnabled = psObj.optBoolean("maghribNotifyEnabled", false),
                    ishaNotifyEnabled = psObj.optBoolean("ishaNotifyEnabled", false)
                )
            } else PrayerLocationSettings()

            val splashLogoRaw = if (pObj.has("splashLogoFileName") && !pObj.isNull("splashLogoFileName")) {
                pObj.optString("splashLogoFileName").takeIf { it.isNotBlank() }
            } else null
            val profileCoverRaw = if (pObj.has("profileCoverFileName") && !pObj.isNull("profileCoverFileName")) {
                pObj.optString("profileCoverFileName").takeIf { it.isNotBlank() }
            } else null
            val heroBgFileRaw = if (pObj.has("heroBgFileName") && !pObj.isNull("heroBgFileName")) {
                pObj.optString("heroBgFileName").takeIf { it.isNotBlank() }
            } else null

            val isNavy102030Migrated = root.optBoolean("navy102030YellowV1", false)
            val isDesign7Migrated = root.optBoolean("design7DefaultV1", false)
            val rawTextTone = AppTextTone.entries.find { it.id == pObj.optString("textTone") }
            val resolvedPalette = if (!isDesign7Migrated) {
                ThemePalette.DESIGN_7_MIDNIGHT_NEON
            } else {
                ThemePalette.entries.find { it.id == pObj.optString("themePalette") }
                    ?: ThemePalette.DESIGN_7_MIDNIGHT_NEON
            }
            val resolvedTextTone = when {
                !isDesign7Migrated -> AppTextTone.AUTO
                rawTextTone != null -> rawTextTone
                else -> AppTextTone.AUTO
            }
            val resolvedDarkMode = if (!isDesign7Migrated) true else pObj.optBoolean("isDarkMode", true)

            TeacherProfile(
                teacherName = pObj.optString("teacherName", ""),
                timetableTeacherKey = pObj.optString("timetableTeacherKey", ""),
                schoolName = pObj.optString("schoolName", "مدرستي"),
                subjectName = subjName,
                subjectSpecialty = specialty,
                preferredGrade = GradeLevel.entries.find { it.id == pObj.optString("preferredGrade") }
                    ?: GradeLevel.FIRST,
                academicYear = pObj.optString("academicYear", "2026 - 2027"),
                showOnlyMyLessons = pObj.optBoolean("showOnlyMyLessons", false),
                workingDays = parsedWorkingDays,
                notificationsMasterEnabled = pObj.optBoolean("notificationsMasterEnabled", true),
                lessonNotificationsEnabled = pObj.optBoolean("lessonNotificationsEnabled", true),
                notifyMinutesBefore = pObj.optInt("notifyMinutesBefore", 1).coerceIn(1, 15),
                periodDurationMinutes = pObj.optInt("periodDurationMinutes", 40),
                breakDurationMinutes = pObj.optInt("breakDurationMinutes", 5),
                schoolStartHour = pObj.optInt("schoolStartHour", 8),
                schoolStartMinute = pObj.optInt("schoolStartMinute", 0),
                themePalette = resolvedPalette,
                useSpecialtyTheme = if (!isDesign7Migrated) false else pObj.optBoolean("useSpecialtyTheme", false),
                textTone = resolvedTextTone,
                isDarkMode = resolvedDarkMode,
                neonGlowEnabled = if (!isDesign7Migrated) true else pObj.optBoolean("neonGlowEnabled", true),
                fontOption = AppFontOption.entries.find { it.id == pObj.optString("fontOption") }
                    ?: AppFontOption.CAIRO,
                fontScaleMultiplier = pObj.optDouble("fontScaleMultiplier", 1.0).toFloat().coerceIn(0.85f, 1.5f),
                vaultPin = pObj.optString("vaultPin", "1234"),
                teacherSelectedOnStartup = pObj.optBoolean("teacherSelectedOnStartup", false),
                enableGeneralLedger = pObj.optBoolean("enableGeneralLedger", true),
                enableMathLedger = pObj.optBoolean("enableMathLedger", false),
                activeLedgerSubject = LedgerSubject.entries.find { it.id == pObj.optString("activeLedgerSubject") }
                    ?: LedgerSubject.GENERAL,
                notifyBeforeLessonStart = pObj.optBoolean("notifyBeforeLessonStart", true),
                notifyBeforeLessonEnd = pObj.optBoolean("notifyBeforeLessonEnd", true),
                voiceAnnouncementEnabled = pObj.optBoolean("voiceAnnouncementEnabled", true),
                alertVolumePercent = pObj.optInt("alertVolumePercent", 95).coerceIn(20, 100),
                questionBankLockEnabled = pObj.optBoolean("questionBankLockEnabled", true),
                questionBankPin = pObj.optString("questionBankPin", "1234"),
                questionBankBiometricEnabled = pObj.optBoolean("questionBankBiometricEnabled", true),
                questionBankThemeDark = pObj.optBoolean("questionBankThemeDark", true),
                customLedgers = parsedLedgers,
                activeLedgerId = pObj.optString("activeLedgerId", parsedLedgers.first().id),
                autoScrollEnabled = pObj.optBoolean("autoScrollEnabled", false),
                autoScrollHighlightColorHex = pObj.optLong("autoScrollHighlightColorHex", 0xFF00E5FFL),
                appLockEnabled = pObj.optBoolean("appLockEnabled", false),
                appLockFingerprintEnabled = pObj.optBoolean("appLockFingerprintEnabled", true),
                appLockPassword = pObj.optString("appLockPassword", "1234"),
                safeGradeEditLockEnabled = pObj.optBoolean("safeGradeEditLockEnabled", false),
                splashEnabled = pObj.optBoolean("splashEnabled", true),
                splashShowName = pObj.optBoolean("splashShowName", true),
                splashShowLogo = pObj.optBoolean("splashShowLogo", true),
                splashLogoFileName = splashLogoRaw,
                splashGreeting = pObj.optString("splashGreeting", "بسم الله نبدأ — أهلاً وسهلاً أستاذنا الكريم"),
                splashEmoji = pObj.optString("splashEmoji", "").let { if (it == "🧬") "" else it },
                profileCoverFileName = profileCoverRaw,
                teacherBio = pObj.optString("teacherBio", ""),
                liveScheduleBannerEnabled = if (!isNavy102030Migrated) true else pObj.optBoolean("liveScheduleBannerEnabled", true),
                floatingDhikrEnabled = pObj.optBoolean("floatingDhikrEnabled", false),
                floatingDhikrIntervalMinutes = pObj.optInt("floatingDhikrIntervalMinutes", 60).coerceIn(5, 240),
                floatingDhikrCustomText = pObj.optString(
                    "floatingDhikrCustomText",
                    "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ"
                ),
                morningAthkarNotifyEnabled = pObj.optBoolean("morningAthkarNotifyEnabled", false),
                eveningAthkarNotifyEnabled = pObj.optBoolean("eveningAthkarNotifyEnabled", false),
                morningAthkarHour = pObj.optInt("morningAthkarHour", 6).coerceIn(4, 11),
                morningAthkarMinute = pObj.optInt("morningAthkarMinute", 30).coerceIn(0, 59),
                eveningAthkarHour = pObj.optInt("eveningAthkarHour", 17).coerceIn(14, 22),
                eveningAthkarMinute = pObj.optInt("eveningAthkarMinute", 30).coerceIn(0, 59),
                availableBadges = parsedBadges,
                toolOrder = parsedTools,
                toolCustomImages = parsedToolImages,
                toolCustomColors = parsedToolColors,
                toolWhiteBackdrop = parsedToolWhite,
                quranLastSurahId = pObj.optInt("quranLastSurahId", 1),
                quranLastAyahNumber = pObj.optInt("quranLastAyahNumber", 1),
                quranColoredAyahs = coloredMap,
                prayerSettings = prayerSettings,
                analyticsShowExemptions = pObj.optBoolean("analyticsShowExemptions", true),
                analyticsShowGrade3 = pObj.optBoolean("analyticsShowGrade3", true),
                analyticsShowPassFail = pObj.optBoolean("analyticsShowPassFail", true),
                analyticsShowCriticalCases = pObj.optBoolean("analyticsShowCriticalCases", true),
                heroBgMode = HeroBackgroundMode.entries.find { it.id == pObj.optString("heroBgMode") }
                    ?: HeroBackgroundMode.NAME_ONLY,
                heroBgSize = HeroBackgroundSize.entries.find { it.id == pObj.optString("heroBgSize") }
                    ?: HeroBackgroundSize.MINI,
                heroBgFileName = heroBgFileRaw,
                heroBgOpacity = pObj.optDouble("heroBgOpacity", 0.42).toFloat().coerceIn(0.15f, 1.0f),
                heroShowTeacherName = pObj.optBoolean("heroShowTeacherName", true),
                heroDuaText = pObj.optString("heroDuaText", "اللهم بارك لنا في علمنا وعملنا وطلبتنا"),
                timetableBgColorHex = pObj.optLong("timetableBgColorHex", 0L),
                timetableAccentColorHex = pObj.optLong("timetableAccentColorHex", 0L),
                studentNamesColorHex = pObj.optLong("studentNamesColorHex", 0L),
                gradebookTableBgColorHex = pObj.optLong("gradebookTableBgColorHex", 0L),
                accountUsername = pObj.optString("accountUsername", ""),
                halfMinuteAlertEnabled = pObj.optBoolean("halfMinuteAlertEnabled", true),
                feedbackLockedUntilMillis = pObj.optLong("feedbackLockedUntilMillis", 0L),
                lastComplaintText = pObj.optString("lastComplaintText", ""),
                unlockedPointsTools = pObj.optJSONArray("unlockedPointsTools")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                } ?: emptyList(),
                hiddenTools = pObj.optJSONArray("hiddenTools")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                } ?: emptyList(),
                toolTileScale = pObj.optDouble("toolTileScale", 1.0).toFloat().coerceIn(0.8f, 1.25f),
                appBgImageFileName = if (pObj.has("appBgImageFileName") && !pObj.isNull("appBgImageFileName")) {
                    pObj.optString("appBgImageFileName").takeIf { it.isNotBlank() }
                } else null,
                sectionBgImageFiles = buildMap {
                    val sBgObj = pObj.optJSONObject("sectionBgImageFiles")
                    if (sBgObj != null) {
                        val ks = sBgObj.keys()
                        while (ks.hasNext()) {
                            val k = ks.next()
                            val v = sBgObj.optString(k)
                            if (v.isNotBlank()) put(k, v)
                        }
                    }
                },
                dismissedNotificationIds = pObj.optJSONArray("dismissedNotificationIds")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                } ?: emptyList(),
                lastAppActiveMillis = pObj.optLong("lastAppActiveMillis", 0L),
                lastPasswordChangeMillis = pObj.optLong("lastPasswordChangeMillis", 0L),
                lastNameChangeMillis = pObj.optLong("lastNameChangeMillis", 0L),
                initialNameChosen = pObj.optBoolean("initialNameChosen", pObj.optString("teacherName", "").isNotBlank()),
                autoGeneratedUsername = pObj.optString("autoGeneratedUsername", ""),
                deviceInstallId = pObj.optString("deviceInstallId", ""),
                adminSectionUnlocked = pObj.optBoolean("adminSectionUnlocked", false),
                customColorsEnabled = pObj.optBoolean("customColorsEnabled", false),
                customTextColorHex = pObj.optLong("customTextColorHex", 0L),
                customButtonColorHex = pObj.optLong("customButtonColorHex", 0L),
                customBgColorHex = pObj.optLong("customBgColorHex", 0L),
                customCardAccentColorHex = pObj.optLong("customCardAccentColorHex", 0L),
                appliedInternalUpdateRevision = pObj.optLong("appliedInternalUpdateRevision", 0L),
                preferDefaultDesign = pObj.optBoolean("preferDefaultDesign", false),
                adminLocalExperimentalMode = pObj.optString("adminLocalExperimentalMode", "CLASSIC"),
                archivedTools = pObj.optJSONArray("archivedTools")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                } ?: emptyList(),
                favoriteTools = pObj.optJSONArray("favoriteTools")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                } ?: emptyList(),
                gradebookSimpleMode = pObj.optBoolean("gradebookSimpleMode", false),
                gradebookVisibleActions = pObj.optJSONArray("gradebookVisibleActions")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                } ?: emptyList(),
                chatActiveOnlineVisible = pObj.optBoolean("chatActiveOnlineVisible", true)
            )
        } else TeacherProfile()

        val gsObj = root.optJSONObject("gradebookSettings")
        val gs = if (gsObj != null) {
            GradebookSettings(
                monthsPerTerm = gsObj.optInt("monthsPerTerm", 2).coerceIn(1, 3),
                dailyCalcMode = DailyCalcMode.entries.find { it.id == gsObj.optString("dailyCalcMode") }
                    ?: DailyCalcMode.NEVER,
                splitMidYearOralWritten = gsObj.optBoolean("splitMidYearOralWritten", false),
                enableSmartRounding = gsObj.optBoolean("enableSmartRounding", true),
                passingScore = gsObj.optInt("passingScore", 50),
                exemptionThreshold = gsObj.optInt("exemptionThreshold", 90).coerceIn(85, 98),
                hideTransferredStudents = gsObj.optBoolean("hideTransferredStudents", true),
                includeTransferredInStats = gsObj.optBoolean("includeTransferredInStats", false),
                headers = parseHeaders(gsObj.optJSONObject("headers"))
            )
        } else GradebookSettings()

        val studentsArr = root.optJSONArray("students")
        val rawStudents = if (studentsArr != null && studentsArr.length() > 0) {
            (0 until studentsArr.length()).map { i ->
                val o = studentsArr.getJSONObject(i)
                val gObj = o.optJSONObject("grades")
                val mgObj = o.optJSONObject("mathGrades")
                val egObj = o.optJSONObject("extraGrades")
                val extraGradesMap = mutableMapOf<String, StudentGrades>()
                if (egObj != null) {
                    val keys = egObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        extraGradesMap[k] = parseGrades(egObj.optJSONObject(k))
                    }
                }
                val bArr = o.optJSONArray("badges")
                val badgesList = if (bArr != null) {
                    (0 until bArr.length()).mapNotNull { bArr.optString(it).takeIf { s -> s.isNotBlank() } }.take(5)
                } else emptyList()

                Student(
                    id = o.getString("id"),
                    orderNumber = o.optInt("orderNumber", i + 1),
                    fullName = o.getString("fullName"),
                    gradeLevel = GradeLevel.entries.find { it.id == o.optString("gradeLevel") } ?: GradeLevel.FIRST,
                    section = SectionLetter.entries.find { it.id == o.optString("section") } ?: SectionLetter.A,
                    birthDate = o.optString("birthDate", ""),
                    registryNumber = o.optString("registryNumber", ""),
                    phoneNumber = o.optString("phoneNumber", ""),
                    notes = o.optString("notes", ""),
                    colorTag = runCatching { StudentColorTag.valueOf(o.optString("colorTag")) }.getOrDefault(StudentColorTag.NONE),
                    behaviorStatus = runCatching { BehaviorStatus.valueOf(o.optString("behaviorStatus")) }.getOrDefault(BehaviorStatus.GOOD),
                    behaviorScore = o.optInt("behaviorScore", 10),
                    isInactive = o.optBoolean("isInactive", false),
                    isTransferred = o.optBoolean("isTransferred", false),
                    badges = badgesList,
                    notebookStatus = runCatching { NotebookStatus.valueOf(o.optString("notebookStatus")) }.getOrDefault(NotebookStatus.GOOD),
                    dailyPrepDone = o.optBoolean("dailyPrepDone", true),
                    participationPoints = o.optInt("participationPoints", 0),
                    homeworkPoints = o.optInt("homeworkPoints", 0),
                    behaviorPoints = o.optInt("behaviorPoints", 0),
                    weeklyPoints = o.optInt("weeklyPoints", 0),
                    grades = parseGrades(gObj),
                    mathGrades = parseGrades(mgObj),
                    extraGrades = extraGradesMap
                )
            }
        } else InitialStudentsData.buildAllStudents()

        val studentsCleared = root.optBoolean("studentsClearedV1", false)
        val students = if (!studentsCleared) {
            InitialStudentsData.stripLegacySeededStudents(rawStudents)
        } else rawStudents

        val ttArr = root.optJSONArray("timetable")
        val rawTimetable = if (ttArr != null) {
            (0 until ttArr.length()).map { i ->
                val o = ttArr.getJSONObject(i)
                TimetableCell(
                    day = SchoolDay.entries.find { it.id == o.optString("day") } ?: SchoolDay.SUNDAY,
                    periodNumber = o.optInt("period", 1),
                    gradeLevel = GradeLevel.entries.find { it.id == o.optString("grade") } ?: GradeLevel.FIRST,
                    section = SectionLetter.entries.find { it.id == o.optString("section") } ?: SectionLetter.A,
                    subject = o.optString("subject", ""),
                    teacherName = o.optString("teacher", "")
                )
            }
        } else InitialTimetableData.buildDefaultTimetable()

        val teachersCleared = root.optBoolean("teachersClearedV2", false)
        val timetable = if (!teachersCleared) {
            InitialTimetableData.stripLegacySeededTeachers(
                cells = rawTimetable,
                currentTeacherName = profile.timetableTeacherKey.ifBlank { profile.teacherName }
            )
        } else rawTimetable

        val dptObj = root.optJSONObject("dayPeriodTimes")
        val dayPeriodTimes = if (dptObj != null) {
            SchoolDay.entries.associateWith { day ->
                val arr = dptObj.optJSONArray(day.id)
                if (arr != null && arr.length() > 0) {
                    (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        PeriodTime(
                            periodNumber = o.getInt("periodNumber"),
                            startHour = o.getInt("startHour"),
                            startMinute = o.getInt("startMinute"),
                            endHour = o.getInt("endHour"),
                            endMinute = o.getInt("endMinute")
                        )
                    }
                } else defaultPeriodTimesForDay(day)
            }
        } else defaultDayPeriodTimes()

        val abArr = root.optJSONArray("absences")
        val absences = if (abArr != null) {
            (0 until abArr.length()).map { i ->
                val o = abArr.getJSONObject(i)
                AbsenceRecord(o.getString("id"), o.getString("studentId"), o.getString("dateStr"), o.optString("reason", "غياب"))
            }
        } else emptyList()

        val vnArr = root.optJSONArray("vaultNotes")
        val vaultNotes = if (vnArr != null) {
            (0 until vnArr.length()).map { i ->
                val o = vnArr.getJSONObject(i)
                VaultNote(o.getString("id"), o.getString("title"), o.getString("content"), o.optLong("createdAtMillis", 0L))
            }
        } else emptyList()

        val tArr = root.optJSONArray("tasks")
        val tasks = if (tArr != null) {
            (0 until tArr.length()).map { i ->
                val o = tArr.getJSONObject(i)
                TeacherTask(o.getString("id"), o.getString("title"), o.optString("category", "يومي"), o.optBoolean("isDone", false), o.optLong("createdAtMillis", 0L))
            }
        } else emptyList()

        val chArr = root.optJSONArray("chapters")
        val chapters = if (chArr != null) {
            (0 until chArr.length()).map { i ->
                val o = chArr.getJSONObject(i)
                CurriculumChapter(
                    o.getString("id"),
                    GradeLevel.entries.find { it.id == o.optString("gradeLevel") } ?: GradeLevel.FIRST,
                    o.getString("title"),
                    o.optBoolean("isCompleted", false)
                )
            }
        } else emptyList()

        val qnArr = root.optJSONArray("quickNotes")
        val quickNotes = if (qnArr != null) {
            (0 until qnArr.length()).map { i ->
                val o = qnArr.getJSONObject(i)
                QuickStickyNote(o.getString("id"), o.getString("title"), o.getString("content"), o.optLong("createdAtMillis", 0L))
            }
        } else emptyList()

        val libArr = root.optJSONArray("libraryFiles")
        val libFiles = if (libArr != null) {
            (0 until libArr.length()).map { i ->
                val o = libArr.getJSONObject(i)
                LibraryFileItem(
                    id = o.getString("id"),
                    displayName = o.getString("displayName"),
                    localFileName = o.getString("localFileName"),
                    mimeType = o.optString("mimeType", "*/*"),
                    addedAtMillis = o.optLong("addedAtMillis", 0L),
                    lastPageIndex = o.optInt("lastPageIndex", 0)
                )
            }
        } else emptyList()

        val dcArr = root.optJSONArray("dailyColumns")
        val dailyColumns = if (dcArr != null && dcArr.length() > 0) {
            (0 until dcArr.length()).map { i ->
                val o = dcArr.getJSONObject(i)
                val ls = LedgerSubject.entries.find { it.id == o.optString("ledgerSubject") } ?: LedgerSubject.GENERAL
                DailyColumnDef(
                    id = o.getString("id"),
                    ledgerSubject = ls,
                    slot = DailyPeriodSlot.entries.find { it.id == o.optString("slot") } ?: DailyPeriodSlot.T1_M1,
                    title = o.optString("title", "عمود يومي"),
                    maxScore = o.optDouble("maxScore", 25.0).coerceAtLeast(1.0),
                    ledgerId = o.optString("ledgerId", ls.id).ifBlank { ls.id }
                )
            }
        } else defaultDailyColumns()

        val dsObj = root.optJSONObject("dailyScores")
        val dailyScores = mutableMapOf<String, Double>()
        if (dsObj != null) {
            val keys = dsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                dailyScores[k] = dsObj.optDouble(k, 0.0)
            }
        }

        val qcArr = root.optJSONArray("questionChapters")
        val questionChapters = if (qcArr != null && qcArr.length() > 0) {
            (0 until qcArr.length()).map { i ->
                val o = qcArr.getJSONObject(i)
                QuestionChapter(
                    id = o.getString("id"),
                    chapterNumber = o.optInt("chapterNumber", i + 1),
                    title = o.optString("title", "الفصل ${i + 1}"),
                    gradeWeight = o.optInt("gradeWeight", 25).coerceAtLeast(0)
                )
            }
        } else defaultQuestionChapters()

        val qsArr = root.optJSONArray("questionSections")
        val questionSections = if (qsArr != null && qsArr.length() > 0) {
            (0 until qsArr.length()).map { i ->
                val o = qsArr.getJSONObject(i)
                QuestionSectionDef(
                    id = o.getString("id"),
                    title = o.optString("title", "قسم"),
                    colorHex = o.optLong("colorHex", 0xFF40D6B4),
                    isBuiltIn = o.optBoolean("isBuiltIn", false)
                )
            }
        } else defaultQuestionSections()

        val bqArr = root.optJSONArray("bankQuestions")
        val bankQuestions = if (bqArr != null) {
            (0 until bqArr.length()).map { i ->
                val o = bqArr.getJSONObject(i)
                val imgVal = if (o.has("imageFileName") && !o.isNull("imageFileName")) o.optString("imageFileName") else null
                BankQuestion(
                    id = o.getString("id"),
                    chapterId = o.getString("chapterId"),
                    sectionId = o.getString("sectionId"),
                    questionText = o.optString("questionText", ""),
                    answerText = o.optString("answerText", ""),
                    marks = o.optInt("marks", 5).coerceAtLeast(1),
                    imageFileName = imgVal?.takeIf { it.isNotBlank() },
                    createdAtMillis = o.optLong("createdAtMillis", 0L)
                )
            }
        } else emptyList()

        val spArr = root.optJSONArray("studentPapers")
        val studentPapers = if (spArr != null) {
            (0 until spArr.length()).map { i ->
                val o = spArr.getJSONObject(i)
                val pagesArr = o.optJSONArray("pageFileNames")
                val pages = if (pagesArr != null) {
                    (0 until pagesArr.length()).mapNotNull { pagesArr.optString(it).takeIf { s -> s.isNotBlank() } }
                } else emptyList()
                StudentPaperGroup(
                    id = o.getString("id"),
                    studentId = o.optString("studentId", ""),
                    studentName = o.optString("studentName", ""),
                    gradeLevel = GradeLevel.entries.find { it.id == o.optString("gradeLevel") } ?: GradeLevel.FIRST,
                    section = SectionLetter.entries.find { it.id == o.optString("section") } ?: SectionLetter.A,
                    scoreText = o.optString("scoreText", ""),
                    notes = o.optString("notes", ""),
                    pageFileNames = pages,
                    themeColorHex = o.optLong("themeColorHex", 0xFF1E88E5L),
                    createdAtMillis = o.optLong("createdAtMillis", 0L)
                )
            }
        } else emptyList()

        val exArr = root.optJSONArray("examArchive")
        val examArchive = if (exArr != null) {
            (0 until exArr.length()).map { i ->
                val o = exArr.getJSONObject(i)
                ExamArchiveItem(
                    id = o.getString("id"),
                    title = o.optString("title", "أسئلة امتحان"),
                    category = ExamArchiveCategory.entries.find { it.id == o.optString("category") }
                        ?: ExamArchiveCategory.MONTHLY,
                    gradeLevel = GradeLevel.entries.find { it.id == o.optString("gradeLevel") } ?: GradeLevel.FIRST,
                    fileName = o.optString("fileName", ""),
                    mimeType = o.optString("mimeType", "image/jpeg"),
                    notes = o.optString("notes", ""),
                    createdAtMillis = o.optLong("createdAtMillis", 0L)
                )
            }
        } else emptyList()

        val stArr = root.optJSONArray("sectionStars")
        val sectionStars = if (stArr != null) {
            (0 until stArr.length()).map { i ->
                val o = stArr.getJSONObject(i)
                val photoRaw = if (o.has("photoFileName") && !o.isNull("photoFileName")) o.optString("photoFileName") else null
                SectionStarEntry(
                    id = o.getString("id"),
                    studentId = o.optString("studentId", ""),
                    studentName = o.optString("studentName", ""),
                    gradeLevel = GradeLevel.entries.find { it.id == o.optString("gradeLevel") } ?: GradeLevel.FIRST,
                    section = SectionLetter.entries.find { it.id == o.optString("section") } ?: SectionLetter.A,
                    customTitle = o.optString("customTitle", "نجم الشعبة"),
                    emblem = o.optString("emblem", "⭐"),
                    photoFileName = photoRaw?.takeIf { it.isNotBlank() },
                    glowColorHex = o.optLong("glowColorHex", 0xFFFFD54FL),
                    orbitTier = o.optInt("orbitTier", 1).coerceIn(1, 3)
                )
            }
        } else emptyList()

        return AppState(
            profile = profile,
            gradebookSettings = gs,
            students = students,
            timetable = timetable,
            dayPeriodTimes = dayPeriodTimes,
            absences = absences,
            vaultNotes = vaultNotes,
            tasks = tasks,
            chapters = chapters,
            quickNotes = quickNotes,
            libraryFiles = libFiles,
            dailyColumns = dailyColumns,
            dailyScores = dailyScores,
            questionChapters = questionChapters,
            questionSections = questionSections,
            bankQuestions = bankQuestions,
            studentPapers = studentPapers,
            examArchive = examArchive,
            sectionStars = sectionStars
        )
    }

    private fun serializeGrades(g: StudentGrades): JSONObject = JSONObject().apply {
        putNullableDouble("t1m1", g.term1Month1)
        putNullableDouble("t1m2", g.term1Month2)
        putNullableDouble("t1m3", g.term1Month3)
        putNullableDouble("t1d", g.term1Daily)
        putNullableDouble("t1m1d", g.term1Month1Daily)
        putNullableDouble("t1m2d", g.term1Month2Daily)
        putNullableDouble("mid", g.midYearTotal)
        putNullableDouble("midO", g.midYearOral)
        putNullableDouble("midW", g.midYearWritten)
        putNullableDouble("t2m1", g.term2Month1)
        putNullableDouble("t2m2", g.term2Month2)
        putNullableDouble("t2m3", g.term2Month3)
        putNullableDouble("t2d", g.term2Daily)
        putNullableDouble("t2m1d", g.term2Month1Daily)
        putNullableDouble("t2m2d", g.term2Month2Daily)
        putNullableDouble("finalExam", g.finalExam)
    }

    private fun parseGrades(gObj: JSONObject?): StudentGrades = if (gObj != null) StudentGrades(
        term1Month1 = gObj.optNullableDouble("t1m1"),
        term1Month2 = gObj.optNullableDouble("t1m2"),
        term1Month3 = gObj.optNullableDouble("t1m3"),
        term1Daily = gObj.optNullableDouble("t1d"),
        term1Month1Daily = gObj.optNullableDouble("t1m1d"),
        term1Month2Daily = gObj.optNullableDouble("t1m2d"),
        midYearTotal = gObj.optNullableDouble("mid"),
        midYearOral = gObj.optNullableDouble("midO"),
        midYearWritten = gObj.optNullableDouble("midW"),
        term2Month1 = gObj.optNullableDouble("t2m1"),
        term2Month2 = gObj.optNullableDouble("t2m2"),
        term2Month3 = gObj.optNullableDouble("t2m3"),
        term2Daily = gObj.optNullableDouble("t2d"),
        term2Month1Daily = gObj.optNullableDouble("t2m1d"),
        term2Month2Daily = gObj.optNullableDouble("t2m2d"),
        finalExam = gObj.optNullableDouble("finalExam")
    ) else StudentGrades()

    private fun JSONObject.putNullableDouble(key: String, value: Double?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key)
    }
}
