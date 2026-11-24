package com.example.notification

import androidx.compose.ui.graphics.toArgb
import com.example.data.CourseColorPalette
import com.example.data.CourseOccurrenceStateEntity
import com.example.data.DateTimeUtils
import com.example.data.RepeatRule
import com.example.data.ScheduleCategory
import com.example.data.ScheduleDao
import com.example.data.ScheduleEntity
import com.example.data.TimetableDao
import com.example.data.UserPreferencesRepository
import kotlinx.coroutines.flow.first

/**
 * Single source of truth coordinating Room Schedules, Active Timetable Courses, AlarmManager,
 * and ColorOS 17 Fluid Cloud (Android 16+ Live Updates) notifications.
 */
class LiveUpdateCoordinator(
    private val scheduleDao: ScheduleDao,
    private val timetableDao: TimetableDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val alarmScheduler: ScheduleAlarmScheduler,
    private val liveUpdateManager: LiveUpdateManager,
    private val onWidgetRefresh: (suspend (Boolean) -> Unit)? = null,
) {

    suspend fun syncAll(
        nowMillis: Long = System.currentTimeMillis(),
        forceAlert: Boolean = false,
        rescheduleAlarms: Boolean = false,
    ) {
        liveUpdateManager.purgeExpiredNotificationIfNeeded(nowMillis)
        val settings = preferencesRepository.settings.first()
        liveUpdateManager.setPreferredDndLevel(settings.dndLevel)

        // 1. Maintain schedules (auto-complete expired tests & advance expired recurring schedules)
        val incompleteSchedules = scheduleDao.getAllIncompleteSchedules()
        var schedulesMutated = false
        for (item in incompleteSchedules) {
            if (item.isExpired(nowMillis)) {
                if (item.isTestSchedule) {
                    alarmScheduler.cancelAlarmsForSchedule(item.id)
                    alarmScheduler.cancelAlarmsForTestSchedule(item.id)
                    scheduleDao.updateSchedule(
                        item.copy(
                            isCompleted = true,
                            completedAtMillis = nowMillis,
                            dismissedLiveUpdate = true,
                        ),
                    )
                    schedulesMutated = true
                } else if (item.repeatRule != RepeatRule.NONE) {
                    completeScheduleInternal(item.id, nowMillis)
                    schedulesMutated = true
                }
            }
        }

        val freshSchedules = if (schedulesMutated) {
            scheduleDao.getAllIncompleteSchedules()
        } else {
            incompleteSchedules
        }

        val candidates = mutableListOf<LiveActivityItem>()

        freshSchedules.forEach { s ->
            if (!s.dismissedLiveUpdate) {
                val isCoursePreviewTest = s.isTestSchedule && s.category == ScheduleCategory.TEST
                val liveItem = LiveActivityItem(
                    sourceType = if (isCoursePreviewTest) LiveActivitySourceType.COURSE else LiveActivitySourceType.SCHEDULE,
                    sourceId = s.id,
                    dateKey = DateTimeUtils.formatDateKey(s.startTimeMillis),
                    title = s.title,
                    location = extractLocationFromNotes(s.notes),
                    notes = s.notes,
                    sectionLabel = if (isCoursePreviewTest) "临近课程演练" else "",
                    startTimeMillis = s.startTimeMillis,
                    endTimeMillis = s.endTimeMillis,
                    reminderMinutesBefore = s.reminderMinutesBefore,
                    isTest = s.isTestSchedule,
                    accentColorArgb = s.resolvedTagColor(settings.customScheduleTags).toArgb(),
                    showLocation = settings.fluidCloudShowLocation,
                    showTeacher = settings.fluidCloudShowTeacher,
                    showNotes = settings.fluidCloudShowNotes,
                    showLockscreenDetails = settings.fluidCloudLockscreenDetails,
                )
                candidates.add(liveItem)
                if (rescheduleAlarms && !s.isExpired(nowMillis)) {
                    alarmScheduler.scheduleAlarmsFor(liveItem, nowMillis)
                }
            }
        }

        // 2. Resolve upcoming active timetable courses into concrete LiveActivityItems (DST-safe)
        if (settings.courseReminderEnabled) {
            val activeTimetable = timetableDao.getActiveTimetable()
            if (activeTimetable != null) {
                val courses = timetableDao.getCoursesForTimetable(activeTimetable.id)
                val sectionTimes = timetableDao.getSectionTimes(activeTimetable.id)
                    .ifEmpty { CourseColorPalette.defaultSectionTimes(activeTimetable.id) }
                    .associateBy { it.sectionNumber }

                val dayOffsets = if (rescheduleAlarms) listOf(0, 1, 2) else listOf(0, 1)
                for (offset in dayOffsets) {
                    val dayMillis = DateTimeUtils.addDays(nowMillis, offset)
                    val dateKey = DateTimeUtils.formatDateKey(dayMillis)
                    val semesterWeek = DateTimeUtils.computeSemesterWeek(
                        activeTimetable.semesterStartMondayMillis,
                        dayMillis,
                        activeTimetable.totalWeeks,
                        settings.weekStartDay,
                    )
                    val isoDay = DateTimeUtils.isoDayOfWeek(dayMillis)
                    val stateMap = timetableDao.getCourseStatesForDate(dateKey).associateBy { it.courseId }

                    courses.filter { it.dayOfWeek == isoDay && it.isInWeek(semesterWeek) }.forEach { course ->
                        val state = stateMap[course.id]
                        if (state?.isCompleted == true || state?.dismissedLiveUpdate == true) return@forEach

                        val startSlot = sectionTimes[course.startSection] ?: return@forEach
                        val endSlot = sectionTimes[course.endSection] ?: startSlot
                        val snoozeMs = (state?.snoozeMinutes ?: 0) * 60_000L

                        val rawStart = DateTimeUtils.combineDateAndTime(
                            dayMillis,
                            startSlot.startHour,
                            startSlot.startMinute,
                        )
                        val rawEnd = DateTimeUtils.combineDateAndTime(
                            dayMillis,
                            endSlot.endHour,
                            endSlot.endMinute,
                        )
                        val startMillis = if (nowMillis >= rawStart) rawStart else rawStart + snoozeMs
                        val endMillis = (rawEnd + snoozeMs).coerceAtLeast(startMillis + 60_000L)
                        val secLabel = if (course.startSection == course.endSection) {
                            "第${course.startSection}节"
                        } else {
                            "第${course.startSection}-${course.endSection}节"
                        }

                        val liveItem = LiveActivityItem(
                            sourceType = LiveActivitySourceType.COURSE,
                            sourceId = course.id,
                            dateKey = dateKey,
                            title = course.name,
                            location = course.compactDisplayLocation(),
                            teacher = course.teacher,
                            notes = course.notes,
                            sectionLabel = secLabel,
                            startTimeMillis = startMillis,
                            endTimeMillis = endMillis,
                            reminderMinutesBefore = settings.courseReminderLeadMinutes,
                            isTest = false,
                            accentColorArgb = course.resolvedColorStyle().lightAccent.toArgb(),
                            showLocation = settings.fluidCloudShowLocation,
                            showTeacher = settings.fluidCloudShowTeacher,
                            showNotes = settings.fluidCloudShowNotes,
                            showLockscreenDetails = settings.fluidCloudLockscreenDetails,
                        )
                        if (liveItem.isLiveEligible(nowMillis)) {
                            candidates.add(liveItem)
                        }
                        if (rescheduleAlarms && endMillis > nowMillis) {
                            alarmScheduler.scheduleAlarmsFor(liveItem, nowMillis)
                        }
                    }
                }
            }
        }

        liveUpdateManager.syncLiveActivity(
            candidates = candidates,
            nowMillis = nowMillis,
            forceAlert = forceAlert,
        )

        if (rescheduleAlarms) {
            alarmScheduler.scheduleDailyRolloverAlarm(nowMillis)
        }

        onWidgetRefresh?.invoke(rescheduleAlarms || schedulesMutated)
    }

    /**
     * Triggers an immediate upcoming-course Live Activity preview (15s pre-class prep -> 60s in-progress)
     * using a real course from the active timetable (or fallback sample), so users can verify
     * ColorOS 17 Fluid Cloud course name, remaining time, and @location display across apps.
     */
    suspend fun triggerCourseLivePreview(
        fallbackTitle: String,
        fallbackLocation: String = "教A-301",
    ): Long {
        val now = System.currentTimeMillis()
        scheduleDao.getActiveTestSchedules().forEach { existingTest ->
            alarmScheduler.cancelAlarmsForSchedule(existingTest.id)
            alarmScheduler.cancelAlarmsForTestSchedule(existingTest.id)
            alarmScheduler.cancelCustomReReminder(isCourse = false, itemId = existingTest.id)
        }
        scheduleDao.deleteAllTestSchedules()

        val activeTimetable = timetableDao.getActiveTimetable()
        val sampleCourse = activeTimetable?.let { tt ->
            val courses = timetableDao.getCoursesForTimetable(tt.id)
            val todayIso = DateTimeUtils.isoDayOfWeek(now)
            courses.firstOrNull { it.dayOfWeek == todayIso } ?: courses.firstOrNull()
        }

        val courseTitle = sampleCourse?.name?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val courseLocation = sampleCourse?.location?.trim()?.removePrefix("@")
            ?.takeIf { it.isNotBlank() } ?: fallbackLocation

        val testSchedule = ScheduleEntity(
            title = courseTitle,
            notes = "@$courseLocation · 临近课程实况通知演练：前 15 秒展示课前倒计时，随后自动切入上课进行中阶段。",
            startTimeMillis = now + 15_000L,
            endTimeMillis = now + 75_000L,
            repeatRule = RepeatRule.NONE,
            reminderMinutesBefore = 1,
            category = ScheduleCategory.TEST,
            isCompleted = false,
            isTestSchedule = true,
            dismissedLiveUpdate = false,
        )
        val newId = scheduleDao.insertSchedule(testSchedule)
        syncAll(
            nowMillis = now,
            forceAlert = true,
            rescheduleAlarms = true,
        )
        return newId
    }

    /**
     * Immediately stops all active Live Update test schedules: cancels their alarms, removes
     * test records, dismisses the test Live Update notification, and restores any real active item.
     */
    suspend fun stopCourseLivePreview(nowMillis: Long = System.currentTimeMillis()) {
        val activeTests = scheduleDao.getActiveTestSchedules()
        activeTests.forEach { item ->
            alarmScheduler.cancelAlarmsForSchedule(item.id)
            alarmScheduler.cancelAlarmsForTestSchedule(item.id)
            alarmScheduler.cancelCustomReReminder(isCourse = false, itemId = item.id)
        }
        scheduleDao.deleteAllTestSchedules()
        liveUpdateManager.cancelLiveUpdate()
        syncAll(
            nowMillis = nowMillis,
            forceAlert = false,
            rescheduleAlarms = true,
        )
    }

    suspend fun handleCompleteAction(
        sourceType: LiveActivitySourceType,
        sourceId: Long,
        dateKey: String,
    ) {
        val now = System.currentTimeMillis()
        if (sourceType == LiveActivitySourceType.FOCUS) {
            liveUpdateManager.clearFocusLiveActivity()
            liveUpdateManager.restoreDndIfEnabledByApp()
            syncAll(nowMillis = now, forceAlert = false, rescheduleAlarms = false)
            return
        }
        val existingSchedule = scheduleDao.getScheduleById(sourceId)
        if (sourceType == LiveActivitySourceType.SCHEDULE || existingSchedule?.isTestSchedule == true) {
            completeScheduleInternal(sourceId, now)
        } else {
            val existing = timetableDao.getCourseState(sourceId, dateKey)
            timetableDao.upsertCourseState(
                CourseOccurrenceStateEntity(
                    courseId = sourceId,
                    dateKey = dateKey,
                    isCompleted = true,
                    snoozeMinutes = existing?.snoozeMinutes ?: 0,
                    dismissedLiveUpdate = true,
                ),
            )
        }
        liveUpdateManager.restoreDndIfEnabledByApp()
        syncAll(nowMillis = now, forceAlert = false, rescheduleAlarms = false)
    }

    suspend fun handleToggleDndAction() {
        liveUpdateManager.toggleDoNotDisturb()
        syncAll(nowMillis = System.currentTimeMillis(), forceAlert = false, rescheduleAlarms = false)
    }

    suspend fun handleSnooze10MinAction(
        sourceType: LiveActivitySourceType,
        sourceId: Long,
        dateKey: String,
    ) {
        val now = System.currentTimeMillis()
        val existingSchedule = scheduleDao.getScheduleById(sourceId)
        if (sourceType == LiveActivitySourceType.SCHEDULE || existingSchedule?.isTestSchedule == true) {
            val current = existingSchedule ?: return
            if (current.isCompleted) return
            val tenMin = 10 * 60_000L
            val updated = if (current.isInProgress(now)) {
                current.copy(endTimeMillis = current.endTimeMillis + tenMin, dismissedLiveUpdate = false)
            } else {
                current.copy(
                    startTimeMillis = current.startTimeMillis + tenMin,
                    endTimeMillis = current.endTimeMillis + tenMin,
                    dismissedLiveUpdate = false,
                )
            }
            scheduleDao.updateSchedule(updated)
        } else {
            val existing = timetableDao.getCourseState(sourceId, dateKey)
            timetableDao.upsertCourseState(
                CourseOccurrenceStateEntity(
                    courseId = sourceId,
                    dateKey = dateKey,
                    isCompleted = false,
                    snoozeMinutes = (existing?.snoozeMinutes ?: 0) + 10,
                    dismissedLiveUpdate = false,
                ),
            )
        }
        syncAll(nowMillis = now, forceAlert = false, rescheduleAlarms = true)
    }

    suspend fun handleDismissAction(
        sourceType: LiveActivitySourceType,
        sourceId: Long,
        dateKey: String,
    ) {
        if (sourceType == LiveActivitySourceType.FOCUS) {
            liveUpdateManager.clearFocusLiveActivity()
            return
        }
        val existingSchedule = scheduleDao.getScheduleById(sourceId)
        if (sourceType == LiveActivitySourceType.SCHEDULE || existingSchedule?.isTestSchedule == true) {
            val current = existingSchedule ?: return
            scheduleDao.updateSchedule(current.copy(dismissedLiveUpdate = true))
        } else {
            val existing = timetableDao.getCourseState(sourceId, dateKey)
            timetableDao.upsertCourseState(
                CourseOccurrenceStateEntity(
                    courseId = sourceId,
                    dateKey = dateKey,
                    isCompleted = existing?.isCompleted ?: false,
                    snoozeMinutes = existing?.snoozeMinutes ?: 0,
                    dismissedLiveUpdate = true,
                ),
            )
        }
        liveUpdateManager.cancelLiveUpdate()
    }

    private suspend fun completeScheduleInternal(scheduleId: Long, nowMillis: Long) {
        val schedule = scheduleDao.getScheduleById(scheduleId) ?: return
        if (schedule.isCompleted) return

        alarmScheduler.cancelAlarmsForSchedule(schedule.id)
        if (schedule.isTestSchedule) {
            alarmScheduler.cancelAlarmsForTestSchedule(schedule.id)
        }
        scheduleDao.updateSchedule(
            schedule.copy(
                isCompleted = true,
                completedAtMillis = nowMillis,
                dismissedLiveUpdate = true,
            ),
        )

        val nextRange = schedule.repeatRule.computeNextOccurrence(
            schedule.startTimeMillis,
            schedule.endTimeMillis,
        )
        if (nextRange != null) {
            val nextSchedule = schedule.copy(
                id = 0L,
                startTimeMillis = nextRange.first,
                endTimeMillis = nextRange.second,
                isCompleted = false,
                completedAtMillis = null,
                dismissedLiveUpdate = false,
                createdAtMillis = nowMillis,
            )
            val newId = scheduleDao.insertSchedule(nextSchedule)
            alarmScheduler.scheduleAlarmsFor(
                LiveActivityItem(
                    sourceType = LiveActivitySourceType.SCHEDULE,
                    sourceId = newId,
                    dateKey = DateTimeUtils.formatDateKey(nextSchedule.startTimeMillis),
                    title = nextSchedule.title,
                    location = extractLocationFromNotes(nextSchedule.notes),
                    startTimeMillis = nextSchedule.startTimeMillis,
                    endTimeMillis = nextSchedule.endTimeMillis,
                    reminderMinutesBefore = nextSchedule.reminderMinutesBefore,
                    accentColorArgb = nextSchedule.category.accentColor.toArgb(),
                ),
                nowMillis,
            )
        }
    }

    private fun extractLocationFromNotes(notes: String): String {
        val match = Regex("@([^\\s，。·,]+)").find(notes)
        return match?.groupValues?.get(0).orEmpty()
    }
}
