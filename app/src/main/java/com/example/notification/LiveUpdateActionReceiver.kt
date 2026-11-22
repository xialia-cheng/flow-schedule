package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ScheduleApp
import com.example.data.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LiveUpdateActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sourceId = intent.getLongExtra(
            LiveUpdateManager.EXTRA_SOURCE_ID,
            intent.getLongExtra(LiveUpdateManager.EXTRA_SCHEDULE_ID, -1L),
        )
        if (sourceId <= 0L) return

        val sourceTypeRaw = intent.getStringExtra(LiveUpdateManager.EXTRA_SOURCE_TYPE)
        val sourceType = runCatching {
            if (sourceTypeRaw != null) LiveActivitySourceType.valueOf(sourceTypeRaw) else LiveActivitySourceType.SCHEDULE
        }.getOrDefault(LiveActivitySourceType.SCHEDULE)

        val dateKey = intent.getStringExtra(LiveUpdateManager.EXTRA_DATE_KEY)
            ?: DateTimeUtils.formatDateKey(System.currentTimeMillis())

        val pendingResult = goAsync()
        val app = context.applicationContext as ScheduleApp
        val coordinator = app.container.liveUpdateCoordinator

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE -> coordinator.handleCompleteAction(sourceType, sourceId, dateKey)
                    ACTION_SNOOZE_10M -> coordinator.handleSnooze10MinAction(sourceType, sourceId, dateKey)
                    ACTION_TOGGLE_DND -> coordinator.handleToggleDndAction()
                    ACTION_DISMISSED -> coordinator.handleDismissAction(sourceType, sourceId, dateKey)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.example.action.LIVE_UPDATE_COMPLETE"
        const val ACTION_SNOOZE_10M = "com.example.action.LIVE_UPDATE_SNOOZE_10M"
        const val ACTION_TOGGLE_DND = "com.example.action.LIVE_UPDATE_TOGGLE_DND"
        const val ACTION_DISMISSED = "com.example.action.LIVE_UPDATE_DISMISSED"
    }
}
