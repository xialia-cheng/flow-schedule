package com.example.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.ScheduleApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic 15-minute WorkManager watchdog that verifies [ScheduleGuardService] and replenishes
 * upcoming 24h+ course/schedule alarms whenever background guard mode is enabled.
 */
class ScheduleGuardWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ScheduleApp ?: return Result.success()
        val settings = runCatching {
            app.container.preferencesRepository.settings.first()
        }.getOrNull() ?: return Result.success()

        if (!settings.backgroundGuardEnabled) {
            ScheduleGuardService.stop(applicationContext)
            ScheduleGuardWorkScheduler.cancelPeriodicGuard(applicationContext)
            return Result.success()
        }

        runCatching {
            app.container.liveUpdateCoordinator.syncAll(
                nowMillis = System.currentTimeMillis(),
                forceAlert = false,
                rescheduleAlarms = true,
            )
        }

        if (!ScheduleGuardService.isRunning) {
            ScheduleGuardService.tryStart(applicationContext)
        }

        return Result.success()
    }
}

object ScheduleGuardWorkScheduler {
    const val PERIODIC_WORK_NAME = "ripple_plan_guard_periodic_work"
    const val RECOVERY_WORK_NAME = "ripple_plan_guard_recovery_work"

    fun schedulePeriodicGuard(context: Context) {
        val request = PeriodicWorkRequestBuilder<ScheduleGuardWorker>(
            15,
            TimeUnit.MINUTES,
        ).build()

        runCatching {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    fun cancelPeriodicGuard(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            val wm = WorkManager.getInstance(appContext)
            wm.cancelUniqueWork(PERIODIC_WORK_NAME)
            wm.cancelUniqueWork(RECOVERY_WORK_NAME)
        }
    }

    fun enqueueDelayedRecovery(context: Context) {
        val request = OneTimeWorkRequestBuilder<ScheduleGuardWorker>()
            .setInitialDelay(3, TimeUnit.SECONDS)
            .build()

        runCatching {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                RECOVERY_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
