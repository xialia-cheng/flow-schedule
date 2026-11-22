package com.example.notification

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ScheduleApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
        if (intent.action !in validActions) return

        val pendingResult = goAsync()
        val app = context.applicationContext as ScheduleApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.liveUpdateCoordinator.syncAll(
                    nowMillis = System.currentTimeMillis(),
                    forceAlert = false,
                    rescheduleAlarms = true,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
