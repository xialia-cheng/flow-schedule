package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.data.DateTimeUtils
import java.util.Calendar

class ScheduleAlarmScheduler(private val context: Context) {

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarmsFor(item: LiveActivityItem, nowMillis: Long = System.currentTimeMillis()) {
        val baseKey = item.alarmRequestKey()
        cancelAlarmsForKey(baseKey)

        if (item.reminderMinutesBefore > 0 && item.reminderTriggerMillis > nowMillis) {
            setAlarm(
                triggerAtMillis = item.reminderTriggerMillis,
                baseRequestKey = baseKey,
                phase = PHASE_REMINDER,
            )
        }

        if (item.startTimeMillis > nowMillis) {
            setAlarm(
                triggerAtMillis = item.startTimeMillis,
                baseRequestKey = baseKey,
                phase = PHASE_START,
            )
        }

        val duration = item.durationMillis
        val checkpoints = listOf(
            PHASE_PROGRESS_25 to (item.startTimeMillis + duration / 4L),
            PHASE_PROGRESS_50 to (item.startTimeMillis + duration / 2L),
            PHASE_PROGRESS_75 to (item.startTimeMillis + (duration * 3L) / 4L),
        )
        checkpoints.forEach { (phase, checkpointTime) ->
            if (checkpointTime > nowMillis + 5_000L && checkpointTime < item.endTimeMillis - 5_000L) {
                setAlarm(
                    triggerAtMillis = checkpointTime,
                    baseRequestKey = baseKey,
                    phase = phase,
                )
            }
        }

        if (item.endTimeMillis > nowMillis) {
            setAlarm(
                triggerAtMillis = item.endTimeMillis,
                baseRequestKey = baseKey,
                phase = PHASE_END,
            )
        }
    }

    /**
     * Schedules a daily 00:05 rollover alarm so tomorrow's and upcoming days' timetable courses
     * automatically arm their Fluid Cloud Live Activity triggers even if the app isn't opened.
     */
    fun scheduleDailyRolloverAlarm(nowMillis: Long = System.currentTimeMillis()) {
        val nextDayStart = DateTimeUtils.addDays(nowMillis, 1)
        val cal = Calendar.getInstance().apply {
            timeInMillis = nextDayStart
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        setAlarm(
            triggerAtMillis = cal.timeInMillis,
            baseRequestKey = SYSTEM_DAILY_ROLLOVER_REQUEST_KEY,
            phase = PHASE_DAILY_ROLLOVER,
        )
    }

    fun cancelAlarmsForSchedule(scheduleId: Long) {
        val base = (scheduleId.toInt() and 0x3FFF) * 8
        for (day in 0..7) {
            cancelAlarmsForKey(day * 50_000 + base)
        }
    }

    fun cancelAlarmsForTestSchedule(scheduleId: Long) {
        val base = (scheduleId.toInt() and 0x3FFF) * 8
        for (day in 0..7) {
            cancelAlarmsForKey(800_000 + day * 50_000 + base)
        }
    }

    fun cancelAlarmsForCourse(courseId: Long) {
        val base = (courseId.toInt() and 0x3FFF) * 8
        for (day in 0..7) {
            cancelAlarmsForKey(400_000 + day * 50_000 + base)
        }
    }

    /**
     * Schedules an AlarmManager broadcast 1.5s after task swipe removal using setAlarmClock
     * (when permitted) so the OS grants an exact-alarm exemption to revive [ScheduleGuardService]
     * and re-post any active Fluid Cloud Live Update notification.
     */
    fun scheduleGuardResurrection(delayMillis: Long = 1_500L) {
        val triggerAtMillis = System.currentTimeMillis() + delayMillis.coerceAtLeast(500L)
        val restartIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ScheduleAlarmReceiver.ACTION_RESURRECT_GUARD
            putExtra(EXTRA_TRIGGER_MILLIS, triggerAtMillis)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            GUARD_RESURRECT_REQUEST_CODE,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleExactOrFallbackAlarm(
            triggerAtMillis = triggerAtMillis,
            pendingIntent = pi,
            useAlarmClock = true,
        )
    }

    private fun cancelAlarmsForKey(baseKey: Int) {
        ALL_PHASES.forEach { phase ->
            val pi = createPendingIntent(baseKey, phase, 0L)
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    private fun setAlarm(triggerAtMillis: Long, baseRequestKey: Int, phase: Int) {
        val pendingIntent = createPendingIntent(baseRequestKey, phase, triggerAtMillis)
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }
        val isCriticalTransition = phase == PHASE_REMINDER ||
            phase == PHASE_START ||
            phase == PHASE_END

        runCatching {
            if (canExact && isCriticalTransition) {
                // setAlarmClock is exempt from ColorOS Doze/background throttling and fires
                // to the exact second so 00:00 phase transitions never stall.
                val showIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                    pendingIntent,
                )
            } else if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
        }.onFailure {
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
        }
    }

        private fun createPendingIntent(
        baseRequestKey: Int,
        phase: Int,
        triggerAtMillis: Long,
    ): PendingIntent {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ScheduleAlarmReceiver.ACTION_SCHEDULE_TRIGGER
            putExtra(EXTRA_ALARM_PHASE, phase)
            putExtra(EXTRA_TRIGGER_MILLIS, triggerAtMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            baseRequestKey + phase,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val reReminderPrefs by lazy {
        context.getSharedPreferences(PREFS_RE_REMINDERS, Context.MODE_PRIVATE)
    }

    fun scheduleCustomReReminder(
        isCourse: Boolean,
        itemId: Long,
        title: String,
        subtitle: String,
        delayMinutes: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val safeDelay = delayMinutes.coerceIn(1, 720)
        val triggerAtMillis = nowMillis + safeDelay * 60_000L
        val key = reReminderPrefKey(isCourse, itemId)
        reReminderPrefs.edit().putLong(key, triggerAtMillis).apply()

        val pi = createReReminderPendingIntent(
            isCourse = isCourse,
            itemId = itemId,
            title = title,
            subtitle = subtitle,
            triggerAtMillis = triggerAtMillis,
        )
        scheduleExactOrFallbackAlarm(triggerAtMillis, pi, useAlarmClock = true)
        return triggerAtMillis
    }

    fun cancelCustomReReminder(isCourse: Boolean, itemId: Long) {
        val key = reReminderPrefKey(isCourse, itemId)
        reReminderPrefs.edit().remove(key).apply()
        val pi = createReReminderPendingIntent(
            isCourse = isCourse,
            itemId = itemId,
            title = "",
            subtitle = "",
            triggerAtMillis = 0L,
        )
        alarmManager.cancel(pi)
        pi.cancel()
    }

    fun getActiveReReminderMillis(
        isCourse: Boolean,
        itemId: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long? {
        val key = reReminderPrefKey(isCourse, itemId)
        val stored = reReminderPrefs.getLong(key, 0L)
        if (stored <= 0L) return null
        if (stored <= nowMillis) {
            reReminderPrefs.edit().remove(key).apply()
            return null
        }
        return stored
    }

    fun clearReReminderEntry(key: String) {
        if (key.isNotBlank()) {
            reReminderPrefs.edit().remove(key).apply()
        }
    }

    private fun scheduleExactOrFallbackAlarm(
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
        useAlarmClock: Boolean,
    ) {
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }
        runCatching {
            if (canExact && useAlarmClock) {
                val showIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                    pendingIntent,
                )
            } else if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
        }.onFailure {
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
        }
    }

    private fun createReReminderPendingIntent(
        isCourse: Boolean,
        itemId: Long,
        title: String,
        subtitle: String,
        triggerAtMillis: Long,
    ): PendingIntent {
        val requestCode = RE_REMINDER_REQUEST_BASE +
            (if (isCourse) 10_000 else 0) +
            (itemId.toInt() and 0x1FFF)
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ScheduleAlarmReceiver.ACTION_CUSTOM_RE_REMINDER
            putExtra(EXTRA_RE_REMINDER_KEY, reReminderPrefKey(isCourse, itemId))
            putExtra(EXTRA_RE_REMINDER_IS_COURSE, isCourse)
            putExtra(EXTRA_RE_REMINDER_ITEM_ID, itemId)
            putExtra(EXTRA_RE_REMINDER_TITLE, title)
            putExtra(EXTRA_RE_REMINDER_SUBTITLE, subtitle)
            putExtra(EXTRA_TRIGGER_MILLIS, triggerAtMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_ALARM_PHASE = "extra_alarm_phase"
        const val EXTRA_TRIGGER_MILLIS = "extra_trigger_millis"
        const val EXTRA_RE_REMINDER_KEY = "extra_re_reminder_key"
        const val EXTRA_RE_REMINDER_IS_COURSE = "extra_re_reminder_is_course"
        const val EXTRA_RE_REMINDER_ITEM_ID = "extra_re_reminder_item_id"
        const val EXTRA_RE_REMINDER_TITLE = "extra_re_reminder_title"
        const val EXTRA_RE_REMINDER_SUBTITLE = "extra_re_reminder_subtitle"
        const val PHASE_REMINDER = 1
        const val PHASE_START = 2
        const val PHASE_PROGRESS_25 = 3
        const val PHASE_PROGRESS_50 = 4
        const val PHASE_PROGRESS_75 = 5
        const val PHASE_END = 6
        const val PHASE_DAILY_ROLLOVER = 8

        private const val SYSTEM_DAILY_ROLLOVER_REQUEST_KEY = 920_000
        private const val GUARD_RESURRECT_REQUEST_CODE = 925_001
        private const val RE_REMINDER_REQUEST_BASE = 850_000
        private const val PREFS_RE_REMINDERS = "flow_re_reminders"

        fun reReminderPrefKey(isCourse: Boolean, itemId: Long): String =
            if (isCourse) "course_$itemId" else "schedule_$itemId"

        private val ALL_PHASES = listOf(
            PHASE_REMINDER,
            PHASE_START,
            PHASE_PROGRESS_25,
            PHASE_PROGRESS_50,
            PHASE_PROGRESS_75,
            PHASE_END,
        )
    }
}
