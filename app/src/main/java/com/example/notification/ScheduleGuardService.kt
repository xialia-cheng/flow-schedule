package com.example.notification

import android.annotation.SuppressLint
import android.app.AlarmManager
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lightweight all-day foreground guard service (`specialUse`) that listens to time ticks and
 * date rollovers, keeps upcoming alarms armed across midnight or task-swipe removal, and posts
 * a low-priority silent ongoing notification isolated from [LiveUpdateManager].
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

    private var activeSyncJob: Job? = null

    private val timeEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val forceReschedule = action == Intent.ACTION_DATE_CHANGED ||
                action == Intent.ACTION_TIME_CHANGED ||
                action == Intent.ACTION_TIMEZONE_CHANGED ||
                action == Intent.ACTION_USER_PRESENT
            triggerSync(rescheduleAlarms = forceReschedule)
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_GUARD) {
            lastKnownGuardEnabled = false
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        val initialText = lastPostedStatusText.ifBlank { getString(R.string.guard_notification_idle) }
        if (!promoteToForeground(initialText)) {
            stopSelf()
            return START_NOT_STICKY
        }

        triggerSync(rescheduleAlarms = true)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (lastKnownGuardEnabled) {
            scheduleDelayedRestartRecovery(applicationContext)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
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

    private fun triggerSync(rescheduleAlarms: Boolean) {
        if (activeSyncJob?.isActive == true && !rescheduleAlarms) return
        activeSyncJob = serviceScope.launch {
            syncMutex.withLock {
                val app = applicationContext as? ScheduleApp ?: return@withLock
                val settings = app.container.preferencesRepository.settings.first()
                lastKnownGuardEnabled = settings.backgroundGuardEnabled
                if (!settings.backgroundGuardEnabled) {
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
                        forceAlert = false,
                        rescheduleAlarms = shouldReschedule,
                    )
                }

                if (shouldReschedule) {
                    lastSyncedDateKey = todayKey
                    lastRescheduleMillis = now
                }

                val statusText = resolveGuardStatusText(applicationContext, now)
                if (statusText != lastPostedStatusText) {
                    lastPostedStatusText = statusText
                    updateGuardNotification(statusText)
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

    companion object {
        // Strictly isolated from LiveUpdateManager.CHANNEL_ID and LIVE_UPDATE_NOTIFICATION_ID
        const val CHANNEL_ID = "ripple_plan_guard_service"
        const val GUARD_NOTIFICATION_ID = 2001

        const val ACTION_START_GUARD = "com.example.action.START_SCHEDULE_GUARD"
        const val ACTION_REFRESH_GUARD = "com.example.action.REFRESH_SCHEDULE_GUARD"
        const val ACTION_STOP_GUARD = "com.example.action.STOP_SCHEDULE_GUARD"

        private const val GUARD_CONTENT_REQUEST_CODE = 2002
        private const val GUARD_RESTART_REQUEST_CODE = 2003
        private const val RESCHEDULE_INTERVAL_MILLIS = 15 * 60_000L

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

        private suspend fun resolveGuardStatusText(context: Context, nowMillis: Long): String {
            val idleText = context.getString(R.string.guard_notification_idle)
            return runCatching {
                val db = ScheduleDatabase.getInstance(context)
                val prefs = UserPreferencesRepository(context).settings.first()
                val scheduleDao = db.scheduleDao()
                val timetableDao = db.timetableDao()

                data class Candidate(
                    val title: String,
                    val startMillis: Long,
                    val endMillis: Long,
                )

                val candidates = mutableListOf<Candidate>()

                scheduleDao.getAllIncompleteSchedules().forEach { s ->
                    if (s.isTestSchedule) return@forEach
                    val isTodayOrActive = DateTimeUtils.isToday(s.startTimeMillis, nowMillis) || s.isInProgress(nowMillis)
                    if (!isTodayOrActive || s.endTimeMillis <= nowMillis) return@forEach
                    candidates.add(Candidate(s.title, s.startTimeMillis, s.endTimeMillis))
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
                            candidates.add(Candidate(course.name, startMs, endMs))
                        }
                    }
                }

                val chosen = candidates
                    .sortedWith(
                        compareByDescending<Candidate> { nowMillis in it.startMillis until it.endMillis }
                            .thenBy { it.startMillis },
                    )
                    .firstOrNull()

                if (chosen == null) {
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
            }.getOrDefault(idleText)
        }

        private fun scheduleDelayedRestartRecovery(context: Context) {
            val appContext = context.applicationContext
            ScheduleGuardWorkScheduler.enqueueDelayedRecovery(appContext)
            runCatching {
                val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val restartIntent = Intent(appContext, ScheduleAlarmReceiver::class.java).apply {
                    action = ScheduleAlarmReceiver.ACTION_GUARD_RESTART
                }
                val pi = PendingIntent.getBroadcast(
                    appContext,
                    GUARD_RESTART_REQUEST_CODE,
                    restartIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                alarmManager?.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 3_000L,
                    pi,
                )
            }
        }
    }
}
