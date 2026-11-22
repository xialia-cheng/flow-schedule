package com.example.data

import com.example.notification.LiveUpdateCoordinator
import com.example.notification.ScheduleAlarmScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableRepository(
    private val timetableDao: TimetableDao,
    private val scheduleDao: ScheduleDao,
    private val alarmScheduler: ScheduleAlarmScheduler,
    private val liveUpdateCoordinator: LiveUpdateCoordinator,
) {
    val allTimetables: Flow<List<TimetableEntity>> = timetableDao.observeAllTimetables()
    val activeTimetable: Flow<TimetableEntity?> = timetableDao.observeActiveTimetable()
    val studyTasks: Flow<List<StudyTaskEntity>> = timetableDao.observeStudyTasks()

    val activeCourses: Flow<List<CourseEntity>> = activeTimetable.flatMapLatest { tt ->
        if (tt == null) flowOf(emptyList()) else timetableDao.observeCoursesForTimetable(tt.id)
    }

    val activeSectionTimes: Flow<List<SectionTimeEntity>> = activeTimetable.flatMapLatest { tt ->
        if (tt == null) flowOf(emptyList()) else timetableDao.observeSectionTimes(tt.id)
    }

    fun observeCourseStatesForDate(dateKey: String): Flow<List<CourseOccurrenceStateEntity>> {
        return timetableDao.observeCourseStatesForDate(dateKey)
    }

    suspend fun ensureDefaultTimetableExists(): TimetableEntity {
        val existing = timetableDao.getActiveTimetable() ?: timetableDao.getAllTimetables().firstOrNull()
        if (existing != null) {
            if (!existing.isActive) {
                timetableDao.setActiveTimetable(existing.id)
            }
            if (timetableDao.getSectionTimes(existing.id).isEmpty()) {
                timetableDao.upsertSectionTimes(CourseColorPalette.defaultSectionTimes(existing.id))
            }
            return existing.copy(isActive = true)
        }

        val defaultMonday = DateTimeUtils.mondayOfWeekMillis(System.currentTimeMillis())
        val defaultTimetable = TimetableEntity(
            name = "2026 秋季课表",
            semesterStartMondayMillis = defaultMonday,
            totalWeeks = 20,
            isActive = true,
        )
        val id = timetableDao.insertTimetable(defaultTimetable)
        timetableDao.upsertSectionTimes(CourseColorPalette.defaultSectionTimes(id))
        return defaultTimetable.copy(id = id)
    }

    suspend fun createTimetable(name: String, totalWeeks: Int = 20, activate: Boolean = true): Long {
        val monday = DateTimeUtils.mondayOfWeekMillis(System.currentTimeMillis())
        val entity = TimetableEntity(
            name = name.trim().ifEmpty { "新建课表" },
            semesterStartMondayMillis = monday,
            totalWeeks = totalWeeks.coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS),
            isActive = activate,
        )
        val id = timetableDao.insertTimetable(entity)
        timetableDao.upsertSectionTimes(CourseColorPalette.defaultSectionTimes(id))
        if (activate) {
            timetableDao.setActiveTimetable(id)
        }
        liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
        return id
    }

    suspend fun switchTimetable(timetableId: Long) {
        timetableDao.setActiveTimetable(timetableId)
        liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
    }

    suspend fun updateTimetable(timetable: TimetableEntity) {
        timetableDao.updateTimetable(timetable)
        liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
    }

    suspend fun deleteTimetable(timetableId: Long): Boolean {
        val all = timetableDao.getAllTimetables()
        if (all.size <= 1) return false
        timetableDao.deleteTimetableById(timetableId)
        val remaining = timetableDao.getAllTimetables()
        if (remaining.none { it.isActive } && remaining.isNotEmpty()) {
            timetableDao.setActiveTimetable(remaining.first().id)
        }
        liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
        return true
    }

    suspend fun saveCourse(course: CourseEntity): Long {
        val id = if (course.id == 0L) {
            timetableDao.insertCourse(course)
        } else {
            timetableDao.updateCourse(course)
            course.id
        }
        liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
        return id
    }

    suspend fun deleteCourse(courseId: Long) {
        alarmScheduler.cancelAlarmsForCourse(courseId)
        timetableDao.deleteCourseById(courseId)
        liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
    }

    suspend fun saveSectionTimes(timetableId: Long, times: List<SectionTimeEntity>) {
        val normalized = BellScheduleGenerator.normalize(times, timetableId)
        timetableDao.replaceSectionTimes(timetableId, normalized)
        // Cancel existing alarms for all courses in this timetable so stale bell times never fire
        timetableDao.getCoursesForTimetable(timetableId).forEach { course ->
            alarmScheduler.cancelAlarmsForCourse(course.id)
        }
        liveUpdateCoordinator.syncAll(
            nowMillis = System.currentTimeMillis(),
            forceAlert = false,
            rescheduleAlarms = true,
        )
    }

    suspend fun getActiveSectionTimes(): List<SectionTimeEntity> {
        val active = ensureDefaultTimetableExists()
        val times = timetableDao.getSectionTimes(active.id).sortedBy { it.sectionNumber }
        return if (times.isNotEmpty()) times else CourseColorPalette.defaultSectionTimes(active.id)
    }

    suspend fun importCoursesIntoActive(
        courses: List<CourseEntity>,
        replaceExisting: Boolean,
        sectionTimes: List<SectionTimeEntity> = emptyList(),
    ): Int {
        val active = ensureDefaultTimetableExists()
        val maxSupportedSection = BellScheduleGenerator.MAX_SECTION_COUNT
        val sanitized = courses.map { course ->
            val safeDay = course.dayOfWeek.coerceIn(1, 7)
            val safeStartSec = course.startSection.coerceIn(1, maxSupportedSection)
            val safeEndSec = course.endSection.coerceIn(safeStartSec, maxSupportedSection)
            val safeStartWeek = course.startWeek.coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS)
            val safeEndWeek = course.endWeek.coerceIn(safeStartWeek, CourseEntity.MAX_TOTAL_WEEKS)
            course.copy(
                id = 0L,
                timetableId = active.id,
                dayOfWeek = safeDay,
                startSection = safeStartSec,
                endSection = safeEndSec,
                startWeek = safeStartWeek,
                endWeek = safeEndWeek,
            )
        }
        val maxImportedWeek = sanitized.maxOfOrNull { it.endWeek } ?: active.totalWeeks
        if (maxImportedWeek > active.totalWeeks) {
            timetableDao.updateTimetable(
                active.copy(totalWeeks = maxImportedWeek.coerceIn(1, CourseEntity.MAX_TOTAL_WEEKS)),
            )
        }
        if (replaceExisting) {
            timetableDao.getCoursesForTimetable(active.id).forEach {
                alarmScheduler.cancelAlarmsForCourse(it.id)
            }
            timetableDao.replaceTimetableCourses(active.id, sanitized)
        } else {
            timetableDao.insertCourses(sanitized)
        }
        if (sectionTimes.isNotEmpty()) {
            val normalized = BellScheduleGenerator.normalize(sectionTimes, active.id)
            timetableDao.replaceSectionTimes(active.id, normalized)
        } else {
            val maxImportedSection = sanitized.maxOfOrNull { it.endSection } ?: 0
            val currentTimes = getActiveSectionTimes()
            if (maxImportedSection > currentTimes.size) {
                val expanded = BellScheduleGenerator.resize(
                    existing = currentTimes,
                    newCount = maxImportedSection,
                    timetableId = active.id,
                )
                timetableDao.replaceSectionTimes(active.id, expanded)
            }
        }
        liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
        return sanitized.size
    }

    suspend fun buildFullBackupJson(): String {
        val timetables = timetableDao.getAllTimetables()
        val courses = timetableDao.getAllCourses()
        val times = timetables.flatMap { timetableDao.getSectionTimes(it.id) }
        val schedules = scheduleDao.getAllIncompleteSchedules()
        return TimetableImportExport.exportFullBackupJson(
            FullBackupPayload(
                timetables = timetables,
                courses = courses,
                sectionTimes = times,
                schedules = schedules,
            ),
        )
    }

    suspend fun restoreFullBackupJson(jsonText: String): Pair<Int, Int>? {
        val payload = TimetableImportExport.parseFullBackupJson(jsonText) ?: return null
        val idMapping = mutableMapOf<Long, Long>()

        if (payload.timetables.isNotEmpty()) {
            payload.timetables.forEach { tt ->
                val newId = timetableDao.insertTimetable(tt.copy(id = 0L, isActive = false))
                idMapping[tt.id] = newId
                val matchingTimes = payload.sectionTimes.filter { it.timetableId == tt.id }
                timetableDao.upsertSectionTimes(
                    if (matchingTimes.isNotEmpty()) {
                        matchingTimes.map { it.copy(timetableId = newId) }
                    } else {
                        CourseColorPalette.defaultSectionTimes(newId)
                    },
                )
            }
            val activeOld = payload.timetables.firstOrNull { it.isActive } ?: payload.timetables.first()
            idMapping[activeOld.id]?.let { timetableDao.setActiveTimetable(it) }
        }

        val fallbackId = ensureDefaultTimetableExists().id
        val remappedCourses = payload.courses.map { c ->
            c.copy(id = 0L, timetableId = idMapping[c.timetableId] ?: fallbackId)
        }
        if (remappedCourses.isNotEmpty()) {
            timetableDao.insertCourses(remappedCourses)
        }

        payload.schedules.forEach { s ->
            scheduleDao.insertSchedule(s.copy(id = 0L))
        }

        liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
        return remappedCourses.size to payload.schedules.size
    }

    // --- Study Tasks ---
    suspend fun addStudyTask(title: String, courseTag: String, targetMinutes: Int) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        timetableDao.insertStudyTask(
            StudyTaskEntity(
                title = trimmed,
                courseTag = courseTag.trim(),
                targetMinutes = targetMinutes.coerceIn(1, 360),
            ),
        )
    }

    suspend fun toggleStudyTask(task: StudyTaskEntity) {
        timetableDao.updateStudyTask(task.copy(isCompleted = !task.isCompleted))
    }

    suspend fun deleteStudyTask(taskId: Long) {
        timetableDao.deleteStudyTaskById(taskId)
    }
}
