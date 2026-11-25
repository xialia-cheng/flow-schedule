package com.example.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ScheduleApp
import com.example.data.CourseColorPalette
import com.example.data.DateTimeUtils
import com.example.data.ScheduleDatabase
import com.example.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lightweight all-day foreground guard service (`specialUse`) that observes `activeLiveActivity`,
 * listens to `ACTION_TIME_TICK` and date rollovers, waits precisely with a short WakeLock when a
 * phase boundary (`startTimeMillis` / `endTimeMillis`) is within 90s, and directly drives
 * [LiveUpdateCoordinator.syncAll] so Live Update capsules transition cleanly on lockscreen.
 */
class ScheduleGuardService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    @Volatile
    private var lastKnownGuardEnabled: Boolean = true

    @Volatile
    private var lastSyncedDateKey: String = ""

    @Volatile
    private var lastRescheduleMillis: Long = 0L

    @Volatile
    private var lastPostedStatusText: String = ""

    @Volatile
    private var lastScheduledBoundaryMillis: Long = 0L

    private var activeSyncJob: Job? = null
    private var phaseBoundaryJob: Job? = null
    private var liveActivityObserverJob: Job? = null

    private val timeEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            if (action == Intent.ACTION_TIME_TICK) {
                triggerTimeTickSync()
                return
            }
            val now = System.currentTimeMillis()
            val activeLive = (applicationContext as? ScheduleApp)
                ?.container
                ?.liveUpdateManager
                ?.activeLiveActivity
                ?.value
            val crossedBoundary = (lastScheduledBoundaryMillis > 0L && now >= lastScheduledBoundaryMillis) ||
                (activeLive != null && now >= activeLive.endTimeMillis)
            val forceReschedule = crossedBoundary ||
                action == Intent.ACTION_DATE_CHANGED ||
                action == Intent.ACTION_TIME_CHANGED ||
                action == Intent.ACTION_TIMEZONE_CHANGED ||
                action == Intent.ACTION_USER_PRESENT
            val forceLiveSync = crossedBoundary || activeLive != null
            triggerSync(
                rescheduleAlarms = forceReschedule,
                forceAlert = crossedBoundary,
                holdShortWakeLock = forceLiveSync,
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        ensureNotificationChannel(this)
        if (!promoteToForeground(getString(R.string.guard_notification_idle))) {
            stopSelf()
            return
        }
        registerTimeReceivers()
        observeActiveLiveActivity()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_GUARD) {
            lastKnownGuardEnabled = false
            phaseBoundaryJob?.cancel()
            liveActivityObserverJob?.cancel()
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        val initialText = lastPostedStatusText.ifBlank { getString(R.string.guard_notification_idle) }
        if (!promoteToForeground(initialText)) {
            stopSelf()
            return START_NOT_STICKY
        }

        triggerSync(rescheduleAlarms = true, forceAlert = false)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (lastKnownGuardEnabled) {
            scheduleResurrectionRecovery(applicationContext)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        phaseBoundaryJob?.cancel()
        liveActivityObserverJob?.cancel()
        runCatching { unregisterReceiver(timeEventReceiver) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun registerTimeReceivers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                timeEventReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    private fun observeActiveLiveActivity() {
        liveActivityObserverJob?.cancel()
        val app = applicationContext as? ScheduleApp ?: return
        liveActivityObserverJob = serviceScope.launch {
            app.container.liveUpdateManager.activeLiveActivity.collect { item ->
                if (item == null || !lastKnownGuardEnabled) return@collect
                val now = System.currentTimeMillis()
                val nextBoundary = listOf(item.startTimeMillis, item.endTimeMillis)
                    .filter { it > now }
                    .minOrNull()
                if (nextBoundary != null) {
                    val currentArmed = lastScheduledBoundaryMillis
                    if (currentArmed == 0L || currentArmed <= now || nextBoundary < currentArmed) {
                        schedulePhaseBoundaryPatrol(nextBoundary)
                    }
                }
            }
        }
    }

    private fun promoteToForeground(statusText: String): Boolean {
        val notification = buildGuardNotification(this, statusText)
        lastPostedStatusText = statusText
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        return runCatching {
            ServiceCompat.startForeground(
                this,
                GUARD_NOTIFICATION_ID,
                notification,
                fgsType,
            )
            true
        }.getOrDefault(false)
    }

    /**
     * Handles every system [Intent.ACTION_TIME_TICK] broadcast directly in the service coroutine,
     * driving [LiveUpdateCoordinator.syncAll] even if background AlarmManager timers were deferred.
     */
    internal fun triggerTimeTickSync(nowOverrideMillis: Long? = null) {
        val wakeLock = acquireWakeLock(SYNC_WAKE_LOCK_TAG, 6_000L)
        serviceScope.launch {
            try {
                syncMutex.withLock {
                    val app = applicationContext as? ScheduleApp ?: return@withLock
                    val settings = app.container.preferencesRepository.settings.first()
                    lastKnownGuardEnabled = settings.backgroundGuardEnabled
                    if (!settings.backgroundGuardEnabled) {
                        phaseBoundaryJob?.cancel()
                        stopForegroundAndSelf()
                        return@withLock
                    }

                    val now = nowOverrideMillis ?: System.currentTimeMillis()
                    runCatching {
                        app.container.liveUpdateCoordinator.syncAll(
                            nowMillis = now,
                            forceAlert = false,
                            rescheduleAlarms = false,
                        )
                    }

                    val snapshot = resolveGuardSnapshot(applicationContext, now)
                    if (snapshot.statusText != lastPostedStatusText) {
                        lastPostedStatusText = snapshot.statusText
                        updateGuardNotification(snapshot.statusText)
                    }
                    schedulePhaseBoundaryPatrol(snapshot.nextBoundaryMillis)
                }
            } finally {
                releaseWakeLockSafely(wakeLock)
            }
        }
    }

    private fun triggerSync(
        rescheduleAlarms: Boolean,
        forceAlert: Boolean = false,
        holdShortWakeLock: Boolean = false,
    ) {
        if (activeSyncJob?.isActive == true && !rescheduleAlarms && !forceAlert) return
        val wakeLock = if (holdShortWakeLock) acquireWakeLock(SYNC_WAKE_LOCK_TAG, 6_000L) else null
        activeSyncJob = serviceScope.launch {
            try {
                syncMutex.withLock {
                    val app = applicationContext as? ScheduleApp ?: return@withLock
                    val settings = app.container.preferencesRepository.settings.first()
                    lastKnownGuardEnabled = settings.backgroundGuardEnabled
                    if (!settings.backgroundGuardEnabled) {
                        phaseBoundaryJob?.cancel()
                        stopForegroundAndSelf()
                        return@withLock
                    }

                    val now = System.currentTimeMillis()
                    val todayKey = DateTimeUtils.formatDateKey(now)
                    val shouldReschedule = rescheduleAlarms ||
                        lastSyncedDateKey != todayKey ||
                        (now - lastRescheduleMillis) >= RESCHEDULE_INTERVAL_MILLIS

                    runCatching {
                        app.container.liveUpdateCoordinator.syncAll(
                            nowMillis = now,
                            forceAlert = forceAlert,
                            rescheduleAlarms = shouldReschedule,
                        )
                    }

                    if (shouldReschedule) {
                        lastSyncedDateKey = todayKey
                        lastRescheduleMillis = now
                    }

                    val snapshot = resolveGuardSnapshot(applicationContext, now)
                    if (snapshot.statusText != lastPostedStatusText) {
                        lastPostedStatusText = snapshot.statusText
                        updateGuardNotification(snapshot.statusText)
                    }
                    schedulePhaseBoundaryPatrol(snapshot.nextBoundaryMillis)
                }
            } finally {
                releaseWakeLockSafely(wakeLock)
            }
        }
    }

    private fun schedulePhaseBoundaryPatrol(nextBoundaryMillis: Long?) {
        if (nextBoundaryMillis == null) {
            phaseBoundaryJob?.cancel()
            lastScheduledBoundaryMillis = 0L
            return
        }
        if (phaseBoundaryJob?.isActive == true && lastScheduledBoundaryMillis == nextBoundaryMillis) {
            return
        }

        phaseBoundaryJob?.cancel()
        lastScheduledBoundaryMillis = nextBoundaryMillis

        val remainingMillis = nextBoundaryMillis - System.currentTimeMillis()
        // Only hold an active coroutine wait + WakeLock when the next phase boundary is within 90s;
        // longer waits will be picked up by the every-minute ACTION_TIME_TICK receiver.
        if (remainingMillis > SHORT_BOUNDARY_WINDOW_MS) return

        val wakeLockTimeout = (remainingMillis.coerceAtLeast(0L) + 4_000L)
            .coerceAtMost(SHORT_BOUNDARY_WINDOW_MS + 5_000L)
        val wakeLock = acquireWakeLock(BOUNDARY_WAKE_LOCK_TAG, wakeLockTimeout)

        phaseBoundaryJob = serviceScope.launch {
            try {
                val targetAt = nextBoundaryMillis + BOUNDARY_JITTER_OFFSET_MS
                while (isActive) {
                    val left = targetAt - System.currentTimeMillis()
                    if (left <= 0L) break
                    delay(left.coerceAtMost(BOUNDARY_POLL_STEP_MS))
                }
                if (!isActive || !lastKnownGuardEnabled) return@launch

                val app = applicationContext as? ScheduleApp ?: return@launch
                syncMutex.withLock {
                    if (!lastKnownGuardEnabled) return@withLock
                    val boundaryNow = System.currentTimeMillis()
                    runCatching {
                        app.container.liveUpdateCoordinator.syncAll(
                            nowMillis = boundaryNow,
                            forceAlert = true,
                            rescheduleAlarms = false,
                        )
                    }
                    val snapshot = resolveGuardSnapshot(applicationContext, boundaryNow)
                    if (snapshot.statusText != lastPostedStatusText) {
                        lastPostedStatusText = snapshot.statusText
                        updateGuardNotification(snapshot.statusText)
                    }
                    // Reset before chaining to avoid equality check skipping next boundary
                    lastScheduledBoundaryMillis = 0L
                    val chainedNext = snapshot.nextBoundaryMillis
                    if (chainedNext != null && chainedNext - boundaryNow <= SHORT_BOUNDARY_WINDOW_MS) {
                        schedulePhaseBoundaryPatrol(chainedNext)
                    } else {
                        lastScheduledBoundaryMillis = chainedNext ?: 0L
                    }
                }
            } finally {
                releaseWakeLockSafely(wakeLock)
            }
        }
    }

    private fun acquireWakeLock(tag: String, timeoutMillis: Long): PowerManager.WakeLock? {
        return runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)?.apply {
                setReferenceCounted(false)
                acquire(timeoutMillis)
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

    @SuppressLint("MissingPermission")
    private fun updateGuardNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching {
            manager.notify(GUARD_NOTIFICATION_ID, buildGuardNotification(this, statusText))
        }
    }

    private fun stopForegroundAndSelf() {
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    private data class GuardSnapshot(
        val statusText: String,
        val nextBoundaryMillis: Long?,
    )

    companion object {
        // Strictly isolated from LiveUpdateManager.CHANNEL_ID and LIVE_UPDATE_NOTIFICATION_ID
        const val CHANNEL_ID = "ripple_plan_guard_service"
        const val GUARD_NOTIFICATION_ID = 2001

        const val ACTION_START_GUARD = "com.example.action.START_SCHEDULE_GUARD"
        const val ACTION_REFRESH_GUARD = "com.example.action.REFRESH_SCHEDULE_GUARD"
        const val ACTION_STOP_GUARD = "com.example.action.STOP_SCHEDULE_GUARD"

        private const val GUARD_CONTENT_REQUEST_CODE = 2002
        private const val RESCHEDULE_INTERVAL_MILLIS = 15 * 60_000L
        private const val BOUNDARY_JITTER_OFFSET_MS = 250L
        private const val BOUNDARY_POLL_STEP_MS = 1_000L
        internal const val SHORT_BOUNDARY_WINDOW_MS = 90_000L
        private const val RESURRECT_DELAY_MS = 1_500L
        private const val SYNC_WAKE_LOCK_TAG = "rippleplan:guard_sync"
        private const val BOUNDARY_WAKE_LOCK_TAG = "rippleplan:guard_boundary"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return
                val existing = manager.getNotificationChannel(CHANNEL_ID)
                if (existing == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.channel_guard_service_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = context.getString(R.string.channel_guard_service_desc)
                        setSound(null, null)
                        enableVibration(false)
                        setShowBadge(false)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }

        internal fun buildGuardNotification(context: Context, statusText: String): Notification {
            ensureNotificationChannel(context)
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                GUARD_CONTENT_REQUEST_CODE,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_flow_schedule)
                .setContentTitle(context.getString(R.string.guard_notification_title))
                .setContentText(statusText)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(contentPendingIntent)
                .build()
        }

        fun tryStart(context: Context): Boolean {
            val appContext = context.applicationContext
            val intent = Intent(appContext, ScheduleGuardService::class.java).apply {
                action = ACTION_START_GUARD
            }
            return runCatching {
                ContextCompat.startForegroundService(appContext, intent)
                true
            }.getOrDefault(false)
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            runCatching {
                appContext.stopService(Intent(appContext, ScheduleGuardService::class.java))
            }
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            runCatching {
                manager?.cancel(GUARD_NOTIFICATION_ID)
            }
        }

        internal fun formatGuardStatusText(
            nextTitle: String?,
            targetTimeMillis: Long?,
            isInProgress: Boolean,
            idleText: String,
        ): String {
            val cleanTitle = nextTitle?.trim().orEmpty()
            if (cleanTitle.isEmpty() || targetTimeMillis == null) {
                return idleText
            }
            val timeStr = DateTimeUtils.formatTime(targetTimeMillis)
            return if (isInProgress) {
                "进行中：$cleanTitle（$timeStr 结束）"
            } else {
                "下一项：$cleanTitle $timeStr"
            }
        }

        internal fun nextLivePhaseBoundary(
            activeItem: LiveActivityItem?,
            candidateBoundaries: List<Long>,
            nowMillis: Long,
        ): Long? {
            val itemBoundaries = if (activeItem != null) {
                listOf(activeItem.startTimeMillis, activeItem.endTimeMillis)
            } else {
                emptyList()
            }
            return (candidateBoundaries + itemBoundaries)
                .filter { it > nowMillis }
                .minOrNull()
        }

        private suspend fun resolveGuardSnapshot(context: Context, nowMillis: Long): GuardSnapshot {
            val idleText = context.getString(R.string.guard_notification_idle)
            return runCatching {
                val app = context.applicationContext as? ScheduleApp
                val db = ScheduleDatabase.getInstance(context)
                val prefs = UserPreferencesRepository(context).settings.first()
                val scheduleDao = db.scheduleDao()
                val timetableDao = db.timetableDao()

                data class Candidate(
                    val title: String,
                    val reminderTriggerMillis: Long?,
                    val startMillis: Long,
                    val endMillis: Long,
                    val isTest: Boolean,
                    val dismissed: Boolean,
                )

                val candidates = mutableListOf<Candidate>()

                scheduleDao.getAllIncompleteSchedules().forEach { s ->
                    val isTodayOrActive = DateTimeUtils.isToday(s.startTimeMillis, nowMillis) ||
                        s.isInProgress(nowMillis) ||
                        s.isTestSchedule
                    if (!isTodayOrActive || s.endTimeMillis <= nowMillis) return@forEach
                    val reminderAt = if (s.reminderMinutesBefore > 0) {
                        s.startTimeMillis - s.reminderMinutesBefore * 60_000L
                    } else {
                        null
                    }
                    candidates.add(
                        Candidate(
                            title = s.title,
                            reminderTriggerMillis = reminderAt,
                            startMillis = s.startTimeMillis,
                            endMillis = s.endTimeMillis,
                            isTest = s.isTestSchedule,
                            dismissed = s.dismissedLiveUpdate,
                        ),
                    )
                }

                if (prefs.courseReminderEnabled) {
                    val activeTimetable = timetableDao.getActiveTimetable()
                    if (activeTimetable != null) {
                        val dayStart = DateTimeUtils.startOfDayMillis(nowMillis)
                        val dateKey = DateTimeUtils.formatDateKey(dayStart)
                        val isoDay = DateTimeUtils.isoDayOfWeek(dayStart)
                        val semesterWeek = DateTimeUtils.computeSemesterWeek(
                            activeTimetable.semesterStartMondayMillis,
                            dayStart,
                            activeTimetable.totalWeeks,
                            prefs.weekStartDay,
                        )
                        val courses = timetableDao.getCoursesForTimetable(activeTimetable.id)
                        val sectionTimes = timetableDao.getSectionTimes(activeTimetable.id)
                            .ifEmpty { CourseColorPalette.defaultSectionTimes(activeTimetable.id) }
                            .associateBy { it.sectionNumber }
                        val stateMap = timetableDao.getCourseStatesForDate(dateKey).associateBy { it.courseId }

                        courses.filter { it.dayOfWeek == isoDay && it.isInWeek(semesterWeek) }.forEach { course ->
                            val state = stateMap[course.id]
                            if (state?.isCompleted == true) return@forEach
                            val startSlot = sectionTimes[course.startSection] ?: return@forEach
                            val endSlot = sectionTimes[course.endSection] ?: startSlot
                            val snoozeMs = (state?.snoozeMinutes ?: 0) * 60_000L
                            val rawStart = DateTimeUtils.combineDateAndTime(dayStart, startSlot.startHour, startSlot.startMinute)
                            val rawEnd = DateTimeUtils.combineDateAndTime(dayStart, endSlot.endHour, endSlot.endMinute)
                            val startMs = if (nowMillis >= rawStart) rawStart else rawStart + snoozeMs
                            val endMs = (rawEnd + snoozeMs).coerceAtLeast(startMs + 60_000L)
                            if (endMs <= nowMillis) return@forEach
                            val reminderAt = if (prefs.courseReminderLeadMinutes > 0) {
                                startMs - prefs.courseReminderLeadMinutes * 60_000L
                            } else {
                                null
                            }
                            candidates.add(
                                Candidate(
                                    title = course.name,
                                    reminderTriggerMillis = reminderAt,
                                    startMillis = startMs,
                                    endMillis = endMs,
                                    isTest = false,
                                    dismissed = state?.dismissedLiveUpdate == true,
                                ),
                            )
                        }
                    }
                }

                val candidateBoundaries = candidates
                    .filter { !it.dismissed }
                    .flatMap { candidate ->
                        listOfNotNull(
                            candidate.reminderTriggerMillis,
                            candidate.startMillis,
                            candidate.endMillis,
                        )
                    }
                val activeLive = app?.container?.liveUpdateManager?.activeLiveActivity?.value
                val nextBoundary = nextLivePhaseBoundary(
                    activeItem = activeLive,
                    candidateBoundaries = candidateBoundaries,
                    nowMillis = nowMillis,
                )

                val chosen = candidates
                    .filter { !it.isTest }
                    .sortedWith(
                        compareByDescending<Candidate> { nowMillis in it.startMillis until it.endMillis }
                            .thenBy { it.startMillis },
                    )
                    .firstOrNull()

                val statusText = if (chosen == null) {
                    idleText
                } else {
                    val inProgress = nowMillis in chosen.startMillis until chosen.endMillis
                    val targetMs = if (inProgress) chosen.endMillis else chosen.startMillis
                    formatGuardStatusText(
                        nextTitle = chosen.title,
                        targetTimeMillis = targetMs,
                        isInProgress = inProgress,
                        idleText = idleText,
                    )
                }
                GuardSnapshot(statusText = statusText, nextBoundaryMillis = nextBoundary)
            }.getOrDefault(GuardSnapshot(statusText = idleText, nextBoundaryMillis = null))
        }

        internal fun scheduleResurrectionRecovery(context: Context) {
            val appContext = context.applicationContext
            ScheduleGuardWorkScheduler.enqueueDelayedRecovery(appContext)
            runCatching {
                ScheduleAlarmScheduler(appContext).scheduleGuardResurrection(RESURRECT_DELAY_MS)
            }
        }
    }
}
