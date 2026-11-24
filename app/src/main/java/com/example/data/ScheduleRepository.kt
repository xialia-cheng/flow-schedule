package com.example.data

import com.example.notification.LiveActivitySourceType
import com.example.notification.LiveUpdateCoordinator
import com.example.notification.ScheduleAlarmScheduler
import kotlinx.coroutines.flow.Flow

class ScheduleRepository(
    private val dao: ScheduleDao,
    private val alarmScheduler: ScheduleAlarmScheduler,
    private val liveUpdateCoordinator: LiveUpdateCoordinator,
) {
    val allSchedules: Flow<List<ScheduleEntity>> = dao.observeAllSchedules()

    suspend fun saveSchedule(schedule: ScheduleEntity): Long {
        val isNew = schedule.id == 0L
        val sanitized = schedule.copy(dismissedLiveUpdate = false)
        val savedId = if (isNew) {
            dao.insertSchedule(sanitized)
        } else {
            dao.updateSchedule(sanitized)
            sanitized.id
        }
        liveUpdateCoordinator.syncAll(
            forceAlert = sanitized.isTestSchedule,
            rescheduleAlarms = true,
        )
        return savedId
    }

    suspend fun createTestFluidCloudSchedule(
        title: String,
        notes: String,
    ): Long {
        val locMatch = Regex("@([^\\s，。·,]+)").find(notes)?.groupValues?.getOrNull(1)
        return liveUpdateCoordinator.triggerCourseLivePreview(
            fallbackTitle = title.ifBlank { "高等数学" },
            fallbackLocation = locMatch?.takeIf { it.isNotBlank() } ?: "教A-301",
        )
    }

    suspend fun stopTestFluidCloudSchedules() {
        liveUpdateCoordinator.stopCourseLivePreview()
    }

    suspend fun toggleComplete(scheduleId: Long, completed: Boolean) {
        val current = dao.getScheduleById(scheduleId) ?: return
        if (completed) {
            liveUpdateCoordinator.handleCompleteAction(
                sourceType = LiveActivitySourceType.SCHEDULE,
                sourceId = scheduleId,
                dateKey = DateTimeUtils.formatDateKey(current.startTimeMillis),
            )
        } else {
            val reopened = current.copy(
                isCompleted = false,
                completedAtMillis = null,
                dismissedLiveUpdate = false,
            )
            dao.updateSchedule(reopened)
            liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
        }
    }

    suspend fun snoozeScheduleBy10Minutes(scheduleId: Long) {
        val current = dao.getScheduleById(scheduleId) ?: return
        liveUpdateCoordinator.handleSnooze10MinAction(
            sourceType = LiveActivitySourceType.SCHEDULE,
            sourceId = scheduleId,
            dateKey = DateTimeUtils.formatDateKey(current.startTimeMillis),
        )
    }

    suspend fun updateScheduleQuick(schedule: ScheduleEntity) {
        alarmScheduler.cancelAlarmsForSchedule(schedule.id)
        dao.updateSchedule(schedule.copy(dismissedLiveUpdate = false))
        liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
    }

    fun scheduleCustomReReminder(schedule: ScheduleEntity, delayMinutes: Int): Long {
        val timeLabel = DateTimeUtils.formatTimeRange(schedule.startTimeMillis, schedule.endTimeMillis)
        return alarmScheduler.scheduleCustomReReminder(
            isCourse = false,
            itemId = schedule.id,
            title = "日程重提醒 · ${schedule.title}",
            subtitle = "$timeLabel · 点击回到微澜日程查看详情",
            delayMinutes = delayMinutes,
        )
    }

    fun cancelCustomReReminder(scheduleId: Long) {
        alarmScheduler.cancelCustomReReminder(isCourse = false, itemId = scheduleId)
    }

    fun getCustomReReminderMillis(scheduleId: Long): Long? {
        return alarmScheduler.getActiveReReminderMillis(isCourse = false, itemId = scheduleId)
    }

    suspend fun deleteSchedule(schedule: ScheduleEntity) {
        alarmScheduler.cancelAlarmsForSchedule(schedule.id)
        if (schedule.isTestSchedule) {
            alarmScheduler.cancelAlarmsForTestSchedule(schedule.id)
        }
        alarmScheduler.cancelCustomReReminder(isCourse = false, itemId = schedule.id)
        dao.deleteById(schedule.id)
        liveUpdateCoordinator.syncAll(rescheduleAlarms = false)
    }

    suspend fun restoreSchedule(schedule: ScheduleEntity) {
        val restored = schedule.copy(id = 0L)
        dao.insertSchedule(restored)
        liveUpdateCoordinator.syncAll(rescheduleAlarms = true)
    }

    suspend fun updateCustomTagMetadata(tagId: String, name: String, colorHex: String) {
        dao.updateCustomTagMetadata(tagId, name, colorHex)
        liveUpdateCoordinator.syncAll(rescheduleAlarms = false)
    }

    suspend fun clearDeletedCustomTag(tagId: String) {
        dao.clearDeletedCustomTag(tagId)
        liveUpdateCoordinator.syncAll(rescheduleAlarms = false)
    }

    suspend fun syncAllLiveUpdates(
        nowMillis: Long = System.currentTimeMillis(),
        forceAlert: Boolean = false,
        rescheduleAlarms: Boolean = false,
    ) {
        liveUpdateCoordinator.syncAll(
            nowMillis = nowMillis,
            forceAlert = forceAlert,
            rescheduleAlarms = rescheduleAlarms,
        )
    }
}
