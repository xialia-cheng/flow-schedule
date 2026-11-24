package com.example.data

import androidx.room.withTransaction
import com.example.notification.LiveUpdateCoordinator
import com.example.notification.ScheduleAlarmScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

enum class BackupRestoreMode(val label: String) {
    SMART_MERGE("智能合并（自动去重）"),
    OVERWRITE_ALL("清空并覆盖恢复"),
}

data class BackupRestoreResult(
    val coursesImported: Int,
    val schedulesImported: Int,
    val skippedDuplicates: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableRepository(
    private val timetableDao: TimetableDao,
    private val scheduleDao: ScheduleDao,
    private val alarmScheduler: ScheduleAlarmScheduler,
    private val liveUpdateCoordinator: LiveUpdateCoordinator,
    private val database: ScheduleDatabase? = null,
    private val preferencesRepository: UserPreferencesRepository? = null,
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

    suspend fun moveCourseToDay(course: CourseEntity, targetDayOfWeek: Int) {
        alarmScheduler.cancelAlarmsForCourse(course.id)
        timetableDao.updateCourse(course.copy(dayOfWeek = targetDayOfWeek.coerceIn(1, 7)))
        liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
    }

    suspend fun updateCourseColor(
        course: CourseEntity,
        sameNameSiblings: List<CourseEntity>,
        colorIndex: Int,
        customColorHex: String = "",
    ) {
        val normalizedHex = CourseColorPalette.parseHexColorOrNull(customColorHex)
            ?.let { CourseColorPalette.formatHex(it) }
            ?: ""
        timetableDao.updateCourse(
            course.copy(colorIndex = colorIndex, customColorHex = normalizedHex),
        )
        sameNameSiblings.forEach { sibling ->
            if (sibling.id != course.id) {
                timetableDao.updateCourse(
                    sibling.copy(colorIndex = colorIndex, customColorHex = normalizedHex),
                )
            }
        }
        liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
    }

    fun scheduleCourseReReminder(course: CourseEntity, delayMinutes: Int): Long {
        val secLabel = if (course.startSection == course.endSection) {
            "第${course.startSection}节"
        } else {
            "第${course.startSection}-${course.endSection}节"
        }
        val locPart = course.compactDisplayLocation().let { if (it.isNotEmpty()) " $it" else "" }
        return alarmScheduler.scheduleCustomReReminder(
            isCourse = true,
            itemId = course.id,
            title = "课程重提醒 · ${course.name}",
            subtitle = "${DateTimeUtils.dayOfWeekFullChinese(course.dayOfWeek)} $secLabel$locPart",
            delayMinutes = delayMinutes,
        )
    }

    fun cancelCourseReReminder(courseId: Long) {
        alarmScheduler.cancelCustomReReminder(isCourse = true, itemId = courseId)
    }

    fun getCourseReReminderMillis(courseId: Long): Long? {
        return alarmScheduler.getActiveReReminderMillis(isCourse = true, itemId = courseId)
    }

    suspend fun deleteCourse(courseId: Long) {
        alarmScheduler.cancelAlarmsForCourse(courseId)
        alarmScheduler.cancelCustomReReminder(isCourse = true, itemId = courseId)
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
        val schedules = scheduleDao.getAllSchedules()
        val settings = preferencesRepository?.settings?.first()
        val lunchBreakBackup = settings?.let {
            LunchBreakBackupConfig(
                showLunchBreak = it.showLunchBreak,
                title = it.lunchBreakTitle,
                afterSection = it.lunchAfterSection,
                startTime = it.lunchBreakStartTime,
                endTime = it.lunchBreakEndTime,
            )
        }
        return TimetableImportExport.exportFullBackupJson(
            FullBackupPayload(
                timetables = timetables,
                courses = courses,
                sectionTimes = times,
                schedules = schedules,
                lunchBreakBackup = lunchBreakBackup,
            ),
        )
    }

    suspend fun restoreFullBackupJson(
        jsonText: String,
        mode: BackupRestoreMode = BackupRestoreMode.SMART_MERGE,
    ): Pair<Int, Int>? {
        val res = restoreFullBackupWithMode(jsonText, mode) ?: return null
        return res.coursesImported to res.schedulesImported
    }

    suspend fun restoreFullBackupWithMode(
        jsonText: String,
        mode: BackupRestoreMode = BackupRestoreMode.SMART_MERGE,
    ): BackupRestoreResult? {
        val payload = TimetableImportExport.parseFullBackupJson(jsonText) ?: return null
        if (mode == BackupRestoreMode.OVERWRITE_ALL &&
            payload.timetables.isEmpty() &&
            payload.courses.isEmpty() &&
            payload.schedules.isEmpty()
        ) {
            return null
        }

        val executeBlock: suspend () -> BackupRestoreResult = {
            when (mode) {
                BackupRestoreMode.OVERWRITE_ALL -> applyOverwriteRestore(payload)
                BackupRestoreMode.SMART_MERGE -> applySmartMergeRestore(payload)
            }
        }

        val result = if (database != null) {
            database.withTransaction { executeBlock() }
        } else {
            executeBlock()
        }

        payload.lunchBreakBackup?.let { lb ->
            preferencesRepository?.let { prefs ->
                prefs.setShowLunchBreak(lb.showLunchBreak)
                prefs.setLunchBreakConfig(
                    title = lb.title,
                    afterSection = lb.afterSection,
                    startTime = lb.startTime,
                    endTime = lb.endTime,
                )
            }
        }

        liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
        return result
    }

    private suspend fun applyOverwriteRestore(payload: FullBackupPayload): BackupRestoreResult {
        timetableDao.getAllCourses().forEach { alarmScheduler.cancelAlarmsForCourse(it.id) }
        scheduleDao.getAllSchedules().forEach { alarmScheduler.cancelAlarmsForSchedule(it.id) }
        scheduleDao.deleteAllSchedules()
        timetableDao.deleteAllTimetables()

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
        val deduplicatedCourses = deduplicateCourses(
            payload.courses.map { c ->
                c.copy(id = 0L, timetableId = idMapping[c.timetableId] ?: fallbackId)
            },
        )
        if (deduplicatedCourses.isNotEmpty()) {
            timetableDao.insertCourses(deduplicatedCourses)
        }

        val deduplicatedSchedules = deduplicateSchedules(payload.schedules)
        deduplicatedSchedules.forEach { s ->
            scheduleDao.insertSchedule(s.copy(id = 0L))
        }

        val skipped = (payload.courses.size - deduplicatedCourses.size) +
            (payload.schedules.size - deduplicatedSchedules.size)
        return BackupRestoreResult(
            coursesImported = deduplicatedCourses.size,
            schedulesImported = deduplicatedSchedules.size,
            skippedDuplicates = skipped.coerceAtLeast(0),
        )
    }

    private suspend fun applySmartMergeRestore(payload: FullBackupPayload): BackupRestoreResult {
        val existingTimetables = timetableDao.getAllTimetables().toMutableList()
        val idMapping = mutableMapOf<Long, Long>()

        if (payload.timetables.isNotEmpty()) {
            payload.timetables.forEach { tt ->
                val matchedExisting = existingTimetables.firstOrNull {
                    it.name.trim().equals(tt.name.trim(), ignoreCase = true)
                }
                val targetId = if (matchedExisting != null) {
                    matchedExisting.id
                } else {
                    val createdId = timetableDao.insertTimetable(tt.copy(id = 0L, isActive = false))
                    existingTimetables.add(tt.copy(id = createdId, isActive = false))
                    val matchingTimes = payload.sectionTimes.filter { it.timetableId == tt.id }
                    timetableDao.upsertSectionTimes(
                        if (matchingTimes.isNotEmpty()) {
                            matchingTimes.map { it.copy(timetableId = createdId) }
                        } else {
                            CourseColorPalette.defaultSectionTimes(createdId)
                        },
                    )
                    createdId
                }
                idMapping[tt.id] = targetId
            }
            if (existingTimetables.none { it.isActive }) {
                val activeOld = payload.timetables.firstOrNull { it.isActive } ?: payload.timetables.first()
                idMapping[activeOld.id]?.let { timetableDao.setActiveTimetable(it) }
            }
        }

        val fallbackId = ensureDefaultTimetableExists().id
        val existingCourseKeys = timetableDao.getAllCourses()
            .map { courseDedupKey(it) }
            .toMutableSet()

        val coursesToInsert = mutableListOf<CourseEntity>()
        var skippedDuplicates = 0
        payload.courses.forEach { raw ->
            val remapped = raw.copy(
                id = 0L,
                timetableId = idMapping[raw.timetableId] ?: fallbackId,
            )
            val key = courseDedupKey(remapped)
            if (existingCourseKeys.add(key)) {
                coursesToInsert.add(remapped)
            } else {
                skippedDuplicates++
            }
        }
        if (coursesToInsert.isNotEmpty()) {
            timetableDao.insertCourses(coursesToInsert)
        }

        val existingScheduleKeys = scheduleDao.getAllSchedules()
            .map { scheduleDedupKey(it) }
            .toMutableSet()

        var importedSchedules = 0
        payload.schedules.forEach { s ->
            val key = scheduleDedupKey(s)
            if (existingScheduleKeys.add(key)) {
                scheduleDao.insertSchedule(s.copy(id = 0L))
                importedSchedules++
            } else {
                skippedDuplicates++
            }
        }

        return BackupRestoreResult(
            coursesImported = coursesToInsert.size,
            schedulesImported = importedSchedules,
            skippedDuplicates = skippedDuplicates,
        )
    }

    private fun courseDedupKey(c: CourseEntity): String = listOf(
        c.timetableId.toString(),
        c.name.trim(),
        c.dayOfWeek.toString(),
        c.startSection.toString(),
        c.endSection.toString(),
        c.startWeek.toString(),
        c.endWeek.toString(),
        c.weekType.name,
        c.customWeeksCsv.trim(),
    ).joinToString("|")

    private fun scheduleDedupKey(s: ScheduleEntity): String = listOf(
        s.title.trim(),
        s.startTimeMillis.toString(),
        s.endTimeMillis.toString(),
        s.repeatRule.name,
    ).joinToString("|")

    private fun deduplicateCourses(courses: List<CourseEntity>): List<CourseEntity> {
        val seen = HashSet<String>()
        return courses.filter { seen.add(courseDedupKey(it)) }
    }

    private fun deduplicateSchedules(schedules: List<ScheduleEntity>): List<ScheduleEntity> {
        val seen = HashSet<String>()
        return schedules.filter { seen.add(scheduleDedupKey(it)) }
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
