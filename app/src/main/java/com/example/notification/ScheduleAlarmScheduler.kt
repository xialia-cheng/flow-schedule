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

    fun cancelAlarmsForCourse(courseId: Long) {
        val base = (courseId.toInt() and 0x3FFF) * 8
        for (day in 0..7) {
            cancelAlarmsForKey(400_000 + day * 50_000 + base)
        }
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

    companion object {
        const val EXTRA_ALARM_PHASE = "extra_alarm_phase"
        const val EXTRA_TRIGGER_MILLIS = "extra_trigger_millis"
        const val PHASE_REMINDER = 1
        const val PHASE_START = 2
        const val PHASE_PROGRESS_25 = 3
        const val PHASE_PROGRESS_50 = 4
        const val PHASE_PROGRESS_75 = 5
        const val PHASE_END = 6
        const val PHASE_DAILY_ROLLOVER = 8

        private const val SYSTEM_DAILY_ROLLOVER_REQUEST_KEY = 920_000

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
