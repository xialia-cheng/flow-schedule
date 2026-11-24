package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.example.ScheduleApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == ACTION_RESURRECT_GUARD || action == ACTION_GUARD_RESTART) {
            val pendingResult = goAsync()
            val wakeLock = acquireReceiverWakeLock(context)
            val app = context.applicationContext as ScheduleApp
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val guardEnabled = runCatching {
                        app.container.preferencesRepository.settings.first().backgroundGuardEnabled
                    }.getOrDefault(false)
                    if (guardEnabled) {
                        runCatching {
                            ScheduleGuardService.tryStart(context)
                        }
                        app.container.liveUpdateCoordinator.syncAll(
                            nowMillis = System.currentTimeMillis(),
                            forceAlert = false,
                            rescheduleAlarms = true,
                        )
                    }
                } finally {
                    releaseWakeLockSafely(wakeLock)
                    pendingResult.finish()
                }
            }
            return
        }

        if (action == ACTION_CUSTOM_RE_REMINDER) {
            val key = intent.getStringExtra(ScheduleAlarmScheduler.EXTRA_RE_REMINDER_KEY).orEmpty()
            val isCourse = intent.getBooleanExtra(ScheduleAlarmScheduler.EXTRA_RE_REMINDER_IS_COURSE, false)
            val itemId = intent.getLongExtra(ScheduleAlarmScheduler.EXTRA_RE_REMINDER_ITEM_ID, 0L)
            val title = intent.getStringExtra(ScheduleAlarmScheduler.EXTRA_RE_REMINDER_TITLE).orEmpty()
            val subtitle = intent.getStringExtra(ScheduleAlarmScheduler.EXTRA_RE_REMINDER_SUBTITLE).orEmpty()
            val pendingResult = goAsync()
            val wakeLock = acquireReceiverWakeLock(context)
            val app = context.applicationContext as ScheduleApp
            app.container.alarmScheduler.clearReReminderEntry(key)
            app.container.liveUpdateManager.postCustomReReminderNotification(
                itemId = itemId,
                isCourse = isCourse,
                title = title,
                subtitle = subtitle,
            )
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    app.container.liveUpdateCoordinator.syncAll(
                        nowMillis = System.currentTimeMillis(),
                        forceAlert = false,
                        rescheduleAlarms = false,
                    )
                } finally {
                    releaseWakeLockSafely(wakeLock)
                    pendingResult.finish()
                }
            }
            return
        }

        if (action != ACTION_SCHEDULE_TRIGGER) return

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
        val shouldReschedule = phase == ScheduleAlarmScheduler.PHASE_REMINDER ||
            phase == ScheduleAlarmScheduler.PHASE_START ||
            phase == ScheduleAlarmScheduler.PHASE_END ||
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
        val wakeLock = acquireReceiverWakeLock(context)
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
                val guardEnabled = runCatching {
                    app.container.preferencesRepository.settings.first().backgroundGuardEnabled
                }.getOrDefault(false)
                if (guardEnabled && !ScheduleGuardService.isRunning) {
                    runCatching {
                        ScheduleGuardService.tryStart(context)
                    }
                }
                app.container.liveUpdateCoordinator.syncAll(
                    nowMillis = effectiveNow,
                    forceAlert = shouldAlert,
                    rescheduleAlarms = shouldReschedule,
                )
            } finally {
                releaseWakeLockSafely(wakeLock)
                pendingResult.finish()
            }
        }
    }

    private fun acquireReceiverWakeLock(context: Context): PowerManager.WakeLock? {
        return runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)?.apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }.getOrNull()
    }

    private fun releaseWakeLockSafely(wakeLock: PowerManager.WakeLock?) {
        if (wakeLock != null) {
            runCatching {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }
        }
    }

    companion object {
        const val ACTION_SCHEDULE_TRIGGER = "com.example.action.SCHEDULE_ALARM_TRIGGER"
        const val ACTION_RESURRECT_GUARD = "com.example.action.RESURRECT_GUARD"
        const val ACTION_GUARD_RESTART = "com.example.action.GUARD_RESTART"
        const val ACTION_CUSTOM_RE_REMINDER = "com.example.action.CUSTOM_RE_REMINDER"

        private const val WAKE_LOCK_TAG = "rippleplan:alarm_receiver"
        private const val WAKE_LOCK_TIMEOUT_MS = 8_000L
    }
}
