package com.example

import android.app.Application
import com.example.data.ScheduleDatabase
import com.example.data.ScheduleRepository
import com.example.data.TimetableRepository
import com.example.data.UserPreferencesRepository
import com.example.notification.LiveUpdateCoordinator
import com.example.notification.LiveUpdateManager
import com.example.notification.ScheduleAlarmScheduler
import com.example.notification.ScheduleGuardService
import com.example.notification.ScheduleGuardWorkScheduler
import com.example.smart.SmartCaptureOrchestrator
import com.example.widget.FlowWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    val database: ScheduleDatabase = ScheduleDatabase.getInstance(application)
    val liveUpdateManager: LiveUpdateManager = LiveUpdateManager(application)
    val alarmScheduler: ScheduleAlarmScheduler = ScheduleAlarmScheduler(application)
    val preferencesRepository: UserPreferencesRepository = UserPreferencesRepository(application)

    val liveUpdateCoordinator: LiveUpdateCoordinator = LiveUpdateCoordinator(
        scheduleDao = database.scheduleDao(),
        timetableDao = database.timetableDao(),
        preferencesRepository = preferencesRepository,
        alarmScheduler = alarmScheduler,
        liveUpdateManager = liveUpdateManager,
        onWidgetRefresh = { force -> FlowWidgetUpdater.requestUpdate(application, force = force) },
    )

    val scheduleRepository: ScheduleRepository = ScheduleRepository(
        dao = database.scheduleDao(),
        alarmScheduler = alarmScheduler,
        liveUpdateCoordinator = liveUpdateCoordinator,
    )

    val timetableRepository: TimetableRepository = TimetableRepository(
        timetableDao = database.timetableDao(),
        scheduleDao = database.scheduleDao(),
        alarmScheduler = alarmScheduler,
        liveUpdateCoordinator = liveUpdateCoordinator,
        database = database,
    )

    val smartCaptureOrchestrator: SmartCaptureOrchestrator = SmartCaptureOrchestrator(
        scheduleDao = database.scheduleDao(),
        timetableDao = database.timetableDao(),
        timetableRepository = timetableRepository,
        preferencesRepository = preferencesRepository,
        liveUpdateCoordinator = liveUpdateCoordinator,
    )
}

class ScheduleApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.liveUpdateManager.ensureNotificationChannel()
        ScheduleGuardService.ensureNotificationChannel(this)
        // Arm upcoming course & schedule Live Activity alarms immediately on cold start
        appScope.launch {
            runCatching {
                container.liveUpdateCoordinator.syncAll(
                    nowMillis = System.currentTimeMillis(),
                    forceAlert = false,
                    rescheduleAlarms = true,
                )
                val guardEnabled = container.preferencesRepository.settings.first().backgroundGuardEnabled
                if (guardEnabled) {
                    ScheduleGuardWorkScheduler.schedulePeriodicGuard(this@ScheduleApp)
                    ScheduleGuardService.tryStart(this@ScheduleApp)
                } else {
                    ScheduleGuardWorkScheduler.cancelPeriodicGuard(this@ScheduleApp)
                }
            }
        }
    }
}
