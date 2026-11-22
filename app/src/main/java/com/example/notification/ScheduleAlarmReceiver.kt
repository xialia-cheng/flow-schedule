package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ScheduleApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SCHEDULE_TRIGGER) return

        val phase = intent.getIntExtra(
            ScheduleAlarmScheduler.EXTRA_ALARM_PHASE,
            ScheduleAlarmScheduler.PHASE_REMINDER,
        )
        val expectedTriggerMillis = intent.getLongExtra(
            ScheduleAlarmScheduler.EXTRA_TRIGGER_MILLIS,
            0L,
        )
        val shouldAlert = phase == ScheduleAlarmScheduler.PHASE_REMINDER ||
            phase == ScheduleAlarmScheduler.PHASE_START
        val shouldReschedule = phase == ScheduleAlarmScheduler.PHASE_END ||
            phase == ScheduleAlarmScheduler.PHASE_DAILY_ROLLOVER

        // Guard against AlarmManager early-delivery jitter (e.g., firing 100ms before target endTime)
        // so PHASE_START cleanly switches to In-Progress and PHASE_END cleanly dismisses/advances.
        val actualNow = System.currentTimeMillis()
        val effectiveNow = if (
            expectedTriggerMillis > 0L &&
            (phase == ScheduleAlarmScheduler.PHASE_REMINDER ||
                phase == ScheduleAlarmScheduler.PHASE_START ||
                phase == ScheduleAlarmScheduler.PHASE_END)
        ) {
            maxOf(actualNow, expectedTriggerMillis + 500L)
        } else {
            actualNow
        }

        val pendingResult = goAsync()
        val app = context.applicationContext as ScheduleApp
        val liveUpdateManager = app.container.liveUpdateManager

        // Double-safety guard: immediately cancel & restore DND on PHASE_END or whenever current
        // time has already passed the active notification's end timestamp.
        if (phase == ScheduleAlarmScheduler.PHASE_END) {
            liveUpdateManager.cancelLiveUpdate()
        } else {
            liveUpdateManager.purgeExpiredNotificationIfNeeded(effectiveNow)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.liveUpdateCoordinator.syncAll(
                    nowMillis = effectiveNow,
                    forceAlert = shouldAlert,
                    rescheduleAlarms = shouldReschedule,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SCHEDULE_TRIGGER = "com.example.action.SCHEDULE_ALARM_TRIGGER"
    }
}
