package com.example.smart

import com.example.data.CourseColorPalette
import com.example.data.CourseEntity
import com.example.data.DateTimeUtils
import com.example.data.ScheduleDao
import com.example.data.ScheduleEntity
import com.example.data.TimetableDao
import com.example.data.TimetableRepository
import com.example.data.UserPreferencesRepository
import com.example.notification.LiveUpdateCoordinator
import kotlinx.coroutines.flow.first

/**
 * Coordinates smart natural-language parsing (Custom OpenAI-compatible API -> automatic offline fallback),
 * live conflict checking against Room schedules & active timetable courses, and committing drafts
 * with immediate AlarmManager & Glance Widget synchronization.
 */
class SmartCaptureOrchestrator(
    private val scheduleDao: ScheduleDao,
    private val timetableDao: TimetableDao,
    private val timetableRepository: TimetableRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val liveUpdateCoordinator: LiveUpdateCoordinator,
) {

    suspend fun parseAndCheckConflicts(
        rawInput: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): SmartParseResult {
        val settings = preferencesRepository.settings.first()
        val activeTimetable = timetableRepository.ensureDefaultTimetableExists()
        val sectionTimes = timetableDao.getSectionTimes(activeTimetable.id)
            .ifEmpty { CourseColorPalette.defaultSectionTimes(activeTimetable.id) }
        val totalWeeks = activeTimetable.totalWeeks

        var source = ParseSource.OFFLINE_LOCAL
        var notice: String? = null
        var drafts: List<SmartParsedDraft> = emptyList()

        if (settings.customAiConfig.isReady) {
            val aiResult = OpenAiCompatibleClient.parseToDrafts(
                rawInput = rawInput,
                config = settings.customAiConfig,
                nowMillis = nowMillis,
                sectionTimes = sectionTimes,
                defaultTotalWeeks = totalWeeks,
            )
            if (aiResult.isSuccess) {
                drafts = aiResult.getOrDefault(emptyList())
                source = ParseSource.CUSTOM_OPENAI_API
            } else {
                source = ParseSource.OFFLINE_FALLBACK
                notice = "自定义接口暂不可用，已自动无缝回退至本地离线解析"
            }
        }

        if (drafts.isEmpty()) {
            drafts = ChineseNaturalLanguageParser.parse(
                rawInput = rawInput,
                nowMillis = nowMillis,
                sectionTimes = sectionTimes,
                defaultTotalWeeks = totalWeeks,
            )
        }

        val withConflicts = refreshConflicts(drafts)
        return SmartParseResult(
            drafts = withConflicts,
            source = source,
            notice = notice,
        )
    }

    suspend fun refreshConflicts(drafts: List<SmartParsedDraft>): List<SmartParsedDraft> {
        val schedules = scheduleDao.getAllIncompleteSchedules()
        val activeTimetable = timetableDao.getActiveTimetable()
        val courses = activeTimetable?.let { timetableDao.getCoursesForTimetable(it.id) }.orEmpty()
        val sectionTimes = activeTimetable?.let { timetableDao.getSectionTimes(it.id) }.orEmpty()

        return drafts.map { draft ->
            val detected = ConflictDetector.detectConflicts(
                draft = draft,
                existingSchedules = schedules,
                activeTimetable = activeTimetable,
                existingCourses = courses,
                sectionTimes = sectionTimes,
            )
            draft.copy(conflicts = detected)
        }
    }

    /**
     * Persists confirmed drafts to Room, registers exact AlarmManager alarms,
     * and refreshes Live Updates & Home Screen Glance Widget.
     * Returns Pair(createdScheduleCount, createdCourseCount).
     */
    suspend fun commitDrafts(drafts: List<SmartParsedDraft>): Pair<Int, Int> {
        val activeTimetable = timetableRepository.ensureDefaultTimetableExists()
        var scheduleCount = 0
        var courseCount = 0
        var colorOffset = timetableDao.getCoursesForTimetable(activeTimetable.id).size

        for (draft in drafts) {
            if (draft.title.isBlank()) continue
            if (draft.draftType == DraftType.COURSE) {
                val days = draft.daysOfWeek.ifEmpty {
                    listOf(DateTimeUtils.isoDayOfWeek(draft.startTimeMillis))
                }
                val noteText = buildNotes(draft)
                for (day in days) {
                    timetableDao.insertCourse(
                        CourseEntity(
                            timetableId = activeTimetable.id,
                            name = draft.title.trim(),
                            location = draft.location.trim().removePrefix("@"),
                            teacher = "",
                            dayOfWeek = day.coerceIn(1, 7),
                            startSection = draft.startSection.coerceIn(1, 16),
                            endSection = draft.endSection.coerceIn(draft.startSection.coerceIn(1, 16), 16),
                            startWeek = draft.startWeek.coerceIn(1, activeTimetable.totalWeeks),
                            endWeek = draft.endWeek.coerceIn(draft.startWeek.coerceIn(1, activeTimetable.totalWeeks), activeTimetable.totalWeeks),
                            weekType = draft.weekType,
                            colorIndex = colorOffset % CourseColorPalette.styles.size,
                            notes = noteText,
                        ),
                    )
                    courseCount++
                }
                colorOffset++
            } else {
                val entity = ScheduleEntity(
                    title = draft.title.trim(),
                    notes = buildNotes(draft),
                    startTimeMillis = draft.startTimeMillis,
                    endTimeMillis = draft.endTimeMillis.coerceAtLeast(draft.startTimeMillis + 60_000L),
                    repeatRule = draft.repeatRule,
                    reminderMinutesBefore = draft.reminderMinutesBefore,
                    category = draft.category,
                    isCompleted = false,
                    isTestSchedule = false,
                    dismissedLiveUpdate = false,
                )
                scheduleDao.insertSchedule(entity)
                scheduleCount++
            }
        }

        // Register exact AlarmManager triggers + refresh Fluid Cloud + refresh Glance Widget
        liveUpdateCoordinator.syncAll(
            nowMillis = System.currentTimeMillis(),
            forceAlert = false,
            rescheduleAlarms = true,
        )

        return scheduleCount to courseCount
    }

    private fun buildNotes(draft: SmartParsedDraft): String {
        val parts = mutableListOf<String>()
        val cleanLoc = draft.location.trim().removePrefix("@")
        if (cleanLoc.isNotEmpty()) parts.add("@$cleanLoc")
        if (draft.enableDnd) parts.add("[免打扰]")
        val extra = draft.notes
            .replace("@$cleanLoc", "")
            .replace("[免打扰]", "")
            .replace("·", "")
            .trim()
        if (extra.isNotEmpty()) parts.add(extra)
        return parts.joinToString(" · ")
    }
}
