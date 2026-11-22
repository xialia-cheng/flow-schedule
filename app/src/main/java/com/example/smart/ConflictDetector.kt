package com.example.smart

import com.example.data.CourseColorPalette
import com.example.data.CourseEntity
import com.example.data.DateTimeUtils
import com.example.data.ScheduleEntity
import com.example.data.SectionTimeEntity
import com.example.data.TimetableEntity

/**
 * Pure local time-overlap detector checking candidate schedules/courses against
 * existing incomplete schedules and active timetable courses.
 */
object ConflictDetector {

    fun detectConflicts(
        draft: SmartParsedDraft,
        existingSchedules: List<ScheduleEntity>,
        activeTimetable: TimetableEntity?,
        existingCourses: List<CourseEntity>,
        sectionTimes: List<SectionTimeEntity>,
    ): List<TimeConflict> {
        val conflicts = mutableListOf<TimeConflict>()
        val timesBySec = sectionTimes
            .ifEmpty { CourseColorPalette.defaultSectionTimes(activeTimetable?.id ?: 0L) }
            .associateBy { it.sectionNumber }

        if (draft.draftType == DraftType.COURSE) {
            val targetDays = draft.daysOfWeek.ifEmpty {
                listOf(DateTimeUtils.isoDayOfWeek(draft.startTimeMillis))
            }
            // 1. Check against existing courses on overlapping days, weeks, and sections
            for (course in existingCourses) {
                if (course.dayOfWeek !in targetDays) continue
                val weeksOverlap = weeksIntersect(
                    draft.startWeek, draft.endWeek, draft.weekType,
                    course.startWeek, course.endWeek, course.weekType,
                )
                if (!weeksOverlap) continue
                val sectionsOverlap = draft.startSection <= course.endSection &&
                    draft.endSection >= course.startSection
                if (sectionsOverlap) {
                    val sSlot = timesBySec[course.startSection]
                    val eSlot = timesBySec[course.endSection] ?: sSlot
                    val range = if (sSlot != null && eSlot != null) {
                        "${sSlot.startLabel()} - ${eSlot.endLabel()}"
                    } else {
                        "第${course.startSection}-${course.endSection}节"
                    }
                    conflicts.add(
                        TimeConflict(
                            title = course.name,
                            timeRange = "${DateTimeUtils.dayOfWeekFullChinese(course.dayOfWeek)} $range",
                            isCourse = true,
                            detail = course.formattedLocation(),
                        ),
                    )
                }
            }

            // 2. Check against existing schedules on the draft's concrete start date
            for (schedule in existingSchedules) {
                if (schedule.isCompleted || schedule.isTestSchedule) continue
                if (intervalsOverlap(draft.startTimeMillis, draft.endTimeMillis, schedule.startTimeMillis, schedule.endTimeMillis)) {
                    conflicts.add(
                        TimeConflict(
                            title = schedule.title,
                            timeRange = DateTimeUtils.formatTimeRange(schedule.startTimeMillis, schedule.endTimeMillis),
                            isCourse = false,
                        ),
                    )
                }
            }
        } else {
            // Draft is a concrete SCHEDULE
            // 1. Check against existing incomplete schedules
            for (schedule in existingSchedules) {
                if (schedule.isCompleted || schedule.isTestSchedule) continue
                if (intervalsOverlap(draft.startTimeMillis, draft.endTimeMillis, schedule.startTimeMillis, schedule.endTimeMillis)) {
                    conflicts.add(
                        TimeConflict(
                            title = schedule.title,
                            timeRange = DateTimeUtils.formatTimeRange(schedule.startTimeMillis, schedule.endTimeMillis),
                            isCourse = false,
                        ),
                    )
                }
            }

            // 2. Check against active timetable courses occurring on that exact date
            if (activeTimetable != null) {
                val dayStart = DateTimeUtils.startOfDayMillis(draft.startTimeMillis)
                val isoDay = DateTimeUtils.isoDayOfWeek(draft.startTimeMillis)
                val semesterWeek = DateTimeUtils.computeSemesterWeek(
                    activeTimetable.semesterStartMondayMillis,
                    draft.startTimeMillis,
                    activeTimetable.totalWeeks,
                )

                for (course in existingCourses) {
                    if (course.dayOfWeek != isoDay || !course.isInWeek(semesterWeek)) continue
                    val startSlot = timesBySec[course.startSection] ?: continue
                    val endSlot = timesBySec[course.endSection] ?: startSlot
                    val courseStart = DateTimeUtils.combineDateAndTime(dayStart, startSlot.startHour, startSlot.startMinute)
                    val courseEnd = DateTimeUtils.combineDateAndTime(dayStart, endSlot.endHour, endSlot.endMinute)
                    if (intervalsOverlap(draft.startTimeMillis, draft.endTimeMillis, courseStart, courseEnd)) {
                        conflicts.add(
                            TimeConflict(
                                title = course.name,
                                timeRange = "${startSlot.startLabel()} - ${endSlot.endLabel()}",
                                isCourse = true,
                                detail = course.formattedLocation(),
                            ),
                        )
                    }
                }
            }
        }

        return conflicts.distinctBy { "${it.isCourse}_${it.title}_${it.timeRange}" }
    }

    private fun intervalsOverlap(startA: Long, endA: Long, startB: Long, endB: Long): Boolean {
        return startA < endB && endA > startB
    }

    private fun weeksIntersect(
        startA: Int,
        endA: Int,
        typeA: com.example.data.WeekType,
        startB: Int,
        endB: Int,
        typeB: com.example.data.WeekType,
    ): Boolean {
        val lo = maxOf(startA, startB)
        val hi = minOf(endA, endB)
        if (lo > hi) return false
        for (w in lo..hi) {
            val matchA = when (typeA) {
                com.example.data.WeekType.ALL -> true
                com.example.data.WeekType.ODD -> w % 2 == 1
                com.example.data.WeekType.EVEN -> w % 2 == 0
            }
            val matchB = when (typeB) {
                com.example.data.WeekType.ALL -> true
                com.example.data.WeekType.ODD -> w % 2 == 1
                com.example.data.WeekType.EVEN -> w % 2 == 0
            }
            if (matchA && matchB) return true
        }
        return false
    }
}
